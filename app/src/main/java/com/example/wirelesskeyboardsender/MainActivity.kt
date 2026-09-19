package com.example.wirelesskeyboardsender

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
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
    private lateinit var btnClear: Button
    private lateinit var btnBackspace: Button
    private lateinit var btnTab: Button
    private lateinit var btnEnter: Button
    private lateinit var btnDelete: Button
    
    private val escapeSequence = "<<STOP>>"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupListeners()
        networkManager = NetworkManager(this)
        
        // Focus on EditText by default
        etInput.requestFocus()
    }
    
    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        etInput = findViewById(R.id.etInput)
        btnConnect = findViewById(R.id.btnConnect)
        btnSend = findViewById(R.id.btnSend)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        btnClear = findViewById(R.id.btnClear)
        btnBackspace = findViewById(R.id.btnBackspace)
        btnTab = findViewById(R.id.btnTab)
        btnEnter = findViewById(R.id.btnEnter)
        btnDelete = findViewById(R.id.btnDelete)
    }
    
    private fun setupListeners() {
        // Connect button
        btnConnect.setOnClickListener {
            showPasswordDialog()
        }
        
        // Send button - sends text WITHOUT clearing
        btnSend.setOnClickListener {
            sendText()
        }
        
        // Disconnect button - moved to top
        btnDisconnect.setOnClickListener {
            disconnect()
        }
        
        // Clear button - new
        btnClear.setOnClickListener {
            etInput.text.clear()
            showToast("Text cleared")
        }
        
        // Special key buttons - insert markers
        btnBackspace.setOnClickListener {
            val currentText = etInput.text.toString()
            etInput.setText(currentText + "<<BACKSPACE>>")
            etInput.setSelection(etInput.text.length)
        }
        
        btnTab.setOnClickListener {
            val currentText = etInput.text.toString()
            etInput.setText(currentText + "<<TAB>>")
            etInput.setSelection(etInput.text.length)
        }
        
        btnEnter.setOnClickListener {
            val currentText = etInput.text.toString()
            etInput.setText(currentText + "<<ENTER>>")
            etInput.setSelection(etInput.text.length)
        }
        
        btnDelete.setOnClickListener {
            val currentText = etInput.text.toString()
            etInput.setText(currentText + "<<DELETE>>")
            etInput.setSelection(etInput.text.length)
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
        updateStatus("Checking network...", "#6d4aff")
        val localIp = networkManager.getLocalIpAddress()
        
        if (localIp == null) {
            Toast.makeText(this, "Could not get IP. Check WiFi.", Toast.LENGTH_SHORT).show()
            updateStatus("Disconnected", "#FF5722")
            return
        }
        
        updateStatus("Auto-discovering receiver...", "#6d4aff")
        val receiverIp = networkManager.discoverReceiver()
        
        if (receiverIp.isNullOrEmpty()) {
            Toast.makeText(this, "No receiver found", Toast.LENGTH_SHORT).show()
            
            val manualIp = promptManualIp(localIp)
            if (manualIp.isNullOrEmpty()) {
                updateStatus("Disconnected", "#FF5722")
                return
            }
            
            connectWithIp(manualIp, password)
        } else {
            connectWithIp(receiverIp, password)
        }
    }
    
    private suspend fun connectWithIp(receiverIp: String, password: String) {
        updateStatus("Connecting to $receiverIp...", "#6d4aff")
        
        val success = networkManager.connect(receiverIp, password)
        
        if (success) {
            updateStatus("Connected! ($receiverIp)", "#4CAF50")
            enableSendingMode(true)
            showToast("Connected successfully!")
        } else {
            updateStatus("Connection Failed", "#FF5722")
            Toast.makeText(this, "Auth failed. Check password.", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun promptManualIp(localIpPrefix: String?): String? {
        var result: String? = null
        
        val editText = EditText(this).apply {
            hint = "192.168.1.xxx"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(40, 20, 40, 0)
            
            if (!localIpPrefix.isNullOrEmpty()) {
                val prefix = localIpPrefix.substringBeforeLast(".")
                hint = "Suggested: ${prefix}.___"
            }
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Enter Receiver IP Manually")
            .setMessage("Find IP from receiver.py terminal")
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
                    Toast.makeText(this, "Invalid IP address", Toast.LENGTH_SHORT).show()
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
            showToast("Disconnect requested")
            return
        }
        
        lifecycleScope.launch {
            updateStatus("Sending...", "#6d4aff")
            val success = networkManager.send(text)
            
            if (success) {
                showToast("✓ Text sent")
                updateStatus("Connected", "#4CAF50")
                // DO NOT clear text box - user can edit and resend
            } else {
                Toast.makeText(this@MainActivity, "Send failed! Connection lost.", Toast.LENGTH_LONG).show()
                updateStatus("Connection Lost", "#FF5722")
                enableSendingMode(false)  // Allow reconnect
            }
        }
    }
    
    private fun disconnect() {
        lifecycleScope.launch {
            networkManager.disconnect()
            updateStatus("Disconnected", "#FF5722")
            enableSendingMode(false)
            showToast("Disconnected")
        }
    }
    
    private fun enableSendingMode(enabled: Boolean) {
        // Show/hide appropriate buttons
        btnConnect.visibility = if (enabled) View.GONE else View.VISIBLE
        btnClear.visibility = if (enabled) View.VISIBLE else View.GONE
        btnDisconnect.visibility = if (enabled) View.VISIBLE else View.GONE
        btnSend.visibility = if (enabled) View.VISIBLE else View.GONE
        btnBackspace.visibility = if (enabled) View.VISIBLE else View.GONE
        btnTab.visibility = if (enabled) View.VISIBLE else View.GONE
        btnEnter.visibility = if (enabled) View.VISIBLE else View.GONE
        btnDelete.visibility = if (enabled) View.VISIBLE else View.GONE
        
        etInput.isEnabled = enabled
        
        if (enabled) {
            etInput.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(etInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }
    
    private fun updateStatus(message: String, colorHex: String) {
        runOnUiThread {
            tvStatus.text = message
            tvStatus.setTextColor(android.graphics.Color.parseColor(colorHex))
        }
    }
    
    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        networkManager.disconnect()
    }
    
    // FIX: Don't disconnect when screen goes off!
    override fun onPause() {
        super.onPause()
        // Keep connection alive when app backgrounds or screen locks
        // Commented out: networkManager.disconnect()
    }
}
