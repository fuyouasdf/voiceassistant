package com.voiceassistant.app.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.voiceassistant.app.R
import com.voiceassistant.app.di.ConfigHolder
import com.voiceassistant.core.dlna.DLNADevice
import com.voiceassistant.core.dlna.DLNAManager
import com.voiceassistant.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var configHolder: ConfigHolder

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

    // DLNA
    private lateinit var etDlnaDeviceName: TextInputEditText
    private lateinit var etDlnaDeviceIp: TextInputEditText
    private lateinit var btnScanDlna: MaterialButton
    private lateinit var progressDlna: ProgressBar

    private lateinit var btnSave: MaterialButton

    private var dlnaManager: DLNAManager? = null
    private var selectedDevice: DLNADevice? = null
    private var isDialogShowing = false
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        setupListeners()
        showLoadingState()

        // 延迟加载设置和初始化 DLNA，避免阻塞 UI
        lifecycleScope.launch {
            delay(50) // 让 UI 先渲染
            initDlnaManager()
            loadSettings()
            hideLoadingState()
        }
    }

    private fun showLoadingState() {
        isLoading = true
        btnSave.isEnabled = false
        btnScanDlna.isEnabled = false
    }

    private fun hideLoadingState() {
        isLoading = false
        btnSave.isEnabled = true
        btnScanDlna.isEnabled = true
    }

    private fun initDlnaManager() {
        dlnaManager = DLNAManager(this)

        // Observe DLNA scanning state
        lifecycleScope.launch {
            dlnaManager?.isScanning?.collectLatest { isScanning ->
                progressDlna.visibility = if (isScanning) View.VISIBLE else View.GONE
                btnScanDlna.isEnabled = !isScanning
                btnScanDlna.text = if (isScanning) getString(R.string.settings_dlna_scanning) else getString(R.string.settings_dlna_scan)
            }
        }

        // Observe DLNA devices
        lifecycleScope.launch {
            dlnaManager?.devices?.collectLatest { devices ->
                if (devices.isNotEmpty() && !isDialogShowing) {
                    // Show device selection dialog immediately when devices found
                    showDeviceSelectionDialog(devices)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dlnaManager?.release()
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

        // DLNA
        etDlnaDeviceName = findViewById(R.id.etDlnaDeviceName)
        etDlnaDeviceIp = findViewById(R.id.etDlnaDeviceIp)
        btnScanDlna = findViewById(R.id.btnScanDlna)
        progressDlna = findViewById(R.id.progressDlna)

        // Save Button
        btnSave = findViewById(R.id.btnSave)
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

        // DLNA scan button
        btnScanDlna.setOnClickListener {
            if (isLoading) {
                Toast.makeText(this, "页面加载中，请稍候", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startDlnaScan()
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

            // Load DLNA settings
            val dlnaDeviceName = settingsRepository.getDLNADeviceName()
            val dlnaDeviceIp = settingsRepository.getDLNADeviceIp()
            val dlnaDeviceUuid = settingsRepository.getDLNADeviceUuid()
            if (dlnaDeviceName.isNotEmpty()) {
                etDlnaDeviceName.setText(dlnaDeviceName)
                etDlnaDeviceIp.setText(dlnaDeviceIp)
                selectedDevice = DLNADevice(
                    uuid = dlnaDeviceUuid,
                    name = dlnaDeviceName,
                    ipAddress = dlnaDeviceIp
                )
            } else {
                etDlnaDeviceName.setText(getString(R.string.settings_dlna_no_device))
            }
        }
    }

    private fun startDlnaScan() {
        val manager = dlnaManager
        if (manager == null) {
            Toast.makeText(this, "DLNA 初始化中，请稍后再试", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            manager.startDiscovery()
            // Show toast after a short delay to indicate scan started
            Toast.makeText(this@SettingsActivity, R.string.settings_dlna_scanning, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeviceSelectionDialog(devices: List<DLNADevice>) {
        if (devices.isEmpty() || isDialogShowing) {
            return
        }

        isDialogShowing = true

        val deviceNames = devices.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_dlna_select_device)
            .setItems(deviceNames) { _, which ->
                val device = devices[which]
                selectDevice(device)
                isDialogShowing = false
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                isDialogShowing = false
            }
            .setOnDismissListener {
                isDialogShowing = false
            }
            .show()
    }

    private fun selectDevice(device: DLNADevice) {
        selectedDevice = device
        etDlnaDeviceName.setText(device.name)
        etDlnaDeviceIp.setText(device.ipAddress)
        Toast.makeText(this, "已选择: ${device.name}", Toast.LENGTH_SHORT).show()
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

                // Save DLNA settings
                selectedDevice?.let { device ->
                    settingsRepository.setDLNADeviceName(device.name)
                    settingsRepository.setDLNADeviceIp(device.ipAddress)
                    settingsRepository.setDLNADeviceUuid(device.uuid)
                }

                // Update ConfigHolder for immediate use
                configHolder.settingsRepository = settingsRepository
                configHolder.reload()

                Toast.makeText(this@SettingsActivity, R.string.settings_saved, Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}