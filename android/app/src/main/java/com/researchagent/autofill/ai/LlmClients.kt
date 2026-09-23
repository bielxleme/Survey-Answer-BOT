package com.researchagent.autofill.ai

import com.researchagent.autofill.core.AnswerAction
import com.researchagent.autofill.core.AnswerDecision
import com.researchagent.autofill.core.LlmClient
import com.researchagent.autofill.core.QuestionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

/** Prompts e parsing compartilhados — o modelo SEMPRE devolve JSON (Seção 24). */
object LlmPrompts {

    const val SELECT_SYSTEM = """Você ajuda um agente de pesquisas no Android. Dada uma pergunta de pesquisa e um CATÁLOGO de
campos de perfil (apenas nomes, sem valores), devolva quais campos podem responder à pergunta.
Responda SOMENTE com JSON: {"fields": ["chave1", "chave2"]}. Use no máximo 4 chaves existentes no catálogo.
Se nenhum campo servir, devolva {"fields": []}."""

    const val DECIDE_SYSTEM = """Você é o núcleo semântico de um agente que preenche pesquisas em nome do usuário.
REGRAS ABSOLUTAS:
1. NUNCA invente informação pessoal. Use SOMENTE os valores de "profile".
2. Se o perfil não determina a resposta com certeza, responda action="ASK_USER".
3. Se houver "options", "answer" deve ser EXATAMENTE o texto de uma opção (ou lista de opções, separadas por " | ", em múltipla escolha).
4. Derivações só quando forem lógicas/matemáticas (ex.: data de nascimento → idade).
5. "source" deve ser a chave do perfil usada (ex.: "profissao").
Responda SOMENTE com JSON no formato:
{"action":"ANSWER"|"ASK_USER","answer":string|null,"confidence":0..1,"source":string|null,"reason":string,"needs_user":boolean}"""

    fun selectUser(question: String, options: List<String>, catalog: Map<String, String>): String =
        JSONObject().apply {
            put("question", question)
            put("options", JSONArray(options))
            put("catalog", JSONObject(catalog as Map<*, *>))
        }.toString()

    fun decideUser(question: String, options: List<String>, type: QuestionType, subset: Map<String, String>): String =
        JSONObject().apply {
            put("question", question)
            put("options", JSONArray(options))
            put("field_type", type.name)
            put("profile", JSONObject(subset as Map<*, *>))
        }.toString()

    /** Extrai o primeiro objeto JSON de um texto (o modelo às vezes envolve em ```json). */
    fun extractJson(text: String): JSONObject {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        require(start >= 0 && end > start) { "Resposta sem JSON" }
        return JSONObject(text.substring(start, end + 1))
    }

    fun parseFields(text: String): List<String> {
        val arr = extractJson(text).optJSONArray("fields") ?: return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }

    fun parseDecision(text: String, multi: Boolean): AnswerDecision {
        val o = extractJson(text)
        val needsUser = o.optBoolean("needs_user", false) || o.optString("action") != "ANSWER"
        val answerRaw = if (o.isNull("answer")) "" else o.optString("answer", "")
        if (needsUser || answerRaw.isBlank()) {
            return AnswerDecision.ask(o.optString("reason", "Sem informação suficiente no perfil."), engine = "llm")
        }
        val answers = if (multi) answerRaw.split("|").map { it.trim() }.filter { it.isNotEmpty() } else listOf(answerRaw.trim())
        return AnswerDecision(
            action = AnswerAction.ANSWER,
            answers = answers,
            confidence = o.optDouble("confidence", 0.0).coerceIn(0.0, 1.0),
            source = if (o.isNull("source")) null else o.optString("source"),
            reason = o.optString("reason", ""),
            engine = "llm"
        )
    }
}

private fun httpPostJson(url: String, body: JSONObject, headers: Map<String, String>, timeoutMs: Int = 20_000): JSONObject {
    val conn = URI(url).toURL().openConnection() as HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("HTTP $code: ${text.take(300)}")
        return JSONObject(text)
    } finally {
        conn.disconnect()
    }
}

/** Google Gemini (generateContent) com resposta JSON forçada. */
class GeminiClient(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com"
) : LlmClient {
    override val name = "gemini"

    private suspend fun call(system: String, user: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", user)))))
            put("generationConfig", JSONObject().put("temperature", 0.0).put("responseMimeType", "application/json"))
        }
        val url = "${baseUrl.trimEnd('/')}/v1beta/models/$model:generateContent"
        val res = httpPostJson(url, body, mapOf("x-goog-api-key" to apiKey))
        val parts = res.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }
    }

    override suspend fun selectFields(question: String, options: List<String>, catalog: Map<String, String>): List<String> =
        LlmPrompts.parseFields(call(LlmPrompts.SELECT_SYSTEM, LlmPrompts.selectUser(question, options, catalog)))

    override suspend fun decide(question: String, options: List<String>, type: QuestionType, subset: Map<String, String>): AnswerDecision =
        LlmPrompts.parseDecision(call(LlmPrompts.DECIDE_SYSTEM, LlmPrompts.decideUser(question, options, type, subset)),
            multi = type == QuestionType.MULTI_CHOICE)
}

/** OpenAI Chat Completions e servidores compatíveis (Ollama em /v1, LM Studio, vLLM…). */
class OpenAiCompatibleClient(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String
) : LlmClient {
    override val name = "openai-compatible"

    private suspend fun call(system: String, user: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", model)
            put("temperature", 0)
            put("response_format", JSONObject().put("type", "json_object"))
            put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
        }
        val headers = if (apiKey.isNotBlank()) mapOf("Authorization" to "Bearer $apiKey") else emptyMap()
        val res = httpPostJson("${baseUrl.trimEnd('/')}/chat/completions", body, headers)
        res.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
    }

    override suspend fun selectFields(question: String, options: List<String>, catalog: Map<String, String>): List<String> =
        LlmPrompts.parseFields(call(LlmPrompts.SELECT_SYSTEM, LlmPrompts.selectUser(question, options, catalog)))

    override suspend fun decide(question: String, options: List<String>, type: QuestionType, subset: Map<String, String>): AnswerDecision =
        LlmPrompts.parseDecision(call(LlmPrompts.DECIDE_SYSTEM, LlmPrompts.decideUser(question, options, type, subset)),
            multi = type == QuestionType.MULTI_CHOICE)
}
