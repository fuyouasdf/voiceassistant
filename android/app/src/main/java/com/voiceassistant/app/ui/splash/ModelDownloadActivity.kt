package com.voiceassistant.app.ui.splash

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.voiceassistant.app.R
import com.voiceassistant.app.model.ModelDownloadState
import com.voiceassistant.app.model.ModelInitializer
import com.voiceassistant.app.model.ModelStatus
import com.voiceassistant.app.ui.main.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Activity shown on first launch to download models
 */
class ModelDownloadActivity : AppCompatActivity() {
    
    private lateinit var modelInitializer: ModelInitializer
    
    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var btnAction: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        modelInitializer = ModelInitializer(this)
        
        // Check if models already ready
        if (modelInitializer.areModelsReady()) {
            navigateToMain()
            return
        }
        
        setContentView(R.layout.activity_model_download)
        
        initViews()
        observeDownloadState()
        startDownload()
    }
    
    private fun initViews() {
        tvTitle = findViewById(R.id.tvTitle)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)
        btnAction = findViewById(R.id.btnAction)
        
        btnAction.setOnClickListener {
            startDownload()
        }
    }
    
    private fun observeDownloadState() {
        lifecycleScope.launch {
            modelInitializer.downloadStates.collectLatest { states ->
                updateUI(states)
            }
        }
    }
    
    private fun updateUI(states: List<ModelDownloadState>) {
        if (states.isEmpty()) return
        
        val allReady = states.all { it.status == ModelStatus.READY }
        val hasError = states.any { it.status == ModelStatus.ERROR }
        val downloading = states.filter { it.status == ModelStatus.DOWNLOADING || it.status == ModelStatus.EXTRACTING }
        
        when {
            allReady -> {
                tvTitle.text = "模型准备完成"
                tvStatus.text = "所有模型已就绪"
                progressBar.progress = 100
                tvProgress.text = "100%"
                btnAction.text = "开始使用"
                btnAction.setOnClickListener { navigateToMain() }
            }
            hasError -> {
                tvTitle.text = "下载失败"
                tvStatus.text = states.firstOrNull { it.error != null }?.error ?: "未知错误"
                btnAction.text = "重试"
                btnAction.isEnabled = true
            }
            downloading.isNotEmpty() -> {
                val current = downloading.first()
                val totalProgress = states.map { it.progress }.average()
                
                tvTitle.text = "正在下载模型"
                tvStatus.text = when (current.status) {
                    ModelStatus.DOWNLOADING -> "正在下载: ${current.modelInfo.name}"
                    ModelStatus.EXTRACTING -> "正在解压: ${current.modelInfo.name}"
                    else -> "处理中..."
                }
                progressBar.progress = (totalProgress * 100).toInt()
                tvProgress.text = "${(totalProgress * 100).toInt()}%"
                btnAction.isEnabled = false
                btnAction.text = "下载中..."
            }
            else -> {
                tvStatus.text = "准备下载..."
                btnAction.isEnabled = true
            }
        }
    }
    
    private fun startDownload() {
        btnAction.isEnabled = false
        
        lifecycleScope.launch {
            val result = modelInitializer.initialize()
            
            if (result.isSuccess) {
                navigateToMain()
            } else {
                btnAction.isEnabled = true
                tvStatus.text = "下载失败: ${result.exceptionOrNull()?.message}"
            }
        }
    }
    
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
