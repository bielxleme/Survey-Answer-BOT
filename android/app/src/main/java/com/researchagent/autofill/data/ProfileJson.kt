package com.researchagent.autofill.data

import com.researchagent.autofill.core.Category
import com.researchagent.autofill.core.FieldType
import com.researchagent.autofill.core.NumberRange
import com.researchagent.autofill.core.ProfileSchema
import com.researchagent.autofill.core.Text
import com.researchagent.autofill.core.UserProfile
import com.researchagent.autofill.core.Values
import org.json.JSONArray
import org.json.JSONObject

/**
 * Importação/exportação do perfil em JSON (Seções 4, 35, 36).
 * Exporta no template aninhado por categoria; importa tanto o template aninhado
 * quanto o formato plano — chaves desconhecidas viram campos personalizados.
 */
object ProfileJson {

    data class ImportResult(val profile: UserProfile, val imported: Int, val custom: Int, val ignored: Int)

    fun export(profile: UserProfile, includeEmpty: Boolean = true): String {
        val root = JSONObject()
        root.put("_formato", "research-agent-perfil/v1")
        for (cat in Category.values()) {
            if (cat == Category.PERSONALIZADO) continue
            val obj = JSONObject()
            for (def in ProfileSchema.byCategory(cat)) {
                val raw = profile.raw(def.key)
                if (raw == null && !includeEmpty) continue
                obj.put(def.key, typed(def.type, raw))
            }
            root.put(cat.jsonName, obj)
        }
        val custom = JSONObject()
        for (k in profile.customKeys) {
            val label = profile.customLabels[k] ?: k.removePrefix(ProfileSchema.CUSTOM_PREFIX)
            custom.put(label, profile.raw(k) ?: "")
        }
        root.put(Category.PERSONALIZADO.jsonName, custom)
        root.put("observacoes", profile.raw("observacoes") ?: "")
        return root.toString(2)
    }

    /** Template vazio para o usuário preencher fora do app. */
    fun template(): String = export(UserProfile(values = mapOf("pais" to "Brasil", "nacionalidade" to "Brasileira")))

    private fun typed(type: FieldType, raw: String?): Any {
        if (raw == null) return when (type) {
            FieldType.LIST -> JSONArray()
            FieldType.BOOLEAN, FieldType.NUMBER -> JSONObject.NULL
            else -> ""
        }
        return when (type) {
            FieldType.BOOLEAN -> Values.parseBoolean(raw) ?: raw
            FieldType.NUMBER -> Values.parseNumber(raw)?.let { if (it == Math.floor(it)) it.toLong() else it } ?: raw
            FieldType.LIST -> JSONArray().also { arr -> Text.splitList(raw).forEach { arr.put(it) } }
            else -> raw
        }
    }

    fun import(json: String, base: UserProfile = UserProfile()): ImportResult {
        val root = JSONObject(json.trim().removePrefix("\uFEFF"))
        var p = base
        var imported = 0
        var custom = 0
        var ignored = 0

        fun leafToString(v: Any?): String? = when (v) {
            null, JSONObject.NULL -> null
            is JSONArray -> (0 until v.length()).mapNotNull { i -> v.opt(i)?.takeIf { it != JSONObject.NULL }?.toString()?.trim() }
                .filter { it.isNotEmpty() }.joinToString("; ").ifBlank { null }
            is Boolean -> v.toString()
            is Number -> Values.formatNumber(v.toDouble())
            else -> v.toString().trim().ifBlank { null }
        }

        fun putCustom(label: String, value: String) {
            p = p.withCustom(label, value); custom++
        }

        fun walk(obj: JSONObject, path: String) {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                if (name.startsWith("_")) continue
                val v = obj.opt(name)
                if (name == Category.PERSONALIZADO.jsonName && v is JSONObject) {
                    val ck = v.keys()
                    while (ck.hasNext()) {
                        val label = ck.next()
                        leafToString(v.opt(label))?.let { putCustom(label, it) }
                    }
                    continue
                }
                if (v is JSONObject) {
                    val isSection = Category.values().any { it.jsonName == name } || ProfileSchema.resolveJsonName(name) == null
                    if (isSection) { walk(v, if (path.isEmpty()) name else "$path.$name"); continue }
                    // dicionário dentro de um campo (ex.: frequencia_redes_sociais) → vira campos personalizados
                    val ck = v.keys()
                    while (ck.hasNext()) {
                        val sub = ck.next()
                        leafToString(v.opt(sub))?.let { putCustom("$name $sub".replace('_', ' '), it) }
                    }
                    continue
                }
                val value = leafToString(v)
                if (value == null) { ignored++; continue }
                val def = ProfileSchema.resolveJsonName(name)
                if (def == null) {
                    putCustom(name.replace('_', ' '), value); continue
                }
                val converted: String? = when (def.type) {
                    FieldType.BOOLEAN -> Values.parseBoolean(value)?.toString()
                    FieldType.NUMBER -> if (v is Boolean) null else NumberRange.parse(value)
                        ?.takeIf { it.min == it.max }?.let { Values.formatNumber(it.min) }
                    FieldType.DATE -> Values.toIsoDate(value) ?: value
                    else -> if (v is Boolean) null else value
                }
                if (converted == null) { putCustom(name.replace('_', ' '), value); continue }
                // não sobrescreve um valor já importado de uma chave mais específica
                if (p.raw(def.key) != null && name != def.key) { ignored++; continue }
                p = p.with(def.key, converted); imported++
            }
        }
        walk(root, "")
        return ImportResult(p, imported, custom, ignored)
    }
}
