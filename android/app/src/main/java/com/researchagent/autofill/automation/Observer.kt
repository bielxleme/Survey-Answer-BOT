package com.researchagent.autofill.automation

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import com.researchagent.autofill.AppGraph
import com.researchagent.autofill.accessibility.SurveyAccessibilityService
import com.researchagent.autofill.core.ActionType
import com.researchagent.autofill.core.DecisionOrigin
import com.researchagent.autofill.core.DecisionRecord
import com.researchagent.autofill.core.ElementRole
import com.researchagent.autofill.core.FlowLearner
import com.researchagent.autofill.core.ObservedAction
import com.researchagent.autofill.core.ScreenKind
import com.researchagent.autofill.core.ScreenNode
import com.researchagent.autofill.core.ScreenSnapshot
import com.researchagent.autofill.core.SurveyAnalyzer
import com.researchagent.autofill.core.UiSemantics
import com.researchagent.autofill.data.LogEntry
import com.researchagent.autofill.data.LogType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Observa as ações do USUÁRIO (Seções 3, 4, 6, 7, 17) e transforma em conhecimento:
 * papel dos elementos (entre apps), respostas escolhidas e fluxos abstratos.
 * Registra texto, tipo, id, região relativa, hierarquia, pergunta associada e o resultado (antes/depois).
 */
object Observer {

    enum class Mode(val label: String) {
        OFF("Desligado"),
        TEACH("Ensinar automação (observando)"),
        RECORD("Gravando operação"),
        AUTO("Observando tela não reconhecida")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _mode = MutableStateFlow(Mode.OFF)
    val mode: StateFlow<Mode> = _mode.asStateFlow()
    private val _recordedCount = MutableStateFlow(0)
    val recordedCount: StateFlow<Int> = _recordedCount.asStateFlow()
    private val _lastLearned = MutableStateFlow("")
    val lastLearned: StateFlow<String> = _lastLearned.asStateFlow()

    private val recent = ArrayDeque<ObservedAction>()
    private val recording = ArrayList<ObservedAction>()
    /** Ações feitas fora de telas de pergunta até entrar numa pesquisa (como o usuário INICIA uma pesquisa). */
    private val entryPath = ArrayList<ObservedAction>()
    private var sawQuestionInSession = false
    private var recordingPackage = ""

    @Volatile private var lastSnapshot: ScreenSnapshot? = null
    @Volatile private var lastSnapshotAt = 0L

    /** Observar também durante intervenções (para verificar e aprender com "JÁ RESOLVI"). */
    val isCapturing: Boolean get() = _mode.value != Mode.OFF || InterventionBus.current.value != null

    fun start(m: Mode) {
        if (m == Mode.RECORD) { recording.clear(); _recordedCount.value = 0; recordingPackage = "" }
        entryPath.clear(); sawQuestionInSession = false
        _mode.value = m
        log(LogType.OBSERVE, "", "Observação iniciada: ${m.label}")
        refreshSnapshot(force = true)
    }

    /** Para a observação. Em modo gravação, salva o fluxo e retorna o nome. */
    fun stop(): String? {
        val m = _mode.value
        _mode.value = Mode.OFF
        if (m == Mode.RECORD) return saveRecording()
        return null
    }

    fun shutdown() { _mode.value = Mode.OFF; recording.clear(); entryPath.clear(); synchronized(recent) { recent.clear() } }

    fun actionsSince(time: Long): List<ObservedAction> = synchronized(recent) { recent.filter { it.time >= time } }

    // ── eventos do serviço de acessibilidade ─────────────────────────
    fun onWindowChanged(service: SurveyAccessibilityService) {
        if (!isCapturing) return
        refreshSnapshot(service = service)
    }

    private fun refreshSnapshot(force: Boolean = false, service: SurveyAccessibilityService? = SurveyAccessibilityService.instance) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSnapshotAt < 600) return
        lastSnapshotAt = now
        val svc = service ?: return
        scope.launch {
            runCatching { svc.driver.snapshotDetached() }.getOrNull()?.let { s ->
                if (s.packageName != AppGraph.app.packageName) lastSnapshot = s
            }
        }
    }

    fun onUserEvent(service: SurveyAccessibilityService, event: AccessibilityEvent) {
        if (!isCapturing) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_VIEW_CLICKED && type != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isEmpty() || pkg == service.packageName) return
        // ignora ações do próprio agente
        if (System.currentTimeMillis() - service.driver.lastAgentActionAt < 900) return
        if (!AppGraph.settings.isPackageAllowed(pkg, service.packageName)) return
        val src = event.source ?: return
        if (src.isPassword) return // nunca observa senhas
        val r = Rect().also { src.getBoundsInScreen(it) }
        val srcText = src.text?.toString().orEmpty()
        val srcDesc = src.contentDescription?.toString().orEmpty()
        val srcClass = src.className?.toString().orEmpty()
        val typed = if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) event.text.joinToString("") { it.toString() } else null
        val before = lastSnapshot
        val time = System.currentTimeMillis()
        scope.launch { handle(service, pkg, before, r, srcText, srcDesc, srcClass, typed, time) }
    }

    private suspend fun handle(
        service: SurveyAccessibilityService, pkg: String, cached: ScreenSnapshot?, r: Rect,
        srcText: String, srcDesc: String, srcClass: String, typed: String?, time: Long
    ) {
        val before = cached?.takeIf { it.packageName == pkg } ?: service.driver.snapshotDetached() ?: return
        val node = locate(before, r, srcText, srcDesc, srcClass) ?: return
        val page = SurveyAnalyzer.analyze(before, AppGraph.learning.knowledge)
        val kind = UiSemantics.classifyScreen(before, page)
        val question = page.questions.firstOrNull { q -> q.options.any { it.nodeId == node.id } || q.inputNodeId == node.id }
        val desc = UiSemantics.describe(before, node, kind, question?.text.orEmpty())
        val action = ObservedAction(
            packageName = pkg, time = time, before = UiSemantics.screenInfo(before, page), target = desc,
            action = if (typed != null) ActionType.TEXT else ActionType.CLICK,
            text = typed?.takeIf { !(question?.let { q -> AppGraph.profile.current.fieldDef(q.key)?.sensitive } ?: false) },
            question = question?.text, options = question?.optionTexts.orEmpty()
        )
        // digitação: atualiza a última ação de texto no mesmo campo em vez de criar várias
        synchronized(recent) {
            val last = recent.lastOrNull()
            if (typed != null && last != null && last.action == ActionType.TEXT && last.target.viewId == desc.viewId &&
                last.target.row == desc.row && time - last.time < 8000) {
                recent.removeLast()
            }
            recent.addLast(action)
            while (recent.size > 80) recent.removeFirst()
        }
        // resultado da ação: antes/depois (Seção 4)
        delay(1300)
        val after = service.driver.snapshotDetached()
        if (after != null) {
            val afterPage = SurveyAnalyzer.analyze(after, AppGraph.learning.knowledge)
            action.after = UiSemantics.screenInfo(after, afterPage)
            action.changedScreen = after.contentSignature != before.contentSignature
            action.selectedAfter = question?.let { q -> afterPage.questions.firstOrNull { it.key == q.key }?.options?.any { it.checked } } ?: false
            lastSnapshot = after
        }
        learn(action)
    }

    /** Encontra o nó do evento na leitura: por limites (tolerância) + texto/classe. Coordenadas só como pista. */
    private fun locate(s: ScreenSnapshot, r: Rect, text: String, desc: String, cls: String): ScreenNode? {
        val label = text.ifBlank { desc }
        val cands = s.visibleNodes.filter { n ->
            kotlin.math.abs(n.bounds.left - r.left) <= 8 && kotlin.math.abs(n.bounds.top - r.top) <= 8 &&
                kotlin.math.abs(n.bounds.right - r.right) <= 8 && kotlin.math.abs(n.bounds.bottom - r.bottom) <= 8
        }
        return cands.firstOrNull { it.className == cls && (label.isBlank() || it.label == label) }
            ?: cands.firstOrNull()
            ?: s.visibleNodes.firstOrNull { label.isNotBlank() && it.label == label && it.className == cls }
    }

    // ── aprendizado ──────────────────────────────────────────────────
    private fun learn(a: ObservedAction) {
        val learning = AppGraph.learning
        val m = _mode.value
        learning.onObservedAction()
        val beforeKind = a.before.kind
        val afterKind = a.after?.kind
        val role = when {
            a.target.role == ElementRole.ANSWER_OPTION || a.target.role == ElementRole.TEXT_INPUT -> a.target.role
            beforeKind == ScreenKind.SURVEY_QUESTION && a.changedScreen -> ElementRole.NEXT
            (beforeKind == ScreenKind.SURVEY_LIST || beforeKind == ScreenKind.OTHER) && afterKind == ScreenKind.SURVEY_QUESTION -> ElementRole.START_ITEM
            beforeKind == ScreenKind.SURVEY_LIST && a.changedScreen -> ElementRole.START_ITEM
            else -> a.target.role
        }
        if (role != ElementRole.ANSWER_OPTION && role != ElementRole.TEXT_INPUT && role != ElementRole.OTHER) {
            learning.recordRole(a.target, role, a.packageName, a.changedScreen || role == ElementRole.FILTER || role == ElementRole.TAB)
        }
        // respostas escolhidas pelo usuário (fora de intervenção — lá o motor já registra)
        if (InterventionBus.current.value == null && a.question != null && m != Mode.OFF) {
            val answer = if (a.action == ActionType.TEXT) a.text else a.options.firstOrNull { com.researchagent.autofill.core.Text.looselyEquals(it, a.target.text) }
            if (!answer.isNullOrBlank()) {
                learning.addDecision(DecisionRecord(com.researchagent.autofill.core.Text.questionKey(a.question), a.question, a.options,
                    listOf(answer), DecisionOrigin.USER, 1.0, a.packageName, a.time))
            }
        }
        val detail = "${role.label}: \"${a.target.text.take(40)}\" (${a.target.cls}, região ${regionName(a.target.row, a.target.col)})" +
            if (a.changedScreen) " → tela mudou (${afterKind?.label ?: "?"})" else ""
        _lastLearned.value = detail
        log(LogType.OBSERVE, a.packageName, "Ação observada", detail)

        when (m) {
            Mode.RECORD -> {
                if (recordingPackage.isEmpty()) recordingPackage = a.packageName
                synchronized(recording) {
                    val last = recording.lastOrNull()
                    if (a.action == ActionType.TEXT && last != null && last.action == ActionType.TEXT && last.target.viewId == a.target.viewId) recording.removeAt(recording.size - 1)
                    recording += a
                }
                _recordedCount.value = recording.size
            }
            Mode.TEACH, Mode.AUTO -> learnSurveyEntry(a)
            Mode.OFF -> Unit
        }
    }

    /**
     * Como o usuário ENTRA numa pesquisa (lista → filtro → item → iniciar). Após várias demonstrações
     * o padrão é comparado: o que se repete é estrutural, o que muda é variável (Seção 17).
     */
    private fun learnSurveyEntry(a: ObservedAction) {
        val learning = AppGraph.learning
        if (a.before.kind != ScreenKind.SURVEY_QUESTION && a.after?.kind != ScreenKind.COMPLETION) {
            if (sawQuestionInSession && a.before.kind != ScreenKind.SURVEY_QUESTION) {
                // voltou para fora da pesquisa: nova sessão de entrada
                sawQuestionInSession = false
                entryPath.clear()
            }
            if (a.action == ActionType.CLICK && a.target.role != ElementRole.BACK) entryPath += a
            while (entryPath.size > 8) entryPath.removeAt(0)
        }
        if (a.after?.kind == ScreenKind.SURVEY_QUESTION && !sawQuestionInSession) {
            sawQuestionInSession = true
            val steps = FlowLearner.toSteps(entryPath.filter { it.changedScreen || it.target.role == ElementRole.FILTER || it.target.role == ElementRole.TAB })
            if (steps.isNotEmpty()) {
                val label = appLabel(a.packageName)
                val flow = learning.addDemonstration(a.packageName, steps, "Iniciar pesquisa em $label", "survey-entry", System.currentTimeMillis())
                if (flow != null) {
                    val need = flow.demonstrationsNeeded
                    val msg = if (need > 0) "Aprendi como abrir uma pesquisa em $label (${flow.demonstrations} demonstração(ões)). " +
                        "Complete mais $need pesquisa(s) desse tipo para eu comparar as etapas e confirmar o padrão."
                        else "Padrão confirmado: sei abrir pesquisas em $label (confiança ${"%.0f".format(flow.confidence * 100)}%)."
                    _lastLearned.value = msg
                    log(LogType.LEARN, a.packageName, "Novo padrão aprendido", msg)
                }
            }
            entryPath.clear()
        }
        if (a.after?.kind == ScreenKind.COMPLETION) {
            learning.onObservedSurvey()
            log(LogType.LEARN, a.packageName, "Pesquisa observada até o fim", "Pesquisas observadas: ${learning.stats.value.observedSurveys}")
        }
    }

    private fun saveRecording(): String? {
        val actions = synchronized(recording) { recording.toList() }
        recording.clear(); _recordedCount.value = 0
        if (actions.isEmpty()) return null
        val pkg = recordingPackage.ifEmpty { actions.first().packageName }
        val name = "Operação em ${appLabel(pkg)}"
        val flow = AppGraph.learning.addDemonstration(pkg, FlowLearner.toSteps(actions), name, "operation", System.currentTimeMillis())
            ?: return null
        val msg = "Automação \"${flow.name}\" salva: ${flow.steps.size} passos, ${flow.demonstrations} demonstração(ões)." +
            if (flow.demonstrationsNeeded > 0) " Grave mais ${flow.demonstrationsNeeded} vez(es) para aumentar a confiança." else ""
        log(LogType.LEARN, pkg, "Automação criada", msg)
        _lastLearned.value = msg
        return flow.name
    }

    fun appLabel(pkg: String): String = runCatching {
        val pm = AppGraph.app.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun regionName(row: Int, col: Int): String =
        listOf("superior", "meio", "inferior")[row.coerceIn(0, 2)] + "-" + listOf("esquerda", "centro", "direita")[col.coerceIn(0, 2)]

    private fun log(type: LogType, pkg: String, msg: String, detail: String = "") {
        AppGraph.logs.add(LogEntry(System.currentTimeMillis(), type, pkg, msg, detail))
    }
}
