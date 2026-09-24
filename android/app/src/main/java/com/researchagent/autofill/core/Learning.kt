package com.researchagent.autofill.core

import kotlin.math.max
import kotlin.math.min

/*
 * Aprendizado por observação (Seções 3–10, 16–19 da evolução).
 * Tudo aqui é Kotlin puro: nada depende de coordenadas absolutas, de um app específico
 * ou do Android. Coordenadas entram só como "região relativa" (terço da tela) e como desempate.
 */

/** Função semântica de um elemento — o que ele REPRESENTA, não onde está. */
enum class ElementRole(val label: String) {
    NEXT("Avançar"), SUBMIT("Enviar/concluir"), START_ITEM("Abrir tarefa/pesquisa"), ANSWER_OPTION("Opção de resposta"),
    TEXT_INPUT("Campo de texto"), FILTER("Filtro/categoria"), TAB("Aba"), SORT("Ordenação"), MENU("Menu"),
    LIST_ITEM("Item de lista"), CLOSE("Fechar"), BACK("Voltar"), CONFIRM("Confirmar"), OTHER("Outro")
}

/** Tipo de tela, reconhecido por estrutura (não por app). */
enum class ScreenKind(val label: String) {
    SURVEY_QUESTION("Pergunta"), SURVEY_LIST("Lista de pesquisas/tarefas"), COMPLETION("Conclusão"),
    LOADING("Carregando"), OTHER("Outra tela")
}

enum class ActionType { CLICK, TEXT }

/**
 * Descritor semântico de um elemento (Seção 5): texto, papel, tipo, id de recurso, região relativa,
 * posição na lista e contexto. É o que o agente aprende e depois procura em telas diferentes.
 */
data class ElementDescriptor(
    val role: ElementRole,
    val text: String,            // normalizado
    val viewId: String,          // só o nome do recurso, sem pacote
    val cls: String,             // classe curta (Button, View, TextView…)
    val row: Int,                // 0 = topo, 1 = meio, 2 = base
    val col: Int,                // 0 = esquerda, 1 = centro, 2 = direita
    val listIndex: Int,          // posição entre irmãos parecidos (-1 se não está numa lista)
    val listSize: Int,
    val checkable: Boolean,
    val editable: Boolean,
    val contextText: String = "" // pergunta/título próximo, normalizado
) {
    /** Chave estrutural (sem texto) — agrupa elementos com a mesma função em apps diferentes. */
    val structuralKey: String get() = "${role.name}|$viewId|$cls|$row"
    val textTokens: Set<String> get() = Text.tokenSet(text)
}

data class ScreenInfo(
    val packageName: String,
    val kind: ScreenKind,
    val fingerprint: Set<String>,
    val signature: Int
)

/** Uma ação observada do usuário, com o antes e o depois (Seção 4). */
data class ObservedAction(
    val packageName: String,
    val time: Long,
    val before: ScreenInfo,
    val target: ElementDescriptor,
    val action: ActionType,
    val text: String? = null,
    val question: String? = null,
    val options: List<String> = emptyList(),
    var after: ScreenInfo? = null,
    var changedScreen: Boolean = false,
    var selectedAfter: Boolean = false
)

// ═══════════════════════════════════════════════════════════════════
// Descrição e reconhecimento de elementos/telas (Seções 5, 9, 23)
// ═══════════════════════════════════════════════════════════════════
object UiSemantics {

    private val REWARD_RE = Regex("(\\d+\\s*(min|mins|minutos|minutes|m)\\b)|([$€£¢]|r\\$)|(\\b\\d+\\s*(pts|points|pontos|coins|moedas|creditos|credits)\\b)|(\\bloi\\b)")
    private val FILTER_WORDS = listOf("filtro", "filtrar", "filter", "categoria", "category", "todas", "todos", "all", "tipo", "type")
    private val SORT_WORDS = listOf("ordenar", "ordem", "sort", "classificar", "mais recentes", "maior", "menor")
    private val CLOSE_WORDS = listOf("fechar", "close", "x", "cancelar", "cancel", "dispensar", "dismiss", "agora nao", "not now", "pular", "skip")
    private val BACK_WORDS = listOf("voltar", "back", "anterior", "previous", "navigate up", "subir")
    private val CONFIRM_WORDS = listOf("ok", "entendi", "aceitar", "accept", "concordo", "agree", "confirmar", "confirm", "sim", "yes", "permitir", "allow")

    fun shortClass(c: String): String = c.substringAfterLast('.')

    fun viewIdTail(id: String): String = id.substringAfter(":id/", id).lowercase()

    /** Rótulo efetivo: o próprio ou o texto dos descendentes (cartões clicáveis). */
    fun effectiveLabel(s: ScreenSnapshot, n: ScreenNode, max: Int = 120): String {
        if (n.label.isNotBlank()) return n.label.take(max)
        return s.descendants(n).filter { it.label.isNotBlank() }.joinToString(" ") { it.label }.take(max)
    }

    fun screenBounds(s: ScreenSnapshot): Bounds {
        val vis = s.visibleNodes.filter { !it.bounds.isEmpty }
        if (vis.isEmpty()) return Bounds(0, 0, 1080, 2400)
        return Bounds(vis.minOf { it.bounds.left }, vis.minOf { it.bounds.top }, vis.maxOf { it.bounds.right }, vis.maxOf { it.bounds.bottom })
    }

    fun region(screen: Bounds, b: Bounds): Pair<Int, Int> {
        val w = max(1, screen.right - screen.left); val h = max(1, screen.bottom - screen.top)
        val row = ((b.centerY - screen.top) * 3 / h).coerceIn(0, 2)
        val col = ((b.centerX - screen.left) * 3 / w).coerceIn(0, 2)
        return row to col
    }

    /** Irmãos "parecidos" (mesma classe, clicáveis) — detecta listas, grades e grupos de opções. */
    fun similarSiblings(s: ScreenSnapshot, n: ScreenNode): List<ScreenNode> {
        val p = s.parent(n) ?: return listOf(n)
        return s.children(p).filter { it.isVisible && it.className == n.className && (it.isClickable == n.isClickable) }
    }

    fun isRewardLike(text: String): Boolean = REWARD_RE.containsMatchIn(text.lowercase())

    fun classifyScreen(s: ScreenSnapshot, page: SurveyPage? = null): ScreenKind {
        val p = page ?: SurveyAnalyzer.analyze(s)
        if (p.completed) return ScreenKind.COMPLETION
        if (p.isSurvey) return ScreenKind.SURVEY_QUESTION
        if (surveyListItems(s).size >= 2) return ScreenKind.SURVEY_LIST
        val vis = s.visibleNodes
        if (vis.count { it.label.isNotBlank() } <= 2 && vis.any { it.kind == WidgetKind.PROGRESS }) return ScreenKind.LOADING
        return ScreenKind.OTHER
    }

    /**
     * Itens de uma lista de pesquisas/tarefas: cartões clicáveis repetidos, com 2+ textos e
     * sinais de duração/recompensa ("10 min", "$0.50", "30 pts"). Genérico para qualquer app.
     */
    fun surveyListItems(s: ScreenSnapshot): List<ScreenNode> {
        val clickable = s.visibleNodes.filter { it.isClickable && !it.isEditable && !it.isOption }
        val byParent = clickable.groupBy { it.parentId }
        val out = ArrayList<ScreenNode>()
        for ((_, group) in byParent) {
            if (group.size < 2) continue
            val cards = group.filter { c ->
                val texts = s.descendants(c).filter { it.label.isNotBlank() }.map { it.label } + listOfNotNull(c.label.takeIf { it.isNotBlank() })
                texts.size >= 2 && isRewardLike(texts.joinToString(" "))
            }
            if (cards.size >= 2) out += cards
        }
        return out.sortedBy { it.bounds.top }
    }

    // cache por snapshot (evita recalcular listas/opções para cada candidato)
    private var cacheSnap: ScreenSnapshot? = null
    private var cacheListIds: Set<Int> = emptySet()
    private var cacheOptionIds: Set<Int>? = null

    @Synchronized
    private fun listIds(s: ScreenSnapshot): Set<Int> {
        if (cacheSnap !== s) { cacheSnap = s; cacheListIds = surveyListItems(s).map { it.id }.toSet(); cacheOptionIds = null }
        return cacheListIds
    }

    @Synchronized
    private fun optionIds(s: ScreenSnapshot): Set<Int> {
        listIds(s)
        return cacheOptionIds ?: SurveyAnalyzer.extractQuestions(s).flatMap { q -> q.options.map { it.nodeId } }.toSet().also { cacheOptionIds = it }
    }

    fun classifyRole(s: ScreenSnapshot, n: ScreenNode, kind: ScreenKind? = null): ElementRole {
        val label = Text.normalize(effectiveLabel(s, n, 60))
        val vid = viewIdTail(n.viewId)
        fun has(words: List<String>) = words.any { w ->
            val nw = Text.normalize(w).replace(' ', '_')
            SurveyAnalyzer.containsPhrase(label, w) || (nw.length >= 3 && vid.contains(nw))
        }
        return when {
            n.isEditable -> ElementRole.TEXT_INPUT
            n.isOption -> ElementRole.ANSWER_OPTION
            n.id in listIds(s) -> ElementRole.START_ITEM
            kind == ScreenKind.SURVEY_QUESTION && n.id in optionIds(s) -> ElementRole.ANSWER_OPTION
            vid.contains("next") || vid.contains("continue") || vid.contains("proximo") || (has(SurveyAnalyzer.NEXT_WORDS) && label.length <= 30) -> ElementRole.NEXT
            vid.contains("submit") || vid.contains("finish") || (has(SurveyAnalyzer.SUBMIT_WORDS) && label.length <= 30) -> ElementRole.SUBMIT
            vid.contains("back") || has(BACK_WORDS) -> ElementRole.BACK
            vid.contains("close") || vid.contains("dismiss") || (has(CLOSE_WORDS) && label.length <= 20) -> ElementRole.CLOSE
            vid.contains("filter") || vid.contains("chip") || vid.contains("category") || has(FILTER_WORDS) && label.length <= 25 -> ElementRole.FILTER
            vid.contains("sort") || has(SORT_WORDS) && label.length <= 25 -> ElementRole.SORT
            vid.contains("tab") || n.className.contains("Tab") -> ElementRole.TAB
            vid.contains("menu") || label == "menu" || label == "mais opcoes" || label == "more options" -> ElementRole.MENU
            has(CONFIRM_WORDS) && label.length <= 20 -> ElementRole.CONFIRM
            has(SurveyAnalyzer.START_SURVEY_WORDS) && label.length <= 40 -> ElementRole.START_ITEM
            similarSiblings(s, n).size >= 3 -> ElementRole.LIST_ITEM
            else -> ElementRole.OTHER
        }
    }

    fun describe(s: ScreenSnapshot, n: ScreenNode, kind: ScreenKind? = null, context: String = ""): ElementDescriptor {
        val sib = similarSiblings(s, n).sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
        val idx = if (sib.size >= 2) sib.indexOfFirst { it.id == n.id } else -1
        val (row, col) = region(screenBounds(s), n.bounds)
        return ElementDescriptor(
            role = classifyRole(s, n, kind),
            text = Text.normalize(effectiveLabel(s, n, 80)),
            viewId = viewIdTail(n.viewId),
            cls = shortClass(n.className),
            row = row, col = col,
            listIndex = idx, listSize = if (idx >= 0) sib.size else 0,
            checkable = n.isCheckable, editable = n.isEditable,
            contextText = Text.normalize(context).take(120)
        )
    }

    /** Impressão digital estrutural: ids de recurso + rótulos curtos estáveis (sem números/valores). */
    fun fingerprint(s: ScreenSnapshot): Set<String> {
        val out = HashSet<String>()
        for (n in s.visibleNodes) {
            val vid = viewIdTail(n.viewId)
            if (vid.isNotEmpty()) out += "id:$vid"
            val l = Text.normalize(n.label)
            if (l.isNotEmpty() && l.length <= 24 && !l.any { it.isDigit() } && (n.isClickable || n.kind == WidgetKind.BUTTON)) out += "t:$l"
        }
        return out
    }

    fun screenInfo(s: ScreenSnapshot, page: SurveyPage? = null): ScreenInfo =
        ScreenInfo(s.packageName, classifyScreen(s, page), fingerprint(s), s.signature)

    fun similarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size
    }
}

// ═══════════════════════════════════════════════════════════════════
// Localização de um elemento aprendido numa tela nova (Seção 5)
// ═══════════════════════════════════════════════════════════════════
object ElementMatcher {

    data class Match(val node: ScreenNode, val score: Double)

    /**
     * Pontua cada candidato: id de recurso, texto (se estável), papel, classe, região relativa e
     * posição na lista. Coordenadas nunca decidem sozinhas.
     */
    fun find(s: ScreenSnapshot, d: ElementDescriptor, textIsVariable: Boolean = false, kind: ScreenKind? = null): Match? {
        val cands = s.visibleNodes.filter { it.isEnabled && (it.isClickable || it.isEditable || it.isCheckable || it.kind == WidgetKind.BUTTON || it.fromOcr) }
        if (cands.isEmpty()) return null
        val screen = UiSemantics.screenBounds(s)
        var best: Match? = null
        for (n in cands) {
            var score = 0.0
            var weight = 0.0
            val vid = UiSemantics.viewIdTail(n.viewId)
            if (d.viewId.isNotEmpty()) { weight += 3; if (vid == d.viewId) score += 3 }
            if (!textIsVariable && d.text.isNotEmpty()) {
                weight += 4
                val t = Text.normalize(UiSemantics.effectiveLabel(s, n, 80))
                score += when {
                    t == d.text -> 4.0
                    d.textTokens.isNotEmpty() && Text.tokenSet(t).containsAll(d.textTokens) -> 3.0
                    else -> 4.0 * UiSemantics.similarity(Text.tokenSet(t), d.textTokens)
                }
            }
            weight += 1; if (UiSemantics.shortClass(n.className) == d.cls) score += 1
            weight += 1; if (n.isEditable == d.editable && n.isCheckable == d.checkable) score += 1
            val (row, col) = UiSemantics.region(screen, n.bounds)
            weight += 1; score += if (row == d.row) 0.7 else 0.0; score += if (col == d.col) 0.3 else 0.0
            if (d.listIndex >= 0) {
                weight += 2
                val sib = UiSemantics.similarSiblings(s, n).sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
                if (sib.size >= 2) {
                    val idx = sib.indexOfFirst { it.id == n.id }
                    score += when {
                        idx == d.listIndex -> 2.0
                        textIsVariable -> 1.2 // "um item da lista" serve, mesmo em outra posição
                        else -> 0.6
                    }
                }
            }
            weight += 2
            val role = UiSemantics.classifyRole(s, n, kind)
            if (role == d.role) score += 2 else if (d.role == ElementRole.OTHER || role == ElementRole.OTHER) score += 0.6
            val norm = if (weight == 0.0) 0.0 else score / weight
            if (best == null || norm > best.score) best = Match(n, norm)
        }
        return best
    }
}

// ═══════════════════════════════════════════════════════════════════
// Conhecimento generalizado entre apps (Seção 7)
// ═══════════════════════════════════════════════════════════════════
/**
 * Estatística por "token" (texto normalizado ou id) de qual PAPEL um elemento cumpriu e se deu certo.
 * Ex.: token "t:seguinte" → NEXT, 5 sucessos em 3 apps. Vale para qualquer app.
 */
data class RoleEvidence(val token: String, val role: ElementRole, var success: Int = 0, var failure: Int = 0, val apps: MutableSet<String> = HashSet()) {
    val confidence: Double get() {
        val n = success + failure
        if (n == 0) return 0.0
        val base = success.toDouble() / (n + 1)          // suavização: 1 observação nunca é certeza
        val spread = min(1.0, 0.7 + 0.1 * apps.size)     // visto em vários apps → mais geral
        return (base * spread).coerceIn(0.0, 0.99)
    }
}

class UiKnowledge(val evidence: MutableMap<String, RoleEvidence> = HashMap()) {
    /** Chamado a cada aprendizado (persistência). */
    var onChange: (() -> Unit)? = null

    private fun key(token: String, role: ElementRole) = "${role.name}|$token"

    fun tokensOf(d: ElementDescriptor): List<String> = buildList {
        if (d.text.isNotEmpty() && d.text.length <= 40) add("t:${d.text}")
        if (d.viewId.isNotEmpty()) add("id:${d.viewId}")
    }

    fun record(d: ElementDescriptor, role: ElementRole, pkg: String, success: Boolean) {
        for (t in tokensOf(d)) {
            val e = evidence.getOrPut(key(t, role)) { RoleEvidence(t, role) }
            if (success) e.success++ else e.failure++
            e.apps += pkg
        }
        onChange?.invoke()
    }

    /** Confiança de que o nó cumpre [role], pelo que foi aprendido (0 se nada aprendido). */
    fun roleConfidence(s: ScreenSnapshot, n: ScreenNode, role: ElementRole): Double {
        val t = Text.normalize(UiSemantics.effectiveLabel(s, n, 60))
        val vid = UiSemantics.viewIdTail(n.viewId)
        var best = 0.0
        if (t.isNotEmpty()) evidence[key("t:$t", role)]?.let { best = max(best, it.confidence) }
        if (vid.isNotEmpty()) evidence[key("id:$vid", role)]?.let { best = max(best, it.confidence) }
        return best
    }

    /** Melhor nó aprendido para um papel nesta tela (ex.: botão de avançar que o usuário sempre usa). */
    fun bestFor(s: ScreenSnapshot, role: ElementRole, minConfidence: Double = 0.45): Pair<ScreenNode, Double>? =
        s.visibleNodes.filter { it.isEnabled && (it.isClickable || it.kind == WidgetKind.BUTTON) }
            .map { it to roleConfidence(s, it, role) }
            .filter { it.second >= minConfidence }
            .maxByOrNull { it.second }

    fun forget(token: String) { evidence.keys.filter { it.endsWith("|$token") }.forEach { evidence.remove(it) } }
}

// ═══════════════════════════════════════════════════════════════════
// Fluxos (Seções 6, 8, 17) — sequência abstrata de passos
// ═══════════════════════════════════════════════════════════════════
data class FlowStep(
    val screenKind: ScreenKind,
    val fingerprint: Set<String>,
    val target: ElementDescriptor,
    val action: ActionType,
    val text: String? = null,
    /** O texto do alvo mudou entre demonstrações → é VARIÁVEL (título, pergunta, recompensa…). */
    val textVariable: Boolean = false,
    /** O texto digitado mudou entre demonstrações → precisa vir do perfil/usuário. */
    val inputVariable: Boolean = false
)

data class Flow(
    val id: String,
    var name: String,
    val packageName: String,
    var steps: List<FlowStep>,
    var demonstrations: Int = 1,
    var successes: Int = 0,
    var failures: Int = 0,
    val createdAt: Long = 0,
    /** "survey-entry" = como iniciar uma pesquisa neste app; "operation" = gravada pelo usuário. */
    val kind: String = "operation"
) {
    /** Confiança cresce com demonstrações consistentes e execuções bem-sucedidas (Seção 17). */
    val confidence: Double get() {
        val demo = when (demonstrations) { 0 -> 0.0; 1 -> 0.5; 2 -> 0.7; else -> 0.85 }
        val runs = successes + failures
        val runFactor = if (runs == 0) 0.0 else (successes - 2.0 * failures) / (runs + 2) * 0.15
        return (demo + runFactor).coerceIn(0.05, 0.99)
    }
    val demonstrationsNeeded: Int get() = max(0, 3 - demonstrations)
}

object FlowLearner {

    /** Converte ações observadas em passos abstratos. */
    fun toSteps(actions: List<ObservedAction>): List<FlowStep> = actions.map { a ->
        FlowStep(a.before.kind, a.before.fingerprint, a.target, a.action, a.text)
    }

    /**
     * Compara uma nova demonstração com o fluxo existente (Seção 17): o que se repete é ESTRUTURAL;
     * o que muda é VARIÁVEL. Retorna null se as demonstrações não se alinham (outro fluxo).
     */
    fun merge(flow: Flow, demo: List<FlowStep>): Flow? {
        if (demo.isEmpty()) return null
        val a = flow.steps
        val n = min(a.size, demo.size)
        if (n == 0) return null
        var aligned = 0
        val merged = ArrayList<FlowStep>()
        for (i in 0 until n) {
            val x = a[i]; val y = demo[i]
            val sameRole = x.target.role == y.target.role && x.action == y.action
            val sameStruct = x.target.viewId == y.target.viewId && x.target.cls == y.target.cls
            if (sameRole || sameStruct) aligned++
            val textVar = x.textVariable || x.target.text != y.target.text
            val inputVar = x.inputVariable || (x.text != null && x.text != y.text)
            merged += x.copy(
                fingerprint = x.fingerprint.intersect(y.fingerprint).ifEmpty { x.fingerprint },
                textVariable = textVar,
                inputVariable = inputVar,
                target = if (textVar) x.target.copy(text = commonText(x.target.text, y.target.text)) else x.target
            )
        }
        if (aligned < max(1, (n * 0.6).toInt())) return null
        // passos extras da demonstração mais longa ficam (podem ser opcionais)
        if (a.size > n) merged += a.drop(n)
        return flow.copy(steps = merged, demonstrations = flow.demonstrations + 1)
    }

    private fun commonText(a: String, b: String): String {
        val common = Text.tokenSet(a).intersect(Text.tokenSet(b))
        return a.split(' ').filter { Text.stem(it) in common }.joinToString(" ")
    }

    /** Qual fluxo combina com a tela atual e em que passo (Seção 7: específico por app, ou geral). */
    fun match(flows: List<Flow>, screen: ScreenInfo): Pair<Flow, Int>? {
        var best: Triple<Flow, Int, Double>? = null
        for (f in flows) {
            if (f.packageName != screen.packageName && f.packageName != "*") continue
            f.steps.forEachIndexed { i, st ->
                if (st.screenKind != screen.kind && st.screenKind != ScreenKind.OTHER) return@forEachIndexed
                val sim = UiSemantics.similarity(st.fingerprint, screen.fingerprint)
                val score = sim * 0.7 + f.confidence * 0.3
                if (sim >= 0.35 && (best == null || score > best!!.third)) best = Triple(f, i, score)
            }
        }
        return best?.let { it.first to it.second }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Memória de decisões (Seções 2, 10, 18)
// ═══════════════════════════════════════════════════════════════════
enum class DecisionOrigin(val label: String, val weight: Double) {
    USER("Usuário", 1.0), CORRECTION("Correção do usuário", 1.2), PROFILE("Perfil", 0.8), GUESS("Tentativa (chute)", 0.15)
}

enum class DecisionOutcome(val label: String) { CONTINUED("Pesquisa continuou"), FAILED("Falhou"), CHANGED("Mudou de tela"), UNKNOWN("Sem resultado") }

data class DecisionRecord(
    val questionKey: String,
    val question: String,
    val options: List<String>,
    val answers: List<String>,
    val origin: DecisionOrigin,
    val confidence: Double,
    val packageName: String,
    val time: Long,
    var outcome: DecisionOutcome = DecisionOutcome.UNKNOWN
)

class DecisionMemory(val records: MutableList<DecisionRecord> = ArrayList()) {
    /** Chamado a cada alteração (persistência). */
    var onChange: (() -> Unit)? = null

    fun add(r: DecisionRecord) {
        synchronized(records) {
            records += r
            if (records.size > 3000) records.subList(0, records.size - 3000).clear()
        }
        onChange?.invoke()
    }

    fun setOutcome(questionKey: String, outcome: DecisionOutcome) {
        synchronized(records) {
            records.lastOrNull { it.questionKey == questionKey && it.outcome == DecisionOutcome.UNKNOWN }?.outcome = outcome
        }
        onChange?.invoke()
    }

    /**
     * Resposta lembrada para uma pergunta semelhante, com opções compatíveis.
     * Chutes nunca viram verdade: pesam pouco e a confiança fica limitada (Seção 2).
     * Uma única correção também não vira regra universal (Seção 18): confiança cresce com repetição.
     */
    fun recall(question: String, options: List<String>): AnswerDecision? {
        val key = Text.questionKey(question)
        val scored = HashMap<String, Double>()
        var support = 0
        var onlyGuesses = true
        val snapshot = synchronized(records) { records.toList() }
        for (r in snapshot) {
            val sim = if (r.questionKey == key) 1.0 else jaccard(r.questionKey, key)
            if (sim < 0.75) continue
            val ansKey = r.answers.joinToString(" | ")
            if (options.isNotEmpty() && r.answers.any { a -> options.none { Text.looselyEquals(it, a) } }) continue
            val outcomeFactor = when (r.outcome) { DecisionOutcome.FAILED -> -0.5; DecisionOutcome.CONTINUED -> 1.1; else -> 1.0 }
            scored[ansKey] = (scored[ansKey] ?: 0.0) + r.origin.weight * sim * outcomeFactor
            support++
            if (r.origin != DecisionOrigin.GUESS) onlyGuesses = false
        }
        val best = scored.maxByOrNull { it.value } ?: return null
        if (best.value <= 0) return null
        val total = scored.values.filter { it > 0 }.sum()
        val agreement = best.value / total
        var conf = (0.45 + 0.12 * min(support, 4)) * agreement
        if (onlyGuesses) conf = min(conf, 0.4)
        val answers = best.key.split(" | ").map { a -> options.firstOrNull { Text.looselyEquals(it, a) } ?: a }
        return AnswerDecision(AnswerAction.ANSWER, answers, conf.coerceIn(0.0, 0.9), "memory",
            "Resposta lembrada de $support ocorrência(s) semelhante(s)${if (onlyGuesses) " (só tentativas)" else ""}.", engine = "memory")
    }

    private fun jaccard(a: String, b: String): Double {
        val sa = a.split(' ').filter { it.isNotBlank() }.toSet(); val sb = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (sa.isEmpty() || sb.isEmpty()) return 0.0
        return sa.intersect(sb).size.toDouble() / sa.union(sb).size
    }
}

// ═══════════════════════════════════════════════════════════════════
// Chute (Seção 2) — escolhe a alternativa mais provável, sem virar verdade
// ═══════════════════════════════════════════════════════════════════
object Guesser {
    /**
     * Ordem: resposta de baixa confiança já calculada → memória → heurística neutra.
     * Nunca chuta campo de texto livre sem memória, nem dados sensíveis.
     */
    fun guess(q: SurveyQuestion, low: AnswerDecision?, memory: DecisionMemory?, sensitiveField: Boolean): AnswerDecision? {
        if (sensitiveField) return null
        if (low != null && !low.needsUser && low.answers.isNotEmpty()) {
            return low.copy(engine = "guess", source = "guess", reason = "Tentativa a partir de resposta de baixa confiança: ${low.reason}")
        }
        memory?.recall(q.text, q.optionTexts)?.let { m ->
            return m.copy(confidence = min(m.confidence, 0.6), engine = "guess", source = "guess", reason = "Tentativa pela memória: ${m.reason}")
        }
        if (q.options.isEmpty()) return null
        val opts = q.optionTexts
        val pick: String = when {
            // prefere "não sei/prefiro não dizer" quando existir: é a tentativa que não afirma nada falso
            opts.any { Answers.isRefuseOption(it) } -> opts.first { Answers.isRefuseOption(it) }
            opts.size >= 3 && opts.all { NumberRange.parse(it) != null } -> opts[opts.size / 2]   // faixa central
            else -> opts.firstOrNull { !Answers.isOtherOption(it) && !Answers.isNoneOption(it) } ?: opts.first()
        }
        val conf = 1.0 / max(2, opts.size)
        return AnswerDecision(AnswerAction.ANSWER, listOf(pick), conf, "guess",
            "Tentativa (chute) entre ${opts.size} opções — não é um dado confirmado.", engine = "guess")
    }
}

// ═══════════════════════════════════════════════════════════════════
// Detecção de loops (Seção 19)
// ═══════════════════════════════════════════════════════════════════
class LoopDetector(private val window: Int = 12, private val threshold: Int = 3) {
    private val history = ArrayDeque<Pair<Int, String>>()

    /** Registra (tela, ação). true = mesma ação na mesma tela repetida [threshold] vezes. */
    fun record(screenSignature: Int, actionKey: String): Boolean {
        history.addLast(screenSignature to actionKey)
        while (history.size > window) history.removeFirst()
        return history.count { it.first == screenSignature && it.second == actionKey } >= threshold
    }

    fun reset() = history.clear()
}

/** Confiança separada por dimensão (Seção 11). */
data class ConfidenceReport(
    val survey: Double = 0.0,
    val question: Double = 0.0,
    val answers: Double = 0.0,
    val button: Double = 0.0,
    val decision: Double = 0.0,
    val result: Double = 0.0
) {
    fun asMap(): Map<String, Double> = linkedMapOf(
        "Pesquisa" to survey, "Pergunta" to question, "Respostas" to answers,
        "Botão" to button, "Decisão" to decision, "Resultado" to result
    )
}

/** Faixas de confiança configuráveis (Seção 11). */
data class ConfidenceBands(val high: Double = 0.95, val good: Double = 0.80, val mid: Double = 0.60) {
    fun label(v: Double): String = when {
        v >= high -> "Alta"
        v >= good -> "Boa"
        v >= mid -> "Intermediária"
        else -> "Baixa"
    }
}

/** Estado persistente da tarefa (Seção 15). */
data class TaskState(
    val status: String = "idle",
    val packageName: String = "",
    val surveyIndex: Int = 0,
    val step: Int = 0,
    val question: String = "",
    val questionKey: String = "",
    val options: List<String> = emptyList(),
    val detectedAnswers: List<String> = emptyList(),
    val userIntervention: Boolean = false,
    val nextAction: String = "",
    val screenSignature: Int = 0,
    val updatedAt: Long = 0
)
