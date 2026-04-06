package com.voiceassistant.app.ui.music

import android.view.animation.AccelerateDecelerateInterpolator
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.voiceassistant.app.databinding.ItemLyricLineBinding
import com.voiceassistant.data.remote.LyricLine

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
        holder.bind(items[position])
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

    inner class ViewHolder(
        private val binding: ItemLyricLineBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LyricUiItem) {
            val targetTextAlpha = if (item.isActive) 1f else 0.55f
            val targetScale = if (item.isActive) 1f else 0.98f
            val targetTranslationY = if (item.isActive) 0f else 4f

            binding.tvLyricLine.text = item.line.text
            binding.tvLyricLine.textSize = if (item.isActive) 22f else 16f
            binding.tvLyricLine.setLineSpacing(0f, if (item.isActive) 1.18f else 1.08f)
            binding.tvLyricLine.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            binding.root.animate().cancel()
            binding.tvLyricLine.animate().cancel()

            binding.root.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .translationY(targetTranslationY)
                .setDuration(220L)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()

            binding.tvLyricLine.animate()
                .alpha(targetTextAlpha)
                .setDuration(220L)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()

            binding.root.setOnClickListener {
                onLyricClick(item.line)
            }
        }
    }
}
