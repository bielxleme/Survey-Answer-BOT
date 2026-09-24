package com.researchagent.autofill.data

import android.content.Context
import com.researchagent.autofill.core.AgentMode
import com.researchagent.autofill.core.Confidence
import com.researchagent.autofill.core.ProfileSchema
import com.researchagent.autofill.core.Text
import com.researchagent.autofill.core.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

// ═══════════════════════════════════════════════════════════════════
// Perfil
// ═══════════════════════════════════════════════════════════════════
class ProfileRepository(private val store: SecureStore) {
    private val _profile = MutableStateFlow(load())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    val current: UserProfile get() = _profile.value

    private fun load(): UserProfile {
        val txt = store.readText(FILE) ?: return UserProfile()
        return runCatching {
            val o = JSONObject(txt)
            val values = LinkedHashMap<String, String>()
            val labels = LinkedHashMap<String, String>()
            o.optJSONObject("values")?.let { v -> v.keys().forEach { k -> values[k] = v.optString(k) } }
            o.optJSONObject("labels")?.let { v -> v.keys().forEach { k -> labels[k] = v.optString(k) } }
            UserProfile(values, labels)
        }.getOrDefault(UserProfile())
    }

    fun update(transform: (UserProfile) -> UserProfile) {
        val next = transform(_profile.value)
        _profile.value = next
        persist(next)
    }

    fun set(key: String, value: String?) = update { it.with(key, value) }

    fun replace(p: UserProfile) = update { p }

    fun clear() = update { UserProfile() }

    private fun persist(p: UserProfile) {
        ioScope.launch {
            val o = JSONObject()
            o.put("values", JSONObject(p.values as Map<*, *>))
            o.put("labels", JSONObject(p.customLabels as Map<*, *>))
            store.writeText(FILE, o.toString())
        }
    }

    companion object { private const val FILE = "profile.enc" }
}

// ═══════════════════════════════════════════════════════════════════
// Configurações
// ═══════════════════════════════════════════════════════════════════
enum class LlmProvider(val label: String, val defaultUrl: String, val defaultModel: String) {
    NONE("Desativado (somente regras locais)", "", ""),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com", "gemini-2.5-flash"),
    OPENAI_COMPATIBLE("OpenAI / compatível (OpenAI, Ollama, LM Studio…)", "https://api.openai.com/v1", "gpt-4o-mini")
}

data class AppSettings(
    val mode: AgentMode = AgentMode.ASSISTED,
    val threshold: Double = 0.85,
    val autoNextSurvey: Boolean = false,
    val useOcr: Boolean = true,
    val llmProvider: LlmProvider = LlmProvider.NONE,
    val llmBaseUrl: String = "",
    val llmModel: String = "",
    val blockedPackages: Set<String> = DEFAULT_BLOCKED,
    val allowedPackages: Set<String> = emptySet(),
    val allowlistOnly: Boolean = false,
    val minimumWage: Double = 0.0,
    val logAnswers: Boolean = true,
    val secureScreens: Boolean = true,
    val soundOnIntervention: Boolean = true,
    val onboardingDone: Boolean = false,
    val bubbleEnabled: Boolean = true,
    /** "Chutar respostas": tentativas registradas como TENTATIVA, nunca como verdade. */
    val guessMode: Boolean = false,
    /** Observa o usuário quando a pesquisa não é reconhecida. */
    val observeUnknown: Boolean = true,
    /** Faixas de confiança configuráveis (Seção 11). */
    val bandHigh: Double = 0.95,
    val bandGood: Double = 0.80,
    val bandMid: Double = 0.60,
    /** Ao "Encerrar aplicativo", também desativa o serviço de acessibilidade. */
    val disableServiceOnExit: Boolean = false
) {
    companion object {
        /** Seção 18: bancos, mensagens, pagamentos e sistema nunca são automatizados. */
        val DEFAULT_BLOCKED: Set<String> = setOf(
            "com.android.settings", "com.android.systemui", "com.google.android.packageinstaller",
            "com.android.packageinstaller", "com.google.android.permissioncontroller",
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.facebook.orca",
            "com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging",
            "com.google.android.gm", "com.microsoft.office.outlook", "com.instagram.android",
            "com.nu.production", "com.itau", "br.com.bb.android", "com.bradesco", "br.com.bradesco.next",
            "com.santander.app", "br.com.intermedium", "com.c6bank.app", "br.com.gabba.Caixa", "br.gov.caixa.tem",
            "com.picpay", "com.mercadopago.wallet", "br.com.original.bank", "com.btg.pactual.digital.mobile",
            "com.xp.investimentos", "com.paypal.android.p2pmobile", "com.google.android.apps.walletnfcrel",
            "com.google.android.apps.authenticator2", "com.azure.authenticator", "com.android.vending"
        )
        /** Palavras no nome do pacote que indicam app financeiro ou de mensagens. */
        val RISKY_PACKAGE_WORDS = listOf("bank", "banco", "wallet", "pay", "invest", "broker", "authenticator", "messag", "sms")
    }
}

class SettingsRepository(context: Context, private val store: SecureStore) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val current: AppSettings get() = _settings.value

    private fun load(): AppSettings {
        val txt = prefs.getString("json", null) ?: return AppSettings()
        return runCatching {
            val o = JSONObject(txt)
            fun set(name: String, def: Set<String>): Set<String> =
                o.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: def
            AppSettings(
                mode = runCatching { AgentMode.valueOf(o.optString("mode")) }.getOrDefault(AgentMode.ASSISTED),
                threshold = o.optDouble("threshold", 0.85),
                autoNextSurvey = o.optBoolean("autoNextSurvey", false),
                useOcr = o.optBoolean("useOcr", true),
                llmProvider = runCatching { LlmProvider.valueOf(o.optString("llmProvider")) }.getOrDefault(LlmProvider.NONE),
                llmBaseUrl = o.optString("llmBaseUrl", ""),
                llmModel = o.optString("llmModel", ""),
                blockedPackages = set("blockedPackages", AppSettings.DEFAULT_BLOCKED),
                allowedPackages = set("allowedPackages", emptySet()),
                allowlistOnly = o.optBoolean("allowlistOnly", false),
                minimumWage = o.optDouble("minimumWage", 0.0),
                logAnswers = o.optBoolean("logAnswers", true),
                secureScreens = o.optBoolean("secureScreens", true),
                soundOnIntervention = o.optBoolean("soundOnIntervention", true),
                onboardingDone = o.optBoolean("onboardingDone", false),
                bubbleEnabled = o.optBoolean("bubbleEnabled", true),
                guessMode = o.optBoolean("guessMode", false),
                observeUnknown = o.optBoolean("observeUnknown", true),
                bandHigh = o.optDouble("bandHigh", 0.95),
                bandGood = o.optDouble("bandGood", 0.80),
                bandMid = o.optDouble("bandMid", 0.60),
                disableServiceOnExit = o.optBoolean("disableServiceOnExit", false)
            )
        }.getOrDefault(AppSettings())
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        _settings.value = s
        val o = JSONObject()
        o.put("mode", s.mode.name)
        o.put("threshold", s.threshold)
        o.put("autoNextSurvey", s.autoNextSurvey)
        o.put("useOcr", s.useOcr)
        o.put("llmProvider", s.llmProvider.name)
        o.put("llmBaseUrl", s.llmBaseUrl)
        o.put("llmModel", s.llmModel)
        o.put("blockedPackages", JSONArray(s.blockedPackages.toList()))
        o.put("allowedPackages", JSONArray(s.allowedPackages.toList()))
        o.put("allowlistOnly", s.allowlistOnly)
        o.put("minimumWage", s.minimumWage)
        o.put("logAnswers", s.logAnswers)
        o.put("secureScreens", s.secureScreens)
        o.put("soundOnIntervention", s.soundOnIntervention)
        o.put("onboardingDone", s.onboardingDone)
        o.put("bubbleEnabled", s.bubbleEnabled)
        o.put("guessMode", s.guessMode)
        o.put("observeUnknown", s.observeUnknown)
        o.put("bandHigh", s.bandHigh)
        o.put("bandGood", s.bandGood)
        o.put("bandMid", s.bandMid)
        o.put("disableServiceOnExit", s.disableServiceOnExit)
        prefs.edit().putString("json", o.toString()).apply()
    }

    /** Chave de API guardada criptografada (nunca em texto puro). */
    var apiKey: String
        get() = store.decryptString(prefs.getString("api_key", null)).orEmpty()
        set(value) {
            prefs.edit().putString("api_key", if (value.isBlank()) null else store.encryptString(value.trim())).apply()
        }

    fun isPackageAllowed(pkg: String, ownPackage: String): Boolean {
        if (pkg.isBlank() || pkg == ownPackage) return false
        val s = current
        if (pkg in s.blockedPackages) return false
        if (s.allowlistOnly) return pkg in s.allowedPackages
        if (pkg in s.allowedPackages) return true
        val lower = pkg.lowercase()
        return AppSettings.RISKY_PACKAGE_WORDS.none { lower.contains(it) }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Logs (Seção 20)
// ═══════════════════════════════════════════════════════════════════
enum class LogType(val label: String) {
    INFO("Info"), SURVEY("Pesquisa"), ANSWER("Resposta"), USER_ANSWER("Resposta do usuário"),
    UNKNOWN("Desconhecida"), INTERVENTION("Intervenção"), COMPLETED("Concluída"), ERROR("Erro"),
    OBSERVE("Observação"), LEARN("Aprendizado"), GUESS("Tentativa"), LOOP("Loop"), RESUME("Retomada")
}

data class LogEntry(
    val time: Long,
    val type: LogType,
    val packageName: String,
    val message: String,
    val detail: String = "",
    val confidence: Confidence? = null
)

class LogRepository(private val store: SecureStore) {
    private val _logs = MutableStateFlow(load())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private fun load(): List<LogEntry> = runCatching {
        val a = JSONArray(store.readText(FILE) ?: "[]")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            LogEntry(
                o.getLong("t"),
                runCatching { LogType.valueOf(o.getString("y")) }.getOrDefault(LogType.INFO),
                o.optString("p"), o.optString("m"), o.optString("d"),
                o.optString("c").takeIf { it.isNotEmpty() }?.let { c -> runCatching { Confidence.valueOf(c) }.getOrNull() }
            )
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun add(entry: LogEntry) {
        _logs.value = (listOf(entry) + _logs.value).take(MAX)
        persist()
    }

    fun clear() { _logs.value = emptyList(); persist() }

    private fun persist() {
        val snapshot = _logs.value
        ioScope.launch {
            val a = JSONArray()
            snapshot.forEach { e ->
                a.put(JSONObject().apply {
                    put("t", e.time); put("y", e.type.name); put("p", e.packageName); put("m", e.message)
                    put("d", e.detail); put("c", e.confidence?.name ?: "")
                })
            }
            store.writeText(FILE, a.toString())
        }
    }

    companion object { private const val FILE = "logs.enc"; private const val MAX = 600 }
}

// ═══════════════════════════════════════════════════════════════════
// Estatísticas (Seção 42)
// ═══════════════════════════════════════════════════════════════════
data class Stats(
    val surveysCompleted: Int = 0,
    val questionsAnswered: Int = 0,
    val autoAnswered: Int = 0,
    val userAnswered: Int = 0,
    val interventions: Int = 0,
    val unknownQuestions: Int = 0,
    val highConfidence: Int = 0,
    val automatedMs: Long = 0
) {
    val highConfidenceRate: Int get() = if (autoAnswered == 0) 0 else (highConfidence * 100 / autoAnswered)
}

class StatsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("stats", Context.MODE_PRIVATE)
    private val _stats = MutableStateFlow(load())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    private fun load() = Stats(
        prefs.getInt("surveys", 0), prefs.getInt("questions", 0), prefs.getInt("auto", 0), prefs.getInt("user", 0),
        prefs.getInt("interventions", 0), prefs.getInt("unknown", 0), prefs.getInt("high", 0), prefs.getLong("ms", 0)
    )

    @Synchronized
    fun update(f: (Stats) -> Stats) {
        val s = f(_stats.value)
        _stats.value = s
        prefs.edit()
            .putInt("surveys", s.surveysCompleted).putInt("questions", s.questionsAnswered).putInt("auto", s.autoAnswered)
            .putInt("user", s.userAnswered).putInt("interventions", s.interventions).putInt("unknown", s.unknownQuestions)
            .putInt("high", s.highConfidence).putLong("ms", s.automatedMs).apply()
    }

    fun reset() = update { Stats() }
}

// ═══════════════════════════════════════════════════════════════════
// Perguntas pendentes (Seções 6 e 34) e mapeamentos aprendidos (Seção 27)
// ═══════════════════════════════════════════════════════════════════
data class PendingQuestion(
    val question: String,
    val fieldKey: String?,
    val options: List<String>,
    val count: Int,
    val lastSeen: Long
) {
    val key: String get() = Text.questionKey(question)
}

class KnowledgeRepository(private val store: SecureStore) {
    private val _pending = MutableStateFlow<List<PendingQuestion>>(emptyList())
    val pending: StateFlow<List<PendingQuestion>> = _pending.asStateFlow()
    private val _learned = MutableStateFlow<Map<String, String>>(emptyMap())
    val learned: StateFlow<Map<String, String>> = _learned.asStateFlow()

    init { load() }

    private fun load() {
        runCatching {
            val o = JSONObject(store.readText(FILE) ?: "{}")
            val lm = LinkedHashMap<String, String>()
            o.optJSONObject("learned")?.let { l -> l.keys().forEach { k -> lm[k] = l.getString(k) } }
            _learned.value = lm
            val arr = o.optJSONArray("pending") ?: JSONArray()
            _pending.value = (0 until arr.length()).map { i ->
                val p = arr.getJSONObject(i)
                val opts = p.optJSONArray("o") ?: JSONArray()
                PendingQuestion(
                    p.getString("q"), p.optString("f").ifBlank { null },
                    (0 until opts.length()).map { opts.getString(it) }, p.optInt("n", 1), p.optLong("t")
                )
            }
        }
    }

    @Synchronized
    fun addPending(question: String, fieldKey: String?, options: List<String>) {
        val key = Text.questionKey(question)
        if (key.isBlank()) return
        val list = _pending.value.toMutableList()
        val idx = list.indexOfFirst { it.key == key }
        if (idx >= 0) list[idx] = list[idx].copy(count = list[idx].count + 1, lastSeen = System.currentTimeMillis(), fieldKey = fieldKey ?: list[idx].fieldKey)
        else list.add(0, PendingQuestion(question, fieldKey, options.take(20), 1, System.currentTimeMillis()))
        _pending.value = list.take(200)
        persist()
    }

    @Synchronized
    fun removePending(question: String) {
        val key = Text.questionKey(question)
        _pending.value = _pending.value.filterNot { it.key == key }
        persist()
    }

    /** Remove pendências cujo campo agora está preenchido no perfil. */
    @Synchronized
    fun prune(profile: UserProfile) {
        val next = _pending.value.filterNot { p -> p.fieldKey != null && profile.resolve(p.fieldKey).isKnown }
        if (next.size != _pending.value.size) { _pending.value = next; persist() }
    }

    @Synchronized
    fun learn(question: String, fieldKey: String) {
        if (ProfileSchema.field(fieldKey) == null && !ProfileSchema.isCustom(fieldKey)) return
        _learned.value = _learned.value + (Text.questionKey(question) to fieldKey)
        persist()
    }

    @Synchronized
    fun clearLearned() { _learned.value = emptyMap(); persist() }

    @Synchronized
    fun clearPending() { _pending.value = emptyList(); persist() }

    private fun persist() {
        val learned = _learned.value
        val pending = _pending.value
        ioScope.launch {
            val o = JSONObject()
            o.put("learned", JSONObject(learned as Map<*, *>))
            val arr = JSONArray()
            pending.forEach { p ->
                arr.put(JSONObject().apply {
                    put("q", p.question); put("f", p.fieldKey ?: ""); put("o", JSONArray(p.options))
                    put("n", p.count); put("t", p.lastSeen)
                })
            }
            o.put("pending", arr)
            store.writeText(FILE, o.toString())
        }
    }

    companion object { private const val FILE = "knowledge.enc" }
}
