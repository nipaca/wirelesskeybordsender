package com.example.wirelesskeyboardsender

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureSettingsManager(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context)

    private fun createPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "secure_keyboard_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                // Delete invalid/corrupt keystore file if present and retry
                context.deleteSharedPreferences("secure_keyboard_prefs")
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    "secure_keyboard_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e2: Exception) {
                e2.printStackTrace()
                // Safe fallback to standard preferences to prevent startup crash
                context.getSharedPreferences("keyboard_prefs_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    var password: String
        get() = prefs.getString("KEY_PASSWORD", "") ?: ""
        set(value) = prefs.edit().putString("KEY_PASSWORD", value).apply()

    var lastIp: String
        get() = prefs.getString("KEY_LAST_IP", "") ?: ""
        set(value) = prefs.edit().putString("KEY_LAST_IP", value).apply()

    var tcpPort: Int
        get() = prefs.getInt("KEY_TCP_PORT", NetworkManager.PORT)
        set(value) = prefs.edit().putInt("KEY_TCP_PORT", value).apply()

    var udpPort: Int
        get() = prefs.getInt("KEY_UDP_PORT", NetworkManager.DISCOVERY_PORT)
        set(value) = prefs.edit().putInt("KEY_UDP_PORT", value).apply()
}
