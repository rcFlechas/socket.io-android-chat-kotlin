package com.github.nkzawa.socketio.androidchat

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView.OnEditorActionListener
import androidx.appcompat.app.AppCompatActivity
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.android.synthetic.main.activity_login.*
import org.json.JSONException
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var mUsername: String
    private var mSocket: Socket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        val app = application as ChatApplication
        mSocket = app.socket

        // Set up the login form.
        usernameEditText.setOnEditorActionListener(OnEditorActionListener { textView, id, keyEvent ->

            if (id == R.id.login || id == EditorInfo.IME_NULL) {
                attemptLogin()
                return@OnEditorActionListener true
            }
            false
        })

        val signInButton = findViewById<View>(R.id.sign_in_button) as Button
        signInButton.setOnClickListener { attemptLogin() }
        mSocket!!.on("login", onLogin)
    }

    override fun onDestroy() {

        super.onDestroy()
        mSocket!!.off("login", onLogin)
    }

    /**
     * Attempts to sign in the account specified by the login form.
     * If there are form errors (invalid username, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private fun attemptLogin() {

        // Reset errors.
        usernameEditText.error = null

        // Store values at the time of the login attempt.
        val username = usernameEditText.text.toString().trim { it <= ' ' }

        // Check for a valid username.
        if (TextUtils.isEmpty(username)) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            usernameEditText.error = getString(R.string.error_field_required)
            usernameEditText.requestFocus()
            return
        }
        mUsername = username

        // perform the user login attempt.
        mSocket!!.emit("add user", username)
    }

    private val onLogin = Emitter.Listener { args ->

        val data = args[0] as JSONObject
        val numUsers: Int
        numUsers = try {
            data.getInt("numUsers")
        } catch (e: JSONException) {
            return@Listener
        }
        val intent = Intent()
        intent.putExtra("username", mUsername)
        intent.putExtra("numUsers", numUsers)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}