package com.researchagent.autofill.core

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

/** Origem de um dado (Seção 3): fornecido, derivado ou indisponível. */
enum class DataOrigin { PROVIDED, DERIVED, UNAVAILABLE }

/** Status visual de cada campo na tela de perfil (Seção 6). */
enum class FieldStatus(val symbol: String, val label: String) {
    INFORMED("✓", "INFORMADO"),
    DERIVABLE("?", "DERIVÁVEL"),
    NEEDED("!", "NECESSÁRIO"),
    NOT_CONFIGURED("—", "NÃO CONFIGURADO")
}

data class ResolvedValue(
    val key: String,
    val value: String?,
    val origin: DataOrigin,
    val source: String
) {
    val isKnown: Boolean get() = origin != DataOrigin.UNAVAILABLE && !value.isNullOrBlank()
}

/**
 * Perfil do usuário: mapa plano "chave → valor" (strings), mais rótulos de campos personalizados.
 * Booleanos = "true"/"false"; datas = ISO yyyy-MM-dd; listas separadas por ";".
 */
data class UserProfile(
    val values: Map<String, String> = emptyMap(),
    val customLabels: Map<String, String> = emptyMap()
) {
    fun raw(key: String): String? = values[key]?.takeIf { it.isNotBlank() }

    fun with(key: String, value: String?): UserProfile {
        val m = values.toMutableMap()
        if (value.isNullOrBlank()) m.remove(key) else m[key] = value.trim()
        return copy(values = m)
    }

    fun withCustom(label: String, value: String?): UserProfile {
        val key = ProfileSchema.customKey(label)
        return with(key, value).copy(customLabels = customLabels + (key to label.trim()))
    }

    fun without(key: String): UserProfile = copy(values = values - key, customLabels = customLabels - key)

    fun fieldDef(key: String): FieldDef? =
        ProfileSchema.field(key) ?: ProfileSchema.customField(key, customLabels[key])

    val customKeys: List<String> get() = (values.keys + customLabels.keys).filter { ProfileSchema.isCustom(it) }.distinct()

    /**
     * Resolve um campo: valor fornecido ou derivado de forma lógica/matemática (Seção 10).
     * Nunca produz hipótese.
     */
    fun resolve(key: String, today: LocalDate = LocalDate.now()): ResolvedValue {
        raw(key)?.let { return ResolvedValue(key, it, DataOrigin.PROVIDED, "profile.$key") }
        val derived: Pair<String, String>? = when (key) {
            "idade" -> Values.parseDate(raw("data_nascimento"))?.let {
                Period.between(it, today).years.toString() to "derived:data_nascimento"
            }
            "quantidade_filhos" -> when (Values.parseBoolean(raw("tem_filhos"))) {
                false -> "0" to "derived:tem_filhos"
                else -> raw("idade_filhos")?.let { Text.splitList(it).size.toString() to "derived:idade_filhos" }
            }
            "tem_filhos" -> Values.parseNumber(raw("quantidade_filhos"))?.let {
                (it > 0).toString() to "derived:quantidade_filhos"
            }
            "possui_animais" -> when {
                raw("animais") != null -> "true" to "derived:animais"
                Values.parseNumber(raw("quantidade_animais"))?.let { it > 0 } == true -> "true" to "derived:quantidade_animais"
                Values.parseNumber(raw("quantidade_animais")) == 0.0 -> "false" to "derived:quantidade_animais"
                else -> null
            }
            "quantidade_animais" -> if (Values.parseBoolean(raw("possui_animais")) == false) "0" to "derived:possui_animais" else null
            "possui_carro" -> raw("veiculos")?.let { "true" to "derived:veiculos" }
            "estudante" -> if (raw("situacao_emprego")?.let { Text.normalize(it).contains("estudante") } == true)
                "true" to "derived:situacao_emprego" else null
            "sistema_celular" -> raw("celular_marca")?.let {
                val n = Text.normalize(it)
                when {
                    n.contains("iphone") || n.contains("apple") -> "iOS" to "derived:celular_marca"
                    listOf("samsung", "motorola", "xiaomi", "redmi", "poco", "lg", "asus", "realme", "oppo", "pixel", "oneplus")
                        .any { b -> n.split(' ').contains(b) } -> "Android" to "derived:celular_marca"
                    else -> null
                }
            }
            "compras_online" -> raw("sites_compras")?.let { "true" to "derived:sites_compras" }
            "usa_delivery" -> raw("apps_delivery")?.let { "true" to "derived:apps_delivery" }
            else -> null
        }
        return if (derived != null) ResolvedValue(key, derived.first, DataOrigin.DERIVED, derived.second)
        else ResolvedValue(key, null, DataOrigin.UNAVAILABLE, "")
    }

    fun status(key: String, neededKeys: Set<String> = emptySet()): FieldStatus {
        if (raw(key) != null) return FieldStatus.INFORMED
        if (resolve(key).isKnown) return FieldStatus.DERIVABLE
        if (key in neededKeys) return FieldStatus.NEEDED
        return FieldStatus.NOT_CONFIGURED
    }

    /** Subconjunto mínimo para enviar à IA (Seção 21). Campos sensíveis nunca saem se [includeSensitive] = false. */
    fun subset(keys: Collection<String>, includeSensitive: Boolean = false): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (k in keys) {
            val def = fieldDef(k) ?: continue
            if (def.sensitive && !includeSensitive) continue
            val r = resolve(k)
            if (r.isKnown) out[k] = r.value!!
        }
        return out
    }
}

/** Conversões de valores usadas pelo motor de respostas e pela UI. */
object Values {
    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE
    private val BR = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun parseBoolean(v: String?): Boolean? = when (Text.normalize(v)) {
        "true", "sim", "s", "yes", "y", "1", "verdadeiro", "possuo", "tenho" -> true
        "false", "nao", "n", "no", "0", "falso", "nenhum", "nenhuma" -> false
        else -> null
    }

    fun parseDate(v: String?): LocalDate? {
        if (v.isNullOrBlank()) return null
        val s = v.trim()
        return runCatching { LocalDate.parse(s, ISO) }.getOrNull()
            ?: runCatching { LocalDate.parse(s, BR) }.getOrNull()
            ?: Regex("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})$").find(s)?.let {
                runCatching {
                    LocalDate.of(it.groupValues[3].toInt(), it.groupValues[2].toInt(), it.groupValues[1].toInt())
                }.getOrNull()
            }
    }

    fun formatDate(d: LocalDate, pattern: String = "dd/MM/yyyy"): String = d.format(DateTimeFormatter.ofPattern(pattern))

    fun toIsoDate(v: String?): String? = parseDate(v)?.format(ISO)

    /**
     * Número em formatos BR/EN: "1.500", "1.500,50", "1500.5", "R$ 3 mil", "10k".
     */
    fun parseNumber(v: String?): Double? {
        if (v.isNullOrBlank()) return null
        val n = v.lowercase()
        val m = Regex("(\\d[\\d.,]*)\\s*(mil\\b|k\\b)?").find(n) ?: return null
        val base = parseNumericToken(m.groupValues[1]) ?: return null
        return if (m.groupValues[2].isNotEmpty()) base * 1000 else base
    }

    fun parseNumericToken(tok: String): Double? {
        var t = tok.trim().trimEnd('.', ',')
        if (t.isEmpty()) return null
        val hasDot = t.contains('.')
        val hasComma = t.contains(',')
        t = when {
            hasDot && hasComma -> if (t.lastIndexOf(',') > t.lastIndexOf('.')) t.replace(".", "").replace(',', '.')
                                  else t.replace(",", "")
            hasComma -> if (Regex("^\\d{1,3}(,\\d{3})+$").matches(t)) t.replace(",", "") else t.replace(',', '.')
            hasDot -> if (Regex("^\\d{1,3}(\\.\\d{3})+$").matches(t)) t.replace(".", "") else t
            else -> t
        }
        return t.toDoubleOrNull()
    }

    fun formatNumber(d: Double): String =
        if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()

    fun digits(v: String?): String = v.orEmpty().filter { it.isDigit() }

    /** Formatação de campos com máscara (Seção 28) — somente com dados fornecidos. */
    fun formatForField(key: String, value: String): String {
        val d = digits(value)
        return when (key) {
            "cpf" -> if (d.length == 11) "${d.substring(0, 3)}.${d.substring(3, 6)}.${d.substring(6, 9)}-${d.substring(9)}" else value
            "cep" -> if (d.length == 8) "${d.substring(0, 5)}-${d.substring(5)}" else value
            "telefone" -> when (d.length) {
                11 -> "(${d.substring(0, 2)}) ${d.substring(2, 7)}-${d.substring(7)}"
                10 -> "(${d.substring(0, 2)}) ${d.substring(2, 6)}-${d.substring(6)}"
                else -> value
            }
            else -> value
        }
    }
}
