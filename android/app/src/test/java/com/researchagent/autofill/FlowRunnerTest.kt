package com.researchagent.autofill

import com.researchagent.autofill.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** "Automatizar operação": fluxo gravado numa resolução é reproduzido em outra, localizando por semântica. */
class FlowRunnerTest {

    /** App genérico: lista → detalhe ("Começar") → pergunta. [scale] simula outra resolução; [prefix] muda textos variáveis. */
    class FakeApp(private val scale: Double, private val prefix: String, private val withStart: Boolean = true) : ScreenDriver {
        var state = 0
        val clicked = ArrayList<String>()
        private fun b(l: Int, t: Int, r: Int, bo: Int) = Bounds((l * scale).toInt(), (t * scale).toInt(), (r * scale).toInt(), (bo * scale).toInt())
        private fun build(): ScreenSnapshot {
            val n = ArrayList<ScreenNode>()
            when (state) {
                0 -> {
                    n += ScreenNode(0, -1, 0, "android.widget.FrameLayout", childIds = listOf(1, 2), bounds = b(0, 0, 1080, 2200))
                    n += ScreenNode(1, 0, 1, "android.widget.TextView", text = "Tasks", bounds = b(40, 80, 400, 150))
                    n += ScreenNode(2, 0, 1, "androidx.recyclerview.widget.RecyclerView", childIds = listOf(3, 5), isScrollable = true, bounds = b(0, 200, 1080, 2200))
                    for (k in 0 until 2) {
                        val id = 3 + k * 2
                        n += ScreenNode(id, 2, 2, "android.view.ViewGroup", viewId = "x:id/task_card", isClickable = true, childIds = listOf(id + 1), bounds = b(20, 220 + k * 300, 1060, 500 + k * 300))
                        n += ScreenNode(id + 1, id, 3, "android.widget.TextView", text = "$prefix ${k + 1}", bounds = b(40, 240 + k * 300, 600, 300 + k * 300))
                    }
                }
                1 -> {
                    n += ScreenNode(0, -1, 0, "android.widget.FrameLayout", childIds = listOf(1, 2), bounds = b(0, 0, 1080, 2200))
                    n += ScreenNode(1, 0, 1, "android.widget.TextView", text = "$prefix detalhes", bounds = b(40, 80, 800, 150))
                    if (withStart) n += ScreenNode(2, 0, 1, "android.widget.Button", text = "Começar", viewId = "x:id/btn_go", isClickable = true, bounds = b(300, 1900, 780, 2050))
                }
                else -> {
                    n += ScreenNode(0, -1, 0, "android.widget.FrameLayout", childIds = listOf(1), bounds = b(0, 0, 1080, 2200))
                    n += ScreenNode(1, 0, 1, "android.widget.TextView", text = "Concluído", bounds = b(40, 80, 800, 150))
                }
            }
            return ScreenSnapshot("com.example.tasks", n)
        }
        override suspend fun snapshot() = build()
        override suspend fun click(node: ScreenNode): Boolean {
            clicked += UiSemantics.viewIdTail(node.viewId)
            state = when (UiSemantics.viewIdTail(node.viewId)) { "task_card" -> 1; "btn_go" -> 2; else -> state }
            return true
        }
        override suspend fun setText(node: ScreenNode, text: String) = true
        override suspend fun setProgress(node: ScreenNode, value: Float) = true
        override suspend fun scrollForward() = false
        override suspend fun back() = true
        override suspend fun awaitChange(previous: ScreenSnapshot, timeoutMs: Long): ScreenSnapshot? =
            build().takeIf { it.signature != previous.signature }
    }

    private fun recordFlow(): Flow = runBlocking {
        val app = FakeApp(1.0, "Tarefa")
        val s0 = app.snapshot()
        val card = s0.visibleNodes.first { it.viewId.endsWith("task_card") }
        val step1 = FlowStep(ScreenKind.OTHER, UiSemantics.fingerprint(s0), UiSemantics.describe(s0, card), ActionType.CLICK, textVariable = true)
        app.click(card)
        val s1 = app.snapshot()
        val go = s1.visibleNodes.first { it.viewId.endsWith("btn_go") }
        val step2 = FlowStep(ScreenKind.OTHER, UiSemantics.fingerprint(s1), UiSemantics.describe(s1, go), ActionType.CLICK)
        Flow("f1", "Abrir tarefa", "com.example.tasks", listOf(step1, step2))
    }

    @Test fun `fluxo gravado roda em outra resolucao com textos diferentes`() = runBlocking {
        val flow = recordFlow()
        val app = FakeApp(0.66, "Job")
        val r = FlowRunner(app, stepTimeoutMs = 2000).run(flow)
        assertTrue(r.message, r.success)
        assertEquals(listOf("task_card", "btn_go"), app.clicked)
        assertEquals(2, app.state)
    }

    @Test fun `fluxo para com seguranca quando o elemento nao existe`() = runBlocking {
        val flow = recordFlow()
        val app = FakeApp(1.0, "Tarefa", withStart = false)
        val r = FlowRunner(app, stepTimeoutMs = 2000).run(flow)
        assertFalse(r.success)
        assertEquals(1, r.stepsDone)
        assertEquals(listOf("task_card"), app.clicked)
    }
}
