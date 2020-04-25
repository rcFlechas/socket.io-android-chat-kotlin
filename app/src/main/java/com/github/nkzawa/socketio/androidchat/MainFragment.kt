package com.github.nkzawa.socketio.androidchat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.TextView.OnEditorActionListener
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.android.synthetic.main.fragment_main.*
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class MainFragment : Fragment() {

    private val mMessages: MutableList<Message> = ArrayList()
    private lateinit var mAdapter: RecyclerView.Adapter<*>
    private var mTyping = false
    private val mTypingHandler = Handler()
    private var mUsername: String? = null
    private var mSocket: Socket? = null
    private var isConnected = true

    // This event fires 1st, before creation of fragment or any views
    // The onAttach method is called when the Fragment instance is associated with an Activity.
    // This does not mean the Activity is fully initialized.
    override fun onAttach(context: Context) {
        super.onAttach(context)

        mAdapter = MessageAdapter(context, mMessages)
        if (context is Activity) {
            //this.listener = (MainActivity) context;
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)
        val app = activity!!.application as ChatApplication
        mSocket = app.socket
        mSocket!!.on(Socket.EVENT_CONNECT, onConnect)
        mSocket!!.on(Socket.EVENT_DISCONNECT, onDisconnect)
        mSocket!!.on(Socket.EVENT_CONNECT_ERROR, onConnectError)
        mSocket!!.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectError)
        mSocket!!.on("new message", onNewMessage)
        mSocket!!.on("user joined", onUserJoined)
        mSocket!!.on("user left", onUserLeft)
        mSocket!!.on("typing", onTyping)
        mSocket!!.on("stop typing", onStopTyping)
        mSocket!!.connect()
        startSignIn()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onDestroy() {
        super.onDestroy()

        mSocket!!.disconnect()
        mSocket!!.off(Socket.EVENT_CONNECT, onConnect)
        mSocket!!.off(Socket.EVENT_DISCONNECT, onDisconnect)
        mSocket!!.off(Socket.EVENT_CONNECT_ERROR, onConnectError)
        mSocket!!.off(Socket.EVENT_CONNECT_TIMEOUT, onConnectError)
        mSocket!!.off("new message", onNewMessage)
        mSocket!!.off("user joined", onUserJoined)
        mSocket!!.off("user left", onUserLeft)
        mSocket!!.off("typing", onTyping)
        mSocket!!.off("stop typing", onStopTyping)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        messages.run {
            layoutManager = LinearLayoutManager(activity)
            adapter = mAdapter
        }

        messageEditText.setOnEditorActionListener(OnEditorActionListener { v, id, event ->
            if (id == R.id.send || id == EditorInfo.IME_NULL) {
                attemptSend()
                return@OnEditorActionListener true
            }
            false
        })
        messageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

                if (null == mUsername) return
                if (!mSocket!!.connected()) return
                if (!mTyping) {
                    mTyping = true
                    mSocket!!.emit("typing")
                }
                mTypingHandler.removeCallbacks(onTypingTimeout)
                mTypingHandler.postDelayed(
                    onTypingTimeout,
                    TYPING_TIMER_LENGTH.toLong()
                )
            }

            override fun afterTextChanged(s: Editable) {}
        })

        val sendButton = view.findViewById<View>(R.id.send_button) as ImageButton
        sendButton.setOnClickListener { attemptSend() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (Activity.RESULT_OK != resultCode) {
            activity!!.finish()
            return
        }
        mUsername = data!!.getStringExtra("username")
        val numUsers = data.getIntExtra("numUsers", 1)
        addLog(resources.getString(R.string.message_welcome))
        addParticipantsLog(numUsers)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_main, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId
        if (id == R.id.action_leave) {
            leave()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun addLog(message: String) {

        mMessages.add(Message.Builder(Message.TYPE_LOG).message(message).build())
        mAdapter.notifyItemInserted(mMessages.size - 1)
        scrollToBottom()
    }

    private fun addParticipantsLog(numUsers: Int) {

        addLog(resources.getQuantityString(R.plurals.message_participants, numUsers, numUsers))
    }

    private fun addMessage(username: String, message: String) {

        mMessages.add(Message.Builder(Message.TYPE_MESSAGE).username(username).message(message).build())
        mAdapter.notifyItemInserted(mMessages.size - 1)
        scrollToBottom()
    }

    private fun addTyping(username: String) {

        mMessages.add(Message.Builder(Message.TYPE_ACTION).username(username).build())
        mAdapter.notifyItemInserted(mMessages.size - 1)
        scrollToBottom()
    }

    private fun removeTyping(username: String) {

        for (i in mMessages.indices.reversed()) {
            val message = mMessages[i]
            if (message.type == Message.TYPE_ACTION && message.username == username) {
                mMessages.removeAt(i)
                mAdapter.notifyItemRemoved(i)
            }
        }
    }

    private fun attemptSend() {

        if (null == mUsername) return
        if (!mSocket!!.connected()) return
        mTyping = false
        val message = messageEditText.text.toString().trim { it <= ' ' }
        if (TextUtils.isEmpty(message)) {
            messageEditText.requestFocus()
            return
        }
        messageEditText.setText("")
        addMessage(mUsername!!, message)

        // perform the sending message attempt.
        mSocket!!.emit("new message", message)
    }

    private fun startSignIn() {

        mUsername = null
        val intent = Intent(activity, LoginActivity::class.java)
        startActivityForResult(intent, REQUEST_LOGIN)
    }

    private fun leave() {

        mUsername = null
        mSocket!!.disconnect()
        mSocket!!.connect()
        startSignIn()
    }

    private fun scrollToBottom() {

        messages.scrollToPosition(mAdapter.itemCount - 1)
    }

    private val onConnect = Emitter.Listener {

        activity!!.runOnUiThread {
            if (!isConnected) {
                if (null != mUsername) mSocket!!.emit("add user", mUsername)
                Toast.makeText(
                    activity!!.applicationContext,
                    R.string.connect, Toast.LENGTH_LONG
                ).show()
                isConnected = true
            }
        }
    }

    private val onDisconnect = Emitter.Listener {

        activity!!.runOnUiThread {
            Log.i(TAG, "diconnected")
            isConnected = false
            Toast.makeText(
                activity!!.applicationContext,
                R.string.disconnect, Toast.LENGTH_LONG
            ).show()
        }
    }
    private val onConnectError = Emitter.Listener {

        activity!!.runOnUiThread {
            Log.e(TAG, "Error connecting")
            Toast.makeText(
                activity!!.applicationContext,
                R.string.error_connect, Toast.LENGTH_LONG
            ).show()
        }
    }
    private val onNewMessage = Emitter.Listener { args ->

        activity!!.runOnUiThread(Runnable {
            val data = args[0] as JSONObject
            val username: String
            val message: String
            try {
                username = data.getString("username")
                message = data.getString("message")
            } catch (e: JSONException) {
                Log.e(TAG, e.message)
                return@Runnable
            }
            removeTyping(username)
            addMessage(username, message)
        })
    }
    private val onUserJoined = Emitter.Listener { args ->

        activity!!.runOnUiThread(Runnable {
            val data = args[0] as JSONObject
            val username: String
            val numUsers: Int
            try {
                username = data.getString("username")
                numUsers = data.getInt("numUsers")
            } catch (e: JSONException) {
                Log.e(TAG, e.message)
                return@Runnable
            }
            addLog(resources.getString(R.string.message_user_joined, username))
            addParticipantsLog(numUsers)
        })
    }
    private val onUserLeft = Emitter.Listener { args ->

        activity!!.runOnUiThread(Runnable {
            val data = args[0] as JSONObject
            val username: String
            val numUsers: Int
            try {
                username = data.getString("username")
                numUsers = data.getInt("numUsers")
            } catch (e: JSONException) {
                Log.e(TAG, e.message)
                return@Runnable
            }
            addLog(resources.getString(R.string.message_user_left, username))
            addParticipantsLog(numUsers)
            removeTyping(username)
        })
    }
    private val onTyping = Emitter.Listener { args ->

        activity!!.runOnUiThread(Runnable {
            val data = args[0] as JSONObject
            val username: String
            username = try {
                data.getString("username")
            } catch (e: JSONException) {
                Log.e(TAG, e.message)
                return@Runnable
            }
            addTyping(username)
        })
    }
    private val onStopTyping = Emitter.Listener { args ->

        activity!!.runOnUiThread(Runnable {
            val data = args[0] as JSONObject
            val username: String
            username = try {
                data.getString("username")
            } catch (e: JSONException) {
                Log.e(TAG, e.message)
                return@Runnable
            }
            removeTyping(username)
        })
    }
    private val onTypingTimeout = Runnable {

        if (!mTyping) return@Runnable
        mTyping = false
        mSocket!!.emit("stop typing")
    }

    companion object {
        private const val TAG = "MainFragment"
        private const val REQUEST_LOGIN = 0
        private const val TYPING_TIMER_LENGTH = 600
    }
}