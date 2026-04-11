package com.voiceassistant.app.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.animation.ObjectAnimator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.voiceassistant.app.R
import com.voiceassistant.app.di.ConfigHolder
import com.voiceassistant.app.service.VoiceAssistantService
import com.voiceassistant.data.remote.JellyfinClient
import com.voiceassistant.data.local.ChatMessageDao
import com.voiceassistant.data.local.ChatMessageEntity
import com.voiceassistant.domain.repository.LLMRepository
import com.voiceassistant.app.ui.settings.SettingsActivity
import com.voiceassistant.app.ui.music.JellyfinBrowseActivity
import com.voiceassistant.app.ui.music.PlaylistListActivity
import com.voiceassistant.app.ui.playback.MiniPlayerFragment
import com.voiceassistant.core.pipeline.PipelineState
import com.voiceassistant.core.pipeline.VoicePipeline
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * 主活动界面 - 极简对话风格
 * 类似 ChatGPT App 的简洁交互
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    companion object {
        private const val PREF_SELECTED_DEVICE_NAME = "jellyfin_selected_device_name"
        private const val HISTORY_PAGE_SIZE = 20
    }

    @Inject
    lateinit var voicePipeline: VoicePipeline

    @Inject
    lateinit var configHolder: ConfigHolder

    @Inject
    lateinit var llmRepository: LLMRepository

    @Inject
    lateinit var jellyfinClient: JellyfinClient

    @Inject
    lateinit var chatMessageDao: ChatMessageDao

    // UI Components
    private lateinit var statusBarArea: LinearLayout
    private lateinit var statusDot: View
    private lateinit var tvStatus: TextView
    private lateinit var tvProvider: TextView
    private lateinit var jellyfinStatusDot: View
    private lateinit var tvJellyfinStatus: TextView
    private lateinit var tvSelectedPlaybackDevice: TextView
    private lateinit var btnSettings: ImageButton

    // Conversation
    private lateinit var tvEmptyHint: TextView
    private lateinit var conversationRecyclerView: RecyclerView
    private lateinit var chatMessageAdapter: ChatMessageAdapter

    // ASR Real-time Result
    private lateinit var asrResultCard: MaterialCardView
    private lateinit var asrPulseDot: View
    private lateinit var tvAsrResult: TextView

    // Input Area
    private lateinit var inputArea: LinearLayout
    private lateinit var voiceInputCard: MaterialCardView
    private lateinit var etTextInput: EditText
    private lateinit var btnSend: ImageButton

    // Quick Actions
    private lateinit var chipWeather: Chip
    private lateinit var chipJellyfin: Chip
    private lateinit var chipPlaylist: Chip

    // Playback
    private lateinit var miniPlayerContainer: View

    // Haptic Feedback
    private var vibrator: Vibrator? = null

    // State
    private var lastRecognizedText = ""
    private var lastResponseText = ""
    private var llmConnectionCheckJob: Job? = null
    private var llmStatusPollingJob: Job? = null
    private var isLoadingHistory = false
    private var hasMoreHistory = true
    private var oldestLoadedMessageId: Long? = null
    private var oldestLoadedMessageCreatedAt: Long? = null

    // Animation
    private var pulseAnimatorX: ObjectAnimator? = null
    private var pulseAnimatorY: ObjectAnimator? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] != true) {
            Toast.makeText(this, R.string.permission_mic_denied, Toast.LENGTH_SHORT).show()
        } else {
            startVoiceService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        initViews()
        setupInsets()
        setupListeners()
        setupConversationPagination()
        setupMiniPlayer(savedInstanceState)
        loadInitialConversationHistory()
        observeVoicePipeline()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        // 每次返回主页时重新检测 LLM 和 Jellyfin 连接状态
        testLlmConnection()
        testJellyfinConnection()
        refreshSelectedPlaybackDeviceDisplay()
    }

    override fun onStart() {
        super.onStart()
        startLlmStatusPolling()
    }

    override fun onStop() {
        llmStatusPollingJob?.cancel()
        llmStatusPollingJob = null
        stopAsrPulseAnimation()
        super.onStop()
    }

    private fun initViews() {
        // Status
        statusBarArea = findViewById(R.id.statusBarArea)
        statusDot = findViewById(R.id.statusDot)
        tvStatus = findViewById(R.id.tvStatus)
        tvProvider = findViewById(R.id.tvProvider)
        jellyfinStatusDot = findViewById(R.id.jellyfinStatusDot)
        tvJellyfinStatus = findViewById(R.id.tvJellyfinStatus)
        tvSelectedPlaybackDevice = findViewById(R.id.tvSelectedDlnaDevice)
        btnSettings = findViewById(R.id.btnSettings)

        // 测试 LLM 连接状态
        testLlmConnection()

        // 测试 Jellyfin 连接状态
        testJellyfinConnection()
        refreshSelectedPlaybackDeviceDisplay()

        // Conversation
        tvEmptyHint = findViewById(R.id.tvEmptyHint)
        conversationRecyclerView = findViewById(R.id.conversationRecyclerView)
        chatMessageAdapter = ChatMessageAdapter { messageId, view ->
            showDeleteMessageDialog(messageId, view)
        }

        // ASR Result Card
        asrResultCard = findViewById(R.id.asrResultCard)
        asrPulseDot = findViewById(R.id.asrPulseDot)
        tvAsrResult = findViewById(R.id.tvAsrResult)

        // Input
        inputArea = findViewById(R.id.inputArea)
        voiceInputCard = findViewById(R.id.voiceInputCard)
        etTextInput = findViewById(R.id.etTextInput)
        btnSend = findViewById(R.id.btnSend)

        // Quick actions
        chipWeather = findViewById(R.id.chipWeather)
        chipJellyfin = findViewById(R.id.chipJellyfin)
        chipPlaylist = findViewById(R.id.chipPlaylist)

        // Playback
        miniPlayerContainer = findViewById(R.id.miniPlayerContainer)

        // Initialize vibrator for haptic feedback
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun setupInsets() {
        // Top: Status bar area
        ViewCompat.setOnApplyWindowInsetsListener(statusBarArea) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, insets.top, 0, 0)
            windowInsets
        }

        // Bottom: Input area with navigation bar padding
        ViewCompat.setOnApplyWindowInsetsListener(inputArea) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                insets.bottom + resources.getDimensionPixelSize(R.dimen.bottom_padding_standard)
            )
            windowInsets
        }

        // Mini player container with system bars insets
        ViewCompat.setOnApplyWindowInsetsListener(miniPlayerContainer) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, insets.bottom)
            windowInsets
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun setupMiniPlayer(savedInstanceState: Bundle?) {
        // Add MiniPlayerFragment if not already added
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.miniPlayerContainer, MiniPlayerFragment())
                .commit()
        }
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
        chipWeather.setOnClickListener { triggerQuickAction(getString(R.string.quick_action_weather)) }
        chipJellyfin.setOnClickListener {
            startActivity(Intent(this, JellyfinBrowseActivity::class.java))
        }
        chipPlaylist.setOnClickListener {
            startActivity(Intent(this, PlaylistListActivity::class.java))
        }
    }

    private fun submitTextInput() {
        val text = etTextInput.text.toString().trim()
        if (text.isBlank()) return

        // Clear input
        etTextInput.text.clear()

        // Update state
        updateUIFromState(PipelineState.THINKING, getString(R.string.state_thinking))

        // Trigger voice pipeline with text input
        try {
            voicePipeline.processTextInput(text)
        } catch (e: Exception) {
            Timber.e(e, "Failed to process text input")
        }
    }

    private fun triggerQuickAction(text: String) {
        updateUIFromState(PipelineState.THINKING, getString(R.string.state_thinking))
        try {
            voicePipeline.processTextInput(text)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start voice pipeline")
        }
    }

    private fun observeVoicePipeline() {
        lifecycleScope.launch {
            voicePipeline.state.collectLatest { stateInfo ->
                updateUIFromState(stateInfo.state, stateInfo.message, stateInfo.recognizedText, stateInfo.wakeConfidence)
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

    private fun updateUIFromState(state: PipelineState, message: String?, recognizedText: String = "", wakeConfidence: Float = 0f) {
        runOnUiThread {
            try {
                when (state) {
                    PipelineState.INITIALIZING -> {
                        btnSend.visibility = View.INVISIBLE
                    }
                    PipelineState.IDLE -> {
                        resetUI()
                        stopAsrPulseAnimation()
                        // Show toast if init failed (message indicates failure)
                        if (message?.contains("暂不可用") == true || message?.contains("初始化") == true) {
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                        }
                    }
                    PipelineState.WAKEWORD_DETECTED -> {
                        btnSend.visibility = View.INVISIBLE
                        // Haptic feedback on wake word detection
                        triggerHapticFeedback()
                        // Show wake success indicator with confidence
                        showWakeSuccessIndicator(message ?: getString(R.string.state_wakeword_detected), wakeConfidence)
                    }
                    PipelineState.LISTENING -> {
                        btnSend.visibility = View.INVISIBLE
                        // Start pulse animation
                        startAsrPulseAnimation()
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
                        stopAsrPulseAnimation()
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
            } catch (e: Exception) {
                Timber.e(e, "Error updating UI for state $state")
            }
        }
    }

    /**
     * Trigger haptic feedback (vibration) for wake word detection
     */
    private fun triggerHapticFeedback() {
        try {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(100)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w("Haptic feedback failed: ${e.message}")
        }
    }

    /**
     * Start pulse animation on the ASR pulse dot (for LISTENING state)
     */
    private fun startAsrPulseAnimation() {
        asrPulseDot.visibility = View.VISIBLE
        // Cancel any existing animators first
        pulseAnimatorX?.cancel()
        pulseAnimatorY?.cancel()

        pulseAnimatorX = ObjectAnimator.ofFloat(asrPulseDot, "scaleX", 1f, 1.5f, 1f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
        pulseAnimatorY = ObjectAnimator.ofFloat(asrPulseDot, "scaleY", 1f, 1.5f, 1f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    /**
     * Stop the pulse animation
     */
    private fun stopAsrPulseAnimation() {
        pulseAnimatorX?.cancel()
        pulseAnimatorY?.cancel()
        pulseAnimatorX = null
        pulseAnimatorY = null
        asrPulseDot.animate().cancel()
        asrPulseDot.scaleX = 1f
        asrPulseDot.scaleY = 1f
    }

    // ==================== ASR Card ====================

    private fun showAsrCard() {
        asrResultCard.visibility = View.VISIBLE
        asrResultCard.alpha = 1f
    }

    private fun hideAsrCard() {
        asrResultCard.visibility = View.GONE
    }

    private fun showWakeSuccessIndicator(message: String, confidence: Float = 0f) {
        asrResultCard.visibility = View.VISIBLE
        val displayMessage = if (confidence > 0) {
            "$message (置信度: %.2f)".format(confidence)
        } else {
            message
        }
        tvAsrResult.text = displayMessage
        asrResultCard.alpha = 1f
        // Change card background to indicate success
        asrResultCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.primary))
    }

    private fun updateAsrText(text: String, isFinal: Boolean) {
        // Reset card background to normal when recording starts
        asrResultCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface))
        tvAsrResult.text = text
    }

    // ==================== Message Handling ====================

    private fun setupConversationPagination() {
        conversationRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                // 向上滚动时检查是否到达顶部
                if (dy < 0 && !chatMessageAdapter.isLoadingMore) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val firstVisible = layoutManager.findFirstVisibleItemPosition()
                    if (firstVisible <= 1) { // <=1 因为可能有 loading 占位符
                        loadMoreConversationHistory()
                    }
                }
            }
        })

        // Initialize RecyclerView
        conversationRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
            adapter = chatMessageAdapter
        }
    }

    private fun loadInitialConversationHistory() {
        lifecycleScope.launch {
            isLoadingHistory = true
            try {
                val latestMessages = withContext(Dispatchers.IO) {
                    chatMessageDao.getLatestMessages(HISTORY_PAGE_SIZE)
                }

                if (latestMessages.isEmpty()) {
                    tvEmptyHint.visibility = View.VISIBLE
                    conversationRecyclerView.visibility = View.GONE
                    hasMoreHistory = false
                    oldestLoadedMessageId = null
                    oldestLoadedMessageCreatedAt = null
                    return@launch
                }

                tvEmptyHint.visibility = View.GONE
                conversationRecyclerView.visibility = View.VISIBLE

                chatMessageAdapter.clearMessages()
                chatMessageAdapter.addMessages(latestMessages.reversed(), atEnd = true)

                val oldestMessage = latestMessages.last()
                oldestLoadedMessageId = oldestMessage.id
                oldestLoadedMessageCreatedAt = oldestMessage.createdAt
                hasMoreHistory = latestMessages.size >= HISTORY_PAGE_SIZE

                // 初始加载滚动到底部显示最新消息
                conversationRecyclerView.scrollToPosition(chatMessageAdapter.itemCount - 1)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load initial conversation history")
                Toast.makeText(this@MainActivity, R.string.error_chat_history_load_failed, Toast.LENGTH_SHORT).show()
            } finally {
                isLoadingHistory = false
            }
        }
    }

    private fun loadMoreConversationHistory() {
        val oldestId = oldestLoadedMessageId ?: return
        val oldestCreatedAt = oldestLoadedMessageCreatedAt ?: return
        if (chatMessageAdapter.isLoadingMore || !hasMoreHistory) return

        chatMessageAdapter.isLoadingMore = true

        lifecycleScope.launch {
            try {
                // 在 IO 线程预加载数据
                val olderMessages = withContext(Dispatchers.IO) {
                    chatMessageDao.getMessagesBefore(
                        beforeCreatedAt = oldestCreatedAt,
                        beforeId = oldestId,
                        limit = HISTORY_PAGE_SIZE
                    )
                }

                if (olderMessages.isEmpty()) {
                    hasMoreHistory = false
                    withContext(Dispatchers.Main) {
                        chatMessageAdapter.isLoadingMore = false
                    }
                    return@launch
                }

                // 回到主线程更新 UI
                withContext(Dispatchers.Main) {
                    chatMessageAdapter.addMessages(olderMessages, atEnd = false)

                    val newOldest = olderMessages.last()
                    oldestLoadedMessageId = newOldest.id
                    oldestLoadedMessageCreatedAt = newOldest.createdAt
                    hasMoreHistory = olderMessages.size >= HISTORY_PAGE_SIZE

                    chatMessageAdapter.isLoadingMore = false

                    // 恢复滚动位置
                    val layoutManager = conversationRecyclerView.layoutManager as LinearLayoutManager
                    layoutManager.scrollToPositionWithOffset(olderMessages.size + 1, 0)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load more conversation history")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, R.string.error_load_history_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        val createdAt = System.currentTimeMillis()
        lifecycleScope.launch {
            var newId: Long = 0L
            try {
                newId = withContext(Dispatchers.IO) {
                    chatMessageDao.insertMessage(
                        ChatMessageEntity(
                            text = text,
                            isUser = isUser,
                            createdAt = createdAt
                        )
                    )
                }

                if (oldestLoadedMessageId == null || oldestLoadedMessageCreatedAt == null) {
                    oldestLoadedMessageId = newId
                    oldestLoadedMessageCreatedAt = createdAt
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to persist chat message")
                Toast.makeText(this@MainActivity, "聊天记录保存失败", Toast.LENGTH_SHORT).show()
            }

            tvEmptyHint.visibility = View.GONE
            conversationRecyclerView.visibility = View.VISIBLE
            chatMessageAdapter.addMessage(
                ChatMessageEntity(
                    id = newId,
                    text = text,
                    isUser = isUser,
                    createdAt = createdAt
                )
            )
            conversationRecyclerView.scrollToPosition(chatMessageAdapter.itemCount - 1)
        }
    }

    private fun showDeleteMessageDialog(messageId: Long, messageView: View) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_message_title)
            .setMessage(R.string.dialog_delete_message_content)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                deleteMessage(messageId, messageView)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun deleteMessage(messageId: Long, messageView: View) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    chatMessageDao.deleteMessage(messageId)
                }
                // 从适配器移除消息
                val position = (0 until chatMessageAdapter.itemCount).firstOrNull { i ->
                    chatMessageAdapter.getMessageAt(i).id == messageId
                }
                if (position != null) {
                    chatMessageAdapter.removeMessage(position)
                }
                // 检查是否为空
                if (chatMessageAdapter.itemCount == 0) {
                    tvEmptyHint.visibility = View.VISIBLE
                    conversationRecyclerView.visibility = View.GONE
                    hasMoreHistory = true
                    oldestLoadedMessageId = null
                    oldestLoadedMessageCreatedAt = null
                }
                Toast.makeText(this@MainActivity, R.string.message_deleted, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete message")
                Toast.makeText(this@MainActivity, R.string.error_delete_failed, Toast.LENGTH_SHORT).show()
            }
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
        if (!VoiceAssistantService.isServiceRunning) {
            val intent = Intent(this, VoiceAssistantService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            Timber.d("VoiceAssistantService already running, skip duplicate start")
        }

        // Start KWS listening for wake word detection
        voicePipeline.start()

        // Start ASR/TTS background initialization after service starts
        voicePipeline.initializeInBackground()
    }

    /**
     * 测试 LLM 连接状态
     */
    private fun testLlmConnection() {
        val llmConfigured = configHolder.llmBaseUrl.isNotEmpty() && configHolder.llmApiKey.isNotEmpty()
        if (!llmConfigured) {
            tvProvider.text = getString(R.string.llm_not_configured)
            updateStatus(false)
            return
        }

        tvProvider.text = getString(R.string.llm_checking)
        llmConnectionCheckJob?.cancel()
        llmConnectionCheckJob = lifecycleScope.launch {
            try {
                val result = llmRepository.chat("ping")
                val isOnline = result.isSuccess
                tvProvider.text = if (isOnline) getString(R.string.llm_connected) else getString(R.string.llm_not_connected)
                updateStatus(isOnline)
            } catch (e: Exception) {
                Timber.e(e, "LLM connection test failed")
                tvProvider.text = getString(R.string.llm_not_connected)
                updateStatus(false)
            }
        }
    }

    private fun startLlmStatusPolling() {
        llmStatusPollingJob?.cancel()
        llmStatusPollingJob = lifecycleScope.launch {
            while (isActive) {
                testLlmConnection()
                delay(30_000)
            }
        }
    }

    /**
     * 测试 Jellyfin 连接状态
     */
    private fun testJellyfinConnection() {
        lifecycleScope.launch {
            val jellyfinConfigured = configHolder.jellyfinUrl.isNotEmpty() &&
                configHolder.jellyfinApiKey.isNotEmpty()
            if (!jellyfinConfigured) {
                tvJellyfinStatus.text = getString(R.string.jellyfin_not_configured)
                jellyfinStatusDot.setBackgroundResource(R.drawable.circle_status_offline)
                return@launch
            }

            // 重新加载 Jellyfin 配置，确保使用最新地址
            jellyfinClient.reload(configHolder.jellyfinUrl, configHolder.jellyfinApiKey)

            tvJellyfinStatus.text = getString(R.string.jellyfin_connecting)
            try {
                val result = jellyfinClient.testConnection()
                val isOnline = result.isSuccess
                tvJellyfinStatus.text = if (isOnline) getString(R.string.jellyfin_connected) else getString(R.string.jellyfin_not_connected)
                jellyfinStatusDot.setBackgroundResource(
                    if (isOnline) R.drawable.circle_status_online else R.drawable.circle_status_offline
                )
            } catch (e: Exception) {
                Timber.e(e, "Jellyfin connection test failed")
                tvJellyfinStatus.text = getString(R.string.jellyfin_not_connected)
                jellyfinStatusDot.setBackgroundResource(R.drawable.circle_status_offline)
            }
        }
    }

    private fun refreshSelectedPlaybackDeviceDisplay() {
        val prefs = getSharedPreferences("voice_assistant_prefs", Context.MODE_PRIVATE)
        val deviceName = prefs.getString(PREF_SELECTED_DEVICE_NAME, null)
        tvSelectedPlaybackDevice.text = if (deviceName.isNullOrBlank()) {
            getString(R.string.playback_device_not_selected)
        } else {
            getString(R.string.playback_device_selected, deviceName)
        }
    }
}
