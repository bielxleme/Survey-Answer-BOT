package com.researchagent.autofill.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Armazenamento local criptografado em repouso (Seção 21):
 * AES-256-GCM com chave não exportável do Android Keystore.
 * Formato do arquivo: [1 byte versão][1 byte tamanho IV][IV][ciphertext+tag].
 */
class SecureStore(context: Context) {

    private val dir: File = File(context.filesDir, "secure").apply { mkdirs() }
    private val lock = Any()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        return try {
            val c = Cipher.getInstance(TRANSFORMATION)
            c.init(Cipher.ENCRYPT_MODE, key())
            val iv = c.iv
            val ct = c.doFinal(plain)
            byteArrayOf(VERSION_ENCRYPTED, iv.size.toByte()) + iv + ct
        } catch (e: Exception) {
            // Keystore indisponível (raro, alguns emuladores): mantém funcionamento, sinaliza no log.
            Log.e(TAG, "Falha ao criptografar; gravando sem criptografia", e)
            byteArrayOf(VERSION_PLAIN) + plain
        }
    }

    fun decrypt(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        return when (data[0]) {
            VERSION_PLAIN -> data.copyOfRange(1, data.size)
            VERSION_ENCRYPTED -> {
                val ivLen = data[1].toInt()
                val iv = data.copyOfRange(2, 2 + ivLen)
                val c = Cipher.getInstance(TRANSFORMATION)
                c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                c.doFinal(data, 2 + ivLen, data.size - 2 - ivLen)
            }
            else -> throw IllegalStateException("Formato desconhecido")
        }
    }

    fun writeText(name: String, text: String): Unit = synchronized(lock) {
        val target = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(encrypt(text.toByteArray(Charsets.UTF_8)))
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun readText(name: String): String? {
        synchronized(lock) {
            val f = File(dir, name)
            if (!f.exists()) return null
            return try {
                decrypt(f.readBytes()).toString(Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao ler $name", e)
                null
            }
        }
    }

    fun encryptString(value: String): String =
        android.util.Base64.encodeToString(encrypt(value.toByteArray(Charsets.UTF_8)), android.util.Base64.NO_WRAP)

    fun decryptString(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return try {
            decrypt(android.util.Base64.decode(value, android.util.Base64.NO_WRAP)).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun delete(name: String): Boolean = synchronized(lock) { File(dir, name).delete() }

    companion object {
        private const val TAG = "SecureStore"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "research_agent_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val VERSION_ENCRYPTED: Byte = 1
        private const val VERSION_PLAIN: Byte = 0
    }
}
