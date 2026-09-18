package com.example.wirelesskeyboardsender

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : AppCompatActivity() {
    
    private lateinit var networkManager: NetworkManager
    private lateinit var tvStatus: TextView
    private lateinit var etInput: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnSend: Button
    private lateinit var btnDisconnect: Button
    
    private val escapeSequence = "<<STOP>>"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupListeners()
        networkManager = NetworkManager(this)
    }
    
    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        etInput = findViewById(R.id.etInput)
        btnConnect = findViewById(R.id.btnConnect)
        btnSend = findViewById(R.id.btnSend)
        btnDisconnect = findViewById(R.id.btnDisconnect)
    }
    
    private fun setupListeners() {
        btnConnect.setOnClickListener {
            showPasswordDialog()
        }
        
        btnSend.setOnClickListener {
            sendText()
        }
        
        btnDisconnect.setOnClickListener {
            disconnect()
        }
        
        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendText()
                true
            } else {
                false
            }
        }
    }
    
    private fun showPasswordDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enter Session Password")
            .setMessage("Enter the same password configured on the receiver")
            .setCancelable(false)
            .setView(R.layout.dialog_password)
            .setPositiveButton("Connect") { dialog, _ ->
                val password = getPasswordFromDialog()
                if (password.isNullOrEmpty() || password.length < 4) {
                    Toast.makeText(this, "Password too short (min 4 characters)", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    connectToDevice(password)
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }
    
    private fun getPasswordFromDialog(): String? {
        val passwordField = (currentFocus as? EditText) ?: findViewById<EditText>(R.id.etPassword)
        return passwordField?.text.toString()
    }
    
    private suspend fun connectToDevice(password: String) {
        updateStatus("Discovering...", "#6d4aff")
        
        val localIp = networkManager.getLocalIpAddress()
        if (localIp == null) {
            showPopup("Error", "Could not get local IP address")
            updateStatus("Disconnected", "#FF5722")
            return
        }
        
        // Note: Full broadcast discovery requires additional permissions
        // For now, user enters receiver IP manually or we use a simpler method
        val receiverIp = promptReceiverIp()
        
        if (receiverIp.isNullOrEmpty()) {
            updateStatus("Disconnected", "#FF5722")
            return
        }
        
        updateStatus("Connecting to $receiverIp...", "#6d4aff")
        
        val success = networkManager.connect(receiverIp, password)
        
        if (success) {
            updateStatus("Connected!", "#4CAF50")
            enableSendingMode(true)
            showPopup("Success", "Connected to receiver at $receiverIp")
        } else {
            updateStatus("Connection Failed", "#FF5722")
            showPopup("Error", "Failed to connect. Check receiver IP and password.")
        }
    }
    
    private fun promptReceiverIp(): String? {
        var result: String? = null
        val editText = EditText(this)
        editText.hint = "192.168.1.xxx"
        editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Enter Receiver IP")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                result = editText.text.toString().trim()
            }
            .setNegativeButton("Cancel", null)
            .create()
            
        dialog.show()
        return result
    }
    
    private fun sendText() {
        val text = etInput.text.toString().trim()
        if (text.isEmpty()) return
        
        if (text == escapeSequence) {
            disconnect()
            etInput.setText("")
            return
        }
        
        lifecycleScope.launch {
            val success = networkManager.send(text)
            if (success) {
                showPopup("Sent", "✓")
                etInput.setText("")
            } else {
                showPopup("Error", "Failed to send message")
                disconnect()
            }
        }
    }
    
    private fun disconnect() {
        lifecycleScope.launch {
            networkManager.disconnect()
            updateStatus("Disconnected", "#FF5722")
            enableSendingMode(false)
            showPopup("Disconnected", "Connection closed")
        }
    }
    
    private fun enableSendingMode(enabled: Boolean) {
        btnConnect.visibility = if (enabled) View.GONE else View.VISIBLE
        btnSend.visibility = if (enabled) View.VISIBLE else View.GONE
        btnDisconnect.visibility = if (enabled) View.VISIBLE else View.GONE
        etInput.isEnabled = enabled
    }
    
    private fun updateStatus(message: String, colorHex: String) {
        runOnUiThread {
            tvStatus.text = message
            tvStatus.setTextColor(android.graphics.Color.parseColor(colorHex))
        }
    }
    
    private fun showPopup(title: String, message: String) {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        networkManager.disconnect()
    }
}
