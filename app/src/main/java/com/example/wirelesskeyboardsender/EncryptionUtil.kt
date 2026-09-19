package com.example.wirelesskeyboardsender

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.security.MessageDigest

object EncryptionUtil {
    
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/ECB/PKCS5Padding"
    
    fun deriveKey(password: String): SecretKeySpec {
        val shaBytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        val aesKey = shaBytes.copyOf(16) // AES-128 needs 16 bytes
        return SecretKeySpec(aesKey, ALGORITHM)
    }
    
    fun encrypt(plaintext: String, key: SecretKeySpec): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plaintext.toByteArray())
        return Base64.encodeToString(encrypted, Base64.DEFAULT)
    }
    
    fun decrypt(encoded: String, key: SecretKeySpec): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key)
        val decrypted = cipher.doFinal(Base64.decode(encoded, Base64.DEFAULT))
        return decrypted.toString(Charsets.UTF_8)
    }
}
