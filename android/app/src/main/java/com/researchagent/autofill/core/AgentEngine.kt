package com.researchagent.autofill.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

/** Máquina de estados do agente (Seção 13). */
enum class AgentState(val label: String) {
    IDLE("Pronto"),
    SCANNING("Analisando a tela"),
    RESEARCH_DETECTED("Pesquisa detectada"),
    READING_QUESTION("Lendo pergunta"),
    UNDERSTANDING_QUESTION("Interpretando pergunta"),
    SEARCHING_PROFILE("Consultando perfil"),
    GENERATING_RESPONSE("Gerando resposta"),
    VALIDATING_RESPONSE("Validando resposta"),
    FILLING_FIELD("Preenchendo campo"),
    VERIFYING_FIELD("Verificando campo"),
    NEXT_PAGE("Avançando página"),
    WAITING("Aguardando"),
    RESEARCH_COMPLETED("Pesquisa concluída"),
    SEARCHING_NEXT_RESEARCH("Procurando próxima pesquisa"),
    USER_INTERVENTION_REQUIRED("Aguardando você"),
    OBSERVING("Observando você para aprender"),
    RUNNING_FLOW("Executando automação aprendida"),
    PAUSED("Pausado")
}

/** Modos de operação (Seção 33). */
enum class AgentMode(val label: String, val description: String) {
    MANUAL("Manual", "Apenas identifica perguntas e sugere respostas."),
    ASSISTED("Assistido", "Preenche respostas de alta confiança e pede confirmação antes de avançar."),
    AUTOMATIC("Automático", "Preenche e avança sozinho quando a resposta é comprovada pelo perfil.")
}

data class Suggestion(val question: String, val answer: String, val level: Confidence, val reason: String)

data class AgentStatus(
    val running: Boolean = false,
    val paused: Boolean = false,
    val state: AgentState = AgentState.IDLE,
    val message: String = "",
    val mode: AgentMode = AgentMode.ASSISTED,
    val surveyIndex: Int = 0,
    val questionIndex: Int = 0,
    val questionsOnPage: Int = 0,
    val pageProgress: Pair<Int, Int>? = null,
    val currentApp: String = "",
    val suggestions: List<Suggestion> = emptyList(),
    val intervention: Intervention? = null,
    val confidence: ConfidenceReport = ConfidenceReport(),
    val guessMode: Boolean = false,
    val screenKind: ScreenKind = ScreenKind.OTHER
)

data class Intervention(
    val id: Long,
    val reason: InterventionReason,
    val message: String,
    val question: SurveyQuestion? = null,
    val suggestedField: String? = null,
    val suggestion: AnswerDecision? = null,
    val packageName: String = ""
)

sealed class InterventionResult {
    /** Usuário forneceu a(s) resposta(s) — o agente preenche. */
    data class Answered(val answers: List<String>) : InterventionResult()
    /** Usuário resolveu na tela (ex.: CAPTCHA, login ou preencheu manualmente) — o agente VERIFICA e continua. */
    object Resume : InterventionResult()
    /** Ignorar esta pergunta/etapa. */
    object Skip : InterventionResult()
    /** Parar a automação. */
    object Stop : InterventionResult()
}

data class AgentPolicy(
    val mode: AgentMode = AgentMode.ASSISTED,
    val threshold: Double = 0.85,
    val autoNextSurvey: Boolean = false,
    val useOcr: Boolean = true,
    val pageTimeoutMs: Long = 12_000,
    val stuckTimeoutMs: Long = 180_000,
    val maxScrollsPerPage: Int = 12,
    /** "Chutar respostas" (Seção 2): continua com a alternativa mais provável quando falta certeza. */
    val guessMode: Boolean = false,
    /** Tempo numa tela não reconhecida antes de propor observar o usuário (Seção 3). */
    val observeUnknownAfterMs: Long = 15_000,
    /** Logo após ATIVAR, quanto esperar numa tela não reconhecida antes de perguntar o que fazer. */
    val unknownPromptAfterMs: Long = 6_000,
    val bands: ConfidenceBands = ConfidenceBands(),
    /** Confiança mínima de um fluxo aprendido para executá-lo sem perguntar. */
    val flowMinConfidence: Double = 0.5
)

sealed class AgentEvent {
    data class SurveyStarted(val packageName: String) : AgentEvent()
    data class QuestionAnswered(val packageName: String, val question: String, val decision: AnswerDecision, val byUser: Boolean) : AgentEvent()
    data class QuestionUnknown(val packageName: String, val question: String, val fieldKey: String?, val options: List<String>) : AgentEvent()
    data class InterventionRaised(val packageName: String, val reason: InterventionReason, val message: String) : AgentEvent()
    data class SurveyCompleted(val packageName: String, val questions: Int, val durationMs: Long) : AgentEvent()
    data class Error(val packageName: String, val message: String) : AgentEvent()
    data class Info(val packageName: String, val message: String) : AgentEvent()
    // ── eventos da evolução (Seção 22) ──
    data class Guess(val packageName: String, val question: String, val decision: AnswerDecision) : AgentEvent()
    data class AnswerConfirmed(val packageName: String, val question: String, val answers: List<String>, val detected: Boolean, val detail: String) : AgentEvent()
    data class LoopDetected(val packageName: String, val detail: String) : AgentEvent()
    data class Resumed(val packageName: String, val detail: String) : AgentEvent()
    data class ButtonDetected(val packageName: String, val label: String, val learned: Boolean, val confidence: Double) : AgentEvent()
    data class Outcome(val packageName: String, val questionKey: String, val outcome: DecisionOutcome) : AgentEvent()
    data class FlowStepRun(val packageName: String, val flowName: String, val step: Int, val ok: Boolean) : AgentEvent()
    data class ObservationRequested(val packageName: String, val reason: String) : AgentEvent()
}

/** Abstração da tela — implementada pelo AccessibilityService no Android e por fakes nos testes. */
interface ScreenDriver {
    suspend fun snapshot(): ScreenSnapshot?
    suspend fun ocrSnapshot(): ScreenSnapshot? = null
    suspend fun click(node: ScreenNode): Boolean
    suspend fun setText(node: ScreenNode, text: String): Boolean
    suspend fun setProgress(node: ScreenNode, value: Float): Boolean
    suspend fun scrollForward(): Boolean
    suspend fun back(): Boolean
    /** Aguarda a tela mudar em relação a [previous] (baseado em eventos + verificação). Null = timeout. */
    suspend fun awaitChange(previous: ScreenSnapshot, timeoutMs: Long): ScreenSnapshot?
}

/** Quem decide a resposta: regras + (opcional) LLM validado. */
interface AnswerProvider {
    suspend fun decide(question: SurveyQuestion, ctx: SurveyContext): AnswerDecision
}

interface AgentHost {
    val ownPackage: String
    fun policy(): AgentPolicy
    fun isPackageAllowed(packageName: String): Boolean
    suspend fun intervene(intervention: Intervention): InterventionResult
    fun onEvent(event: AgentEvent)
    fun now(): Long = System.currentTimeMillis()
    // ── memória e aprendizado (Seção 10). Implementações padrão = sem memória (testes antigos seguem válidos) ──
    fun knowledge(): UiKnowledge? = null
    fun decisions(): DecisionMemory? = null
    fun flows(): List<Flow> = emptyList()
    fun onFlowResult(flowId: String, success: Boolean) {}
    fun onTaskState(state: TaskState) {}
    /** Ações do usuário observadas desde [time] (cliques/digitação durante a intervenção). */
    fun userActionsSince(time: Long): List<ObservedAction> = emptyList()
    /** Pede ao controlador para entrar em modo de observação (tela não reconhecida). */
    fun requestObservation(reason: String) {}
    fun isFieldSensitive(fieldKey: String?): Boolean = false
    /** Valores já conhecidos do perfil (não sensíveis) — usados primeiro pelo "chutar respostas". */
    fun knownAnswers(): List<String> = emptyList()
}

/**
 * Agente orientado a objetivos (Seções 2, 13, 14, 30, 31, 32, 40, 41 + evolução autodidata).
 * Ciclo: OBSERVAR → INTERPRETAR → MEMÓRIA → DECIDIR → EXECUTAR → VERIFICAR → APRENDER.
 * Um passo por iteração: analisa a tela → resolve a primeira pendência → verifica na próxima leitura.
 */
class AgentEngine(
    private val driver: ScreenDriver,
    private val answers: AnswerProvider,
    private val host: AgentHost
) {
    private val _status = MutableStateFlow(AgentStatus())
    val status: StateFlow<AgentStatus> = _status.asStateFlow()

    private val paused = MutableStateFlow(false)
    private var stopRequested = false
    private var interventionSeq = 0L

    // estado da pesquisa atual
    private val ctx = SurveyContext()
    private var inSurvey = false
    private var surveyStartedAt = 0L
    private var surveyQuestions = 0
    private var surveyPackage = ""
    private val skipped = HashSet<String>()
    private val attempts = HashMap<String, Int>()
    /** Perguntas genéricas (cartões clicáveis) já respondidas nesta página — o estado marcado pode não aparecer. */
    private val filledGeneric = HashSet<String>()
    /** Decisões aguardando resultado (chutes e memória) — avaliadas ao avançar (Seção 2). */
    private val pendingOutcome = LinkedHashSet<String>()
    private var samePageNavAttempts = 0
    private var scrollsOnPage = 0
    private var lastProgressAt = 0L
    private var startClicks = 0
    private var lastSuggestionSig = 0
    private var unknownSince = 0L
    private var observationRequestedFor = 0
    private var lastDecisionConfidence = 0.0
    private var lastResultConfidence = 0.0
    private val loops = LoopDetector()
    private var activeFlowId: String? = null
    private var promptedSinceStart = false

    fun pause() {
        paused.value = true
        update { it.copy(paused = true, state = AgentState.PAUSED, message = "Pausado pelo usuário") }
        saveTask("paused", nextAction = "resume")
        poke()
    }
    fun resume() { paused.value = false; update { it.copy(paused = false, message = "Retomando…") }; poke() }
    fun requestStop() { stopRequested = true; paused.value = false; poke() }

    /** Interrompe qualquer espera e relê a tela imediatamente (ex.: usuário tocou em ATIVAR/CONTINUAR). */
    private val pokes = MutableStateFlow(0)
    fun poke() { pokes.value = pokes.value + 1 }

    /** O usuário confirmou que a tela atual É uma pesquisa: aceita perguntas com menos sinais. */
    @Volatile private var forcedPackage: String? = null
    fun forceSurvey(pkg: String) { forcedPackage = pkg; unknownSince = 0; poke() }

    /** Espera a tela mudar, mas acorda na hora se o usuário cutucar o agente (sem travar 15–60 s). */
    private suspend fun waitChange(prev: ScreenSnapshot, timeoutMs: Long): ScreenSnapshot? = coroutineScope {
        val start = pokes.value
        val waiter = async { driver.awaitChange(prev, timeoutMs) }
        val poker = launch { pokes.first { it != start }; waiter.cancel() }
        try { waiter.await() } catch (e: CancellationException) {
            if (!currentCoroutineContext().isActive) throw e
            null
        } finally { poker.cancel() }
    }
    val isPaused: Boolean get() = paused.value

    private fun update(f: (AgentStatus) -> AgentStatus) { _status.value = f(_status.value) }

    private fun setState(state: AgentState, message: String = state.label) =
        update { it.copy(state = state, message = message) }

    private fun saveTask(status: String, q: SurveyQuestion? = null, answers: List<String> = emptyList(),
                         intervention: Boolean = false, nextAction: String = "", signature: Int = 0) {
        val s = _status.value
        host.onTaskState(TaskState(
            status = status, packageName = s.currentApp, surveyIndex = s.surveyIndex, step = s.questionIndex,
            question = q?.text.orEmpty(), questionKey = q?.key.orEmpty(), options = q?.optionTexts.orEmpty(),
            detectedAnswers = answers, userIntervention = intervention, nextAction = nextAction,
            screenSignature = signature, updatedAt = host.now()
        ))
    }

    private suspend fun awaitNotPaused() {
        if (paused.value) {
            setState(AgentState.PAUSED, "Pausado")
            paused.first { !it }
        }
    }

    private fun alive(): Boolean = !stopRequested

    suspend fun run() {
        stopRequested = false
        promptedSinceStart = false
        lastProgressAt = host.now()
        update { AgentStatus(running = true, mode = host.policy().mode, surveyIndex = it.surveyIndex, guessMode = host.policy().guessMode) }
        saveTask("running", nextAction = "scan")
        try {
            while (currentCoroutineContext().isActive && alive()) {
                awaitNotPaused()
                if (!alive()) break
                step()
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            update { it.copy(running = false, paused = false, state = AgentState.IDLE, message = "Parado", intervention = null) }
            saveTask("stopped")
        }
    }

    /** Uma iteração da máquina de estados. Público para testes. */
    suspend fun step() {
        val policy = host.policy()
        update { it.copy(mode = policy.mode, guessMode = policy.guessMode) }
        setState(AgentState.SCANNING)
        var snap = driver.snapshot()
        if (snap == null) { setState(AgentState.WAITING, "Aguardando uma tela legível…"); delay(1500); return }
        if (snap.packageName == host.ownPackage) { setState(AgentState.WAITING, "Abra o app da pesquisa"); delay(1200); return }
        update { it.copy(currentApp = snap!!.packageName) }

        if (!host.isPackageAllowed(snap.packageName)) {
            val r = intervene(InterventionReason.BLOCKED_APP,
                "O app ${snap.packageName} está bloqueado para automação (bancos, mensagens, sistema ou fora da lista permitida).",
                pkg = snap.packageName)
            if (r is InterventionResult.Resume || r is InterventionResult.Skip) waitChange(snap, 20_000)
            return
        }

        // Árvore pobre (canvas, imagem): tenta OCR para compreender (Seção 11)
        if (policy.useOcr && snap.textNodeCount < 3) {
            driver.ocrSnapshot()?.let { ocr -> if (ocr.textNodeCount > snap!!.textNodeCount) snap = ocr }
        }
        val current = snap!!
        var page = SurveyAnalyzer.analyze(current, host.knowledge())
        if (!page.isSurvey && forcedPackage == current.packageName && page.questions.isNotEmpty() && !page.completed && page.guard == null) {
            page = page.copy(isSurvey = true)   // você confirmou que é pesquisa
        }
        val kind = UiSemantics.classifyScreen(current, page)
        reportConfidence(page, kind)

        // Riscos: CAPTCHA, login, pagamento (Seções 16–18)
        page.guard?.let { g ->
            val r = intervene(g.reason, "${g.reason.title}. Resolva na tela e toque em JÁ RESOLVI.", pkg = current.packageName)
            if (r is InterventionResult.Skip) waitChange(current, 30_000)
            return
        }

        if (page.completed) { onCompleted(current, page, policy); return }

        if (!page.isSurvey) { onNoSurvey(current, page, policy, kind); return }

        unknownSince = 0
        if (activeFlowId != null) { host.onFlowResult(activeFlowId!!, true); activeFlowId = null }
        if (!inSurvey) startSurvey(current.packageName)
        startClicks = 0
        setState(AgentState.RESEARCH_DETECTED)
        detectCorrections(current, page)
        val pending = page.questions.filter { !it.answered && it.key !in skipped && !(it.generic && it.key in filledGeneric) }
        update {
            it.copy(pageProgress = page.progress, questionsOnPage = page.questions.size,
                questionIndex = page.questions.count { q -> q.answered || q.key in filledGeneric } + 1)
        }

        // Watchdog global (Seção 41)
        if (host.now() - lastProgressAt > policy.stuckTimeoutMs) {
            lastProgressAt = host.now()
            handleStuck(current, "Sem progresso há ${policy.stuckTimeoutMs / 1000}s nesta etapa.")
            return
        }

        if (policy.mode == AgentMode.MANUAL) { suggestOnly(current, page); return }

        val q = pending.firstOrNull()
        if (q != null) { handleQuestion(current, q, policy); return }

        navigate(current, page, policy)
    }

    private fun reportConfidence(page: SurveyPage, kind: ScreenKind) {
        val qs = page.questions
        val rep = ConfidenceReport(
            survey = if (page.isSurvey) (page.score / 10.0).coerceIn(0.5, 1.0) else (page.score / 10.0).coerceIn(0.0, 0.49),
            question = when { qs.isEmpty() -> 0.0; qs.all { it.generic } -> 0.7; else -> 0.95 },
            answers = when { qs.isEmpty() -> 0.0; qs.any { it.options.isEmpty() && it.inputNodeId == null } -> 0.5; qs.any { it.generic } -> 0.7; else -> 0.95 },
            button = page.buttonConfidence,
            decision = lastDecisionConfidence,
            result = lastResultConfidence
        )
        update { it.copy(confidence = rep, screenKind = kind) }
    }

    private fun startSurvey(pkg: String) {
        inSurvey = true
        surveyStartedAt = host.now()
        surveyQuestions = 0
        surveyPackage = pkg
        ctx.clear(); skipped.clear(); attempts.clear(); filledGeneric.clear(); pendingOutcome.clear()
        samePageNavAttempts = 0; scrollsOnPage = 0
        lastProgressAt = host.now()
        loops.reset()
        update { it.copy(surveyIndex = it.surveyIndex + 1, questionIndex = 0) }
        host.onEvent(AgentEvent.SurveyStarted(pkg))
        saveTask("running", nextAction = "answer")
    }

    /** Aprendizado por correção (Seção 18): o usuário trocou a opção que o agente havia marcado. */
    private fun detectCorrections(snap: ScreenSnapshot, page: SurveyPage) {
        for (q in page.questions) {
            if (q.generic || q.options.isEmpty()) continue
            val mine = ctx.answersByQuestion[q.key] ?: continue
            val now = q.options.filter { it.checked }.map { it.text }
            if (now.isEmpty() || now.toSet() == mine.toSet()) continue
            host.decisions()?.add(DecisionRecord(q.key, q.text, q.optionTexts, now, DecisionOrigin.CORRECTION, 1.0, snap.packageName, host.now()))
            host.onEvent(AgentEvent.AnswerConfirmed(snap.packageName, q.text, now, true, "Correção do usuário: ${mine.joinToString()} → ${now.joinToString()}"))
            ctx.answersByQuestion[q.key] = now
        }
    }

    // ── Pergunta ──────────────────────────────────────────────────────
    private suspend fun handleQuestion(snap: ScreenSnapshot, q: SurveyQuestion, policy: AgentPolicy) {
        val n = (attempts[q.key] ?: 0) + 1
        attempts[q.key] = n
        if (n > 3) {
            val r = intervene(InterventionReason.VALIDATION_ERROR,
                "O campo \"${q.text.take(80)}\" não aceitou a resposta. Corrija na tela e toque em JÁ RESOLVI.", q, pkg = snap.packageName)
            if (r !is InterventionResult.Stop) { skipped += q.key; attempts.remove(q.key) }
            return
        }
        setState(AgentState.READING_QUESTION, "Lendo: ${q.text.take(60)}")
        setState(AgentState.UNDERSTANDING_QUESTION)
        setState(AgentState.SEARCHING_PROFILE)
        setState(AgentState.GENERATING_RESPONSE)
        var decision = answers.decide(q, ctx)
        setState(AgentState.VALIDATING_RESPONSE)

        // Memória de decisões (respostas que você já deu a perguntas iguais/parecidas)
        if (decision.needsUser || decision.confidence < policy.threshold) {
            host.decisions()?.recall(q.text, q.optionTexts)?.let { m ->
                if (m.confidence >= policy.threshold || (decision.needsUser && m.confidence > decision.confidence)) {
                    decision = m.copy(fieldKey = decision.fieldKey)
                }
            }
        }
        lastDecisionConfidence = if (decision.needsUser) 0.0 else decision.confidence

        var isGuess = false
        if (decision.needsUser || decision.confidence < policy.threshold) {
            // "Chutar respostas" (Seção 2): tentativa registrada como tal, nunca como verdade
            val guess = if (policy.guessMode) Guesser.guess(q, decision.takeIf { !it.needsUser }, host.decisions(), host.isFieldSensitive(decision.fieldKey), host.knownAnswers()) else null
            if (guess != null) {
                decision = guess
                isGuess = true
                lastDecisionConfidence = guess.confidence
                host.onEvent(AgentEvent.Guess(snap.packageName, q.text, guess))
            } else {
                if (decision.needsUser) {
                    host.onEvent(AgentEvent.QuestionUnknown(snap.packageName, q.text, decision.fieldKey, q.optionTexts))
                }
                val msg = if (decision.needsUser) "INFORMAÇÃO NECESSÁRIA: ${decision.reason}"
                          else "Resposta com confiança ${decision.level.label.lowercase()} — confirme: ${decision.display}"
                val startedAt = host.now()
                saveTask("paused", q, intervention = true, nextAction = "verify_user_answer", signature = snap.signature)
                when (val r = intervene(InterventionReason.MISSING_INFO, msg, q, decision.fieldKey, decision, snap.packageName)) {
                    is InterventionResult.Answered -> {
                        decision = AnswerDecision(AnswerAction.ANSWER, r.answers, 1.0, "user", "Resposta informada pelo usuário.", decision.fieldKey, "user")
                        // Guarda na memória da pesquisa e preenche no próximo passo, com a tela da pesquisa
                        // novamente em primeiro plano e nós de acessibilidade atualizados.
                        ctx.remember(q, decision)
                        recordUserDecision(snap.packageName, q, r.answers, suggestion = null)
                        surveyQuestions++
                        host.onEvent(AgentEvent.QuestionAnswered(snap.packageName, q.text, decision, byUser = true))
                        lastProgressAt = host.now()
                        saveTask("running", q, r.answers, nextAction = "fill")
                        return
                    }
                    InterventionResult.Resume -> { verifyUserResolution(snap, q, decision, startedAt); return }
                    InterventionResult.Skip -> { skipped += q.key; saveTask("running", q, nextAction = "skip"); return }
                    InterventionResult.Stop -> return
                }
            }
        }

        setState(AgentState.FILLING_FIELD, "Preenchendo: ${decision.display.take(40)}${if (isGuess) " (tentativa)" else ""}")
        val ok = fill(snap, q, decision)
        setState(AgentState.VERIFYING_FIELD)
        if (ok) {
            val fromSession = decision.source == "session"
            ctx.remember(q, decision)
            if (q.generic) filledGeneric += q.key
            lastProgressAt = host.now()
            if (isGuess || decision.engine == "memory") {
                host.decisions()?.add(DecisionRecord(q.key, q.text, q.optionTexts, decision.answers,
                    if (isGuess) DecisionOrigin.GUESS else DecisionOrigin.USER, decision.confidence, snap.packageName, host.now()))
                pendingOutcome += q.key
            }
            if (!fromSession) {
                surveyQuestions++
                host.onEvent(AgentEvent.QuestionAnswered(snap.packageName, q.text, decision, byUser = false))
            }
            driver.awaitChange(snap, 1500) // deixa a UI refletir a marcação
        } else {
            host.onEvent(AgentEvent.Error(snap.packageName, "Falha ao preencher \"${q.text.take(60)}\""))
            delay(400)
        }
    }

    private fun recordUserDecision(pkg: String, q: SurveyQuestion, answers: List<String>, suggestion: String?) {
        if (answers.isEmpty()) return
        host.decisions()?.add(DecisionRecord(q.key, q.text, q.optionTexts, answers,
            if (suggestion != null) DecisionOrigin.CORRECTION else DecisionOrigin.USER, 1.0, pkg, host.now()))
    }

    /**
     * "JÁ RESOLVI" (Seções 13 e 14): não continua às cegas. Captura a tela atual, compara com a anterior,
     * procura a resposta (marcação, texto ou toque observado) e só então segue.
     */
    private suspend fun verifyUserResolution(before: ScreenSnapshot, q: SurveyQuestion, suggestion: AnswerDecision, startedAt: Long) {
        setState(AgentState.VERIFYING_FIELD, "Verificando sua resposta…")
        var tries = 0
        while (tries < 2 && alive()) {
            tries++
            val now = driver.awaitChange(before, 1200) ?: driver.snapshot() ?: return
            val page = SurveyAnalyzer.analyze(now, host.knowledge())
            val same = page.questions.firstOrNull { it.key == q.key }
            val observed = host.userActionsSince(startedAt)
            val tapped = q.options.filter { o -> observed.any { a -> a.target.text.isNotEmpty() && Text.looselyEquals(a.target.text, o.text) } }.map { it.text }
            val typed = observed.lastOrNull { it.action == ActionType.TEXT && !it.text.isNullOrBlank() }?.text
            val detected: List<String>? = when {
                same == null && now.contentSignature != before.contentSignature ->
                    tapped.ifEmpty { listOfNotNull(typed) }.ifEmpty { listOf("(avançou)") }
                same != null && same.options.any { it.checked } -> same.options.filter { it.checked }.map { it.text }
                same != null && same.inputNodeId != null && same.currentValue.isNotBlank() -> listOf(same.currentValue)
                same != null && tapped.isNotEmpty() -> tapped
                same != null && typed != null && q.options.isEmpty() -> listOf(typed)
                else -> null
            }
            if (detected != null) {
                val real = detected.filter { it != "(avançou)" }
                if (real.isNotEmpty()) {
                    val corrected = !suggestion.needsUser && suggestion.answers.isNotEmpty() && suggestion.answers.toSet() != real.toSet()
                    host.decisions()?.add(DecisionRecord(q.key, q.text, q.optionTexts, real,
                        if (corrected) DecisionOrigin.CORRECTION else DecisionOrigin.USER, 1.0, now.packageName, host.now(), DecisionOutcome.CONTINUED))
                    ctx.remember(q, AnswerDecision(AnswerAction.ANSWER, real, 1.0, "user", "Resposta do usuário na tela.", engine = "user"))
                }
                if (q.generic) filledGeneric += q.key
                skipped += q.key // já resolvida: não voltar a perguntar
                surveyQuestions++
                lastProgressAt = host.now()
                host.onEvent(AgentEvent.AnswerConfirmed(now.packageName, q.text, real, true,
                    if (real.isEmpty()) "Tela avançou após sua ação" else "Resposta detectada: ${real.joinToString()}"))
                host.onEvent(AgentEvent.Resumed(now.packageName, "Automação retomada após verificação"))
                saveTask("running", q, real, nextAction = "continue", signature = now.signature)
                return
            }
            // não detectou: não avança e avisa (Seção 14)
            host.onEvent(AgentEvent.AnswerConfirmed(now.packageName, q.text, emptyList(), false, "Resposta não detectada"))
            val r = intervene(InterventionReason.MISSING_INFO,
                "Não consegui identificar sua resposta para \"${q.text.take(90)}\". Marque na tela e toque em JÁ RESOLVI — " +
                    "ou IGNORAR para continuar mesmo assim.", q, suggestion.fieldKey, suggestion, now.packageName)
            when (r) {
                InterventionResult.Resume -> continue
                is InterventionResult.Answered -> {
                    ctx.remember(q, AnswerDecision(AnswerAction.ANSWER, r.answers, 1.0, "user", "Resposta informada pelo usuário.", engine = "user"))
                    recordUserDecision(now.packageName, q, r.answers, null)
                    return
                }
                InterventionResult.Skip -> { skipped += q.key; return }
                InterventionResult.Stop -> return
            }
        }
        // após duas confirmações sem detecção visível, confia no usuário (widget sem estado acessível)
        skipped += q.key
        if (q.generic) filledGeneric += q.key
        host.onEvent(AgentEvent.Resumed(before.packageName, "Continuando por confirmação do usuário (estado não visível)"))
    }

    /** Executa a resposta na interface. Nunca clica em nada fora das opções da pergunta. */
    private suspend fun fill(snap: ScreenSnapshot, q: SurveyQuestion, d: AnswerDecision): Boolean {
        return when (q.type) {
            QuestionType.SINGLE_CHOICE, QuestionType.MULTI_CHOICE -> {
                var any = false
                for (ans in d.answers) {
                    val opt = q.options.firstOrNull { Text.looselyEquals(it.text, ans) }
                        ?: q.options.firstOrNull { AnswerEngine.matchesText(it.text, ans) }
                        ?: continue
                    if (opt.checked) { any = true; continue }
                    val node = snap.node(opt.nodeId) ?: continue
                    if (driver.click(node)) any = true
                    if (q.type == QuestionType.SINGLE_CHOICE) break
                    delay(250)
                }
                any
            }
            QuestionType.TEXT, QuestionType.NUMBER, QuestionType.DATE -> {
                val node = q.inputNodeId?.let { snap.node(it) } ?: return false
                val text = if (q.type == QuestionType.NUMBER) {
                    Values.parseNumber(d.answers.first())?.let { Values.formatNumber(it) } ?: d.answers.first()
                } else d.answers.first()
                driver.setText(node, text)
            }
            QuestionType.SLIDER -> {
                val node = q.inputNodeId?.let { snap.node(it) } ?: return false
                val v = Values.parseNumber(d.answers.first())?.toFloat() ?: return false
                val clamped = v.coerceIn(node.rangeMin ?: v, node.rangeMax ?: v)
                driver.setProgress(node, clamped)
            }
            QuestionType.DROPDOWN -> fillDropdown(snap, q, d)
        }
    }

    private suspend fun fillDropdown(snap: ScreenSnapshot, q: SurveyQuestion, d: AnswerDecision): Boolean {
        val node = q.inputNodeId?.let { snap.node(it) } ?: return false
        if (!driver.click(node)) return false
        val opened = driver.awaitChange(snap, 3000) ?: driver.snapshot() ?: return false
        val wanted = d.answers.first()
        val candidates = opened.visibleNodes.filter { it.label.isNotBlank() && it.id != node.id && !it.isEditable }
        val target = candidates.firstOrNull { Text.looselyEquals(it.label, wanted) }
            ?: candidates.filter { AnswerEngine.matchesText(it.label, wanted) }.singleOrNull()
        if (target == null) {
            driver.back()
            return false
        }
        return driver.click(target)
    }

    // ── Navegação ─────────────────────────────────────────────────────
    private suspend fun navigate(snap: ScreenSnapshot, page: SurveyPage, policy: AgentPolicy) {
        var nav = page.nextButton ?: page.submitButton
        val isSubmit = page.nextButton == null && page.submitButton != null

        if (nav == null) {
            // Rola para revelar mais perguntas ou o botão (Seção 40)
            if (scrollsOnPage < policy.maxScrollsPerPage && driver.scrollForward()) {
                scrollsOnPage++
                setState(AgentState.SCANNING, "Rolando a página…")
                driver.awaitChange(snap, 1500)
                return
            }
            // OCR como último recurso para achar o botão
            if (policy.useOcr) {
                driver.ocrSnapshot()?.let { ocr ->
                    nav = SurveyAnalyzer.findButton(ocr, SurveyAnalyzer.NEXT_WORDS) ?: SurveyAnalyzer.findButton(ocr, SurveyAnalyzer.SUBMIT_WORDS)
                }
            }
            if (nav == null) { handleStuck(snap, "Não encontrei o botão \"Próximo\"/\"Enviar\". Toque nele você mesmo — vou aprender qual é."); return }
        }
        val target = nav!!
        host.onEvent(AgentEvent.ButtonDetected(snap.packageName, target.label, page.learnedButton, page.buttonConfidence))

        if (policy.mode == AgentMode.ASSISTED) {
            val label = if (isSubmit) "ENVIAR a pesquisa" else "avançar para a próxima página"
            when (intervene(InterventionReason.CONFIRM_NEXT, "Respostas preenchidas. Posso $label (\"${target.label}\")?", pkg = snap.packageName)) {
                is InterventionResult.Answered, InterventionResult.Resume -> Unit
                InterventionResult.Skip -> { waitChange(snap, 60_000); return }
                InterventionResult.Stop -> return
            }
        }

        // Proteção contra loop (Seção 19)
        if (loops.record(snap.signature, "nav:${Text.normalize(target.label)}")) {
            onLoop(snap, page, target, "Toquei em \"${target.label}\" várias vezes e a tela não mudou.")
            return
        }

        val errorsBefore = SurveyAnalyzer.errorPhrases(snap)
        val descriptor = UiSemantics.describe(snap, target, ScreenKind.SURVEY_QUESTION)
        setState(AgentState.NEXT_PAGE, "Tocando em \"${target.label}\"")
        driver.click(target)
        setState(AgentState.WAITING, "Aguardando carregamento…")
        val after = driver.awaitChange(snap, policy.pageTimeoutMs)
        if (after != null && after.contentSignature != snap.contentSignature) {
            samePageNavAttempts = 0; scrollsOnPage = 0
            lastProgressAt = host.now()
            attempts.clear(); filledGeneric.clear()
            lastResultConfidence = 0.9
            host.knowledge()?.record(descriptor, if (isSubmit) ElementRole.SUBMIT else ElementRole.NEXT, snap.packageName, true)
            settleOutcomes(snap.packageName, DecisionOutcome.CONTINUED)
            return
        }
        samePageNavAttempts++
        lastResultConfidence = 0.3
        val check = after ?: driver.snapshot()
        val newErrors = check?.let { SurveyAnalyzer.errorPhrases(it) - errorsBefore }.orEmpty()
        if (newErrors.isNotEmpty()) {
            settleOutcomes(snap.packageName, DecisionOutcome.FAILED)
            intervene(InterventionReason.VALIDATION_ERROR,
                "A página mostrou: \"${newErrors.first()}\". Corrija os campos destacados e toque em JÁ RESOLVI.", pkg = snap.packageName)
            samePageNavAttempts = 0
        } else if (samePageNavAttempts >= 3) {
            host.knowledge()?.record(descriptor, ElementRole.NEXT, snap.packageName, false)
            handleStuck(snap, "Toquei em \"${target.label}\" ${samePageNavAttempts}x e a página não mudou.")
        }
    }

    private fun settleOutcomes(pkg: String, outcome: DecisionOutcome) {
        for (k in pendingOutcome) {
            host.decisions()?.setOutcome(k, outcome)
            host.onEvent(AgentEvent.Outcome(pkg, k, outcome))
        }
        pendingOutcome.clear()
    }

    /** Loop detectado (Seção 19): estratégia alternativa → memória → usuário, e registra o erro. */
    private suspend fun onLoop(snap: ScreenSnapshot, page: SurveyPage, failed: ScreenNode, why: String) {
        host.onEvent(AgentEvent.LoopDetected(snap.packageName, why))
        host.knowledge()?.record(UiSemantics.describe(snap, failed), ElementRole.NEXT, snap.packageName, false)
        loops.reset()
        // 1) alternativa: outro botão com papel de avançar aprendido, ou o de enviar
        val alt = host.knowledge()?.bestFor(snap, ElementRole.NEXT)?.first?.takeIf { it.id != failed.id }
            ?: page.submitButton?.takeIf { it.id != failed.id }
        if (alt != null) {
            setState(AgentState.NEXT_PAGE, "Loop detectado — tentando \"${alt.label}\"")
            driver.click(alt)
            if (driver.awaitChange(snap, 6000)?.contentSignature?.let { it != snap.contentSignature } == true) {
                host.knowledge()?.record(UiSemantics.describe(snap, alt), ElementRole.NEXT, snap.packageName, true)
                return
            }
        }
        // 2) rolar (o botão certo pode estar abaixo)
        if (driver.scrollForward()) return
        // 3) usuário
        handleStuck(snap, "$why Mostre-me o caminho tocando no botão certo; vou aprender.")
    }

    private suspend fun handleStuck(snap: ScreenSnapshot, why: String) {
        samePageNavAttempts = 0
        val startedAt = host.now()
        val r = intervene(InterventionReason.STUCK, "Não consegui avançar com segurança. $why", pkg = snap.packageName)
        if (r is InterventionResult.Skip) waitChange(snap, 30_000)
        if (r is InterventionResult.Resume) learnFromUserActions(startedAt)
        scrollsOnPage = 0
        lastProgressAt = host.now()
    }

    /** Aprende o papel dos elementos que o usuário tocou durante a intervenção (ex.: o botão de avançar real). */
    private fun learnFromUserActions(since: Long) {
        val k = host.knowledge() ?: return
        for (a in host.userActionsSince(since)) {
            if (a.action != ActionType.CLICK || !a.changedScreen) continue
            val role = when {
                a.before.kind == ScreenKind.SURVEY_QUESTION && a.target.role != ElementRole.ANSWER_OPTION -> ElementRole.NEXT
                a.before.kind == ScreenKind.SURVEY_LIST || a.target.role == ElementRole.START_ITEM -> ElementRole.START_ITEM
                else -> a.target.role
            }
            k.record(a.target, role, a.packageName, true)
        }
    }

    // ── Conclusão e próxima pesquisa (Seções 31, 32) ─────────────────
    private suspend fun onCompleted(snap: ScreenSnapshot, page: SurveyPage, policy: AgentPolicy) {
        if (inSurvey) {
            host.onEvent(AgentEvent.SurveyCompleted(surveyPackage, surveyQuestions, host.now() - surveyStartedAt))
            settleOutcomes(surveyPackage, DecisionOutcome.CONTINUED)
            inSurvey = false
            ctx.clear(); skipped.clear(); attempts.clear(); filledGeneric.clear()
            saveTask("completed", nextAction = "next_survey")
        }
        setState(AgentState.RESEARCH_COMPLETED, "Pesquisa concluída ✓")
        if (policy.mode == AgentMode.AUTOMATIC && policy.autoNextSurvey) {
            setState(AgentState.SEARCHING_NEXT_RESEARCH)
            if (tryStartNext(snap, page)) return
        }
        setState(AgentState.WAITING, "Pesquisa concluída. Aguardando nova pesquisa na tela…")
        waitChange(snap, 60_000)
    }

    private suspend fun onNoSurvey(snap: ScreenSnapshot, page: SurveyPage, policy: AgentPolicy, kind: ScreenKind) {
        // 1) Fluxo aprendido que combina com esta tela (Seções 6, 7)
        if (policy.mode != AgentMode.MANUAL && tryLearnedFlow(snap, kind, policy)) return

        // 2) Próxima pesquisa: botões conhecidos, itens aprendidos ou cartões de lista com recompensa
        if (policy.mode == AgentMode.AUTOMATIC && policy.autoNextSurvey && !inSurvey) {
            setState(AgentState.SEARCHING_NEXT_RESEARCH)
            if (tryStartNext(snap, page)) return
        }

        // 3) Tela não reconhecida → PERGUNTA ao usuário o que fazer (não fica parado em silêncio)
        val now = host.now()
        if (unknownSince == 0L) unknownSince = now
        val limit = when {
            inSurvey -> policy.observeUnknownAfterMs * 2
            !promptedSinceStart -> minOf(policy.unknownPromptAfterMs, policy.observeUnknownAfterMs)
            else -> policy.observeUnknownAfterMs
        }
        if (now - unknownSince >= limit && observationRequestedFor != snap.contentSignature) {
            observationRequestedFor = snap.contentSignature
            promptedSinceStart = true
            val qs = page.questions.size
            val reason = when {
                kind == ScreenKind.SURVEY_LIST -> "Vi uma lista de pesquisas/tarefas, mas ainda não sei qual abrir."
                qs > 0 -> "Encontrei $qs pergunta(s), mas não tenho certeza de que esta tela é uma pesquisa."
                else -> "Não reconheci esta tela como pesquisa."
            }
            host.onEvent(AgentEvent.ObservationRequested(snap.packageName, reason))
            setState(AgentState.OBSERVING, reason)
            val r = intervene(InterventionReason.UNKNOWN_SCREEN, "$reason O que devo fazer?", pkg = snap.packageName)
            when (r) {
                InterventionResult.Resume -> { unknownSince = 0; observationRequestedFor = 0 }   // tentar de novo já
                InterventionResult.Skip -> { unknownSince = 0; waitChange(snap, 60_000) }         // você vai mostrar / automação
                else -> Unit
            }
            return
        }
        setState(AgentState.WAITING, if (inSurvey) "Carregando próxima página…" else "Nenhuma pesquisa reconhecida nesta tela. Aguardando…")
        waitChange(snap, if (inSurvey) 6_000 else 5_000)
    }

    /** Executa UM passo de um fluxo aprendido que combine com a tela (verifica o resultado). */
    private suspend fun tryLearnedFlow(snap: ScreenSnapshot, kind: ScreenKind, policy: AgentPolicy): Boolean {
        val flows = host.flows().filter { it.confidence >= policy.flowMinConfidence }
        if (flows.isEmpty()) return false
        val info = ScreenInfo(snap.packageName, kind, UiSemantics.fingerprint(snap), snap.signature)
        // 1) pela "cara" da tela; 2) adivinhando: procura o elemento de cada passo na tela
        var pick = FlowLearner.match(flows, info)?.let { (f, i) ->
            val mm = ElementMatcher.find(snap, f.steps[i].target, f.steps[i].textVariable, kind)
            if (mm != null && mm.score >= 0.55) Triple(f, i, mm) else null
        }
        if (pick == null) {
            val g = FlowLearner.bestStart(flows, snap, kind, minScore = 0.7) ?: return false
            val mm = ElementMatcher.find(snap, g.flow.steps[g.step].target, g.flow.steps[g.step].textVariable, kind) ?: return false
            pick = Triple(g.flow, g.step, mm)
        }
        val (flow, idx, m) = pick
        val stepDef = flow.steps[idx]
        if (stepDef.action == ActionType.TEXT && (stepDef.inputVariable || stepDef.text.isNullOrBlank())) return false
        if (loops.record(snap.signature, "flow:${flow.id}:$idx")) {
            host.onEvent(AgentEvent.LoopDetected(snap.packageName, "Fluxo \"${flow.name}\" repetiu o passo ${idx + 1}"))
            host.onFlowResult(flow.id, false)
            loops.reset()
            return false
        }
        setState(AgentState.RUNNING_FLOW, "\"${flow.name}\" — passo ${idx + 1}/${flow.steps.size}")
        activeFlowId = flow.id
        val ok = if (stepDef.action == ActionType.TEXT) driver.setText(m.node, stepDef.text!!) else driver.click(m.node)
        val after = driver.awaitChange(snap, 8000)
        val changed = after != null && after.contentSignature != snap.contentSignature
        host.onEvent(AgentEvent.FlowStepRun(snap.packageName, flow.name, idx + 1, ok && (changed || stepDef.action == ActionType.TEXT)))
        if (!ok) host.onFlowResult(flow.id, false)
        lastProgressAt = host.now()
        return true
    }

    private suspend fun tryStartNext(snap: ScreenSnapshot, page: SurveyPage): Boolean {
        if (startClicks >= 2) return false
        val btn = page.startSurveyButtons.firstOrNull() ?: return false
        if (loops.record(snap.signature, "start:${btn.id}")) { loops.reset(); return false }
        startClicks++
        val label = UiSemantics.effectiveLabel(snap, btn, 60)
        host.onEvent(AgentEvent.Info(snap.packageName, "Iniciando próxima pesquisa: \"$label\""))
        driver.click(btn)
        val after = driver.awaitChange(snap, 10_000)
        host.knowledge()?.record(UiSemantics.describe(snap, btn, ScreenKind.SURVEY_LIST), ElementRole.START_ITEM, snap.packageName,
            after != null && after.contentSignature != snap.contentSignature)
        return true
    }

    // ── Modo manual (Seção 33) ───────────────────────────────────────
    private suspend fun suggestOnly(snap: ScreenSnapshot, page: SurveyPage) {
        if (snap.signature != lastSuggestionSig) {
            lastSuggestionSig = snap.signature
            val list = page.questions.filter { !it.answered }.map { q ->
                var d = answers.decide(q, ctx)
                if (d.needsUser) host.decisions()?.recall(q.text, q.optionTexts)?.let { d = it }
                if (d.needsUser) host.onEvent(AgentEvent.QuestionUnknown(snap.packageName, q.text, d.fieldKey, q.optionTexts))
                Suggestion(q.text, if (d.needsUser) "INFORMAÇÃO NECESSÁRIA" else d.display, d.level, d.reason)
            }
            update { it.copy(suggestions = list) }
        }
        setState(AgentState.WAITING, "Modo manual: veja as sugestões no painel e responda você mesmo.")
        waitChange(snap, 120_000)
    }

    // ── Intervenção humana (Seção 15) ────────────────────────────────
    private suspend fun intervene(
        reason: InterventionReason, message: String, question: SurveyQuestion? = null,
        field: String? = null, suggestion: AnswerDecision? = null, pkg: String = ""
    ): InterventionResult {
        val i = Intervention(++interventionSeq, reason, message, question, field, suggestion, pkg)
        update { it.copy(state = AgentState.USER_INTERVENTION_REQUIRED, message = message, intervention = i) }
        host.onEvent(AgentEvent.InterventionRaised(pkg, reason, message))
        val r = host.intervene(i)
        update { it.copy(intervention = null, message = "Continuando…") }
        if (r is InterventionResult.Stop) stopRequested = true
        lastProgressAt = host.now()
        return r
    }
}
