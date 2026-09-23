package com.researchagent.autofill.core

/**
 * Camada independente anti-alucinação (Seção 25).
 * Toda resposta vinda de um LLM passa por aqui antes de tocar a interface:
 *  - precisa citar um campo do subconjunto de perfil que foi enviado;
 *  - o valor citado precisa existir e sustentar a resposta;
 *  - em perguntas com opções, a resposta precisa ser uma opção existente;
 *  - em perguntas abertas, a resposta precisa estar contida no valor do perfil.
 */
object AnswerValidator {

    data class Result(val accepted: Boolean, val decision: AnswerDecision, val rejection: String? = null)

    fun validate(
        question: SurveyQuestion,
        decision: AnswerDecision,
        sentSubset: Map<String, String>,
        threshold: Double
    ): Result {
        if (decision.needsUser) return Result(true, decision)
        if (decision.answers.isEmpty() || decision.answers.any { it.isBlank() }) return reject(decision, "Resposta vazia.")

        val sourceKeys = parseSources(decision.source)
        val supporting = sourceKeys.mapNotNull { k -> sentSubset[k]?.let { k to it } }
        if (supporting.isEmpty()) return reject(decision, "A IA não citou um campo válido do perfil enviado.")

        // Opções: a resposta precisa ser uma das opções visíveis
        if (question.options.isNotEmpty()) {
            val mapped = decision.answers.map { a -> question.optionTexts.firstOrNull { Text.looselyEquals(it, a) } }
            if (mapped.any { it == null }) return reject(decision, "Resposta não corresponde a uma opção existente.")
            if (question.type == QuestionType.SINGLE_CHOICE && mapped.size != 1) return reject(decision, "Escolha única com múltiplas respostas.")
            val provable = mapped.all { opt -> supporting.any { (k, v) -> optionSupported(opt!!, k, v) } }
            if (!provable) return reject(decision, "A opção escolhida não é comprovada pelo valor do perfil.")
            val fixed = decision.copy(answers = mapped.map { it!! })
            return confidenceGate(fixed, threshold)
        }

        // Texto livre: precisa estar contido no valor do perfil (ou ser numericamente igual)
        val answer = decision.answers.single()
        val provable = supporting.any { (k, v) -> textSupported(answer, k, v) }
        if (!provable) return reject(decision, "O texto da resposta não está no perfil (possível invenção).")
        return confidenceGate(decision, threshold)
    }

    private fun confidenceGate(d: AnswerDecision, threshold: Double): Result =
        if (d.confidence >= threshold) Result(true, d)
        else Result(false, d, "Confiança ${"%.2f".format(d.confidence)} abaixo do limite ${"%.2f".format(threshold)}.")

    private fun reject(d: AnswerDecision, why: String): Result =
        Result(false, AnswerDecision.ask("Resposta da IA rejeitada: $why", d.fieldKey, d.engine), why)

    fun parseSources(source: String?): List<String> =
        source.orEmpty().split(',', ';', ' ')
            .map { it.trim().removePrefix("profile.").removePrefix("derived:").substringAfterLast('.') }
            .filter { it.isNotEmpty() }
            .let { list -> list + source.orEmpty().split(',', ';', ' ').map { it.trim().removePrefix("profile.") } }
            .filter { it.isNotEmpty() }
            .distinct()

    fun optionSupported(option: String, key: String, value: String): Boolean {
        val def = ProfileSchema.field(key) ?: ProfileSchema.customField(key)
        val type = def?.type ?: FieldType.TEXT
        return when (type) {
            FieldType.BOOLEAN -> when (Values.parseBoolean(value)) {
                true -> Answers.isYes(option)
                false -> Answers.isNo(option) || Answers.isNoneOption(option)
                null -> false
            }
            FieldType.NUMBER -> {
                val v = Values.parseNumber(value) ?: return false
                (v == 0.0 && (Answers.isNoneOption(option) || Answers.isNo(option))) ||
                    NumberRange.parse(option)?.contains(v) == true
            }
            FieldType.DATE -> Values.parseDate(value)?.let { d ->
                Text.normalize(option) == d.year.toString() || NumberRange.parse(option)?.contains(d.year.toDouble()) == true
            } ?: false
            FieldType.LIST -> Text.splitList(value).any { AnswerEngine.matchesText(option, it) } ||
                (Answers.isOtherOption(option))
            else -> AnswerEngine.matchesText(option, value) || Text.jaccard(option, value) >= 0.5 ||
                (Answers.isOtherOption(option) && value.isNotBlank())
        }
    }

    fun textSupported(answer: String, key: String, value: String): Boolean {
        val def = ProfileSchema.field(key) ?: ProfileSchema.customField(key)
        val an = Text.normalize(answer)
        val vn = Text.normalize(value)
        if (an.isEmpty()) return false
        if (an == vn || vn.contains(an)) return true
        val aNum = Values.parseNumber(answer)
        val vNum = Values.parseNumber(value)
        if (aNum != null && vNum != null && aNum == vNum) return true
        if (def?.type == FieldType.DATE) {
            val d = Values.parseDate(value) ?: return false
            val ad = Values.parseDate(answer)
            return ad == d || an == d.year.toString()
        }
        if (def?.type == FieldType.BOOLEAN) {
            return Values.parseBoolean(answer) != null && Values.parseBoolean(answer) == Values.parseBoolean(value)
        }
        if (Values.digits(answer).isNotEmpty() && Values.digits(answer) == Values.digits(value)) return true
        return false
    }
}
