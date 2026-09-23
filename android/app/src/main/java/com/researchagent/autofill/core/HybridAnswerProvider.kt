package com.researchagent.autofill.core

/**
 * Cliente de modelo de linguagem trocável (Seção 23): Gemini, OpenAI, Ollama ou local.
 * Recebe somente o necessário e devolve SEMPRE o contrato estruturado (Seção 24).
 */
interface LlmClient {
    val name: String

    /** Passo 1 (privacidade): recebe apenas NOMES de campos disponíveis — nenhum valor pessoal. */
    suspend fun selectFields(question: String, options: List<String>, catalog: Map<String, String>): List<String>

    /** Passo 2: recebe só o subconjunto de valores relevantes. */
    suspend fun decide(question: String, options: List<String>, type: QuestionType, subset: Map<String, String>): AnswerDecision
}

/**
 * Regras determinísticas primeiro; LLM apenas como apoio semântico,
 * sempre passando pelo [AnswerValidator] antes de chegar à interface.
 */
class HybridAnswerProvider(
    private val profile: () -> UserProfile,
    private val engine: () -> AnswerEngine,
    private val llm: () -> LlmClient?,
    private val threshold: () -> Double,
    private val onLlmError: (String) -> Unit = {}
) : AnswerProvider {

    override suspend fun decide(question: SurveyQuestion, ctx: SurveyContext): AnswerDecision {
        val p = profile()
        val rules = engine().answer(question, p, ctx)
        if (!rules.needsUser && rules.confidence >= threshold()) return rules
        val client = llm() ?: return rules
        return try {
            val catalog = LinkedHashMap<String, String>()
            for (def in ProfileSchema.fields) {
                if (def.sensitive || def.key == "observacoes") continue
                if (p.resolve(def.key).isKnown) catalog[def.key] = def.label
            }
            for (k in p.customKeys) if (p.resolve(k).isKnown) catalog[k] = p.fieldDef(k)?.label ?: k
            if (catalog.isEmpty()) return rules
            val keys = client.selectFields(question.text, question.optionTexts, catalog)
                .filter { it in catalog }.distinct().take(6)
            if (keys.isEmpty()) return rules
            val subset = p.subset(keys, includeSensitive = false)
            if (subset.isEmpty()) return rules
            val raw = client.decide(question.text, question.optionTexts, question.type, subset)
            val v = AnswerValidator.validate(question, raw.copy(engine = client.name), subset, threshold())
            when {
                v.accepted && !v.decision.needsUser -> v.decision.copy(engine = client.name, fieldKey = v.decision.fieldKey ?: keys.first())
                !rules.needsUser -> rules
                // resposta plausível mas abaixo do limite: devolve como sugestão para o usuário confirmar
                !v.decision.needsUser -> v.decision.copy(engine = client.name)
                else -> v.decision.copy(fieldKey = rules.fieldKey ?: keys.firstOrNull())
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            onLlmError(e.message ?: e.toString())
            rules
        }
    }
}
