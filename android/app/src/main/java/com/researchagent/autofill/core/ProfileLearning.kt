package com.researchagent.autofill.core

/**
 * Aprendizado do perfil a partir de respostas manuais (Seções 7 e 26).
 * Converte a resposta dada pelo usuário no formato do campo e detecta conflitos.
 * Nenhuma alteração é persistida sem autorização explícita do usuário (feito pela UI).
 */
object ProfileLearning {

    /** Valor a gravar no campo, ou null se a resposta não determina o campo com exatidão. */
    fun valueFor(def: FieldDef, answers: List<String>, existing: String? = null): String? {
        val clean = answers.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return null
        return when (def.type) {
            FieldType.BOOLEAN -> clean.singleOrNull()?.let { a ->
                when {
                    Answers.isYes(a) -> "true"
                    Answers.isNo(a) || Answers.isNoneOption(a) -> "false"
                    else -> Values.parseBoolean(a)?.toString()
                }
            }
            FieldType.NUMBER -> clean.singleOrNull()?.let { a ->
                if (Answers.isNoneOption(a) || Answers.isNo(a)) "0"
                else NumberRange.parse(a)?.takeIf { it.min == it.max }?.let { Values.formatNumber(it.min) }
            }
            FieldType.DATE -> clean.singleOrNull()?.let { Values.toIsoDate(it) }
            FieldType.LIST -> {
                val items = clean.filterNot { Answers.isNoneOption(it) || Answers.isOtherOption(it) }
                val merged = LinkedHashSet<String>()
                Text.splitList(existing).forEach { merged += it }
                for (i in items) if (merged.none { Text.looselyEquals(it, i) }) merged += i
                merged.joinToString("; ").ifBlank { null }
            }
            else -> clean.filterNot { Answers.isOtherOption(it) }.joinToString(", ").ifBlank { null }
        }
    }

    /** Existe conflito entre o que está no perfil e a nova resposta? */
    fun isConflict(def: FieldDef, existing: String?, newValue: String?): Boolean {
        if (existing.isNullOrBlank() || newValue.isNullOrBlank()) return false
        return when (def.type) {
            FieldType.BOOLEAN -> Values.parseBoolean(existing) != Values.parseBoolean(newValue)
            FieldType.NUMBER -> Values.parseNumber(existing) != Values.parseNumber(newValue)
            FieldType.DATE -> Values.parseDate(existing) != Values.parseDate(newValue)
            FieldType.LIST -> false // listas são acumulativas
            else -> !Text.looselyEquals(existing, newValue)
        }
    }

    /** Texto amigável do valor armazenado. */
    fun display(def: FieldDef?, value: String?): String {
        if (value.isNullOrBlank()) return "NÃO INFORMADO"
        return when (def?.type) {
            FieldType.BOOLEAN -> when (Values.parseBoolean(value)) { true -> "Sim"; false -> "Não"; null -> value }
            FieldType.DATE -> Values.parseDate(value)?.let { Values.formatDate(it) } ?: value
            FieldType.LIST -> Text.splitList(value).joinToString(", ")
            else -> value
        }
    }
}
