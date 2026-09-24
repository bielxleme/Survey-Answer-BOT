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
import com.researchagent.autofill.core.ConfidenceBands
import com.researchagent.autofill.core.DecisionMemory
import com.researchagent.autofill.core.DecisionOutcome
import com.researchagent.autofill.core.Flow
import com.researchagent.autofill.core.FlowRunner
import com.researchagent.autofill.core.InterventionReason
import com.researchagent.autofill.core.ObservedAction
import com.researchagent.autofill.core.TaskState
import com.researchagent.autofill.core.UiKnowledge
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
 * ao Android (serviço de acessibilidade, logs, estatísticas, notificações, aprendizado).
 */
object AgentController : AgentHost {

    private const val TAG = "AgentController"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _status = MutableStateFlow(AgentStatus())
    val status: StateFlow<AgentStatus> = _status.asStateFlow()

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    /** Progresso da automação de operação em execução (null = nenhuma). */
    private val _flowProgress = MutableStateFlow<String?>(null)
    val flowProgress: StateFlow<String?> = _flowProgress.asStateFlow()

    /** Pulso para as Activities se fecharem quando o usuário escolhe "Encerrar aplicativo". */
    private val _exitSignal = MutableStateFlow(0L)
    val exitSignal: StateFlow<Long> = _exitSignal.asStateFlow()

    @Volatile private var engine: AgentEngine? = null
    private var runJob: Job? = null
    private var statusJob: Job? = null
    private var flowJob: Job? = null

    val isActive: Boolean get() = runJob?.isActive == true
    val isFlowRunning: Boolean get() = flowJob?.isActive == true

    override val ownPackage: String get() = AppGraph.app.packageName

    // ── modo "dormente": após Encerrar, a bolha só volta quando o usuário abre o app ──
    private val runtimePrefs get() = AppGraph.app.getSharedPreferences("runtime", android.content.Context.MODE_PRIVATE)
    var dormant: Boolean
        get() = runtimePrefs.getBoolean("dormant", false)
        set(v) { runtimePrefs.edit().putBoolean("dormant", v).apply() }

    fun onServiceConnected(@Suppress("UNUSED_PARAMETER") service: SurveyAccessibilityService) {
        _serviceConnected.value = true
    }

    fun onServiceDisconnected() {
        _serviceConnected.value = false
        stop()
        Observer.shutdown()
        flowJob?.cancel()
    }

    /** Chamado quando o usuário abre o app: sai do modo dormente e mostra a bolha. */
    fun wake() {
        if (dormant) dormant = false
        if (AppGraph.settings.current.bubbleEnabled) SurveyAccessibilityService.instance?.showBubble()
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
        log(LogType.INFO, service.lastPackage, "Agente ativado (modo ${policy().mode.label}${if (policy().guessMode) ", chutar respostas" else ""})")
        return true
    }

    /** PAUSAR: preserva o estado da tarefa para continuar depois. */
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

    /**
     * ENCERRAR APLICATIVO (Seção 1): interrompe a automação, cancela tarefas pendentes,
     * libera listeners/observadores, remove a bolha e fecha as telas do app.
     * Diferente de PAUSAR, não mantém nada em execução.
     */
    fun shutdown() {
        runCatching { stop() }
        flowJob?.cancel(); flowJob = null; _flowProgress.value = null
        runCatching { Observer.shutdown() }
        statusJob?.cancel(); statusJob = null
        _status.value = AgentStatus(mode = AppGraph.settings.current.mode, message = "Encerrado")
        Notifier.cancelIntervention(AppGraph.app)
        dormant = true
        _exitSignal.value = System.currentTimeMillis()
        val service = SurveyAccessibilityService.instance
        service?.hideBubble()
        log(LogType.INFO, "", "Aplicativo encerrado pelo usuário")
        if (AppGraph.settings.current.disableServiceOnExit && service != null && android.os.Build.VERSION.SDK_INT >= 24) {
            runCatching { service.disableSelf() }
        }
    }

    fun setMode(mode: AgentMode) {
        AppGraph.settings.update { it.copy(mode = mode) }
        _status.value = _status.value.copy(mode = mode)
    }

    /** 🎯 CHUTAR RESPOSTAS (Seção 2). */
    fun toggleGuess(): Boolean {
        val on = !AppGraph.settings.current.guessMode
        AppGraph.settings.update { it.copy(guessMode = on) }
        _status.value = _status.value.copy(guessMode = on)
        log(LogType.INFO, "", if (on) "Chutar respostas ATIVADO — tentativas serão registradas como TENTATIVA, nunca como verdade"
            else "Chutar respostas desativado")
        return on
    }

    // ── 🧠 Ensinar automação / ⚙️ Automatizar operação ──────────────────
    fun startTeach() { Observer.start(Observer.Mode.TEACH) }
    fun startRecord() { Observer.start(Observer.Mode.RECORD) }
    fun stopObserving(): String? = Observer.stop()

    /** Fluxos aplicáveis ao app atualmente em primeiro plano. */
    fun flowsForCurrentApp(): List<Flow> {
        val pkg = SurveyAccessibilityService.instance?.lastPackage.orEmpty()
        return AppGraph.learning.flows.value.filter { it.packageName == pkg }
    }

    /** Executa um fluxo aprendido localizando cada elemento por semântica (não por coordenadas). */
    fun runFlow(flow: Flow): Boolean {
        val service = SurveyAccessibilityService.instance ?: return false
        if (isFlowRunning) return false
        if (isActive) pause()
        flowJob = scope.launch {
            log(LogType.INFO, flow.packageName, "Executando automação \"${flow.name}\"", "${flow.steps.size} passos · confiança ${"%.0f".format(flow.confidence * 100)}%")
            val runner = FlowRunner(
                driver = service.driver,
                onProgress = { step, total, msg ->
                    _flowProgress.value = msg
                    onEvent(AgentEvent.FlowStepRun(flow.packageName, flow.name, step, true))
                    if (step == 1 && total > 0) Unit
                },
                askText = { prompt ->
                    val r = intervene(Intervention(System.currentTimeMillis(), InterventionReason.MISSING_INFO, prompt, packageName = flow.packageName))
                    (r as? InterventionResult.Answered)?.answers?.firstOrNull()
                }
            )
            val result = try { runner.run(flow) } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                FlowRunner.Result(false, 0, "Erro: ${t.message}")
            } finally { _flowProgress.value = null }
            AppGraph.learning.flowResult(flow.id, result.success)
            log(if (result.success) LogType.COMPLETED else LogType.ERROR, flow.packageName,
                if (result.success) "Automação concluída" else "Automação interrompida", result.message)
            if (!result.success) {
                // ajuda o sistema a aprender: observa o que o usuário fizer a seguir
                Observer.start(Observer.Mode.TEACH)
            }
        }
        return true
    }

    fun cancelFlow() { flowJob?.cancel(); flowJob = null; _flowProgress.value = null }

    // ── AgentHost ─────────────────────────────────────────────────────
    override fun policy(): AgentPolicy {
        val s = AppGraph.settings.current
        return AgentPolicy(
            mode = s.mode, threshold = s.threshold, autoNextSurvey = s.autoNextSurvey, useOcr = s.useOcr,
            guessMode = s.guessMode,
            observeUnknownAfterMs = if (s.observeUnknown) 15_000 else Long.MAX_VALUE / 4,
            bands = ConfidenceBands(high = s.bandHigh, good = s.bandGood, mid = s.bandMid)
        )
    }

    override fun isPackageAllowed(packageName: String): Boolean =
        AppGraph.settings.isPackageAllowed(packageName, ownPackage)

    override suspend fun intervene(intervention: Intervention): InterventionResult {
        val s = AppGraph.settings.current
        AppGraph.learning.onIntervention()
        Notifier.showIntervention(AppGraph.app, intervention, playSound = s.soundOnIntervention)
        return try {
            InterventionBus.request(intervention)
        } finally {
            Notifier.cancelIntervention(AppGraph.app)
        }
    }

    override fun knowledge(): UiKnowledge = AppGraph.learning.knowledge
    override fun decisions(): DecisionMemory = AppGraph.learning.decisions
    override fun flows(): List<Flow> = AppGraph.learning.flows.value
    override fun onFlowResult(flowId: String, success: Boolean) = AppGraph.learning.flowResult(flowId, success)
    override fun onTaskState(state: TaskState) = AppGraph.learning.saveTask(state)
    override fun userActionsSince(time: Long): List<ObservedAction> = Observer.actionsSince(time)
    override fun isFieldSensitive(fieldKey: String?): Boolean =
        fieldKey != null && AppGraph.profile.current.fieldDef(fieldKey)?.sensitive == true

    override fun requestObservation(reason: String) {
        if (!AppGraph.settings.current.observeUnknown) return
        if (Observer.mode.value == Observer.Mode.OFF) Observer.start(Observer.Mode.AUTO)
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
            is AgentEvent.Guess -> {
                val shown = if (!settings.logAnswers) "••••" else event.decision.display
                log(LogType.GUESS, event.packageName, event.question.take(140),
                    "TENTATIVA → $shown · ${event.decision.reason} (confiança ${"%.0f".format(event.decision.confidence * 100)}%)")
            }
            is AgentEvent.AnswerConfirmed -> log(
                if (event.detected) LogType.USER_ANSWER else LogType.INTERVENTION, event.packageName, event.question.take(140),
                if (event.detected) "Resposta detectada: ${if (settings.logAnswers) event.answers.joinToString() else "••••"} · ${event.detail}"
                else "Não consegui identificar sua resposta · ${event.detail}")
            is AgentEvent.LoopDetected -> log(LogType.LOOP, event.packageName, "Loop detectado", event.detail)
            is AgentEvent.Resumed -> log(LogType.RESUME, event.packageName, "Retomado", event.detail)
            is AgentEvent.ButtonDetected -> if (event.learned) log(LogType.LEARN, event.packageName,
                "Botão reconhecido por aprendizado", "\"${event.label}\" · confiança ${"%.0f".format(event.confidence * 100)}%")
            is AgentEvent.Outcome -> if (event.outcome == DecisionOutcome.FAILED)
                log(LogType.LEARN, event.packageName, "Resultado de tentativa", "${event.questionKey.take(80)} → ${event.outcome.name}")
            is AgentEvent.FlowStepRun -> Unit
            is AgentEvent.ObservationRequested -> log(LogType.OBSERVE, event.packageName, "Pesquisa não reconhecida — observando o usuário", event.reason)
        }
    }

    private fun log(type: LogType, pkg: String, message: String, detail: String = "", confidence: Confidence? = null) {
        AppGraph.logs.add(LogEntry(System.currentTimeMillis(), type, pkg, message, detail, confidence))
    }
}
