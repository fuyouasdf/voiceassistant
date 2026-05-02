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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.voiceassistant.app.R
import com.voiceassistant.core.pipeline.WakeWord

/**
 * RecyclerView adapter for wake word list
 */
class WakeWordAdapter(
    private var wakeWords: MutableList<WakeWord>,
    private val onEdit: (Int, WakeWord) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<WakeWordAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvKeyword: TextView = view.findViewById(R.id.tvKeyword)
        val tvResponse: TextView = view.findViewById(R.id.tvResponse)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wake_word, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val wakeWord = wakeWords[position]
        holder.tvKeyword.text = wakeWord.keyword
        holder.tvResponse.text = wakeWord.response

        holder.btnEdit.setOnClickListener {
            onEdit(position, wakeWord)
        }

        holder.btnDelete.setOnClickListener {
            onDelete(position)
        }
    }

    override fun getItemCount(): Int = wakeWords.size

    fun updateData(newWords: List<WakeWord>) {
        wakeWords.clear()
        wakeWords.addAll(newWords)
        notifyDataSetChanged()
    }

    fun removeAt(position: Int) {
        if (position in 0 until wakeWords.size) {
            wakeWords.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}