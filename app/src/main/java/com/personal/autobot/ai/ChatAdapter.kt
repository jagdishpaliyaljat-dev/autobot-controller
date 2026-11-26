package com.personal.autobot.ai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.personal.autobot.R

class ChatAdapter(private val messageList: MutableList<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val llBotContainer: LinearLayout = itemView.findViewById(R.id.llBotContainer)
        val tvBotMessage: TextView = itemView.findViewById(R.id.tvBotMessage)
        val llUserContainer: LinearLayout = itemView.findViewById(R.id.llUserContainer)
        val tvUserMessage: TextView = itemView.findViewById(R.id.tvUserMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val item = messageList[position]
        if (item.isUser) {
            holder.llUserContainer.visibility = View.VISIBLE
            holder.llBotContainer.visibility = View.GONE
            holder.tvUserMessage.text = item.message
        } else {
            holder.llUserContainer.visibility = View.GONE
            holder.llBotContainer.visibility = View.VISIBLE
            holder.tvBotMessage.text = item.message
        }
    }

    override fun getItemCount(): Int = messageList.size

    fun addMessage(msg: ChatMessage) {
        messageList.add(msg)
        notifyItemInserted(messageList.size - 1)
    }
}
