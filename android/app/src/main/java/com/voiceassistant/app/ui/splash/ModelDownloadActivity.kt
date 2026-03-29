package com.voiceassistant.app.ui.splash

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.voiceassistant.app.R
import com.voiceassistant.app.model.ModelDownloadState
import com.voiceassistant.app.model.ModelInitializer
import com.voiceassistant.app.model.ModelStatus
import com.voiceassistant.app.ui.main.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Activity shown on first launch to initialize models from assets
 */
class ModelDownloadActivity : AppCompatActivity() {

    private lateinit var modelInitializer: ModelInitializer

    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var btnAction: Button
    private lateinit var vLogo: android.view.View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        modelInitializer = ModelInitializer(this)

        // Check if models already ready
        if (modelInitializer.areModelsReady()) {
            navigateToMain()
            return
        }

        setContentView(R.layout.activity_model_download)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        initViews()
        setupInsets()
        setupLogoAnimation()
        observeDownloadState()
        startInitialization()
    }

    private fun setupInsets() {
        val rootLayout = findViewById<ConstraintLayout>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left + 24.dpToPx(), insets.top + 24.dpToPx(),
                           insets.right + 24.dpToPx(), insets.bottom + 24.dpToPx())
            windowInsets
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun initViews() {
        tvTitle = findViewById(R.id.tvTitle)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)
        btnAction = findViewById(R.id.btnAction)
        vLogo = findViewById(R.id.vLogo)

        btnAction.setOnClickListener {
            navigateToMain()
        }
    }

    private fun setupLogoAnimation() {
        val drawable = vLogo.background as? GradientDrawable
        drawable?.setColor(ContextCompat.getColor(this, R.color.primary))
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
        val processing = states.filter {
            it.status == ModelStatus.EXTRACTING ||
            it.status == ModelStatus.DOWNLOADING
        }

        when {
            allReady -> {
                tvTitle.text = "初始化完成"
                tvSubtitle.text = "所有模型已就绪，可以开始使用"
                progressBar.progress = 100
                tvProgress.text = "100%"
                tvStatus.text = "✓ 准备完成"
                tvStatus.setTextColor(getColor(R.color.success))
                btnAction.visibility = android.view.View.VISIBLE
            }
            hasError -> {
                tvTitle.text = "初始化失败"
                tvSubtitle.text = "模型加载过程中出错"
                tvStatus.text = states.firstOrNull { it.error != null }?.error ?: "未知错误"
                btnAction.visibility = android.view.View.VISIBLE
                btnAction.text = "重试"
                btnAction.setOnClickListener { startInitialization() }
            }
            processing.isNotEmpty() -> {
                val current = processing.first()
                val totalProgress = states.map { it.progress }.average()
                val progressPercent = (totalProgress * 100).toInt()

                tvTitle.text = "正在初始化"
                tvSubtitle.text = "正在加载 ${current.modelInfo.name} 模型"
                progressBar.progress = progressPercent
                tvProgress.text = "$progressPercent%"
                tvStatus.text = "请稍候..."
                btnAction.visibility = android.view.View.GONE
            }
            else -> {
                tvStatus.text = "准备中..."
            }
        }
    }

    private fun startInitialization() {
        lifecycleScope.launch {
            val result = modelInitializer.initialize()

            if (result.isFailure) {
                tvStatus.text = "初始化失败：${result.exceptionOrNull()?.message}"
                btnAction.visibility = android.view.View.VISIBLE
                btnAction.text = "重试"
                btnAction.setOnClickListener { startInitialization() }
            }
        }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
