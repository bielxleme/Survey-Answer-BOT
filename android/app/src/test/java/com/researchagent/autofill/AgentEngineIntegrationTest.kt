package com.researchagent.autofill

import com.researchagent.autofill.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Teste de integração: pesquisa de 2 páginas simulada numa "tela" falsa.
 * Verifica preenchimento, intervenção para dado ausente, avanço e conclusão.
 */
class AgentEngineIntegrationTest {

    /** Tela simulada com estado mutável. */
    class FakeSurvey : ScreenDriver {
        var page = 1
        var hasKids: String? = null
        var profession = ""
        var pets: String? = null
        val clicks = ArrayList<String>()
        var captcha = false

        override suspend fun snapshot(): ScreenSnapshot = build()

        private fun build(): ScreenSnapshot {
            val n = ArrayList<ScreenNode>()
            fun add(node: ScreenNode) { n += node }
            when {
                captcha -> {
                    add(ScreenNode(0, -1, 0, childIds = listOf(1)))
                    add(ScreenNode(1, 0, 1, "android.widget.TextView", text = "Verify you are human"))
                }
                page == 1 -> {
                    add(ScreenNode(0, -1, 0, childIds = listOf(1, 2, 3, 6, 7, 8)))
                    add(ScreenNode(1, 0, 1, "android.widget.TextView", text = "Pesquisa de consumo - página 1 de 2"))
                    add(ScreenNode(2, 0, 1, "android.widget.TextView", text = "Você possui filhos?"))
                    add(ScreenNode(3, 0, 1, "android.view.View", childIds = listOf(4, 5)))
                    add(ScreenNode(4, 3, 2, "android.widget.RadioButton", text = "Sim", isClickable = true, isCheckable = true, isChecked = hasKids == "Sim"))
                    add(ScreenNode(5, 3, 2, "android.widget.RadioButton", text = "Não", isClickable = true, isCheckable = true, isChecked = hasKids == "Não"))
                    add(ScreenNode(6, 0, 1, "android.widget.TextView", text = "Qual sua profissão?"))
                    add(ScreenNode(7, 0, 1, "android.widget.EditText", text = profession, hint = "Sua resposta", isEditable = true))
                    add(ScreenNode(8, 0, 1, "android.widget.Button", text = "Próxima", isClickable = true))
                }
                page == 2 -> {
                    add(ScreenNode(0, -1, 0, childIds = listOf(1, 2, 3, 6)))
                    add(ScreenNode(1, 0, 1, "android.widget.TextView", text = "Pesquisa de consumo - página 2 de 2"))
                    add(ScreenNode(2, 0, 1, "android.widget.TextView", text = "Você tem animais de estimação?"))
                    add(ScreenNode(3, 0, 1, "android.view.View", childIds = listOf(4, 5)))
                    add(ScreenNode(4, 3, 2, "android.widget.RadioButton", text = "Sim", isClickable = true, isCheckable = true, isChecked = pets == "Sim"))
                    add(ScreenNode(5, 3, 2, "android.widget.RadioButton", text = "Não", isClickable = true, isCheckable = true, isChecked = pets == "Não"))
                    add(ScreenNode(6, 0, 1, "android.widget.Button", text = "Enviar", isClickable = true))
                }
                else -> {
                    add(ScreenNode(0, -1, 0, childIds = listOf(1)))
                    add(ScreenNode(1, 0, 1, "android.widget.TextView", text = "Obrigado por participar!"))
                }
            }
            return ScreenSnapshot("com.android.chrome", n)
        }

        override suspend fun click(node: ScreenNode): Boolean {
            clicks += node.label
            when {
                page == 1 && node.id == 4 -> hasKids = "Sim"
                page == 1 && node.id == 5 -> hasKids = "Não"
                page == 1 && node.id == 8 -> if (hasKids != null && profession.isNotBlank()) page = 2
                page == 2 && node.id == 4 -> pets = "Sim"
                page == 2 && node.id == 5 -> pets = "Não"
                page == 2 && node.id == 6 -> if (pets != null) page = 3
            }
            return true
        }

        override suspend fun setText(node: ScreenNode, text: String): Boolean { profession = text; return true }
        override suspend fun setProgress(node: ScreenNode, value: Float) = false
        override suspend fun scrollForward() = false
        override suspend fun back() = true
        override suspend fun awaitChange(previous: ScreenSnapshot, timeoutMs: Long): ScreenSnapshot? {
            val now = build()
            return if (now.signature != previous.signature) now else null
        }
    }

    class Host(private val mode: AgentMode) : AgentHost {
        val events = ArrayList<AgentEvent>()
        val interventions = ArrayList<Intervention>()
        var onIntervene: (Intervention) -> InterventionResult = { i ->
            if (i.reason == InterventionReason.MISSING_INFO) InterventionResult.Answered(listOf("Sim")) else InterventionResult.Resume
        }
        override val ownPackage = "com.researchagent.autofill"
        override fun policy() = AgentPolicy(mode = mode, threshold = 0.85, useOcr = false)
        override fun isPackageAllowed(packageName: String) = packageName != "com.nu.production"
        override suspend fun intervene(intervention: Intervention): InterventionResult {
            interventions += intervention
            return onIntervene(intervention)
        }
        override fun onEvent(event: AgentEvent) { events += event }
    }

    private val profile = UserProfile().with("tem_filhos", "false").with("profissao", "Consultor de TI")

    private fun provider() = HybridAnswerProvider(
        profile = { profile },
        engine = { AnswerEngine(QuestionClassifier()) },
        llm = { null },
        threshold = { 0.85 }
    )

    @Test fun `modo automatico completa a pesquisa pedindo apenas o dado ausente`() = runBlocking {
        val screen = FakeSurvey()
        val host = Host(AgentMode.AUTOMATIC)
        val agent = AgentEngine(screen, provider(), host)
        repeat(30) { if (host.events.none { it is AgentEvent.SurveyCompleted }) agent.step() }

        assertEquals("Não", screen.hasKids)
        assertEquals("Consultor de TI", screen.profession)
        assertEquals("Sim", screen.pets)
        assertEquals(3, screen.page)
        val completed = host.events.filterIsInstance<AgentEvent.SurveyCompleted>().single()
        assertEquals(3, completed.questions)
        // só uma intervenção: animais de estimação (dado ausente) — nada foi inventado
        assertEquals(1, host.interventions.size)
        assertEquals(InterventionReason.MISSING_INFO, host.interventions[0].reason)
        assertTrue(host.events.any { it is AgentEvent.QuestionUnknown && it.fieldKey == "possui_animais" })
        // nunca clicou fora das opções/botões esperados
        assertTrue(screen.clicks.all { it in setOf("Sim", "Não", "Próxima", "Enviar") })
    }

    @Test fun `modo assistido pede confirmacao antes de avancar`() = runBlocking {
        val screen = FakeSurvey()
        val host = Host(AgentMode.ASSISTED)
        val agent = AgentEngine(screen, provider(), host)
        repeat(30) { if (host.events.none { it is AgentEvent.SurveyCompleted }) agent.step() }
        assertEquals(3, screen.page)
        assertEquals(2, host.interventions.count { it.reason == InterventionReason.CONFIRM_NEXT })
    }

    @Test fun `modo manual nao preenche nada`() = runBlocking {
        val screen = FakeSurvey()
        val host = Host(AgentMode.MANUAL)
        val agent = AgentEngine(screen, provider(), host)
        repeat(3) { agent.step() }
        assertNull(screen.hasKids)
        assertEquals("", screen.profession)
        val s = agent.status.value.suggestions
        assertEquals("Não", s.first { it.question == "Você possui filhos?" }.answer)
    }

    @Test fun `captcha pausa e pede o usuario`() = runBlocking {
        val screen = FakeSurvey().apply { captcha = true }
        val host = Host(AgentMode.AUTOMATIC)
        host.onIntervene = { screen.captcha = false; InterventionResult.Resume }
        val agent = AgentEngine(screen, provider(), host)
        agent.step()
        assertEquals(InterventionReason.CAPTCHA, host.interventions.single().reason)
        assertTrue(screen.clicks.isEmpty())
    }

    @Test fun `parar durante intervencao encerra`() = runBlocking {
        val screen = FakeSurvey().apply { page = 2 }
        val host = Host(AgentMode.AUTOMATIC)
        host.onIntervene = { InterventionResult.Stop }
        val agent = AgentEngine(screen, provider(), host)
        agent.run()
        assertNull(screen.pets)
        assertFalse(agent.status.value.running)
    }
}
