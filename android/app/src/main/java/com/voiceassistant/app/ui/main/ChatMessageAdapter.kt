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

package com.voiceassistant.app.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.voiceassistant.app.R
import com.voiceassistant.data.local.ChatMessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatMessageAdapter(
    private val onMessageLongClick: (Long, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_MESSAGE = 0
        private const val VIEW_TYPE_LOADING = 1
    }

    private val messages = mutableListOf<ChatMessageEntity>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    var isLoadingMore = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    notifyItemInserted(0) // 插入 loading 占位
                } else {
                    notifyItemRemoved(0)
                }
            }
        }

    override fun getItemViewType(position: Int): Int {
        return if (isLoadingMore && position == 0) VIEW_TYPE_LOADING else VIEW_TYPE_MESSAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_LOADING -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_loading, parent, false)
                LoadingViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message, parent, false)
                MessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MessageViewHolder -> {
                val msgPosition = if (isLoadingMore) position - 1 else position
                if (msgPosition in messages.indices) {
                    holder.bind(messages[msgPosition])
                }
            }
            // LoadingViewHolder 无需绑定
        }
    }

    override fun getItemCount(): Int = messages.size + if (isLoadingMore) 1 else 0

    fun getMessageAt(position: Int): ChatMessageEntity = messages[position]

    fun addMessages(newMessages: List<ChatMessageEntity>, atEnd: Boolean = true) {
        if (newMessages.isEmpty()) return

        if (atEnd) {
            val oldSize = messages.size
            messages.addAll(newMessages)
            notifyItemRangeInserted(oldSize, newMessages.size)
        } else {
            // Prepend 历史消息：直接计算插入数量，使用 notifyItemRangeInserted
            // 跳过 DiffUtil，因为 prepending 时位置对应会出错
            val reversed = newMessages.reversed()
            val insertCount = reversed.size
            messages.addAll(0, reversed)
            notifyItemRangeInserted(0, insertCount)
        }
    }

    fun addMessage(message: ChatMessageEntity) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun clearMessages() {
        messages.clear()
        notifyDataSetChanged()
    }

    fun removeMessage(position: Int) {
        if (position in messages.indices) {
            messages.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun getOldestMessageId(): Long? = messages.firstOrNull()?.id

    fun getOldestMessageCreatedAt(): Long? = messages.firstOrNull()?.createdAt

    fun getMessagePosition(messageId: Long): Int {
        return messages.indexOfFirst { it.id == messageId }
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val aiContainer: LinearLayout = itemView.findViewById(R.id.aiMessageContainer)
        private val userContainer: LinearLayout = itemView.findViewById(R.id.userMessageContainer)
        private val tvAiTimestamp: TextView = itemView.findViewById(R.id.tvAiTimestamp)
        private val tvAiMessage: TextView = itemView.findViewById(R.id.tvAiMessage)
        private val tvUserTimestamp: TextView = itemView.findViewById(R.id.tvUserTimestamp)
        private val tvUserMessage: TextView = itemView.findViewById(R.id.tvUserMessage)

        fun bind(message: ChatMessageEntity) {
            val timestamp = timeFormat.format(Date(message.createdAt))

            if (message.isUser) {
                aiContainer.visibility = View.GONE
                userContainer.visibility = View.VISIBLE
                tvUserTimestamp.text = timestamp
                tvUserMessage.text = message.text
            } else {
                userContainer.visibility = View.GONE
                aiContainer.visibility = View.VISIBLE
                tvAiTimestamp.text = timestamp
                tvAiMessage.text = message.text
            }

            itemView.tag = message.id
            itemView.setOnLongClickListener {
                onMessageLongClick(message.id, itemView)
                true
            }
        }
    }

    class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private class MessageDiffCallback(
        private val oldList: List<ChatMessageEntity>,
        private val newList: List<ChatMessageEntity>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
