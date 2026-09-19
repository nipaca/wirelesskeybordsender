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
        // Connect button - triggers password dialog
        btnConnect.setOnClickListener {
            showPasswordDialog()
        }
        
        // Send button - sends text to receiver
        btnSend.setOnClickListener {
            sendText()
        }
        
        // Disconnect button - closes connection
        btnDisconnect.setOnClickListener {
            disconnect()
        }
        
        // Allow Enter key to send text (optional convenience)
        etInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_NULL) {
                sendText()
                true
            } else {
                false
            }
        }
    }
    
    private fun showPasswordDialog() {
        val passwordField = EditText(this).apply {
            hint = "Enter Session Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(40, 20, 40, 0)
        }
        
        AlertDialog.Builder(this)
            .setTitle("Enter Session Password")
            .setMessage("Enter the same password configured on the receiver device")
            .setView(passwordField)
            .setCancelable(false)
            .setPositiveButton("Connect") { dialog, _ ->
                val password = passwordField.text.toString().trim()
                
                if (password.isEmpty()) {
                    Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    return@setPositiveButton
                }
                
                if (password.length < 4) {
                    Toast.makeText(this, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    return@setPositiveButton
                }
                
                dialog.dismiss()
                lifecycleScope.launch {
                    connectToDevice(password)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }
    
    private suspend fun connectToDevice(password: String) {
        // Step 1: Get local IP to ensure WiFi is connected
        updateStatus("Checking network...", "#6d4aff")
        val localIp = networkManager.getLocalIpAddress()
        
        if (localIp == null) {
            showPopup("Error", "Could not get local IP address.\nMake sure you're connected to WiFi.")
            updateStatus("Disconnected", "#FF5722")
            return
        }
        
        // Step 2: Auto-discover receiver via broadcast
        updateStatus("Auto-discovering receiver...", "#6d4aff")
        val receiverIp = networkManager.discoverReceiver()
        
        if (receiverIp.isNullOrEmpty()) {
            // Auto-discovery failed - offer manual entry
            showPopup("Not Found", "No receiver detected on network.\nTry entering IP manually.")
            
            val manualIp = promptManualIp(localIp)
            if (manualIp.isNullOrEmpty()) {
                updateStatus("Disconnected", "#FF5722")
                return
            }
            
            connectWithIp(manualIp, password)
        } else {
            // Success! Auto-discovered receiver
            connectWithIp(receiverIp, password)
        }
    }
    
    private suspend fun connectWithIp(receiverIp: String, password: String) {
        updateStatus("Connecting to $receiverIp...", "#6d4aff")
        
        val success = networkManager.connect(receiverIp, password)
        
        if (success) {
            updateStatus("Connected! ($receiverIp)", "#4CAF50")
            enableSendingMode(true)
            showPopup("Success", "Connected to receiver at\n$receiverIp\n\nStart typing and press Send!")
        } else {
            updateStatus("Connection Failed", "#FF5722")
            showPopup("Error", "Failed to connect. Possible reasons:\n• Wrong password\n• Receiver not running\n• Firewall blocking port ${NetworkManager.PORT}")
        }
    }
    
    private fun promptManualIp(localIpPrefix: String?): String? {
        var result: String? = null
        
        val editText = EditText(this).apply {
            hint = "192.168.1.xxx"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(40, 20, 40, 0)
            
            // Suggest prefix from local IP if available
            if (!localIpPrefix.isNullOrEmpty()) {
                val prefix = localIpPrefix.substringBeforeLast(".")
                hint = "Suggested: ${prefix}.___"
            }
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Enter Receiver IP Manually")
            .setMessage("Find the IP address displayed in receiver.py terminal\n(e.g., 192.168.1.100)")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                result = editText.text.toString().trim()
            }
            .setNegativeButton("Cancel", null)
            .create()
            
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val ip = editText.text.toString().trim()
                if (validateIpAddress(ip)) {
                    result = ip
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, "Please enter a valid IP address", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        dialog.show()
        return result
    }
    
    private fun validateIpAddress(ip: String): Boolean {
        return ip.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))
    }
    
    private fun sendText() {
        val text = etInput.text.toString().trim()
        
        if (text.isEmpty()) {
            Toast.makeText(this, "Nothing to send!", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check for disconnect escape sequence
        if (text.equals(escapeSequence, ignoreCase = true)) {
            disconnect()
            etInput.setText("")
            Toast.makeText(this, "Disconnect requested", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            updateStatus("Sending...", "#6d4aff")
            val success = networkManager.send(text)
            
            if (success) {
                showPopup("Sent ✓", "Text sent successfully")
                etInput.setText("")
                updateStatus("Connected", "#4CAF50")
            } else {
                showPopup("Failed", "Failed to send message.\nTrying to reconnect...")
                updateStatus("Connection Lost", "#FF5722")
                disconnect()
            }
        }
    }
    
    private fun disconnect() {
        lifecycleScope.launch {
            networkManager.disconnect()
            updateStatus("Disconnected", "#FF5722")
            enableSendingMode(false)
            showToast("Disconnected from receiver")
        }
    }
    
    private fun enableSendingMode(enabled: Boolean) {
        btnConnect.visibility = if (enabled) View.GONE else View.VISIBLE
        btnSend.visibility = if (enabled) View.VISIBLE else View.GONE
        btnDisconnect.visibility = if (enabled) View.VISIBLE else View.GONE
        etInput.isEnabled = enabled
        
        if (enabled) {
            etInput.requestFocus()
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(etInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
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
    
    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        networkManager.disconnect()
    }
    
    override fun onPause() {
        super.onPause()
        // Optional: Keep connection when app goes to background
        // Uncomment below to disconnect on background
        // networkManager.disconnect()
    }
}
