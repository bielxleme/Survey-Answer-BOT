package com.researchagent.autofill.core

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Reprodução de um fluxo aprendido ("Automatizar operação", Seção 6).
 * Cada passo é localizado por SEMÂNTICA (ElementMatcher), executado e VERIFICADO.
 * Nunca usa coordenadas como mecanismo principal.
 */
class FlowRunner(
    private val driver: ScreenDriver,
    private val onProgress: (step: Int, total: Int, message: String) -> Unit = { _, _, _ -> },
    /** Pede texto ao usuário quando o passo é de digitação variável. Null = cancelar. */
    private val askText: suspend (prompt: String) -> String? = { null },
    private val minMatch: Double = 0.55,
    private val stepTimeoutMs: Long = 6_000,
    /** Passo não encontrado: pergunta ao usuário (ele pode fazer o passo, pular ou parar). Padrão = parar. */
    private val onStuck: suspend (step: Int, total: Int, message: String) -> StuckChoice = { _, _, _ -> StuckChoice.ABORT }
) {
    data class Result(val success: Boolean, val stepsDone: Int, val message: String)

    enum class StuckChoice { USER_DID_IT, SKIP_STEP, ABORT }

    suspend fun run(flow: Flow, startAt: Int = 0): Result {
        val loops = LoopDetector()
        var i = startAt
        var done = 0
        while (i < flow.steps.size && currentCoroutineContext().isActive) {
            val st = flow.steps[i]
            onProgress(i + 1, flow.steps.size, "Passo ${i + 1}/${flow.steps.size}: ${st.target.role.label} \"${st.target.text.take(30)}\"")
            val snap = waitForStepScreen(st) ?: return Result(false, done, "A tela do passo ${i + 1} não apareceu.")
            val kind = UiSemantics.classifyScreen(snap)
            val m = ElementMatcher.find(snap, st.target, st.textVariable, kind)
            if (m == null || m.score < minMatch) {
                val msg = "Não encontrei \"${st.target.text.ifBlank { st.target.role.label }}\" no passo ${i + 1} " +
                    "(semelhança ${"%.0f".format((m?.score ?: 0.0) * 100)}%)."
                when (onStuck(i + 1, flow.steps.size, msg)) {
                    StuckChoice.USER_DID_IT -> { done++; i++; continue }
                    StuckChoice.SKIP_STEP -> { i++; continue }
                    StuckChoice.ABORT -> return Result(false, done, msg)
                }
            }
            if (loops.record(snap.signature, "step:$i")) {
                val msg = "O passo ${i + 1} não muda a tela."
                when (onStuck(i + 1, flow.steps.size, msg)) {
                    StuckChoice.USER_DID_IT -> { done++; i++; loops.reset(); continue }
                    StuckChoice.SKIP_STEP -> { i++; loops.reset(); continue }
                    StuckChoice.ABORT -> return Result(false, done, "Loop no passo ${i + 1}: a tela não muda.")
                }
            }
            val ok = when (st.action) {
                ActionType.CLICK -> driver.click(m.node)
                ActionType.TEXT -> {
                    val text = if (st.inputVariable || st.text.isNullOrBlank()) askText("Digite o valor para \"${st.target.text.ifBlank { "campo" }}\"") else st.text
                    if (text == null) return Result(false, done, "Cancelado.")
                    driver.setText(m.node, text)
                }
            }
            if (!ok) return Result(false, done, "Falha ao executar o passo ${i + 1}.")
            val after = driver.awaitChange(snap, minOf(stepTimeoutMs, 5_000))
            done++
            // passo de digitação não muda a tela necessariamente
            if (st.action == ActionType.CLICK && (after == null || after.signature == snap.signature)) {
                // tenta de novo no mesmo passo (loop detector encerra se persistir)
                continue
            }
            i++
        }
        return Result(true, done, "Automação concluída (${flow.steps.size} passos).")
    }

    private suspend fun waitForStepScreen(st: FlowStep): ScreenSnapshot? {
        var snap = driver.snapshot() ?: return null
        var waited = 0L
        while (waited < stepTimeoutMs) {
            val sim = UiSemantics.similarity(st.fingerprint, UiSemantics.fingerprint(snap))
            if (sim >= 0.3 || st.fingerprint.isEmpty()) return snap
            // mesmo sem a "cara" da tela, se o alvo existe com boa semelhança, serve
            val m = ElementMatcher.find(snap, st.target, st.textVariable)
            if (m != null && m.score >= 0.75) return snap
            val next = driver.awaitChange(snap, 2000)
            waited += 2000
            if (next != null) snap = next
        }
        return snap
    }
}
