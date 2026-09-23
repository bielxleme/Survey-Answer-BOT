package com.researchagent.autofill.core

import java.text.Normalizer

/**
 * Normalização de texto multilíngue usada por todo o núcleo semântico.
 * Remove acentos, pontuação e aplica um "stemming" leve (plural e gênero em PT/ES).
 */
object Text {

    private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val NON_ALNUM = Regex("[^a-z0-9+]+")

    val STOPWORDS: Set<String> = setOf(
        // português
        "a", "o", "e", "de", "da", "do", "das", "dos", "em", "no", "na", "nos", "nas", "um", "uma",
        "uns", "umas", "para", "pra", "por", "com", "que", "qual", "quais", "seu", "sua", "seus", "suas",
        "voce", "voces", "vc", "ao", "aos", "as", "os", "se", "eh", "e", "ou", "sobre", "atualmente",
        "hoje", "sao", "esta", "este", "essa", "esse", "isso", "meu", "minha", "atual",
        // inglês
        "the", "an", "of", "your", "you", "do", "does", "is", "are", "what", "which", "in", "to", "for",
        "and", "or", "please", "select", "currently", "my", "i", "me",
        // espanhol
        "el", "la", "los", "las", "su", "sus", "usted", "tu", "cual", "del", "y"
    )

    /** minúsculas, sem acentos, sem pontuação, espaços simples. */
    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val lower = input.lowercase()
        val noAccents = DIACRITICS.replace(Normalizer.normalize(lower, Normalizer.Form.NFD), "")
        return NON_ALNUM.replace(noAccents, " ").trim()
    }

    fun stem(word: String): String {
        var w = word
        if (w.length > 3 && w.endsWith("s")) w = w.dropLast(1)
        if (w.length > 4 && (w.endsWith("a") || w.endsWith("o"))) w = w.dropLast(1)
        return w
    }

    /** Tokens significativos (sem stopwords) com stemming. */
    fun tokens(input: String?, keepStopwords: Boolean = false): List<String> =
        normalize(input).split(' ')
            .filter { it.isNotBlank() && (keepStopwords || it !in STOPWORDS) }
            .map { stem(it) }

    fun tokenSet(input: String?): Set<String> = tokens(input).toSet()

    fun jaccard(a: String?, b: String?): Double {
        val sa = tokenSet(a)
        val sb = tokenSet(b)
        if (sa.isEmpty() || sb.isEmpty()) return 0.0
        val inter = sa.intersect(sb).size.toDouble()
        return inter / (sa.union(sb).size.toDouble())
    }

    /** true se todas as palavras de [needle] aparecem em [haystack] (após normalização). */
    fun containsAllTokens(haystack: Set<String>, needle: String): Boolean {
        val n = tokens(needle)
        return n.isNotEmpty() && haystack.containsAll(n)
    }

    /** Igualdade tolerante (acentos, caixa, "(a)", pontuação). */
    fun looselyEquals(a: String?, b: String?): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true
        val ta = tokens(a, keepStopwords = true)
        val tb = tokens(b, keepStopwords = true)
        return ta.isNotEmpty() && ta == tb
    }

    /** Hash estável de uma pergunta para mapeamentos aprendidos. */
    fun questionKey(question: String): String = tokens(question).sorted().joinToString(" ")

    fun splitList(value: String?): List<String> =
        value.orEmpty().split(';', '\n', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
