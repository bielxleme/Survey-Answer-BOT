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
import com.researchagent.autofill.core.ScreenTitle
import com.researchagent.autofill.core.ElementDescriptor
import com.researchagent.autofill.core.Flow
import com.researchagent.autofill.core.Bounds
import com.researchagent.autofill.core.Text
import com.researchagent.autofill.core.SurveyAnalyzer
import com.researchagent.autofill.core.UiSemantics
import com.researchagent.autofill.data.LogEntry
import com.researchagent.autofill.data.LogType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /** Mensagens curtas para a bolha (cada toque gravado, avisos de toque não capturado). */
    private val _feedback = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val feedback: SharedFlow<String> = _feedback.asSharedFlow()

    private var recordingTitle = ""
    @Volatile private var lastUserActionAt = 0L
    @Volatile private var snapshotInFlight = false
    private var lastWarnAt = 0L

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
        // nunca dois modos ao mesmo tempo: se estava gravando, salva antes de trocar
        if (_mode.value == Mode.RECORD && m != Mode.RECORD) saveRecording()
        if (m == Mode.RECORD) { recording.clear(); _recordedCount.value = 0; recordingPackage = ""; recordingTitle = "" }
        entryPath.clear(); sawQuestionInSession = false
        _mode.value = m
        log(LogType.OBSERVE, "", "Observação iniciada: ${m.label}")
        if (m == Mode.RECORD) _feedback.tryEmit("⏺ Gravando. Cada toque capturado aparece aqui.")
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
        if (!force && (now - lastSnapshotAt < 1200 || snapshotInFlight)) return
        lastSnapshotAt = now
        val svc = service ?: return
        snapshotInFlight = true
        scope.launch {
            try {
                runCatching { svc.driver.snapshotDetached() }.getOrNull()?.let { s ->
                    if (s.packageName == AppGraph.app.packageName) return@let
                    val prev = lastSnapshot
                    lastSnapshot = s
                    warnIfTapMissed(svc, prev, s)
                }
            } finally { snapshotInFlight = false }
        }
    }

    /** Gravando: a tela mudou bastante, mas nenhum toque foi capturado → avisa (conteúdo web/jogo não informa toques). */
    private fun warnIfTapMissed(svc: SurveyAccessibilityService, prev: ScreenSnapshot?, now: ScreenSnapshot) {
        if (_mode.value != Mode.RECORD || prev == null || prev.packageName != now.packageName) return
        val t = System.currentTimeMillis()
        if (t - lastUserActionAt < 3_000 || t - svc.driver.lastAgentActionAt < 3_000 || t - lastWarnAt < 8_000) return
        if (UiSemantics.similarity(UiSemantics.fingerprint(prev), UiSemantics.fingerprint(now)) >= 0.5) return
        lastWarnAt = t
        _feedback.tryEmit("⚠ A tela mudou, mas não captei seu toque. Se foi um toque seu, volte e toque no TEXTO do botão.")
    }

    fun onUserEvent(service: SurveyAccessibilityService, event: AccessibilityEvent) {
        if (!isCapturing) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_VIEW_CLICKED && type != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isEmpty() || pkg == service.packageName) return
        // ignora ações do próprio agente
        if (System.currentTimeMillis() - service.driver.lastAgentActionAt < 900) return
        if (!AppGraph.settings.isPackageAllowed(pkg, service.packageName)) {
            if (_mode.value == Mode.RECORD) _feedback.tryEmit("⚠ Este app está bloqueado para automação (Ajustes → apps permitidos).")
            return
        }
        lastUserActionAt = System.currentTimeMillis()
        val src = event.source
        if (src?.isPassword == true || event.isPassword) return // nunca observa senhas
        val r = Rect().also { src?.getBoundsInScreen(it) }
        val evText = event.text.joinToString(" ") { it.toString() }.trim()
        val srcText = src?.text?.toString().orEmpty().ifBlank { if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) evText else "" }
        val srcDesc = src?.contentDescription?.toString().orEmpty().ifBlank { event.contentDescription?.toString().orEmpty() }
        val srcClass = src?.className?.toString().orEmpty().ifBlank { event.className?.toString().orEmpty() }
        val srcId = src?.viewIdResourceName.orEmpty()
        val typed = if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) evText else null
        val before = lastSnapshot
        val time = System.currentTimeMillis()
        scope.launch { handle(service, pkg, before, r, srcText, srcDesc, srcClass, srcId, typed, time) }
    }

    private suspend fun handle(
        service: SurveyAccessibilityService, pkg: String, cached: ScreenSnapshot?, r: Rect,
        srcText: String, srcDesc: String, srcClass: String, srcId: String, typed: String?, time: Long
    ) {
        val before = cached?.takeIf { it.packageName == pkg } ?: service.driver.snapshotDetached() ?: run {
            _feedback.tryEmit("⚠ Não consegui ler a tela deste toque."); return
        }
        val node = locate(before, r, srcText, srcDesc, srcClass)
        val page = SurveyAnalyzer.analyze(before, AppGraph.learning.knowledge)
        val kind = UiSemantics.classifyScreen(before, page)
        val question = node?.let { n -> page.questions.firstOrNull { q -> q.options.any { it.nodeId == n.id } || q.inputNodeId == n.id } }
        // se o nó não foi localizado na leitura, monta a descrição com o que o evento informou (texto, id, classe, região)
        val desc = if (node != null) UiSemantics.describe(before, node, kind, question?.text.orEmpty())
            else fallbackDescriptor(before, r, srcText.ifBlank { srcDesc }, srcId, srcClass, typed != null)
        if (desc.text.isBlank() && desc.viewId.isBlank()) {
            if (_mode.value == Mode.RECORD) _feedback.tryEmit("⚠ Toque sem texto nem identificação — este passo pode não ser reproduzível. Prefira tocar no texto do botão.")
        }
        if (_mode.value == Mode.RECORD && recordingTitle.isBlank()) recordingTitle = ScreenTitle.guess(before)
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
            ?: s.visibleNodes.firstOrNull { label.isNotBlank() && it.label == label }
            // menor elemento clicável que contém o ponto tocado
            ?: if (r.isEmpty) null else s.visibleNodes
                .filter { n -> (n.isClickable || n.isEditable || n.isCheckable) && !n.bounds.isEmpty &&
                    r.centerX() in n.bounds.left..n.bounds.right && r.centerY() in n.bounds.top..n.bounds.bottom }
                .minByOrNull { (it.bounds.right - it.bounds.left).toLong() * (it.bounds.bottom - it.bounds.top) }
    }

    private fun fallbackDescriptor(s: ScreenSnapshot, r: Rect, label: String, viewId: String, cls: String, editable: Boolean): ElementDescriptor {
        val (row, col) = if (r.isEmpty) 1 to 1 else UiSemantics.region(UiSemantics.screenBounds(s), Bounds(r.left, r.top, r.right, r.bottom))
        return ElementDescriptor(
            role = if (editable) ElementRole.TEXT_INPUT else ElementRole.OTHER,
            text = Text.normalize(label).take(80), viewId = UiSemantics.viewIdTail(viewId), cls = UiSemantics.shortClass(cls),
            row = row, col = col, listIndex = -1, listSize = 0, checkable = false, editable = editable
        )
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
                val what = if (a.action == ActionType.TEXT) "digitou em \"${a.target.text.ifBlank { "campo" }.take(24)}\""
                    else "tocou em \"${a.target.text.ifBlank { a.target.viewId.ifBlank { a.target.cls } }.take(28)}\""
                _feedback.tryEmit("⏺ Passo ${recording.size} gravado: $what")
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
                val flow = learning.addDemonstration(a.packageName, steps, "Abrir pesquisa · $label", "survey-entry", System.currentTimeMillis())
                if (flow != null) {
                    val need = flow.demonstrationsNeeded
                    val msg = if (need > 0) "Aprendi como abrir uma pesquisa em $label (${flow.demonstrations} demonstração(ões)). " +
                        "Complete mais $need pesquisa(s) desse tipo para eu comparar as etapas e confirmar o padrão."
                        else "Padrão confirmado: sei abrir pesquisas em $label (confiança ${"%.0f".format(flow.confidence * 100)}%)."
                    _lastLearned.value = msg
                    _feedback.tryEmit("🧠 $msg")
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
        val app = appLabel(pkg)
        val title = recordingTitle.takeIf { it.isNotBlank() && !Text.looselyEquals(it, app) }
        val name = if (title != null) "$app · ${title.take(40)}" else "Operação em $app"
        val flow = AppGraph.learning.addDemonstration(pkg, FlowLearner.toSteps(actions), name, "operation", System.currentTimeMillis())
            ?: return null
        val msg = "Automação \"${flow.name}\" salva: ${flow.steps.size} passos, ${flow.demonstrations} demonstração(ões)." +
            if (flow.demonstrationsNeeded > 0) " Grave mais ${flow.demonstrationsNeeded} vez(es) para aumentar a confiança." else ""
        log(LogType.LEARN, pkg, "Automação criada", msg)
        _lastLearned.value = msg
        _feedback.tryEmit("✓ $msg")
        return flow.name
    }

    private val labelCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun appLabel(pkg: String): String = if (pkg.isBlank()) "" else labelCache.getOrPut(pkg) {
        runCatching {
            val pm = AppGraph.app.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull()?.takeIf { it.isNotBlank() && it != pkg } ?: ScreenTitle.prettyPackage(pkg)
    }

    /** Nome exibido do fluxo: troca "com.app.pacote" pelo nome do app (automações antigas). */
    fun displayName(f: Flow): String =
        if (f.packageName.isNotBlank() && f.name.contains(f.packageName)) f.name.replace(f.packageName, appLabel(f.packageName)) else f.name

    private fun regionName(row: Int, col: Int): String =
        listOf("superior", "meio", "inferior")[row.coerceIn(0, 2)] + "-" + listOf("esquerda", "centro", "direita")[col.coerceIn(0, 2)]

    private fun log(type: LogType, pkg: String, msg: String, detail: String = "") {
        AppGraph.logs.add(LogEntry(System.currentTimeMillis(), type, pkg, msg, detail))
    }
}
