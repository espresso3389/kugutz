package jp.espresso3389.kugutz.db

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SqlcipherPassphraseManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getOrCreatePassphrase(): String {
        val enc = prefs.getString(KEY_CIPHERTEXT, null)
        val iv = prefs.getString(KEY_IV, null)
        if (!enc.isNullOrBlank() && !iv.isNullOrBlank()) {
            return decrypt(enc, iv)
        }
        val passphrase = randomPassphrase()
        val encrypted = encrypt(passphrase)
        prefs.edit()
            .putString(KEY_CIPHERTEXT, encrypted.first)
            .putString(KEY_IV, encrypted.second)
            .apply()
        return passphrase
    }

    private fun encrypt(passphrase: String): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP) to
            Base64.encodeToString(iv, Base64.NO_WRAP)
    }

    private fun decrypt(ciphertext: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val ivBytes = Base64.decode(iv, Base64.NO_WRAP)
        val spec = GCMParameterSpec(128, ivBytes)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
        val plain = cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP))
        return String(plain, Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) {
            return existing
        }
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun randomPassphrase(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    companion object {
        private const val PREFS = "sqlcipher_passphrase"
        private const val KEY_CIPHERTEXT = "ciphertext"
        private const val KEY_IV = "iv"
        private const val KEY_ALIAS = "kugutz_sqlcipher_key"
    }
}
