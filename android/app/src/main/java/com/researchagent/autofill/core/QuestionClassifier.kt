package com.researchagent.autofill.core

data class FieldCandidate(val key: String, val score: Double, val learned: Boolean = false)

/**
 * Relaciona uma pergunta a campos do perfil por similaridade semântica leve:
 * palavras-chave com stemming, frases, palavras negativas, dicas pelas opções e
 * mapeamentos aprendidos (Seções 8 e 27). O aprendizado só aponta o CAMPO;
 * o valor continua vindo exclusivamente do perfil.
 */
class QuestionClassifier(
    private val learned: Map<String, String> = emptyMap(),
    private val extraFields: List<FieldDef> = emptyList()
) {

    fun classify(question: String, options: List<String> = emptyList(), limit: Int = 5): List<FieldCandidate> {
        val qTokens = Text.tokenSet(question)
        val qWords = Text.normalize(question).split(' ').filter { it.isNotBlank() }
        val qNorm = Text.normalize(question)
        if (qTokens.isEmpty()) return emptyList()

        val out = LinkedHashMap<String, FieldCandidate>()

        // 1) Mapeamentos aprendidos (pergunta idêntica ou muito parecida)
        val qKey = Text.questionKey(question)
        learned[qKey]?.let { out[it] = FieldCandidate(it, 100.0, learned = true) }
        if (out.isEmpty()) {
            for ((lk, field) in learned) {
                val sim = jaccardKeys(qKey, lk)
                if (sim >= 0.8) { out[field] = FieldCandidate(field, 50.0 * sim, learned = true); break }
            }
        }

        val optionProfile = OptionShape.of(options)

        for (def in ProfileSchema.fields + extraFields) {
            if (def.keywords.isEmpty()) continue
            if (def.negative.any { neg -> hasNegative(qWords, qNorm, neg) }) continue
            var score = 0.0
            for (kw in def.keywords) {
                val kwTokens = Text.tokens(kw)
                if (kwTokens.isEmpty()) continue
                if (qTokens.containsAll(kwTokens)) {
                    // frases mais longas são mais específicas
                    var s = kwTokens.size.toDouble() * 1.5
                    if (qNorm.contains(Text.normalize(kw))) s += 0.5
                    score = maxOf(score, s)
                }
            }
            if (score <= 0.0) continue
            score += typeAffinity(def, optionProfile)
            val prev = out[def.key]
            if (prev == null || prev.score < score) out[def.key] = FieldCandidate(def.key, score)
        }
        return out.values.sortedByDescending { it.score }.take(limit)
    }

    private fun hasNegative(words: List<String>, norm: String, neg: String): Boolean {
        val n = Text.normalize(neg)
        return if (n.contains(' ')) norm.contains(n) else words.any { it.startsWith(n) }
    }

    private fun jaccardKeys(a: String, b: String): Double {
        val sa = a.split(' ').filter { it.isNotBlank() }.toSet()
        val sb = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (sa.isEmpty() || sb.isEmpty()) return 0.0
        return sa.intersect(sb).size.toDouble() / sa.union(sb).size
    }

    private fun typeAffinity(def: FieldDef, shape: OptionShape): Double = when {
        shape == OptionShape.NONE -> 0.0
        shape == OptionShape.YES_NO && def.type == FieldType.BOOLEAN -> 1.0
        shape == OptionShape.YES_NO && def.type != FieldType.BOOLEAN -> -1.0
        shape == OptionShape.NUMERIC && def.type == FieldType.NUMBER -> 1.0
        shape == OptionShape.NUMERIC && def.type == FieldType.BOOLEAN -> -0.5
        else -> 0.0
    }
}

/** Forma do conjunto de opções, usada como dica semântica. */
enum class OptionShape {
    NONE, YES_NO, NUMERIC, TEXT;

    companion object {
        fun of(options: List<String>): OptionShape {
            if (options.isEmpty()) return NONE
            val yn = options.count { Answers.isYes(it) || Answers.isNo(it) }
            if (yn >= 2 && yn >= options.size - 1) return YES_NO
            val numeric = options.count { NumberRange.parse(it) != null }
            if (numeric >= (options.size + 1) / 2 && numeric >= 2) return NUMERIC
            return TEXT
        }
    }
}
