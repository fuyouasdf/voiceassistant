package com.voiceassistant.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.ScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.voiceassistant.app.R
import com.voiceassistant.app.di.ConfigHolder
import com.voiceassistant.core.pipeline.VoicePipeline
import com.voiceassistant.core.pipeline.WakeWord
import com.voiceassistant.data.remote.JellyfinClient
import com.voiceassistant.data.repository.SettingsRepository
import com.voiceassistant.domain.repository.LLMRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var configHolder: ConfigHolder

    @Inject
    lateinit var llmRepository: LLMRepository

    @Inject
    lateinit var jellyfinClient: JellyfinClient

    @Inject
    lateinit var voicePipeline: VoicePipeline

    // Music Service
    private lateinit var etJellyfinUrl: TextInputEditText
    private lateinit var etJellyfinApiKey: TextInputEditText
    private lateinit var jellyfinStatusDot: View
    private lateinit var tvJellyfinOnlineStatus: TextView
    private lateinit var btnTestJellyfin: MaterialButton
    private lateinit var progressJellyfin: ProgressBar
    private lateinit var tvJellyfinStatus: TextView

    // AI Service
    private lateinit var etLlmUrl: TextInputEditText
    private lateinit var etLlmApiKey: TextInputEditText
    private lateinit var etLlmModel: TextInputEditText
    private lateinit var etLlmSystemPrompt: TextInputEditText
    private lateinit var llmStatusDot: View
    private lateinit var tvLlmOnlineStatus: TextView
    private lateinit var btnTestLlm: MaterialButton
    private lateinit var progressLlm: ProgressBar
    private lateinit var tvLlmStatus: TextView

    // Voice Settings
    private lateinit var sliderWakeSensitivity: Slider
    private lateinit var tvWakeSensitivity: TextView
    private lateinit var switchTtsEnabled: SwitchMaterial
    private lateinit var sliderTtsSpeed: Slider
    private lateinit var tvTtsSpeed: TextView
    private lateinit var rvWakeWords: RecyclerView
    private lateinit var btnAddWakeWord: MaterialButton
    private lateinit var btnTestKws: MaterialButton
    private lateinit var tvKwsStatus: TextView

    private lateinit var btnSave: MaterialButton
    private lateinit var rootScrollView: ScrollView
    private lateinit var contentLayout: View

    private var isLoading = false
    private val wakeWordsList = mutableListOf<WakeWord>()
    private lateinit var wakeWordAdapter: WakeWordAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        initViews()
        setupInsets()
        setupListeners()
        showLoadingState()

        lifecycleScope.launch {
            loadSettings()
            hideLoadingState()
        }
    }

    private fun showLoadingState() {
        isLoading = true
        btnSave.isEnabled = false
    }

    private fun hideLoadingState() {
        isLoading = false
        btnSave.isEnabled = true
    }

    private fun initViews() {
        rootScrollView = findViewById(R.id.rootScrollView)
        contentLayout = findViewById(R.id.contentLayout)

        // Music Service - Jellyfin
        etJellyfinUrl = findViewById(R.id.etJellyfinUrl)
        etJellyfinApiKey = findViewById(R.id.etJellyfinApiKey)
        jellyfinStatusDot = findViewById(R.id.jellyfinStatusDot)
        tvJellyfinOnlineStatus = findViewById(R.id.tvJellyfinOnlineStatus)
        btnTestJellyfin = findViewById(R.id.btnTestJellyfin)
        progressJellyfin = findViewById(R.id.progressJellyfin)
        tvJellyfinStatus = findViewById(R.id.tvJellyfinStatus)

        // AI Service
        etLlmUrl = findViewById(R.id.etLlmUrl)
        etLlmApiKey = findViewById(R.id.etLlmApiKey)
        etLlmModel = findViewById(R.id.etLlmModel)
        etLlmSystemPrompt = findViewById(R.id.etLlmSystemPrompt)
        llmStatusDot = findViewById(R.id.llmStatusDot)
        tvLlmOnlineStatus = findViewById(R.id.tvLlmOnlineStatus)
        btnTestLlm = findViewById(R.id.btnTestLlm)
        progressLlm = findViewById(R.id.progressLlm)
        tvLlmStatus = findViewById(R.id.tvLlmStatus)

        // Voice Settings
        sliderWakeSensitivity = findViewById(R.id.sliderWakeSensitivity)
        tvWakeSensitivity = findViewById(R.id.tvWakeSensitivity)
        switchTtsEnabled = findViewById(R.id.switchTtsEnabled)
        sliderTtsSpeed = findViewById(R.id.sliderTtsSpeed)
        tvTtsSpeed = findViewById(R.id.tvTtsSpeed)
        rvWakeWords = findViewById(R.id.rvWakeWords)
        btnAddWakeWord = findViewById(R.id.btnAddWakeWord)
        btnTestKws = findViewById(R.id.btnTestKws)
        tvKwsStatus = findViewById(R.id.tvKwsStatus)

        // Initialize wake words RecyclerView
        wakeWordAdapter = WakeWordAdapter(
            wakeWords = wakeWordsList,
            onEdit = { position, wakeWord -> showEditWakeWordDialog(position, wakeWord) },
            onDelete = { position -> wakeWordAdapter.removeAt(position) }
        )
        rvWakeWords.layoutManager = LinearLayoutManager(this)
        rvWakeWords.adapter = wakeWordAdapter

        // Save Button
        btnSave = findViewById(R.id.btnSave)
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootScrollView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            contentLayout.setPadding(16, insets.top + 16, 16, insets.bottom + 16)
            windowInsets
        }
    }

    private fun setupListeners() {
        // Slider listeners for real-time value display
        sliderWakeSensitivity.addOnChangeListener { _, value, _ ->
            if (isLoading) return@addOnChangeListener
            tvWakeSensitivity.text = String.format("%.1f", value)
        }

        sliderTtsSpeed.addOnChangeListener { _, value, _ ->
            if (isLoading) return@addOnChangeListener
            tvTtsSpeed.text = String.format("%.1fx", value)
        }

        // Jellyfin test button
        btnTestJellyfin.setOnClickListener {
            if (isLoading) {
                Toast.makeText(this, "页面加载中，请稍候", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            testJellyfinConnection()
        }

        // LLM test button
        btnTestLlm.setOnClickListener {
            if (isLoading) {
                Toast.makeText(this, "页面加载中，请稍候", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            testLlmConnection()
        }

        // Add wake word button
        btnAddWakeWord.setOnClickListener {
            showAddWakeWordDialog()
        }

        // KWS self test button
        btnTestKws.setOnClickListener {
            showKwsDiagnostics()
        }

        // Save button
        btnSave.setOnClickListener {
            if (isLoading) {
                Toast.makeText(this, "页面加载中，请稍候", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveSettings()
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            // Load Music Service settings - Jellyfin
            etJellyfinUrl.setText(settingsRepository.getJellyfinUrl())
            etJellyfinApiKey.setText(settingsRepository.getJellyfinApiKey())

            // Test Jellyfin connection on load
            testJellyfinConnectionOnLoad()

            // Load AI Service settings
            etLlmUrl.setText(settingsRepository.getLLMBaseUrl())
            etLlmApiKey.setText(settingsRepository.getLLMApiKey())
            etLlmModel.setText(settingsRepository.getLLMModel())
            etLlmSystemPrompt.setText(settingsRepository.getLLMSystemPrompt())

            // Test LLM connection on load
            testLlmConnectionOnLoad()

            // Load Voice Settings
            val wakeSensitivity = settingsRepository.getWakeSensitivity()
            sliderWakeSensitivity.value = wakeSensitivity
            tvWakeSensitivity.text = String.format("%.1f", wakeSensitivity)

            switchTtsEnabled.isChecked = settingsRepository.getTtsEnabled()

            val ttsSpeed = settingsRepository.getTtsSpeed()
            sliderTtsSpeed.value = ttsSpeed
            tvTtsSpeed.text = String.format("%.1fx", ttsSpeed)

            // Load Wake Words
            val storedWords = settingsRepository.getWakeWords()
            wakeWordsList.clear()
            storedWords.forEach { line ->
                val parts = line.split(":", limit = 2)
                wakeWordsList.add(WakeWord(
                    keyword = parts[0].trim(),
                    response = parts.getOrNull(1)?.trim() ?: "我在"
                ))
            }
            wakeWordAdapter.notifyDataSetChanged()

            // Show initial KWS diagnostics
            showKwsDiagnostics()
        }
    }

    private fun showKwsDiagnostics() {
        val diagnostics = voicePipeline.getKwsDiagnostics()
        val timeText = diagnostics.lastWakeTimestampMs?.let {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
        } ?: "无"
        val confidenceText = diagnostics.lastWakeConfidence?.let { String.format("%.2f", it) } ?: "无"
        val keywordText = diagnostics.lastWakeKeyword.ifBlank { "无" }
        val initText = if (diagnostics.isInitialized) "已初始化" else "未初始化"
        val startText = if (diagnostics.isStarted) "已启动" else "未启动"
        val failedText = if (diagnostics.initFailed) "是" else "否"

        tvKwsStatus.text = "KWS状态: $initText, $startText, 初始化失败: $failedText\n" +
            "阈值: ${String.format("%.2f", diagnostics.currentThreshold)}, 最近触发词: $keywordText\n" +
            "最近触发时间: $timeText, 最近置信度: $confidenceText"
        tvKwsStatus.visibility = View.VISIBLE
    }

    private fun showAddWakeWordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_wake_word, null)
        val etKeyword = dialogView.findViewById<TextInputEditText>(R.id.etWakeWordKeyword)
        val etResponse = dialogView.findViewById<TextInputEditText>(R.id.etWakeWordResponse)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_wake_words_add)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val keyword = etKeyword.text.toString().trim()
                val response = etResponse.text.toString().trim().ifEmpty { "我在" }
                if (keyword.isNotEmpty()) {
                    wakeWordsList.add(WakeWord(keyword, response))
                    wakeWordAdapter.notifyItemInserted(wakeWordsList.size - 1)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditWakeWordDialog(position: Int, wakeWord: WakeWord) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_wake_word, null)
        val etKeyword = dialogView.findViewById<TextInputEditText>(R.id.etWakeWordKeyword)
        val etResponse = dialogView.findViewById<TextInputEditText>(R.id.etWakeWordResponse)
        etKeyword.setText(wakeWord.keyword)
        etResponse.setText(wakeWord.response)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_wake_words_edit)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val keyword = etKeyword.text.toString().trim()
                val response = etResponse.text.toString().trim().ifEmpty { "我在" }
                if (keyword.isNotEmpty()) {
                    wakeWordsList[position] = WakeWord(keyword, response)
                    wakeWordAdapter.notifyItemChanged(position)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun testJellyfinConnection() {
        btnTestJellyfin.isEnabled = false
        progressJellyfin.visibility = View.VISIBLE
        tvJellyfinStatus.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val url = etJellyfinUrl.text.toString()
                val apiKey = etJellyfinApiKey.text.toString()

                if (url.isEmpty() || apiKey.isEmpty()) {
                    progressJellyfin.visibility = View.GONE
                    btnTestJellyfin.isEnabled = true
                    tvJellyfinStatus.text = getString(R.string.settings_jellyfin_offline)
                    tvJellyfinStatus.setTextColor(getColor(R.color.status_offline))
                    tvJellyfinStatus.visibility = View.VISIBLE
                    updateJellyfinOnlineStatus(false)
                    Toast.makeText(this@SettingsActivity, "请填写完整的 Jellyfin 配置", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val testClient = JellyfinClient(url, apiKey)
                val result = testClient.testConnection()
                progressJellyfin.visibility = View.GONE
                btnTestJellyfin.isEnabled = true

                if (result.isSuccess) {
                    tvJellyfinStatus.text = getString(R.string.settings_jellyfin_online)
                    tvJellyfinStatus.setTextColor(getColor(R.color.status_online))
                    tvJellyfinStatus.visibility = View.VISIBLE
                    updateJellyfinOnlineStatus(true)
                    Toast.makeText(this@SettingsActivity, R.string.settings_jellyfin_online, Toast.LENGTH_SHORT).show()
                } else {
                    tvJellyfinStatus.text = getString(R.string.settings_jellyfin_offline)
                    tvJellyfinStatus.setTextColor(getColor(R.color.status_offline))
                    tvJellyfinStatus.visibility = View.VISIBLE
                    updateJellyfinOnlineStatus(false)
                    Toast.makeText(this@SettingsActivity, getString(R.string.settings_jellyfin_offline) + ": " + result.exceptionOrNull()?.message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressJellyfin.visibility = View.GONE
                btnTestJellyfin.isEnabled = true
                tvJellyfinStatus.text = getString(R.string.settings_jellyfin_offline)
                tvJellyfinStatus.setTextColor(getColor(R.color.status_offline))
                tvJellyfinStatus.visibility = View.VISIBLE
                updateJellyfinOnlineStatus(false)
                Toast.makeText(this@SettingsActivity, getString(R.string.settings_jellyfin_offline) + ": " + e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 测试 Jellyfin 连接（页面加载时静默执行）
     */
    private fun testJellyfinConnectionOnLoad() {
        val url = etJellyfinUrl.text.toString()
        val apiKey = etJellyfinApiKey.text.toString()

        if (url.isEmpty() || apiKey.isEmpty()) {
            updateJellyfinOnlineStatus(false)
            return
        }

        lifecycleScope.launch {
            try {
                val testClient = JellyfinClient(url, apiKey)
                val result = testClient.testConnection()
                updateJellyfinOnlineStatus(result.isSuccess)
            } catch (e: Exception) {
                updateJellyfinOnlineStatus(false)
            }
        }
    }

    /**
     * 更新 Jellyfin 在线状态显示（顶部）
     */
    private fun updateJellyfinOnlineStatus(isOnline: Boolean) {
        runOnUiThread {
            jellyfinStatusDot.setBackgroundResource(
                if (isOnline) R.drawable.circle_status_online else R.drawable.circle_status_offline
            )
            tvJellyfinOnlineStatus.text = if (isOnline) {
                getString(R.string.settings_jellyfin_online)
            } else {
                getString(R.string.settings_jellyfin_offline)
            }
            tvJellyfinOnlineStatus.setTextColor(
                if (isOnline) getColor(R.color.status_online) else getColor(R.color.status_offline)
            )
        }
    }

    private fun testLlmConnection() {
        btnTestLlm.isEnabled = false
        progressLlm.visibility = View.VISIBLE
        tvLlmStatus.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val url = etLlmUrl.text.toString()
                val apiKey = etLlmApiKey.text.toString()
                val model = etLlmModel.text.toString()

                if (url.isEmpty() || apiKey.isEmpty() || model.isEmpty()) {
                    progressLlm.visibility = View.GONE
                    btnTestLlm.isEnabled = true
                    tvLlmStatus.text = getString(R.string.settings_llm_offline)
                    tvLlmStatus.setTextColor(getColor(R.color.status_offline))
                    tvLlmStatus.visibility = View.VISIBLE
                    updateLlmOnlineStatus(false)
                    Toast.makeText(this@SettingsActivity, "请填写完整的 LLM 配置", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val result = llmRepository.chat("Hello")
                progressLlm.visibility = View.GONE
                btnTestLlm.isEnabled = true

                if (result.isSuccess) {
                    tvLlmStatus.text = getString(R.string.settings_llm_online)
                    tvLlmStatus.setTextColor(getColor(R.color.status_online))
                    tvLlmStatus.visibility = View.VISIBLE
                    updateLlmOnlineStatus(true)
                    Toast.makeText(this@SettingsActivity, R.string.settings_llm_online, Toast.LENGTH_SHORT).show()
                } else {
                    tvLlmStatus.text = getString(R.string.settings_llm_offline)
                    tvLlmStatus.setTextColor(getColor(R.color.status_offline))
                    tvLlmStatus.visibility = View.VISIBLE
                    updateLlmOnlineStatus(false)
                    Toast.makeText(this@SettingsActivity, getString(R.string.settings_llm_offline) + ": " + result.exceptionOrNull()?.message, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressLlm.visibility = View.GONE
                btnTestLlm.isEnabled = true
                tvLlmStatus.text = getString(R.string.settings_llm_offline)
                tvLlmStatus.setTextColor(getColor(R.color.status_offline))
                tvLlmStatus.visibility = View.VISIBLE
                updateLlmOnlineStatus(false)
                Toast.makeText(this@SettingsActivity, getString(R.string.settings_llm_offline) + ": " + e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 测试 LLM 连接（页面加载时静默执行）
     */
    private fun testLlmConnectionOnLoad() {
        val url = etLlmUrl.text.toString()
        val apiKey = etLlmApiKey.text.toString()
        val model = etLlmModel.text.toString()

        if (url.isEmpty() || apiKey.isEmpty() || model.isEmpty()) {
            updateLlmOnlineStatus(false)
            return
        }

        lifecycleScope.launch {
            try {
                val result = llmRepository.chat("test")
                updateLlmOnlineStatus(result.isSuccess)
            } catch (e: Exception) {
                updateLlmOnlineStatus(false)
            }
        }
    }

    /**
     * 更新 LLM 在线状态显示（顶部）
     */
    private fun updateLlmOnlineStatus(isOnline: Boolean) {
        runOnUiThread {
            llmStatusDot.setBackgroundResource(
                if (isOnline) R.drawable.circle_status_online else R.drawable.circle_status_offline
            )
            tvLlmOnlineStatus.text = if (isOnline) {
                getString(R.string.settings_llm_online)
            } else {
                getString(R.string.settings_llm_offline)
            }
            tvLlmOnlineStatus.setTextColor(
                if (isOnline) getColor(R.color.status_online) else getColor(R.color.status_offline)
            )
        }
    }

    private fun saveSettings() {
        lifecycleScope.launch {
            try {
                // Save Music Service settings - Jellyfin
                settingsRepository.setJellyfinUrl(etJellyfinUrl.text.toString())
                settingsRepository.setJellyfinApiKey(etJellyfinApiKey.text.toString())

                // Save AI Service settings
                settingsRepository.setLLMBaseUrl(etLlmUrl.text.toString())
                settingsRepository.setLLMApiKey(etLlmApiKey.text.toString())
                settingsRepository.setLLMModel(etLlmModel.text.toString())
                settingsRepository.setLLMSystemPrompt(etLlmSystemPrompt.text.toString())

                // Save Voice Settings
                settingsRepository.setWakeSensitivity(sliderWakeSensitivity.value)
                settingsRepository.setTtsSpeed(sliderTtsSpeed.value)
                settingsRepository.setTtsEnabled(switchTtsEnabled.isChecked)

                // Save Wake Words
                val wakeWordsLines = wakeWordsList.map { "${it.keyword}:${it.response}" }
                settingsRepository.setWakeWords(wakeWordsLines)

                // Update ConfigHolder for immediate use
                configHolder.settingsRepository = settingsRepository
                configHolder.reload()

                // Hot apply wake sensitivity immediately (no restart required)
                voicePipeline.applyWakeSensitivity(sliderWakeSensitivity.value)

                // Reload KWS with new wake words
                voicePipeline.reloadWakeWords(wakeWordsList)

                Toast.makeText(this@SettingsActivity, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
