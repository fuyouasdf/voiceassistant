package com.voiceassistant.app.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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
) : RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<ChatMessageEntity>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    fun getMessageAt(position: Int): ChatMessageEntity = messages[position]

    fun addMessages(newMessages: List<ChatMessageEntity>, atEnd: Boolean = true) {
        val diffCallback = MessageDiffCallback(messages, newMessages, atEnd)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        if (atEnd) {
            messages.addAll(newMessages)
        } else {
            // Prepend - need to add in reverse order so oldest appears first
            messages.addAll(0, newMessages.reversed())
        }

        diffResult.dispatchUpdatesTo(this)
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

    private class MessageDiffCallback(
        private val oldList: List<ChatMessageEntity>,
        private val newList: List<ChatMessageEntity>,
        private val atEnd: Boolean
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = if (atEnd) newList[newItemPosition] else newList[newItemPosition]
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = if (atEnd) newList[newItemPosition] else newList[newItemPosition]
            return oldItem == newItem
        }
    }
}
