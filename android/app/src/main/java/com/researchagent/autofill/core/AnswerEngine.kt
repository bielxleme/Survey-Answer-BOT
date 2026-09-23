package com.researchagent.autofill.core

import java.time.LocalDate

/** Classificação de confiança (Seção 9). */
enum class Confidence(val emoji: String, val label: String) {
    HIGH("🟢", "Alta"), MEDIUM("🟡", "Média"), LOW("🟠", "Baixa"), UNKNOWN("🔴", "Ausente");

    companion object {
        fun of(value: Double): Confidence = when {
            value >= 0.85 -> HIGH
            value >= 0.65 -> MEDIUM
            value > 0.0 -> LOW
            else -> UNKNOWN
        }
    }
}

enum class AnswerAction { ANSWER, ASK_USER }

/**
 * Contrato de resposta (Seção 24). É o ÚNICO formato que o executor de automação aceita —
 * texto livre do modelo nunca é interpretado como comando.
 */
data class AnswerDecision(
    val action: AnswerAction,
    /** Para múltipla escolha: vários valores; para texto/escolha única: um valor. */
    val answers: List<String> = emptyList(),
    val confidence: Double = 0.0,
    val source: String? = null,
    val reason: String = "",
    val fieldKey: String? = null,
    val engine: String = "rules"
) {
    val needsUser: Boolean get() = action == AnswerAction.ASK_USER
    val level: Confidence get() = if (needsUser) Confidence.UNKNOWN else Confidence.of(confidence)
    val display: String get() = answers.joinToString(" | ")

    companion object {
        fun ask(reason: String, fieldKey: String? = null, engine: String = "rules") =
            AnswerDecision(AnswerAction.ASK_USER, emptyList(), 0.0, null, reason, fieldKey, engine)
    }
}

/** Utilidades de Sim/Não multilíngue. */
object Answers {
    private val YES_START = setOf("sim", "yes", "si", "tenho", "possuo", "sou", "uso", "utilizo", "faco", "true", "pratico", "concordo")
    private val NO_START = setOf("nao", "no", "nenhum", "nenhuma", "none", "not", "never", "false", "nunca", "jamais")

    fun isYes(option: String): Boolean {
        val w = Text.normalize(option).split(' ').firstOrNull() ?: return false
        return w in YES_START
    }

    fun isNo(option: String): Boolean {
        val w = Text.normalize(option).split(' ').firstOrNull() ?: return false
        return w in NO_START
    }

    fun isNoneOption(option: String): Boolean {
        val n = Text.normalize(option)
        return n.startsWith("nenhum") || n.startsWith("nenhuma") || n.startsWith("none") ||
            n.startsWith("nao possuo") || n.startsWith("nao tenho") || n.startsWith("nao uso") ||
            n.startsWith("nao utilizo") || n.startsWith("ningun")
    }

    fun isOtherOption(option: String): Boolean {
        val n = Text.normalize(option)
        return n == "outro" || n == "outra" || n == "outros" || n == "other" || n == "otro" ||
            n.startsWith("outro ") || n.startsWith("outra ") || n.startsWith("other ") || n.startsWith("outros ")
    }

    fun isRefuseOption(option: String): Boolean {
        val n = Text.normalize(option)
        return n.contains("prefiro nao") || n.contains("prefer not") || n.contains("nao sei") || n.contains("don t know") ||
            n.contains("nao quero responder")
    }
}

/** Intervalo numérico interpretado a partir do texto de uma opção. */
data class NumberRange(val min: Double, val max: Double) {
    operator fun contains(v: Double): Boolean = v >= min && v <= max

    companion object {
        private val NUM = Regex("(\\d[\\d.,]*)\\s*(mil\\b|k\\b)?")

        fun parse(text: String, minimumWage: Double = 0.0): NumberRange? {
            val lower = text.lowercase()
            val n = Text.normalize(text)
            val nums = NUM.findAll(lower).mapNotNull { m ->
                val base = Values.parseNumericToken(m.groupValues[1]) ?: return@mapNotNull null
                if (m.groupValues[2].isNotEmpty()) base * 1000 else base
            }.toList()
            if (nums.isEmpty()) return null
            val mult = if (n.contains("salario") || Regex("\\bsm\\b").containsMatchIn(n)) {
                if (minimumWage <= 0.0) return null else minimumWage
            } else 1.0
            val a = nums[0] * mult
            val b = nums.getOrNull(1)?.times(mult)
            val below = listOf("menos de", "abaixo de", "ate", "under", "less than", "below", "up to", "menor que", "inferior a", "hasta")
            val above = listOf("mais de", "acima de", "ou mais", "over", "more than", "or more", "above", "maior que",
                "superior a", "a partir de", "mas de", "or older", "ou acima")
            val strictBelow = listOf("menos de", "abaixo de", "under", "less than", "below", "menor que", "inferior a")
            val strictAbove = listOf("mais de", "acima de", "over", "more than", "above", "maior que", "superior a", "mas de")
            return when {
                b != null -> NumberRange(minOf(a, b), maxOf(a, b))
                lower.trim().endsWith("+") || above.any { SurveyAnalyzer.containsPhrase(n, it) } ->
                    NumberRange(if (strictAbove.any { SurveyAnalyzer.containsPhrase(n, it) }) a + 0.000001 else a, Double.MAX_VALUE)
                below.any { SurveyAnalyzer.containsPhrase(n, it) } ->
                    NumberRange(-Double.MAX_VALUE, if (strictBelow.any { SurveyAnalyzer.containsPhrase(n, it) }) a - 0.000001 else a)
                else -> NumberRange(a, a)
            }
        }
    }
}

/** Contexto da pesquisa atual (Seção 30): respostas já dadas nesta pesquisa. */
class SurveyContext {
    val answersByField = LinkedHashMap<String, String>()
    val answersByQuestion = LinkedHashMap<String, List<String>>()
    fun remember(question: SurveyQuestion, decision: AnswerDecision) {
        if (decision.needsUser) return
        answersByQuestion[question.key] = decision.answers
        decision.fieldKey?.let { answersByField[it] = decision.display }
    }
    fun clear() { answersByField.clear(); answersByQuestion.clear() }
}

/**
 * Motor de respostas determinístico: nunca inventa. Só responde com o que está no perfil
 * (fornecido ou derivado de forma lógica) e caso contrário retorna ASK_USER.
 */
class AnswerEngine(
    private val classifier: QuestionClassifier,
    private val minimumWage: Double = 0.0,
    private val today: LocalDate = LocalDate.now()
) {

    fun answer(q: SurveyQuestion, profile: UserProfile, ctx: SurveyContext? = null): AnswerDecision {
        // Resposta manual já dada para a mesma pergunta nesta pesquisa
        ctx?.answersByQuestion?.get(q.key)?.let {
            return AnswerDecision(AnswerAction.ANSWER, it, 1.0, "session", "Resposta já dada nesta pesquisa (pelo usuário ou pelo perfil).")
        }
        val candidates = classifier.classify(q.text, q.optionTexts)

        // Múltipla escolha com opções que são, cada uma, um fato booleano (ex.: "Quais destes você possui?")
        if (q.type == QuestionType.MULTI_CHOICE) {
            val listCandidate = candidates.firstOrNull { profile.fieldDef(it.key)?.type == FieldType.LIST }
            if (listCandidate == null) {
                perOptionBoolean(q, profile)?.let { return it }
            }
        }
        if (candidates.isEmpty()) {
            return AnswerDecision.ask("Não identifiquei a qual dado do perfil esta pergunta se refere.")
        }

        var firstMissing: String? = null
        val decisions = ArrayList<AnswerDecision>()
        for (c in candidates.take(3)) {
            val def = profile.fieldDef(c.key) ?: continue
            val resolved = profile.resolve(c.key, today)
            if (!resolved.isKnown) { if (firstMissing == null) firstMissing = c.key; continue }
            val d = answerFromValue(q, def, resolved) ?: continue
            if (!d.needsUser) decisions += d.copy(confidence = adjustForCandidate(d.confidence, c, candidates))
            if (decisions.isNotEmpty() && c == candidates.first()) break
        }
        if (decisions.isEmpty()) {
            val key = firstMissing ?: candidates.first().key
            val label = profile.fieldDef(key)?.label ?: key
            return AnswerDecision.ask(
                if (firstMissing != null) "O perfil não informa \"$label\"." else "O valor do perfil (\"$label\") não corresponde a nenhuma opção.",
                key
            )
        }
        // Conflito entre candidatos fortes com respostas diferentes → reduz confiança
        val best = decisions.maxByOrNull { it.confidence }!!
        val disagree = decisions.any { it !== best && it.display != best.display && it.confidence >= best.confidence - 0.1 }
        return if (disagree) best.copy(confidence = minOf(best.confidence, 0.6), reason = best.reason + " (ambiguidade entre campos)") else best
    }

    private fun adjustForCandidate(conf: Double, c: FieldCandidate, all: List<FieldCandidate>): Double {
        if (c.learned) return conf
        val second = all.firstOrNull { it.key != c.key }
        return if (second != null && second.score >= c.score - 0.25 && c != all.first()) conf - 0.15 else conf
    }

    fun answerFromValue(q: SurveyQuestion, def: FieldDef, rv: ResolvedValue): AnswerDecision? {
        val value = rv.value ?: return null
        val derivedPenalty = if (rv.origin == DataOrigin.DERIVED) 0.03 else 0.0
        val src = rv.source
        fun ok(ans: List<String>, conf: Double, why: String) =
            AnswerDecision(AnswerAction.ANSWER, ans, conf - derivedPenalty, src, why, def.key)

        return when (q.type) {
            QuestionType.TEXT, QuestionType.NUMBER, QuestionType.DATE -> {
                val text = formatTextAnswer(q, def, value) ?: return null
                ok(listOf(text), 0.95, "Valor do perfil: ${def.label}.")
            }
            QuestionType.SLIDER -> {
                val num = Values.parseNumber(value) ?: return null
                if (def.type != FieldType.NUMBER) return null
                ok(listOf(Values.formatNumber(num)), 0.9, "Valor numérico do perfil: ${def.label}.")
            }
            QuestionType.SINGLE_CHOICE, QuestionType.DROPDOWN -> {
                val opts = q.optionTexts
                if (opts.isEmpty() && q.type == QuestionType.DROPDOWN) {
                    // opções ainda não visíveis: o executor abre a lista e procura este texto
                    return ok(listOf(choiceTextForDropdown(def, value) ?: return null), 0.8, "Valor a procurar na lista.")
                }
                matchSingle(def, value, opts)?.let { (opt, conf, why) -> ok(listOf(opt), conf, why) }
            }
            QuestionType.MULTI_CHOICE -> {
                val (sel, conf, why) = matchMulti(def, value, q.optionTexts) ?: return null
                ok(sel, conf, why)
            }
        }
    }

    private fun choiceTextForDropdown(def: FieldDef, value: String): String? = when (def.type) {
        FieldType.BOOLEAN -> Values.parseBoolean(value)?.let { if (it) "Sim" else "Não" }
        FieldType.LIST -> Text.splitList(value).singleOrNull()
        FieldType.DATE -> Values.parseDate(value)?.let { Values.formatDate(it) }
        else -> value
    }

    fun formatTextAnswer(q: SurveyQuestion, def: FieldDef, value: String): String? {
        val qn = Text.normalize(q.text + " " + q.hint)
        return when (def.type) {
            FieldType.BOOLEAN -> Values.parseBoolean(value)?.let { if (it) "Sim" else "Não" }
            FieldType.NUMBER -> Values.parseNumber(value)?.let { Values.formatNumber(it) }
            FieldType.DATE -> {
                val d = Values.parseDate(value) ?: return null
                when {
                    qn.contains("ano de nascimento") || qn.contains("year of birth") || qn.contains("ano em que nasceu") -> d.year.toString()
                    qn.contains("mm dd") -> Values.formatDate(d, "MM/dd/yyyy")
                    qn.contains("aaaa mm") || qn.contains("yyyy mm") -> Values.formatDate(d, "yyyy-MM-dd")
                    else -> Values.formatDate(d)
                }
            }
            FieldType.LIST -> Text.splitList(value).joinToString(", ")
            else -> Values.formatForField(def.key, value)
        }.takeIf { !it.isNullOrBlank() }
    }

    /** Escolha única: retorna (opção, confiança, motivo). */
    fun matchSingle(def: FieldDef, value: String, options: List<String>): Triple<String, Double, String>? {
        if (options.isEmpty()) return null
        when (def.type) {
            FieldType.BOOLEAN -> {
                val b = Values.parseBoolean(value) ?: return null
                val opt = if (b) options.firstOrNull { Answers.isYes(it) }
                          else options.firstOrNull { Answers.isNo(it) } ?: options.firstOrNull { Answers.isNoneOption(it) }
                return opt?.let { Triple(it, 0.97, "Perfil: ${def.label} = ${if (b) "sim" else "não"}.") }
            }
            FieldType.NUMBER -> {
                val v = Values.parseNumber(value) ?: return null
                if (v == 0.0) options.firstOrNull { Answers.isNoneOption(it) || Answers.isNo(it) }?.let {
                    return Triple(it, 0.95, "Perfil: ${def.label} = 0.")
                }
                val hits = options.filter { o -> NumberRange.parse(o, minimumWage)?.contains(v) == true }
                if (hits.size == 1) return Triple(hits[0], 0.96, "Perfil: ${def.label} = ${Values.formatNumber(v)}, dentro da faixa.")
                return null
            }
            FieldType.DATE -> {
                val d = Values.parseDate(value) ?: return null
                val year = d.year.toString()
                options.firstOrNull { Text.normalize(it) == year }?.let { return Triple(it, 0.95, "Ano de nascimento do perfil.") }
                return null
            }
            FieldType.LIST -> {
                val items = Text.splitList(value)
                val matches = options.filter { o -> items.any { matchesText(o, it) } }
                return when {
                    matches.size == 1 && items.size == 1 -> Triple(matches[0], 0.93, "Único item do perfil em ${def.label}.")
                    matches.size == 1 -> Triple(matches[0], 0.72, "Único item de ${def.label} presente nas opções.")
                    else -> null
                }
            }
            else -> {
                val exact = options.firstOrNull { Text.looselyEquals(it, value) }
                if (exact != null) return Triple(exact, 0.97, "Perfil: ${def.label} = \"$value\".")
                val fuzzy = options.filter { matchesText(it, value) }
                if (fuzzy.size == 1) return Triple(fuzzy[0], 0.86, "Correspondência semântica com ${def.label}.")
                val best = options.map { it to Text.jaccard(it, value) }.maxByOrNull { it.second }
                if (best != null && best.second >= 0.5) return Triple(best.first, 0.7, "Correspondência parcial com ${def.label}.")
                val other = options.firstOrNull { Answers.isOtherOption(it) }
                if (other != null) return Triple(other, 0.55, "Valor do perfil (\"$value\") não listado; opção \"Outro\".")
                return null
            }
        }
    }

    /** Múltipla escolha: retorna (opções, confiança, motivo). */
    fun matchMulti(def: FieldDef, value: String, options: List<String>): Triple<List<String>, Double, String>? {
        if (options.isEmpty()) return null
        return when (def.type) {
            FieldType.LIST, FieldType.TEXT, FieldType.CHOICE -> {
                val items = if (def.type == FieldType.LIST) Text.splitList(value) else listOf(value)
                if (items.isEmpty()) return null
                val selected = LinkedHashSet<String>()
                var unmatched = 0
                for (it in items) {
                    val hit = options.filter { o -> matchesText(o, it) }
                    if (hit.isEmpty()) unmatched++ else selected += hit
                }
                if (selected.isEmpty()) {
                    val none = options.firstOrNull { Answers.isNoneOption(it) }
                    return none?.let { Triple(listOf(it), 0.6, "Nenhum item do perfil aparece entre as opções.") }
                }
                if (unmatched > 0) options.firstOrNull { Answers.isOtherOption(it) }?.let { selected += it }
                Triple(selected.toList(), if (unmatched == 0) 0.95 else 0.85, "Itens do perfil em ${def.label}.")
            }
            FieldType.BOOLEAN -> {
                val b = Values.parseBoolean(value) ?: return null
                val o = if (b) options.firstOrNull { Answers.isYes(it) } else options.firstOrNull { Answers.isNo(it) || Answers.isNoneOption(it) }
                o?.let { Triple(listOf(it), 0.9, "Perfil: ${def.label}.") }
            }
            else -> matchSingle(def, value, options)?.let { Triple(listOf(it.first), it.second, it.third) }
        }
    }

    /**
     * "Quais destes você possui? Carro / Moto / Bicicleta / Nenhum" — cada opção vira uma pergunta booleana.
     * Só responde se TODAS as opções relevantes forem conhecidas no perfil.
     */
    private fun perOptionBoolean(q: SurveyQuestion, profile: UserProfile): AnswerDecision? {
        val opts = q.optionTexts.filterNot { Answers.isNoneOption(it) || Answers.isOtherOption(it) || Answers.isRefuseOption(it) }
        if (opts.size < 2) return null
        val selected = ArrayList<String>()
        val sources = ArrayList<String>()
        for (o in opts) {
            val cands = classifier.classify("$o ${q.text}")
                .filter { profile.fieldDef(it.key)?.type == FieldType.BOOLEAN }
            val boolField = cands.firstOrNull { c ->
                ProfileSchema.field(c.key)?.keywords?.any { kw -> Text.containsAllTokens(Text.tokenSet(o), kw) } == true
            } ?: return null
            val r = profile.resolve(boolField.key, today)
            val b = Values.parseBoolean(r.value) ?: return null
            if (b) selected += o
            sources += r.source
        }
        if (selected.isEmpty()) {
            val none = q.optionTexts.firstOrNull { Answers.isNoneOption(it) } ?: return null
            selected += none
        }
        return AnswerDecision(AnswerAction.ANSWER, selected, 0.92, sources.joinToString(","),
            "Cada opção corresponde a um fato booleano do perfil.", null)
    }

    companion object {
        /** Correspondência tolerante entre um texto de opção e um valor do perfil. */
        fun matchesText(option: String, value: String): Boolean {
            if (Text.looselyEquals(option, value)) return true
            val on = Text.normalize(option)
            val vn = Text.normalize(value)
            if (on.isEmpty() || vn.isEmpty()) return false
            val ot = Text.tokenSet(option)
            val vt = Text.tokenSet(value)
            if (vt.isNotEmpty() && ot.containsAll(vt)) return true
            if (ot.isNotEmpty() && vt.containsAll(ot) && ot.size >= 1 && on.length >= 3) return true
            return false
        }
    }
}
