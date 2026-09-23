package com.researchagent.autofill.automation

import android.util.Log
import com.researchagent.autofill.AppGraph
import com.researchagent.autofill.accessibility.SurveyAccessibilityService
import com.researchagent.autofill.core.AgentEngine
import com.researchagent.autofill.core.AgentEvent
import com.researchagent.autofill.core.AgentHost
import com.researchagent.autofill.core.AgentMode
import com.researchagent.autofill.core.AgentPolicy
import com.researchagent.autofill.core.AgentStatus
import com.researchagent.autofill.core.Confidence
import com.researchagent.autofill.core.Intervention
import com.researchagent.autofill.core.InterventionResult
import com.researchagent.autofill.data.LogEntry
import com.researchagent.autofill.data.LogType
import com.researchagent.autofill.notifications.Notifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Ponte de intervenção humana (Seção 15): o agente suspende até o usuário responder
 * pela bolha, pela notificação ou pela tela de resposta.
 */
object InterventionBus {
    data class Pending(val intervention: Intervention, val deferred: CompletableDeferred<InterventionResult>)

    private val _current = MutableStateFlow<Pending?>(null)
    val current: StateFlow<Pending?> = _current.asStateFlow()

    suspend fun request(i: Intervention): InterventionResult {
        val d = CompletableDeferred<InterventionResult>()
        _current.value = Pending(i, d)
        return try {
            d.await()
        } finally {
            if (_current.value?.intervention?.id == i.id) _current.value = null
        }
    }

    fun respond(id: Long, result: InterventionResult) {
        val p = _current.value ?: return
        if (p.intervention.id != id) return
        _current.value = null
        p.deferred.complete(result)
    }

    fun respondCurrent(result: InterventionResult) {
        _current.value?.let { respond(it.intervention.id, result) }
    }
}

/**
 * Controlador da automação (AutomationController): liga o motor puro [AgentEngine]
 * ao Android (serviço de acessibilidade, logs, estatísticas, notificações).
 */
object AgentController : AgentHost {

    private const val TAG = "AgentController"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _status = MutableStateFlow(AgentStatus())
    val status: StateFlow<AgentStatus> = _status.asStateFlow()

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    @Volatile private var engine: AgentEngine? = null
    private var runJob: Job? = null
    private var statusJob: Job? = null

    val isActive: Boolean get() = runJob?.isActive == true

    override val ownPackage: String get() = AppGraph.app.packageName

    fun onServiceConnected(@Suppress("UNUSED_PARAMETER") service: SurveyAccessibilityService) {
        _serviceConnected.value = true
    }

    fun onServiceDisconnected() {
        _serviceConnected.value = false
        stop()
    }

    /** ATIVAR PESQUISA (Seção 1). */
    fun start(): Boolean {
        val service = SurveyAccessibilityService.instance ?: return false
        if (isActive) { resume(); return true }
        val e = AgentEngine(service.driver, AppGraph.answerProvider, this)
        engine = e
        statusJob?.cancel()
        statusJob = scope.launch { e.status.collect { _status.value = it } }
        runJob = scope.launch {
            try {
                e.run()
            } catch (t: Throwable) {
                if (t !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Falha no agente", t)
                    log(LogType.ERROR, "", "Erro interno: ${t.message}")
                }
            }
        }
        log(LogType.INFO, service.lastPackage, "Agente ativado (modo ${policy().mode.label})")
        return true
    }

    fun pause() { engine?.pause() }

    fun resume() {
        engine?.resume()
        // se estava aguardando o usuário resolver algo (CAPTCHA/login), "continuar" libera
        InterventionBus.respondCurrent(InterventionResult.Resume)
    }

    fun stop() {
        engine?.requestStop()
        InterventionBus.respondCurrent(InterventionResult.Stop)
        runJob?.cancel()
        runJob = null
        engine = null
        _status.value = _status.value.copy(running = false, paused = false, intervention = null, message = "Parado")
        Notifier.cancelIntervention(AppGraph.app)
    }

    fun setMode(mode: AgentMode) {
        AppGraph.settings.update { it.copy(mode = mode) }
        _status.value = _status.value.copy(mode = mode)
    }

    // ── AgentHost ─────────────────────────────────────────────────────
    override fun policy(): AgentPolicy {
        val s = AppGraph.settings.current
        return AgentPolicy(mode = s.mode, threshold = s.threshold, autoNextSurvey = s.autoNextSurvey, useOcr = s.useOcr)
    }

    override fun isPackageAllowed(packageName: String): Boolean =
        AppGraph.settings.isPackageAllowed(packageName, ownPackage)

    override suspend fun intervene(intervention: Intervention): InterventionResult {
        val s = AppGraph.settings.current
        Notifier.showIntervention(AppGraph.app, intervention, playSound = s.soundOnIntervention)
        return try {
            InterventionBus.request(intervention)
        } finally {
            Notifier.cancelIntervention(AppGraph.app)
        }
    }

    override fun onEvent(event: AgentEvent) {
        val settings = AppGraph.settings.current
        when (event) {
            is AgentEvent.SurveyStarted -> log(LogType.SURVEY, event.packageName, "Pesquisa detectada")
            is AgentEvent.QuestionAnswered -> {
                val d = event.decision
                val fieldSensitive = d.fieldKey?.let { AppGraph.profile.current.fieldDef(it)?.sensitive } == true
                val shown = if (!settings.logAnswers || fieldSensitive) "••••" else d.display
                log(if (event.byUser) LogType.USER_ANSWER else LogType.ANSWER, event.packageName,
                    event.question.take(140), "→ $shown · ${d.reason} [${d.engine}]", if (event.byUser) null else d.level)
                AppGraph.stats.update {
                    it.copy(
                        questionsAnswered = it.questionsAnswered + 1,
                        autoAnswered = it.autoAnswered + if (event.byUser) 0 else 1,
                        userAnswered = it.userAnswered + if (event.byUser) 1 else 0,
                        highConfidence = it.highConfidence + if (!event.byUser && d.level == Confidence.HIGH) 1 else 0
                    )
                }
            }
            is AgentEvent.QuestionUnknown -> {
                AppGraph.knowledge.addPending(event.question, event.fieldKey, event.options)
                log(LogType.UNKNOWN, event.packageName, event.question.take(140), "Informação não disponível no perfil", Confidence.UNKNOWN)
                AppGraph.stats.update { it.copy(unknownQuestions = it.unknownQuestions + 1) }
            }
            is AgentEvent.InterventionRaised -> {
                log(LogType.INTERVENTION, event.packageName, event.reason.title, event.message.take(200))
                AppGraph.stats.update { it.copy(interventions = it.interventions + 1) }
            }
            is AgentEvent.SurveyCompleted -> {
                log(LogType.COMPLETED, event.packageName, "Pesquisa concluída",
                    "${event.questions} perguntas em ${event.durationMs / 1000}s")
                AppGraph.stats.update {
                    it.copy(surveysCompleted = it.surveysCompleted + 1, automatedMs = it.automatedMs + event.durationMs)
                }
            }
            is AgentEvent.Error -> log(LogType.ERROR, event.packageName, event.message)
            is AgentEvent.Info -> log(LogType.INFO, event.packageName, event.message)
        }
    }

    private fun log(type: LogType, pkg: String, message: String, detail: String = "", confidence: Confidence? = null) {
        AppGraph.logs.add(LogEntry(System.currentTimeMillis(), type, pkg, message, detail, confidence))
    }
}
