package com.github.nkzawa.socketio.androidchat

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter(
    context: Context,
    private val mMessages: List<Message>
) :
    RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

    private val mUsernameColors: IntArray = context.resources.getIntArray(R.array.username_colors)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        var layout = -1
        when (viewType) {
            Message.TYPE_MESSAGE -> layout = R.layout.item_message
            Message.TYPE_LOG -> layout = R.layout.item_log
            Message.TYPE_ACTION -> layout = R.layout.item_action
        }
        return ViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val message = mMessages[position]
        viewHolder.setMessage(message.message)
        viewHolder.setUsername(message.username)
    }

    override fun getItemCount(): Int {
        return mMessages.size
    }

    override fun getItemViewType(position: Int): Int {
        return mMessages[position].type
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val mUsernameView: TextView? = itemView.findViewById<View>(R.id.username) as? TextView
        private val mMessageView: TextView? = itemView.findViewById<View>(R.id.message) as? TextView

        fun setUsername(username: String?) {

            if (null == mUsernameView) return
            mUsernameView.text = username
            mUsernameView.setTextColor(getUsernameColor(username))
        }

        fun setMessage(message: String?) {

            if (null == mMessageView) return
            mMessageView.text = message
        }

        private fun getUsernameColor(username: String?): Int {

            var hash = 7

            username?.let {
                var i = 0
                val len = it.length
                while (i < len) {
                    hash = it.codePointAt(i) + (hash shl 5) - hash
                    i++
                }
            }

            val index = Math.abs(hash % mUsernameColors.size)
            return mUsernameColors[index]
        }

    }

}
