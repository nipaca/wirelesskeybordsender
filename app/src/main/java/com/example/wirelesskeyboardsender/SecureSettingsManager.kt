package com.example.wirelesskeyboardsender

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureSettingsManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_keyboard_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SKEY_KEY_GEN,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

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
