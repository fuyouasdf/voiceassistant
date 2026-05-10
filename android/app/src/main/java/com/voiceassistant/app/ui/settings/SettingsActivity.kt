/*
 * Copyright 2024 Voice Assistant Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.voiceassistant.app.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.card.MaterialCardView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import android.widget.ScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.voiceassistant.app.R
import com.voiceassistant.app.di.ConfigHolder
import com.voiceassistant.data.LLMPreset
import com.voiceassistant.core.ConversationContextManager
import com.voiceassistant.core.pipeline.VoicePipeline
import com.voiceassistant.data.remote.JellyfinClient
import com.voiceassistant.data.repository.SettingsRepository
import com.voiceassistant.domain.repository.LLMRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
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

    @Inject
    lateinit var conversationContextManager: ConversationContextManager

    // Music Service
    private lateinit var etJellyfinUrl: TextInputEditText
    private lateinit var etJellyfinApiKey: TextInputEditText
    private lateinit var jellyfinStatusDot: View
    private lateinit var tvJellyfinOnlineStatus: TextView
    private lateinit var btnTestJellyfin: MaterialButton
    private lateinit var progressJellyfin: ProgressBar
    private lateinit var tvJellyfinStatus: TextView

    // AI Service
    private lateinit var spinnerLlmPreset: Spinner
    private lateinit var etLlmUrl: TextInputEditText
    private lateinit var etLlmApiKey: TextInputEditText
    private lateinit var etLlmModel: TextInputEditText
    private lateinit var etLlmApiPath: TextInputEditText
    private lateinit var tvLlmFullUrl: TextView
    private lateinit var etLlmSystemPrompt: TextInputEditText
    private lateinit var etLlmRouterPrompt: TextInputEditText
    private lateinit var etLlmCommandPrompt: TextInputEditText
    private lateinit var sliderLlmContextCount: Slider
    private lateinit var tvLlmContextCount: TextView
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
    private lateinit var sliderTtsPitch: Slider
    private lateinit var tvTtsPitch: TextView
    private lateinit var btnSave: MaterialButton

    // Section Collapse
    private lateinit var cardMusicService: MaterialCardView
    private lateinit var cardAiService: MaterialCardView
    private lateinit var cardVoiceSettings: MaterialCardView
    private lateinit var btnCollapseMusic: ImageButton
    private lateinit var btnCollapseAi: ImageButton
    private lateinit var btnCollapseVoice: ImageButton
    private lateinit var rootLayout: View
    private lateinit var rootScrollView: ScrollView
    private lateinit var contentLayout: View

    private var isLoading = false
    private var cachedHorizontalPadding: Int = 0
    private var currentApiPath: String = "/v1/responses"  // 当前 LLM API 路径

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
        rootLayout = findViewById(R.id.rootLayout)
        rootScrollView = findViewById(R.id.rootScrollView)
        contentLayout = findViewById(R.id.contentLayout)
        cachedHorizontalPadding = resources.getDimensionPixelSize(R.dimen.settings_horizontal_padding)

        // Music Service - Jellyfin
        etJellyfinUrl = findViewById(R.id.etJellyfinUrl)
        etJellyfinApiKey = findViewById(R.id.etJellyfinApiKey)
        jellyfinStatusDot = findViewById(R.id.jellyfinStatusDot)
        tvJellyfinOnlineStatus = findViewById(R.id.tvJellyfinOnlineStatus)
        btnTestJellyfin = findViewById(R.id.btnTestJellyfin)
        progressJellyfin = findViewById(R.id.progressJellyfin)
        tvJellyfinStatus = findViewById(R.id.tvJellyfinStatus)

        // AI Service
        spinnerLlmPreset = findViewById(R.id.spinnerLlmPreset)
        etLlmUrl = findViewById(R.id.etLlmUrl)
        etLlmApiKey = findViewById(R.id.etLlmApiKey)
        etLlmModel = findViewById(R.id.etLlmModel)
        etLlmApiPath = findViewById(R.id.etLlmApiPath)
        tvLlmFullUrl = findViewById(R.id.tvLlmFullUrl)
        etLlmSystemPrompt = findViewById(R.id.etLlmSystemPrompt)
        etLlmRouterPrompt = findViewById(R.id.etLlmRouterPrompt)
        etLlmCommandPrompt = findViewById(R.id.etLlmCommandPrompt)
        sliderLlmContextCount = findViewById(R.id.sliderLlmContextCount)
        tvLlmContextCount = findViewById(R.id.tvLlmContextCount)
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
        sliderTtsPitch = findViewById(R.id.sliderTtsPitch)
        tvTtsPitch = findViewById(R.id.tvTtsPitch)

        // Save Button
        btnSave = findViewById(R.id.btnSave)

        // Section Collapse
        cardMusicService = findViewById(R.id.cardMusicService)
        cardAiService = findViewById(R.id.cardAiService)
        cardVoiceSettings = findViewById(R.id.cardVoiceSettings)
        btnCollapseMusic = findViewById(R.id.btnCollapseMusic)
        btnCollapseAi = findViewById(R.id.btnCollapseAi)
        btnCollapseVoice = findViewById(R.id.btnCollapseVoice)
    }

    private fun setupInsets() {
        // Root layout handles system bars insets
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, 0)
            windowInsets
        }

        // Content layout handles IME insets for keyboard
        ViewCompat.setOnApplyWindowInsetsListener(contentLayout) { view, windowInsets ->
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomPadding = if (ime.bottom > 0) ime.bottom + 16 else 16
            view.setPadding(
                cachedHorizontalPadding,
                view.paddingTop,
                cachedHorizontalPadding,
                bottomPadding
            )
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

        sliderTtsPitch.addOnChangeListener { _, value, _ ->
            if (isLoading) return@addOnChangeListener
            tvTtsPitch.text = String.format("%.1fx", value)
        }

        sliderLlmContextCount.addOnChangeListener { _, value, _ ->
            if (isLoading) return@addOnChangeListener
            tvLlmContextCount.text = value.toInt().toString()
        }

        // LLM Preset Spinner
        setupLlmPresetSpinner()

        // Jellyfin test button
        btnTestJellyfin.setOnClickListener {
            if (isLoading) {
                Toast.makeText(this, R.string.settings_loading_hint, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            testJellyfinConnection()
        }

        // LLM test button
        btnTestLlm.setOnClickListener {
            if (isLoading) {
                Toast.makeText(this, R.string.settings_loading_hint, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            testLlmConnection()
        }

        // Section collapse/expand listeners
        btnCollapseMusic.setOnClickListener {
            toggleSection(cardMusicService, btnCollapseMusic)
        }
        btnCollapseAi.setOnClickListener {
            toggleSection(cardAiService, btnCollapseAi)
        }
        btnCollapseVoice.setOnClickListener {
            toggleSection(cardVoiceSettings, btnCollapseVoice)
        }

        // Save button
        btnSave.setOnClickListener {
            if (isLoading) {
                Toast.makeText(this, R.string.settings_loading_hint, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveSettings()
        }
    }

    private fun toggleSection(card: MaterialCardView, button: ImageButton) {
        if (card.visibility == View.VISIBLE) {
            // Collapse
            card.visibility = View.GONE
            button.setImageResource(R.drawable.ic_arrow_down)
        } else {
            // Expand
            card.visibility = View.VISIBLE
            button.setImageResource(R.drawable.ic_arrow_up)
        }
    }

    /**
     * 设置 LLM 预设 Spinner
     */
    private fun setupLlmPresetSpinner() {
        val presetNames = LLMPreset.PRESETS.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, presetNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLlmPreset.adapter = adapter

        // 设置预设匹配当前配置（优先匹配 baseUrl，其次匹配 baseUrl + model）
        val currentUrl = etLlmUrl.text.toString()
        val currentModel = etLlmModel.text.toString()
        val matchingIndex = when {
            // 优先精确匹配 baseUrl + model
            currentUrl.isNotEmpty() -> LLMPreset.PRESETS.indexOfFirst { preset ->
                preset.baseUrl == currentUrl && preset.defaultModel == currentModel
            }.takeIf { it >= 0 }
            // 其次只匹配 baseUrl
            currentUrl.isNotEmpty() -> LLMPreset.PRESETS.indexOfFirst { preset ->
                preset.baseUrl == currentUrl
            }.takeIf { it >= 0 }
            // 默认自定义
            else -> 0
        } ?: 0
        spinnerLlmPreset.setSelection(matchingIndex)
        currentApiPath = LLMPreset.PRESETS[matchingIndex].apiPath

        // Spinner 选择监听器
        spinnerLlmPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isLoading) return
                val selectedPreset = LLMPreset.PRESETS[position]
                currentApiPath = selectedPreset.apiPath  // 保存 API 路径
                if (selectedPreset.name == "自定义") {
                    // 自定义选项：清空字段让用户自行填写
                    etLlmUrl.setText("")
                    etLlmModel.setText("")
                    etLlmApiPath.setText("")
                } else {
                    // 选择预设时：覆盖 URL 和 API 路径，模型仅在为空时填充
                    etLlmUrl.setText(selectedPreset.baseUrl)
                    etLlmApiPath.setText(selectedPreset.apiPath)
                    if (etLlmModel.text.toString().isEmpty()) {
                        etLlmModel.setText(selectedPreset.defaultModel)
                    }
                }
                updateFullUrlPreview()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // 不做任何操作
            }
        }

        // 监听 URL 和 API Path 输入框变化，更新完整路径预览
        val textWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!isLoading) updateFullUrlPreview()
            }
        }
        etLlmUrl.addTextChangedListener(textWatcher)
        etLlmApiPath.addTextChangedListener(textWatcher)
    }

    /**
     * 更新完整 URL 预览
     */
    private fun updateFullUrlPreview() {
        val baseUrl = etLlmUrl.text.toString().trim().trimEnd('/')
        val apiPath = etLlmApiPath.text.toString().trim()
        if (baseUrl.isNotEmpty() && apiPath.isNotEmpty()) {
            tvLlmFullUrl.text = "完整地址: $baseUrl$apiPath"
            tvLlmFullUrl.visibility = android.view.View.VISIBLE
        } else {
            tvLlmFullUrl.visibility = android.view.View.GONE
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
            currentApiPath = settingsRepository.getLLMApiPath()  // 加载 API 路径
            etLlmApiPath.setText(currentApiPath)  // 显示 API 路径到输入框
            etLlmSystemPrompt.setText(settingsRepository.getLLMSystemPrompt())
            etLlmRouterPrompt.setText(settingsRepository.getLLMRouterPrompt())
            etLlmCommandPrompt.setText(settingsRepository.getLLMCommandPrompt())

            // Load LLM Context Count
            val contextCount = settingsRepository.getLLMContextCount()
            sliderLlmContextCount.value = contextCount.toFloat()
            tvLlmContextCount.text = contextCount.toString()
            conversationContextManager.updateMaxContextCount(contextCount)

            // Test LLM connection on load
            testLlmConnectionOnLoad()

            // 更新完整 URL 预览
            updateFullUrlPreview()

            // Load Voice Settings
            val wakeSensitivity = settingsRepository.getWakeSensitivity()
            sliderWakeSensitivity.value = wakeSensitivity
            tvWakeSensitivity.text = String.format("%.1f", wakeSensitivity)

            switchTtsEnabled.isChecked = settingsRepository.getTtsEnabled()

            val ttsSpeed = settingsRepository.getTtsSpeed()
            sliderTtsSpeed.value = ttsSpeed
            tvTtsSpeed.text = String.format("%.1fx", ttsSpeed)

            val ttsPitch = settingsRepository.getTtsPitch()
            sliderTtsPitch.value = ttsPitch
            tvTtsPitch.text = String.format("%.1fx", ttsPitch)
        }
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
                    Toast.makeText(this@SettingsActivity, R.string.jellyfin_config_required, Toast.LENGTH_SHORT).show()
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
                val apiPath = etLlmApiPath.text.toString()

                if (url.isEmpty() || apiKey.isEmpty() || model.isEmpty()) {
                    progressLlm.visibility = View.GONE
                    btnTestLlm.isEnabled = true
                    tvLlmStatus.text = getString(R.string.settings_llm_offline)
                    tvLlmStatus.setTextColor(getColor(R.color.status_offline))
                    tvLlmStatus.visibility = View.VISIBLE
                    updateLlmOnlineStatus(false)
                    Toast.makeText(this@SettingsActivity, R.string.llm_config_required, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // 使用 UI 字段的值直接测试，不保存到 SettingsRepository
                val result = llmRepository.chatWithConfig("Hello", url, apiPath, apiKey, model)
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
                    Toast.makeText(this@SettingsActivity, getString(R.string.settings_llm_offline) + ": " + (result.exceptionOrNull()?.message ?: "Unknown error"), Toast.LENGTH_SHORT).show()
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
                settingsRepository.setLLMApiPath(etLlmApiPath.text.toString())  // 保存 API 路径
                settingsRepository.setLLMSystemPrompt(etLlmSystemPrompt.text.toString())
                settingsRepository.setLLMRouterPrompt(etLlmRouterPrompt.text.toString())
                settingsRepository.setLLMCommandPrompt(etLlmCommandPrompt.text.toString())
                settingsRepository.setLLMContextCount(sliderLlmContextCount.value.toInt())
                conversationContextManager.updateMaxContextCount(sliderLlmContextCount.value.toInt())

                // Save Voice Settings
                settingsRepository.setWakeSensitivity(sliderWakeSensitivity.value)
                settingsRepository.setTtsSpeed(sliderTtsSpeed.value)
                settingsRepository.setTtsPitch(sliderTtsPitch.value)
                settingsRepository.setTtsEnabled(switchTtsEnabled.isChecked)

                // Update ConfigHolder for immediate use
                configHolder.settingsRepository = settingsRepository
                configHolder.reload()

                // Reload JellyfinClient with new server config and clear caches
                // 直接从 settingsRepository 获取最新值，避免依赖 configHolder 的缓存
                val newJellyfinUrl = etJellyfinUrl.text.toString()
                val newJellyfinApiKey = etJellyfinApiKey.text.toString()
                Timber.d("saveSettings: reloading JellyfinClient with url=$newJellyfinUrl")
                jellyfinClient.reload(newJellyfinUrl, newJellyfinApiKey)

                // Hot apply wake sensitivity immediately (no restart required)
                voicePipeline.applyWakeSensitivity(sliderWakeSensitivity.value)

                // 保存后检测 LLM 连接状态
                testLlmConnection()

                Toast.makeText(this@SettingsActivity, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, getString(R.string.error_save_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
