package com.researchagent.autofill

import android.app.Application
import com.researchagent.autofill.ai.GeminiClient
import com.researchagent.autofill.ai.OpenAiCompatibleClient
import com.researchagent.autofill.core.AnswerEngine
import com.researchagent.autofill.core.AnswerProvider
import com.researchagent.autofill.core.HybridAnswerProvider
import com.researchagent.autofill.core.LlmClient
import com.researchagent.autofill.core.QuestionClassifier
import com.researchagent.autofill.data.KnowledgeRepository
import com.researchagent.autofill.data.LearningRepository
import com.researchagent.autofill.data.LlmProvider
import com.researchagent.autofill.data.LogEntry
import com.researchagent.autofill.data.LogRepository
import com.researchagent.autofill.data.LogType
import com.researchagent.autofill.data.ProfileRepository
import com.researchagent.autofill.data.SecureStore
import com.researchagent.autofill.data.SettingsRepository
import com.researchagent.autofill.data.StatsRepository
import com.researchagent.autofill.notifications.Notifier

/** Injeção de dependências simples (sem frameworks) — um grafo por processo. */
object AppGraph {
    lateinit var app: Application
        private set
    lateinit var store: SecureStore
        private set
    lateinit var profile: ProfileRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var logs: LogRepository
        private set
    lateinit var stats: StatsRepository
        private set
    lateinit var knowledge: KnowledgeRepository
        private set
    lateinit var learning: LearningRepository
        private set

    fun init(application: Application) {
        if (::app.isInitialized) return
        app = application
        store = SecureStore(application)
        profile = ProfileRepository(store)
        settings = SettingsRepository(application, store)
        logs = LogRepository(store)
        stats = StatsRepository(application)
        knowledge = KnowledgeRepository(store)
        learning = LearningRepository(store)
        Notifier.createChannels(application)
    }

    fun classifier(): QuestionClassifier {
        val p = profile.current
        return QuestionClassifier(
            learned = knowledge.learned.value,
            extraFields = p.customKeys.mapNotNull { p.fieldDef(it) }
        )
    }

    fun answerEngine(): AnswerEngine = AnswerEngine(classifier(), minimumWage = settings.current.minimumWage)

    /** Cliente LLM conforme configurações — null quando desativado ou sem credenciais. */
    fun llmClient(): LlmClient? {
        val s = settings.current
        val key = settings.apiKey
        return when (s.llmProvider) {
            LlmProvider.NONE -> null
            LlmProvider.GEMINI -> if (key.isBlank()) null else GeminiClient(
                key, s.llmModel.ifBlank { LlmProvider.GEMINI.defaultModel },
                s.llmBaseUrl.ifBlank { LlmProvider.GEMINI.defaultUrl })
            LlmProvider.OPENAI_COMPATIBLE -> OpenAiCompatibleClient(
                key, s.llmModel.ifBlank { LlmProvider.OPENAI_COMPATIBLE.defaultModel },
                s.llmBaseUrl.ifBlank { LlmProvider.OPENAI_COMPATIBLE.defaultUrl })
        }
    }

    val answerProvider: AnswerProvider by lazy {
        HybridAnswerProvider(
            profile = { profile.current },
            engine = { answerEngine() },
            llm = { llmClient() },
            threshold = { settings.current.threshold },
            onLlmError = { msg ->
                logs.add(LogEntry(System.currentTimeMillis(), LogType.ERROR, "", "Falha na IA — usando regras locais", msg.take(200)))
            }
        )
    }
}

class ResearchAgentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
