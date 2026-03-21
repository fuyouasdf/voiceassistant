package com.voiceassistant.app.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.voiceassistant.app.R
import com.voiceassistant.app.service.VoiceAssistantService
import com.voiceassistant.app.ui.settings.SettingsActivity
import com.voiceassistant.core.pipeline.PipelineState
import com.voiceassistant.core.pipeline.VoicePipeline
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 主活动界面 - 新 UI 设计
 * 参考主流语音助手APP交互（小爱同学、天猫精灵）
 *
 * 交互方式：
 * 1. 点击主按钮 → 开始语音识别（单击模式）
 * 2. 长按主按钮 → 说话时按住，松开停止（长按模式）
 * 3. 上滑主按钮 → 打断当前操作
 * 4. 左滑/右滑 → 快捷操作
 * 5. 双击 → 重复上次回复
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var voicePipeline: VoicePipeline

    // ========================
    // UI Components
    // ========================

    // Fluid Gradient Visualizer
    private lateinit var vVisualizer: View

    // Status Section
    private lateinit var statusDot: View
    private lateinit var tvStatusTop: TextView

    // State Display
    private lateinit var tvState: TextView
    private lateinit var tvAppTitle: TextView
    private lateinit var promptText: TextView

    // Transcript Section
    private lateinit var transcriptCard: MaterialCardView
    private lateinit var tvTranscriptLabel: TextView
    private lateinit var tvTranscript: TextView
    private lateinit var tvResponseLabel: TextView
    private lateinit var tvResponse: TextView
    private lateinit var responseDivider: View

    // Buttons Container
    private lateinit var buttonContainer: View

    // Buttons
    private lateinit var btnTrigger: MaterialButton
    private lateinit var btnInterrupt: MaterialButton
    private lateinit var btnSettings: View
    private lateinit var btnHistory: MaterialButton
    private lateinit var btnQuickActions: MaterialButton
    private lateinit var btnHelp: MaterialButton

    // Warnings
    private lateinit var memoryWarningCard: MaterialCardView

    // ========================
    // State Management
    // ========================

    // 记录是否为按说起话模式（默认开启）
    private var isPressToSpeak = true

    // 上次按下的坐标，用于检测滑动手势
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isButtonPressed = false

    // 上次识别的文字和回复，用于重复播放
    private var lastRecognizedText = ""
    private var lastResponseText = ""

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] != true) {
            tvState.text = "需要录音权限"
            updateStatus("离线", false)
        } else {
            startVoiceService()
        }
    }

    // ========================
    // Lifecycle
    // ========================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        observeVoicePipeline()
        checkPermissions()
    }

    // ========================
    // Initialization
    // ========================

    private fun initViews() {
        // Visualizer
        vVisualizer = findViewById(R.id.vVisualizer)

        // Status
        statusDot = findViewById(R.id.statusDot)
        tvStatusTop = findViewById(R.id.tvStatusTop) as TextView

        // State Display
        tvState = findViewById(R.id.tvState) as TextView
        tvAppTitle = findViewById(R.id.tvAppTitle) as TextView
        promptText = findViewById(R.id.promptText) as TextView

        // Transcript
        transcriptCard = findViewById(R.id.transcriptCard)
        tvTranscriptLabel = findViewById(R.id.tvTranscriptLabel) as TextView
        tvTranscript = findViewById(R.id.tvTranscript) as TextView
        tvResponseLabel = findViewById(R.id.tvResponseLabel) as TextView
        tvResponse = findViewById(R.id.tvResponse) as TextView
        responseDivider = findViewById(R.id.responseDivider)

        // Button Container
        buttonContainer = findViewById(R.id.buttonContainer)

        // Buttons
        btnTrigger = findViewById(R.id.btnTrigger) as MaterialButton
        btnInterrupt = findViewById(R.id.btnInterrupt) as MaterialButton
        btnSettings = findViewById(R.id.btnSettings)
        btnHistory = findViewById(R.id.btnHistory) as MaterialButton
        btnQuickActions = findViewById(R.id.btnQuickActions) as MaterialButton
        btnHelp = findViewById(R.id.btnHelp) as MaterialButton

        // Warnings
        memoryWarningCard = findViewById(R.id.memoryWarningCard)
    }

    private fun setupListeners() {
        // 主按钮 - 长按/点击事件
        setupTriggerButton()

        // 设置按钮
        btnSettings.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showSettingsDialog()
        }

        // 打断按钮
        btnInterrupt.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            handleInterrupt()
        }

        // 历史按钮 - 左滑
        btnHistory.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showHistoryDialog()
        }

        // 快捷指令按钮 - 右滑
        btnQuickActions.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showQuickActionsPanel()
        }

        // 帮助按钮
        btnHelp.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showHelpDialog()
        }

        // 双击可视化区域重复上次回复
        vVisualizer.setOnClickListener {
            repeatLastResponse()
        }

        // 设置点击
        btnHistory.setOnLongClickListener {
            Toast.makeText(this, "对话历史", Toast.LENGTH_SHORT).show()
            true
        }

        btnQuickActions.setOnLongClickListener {
            Toast.makeText(this, "快捷指令", Toast.LENGTH_SHORT).show()
            true
        }
    }

    /**
     * 设置主按钮交互 - 支持多种模式
     *
     * 参考小爱同学交互：
     * 1. 点击开始识别
     * 2. 按住说话，松开停止
     * 3. 上滑打断
     */
    private fun setupTriggerButton() {
        btnTrigger.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 按下 - 开始录音
                    isButtonPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

                    if (isPressToSpeak) {
                        // 按住说话模式
                        startRecording()
                    }

                    // 按钮按下动画
                    v.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()

                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // 移动 - 检测上滑手势
                    val deltaY = lastTouchY - event.y
                    val deltaX = event.x - lastTouchX

                    // 计算滑动距离
                    if (isButtonPressed) {
                        // 上滑 - 打断
                        if (deltaY > 100 && kotlin.math.abs(deltaX) < 100) {
                            isButtonPressed = false
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            handleInterrupt()
                            return@setOnTouchListener true
                        }
                    }

                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 松开 - 停止录音
                    isButtonPressed = false

                    if (isPressToSpeak) {
                        // 按住说话模式 - 松开停止
                        stopRecording()
                    }

                    // 按钮恢复动画
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()

                    true
                }

                else -> false
            }
        }

        // 点击事件（用于非按说起话模式）
        btnTrigger.setOnClickListener {
            if (!isPressToSpeak) {
                handleVoiceTrigger()
            }
        }
    }

    // ========================
    // Voice Recording Control
    // ========================

    private fun startRecording() {
        Timber.d("Start recording - press to speak")

        // 更新UI
        tvState.text = "请说话..."
        tvTranscript.text = ""
        hideResponse()

        try {
            voicePipeline.interrupt()
            voicePipeline.start()
        } catch (e: Exception) {
            Timber.e(e, "Failed to start voice pipeline")
            tvState.text = "启动失败"
        }
    }

    private fun stopRecording() {
        Timber.d("Stop recording - releasing button")

        // 如果正在录音/识别，发送打断信号停止录音
        // 但不打断思考/播报
        val currentState = getCurrentPipelineState()
        if (currentState == PipelineState.RECORDING || currentState == PipelineState.LISTENING) {
            // 停止录音但继续处理
            tvState.text = "识别中..."
        }
    }

    private fun getCurrentPipelineState(): PipelineState {
        // 从 voicePipeline 获取当前状态
        // 这里简化处理，实际应该通过 stateFlow 获取
        return PipelineState.IDLE
    }

    // ========================
    // Voice Pipeline Observer
    // ========================

    private fun observeVoicePipeline() {
        lifecycleScope.launch {
            voicePipeline.state.collectLatest { stateInfo ->
                updateUIFromState(stateInfo.state, stateInfo.message)
            }
        }
    }

    // ========================
    // Event Handlers
    // ========================

    private fun handleVoiceTrigger() {
        tvState.text = "正在启动..."
        tvTranscript.text = ""
        hideResponse()

        try {
            // Just call interrupt - it will start recording in IDLE state
            voicePipeline.interrupt()
            Timber.d("Voice trigger: starting recording")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start voice pipeline")
            tvState.text = "启动失败"
            showVoiceUnavailableDialog()
        }
    }

    private fun handleInterrupt() {
        try {
            voicePipeline.interrupt()
            Timber.d("Voice pipeline interrupted")

            // 打断动画反馈
            btnTrigger.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(50)
                .withEndAction {
                    btnTrigger.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()

            // 震动反馈
            btnTrigger.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        } catch (e: Exception) {
            Timber.e(e, "Failed to interrupt voice pipeline")
        }
    }

    private fun repeatLastResponse() {
        if (lastResponseText.isNotBlank()) {
            tvState.text = "重复播放..."
            tvResponse.text = lastResponseText
            tvResponse.visibility = View.VISIBLE
            tvResponseLabel.visibility = View.VISIBLE

            // TODO: 实际播放TTS
            Toast.makeText(this, "重复: $lastResponseText", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "没有可重复的回复", Toast.LENGTH_SHORT).show()
        }
    }

    // ========================
    // UI Updates
    // ========================

    private fun updateUIFromState(state: PipelineState, message: String?) {
        runOnUiThread {
            // Update visualizer state - set background drawable based on state
            val drawable = when (state) {
                PipelineState.IDLE -> R.drawable.circle_idle
                PipelineState.LISTENING -> R.drawable.circle_listening
                PipelineState.RECORDING -> R.drawable.circle_recording
                PipelineState.RECOGNIZING -> R.drawable.circle_recognizing
                PipelineState.THINKING -> R.drawable.circle_thinking
                PipelineState.SPEAKING -> R.drawable.circle_speaking
            }
            vVisualizer.setBackgroundResource(drawable)

            // Update state text and prompt
            val (stateText, prompt) = getStateDisplayText(state)
            tvState.text = stateText
            promptText.text = prompt

            // Update transcript based on message
            message?.let {
                if (it.isNotBlank()) {
                    when (state) {
                        PipelineState.RECORDING -> {
                            // 实时识别中 - 逐字显示
                            tvTranscript.text = it
                            tvTranscriptLabel.text = "识别中"
                        }
                        PipelineState.RECOGNIZING, PipelineState.THINKING -> {
                            // 识别完成/思考中 - 显示完整文字
                            tvTranscript.text = it
                            tvTranscriptLabel.text = "你说"
                            lastRecognizedText = it
                        }
                        PipelineState.SPEAKING -> {
                            // 播报中 - 显示回复
                            showTranscriptResponse(it)
                            lastResponseText = it
                        }
                        else -> {}
                    }
                }
            }

            // Update button states
            updateButtonStates(state)

            // Update status
            updateStatusForState(state)
        }
    }

    private fun getStateDisplayText(state: PipelineState): Pair<String, String> {
        return when (state) {
            PipelineState.IDLE -> Pair("待机中", "对小爱说...")
            PipelineState.LISTENING -> Pair("我在听", "请说话")
            PipelineState.RECORDING -> Pair("正在录音", "松开结束")
            PipelineState.RECOGNIZING -> Pair("识别中", "稍等...")
            PipelineState.THINKING -> Pair("思考中", "处理中...")
            PipelineState.SPEAKING -> Pair("正在说话", "请听好")
        }
    }

    private fun updateButtonStates(state: PipelineState) {
        when (state) {
            PipelineState.IDLE -> {
                btnTrigger.isEnabled = true
                btnTrigger.text = "按住说话"
                btnTrigger.setIconResource(android.R.drawable.ic_btn_speak_now)
                btnInterrupt.visibility = View.GONE

                // 按钮恢复
                btnTrigger.alpha = 1f
                btnTrigger.text = if (isPressToSpeak) "按住说话" else "开始说话"
            }

            PipelineState.LISTENING -> {
                btnTrigger.isEnabled = true
                btnTrigger.text = if (isPressToSpeak) "松开结束" else "录音中..."
                btnTrigger.setIconResource(android.R.drawable.ic_btn_speak_now)
                btnTrigger.alpha = 0.8f
            }

            PipelineState.RECORDING -> {
                btnTrigger.isEnabled = true
                btnTrigger.text = "松开结束"
                btnTrigger.setIconResource(android.R.drawable.ic_btn_speak_now)
                btnTrigger.alpha = 0.8f

                // 录音中波纹动画
                btnTrigger.animate()
                    .scaleX(1.05f)
                    .scaleY(1.05f)
                    .setDuration(500)
                    .withEndAction {
                        btnTrigger.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(500)
                            .start()
                    }
                    .start()
            }

            PipelineState.RECOGNIZING -> {
                btnTrigger.isEnabled = false
                btnTrigger.text = "识别中..."
                btnTrigger.alpha = 0.6f
                btnInterrupt.visibility = View.GONE
            }

            PipelineState.THINKING -> {
                btnTrigger.isEnabled = false
                btnTrigger.text = "思考中..."
                btnTrigger.alpha = 0.6f
                btnInterrupt.visibility = View.VISIBLE
            }

            PipelineState.SPEAKING -> {
                btnTrigger.isEnabled = false
                btnTrigger.text = "播报中..."
                btnInterrupt.visibility = View.VISIBLE
                btnTrigger.alpha = 0.6f
            }
        }
    }

    private fun updateStatusForState(state: PipelineState) {
        val (statusText, isOnline) = when (state) {
            PipelineState.IDLE -> Pair("在线", true)
            PipelineState.LISTENING -> Pair("倾听中", true)
            PipelineState.RECORDING -> Pair("录音中", true)
            PipelineState.RECOGNIZING -> Pair("识别中", true)
            PipelineState.THINKING -> Pair("思考中", true)
            PipelineState.SPEAKING -> Pair("播报中", true)
        }
        updateStatus(statusText, isOnline)
    }

    private fun updateStatus(text: String, isOnline: Boolean) {
        tvStatusTop.text = text
        statusDot.setBackgroundResource(
            if (isOnline) R.drawable.circle_status_online
            else R.drawable.circle_status_offline
        )
    }

    private fun showTranscriptResponse(response: String) {
        tvResponseLabel.visibility = View.VISIBLE
        tvResponse.text = response
        tvResponse.visibility = View.VISIBLE
        responseDivider.visibility = View.VISIBLE
    }

    private fun hideResponse() {
        tvResponseLabel.visibility = View.GONE
        tvResponse.text = ""
        tvResponse.visibility = View.GONE
        responseDivider.visibility = View.GONE
    }

    // ========================
    // Permissions
    // ========================

    private fun checkPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startVoiceService()
                tvState.text = "你好，主人"
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
    }

    // ========================
    // Dialogs & Panels
    // ========================

    private fun showSettingsDialog() {
        // Navigate to SettingsActivity
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun showVoiceUnavailableDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("语音功能不可用")
            .setMessage("您的设备内存不足，无法加载语音识别模型。\n\n请尝试：\n1. 关闭其他应用释放内存\n2. 重启手机后重试\n\n您仍可以使用手动触发功能。")
            .setPositiveButton("确定", null)
            .show()
    }

    private fun showHistoryDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("对话历史")
            .setMessage(
                if (lastRecognizedText.isNotBlank()) {
                    "你说: $lastRecognizedText\n\n我: $lastResponseText"
                } else {
                    "暂无历史记录"
                }
            )
            .setPositiveButton("确定", null)
            .setNegativeButton("清空") { _, _ ->
                lastRecognizedText = ""
                lastResponseText = ""
                Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showQuickActionsPanel() {
        val actions = arrayOf(
            "播放音乐" to "🎵",
            "开灯" to "💡",
            "关灯" to "🌙",
            "问天气" to "🌤️",
            "设闹钟" to "⏰",
            "控制家电" to "📺"
        )

        val actionTexts = actions.map { "${it.second} ${it.first}" }.toTypedArray()

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("快捷指令")
            .setItems(actionTexts) { _, which ->
                val action = actions[which].first
                tvTranscriptLabel.text = "你说"
                tvTranscript.text = action

                // 执行快捷指令
                handleVoiceTrigger()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showHelpDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("使用帮助")
            .setMessage(
                """
                🎤 唤醒说「你好爪爪」

                👆 按住主按钮说话，松开结束

                ⬆️ 上滑按钮可打断当前操作

                👋 打断播报点击 ⚡ 按钮

                🔄 双击圆球重复上次回复

                💡 左滑查看历史，右滑快捷指令

                ⚠️ 小米手机需开启自启动权限
                """.trimIndent()
            )
            .setPositiveButton("确定", null)
            .show()
    }

    private fun openAppSettings() {
        try {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            ).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
        }
    }
}