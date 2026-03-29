package com.voiceassistant.app.ui.main

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.voiceassistant.app.R
import com.voiceassistant.app.di.ConfigHolder
import com.voiceassistant.app.service.VoiceAssistantService
import com.voiceassistant.domain.repository.LLMRepository
import com.voiceassistant.app.ui.settings.SettingsActivity
import com.voiceassistant.app.ui.music.JellyfinBrowseActivity
import com.voiceassistant.core.pipeline.PipelineState
import com.voiceassistant.core.pipeline.VoicePipeline
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 主活动界面 - 极简对话风格
 * 类似 ChatGPT App 的简洁交互
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var voicePipeline: VoicePipeline

    @Inject
    lateinit var configHolder: ConfigHolder

    @Inject
    lateinit var llmRepository: LLMRepository

    // UI Components
    private lateinit var statusDot: View
    private lateinit var tvStatus: TextView
    private lateinit var tvProvider: TextView
    private lateinit var tvJellyfinStatus: TextView
    private lateinit var btnSettings: ImageButton

    // Conversation
    private lateinit var tvEmptyHint: TextView
    private lateinit var conversationScroll: ScrollView
    private lateinit var conversationContainer: LinearLayout

    // ASR Real-time Result
    private lateinit var asrResultCard: MaterialCardView
    private lateinit var asrPulseDot: View
    private lateinit var tvAsrResult: TextView

    // Input Area
    private lateinit var icMic: ImageView
    private lateinit var waveformContainer: View
    private lateinit var tvInputState: TextView
    private lateinit var btnInterrupt: ImageButton
    private lateinit var voiceTouchArea: View
    private lateinit var voiceInputCard: MaterialCardView
    private lateinit var activeRing: View
    private val waveBars = mutableListOf<View>()

    // Quick Actions
    private lateinit var chipWeather: Chip
    private lateinit var chipJellyfin: Chip

    // State
    private var isRecording = false
    private var lastRecognizedText = ""
    private var lastResponseText = ""

    // Animators
    private var ringAnimator: ValueAnimator? = null
    private var asrPulseAnimator: ValueAnimator? = null
    private var typewriterAnimator: ValueAnimator? = null
    private var floatingAnimator: ValueAnimator? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] != true) {
            tvInputState.text = "需要录音权限"
            updateStatus(false)
        } else {
            startVoiceService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        observeVoicePipeline()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        // 每次返回主页时重新检测 LLM 连接状态
        testLlmConnection()
    }

    private fun initViews() {
        // Status
        statusDot = findViewById(R.id.statusDot)
        tvStatus = findViewById(R.id.tvStatus)
        tvProvider = findViewById(R.id.tvProvider)
        tvJellyfinStatus = findViewById(R.id.tvJellyfinStatus)
        btnSettings = findViewById(R.id.btnSettings)

        // 测试 LLM 连接状态
        testLlmConnection()

        // 显示 Jellyfin 配置状态
        val jellyfinConfigured = configHolder.jellyfinUrl.isNotEmpty() &&
            configHolder.jellyfinUsername.isNotEmpty() && configHolder.jellyfinPassword.isNotEmpty()
        tvJellyfinStatus.text = if (jellyfinConfigured) "Jellyfin: 已配置" else "Jellyfin: 未配置"

        // Conversation
        tvEmptyHint = findViewById(R.id.tvEmptyHint)
        conversationScroll = findViewById(R.id.conversationScroll)
        conversationContainer = findViewById(R.id.conversationContainer)

        // ASR Result Card
        asrResultCard = findViewById(R.id.asrResultCard)
        asrPulseDot = findViewById(R.id.asrPulseDot)
        tvAsrResult = findViewById(R.id.tvAsrResult)

        // Input
        voiceInputCard = findViewById(R.id.voiceInputCard)
        icMic = findViewById(R.id.icMic)
        waveformContainer = findViewById(R.id.waveformContainer)
        tvInputState = findViewById(R.id.tvInputState)
        btnInterrupt = findViewById(R.id.btnInterrupt)
        voiceTouchArea = findViewById(R.id.voiceTouchArea)
        activeRing = findViewById(R.id.activeRing)

        // Wave bars
        waveBars.add(findViewById(R.id.waveBar1))
        waveBars.add(findViewById(R.id.waveBar2))
        waveBars.add(findViewById(R.id.waveBar3))
        waveBars.add(findViewById(R.id.waveBar4))
        waveBars.add(findViewById(R.id.waveBar5))

        // Quick actions
        chipWeather = findViewById(R.id.chipWeather)
        chipJellyfin = findViewById(R.id.chipJellyfin)

        // Start floating animation for empty state hint
        startFloatingAnimation()
    }

    // ==================== Empty State Animation ====================

    private fun startFloatingAnimation() {
        floatingAnimator?.cancel()
        // Apply initial position
        tvEmptyHint.translationY = 0f
        floatingAnimator = ValueAnimator.ofFloat(0f, -10f, 0f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                if (isFinishing || isDestroyed) return@addUpdateListener
                tvEmptyHint.translationY = animator.animatedValue as Float
            }
            start()
        }
    }

    private fun stopFloatingAnimation() {
        floatingAnimator?.cancel()
        floatingAnimator = null
    }

    private fun setupListeners() {
        // Voice touch area
        voiceTouchArea.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRecording()
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecording()
                    true
                }
                else -> false
            }
        }

        // Interrupt button
        btnInterrupt.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            handleInterrupt()
        }

        // Settings
        btnSettings.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Quick actions
        chipWeather.setOnClickListener { triggerQuickAction("今天天气怎么样") }
        chipJellyfin.setOnClickListener {
            startActivity(Intent(this, JellyfinBrowseActivity::class.java))
        }
    }

    private fun triggerQuickAction(text: String) {
        addMessage(text, true)
        lastRecognizedText = text
        tvInputState.text = "处理中..."
        try {
            voicePipeline.start()
        } catch (e: Exception) {
            Timber.e(e, "Failed to start voice pipeline")
        }
    }

    private fun startRecording() {
        if (isRecording) return
        isRecording = true

        tvInputState.text = "松开结束"
        icMic.visibility = View.INVISIBLE
        waveformContainer.visibility = View.VISIBLE
        startWaveAnimation()
        showActiveRing()

        try {
            voicePipeline.interrupt()
        } catch (e: Exception) {
            Timber.e(e, "Failed to interrupt")
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false

        tvInputState.text = "按住说话"
        icMic.visibility = View.VISIBLE
        waveformContainer.visibility = View.INVISIBLE
        activeRing.visibility = View.INVISIBLE
        stopWaveAnimation()
        // 不要在这里 hideAsrCard，让状态机控制

        // 通知 Pipeline 停止录音并开始识别
        try {
            voicePipeline.stopRecording()
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop recording")
        }
    }

    private fun observeVoicePipeline() {
        lifecycleScope.launch {
            voicePipeline.state.collectLatest { stateInfo ->
                updateUIFromState(stateInfo.state, stateInfo.message, stateInfo.recognizedText)
            }
        }
    }

    private fun handleInterrupt() {
        try {
            voicePipeline.interrupt()
            resetUI()
        } catch (e: Exception) {
            Timber.e(e, "Failed to interrupt")
        }
    }

    private fun resetUI() {
        tvInputState.text = "按住说话"
        btnInterrupt.visibility = View.INVISIBLE
        icMic.visibility = View.VISIBLE
        waveformContainer.visibility = View.INVISIBLE
        activeRing.visibility = View.INVISIBLE
        stopWaveAnimation()
        hideAsrCard()
        stopAllAnimations()
    }

    private fun updateUIFromState(state: PipelineState, message: String?, recognizedText: String = "") {
        runOnUiThread {
            try {
                when (state) {
                    PipelineState.INITIALIZING -> {
                        tvInputState.text = message ?: "正在准备..."
                        btnInterrupt.visibility = View.INVISIBLE
                        icMic.visibility = View.INVISIBLE
                        waveformContainer.visibility = View.INVISIBLE
                    }
                    PipelineState.IDLE -> {
                        resetUI()
                        updateStatus(true)
                    }
                    PipelineState.WAKEWORD_DETECTED -> {
                        tvInputState.text = "我在听..."
                        btnInterrupt.visibility = View.INVISIBLE
                    }
                    PipelineState.LISTENING -> {
                        tvInputState.text = "请说话..."
                        btnInterrupt.visibility = View.INVISIBLE
                    }
                    PipelineState.RECORDING -> {
                        tvInputState.text = "聆听中..."
                        icMic.visibility = View.INVISIBLE
                        waveformContainer.visibility = View.VISIBLE
                        startWaveAnimation()
                        showAsrCard()
                        startAsrPulseAnimation()
                        tvAsrResult.text = "" // 清空之前的文本
                    }
                    PipelineState.RECOGNIZING -> {
                        tvInputState.text = "识别中..."
                        btnInterrupt.visibility = View.INVISIBLE
                        // 增量追加到 ASR Card
                        message?.let { updateAsrText(it, isFinal = false) }
                    }
                    PipelineState.THINKING -> {
                        tvInputState.text = "思考中..."
                        btnInterrupt.visibility = View.VISIBLE
                        stopAsrPulseAnimation()
                        hideAsrCard()
                        // 添加用户识别的最终文本到对话
                        if (recognizedText.isNotBlank()) {
                            addMessage(recognizedText, true)
                            lastRecognizedText = recognizedText
                        }
                    }
                    PipelineState.SPEAKING -> {
                        tvInputState.text = "播报中..."
                        btnInterrupt.visibility = View.VISIBLE
                        icMic.visibility = View.VISIBLE
                        waveformContainer.visibility = View.INVISIBLE
                        activeRing.visibility = View.INVISIBLE
                        stopWaveAnimation()
                        hideAsrCard()
                    }
                }

                // Update wave color based on state
                val waveColor = when (state) {
                    PipelineState.INITIALIZING -> R.color.primary
                    PipelineState.IDLE -> R.color.primary
                    PipelineState.WAKEWORD_DETECTED -> R.color.success
                    PipelineState.LISTENING, PipelineState.RECORDING -> R.color.info
                    PipelineState.RECOGNIZING -> R.color.warning
                    PipelineState.THINKING -> R.color.secondary
                    PipelineState.SPEAKING -> R.color.success
                }
                val color = ContextCompat.getColor(this, waveColor)
                waveBars.forEach { it.setBackgroundColor(color) }

                // Handle ASR messages
                message?.let {
                    when (state) {
                        PipelineState.RECORDING -> {
                            // 流式显示中间结果到 ASR Card
                            if (it.isNotBlank()) {
                                updateAsrText(it, isFinal = false)
                            }
                        }
                        PipelineState.RECOGNIZING -> {
                            // 不在这里添加消息，等 final result 后在对话中显示
                        }
                        PipelineState.SPEAKING -> {
                            addMessage(it, false)
                            lastResponseText = it
                        }
                        else -> {}
                    }
                }

                updateStatus(state != PipelineState.IDLE || lastRecognizedText.isNotBlank())
            } catch (e: Exception) {
                Timber.e(e, "Error updating UI for state $state")
            }
        }
    }

    // ==================== ASR Card ====================

    private fun showAsrCard() {
        asrResultCard.visibility = View.VISIBLE
        asrResultCard.alpha = 1f
    }

    private fun hideAsrCard() {
        asrResultCard.visibility = View.GONE
    }

    private fun updateAsrText(text: String, isFinal: Boolean) {
        if (isFinal) {
            // 最终结果：直接显示
            typewriterAnimator?.cancel()
            tvAsrResult.text = text
            stopAsrPulseAnimation()
        } else {
            // 中间结果：直接覆盖显示（不追加）
            typewriterAnimator?.cancel()
            tvAsrResult.text = text
        }
    }

    private fun showTypewriterText(fullText: String) {
        typewriterAnimator?.cancel()
        var currentIndex = 0
        typewriterAnimator = ValueAnimator.ofInt(0, fullText.length).apply {
            duration = 300
            addUpdateListener { animator ->
                if (isFinishing || isDestroyed) return@addUpdateListener
                currentIndex = animator.animatedValue as Int
                tvAsrResult.text = fullText.substring(0, currentIndex)
            }
            start()
        }
    }

    private fun stopTypewriterAnimation() {
        typewriterAnimator?.cancel()
        typewriterAnimator = null
    }

    private fun startAsrPulseAnimation() {
        asrPulseAnimator?.cancel()
        asrPulseAnimator = ValueAnimator.ofFloat(0.3f, 1f, 0.3f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                if (isFinishing || isDestroyed) return@addUpdateListener
                val scale = animator.animatedValue as Float
                asrPulseDot.scaleX = scale
                asrPulseDot.scaleY = scale
            }
            start()
        }
    }

    private fun stopAsrPulseAnimation() {
        asrPulseAnimator?.cancel()
        asrPulseAnimator = null
        asrPulseDot.scaleX = 1f
        asrPulseDot.scaleY = 1f
    }

    // ==================== Visual Feedback ====================

    private fun showActiveRing() {
        activeRing.visibility = View.VISIBLE
        ringAnimator?.cancel()
        ringAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                if (isFinishing || isDestroyed) return@addUpdateListener
                activeRing.alpha = (animator.animatedValue as Float) * 0.8f
            }
            start()
        }
    }

    private fun stopAllAnimations() {
        ringAnimator?.cancel()
        ringAnimator = null
        asrPulseAnimator?.cancel()
        asrPulseAnimator = null
        typewriterAnimator?.cancel()
        typewriterAnimator = null
        floatingAnimator?.cancel()
        floatingAnimator = null
    }

    // ==================== Wave Animation ====================

    private fun startWaveAnimation() {
        // Simple visual without complex animation
        waveBars.forEach { bar ->
            bar.scaleY = 0.5f + Math.random().toFloat() * 0.5f
        }
    }

    private fun stopWaveAnimation() {
        waveBars.forEach { bar ->
            bar.scaleY = 1f
        }
    }

    // ==================== Message Handling ====================

    private fun addMessage(text: String, isUser: Boolean) {
        // Hide empty hint and stop floating animation
        stopFloatingAnimation()
        tvEmptyHint.visibility = View.GONE
        conversationScroll.visibility = View.VISIBLE

        // Get current time for timestamp
        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val timestamp = timeFormat.format(java.util.Date())

        // Create message container with timestamp
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)

            if (isUser) {
                gravity = android.view.Gravity.END
            } else {
                gravity = android.view.Gravity.START
            }
        }

        // Add timestamp (small and subtle)
        val timestampView = TextView(this).apply {
            this.text = timestamp
            this.textSize = 10f
            setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (isUser) {
                params.setMargins(0, 0, 16, 4)
            } else {
                params.setMargins(16, 0, 0, 4)
            }
            layoutParams = params
        }

        // Create message view
        val messageView = TextView(this).apply {
            this.text = text
            this.textSize = 16f
            setPadding(24, 16, 24, 16)

            if (isUser) {
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            } else {
                setTextColor(ContextCompat.getColor(context, R.color.on_primary))
            }

            // Rounded corners
            background = androidx.core.content.res.ResourcesCompat.getDrawable(
                resources,
                if (isUser) R.drawable.bg_message_user else R.drawable.bg_message_ai,
                null
            )

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (isUser) marginStart = 64 else marginEnd = 64
            }
        }

        // Add timestamp and message to container
        container.addView(timestampView)
        container.addView(messageView)

        // Create outer container for alignment
        val outerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 0)

            if (isUser) {
                gravity = android.view.Gravity.END
                addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
            }

            addView(container)

            if (!isUser) {
                addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
            }
        }

        conversationContainer.addView(outerContainer)

        // Scroll to bottom
        conversationScroll.post {
            conversationScroll.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    // ==================== Status ====================

    private fun updateStatus(isOnline: Boolean) {
        statusDot.setBackgroundResource(
            if (isOnline) R.drawable.circle_status_online
            else R.drawable.circle_status_offline
        )
        tvStatus.text = if (isOnline) "在线" else "离线"
    }

    private fun checkPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startVoiceService()
            }
            else -> {
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
        }
    }

    private fun startVoiceService() {
        val intent = Intent(this, VoiceAssistantService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // Start ASR/TTS background initialization after service starts
        voicePipeline.initializeInBackground()
    }

    /**
     * 测试 LLM 连接状态
     */
    private fun testLlmConnection() {
        lifecycleScope.launch {
            val llmConfigured = configHolder.llmBaseUrl.isNotEmpty() && configHolder.llmApiKey.isNotEmpty()
            if (!llmConfigured) {
                tvProvider.text = "LLM: 未配置"
                return@launch
            }

            tvProvider.text = "LLM: 连接中..."
            val result = llmRepository.testConnection()
            if (result.isSuccess) {
                tvProvider.text = "LLM: 已连接"
                updateStatus(true) // LLM 在线时更新主页状态为在线
            } else {
                tvProvider.text = "LLM: 未连接"
            }
        }
    }
}
