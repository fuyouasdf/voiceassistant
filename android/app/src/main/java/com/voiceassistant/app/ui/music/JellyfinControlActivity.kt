package com.voiceassistant.app.ui.music

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView
import com.voiceassistant.app.R
import com.voiceassistant.data.remote.SessionInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Jellyfin 远程控制页面
 * 用于控制 Jellyfin 服务端的其他客户端播放
 */
@AndroidEntryPoint
class JellyfinControlActivity : AppCompatActivity() {

    private val viewModel: JellyfinControlViewModel by viewModels()

    // 会话列表
    private lateinit var recyclerSessions: RecyclerView
    private lateinit var sessionAdapter: SessionAdapter

    // 当前播放信息
    private lateinit var tvNowPlaying: MaterialTextView
    private lateinit var tvPosition: MaterialTextView
    private lateinit var sliderPosition: Slider

    // 控制按钮
    private lateinit var btnPrevious: MaterialButton
    private lateinit var btnPlay: MaterialButton
    private lateinit var btnPause: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var btnNext: MaterialButton

    // 其他
    private lateinit var btnRefresh: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jellyfin_control)

        initViews()
        setupRecyclerView()
        setupListeners()
        observeState()
    }

    private fun initViews() {
        recyclerSessions = findViewById(R.id.recyclerSessions)
        tvNowPlaying = findViewById(R.id.tvNowPlaying)
        tvPosition = findViewById(R.id.tvPosition)
        sliderPosition = findViewById(R.id.sliderPosition)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnPlay = findViewById(R.id.btnPlay)
        btnPause = findViewById(R.id.btnPause)
        btnStop = findViewById(R.id.btnStop)
        btnNext = findViewById(R.id.btnNext)
        btnRefresh = findViewById(R.id.btnRefresh)
    }

    private fun setupRecyclerView() {
        sessionAdapter = SessionAdapter { session ->
            viewModel.selectSession(session)
        }
        recyclerSessions.apply {
            layoutManager = LinearLayoutManager(this@JellyfinControlActivity)
            adapter = sessionAdapter
        }
    }

    private fun setupListeners() {
        btnRefresh.setOnClickListener {
            viewModel.refreshSessions()
        }

        btnPrevious.setOnClickListener {
            viewModel.previousTrack()
        }

        btnPlay.setOnClickListener {
            viewModel.play()
        }

        btnPause.setOnClickListener {
            viewModel.pause()
        }

        btnStop.setOnClickListener {
            viewModel.stop()
        }

        btnNext.setOnClickListener {
            viewModel.nextTrack()
        }

        sliderPosition.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}

            override fun onStopTrackingTouch(slider: Slider) {
                val positionMs = slider.value.toLong()
                viewModel.seek(positionMs)
            }
        })
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                // 更新会话列表
                sessionAdapter.submitList(state.sessions)
                sessionAdapter.setSelectedSession(state.selectedSession)

                // 更新当前播放信息
                state.selectedSession?.let { session ->
                    updateNowPlayingInfo(session)
                } ?: run {
                    tvNowPlaying.text = getString(R.string.jellyfin_control_no_session)
                    tvPosition.text = "--:--"
                    sliderPosition.value = 0f
                }

                // 显示错误
                state.error?.let { error ->
                    Toast.makeText(this@JellyfinControlActivity, error, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }

                // 显示消息
                state.message?.let { message ->
                    Toast.makeText(this@JellyfinControlActivity, message, Toast.LENGTH_SHORT).show()
                    viewModel.clearMessage()
                }

                // 控制按钮状态
                val hasSession = state.selectedSession != null
                btnPrevious.isEnabled = hasSession
                btnPlay.isEnabled = hasSession
                btnPause.isEnabled = hasSession
                btnStop.isEnabled = hasSession
                btnNext.isEnabled = hasSession
                sliderPosition.isEnabled = hasSession
            }
        }
    }

    private fun updateNowPlayingInfo(session: SessionInfo) {
        val nowPlaying = session.nowPlayingItem
        if (nowPlaying != null) {
            val artists = nowPlaying.artists.joinToString(", ")
            tvNowPlaying.text = if (artists.isNotEmpty()) {
                "${nowPlaying.name} - $artists"
            } else {
                nowPlaying.name
            }

            // 更新时间
            val positionTicks = session.playbackState?.positionTicks ?: 0
            val durationTicks = nowPlaying.durationTicks
            val positionMs = positionTicks / 10000
            val durationMs = durationTicks / 10000

            tvPosition.text = formatTime(positionMs, durationMs)

            // 更新滑块
            if (durationMs > 0) {
                sliderPosition.valueFrom = 0f
                sliderPosition.valueTo = durationMs.toFloat().coerceAtLeast(1f)
                sliderPosition.value = positionMs.toFloat().coerceIn(0f, durationMs.toFloat())
            }
        } else {
            tvNowPlaying.text = getString(R.string.jellyfin_control_not_playing)
            tvPosition.text = "--/--"
            sliderPosition.value = 0f
        }
    }

    private fun formatTime(positionMs: Long, durationMs: Long): String {
        val posSec = (positionMs / 1000).toInt()
        val durSec = (durationMs / 1000).toInt()
        return String.format("%d:%02d / %d:%02d", posSec / 60, posSec % 60, durSec / 60, durSec % 60)
    }
}

/**
 * 会话列表适配器
 */
class SessionAdapter(
    private val onSessionClick: (SessionInfo) -> Unit
) : RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {

    private var sessions: List<SessionInfo> = emptyList()
    private var selectedSession: SessionInfo? = null

    fun submitList(list: List<SessionInfo>) {
        sessions = list
        notifyDataSetChanged()
    }

    fun setSelectedSession(session: SessionInfo?) {
        val oldSelected = selectedSession
        selectedSession = session
        // 刷新变化的项
        sessions.indexOfFirst { it.id == oldSelected?.id }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
        sessions.indexOfFirst { it.id == session?.id }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): SessionViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(sessions[position], sessions[position].id == selectedSession?.id)
    }

    override fun getItemCount() = sessions.size

    inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDeviceName: MaterialTextView = itemView.findViewById(R.id.tvDeviceName)
        private val tvClient: MaterialTextView = itemView.findViewById(R.id.tvClient)
        private val tvNowPlaying: MaterialTextView = itemView.findViewById(R.id.tvNowPlaying)
        private val tvStatus: MaterialTextView = itemView.findViewById(R.id.tvStatus)
        private val cardView: com.google.android.material.card.MaterialCardView = itemView.findViewById(R.id.cardSession)
        private val selectedIndicator: View = itemView.findViewById(R.id.selectedIndicator)

        fun bind(session: SessionInfo, isSelected: Boolean) {
            tvDeviceName.text = session.deviceName
            tvClient.text = session.client
            tvNowPlaying.text = session.nowPlayingItem?.name ?: itemView.context.getString(R.string.jellyfin_control_not_playing)

            val status = when {
                session.nowPlayingItem == null -> ""
                session.playbackState?.isPaused == true -> itemView.context.getString(R.string.jellyfin_control_paused)
                else -> itemView.context.getString(R.string.jellyfin_control_playing)
            }
            tvStatus.text = status

            cardView.isChecked = isSelected
            cardView.setOnClickListener { onSessionClick(session) }

            // Show selected indicator
            selectedIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
        }
    }
}