package com.researchagent.autofill.core

import kotlinx.coroutines.CancellationException
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
    val intervention: Intervention? = null
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
    /** Usuário resolveu na tela (ex.: CAPTCHA, login ou preencheu manualmente) — continuar. */
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
    val maxScrollsPerPage: Int = 12
)

sealed class AgentEvent {
    data class SurveyStarted(val packageName: String) : AgentEvent()
    data class QuestionAnswered(val packageName: String, val question: String, val decision: AnswerDecision, val byUser: Boolean) : AgentEvent()
    data class QuestionUnknown(val packageName: String, val question: String, val fieldKey: String?, val options: List<String>) : AgentEvent()
    data class InterventionRaised(val packageName: String, val reason: InterventionReason, val message: String) : AgentEvent()
    data class SurveyCompleted(val packageName: String, val questions: Int, val durationMs: Long) : AgentEvent()
    data class Error(val packageName: String, val message: String) : AgentEvent()
    data class Info(val packageName: String, val message: String) : AgentEvent()
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
}

/**
 * Agente orientado a objetivos (Seções 2, 13, 14, 30, 31, 32, 40, 41).
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
    private var samePageNavAttempts = 0
    private var scrollsOnPage = 0
    private var lastProgressAt = 0L
    private var startClicks = 0
    private var lastSuggestionSig = 0

    fun pause() { paused.value = true; update { it.copy(paused = true, state = AgentState.PAUSED, message = "Pausado pelo usuário") } }
    fun resume() { paused.value = false; update { it.copy(paused = false, message = "Retomando…") } }
    fun requestStop() { stopRequested = true; paused.value = false }
    val isPaused: Boolean get() = paused.value

    private fun update(f: (AgentStatus) -> AgentStatus) { _status.value = f(_status.value) }

    private fun setState(state: AgentState, message: String = state.label) =
        update { it.copy(state = state, message = message) }

    private suspend fun awaitNotPaused() {
        if (paused.value) {
            setState(AgentState.PAUSED, "Pausado")
            paused.first { !it }
        }
    }

    private fun alive(): Boolean = !stopRequested

    suspend fun run() {
        stopRequested = false
        lastProgressAt = host.now()
        update { AgentStatus(running = true, mode = host.policy().mode, surveyIndex = it.surveyIndex) }
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
        }
    }

    /** Uma iteração da máquina de estados. Público para testes. */
    suspend fun step() {
        val policy = host.policy()
        update { it.copy(mode = policy.mode) }
        setState(AgentState.SCANNING)
        var snap = driver.snapshot()
        if (snap == null) { setState(AgentState.WAITING, "Aguardando uma tela legível…"); delay(1500); return }
        if (snap.packageName == host.ownPackage) { setState(AgentState.WAITING, "Abra o app da pesquisa"); delay(1200); return }
        update { it.copy(currentApp = snap!!.packageName) }

        if (!host.isPackageAllowed(snap.packageName)) {
            val r = intervene(InterventionReason.BLOCKED_APP,
                "O app ${snap.packageName} está bloqueado para automação (bancos, mensagens, sistema ou fora da lista permitida).",
                pkg = snap.packageName)
            if (r is InterventionResult.Resume || r is InterventionResult.Skip) driver.awaitChange(snap, 20_000)
            return
        }

        // Árvore pobre (canvas, imagem): tenta OCR para compreender (Seção 11)
        if (policy.useOcr && snap.textNodeCount < 3) {
            driver.ocrSnapshot()?.let { ocr -> if (ocr.textNodeCount > snap!!.textNodeCount) snap = ocr }
        }
        val current = snap!!
        val page = SurveyAnalyzer.analyze(current)

        // Riscos: CAPTCHA, login, pagamento (Seções 16–18)
        page.guard?.let { g ->
            val r = intervene(g.reason, "${g.reason.title}. Resolva na tela e toque em CONTINUAR AUTOMAÇÃO.", pkg = current.packageName)
            if (r is InterventionResult.Skip) driver.awaitChange(current, 30_000)
            return
        }

        if (page.completed) { onCompleted(current, page, policy); return }

        if (!page.isSurvey) { onNoSurvey(current, page, policy); return }

        if (!inSurvey) startSurvey(current.packageName)
        startClicks = 0
        setState(AgentState.RESEARCH_DETECTED)
        val pending = page.questions.filter { !it.answered && it.key !in skipped }
        update {
            it.copy(pageProgress = page.progress, questionsOnPage = page.questions.size,
                questionIndex = page.questions.count { q -> q.answered } + 1)
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

    private fun startSurvey(pkg: String) {
        inSurvey = true
        surveyStartedAt = host.now()
        surveyQuestions = 0
        surveyPackage = pkg
        ctx.clear(); skipped.clear(); attempts.clear()
        samePageNavAttempts = 0; scrollsOnPage = 0
        lastProgressAt = host.now()
        update { it.copy(surveyIndex = it.surveyIndex + 1, questionIndex = 0) }
        host.onEvent(AgentEvent.SurveyStarted(pkg))
    }

    // ── Pergunta ──────────────────────────────────────────────────────
    private suspend fun handleQuestion(snap: ScreenSnapshot, q: SurveyQuestion, policy: AgentPolicy) {
        val n = (attempts[q.key] ?: 0) + 1
        attempts[q.key] = n
        if (n > 3) {
            val r = intervene(InterventionReason.VALIDATION_ERROR,
                "O campo \"${q.text.take(80)}\" não aceitou a resposta. Corrija na tela e continue.", q, pkg = snap.packageName)
            if (r !is InterventionResult.Stop) { skipped += q.key; attempts.remove(q.key) }
            return
        }
        setState(AgentState.READING_QUESTION, "Lendo: ${q.text.take(60)}")
        setState(AgentState.UNDERSTANDING_QUESTION)
        setState(AgentState.SEARCHING_PROFILE)
        setState(AgentState.GENERATING_RESPONSE)
        var decision = answers.decide(q, ctx)
        setState(AgentState.VALIDATING_RESPONSE)

        val byUser = false
        if (decision.needsUser || decision.confidence < policy.threshold) {
            if (decision.needsUser) {
                host.onEvent(AgentEvent.QuestionUnknown(snap.packageName, q.text, decision.fieldKey, q.optionTexts))
            }
            val msg = if (decision.needsUser) "INFORMAÇÃO NECESSÁRIA: ${decision.reason}"
                      else "Resposta com confiança ${decision.level.label.lowercase()} — confirme: ${decision.display}"
            when (val r = intervene(InterventionReason.MISSING_INFO, msg, q, decision.fieldKey, decision, snap.packageName)) {
                is InterventionResult.Answered -> {
                    decision = AnswerDecision(AnswerAction.ANSWER, r.answers, 1.0, "user", "Resposta informada pelo usuário.", decision.fieldKey, "user")
                    // Guarda na memória da pesquisa e preenche no próximo passo, com a tela da pesquisa
                    // novamente em primeiro plano e nós de acessibilidade atualizados.
                    ctx.remember(q, decision)
                    surveyQuestions++
                    host.onEvent(AgentEvent.QuestionAnswered(snap.packageName, q.text, decision, byUser = true))
                    lastProgressAt = host.now()
                    return
                }
                InterventionResult.Resume -> { skipped += q.key; lastProgressAt = host.now(); return } // usuário preencheu na tela
                InterventionResult.Skip -> { skipped += q.key; return }
                InterventionResult.Stop -> return
            }
        }

        setState(AgentState.FILLING_FIELD, "Preenchendo: ${decision.display.take(40)}")
        val ok = fill(snap, q, decision)
        setState(AgentState.VERIFYING_FIELD)
        if (ok) {
            val fromSession = decision.source == "session"
            ctx.remember(q, decision)
            lastProgressAt = host.now()
            if (!fromSession) {
                surveyQuestions++
                host.onEvent(AgentEvent.QuestionAnswered(snap.packageName, q.text, decision, byUser))
            }
            driver.awaitChange(snap, 1500) // deixa a UI refletir a marcação
        } else {
            host.onEvent(AgentEvent.Error(snap.packageName, "Falha ao preencher \"${q.text.take(60)}\""))
            delay(400)
        }
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
            if (nav == null) { handleStuck(snap, "Não encontrei o botão \"Próximo\"/\"Enviar\"."); return }
        }
        val target = nav!!

        if (policy.mode == AgentMode.ASSISTED) {
            val label = if (isSubmit) "ENVIAR a pesquisa" else "avançar para a próxima página"
            when (intervene(InterventionReason.CONFIRM_NEXT, "Respostas preenchidas. Posso $label (\"${target.label}\")?", pkg = snap.packageName)) {
                is InterventionResult.Answered, InterventionResult.Resume -> Unit
                InterventionResult.Skip -> { driver.awaitChange(snap, 60_000); return }
                InterventionResult.Stop -> return
            }
        }

        val errorsBefore = SurveyAnalyzer.errorPhrases(snap)
        setState(AgentState.NEXT_PAGE, "Tocando em \"${target.label}\"")
        driver.click(target)
        setState(AgentState.WAITING, "Aguardando carregamento…")
        val after = driver.awaitChange(snap, policy.pageTimeoutMs)
        if (after != null && after.contentSignature != snap.contentSignature) {
            samePageNavAttempts = 0; scrollsOnPage = 0
            lastProgressAt = host.now()
            attempts.clear()
            return
        }
        samePageNavAttempts++
        val check = after ?: driver.snapshot()
        val newErrors = check?.let { SurveyAnalyzer.errorPhrases(it) - errorsBefore }.orEmpty()
        if (newErrors.isNotEmpty()) {
            intervene(InterventionReason.VALIDATION_ERROR,
                "A página mostrou: \"${newErrors.first()}\". Verifique os campos destacados e continue.", pkg = snap.packageName)
            samePageNavAttempts = 0
        } else if (samePageNavAttempts >= 3) {
            handleStuck(snap, "Toquei em \"${target.label}\" ${samePageNavAttempts}x e a página não mudou.")
        }
    }

    private suspend fun handleStuck(snap: ScreenSnapshot, why: String) {
        samePageNavAttempts = 0
        val r = intervene(InterventionReason.STUCK, "Não consegui avançar com segurança. $why", pkg = snap.packageName)
        if (r is InterventionResult.Skip) driver.awaitChange(snap, 30_000)
        scrollsOnPage = 0
        lastProgressAt = host.now()
    }

    // ── Conclusão e próxima pesquisa (Seções 31, 32) ─────────────────
    private suspend fun onCompleted(snap: ScreenSnapshot, page: SurveyPage, policy: AgentPolicy) {
        if (inSurvey) {
            host.onEvent(AgentEvent.SurveyCompleted(surveyPackage, surveyQuestions, host.now() - surveyStartedAt))
            inSurvey = false
            ctx.clear(); skipped.clear(); attempts.clear()
        }
        setState(AgentState.RESEARCH_COMPLETED, "Pesquisa concluída ✓")
        if (policy.mode == AgentMode.AUTOMATIC && policy.autoNextSurvey) {
            setState(AgentState.SEARCHING_NEXT_RESEARCH)
            if (tryStartNext(snap, page)) return
        }
        setState(AgentState.WAITING, "Pesquisa concluída. Aguardando nova pesquisa na tela…")
        driver.awaitChange(snap, 60_000)
    }

    private suspend fun onNoSurvey(snap: ScreenSnapshot, page: SurveyPage, policy: AgentPolicy) {
        if (policy.mode == AgentMode.AUTOMATIC && policy.autoNextSurvey && !inSurvey) {
            setState(AgentState.SEARCHING_NEXT_RESEARCH)
            if (tryStartNext(snap, page)) return
        }
        setState(AgentState.WAITING, if (inSurvey) "Carregando próxima página…" else "Nenhuma pesquisa nesta tela. Aguardando…")
        driver.awaitChange(snap, if (inSurvey) 8_000 else 20_000)
    }

    private suspend fun tryStartNext(snap: ScreenSnapshot, page: SurveyPage): Boolean {
        if (startClicks >= 2) return false
        val btn = page.startSurveyButtons.firstOrNull() ?: return false
        startClicks++
        host.onEvent(AgentEvent.Info(snap.packageName, "Iniciando próxima pesquisa: \"${btn.label}\""))
        driver.click(btn)
        driver.awaitChange(snap, 10_000)
        return true
    }

    // ── Modo manual (Seção 33) ───────────────────────────────────────
    private suspend fun suggestOnly(snap: ScreenSnapshot, page: SurveyPage) {
        if (snap.signature != lastSuggestionSig) {
            lastSuggestionSig = snap.signature
            val list = page.questions.filter { !it.answered }.map { q ->
                val d = answers.decide(q, ctx)
                if (d.needsUser) host.onEvent(AgentEvent.QuestionUnknown(snap.packageName, q.text, d.fieldKey, q.optionTexts))
                Suggestion(q.text, if (d.needsUser) "INFORMAÇÃO NECESSÁRIA" else d.display, d.level, d.reason)
            }
            update { it.copy(suggestions = list) }
        }
        setState(AgentState.WAITING, "Modo manual: veja as sugestões no painel e responda você mesmo.")
        driver.awaitChange(snap, 120_000)
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
