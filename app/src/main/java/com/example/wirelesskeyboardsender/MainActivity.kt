package com.example.wirelesskeyboardsender

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var settingsManager: SecureSettingsManager
    private var keyboardService: KeyboardService? = null
    private var isBound = false

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

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as KeyboardService.LocalBinder
            keyboardService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            keyboardService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settingsManager = SecureSettingsManager(this)

        initViews()
        setupListeners()

        val serviceIntent = Intent(this, KeyboardService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

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
        btnConnect.setOnClickListener {
            showPasswordAndSettingsDialog()
        }

        btnSend.setOnClickListener {
            sendText()
        }

        btnDisconnect.setOnClickListener {
            disconnect()
        }

        btnClear.setOnClickListener {
            etInput.text.clear()
            showToast("Text cleared")
        }

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

    private fun showPasswordAndSettingsDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 10)
        }

        val passwordField = EditText(this).apply {
            hint = "Session Password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(settingsManager.password)
        }

        val tcpPortField = EditText(this).apply {
            hint = "TCP Port (Default: 8566)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(settingsManager.tcpPort.toString())
        }

        val udpPortField = EditText(this).apply {
            hint = "Discovery UDP Port (Default: 8567)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(settingsManager.udpPort.toString())
        }

        layout.addView(TextView(this).apply { text = "Password (Saved Encrypted):" })
        layout.addView(passwordField)
        layout.addView(TextView(this).apply { text = "TCP Port:" })
        layout.addView(tcpPortField)
        layout.addView(TextView(this).apply { text = "Discovery Port:" })
        layout.addView(udpPortField)

        AlertDialog.Builder(this)
            .setTitle("Connection Settings")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("Connect") { dialog, _ ->
                val password = passwordField.text.toString().trim()
                val tcpPort = tcpPortField.text.toString().toIntOrNull() ?: NetworkManager.PORT
                val udpPort = udpPortField.text.toString().toIntOrNull() ?: NetworkManager.DISCOVERY_PORT

                if (password.length < 4) {
                    Toast.makeText(this, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    return@setPositiveButton
                }

                // Save settings in EncryptedSharedPreferences
                settingsManager.password = password
                settingsManager.tcpPort = tcpPort
                settingsManager.udpPort = udpPort

                dialog.dismiss()
                lifecycleScope.launch {
                    connectToDevice(password, tcpPort, udpPort)
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private suspend fun connectToDevice(password: String, tcpPort: Int, udpPort: Int) {
        val networkManager = keyboardService?.networkManager ?: return

        updateStatus("Checking network...", "#6d4aff")
        val localIp = networkManager.getLocalIpAddress()

        if (localIp == null) {
            Toast.makeText(this, "Could not get IP. Check WiFi.", Toast.LENGTH_SHORT).show()
            updateStatus("Disconnected", "#FF5722")
            return
        }

        updateStatus("Auto-discovering receiver...", "#6d4aff")
        val receiverIp = networkManager.discoverReceiver(udpPort)

        if (receiverIp.isNullOrEmpty()) {
            Toast.makeText(this, "No receiver found", Toast.LENGTH_SHORT).show()

            val manualIp = promptManualIp(localIp)
            if (manualIp.isNullOrEmpty()) {
                updateStatus("Disconnected", "#FF5722")
                return
            }

            connectWithIp(manualIp, password, tcpPort)
        } else {
            connectWithIp(receiverIp, password, tcpPort)
        }
    }

    private suspend fun connectWithIp(receiverIp: String, password: String, port: Int) {
        val service = keyboardService ?: return
        updateStatus("Connecting to $receiverIp...", "#6d4aff")

        val success = service.networkManager.connect(receiverIp, password, port)

        if (success) {
            settingsManager.lastIp = receiverIp
            service.startBackgroundLocks()
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

            val savedIp = settingsManager.lastIp
            if (savedIp.isNotEmpty()) {
                setText(savedIp)
            } else if (!localIpPrefix.isNullOrEmpty()) {
                val prefix = localIpPrefix.substringBeforeLast(".")
                hint = "Suggested: ${prefix}.___"
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Enter Receiver IP Manually")
            .setMessage("Find IP from receiver terminal")
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

        if (text.equals(escapeSequence, ignoreCase = true)) {
            disconnect()
            showToast("Disconnect requested")
            return
        }

        lifecycleScope.launch {
            val networkManager = keyboardService?.networkManager ?: return@launch
            updateStatus("Sending...", "#6d4aff")
            val success = networkManager.send(text)

            if (success) {
                showToast("✓ Text sent")
                updateStatus("Connected", "#4CAF50")
            } else {
                Toast.makeText(this@MainActivity, "Send failed! Connection lost.", Toast.LENGTH_LONG).show()
                updateStatus("Connection Lost", "#FF5722")
                enableSendingMode(false)
            }
        }
    }

    private fun disconnect() {
        lifecycleScope.launch {
            keyboardService?.stopBackgroundLocks()
            keyboardService?.networkManager?.disconnect()
            updateStatus("Disconnected", "#FF5722")
            enableSendingMode(false)
            showToast("Disconnected")
        }
    }

    private fun enableSendingMode(enabled: Boolean) {
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
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
