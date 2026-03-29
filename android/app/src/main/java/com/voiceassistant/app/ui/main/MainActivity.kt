package com.voiceassistant.app.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
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
import com.voiceassistant.data.remote.JellyfinClient
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

    @Inject
    lateinit var jellyfinClient: JellyfinClient

    // UI Components
    private lateinit var statusDot: View
    private lateinit var tvStatus: TextView
    private lateinit var tvProvider: TextView
    private lateinit var jellyfinStatusDot: View
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
    private lateinit var voiceInputCard: MaterialCardView
    private lateinit var etTextInput: EditText
    private lateinit var btnSend: ImageButton

    // Quick Actions
    private lateinit var chipWeather: Chip
    private lateinit var chipJellyfin: Chip

    // State
    private var lastRecognizedText = ""
    private var lastResponseText = ""

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] != true) {
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
        // 每次返回主页时重新检测 LLM 和 Jellyfin 连接状态
        testLlmConnection()
        testJellyfinConnection()
    }

    private fun initViews() {
        // Status
        statusDot = findViewById(R.id.statusDot)
        tvStatus = findViewById(R.id.tvStatus)
        tvProvider = findViewById(R.id.tvProvider)
        jellyfinStatusDot = findViewById(R.id.jellyfinStatusDot)
        tvJellyfinStatus = findViewById(R.id.tvJellyfinStatus)
        btnSettings = findViewById(R.id.btnSettings)

        // 测试 LLM 连接状态
        testLlmConnection()

        // 测试 Jellyfin 连接状态
        testJellyfinConnection()

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
        etTextInput = findViewById(R.id.etTextInput)
        btnSend = findViewById(R.id.btnSend)

        // Quick actions
        chipWeather = findViewById(R.id.chipWeather)
        chipJellyfin = findViewById(R.id.chipJellyfin)
    }

    private fun setupListeners() {
        // Send button click
        btnSend.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            submitTextInput()
        }

        // Keyboard action send
        etTextInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                btnSend.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                submitTextInput()
                true
            } else {
                false
            }
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

    private fun submitTextInput() {
        val text = etTextInput.text.toString().trim()
        if (text.isBlank()) return

        // Add user message to conversation
        addMessage(text, true)
        lastRecognizedText = text

        // Clear input
        etTextInput.text.clear()

        // Update state
        updateUIFromState(PipelineState.THINKING, "思考中...")

        // Trigger voice pipeline with text input
        try {
            voicePipeline.processTextInput(text)
        } catch (e: Exception) {
            Timber.e(e, "Failed to process text input")
        }
    }

    private fun triggerQuickAction(text: String) {
        addMessage(text, true)
        lastRecognizedText = text
        updateUIFromState(PipelineState.THINKING, "思考中...")
        try {
            voicePipeline.processTextInput(text)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start voice pipeline")
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
        btnSend.visibility = View.VISIBLE
        hideAsrCard()
    }

    private fun updateUIFromState(state: PipelineState, message: String?, recognizedText: String = "") {
        runOnUiThread {
            try {
                when (state) {
                    PipelineState.INITIALIZING -> {
                        btnSend.visibility = View.INVISIBLE
                    }
                    PipelineState.IDLE -> {
                        resetUI()
                        updateStatus(true)
                    }
                    PipelineState.WAKEWORD_DETECTED -> {
                        btnSend.visibility = View.INVISIBLE
                    }
                    PipelineState.LISTENING -> {
                        btnSend.visibility = View.INVISIBLE
                    }
                    PipelineState.RECORDING -> {
                        showAsrCard()
                    }
                    PipelineState.RECOGNIZING -> {
                        message?.let { updateAsrText(it, isFinal = false) }
                    }
                    PipelineState.THINKING -> {
                        btnSend.visibility = View.VISIBLE
                        stopAsrPulseAnimation()
                        hideAsrCard()
                        // 添加用户识别的最终文本到对话
                        if (recognizedText.isNotBlank()) {
                            addMessage(recognizedText, true)
                            lastRecognizedText = recognizedText
                        }
                    }
                    PipelineState.SPEAKING -> {
                        btnSend.visibility = View.VISIBLE
                        hideAsrCard()
                    }
                }

                // Handle messages
                message?.let {
                    when (state) {
                        PipelineState.RECORDING -> {
                            if (it.isNotBlank()) {
                                updateAsrText(it, isFinal = false)
                            }
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
        tvAsrResult.text = text
    }

    private fun stopAsrPulseAnimation() {
        asrPulseDot.scaleX = 1f
        asrPulseDot.scaleY = 1f
    }

    // ==================== Message Handling ====================

    private fun addMessage(text: String, isUser: Boolean) {
        // Hide empty hint
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
        val llmConfigured = configHolder.llmBaseUrl.isNotEmpty() && configHolder.llmApiKey.isNotEmpty()
        if (!llmConfigured) {
            tvProvider.text = "LLM: 未配置"
            return
        }

        tvProvider.text = "LLM: 已配置"
        updateStatus(true) // LLM 配置时更新主页状态为在线
    }

    /**
     * 测试 Jellyfin 连接状态
     */
    private fun testJellyfinConnection() {
        lifecycleScope.launch {
            val jellyfinConfigured = configHolder.jellyfinUrl.isNotEmpty() &&
                configHolder.jellyfinApiKey.isNotEmpty()
            if (!jellyfinConfigured) {
                tvJellyfinStatus.text = "Jellyfin: 未配置"
                jellyfinStatusDot.setBackgroundResource(R.drawable.circle_status_offline)
                return@launch
            }

            tvJellyfinStatus.text = "Jellyfin: 连接中..."
            try {
                val result = jellyfinClient.testConnection()
                val isOnline = result.isSuccess
                tvJellyfinStatus.text = if (isOnline) "Jellyfin: 已连接" else "Jellyfin: 未连接"
                jellyfinStatusDot.setBackgroundResource(
                    if (isOnline) R.drawable.circle_status_online else R.drawable.circle_status_offline
                )
            } catch (e: Exception) {
                Timber.e(e, "Jellyfin connection test failed")
                tvJellyfinStatus.text = "Jellyfin: 未连接"
                jellyfinStatusDot.setBackgroundResource(R.drawable.circle_status_offline)
            }
        }
    }
}
