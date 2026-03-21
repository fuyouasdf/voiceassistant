package com.voiceassistant.app.ui.settings

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.voiceassistant.app.R
import com.voiceassistant.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    // Music Service
    private lateinit var etNavidromeUrl: TextInputEditText
    private lateinit var etNavidromeUsername: TextInputEditText
    private lateinit var etNavidromePassword: TextInputEditText

    // AI Service
    private lateinit var etLlmUrl: TextInputEditText
    private lateinit var etLlmApiKey: TextInputEditText
    private lateinit var etLlmModel: TextInputEditText

    // Voice Settings
    private lateinit var sliderWakeSensitivity: Slider
    private lateinit var tvWakeSensitivity: TextView
    private lateinit var sliderTtsSpeed: Slider
    private lateinit var tvTtsSpeed: TextView

    private lateinit var btnSave: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        setupListeners()
        loadSettings()
    }

    private fun initViews() {
        // Music Service
        etNavidromeUrl = findViewById(R.id.etNavidromeUrl)
        etNavidromeUsername = findViewById(R.id.etNavidromeUsername)
        etNavidromePassword = findViewById(R.id.etNavidromePassword)

        // AI Service
        etLlmUrl = findViewById(R.id.etLlmUrl)
        etLlmApiKey = findViewById(R.id.etLlmApiKey)
        etLlmModel = findViewById(R.id.etLlmModel)

        // Voice Settings
        sliderWakeSensitivity = findViewById(R.id.sliderWakeSensitivity)
        tvWakeSensitivity = findViewById(R.id.tvWakeSensitivity)
        sliderTtsSpeed = findViewById(R.id.sliderTtsSpeed)
        tvTtsSpeed = findViewById(R.id.tvTtsSpeed)

        // Save Button
        btnSave = findViewById(R.id.btnSave)
    }

    private fun setupListeners() {
        // Slider listeners for real-time value display
        sliderWakeSensitivity.addOnChangeListener { _, value, _ ->
            tvWakeSensitivity.text = String.format("%.1f", value)
        }

        sliderTtsSpeed.addOnChangeListener { _, value, _ ->
            tvTtsSpeed.text = String.format("%.1fx", value)
        }

        // Save button
        btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            // Load Music Service settings
            etNavidromeUrl.setText(settingsRepository.getNavidromeUrl())
            etNavidromeUsername.setText(settingsRepository.getNavidromeUsername())
            etNavidromePassword.setText(settingsRepository.getNavidromePassword())

            // Load AI Service settings
            etLlmUrl.setText(settingsRepository.getLLMBaseUrl())
            etLlmApiKey.setText(settingsRepository.getLLMApiKey())
            etLlmModel.setText(settingsRepository.getLLMModel())

            // Load Voice Settings
            val wakeSensitivity = settingsRepository.getWakeSensitivity()
            sliderWakeSensitivity.value = wakeSensitivity
            tvWakeSensitivity.text = String.format("%.1f", wakeSensitivity)

            val ttsSpeed = settingsRepository.getTtsSpeed()
            sliderTtsSpeed.value = ttsSpeed
            tvTtsSpeed.text = String.format("%.1fx", ttsSpeed)
        }
    }

    private fun saveSettings() {
        lifecycleScope.launch {
            try {
                // Save Music Service settings
                settingsRepository.setNavidromeUrl(etNavidromeUrl.text.toString())
                settingsRepository.setNavidromeUsername(etNavidromeUsername.text.toString())
                settingsRepository.setNavidromePassword(etNavidromePassword.text.toString())

                // Save AI Service settings
                settingsRepository.setLLMBaseUrl(etLlmUrl.text.toString())
                settingsRepository.setLLMApiKey(etLlmApiKey.text.toString())
                settingsRepository.setLLMModel(etLlmModel.text.toString())

                // Save Voice Settings
                settingsRepository.setWakeSensitivity(sliderWakeSensitivity.value)
                settingsRepository.setTtsSpeed(sliderTtsSpeed.value)

                Toast.makeText(this@SettingsActivity, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}