package com.researchagent.autofill.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.researchagent.autofill.core.UserProfile
import org.json.JSONObject
import java.io.File

/**
 * Gerenciador de cópias de segurança permanentes.
 *
 * Garante que quando o usuário desinstala o aplicativo e o reinstala mais tarde,
 * todos os dados coletados (perfil, campos personalizados e aprendizados de pesquisas)
 * sejam recuperados automaticamente, sem exigir novo cadastro.
 */
object DataBackupManager {

    private const val TAG = "DataBackupManager"
    private const val FILE_NAME = "research_agent_auto_backup.json"
    private const val SUBDIR = "ResearchAgent"

    /** Salva uma cópia de segurança completa do perfil e aprendizado */
    fun saveBackup(context: Context, profile: UserProfile, learned: Map<String, String>) {
        if (profile.values.isEmpty() && learned.isEmpty()) return

        try {
            val root = JSONObject()
            root.put("_type", "research_agent_auto_backup")
            root.put("version", 1)
            root.put("timestamp", System.currentTimeMillis())
            root.put("profile", JSONObject(ProfileJson.export(profile)))

            val learnedObj = JSONObject()
            learned.forEach { (q, f) -> learnedObj.put(q, f) }
            root.put("learned", learnedObj)

            val jsonText = root.toString(2)

            // 1. Armazenamento interno de backup (incluído no Google Cloud Backup e hasFragileUserData)
            val backupDir = File(context.filesDir, "backup").apply { mkdirs() }
            val internalFile = File(backupDir, FILE_NAME)
            internalFile.writeText(jsonText, Charsets.UTF_8)

            // 2. Armazenamento externo do app
            runCatching {
                context.getExternalFilesDir(null)?.let { extDir ->
                    File(extDir, FILE_NAME).writeText(jsonText, Charsets.UTF_8)
                }
            }

            // 3. Documentos Públicos (não são apagados ao desinstalar o app)
            saveToPublicDocuments(context, jsonText)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao gravar backup automático", e)
        }
    }

    private fun saveToPublicDocuments(context: Context, jsonText: String) {
        // Tentativa 1: Pasta pública Documentos via File API
        runCatching {
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val dir = File(docs, SUBDIR).apply { mkdirs() }
            val target = File(dir, FILE_NAME)
            target.writeText(jsonText, Charsets.UTF_8)
        }

        // Tentativa 2: MediaStore para Android 10+ (Scoped Storage)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                // Verifica se já existe
                val projection = arrayOf(MediaStore.MediaColumns._ID)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(FILE_NAME)

                var existingUri: Uri? = null
                resolver.query(collection, projection, selection, selectionArgs, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        existingUri = Uri.withAppendedPath(collection, id.toString())
                    }
                }

                val targetUri = existingUri ?: run {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/" + SUBDIR)
                    }
                    resolver.insert(collection, values)
                }

                if (targetUri != null) {
                    resolver.openOutputStream(targetUri, "wt")?.use { os ->
                        os.write(jsonText.toByteArray(Charsets.UTF_8))
                    }
                }
            }
        }
    }

    /**
     * Tenta restaurar dados de backups anteriores caso o app tenha sido reinstalado.
     * Retorna o perfil e os aprendizados recuperados, ou null se não houver backup.
     */
    fun restoreBackup(context: Context): Pair<UserProfile, Map<String, String>>? {
        val candidates = ArrayList<String>()

        // 1. Arquivo de backup interno (restaurado via Google Cloud Backup ou mantido)
        runCatching {
            val f = File(File(context.filesDir, "backup"), FILE_NAME)
            if (f.exists() && f.length() > 0) candidates.add(f.readText(Charsets.UTF_8))
        }

        // 2. Pasta pública de Documentos
        runCatching {
            val f = File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), SUBDIR), FILE_NAME)
            if (f.exists() && f.length() > 0) candidates.add(f.readText(Charsets.UTF_8))
        }

        // 3. Pasta pública de Downloads
        runCatching {
            val f = File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SUBDIR), FILE_NAME)
            if (f.exists() && f.length() > 0) candidates.add(f.readText(Charsets.UTF_8))
        }

        // 4. Armazenamento externo de arquivos
        runCatching {
            context.getExternalFilesDir(null)?.let { extDir ->
                val f = File(extDir, FILE_NAME)
                if (f.exists() && f.length() > 0) candidates.add(f.readText(Charsets.UTF_8))
            }
        }

        // 5. Consulta via MediaStore (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                val projection = arrayOf(MediaStore.MediaColumns._ID)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(FILE_NAME)

                resolver.query(collection, projection, selection, selectionArgs, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        val uri = Uri.withAppendedPath(collection, id.toString())
                        resolver.openInputStream(uri)?.bufferedReader()?.use {
                            candidates.add(it.readText())
                        }
                    }
                }
            }
        }

        for (jsonText in candidates) {
            val parsed = parseBackup(jsonText)
            if (parsed != null && (parsed.first.values.isNotEmpty() || parsed.second.isNotEmpty())) {
                Log.i(TAG, "Backup restaurado com sucesso! ${parsed.first.values.size} campos e ${parsed.second.size} aprendizados.")
                return parsed
            }
        }

        return null
    }

    private fun parseBackup(jsonText: String): Pair<UserProfile, Map<String, String>>? {
        return runCatching {
            val root = JSONObject(jsonText.trim().removePrefix("\uFEFF"))
            val profileJson = root.optJSONObject("profile")?.toString() ?: return null
            val importRes = ProfileJson.import(profileJson)

            val learnedMap = LinkedHashMap<String, String>()
            root.optJSONObject("learned")?.let { lObj ->
                val keys = lObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    learnedMap[k] = lObj.optString(k)
                }
            }

            Pair(importRes.profile, learnedMap)
        }.getOrNull()
    }
}
