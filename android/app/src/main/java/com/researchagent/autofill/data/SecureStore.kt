package com.researchagent.autofill.data

import android.content.Context
import android.provider.Settings
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Armazenamento local criptografado em repouso (Seção 21):
 * AES-256-GCM com chave persistente vinculada ao aparelho e mantida em backup.
 *
 * Resistente à desinstalação:
 * - A chave é derivada do ANDROID_ID (permanece igual ao desinstalar e reinstalar o mesmo app no aparelho)
 *   e armazenada em arquivo mestre local preservado pelo 'hasFragileUserData' e 'AutoBackup'.
 * - Caso existam dados antigos criptografados com o AndroidKeyStore efêmero, faz o fallback automaticamente.
 *
 * Formato do arquivo: [1 byte versão][1 byte tamanho IV][IV][ciphertext+tag].
 */
class SecureStore(private val context: Context) {

    private val dir: File = File(context.filesDir, "secure").apply { mkdirs() }
    private val keyFile: File = File(dir, ".master_key")
    private val lock = Any()

    @Volatile
    private var cachedKey: SecretKey? = null

    private fun key(): SecretKey {
        cachedKey?.let { return it }
        synchronized(lock) {
            cachedKey?.let { return it }

            // 1. Tenta carregar chave mestra salva em disco (preservada em backup / hasFragileUserData)
            if (keyFile.exists() && keyFile.length() == 32L) {
                try {
                    val bytes = keyFile.readBytes()
                    if (bytes.size == 32) {
                        val k = SecretKeySpec(bytes, "AES")
                        cachedKey = k
                        return k
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Falha ao ler master_key", e)
                }
            }

            // 2. Derivação determinística estável por ANDROID_ID (idêntico ao desinstalar e reinstalar)
            val androidId = runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrNull().orEmpty().ifBlank { "research_agent_device_fallback" }

            val pkg = context.packageName.ifBlank { "com.researchagent.autofill" }
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update("ResearchAgent_Persistent_2026_Salt".toByteArray(Charsets.UTF_8))
            digest.update(pkg.toByteArray(Charsets.UTF_8))
            val keyBytes = digest.digest(androidId.toByteArray(Charsets.UTF_8))

            // Grava a chave mestra para sobrevivência e restauração
            try {
                keyFile.writeBytes(keyBytes)
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao salvar master_key", e)
            }

            val k = SecretKeySpec(keyBytes, "AES")
            cachedKey = k
            return k
        }
    }

    private fun legacyKeystoreKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            ks.getKey(ALIAS, null) as? SecretKey
        } catch (_: Exception) {
            null
        }
    }

    fun encrypt(plain: ByteArray): ByteArray {
        return try {
            val c = Cipher.getInstance(TRANSFORMATION)
            c.init(Cipher.ENCRYPT_MODE, key())
            val iv = c.iv
            val ct = c.doFinal(plain)
            byteArrayOf(VERSION_ENCRYPTED, iv.size.toByte()) + iv + ct
        } catch (e: Exception) {
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
                // 1. Tenta com a chave estável persistente
                try {
                    val c = Cipher.getInstance(TRANSFORMATION)
                    c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                    c.doFinal(data, 2 + ivLen, data.size - 2 - ivLen)
                } catch (e: Exception) {
                    // 2. Se falhar, tenta com a chave legada do Keystore (se existir)
                    val legacy = legacyKeystoreKey()
                    if (legacy != null) {
                        try {
                            val c = Cipher.getInstance(TRANSFORMATION)
                            c.init(Cipher.DECRYPT_MODE, legacy, GCMParameterSpec(128, iv))
                            c.doFinal(data, 2 + ivLen, data.size - 2 - ivLen)
                        } catch (_: Exception) {
                            throw e
                        }
                    } else {
                        throw e
                    }
                }
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
