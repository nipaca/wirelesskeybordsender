package com.example.wirelesskeyboardsender

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import javax.crypto.spec.SecretKeySpec

class NetworkManager(private val context: Context) {
    
    companion object {
        const val PORT = 55555
        const val DISCOVERY_PORT = 55556
        const val TIMEOUT_SECONDS = 10
    }
    
    private var connectedSocket: Socket? = null
    private var isConnected = false
    private lateinit var sessionKey: SecretKeySpec
    
    fun setSessionKey(password: String) {
        sessionKey = EncryptionUtil.deriveKey(password)
    }
    
    fun getLocalIpAddress(): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo.ipAddress
            return String.format("%d.%d.%d.%d", 
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun discoverReceiver(): String? = withContext(Dispatchers.IO) {
        val broadcastMessage = "KEYBOARD_SENDER_SEARCH".toByteArray()
        var receiverIp: String? = null
        
        // Note: For full broadcast support, you'd need additional setup
        // This is simplified for reliability on Android
        // In production, you might use multicast DNS or direct IP entry
        
        withTimeoutOrNull(TIMEOUT_SECONDS * 1000L) {
            // Implementation depends on receiver discovery method
            receiverIp
        }
    }
    
    suspend fun connect(receiverIp: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            sessionKey = EncryptionUtil.deriveKey(password)
            
            connectedSocket = Socket(receiverIp, PORT).apply {
                tcpNoDelay = true
                soTimeout = 5000
            }
            
            val authMsg = EncryptionUtil.encrypt("AUTH:$password", sessionKey)
            connectedSocket?.getOutputStream()?.write(authMsg.toByteArray())
            connectedSocket?.getOutputStream()?.flush()
            
            val response = connectedSocket?.getInputStream()?.readBytes()?.decodeToString()
            
            isConnected = (response == "AUTH_OK")
            isConnected
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    suspend fun send(text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isConnected || connectedSocket == null) return@withContext false
            
            val encrypted = EncryptionUtil.encrypt(text, sessionKey)
            connectedSocket?.getOutputStream()?.write(encrypted.toByteArray())
            connectedSocket?.getOutputStream()?.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            isConnected = false
            false
        }
    }
    
    fun disconnect() {
        try {
            isConnected = false
            connectedSocket?.close()
            connectedSocket = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun getConnectionStatus(): Boolean = isConnected
}
