package com.logunovinc.workshopmanager.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface CookieStorage {
    fun read(): String?
    fun write(value: String)
}

/** AES-GCM envelope in no-backup private storage; the encryption key never leaves Keystore. */
class EncryptedCookieStorage(context: Context) : CookieStorage {
    private val file = AtomicFile(File(context.noBackupFilesDir, "session.cookies"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized override fun read(): String? {
        if (!file.baseFile.exists()) return null
        val bytes = file.openRead().use { it.readBytes() }
        require(bytes.size > 13) { "Invalid encrypted session storage" }
        val ivSize = bytes[0].toInt()
        require(ivSize == 12 && bytes.size > ivSize + 1)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 1 + ivSize)))
        return cipher.doFinal(bytes.copyOfRange(1 + ivSize, bytes.size)).toString(Charsets.UTF_8)
    }
    @Synchronized override fun write(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (failure: Exception) { file.failWrite(stream); throw failure }
    }
    private companion object { const val ALIAS = "workshop.session.aes.v1" }
}
