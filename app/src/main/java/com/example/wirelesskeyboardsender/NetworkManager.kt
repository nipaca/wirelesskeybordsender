package com.example.wirelesskeyboardsender

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import javax.crypto.spec.SecretKeySpec

class NetworkManager(private val context: Context) {
    
    companion object {
        const val PORT = 8566
        const val DISCOVERY_PORT = 8567
        const val DISCOVERY_TIMEOUT_MS = 3000L
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
            String.format("%d.%d.%d.%d", 
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    suspend fun discoverReceiver(discoveryPort: Int = DISCOVERY_PORT): String? = withContext(Dispatchers.IO) {
        var receiverIp: String? = null
        
        try {
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            val socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = DISCOVERY_TIMEOUT_MS.toInt()
            
            val requestMsg = "KEYBOARD_SENDER_SEARCH".toByteArray()
            val packet = DatagramPacket(requestMsg, requestMsg.size, broadcastAddr, discoveryPort)
            socket.send(packet)
            
            val buffer = ByteArray(1024)
            val responsePacket = DatagramPacket(buffer, buffer.size)
            
            while (receiverIp == null) {
                try {
                    socket.receive(responsePacket)
                    val response = String(responsePacket.data, 0, responsePacket.length).trim()
                    
                    if (response.startsWith("KEYBOARD_RECEIVER_HERE:")) {
                        receiverIp = response.substringAfter(":")
                        println("Found receiver at: $receiverIp")
                        break
                    }
                } catch (e: Exception) {
                    break
                }
            }
            
            socket.close()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        receiverIp
    }
    
    suspend fun connect(receiverIp: String, password: String, port: Int = PORT): Boolean = withContext(Dispatchers.IO) {
        try {
            sessionKey = EncryptionUtil.deriveKey(password)
            
            connectedSocket = Socket(receiverIp, port).apply {
                tcpNoDelay = true
                soTimeout = 10000
            }
            
            val authMsg = EncryptionUtil.encrypt("AUTH:$password", sessionKey)
            connectedSocket?.getOutputStream()?.write(authMsg.toByteArray())
            connectedSocket?.getOutputStream()?.flush()
            
            val inputStream = connectedSocket?.getInputStream()
            val responseBytes = ByteArray(1024)
            val bytesRead = inputStream?.read(responseBytes, 0, 1024) ?: 0
            val response = String(responseBytes, 0, bytesRead).trim()
            
            isConnected = (response == "AUTH_OK")
            
            if (!isConnected) {
                println("Auth failed - response: '$response'")
                disconnect()
            }
            
            isConnected
        } catch (e: Exception) {
            e.printStackTrace()
            isConnected = false
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
            try {
                if (connectedSocket != null && isConnected) {
                    val disconnectMsg = EncryptionUtil.encrypt("DISCONNECT_REQUEST", sessionKey)
                    connectedSocket?.getOutputStream()?.write(disconnectMsg.toByteArray())
                    connectedSocket?.getOutputStream()?.flush()
                }
            } catch (e: Exception) {
                // Ignore
            }
            
            isConnected = false
            connectedSocket?.close()
            connectedSocket = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun getConnectionStatus(): Boolean = isConnected
}
