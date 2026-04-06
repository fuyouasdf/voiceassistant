package com.voiceassistant.app.ui.music

import android.view.animation.AccelerateDecelerateInterpolator
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.voiceassistant.app.databinding.ItemLyricLineBinding
import com.voiceassistant.data.remote.LyricLine
import kotlin.math.abs

data class LyricUiItem(
    val line: LyricLine,
    val isActive: Boolean
)

class LyricsAdapter(
    private val onLyricClick: (LyricLine) -> Unit
) : RecyclerView.Adapter<LyricsAdapter.ViewHolder>() {

    private val items = mutableListOf<LyricUiItem>()
    private var activeIndex = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLyricLineBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    fun submitLyrics(lines: List<LyricLine>, highlightedIndex: Int) {
        items.clear()
        items.addAll(lines.mapIndexed { index, line ->
            LyricUiItem(line = line, isActive = index == highlightedIndex)
        })
        activeIndex = highlightedIndex
        notifyDataSetChanged()
    }

    fun updateActiveLine(newIndex: Int) {
        if (newIndex == activeIndex) return
        val previous = activeIndex
        activeIndex = newIndex
        if (previous in items.indices) {
            items[previous] = items[previous].copy(isActive = false)
            notifyItemChanged(previous)
        }
        if (newIndex in items.indices) {
            items[newIndex] = items[newIndex].copy(isActive = true)
            notifyItemChanged(newIndex)
        }
    }

    /**
     * Returns the alpha for a lyric line based on its distance from the active line.
     * Active line = 1f, lines progressively dim further away.
     */
    private fun computeAlphaForDistance(distance: Int): Float {
        return when (distance) {
            0 -> 1.0f      // Active line
            1 -> 0.80f     // Adjacent
            2 -> 0.60f     // Two away
            3 -> 0.48f     // Three away
            else -> 0.38f  // Far away
        }
    }

    /**
     * Returns the font scale for a lyric line based on its distance from the active line.
     */
    private fun computeScaleForDistance(distance: Int): Float {
        return when (distance) {
            0 -> 1.0f
            1 -> 0.97f
            2 -> 0.94f
            else -> 0.91f
        }
    }

    /**
     * Returns the line spacing multiplier for a lyric line based on distance.
     */
    private fun computeLineSpacingForDistance(distance: Int): Float {
        return when (distance) {
            0 -> 1.22f
            1 -> 1.14f
            else -> 1.08f
        }
    }

    inner class ViewHolder(
        private val binding: ItemLyricLineBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LyricUiItem, position: Int) {
            val distance = if (activeIndex >= 0) abs(position - activeIndex) else Int.MAX_VALUE
            val targetAlpha = computeAlphaForDistance(distance)
            val targetScale = computeScaleForDistance(distance)
            val targetLineSpacing = computeLineSpacingForDistance(distance)
            val targetTextSize = if (distance == 0) 22f else 16f

            binding.tvLyricLine.text = item.line.text
            binding.tvLyricLine.textSize = targetTextSize
            binding.tvLyricLine.setLineSpacing(0f, targetLineSpacing)
            binding.tvLyricLine.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            binding.root.animate().cancel()
            binding.tvLyricLine.animate().cancel()

            binding.root.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(220L)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()

            binding.tvLyricLine.animate()
                .alpha(targetAlpha)
                .setDuration(220L)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()

            binding.root.setOnClickListener {
                onLyricClick(item.line)
            }
        }
    }
}