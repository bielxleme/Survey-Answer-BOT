package com.researchagent.autofill.data

import com.researchagent.autofill.core.ActionType
import com.researchagent.autofill.core.DecisionMemory
import com.researchagent.autofill.core.DecisionOrigin
import com.researchagent.autofill.core.DecisionOutcome
import com.researchagent.autofill.core.DecisionRecord
import com.researchagent.autofill.core.ElementDescriptor
import com.researchagent.autofill.core.ElementRole
import com.researchagent.autofill.core.Flow
import com.researchagent.autofill.core.FlowLearner
import com.researchagent.autofill.core.FlowStep
import com.researchagent.autofill.core.RoleEvidence
import com.researchagent.autofill.core.ScreenKind
import com.researchagent.autofill.core.TaskState
import com.researchagent.autofill.core.UiKnowledge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Contadores do painel de aprendizado (Seção 21). */
data class LearningStats(
    val observedActions: Int = 0,
    val observedSurveys: Int = 0,
    val interventions: Int = 0,
    val flows: Int = 0,
    val patterns: Int = 0,
    val learnedAnswers: Int = 0,
    val guesses: Int = 0,
    val averageConfidence: Double = 0.0
)

/**
 * Memórias do agente (Seção 10), criptografadas em repouso:
 *  - interface (UiKnowledge: papel de cada elemento por texto/id, entre apps)
 *  - fluxo (Flow: sequências abstratas por app ou gerais)
 *  - decisão (DecisionMemory: respostas escolhidas, origem e resultado)
 *  - intervenção e resultado (contadores + campos dentro das decisões/evidências)
 *  - estado persistente da tarefa (TaskState)
 */
class LearningRepository(private val store: SecureStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    val knowledge = UiKnowledge(java.util.concurrent.ConcurrentHashMap())
    val decisions = DecisionMemory()
    private val _flows = MutableStateFlow<List<Flow>>(emptyList())
    val flows: StateFlow<List<Flow>> = _flows.asStateFlow()
    private val _stats = MutableStateFlow(LearningStats())
    val stats: StateFlow<LearningStats> = _stats.asStateFlow()
    private val _task = MutableStateFlow(TaskState())
    val task: StateFlow<TaskState> = _task.asStateFlow()

    private var observedActions = 0
    private var observedSurveys = 0
    private var interventions = 0

    init {
        load()
        knowledge.onChange = { changed() }
        decisions.onChange = { changed() }
    }

    // ── API ───────────────────────────────────────────────────────────
    @Synchronized fun onObservedAction() { observedActions++; changed() }
    @Synchronized fun onObservedSurvey() { observedSurveys++; changed() }
    @Synchronized fun onIntervention() { interventions++; changed() }

    fun recordRole(d: ElementDescriptor, role: ElementRole, pkg: String, success: Boolean) = knowledge.record(d, role, pkg, success)

    fun addDecision(r: DecisionRecord) = decisions.add(r)

    /** Adiciona uma demonstração: funde com fluxo compatível do mesmo app (Seção 17) ou cria um novo. */
    @Synchronized fun addDemonstration(pkg: String, steps: List<FlowStep>, name: String, kind: String, now: Long): Flow? {
        if (steps.isEmpty()) return null
        val list = _flows.value.toMutableList()
        for ((i, f) in list.withIndex()) {
            if (f.packageName != pkg || f.kind != kind) continue
            val merged = FlowLearner.merge(f, steps) ?: continue
            list[i] = merged
            _flows.value = list; changed()
            return merged
        }
        val flow = Flow("f${now}", name, pkg, steps, createdAt = now, kind = kind)
        list += flow
        _flows.value = list; changed()
        return flow
    }

    @Synchronized fun flowResult(id: String, success: Boolean) {
        _flows.value = _flows.value.map { if (it.id == id) it.copy().also { c -> if (success) c.successes = it.successes + 1 else c.failures = it.failures + 1 } else it }
        changed()
    }

    @Synchronized fun renameFlow(id: String, name: String) {
        _flows.value = _flows.value.map { if (it.id == id) it.copy(name = name) else it }; changed()
    }

    @Synchronized fun deleteFlow(id: String) { _flows.value = _flows.value.filterNot { it.id == id }; changed() }

    @Synchronized fun deletePattern(token: String) { knowledge.forget(token); changed() }

    fun deleteDecisions(questionKey: String) { synchronized(decisions.records) { decisions.records.removeAll { it.questionKey == questionKey } }; changed() }

    @Synchronized fun saveTask(t: TaskState) { _task.value = t; changed() }

    @Synchronized fun resetAll() {
        knowledge.evidence.clear(); decisions.records.clear(); _flows.value = emptyList()
        observedActions = 0; observedSurveys = 0; interventions = 0
        _task.value = TaskState()
        changed()
    }

    // ── persistência ──────────────────────────────────────────────────
    private fun changed() {
        _stats.value = computeStats()
        saveJob?.cancel()
        saveJob = scope.launch { delay(800); persist() }
    }

    private fun computeStats(): LearningStats {
        val records = synchronized(decisions.records) { decisions.records.toList() }
        val patterns = knowledge.evidence.values.filter { it.success >= 1 }
        val confs = patterns.map { it.confidence } + _flows.value.map { it.confidence }
        return LearningStats(
            observedActions = observedActions,
            observedSurveys = observedSurveys,
            interventions = interventions,
            flows = _flows.value.size,
            patterns = patterns.size,
            learnedAnswers = records.count { it.origin == DecisionOrigin.USER || it.origin == DecisionOrigin.CORRECTION },
            guesses = records.count { it.origin == DecisionOrigin.GUESS },
            averageConfidence = if (confs.isEmpty()) 0.0 else confs.average()
        )
    }

    @Synchronized private fun persist() { runCatching { persistNow() } }

    private fun persistNow() {
        val records = synchronized(decisions.records) { decisions.records.toList() }
        val o = JSONObject()
        o.put("v", 1)
        o.put("counters", JSONObject().put("actions", observedActions).put("surveys", observedSurveys).put("interventions", interventions))
        o.put("evidence", JSONArray().also { a ->
            knowledge.evidence.values.forEach { e ->
                a.put(JSONObject().put("t", e.token).put("r", e.role.name).put("s", e.success).put("f", e.failure).put("a", JSONArray(e.apps.toList())))
            }
        })
        o.put("decisions", JSONArray().also { a ->
            records.forEach { r ->
                a.put(JSONObject().put("k", r.questionKey).put("q", r.question).put("o", JSONArray(r.options)).put("a", JSONArray(r.answers))
                    .put("g", r.origin.name).put("c", r.confidence).put("p", r.packageName).put("t", r.time).put("r", r.outcome.name))
            }
        })
        o.put("flows", JSONArray().also { a -> _flows.value.forEach { a.put(flowToJson(it)) } })
        val t = _task.value
        o.put("task", JSONObject().put("status", t.status).put("pkg", t.packageName).put("survey", t.surveyIndex).put("step", t.step)
            .put("question", t.question).put("key", t.questionKey).put("options", JSONArray(t.options))
            .put("answers", JSONArray(t.detectedAnswers)).put("intervention", t.userIntervention).put("next", t.nextAction)
            .put("sig", t.screenSignature).put("at", t.updatedAt))
        store.writeText(FILE, o.toString())
    }

    private fun load() {
        val txt = store.readText(FILE) ?: return
        runCatching {
            val o = JSONObject(txt)
            o.optJSONObject("counters")?.let { c ->
                observedActions = c.optInt("actions"); observedSurveys = c.optInt("surveys"); interventions = c.optInt("interventions")
            }
            o.optJSONArray("evidence")?.let { a ->
                for (i in 0 until a.length()) {
                    val e = a.getJSONObject(i)
                    val role = runCatching { ElementRole.valueOf(e.getString("r")) }.getOrNull() ?: continue
                    val ev = RoleEvidence(e.getString("t"), role, e.optInt("s"), e.optInt("f"), strings(e.optJSONArray("a")).toMutableSet())
                    knowledge.evidence["${role.name}|${ev.token}"] = ev
                }
            }
            o.optJSONArray("decisions")?.let { a ->
                for (i in 0 until a.length()) {
                    val r = a.getJSONObject(i)
                    decisions.records += DecisionRecord(
                        r.getString("k"), r.optString("q"), strings(r.optJSONArray("o")), strings(r.optJSONArray("a")),
                        runCatching { DecisionOrigin.valueOf(r.getString("g")) }.getOrDefault(DecisionOrigin.USER),
                        r.optDouble("c", 0.0), r.optString("p"), r.optLong("t"),
                        runCatching { DecisionOutcome.valueOf(r.getString("r")) }.getOrDefault(DecisionOutcome.UNKNOWN)
                    )
                }
            }
            o.optJSONArray("flows")?.let { a -> _flows.value = (0 until a.length()).mapNotNull { flowFromJson(a.getJSONObject(it)) } }
            o.optJSONObject("task")?.let { t ->
                _task.value = TaskState(t.optString("status"), t.optString("pkg"), t.optInt("survey"), t.optInt("step"),
                    t.optString("question"), t.optString("key"), strings(t.optJSONArray("options")), strings(t.optJSONArray("answers")),
                    t.optBoolean("intervention"), t.optString("next"), t.optInt("sig"), t.optLong("at"))
            }
        }
        _stats.value = computeStats()
    }

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }

    private fun descToJson(d: ElementDescriptor) = JSONObject().put("role", d.role.name).put("text", d.text).put("id", d.viewId)
        .put("cls", d.cls).put("row", d.row).put("col", d.col).put("li", d.listIndex).put("ls", d.listSize)
        .put("chk", d.checkable).put("edt", d.editable).put("ctx", d.contextText)

    private fun descFromJson(o: JSONObject) = ElementDescriptor(
        runCatching { ElementRole.valueOf(o.getString("role")) }.getOrDefault(ElementRole.OTHER),
        o.optString("text"), o.optString("id"), o.optString("cls"), o.optInt("row"), o.optInt("col"),
        o.optInt("li", -1), o.optInt("ls"), o.optBoolean("chk"), o.optBoolean("edt"), o.optString("ctx")
    )

    private fun flowToJson(f: Flow) = JSONObject().put("id", f.id).put("name", f.name).put("pkg", f.packageName)
        .put("demos", f.demonstrations).put("ok", f.successes).put("fail", f.failures).put("at", f.createdAt).put("kind", f.kind)
        .put("steps", JSONArray().also { a ->
            f.steps.forEach { st ->
                a.put(JSONObject().put("sk", st.screenKind.name).put("fp", JSONArray(st.fingerprint.toList())).put("t", descToJson(st.target))
                    .put("a", st.action.name).put("x", st.text ?: JSONObject.NULL).put("tv", st.textVariable).put("iv", st.inputVariable))
            }
        })

    private fun flowFromJson(o: JSONObject): Flow? = runCatching {
        val a = o.getJSONArray("steps")
        val steps = (0 until a.length()).map { i ->
            val s = a.getJSONObject(i)
            FlowStep(
                runCatching { ScreenKind.valueOf(s.getString("sk")) }.getOrDefault(ScreenKind.OTHER),
                strings(s.optJSONArray("fp")).toSet(), descFromJson(s.getJSONObject("t")),
                runCatching { ActionType.valueOf(s.getString("a")) }.getOrDefault(ActionType.CLICK),
                if (s.isNull("x")) null else s.optString("x"), s.optBoolean("tv"), s.optBoolean("iv")
            )
        }
        Flow(o.getString("id"), o.optString("name"), o.optString("pkg"), steps, o.optInt("demos", 1), o.optInt("ok"),
            o.optInt("fail"), o.optLong("at"), o.optString("kind", "operation"))
    }.getOrNull()

    companion object { private const val FILE = "learning.enc" }
}
