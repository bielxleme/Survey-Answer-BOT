package com.researchagent.autofill

import com.researchagent.autofill.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Testes da evolução autodidata: reconhecimento genérico, memória, chute, retomada, fluxos e loops. */
class LearningTest {

    // ── telas de exemplo (genéricas, sem depender de app) ─────────────
    /** Pergunta de WebView com "cartões" clicáveis (sem RadioButton). */
    private fun webViewQuestion(selected: String? = null, page: Int = 1): ScreenSnapshot {
        val n = ArrayList<ScreenNode>()
        n += ScreenNode(0, -1, 0, "android.webkit.WebView", childIds = listOf(1, 2, 3, 7), bounds = Bounds(0, 0, 1080, 2200))
        n += ScreenNode(1, 0, 1, "android.view.View", text = "Pergunta $page de 10", bounds = Bounds(40, 100, 600, 150))
        n += ScreenNode(2, 0, 1, "android.view.View", text = "Com que frequência você pratica esportes?", bounds = Bounds(40, 200, 1040, 300))
        n += ScreenNode(3, 0, 1, "android.view.View", childIds = listOf(4, 5, 6), bounds = Bounds(40, 350, 1040, 1000))
        listOf("Todos os dias", "Toda semana", "Nunca").forEachIndexed { i, t ->
            n += ScreenNode(4 + i, 3, 2, "android.view.View", text = t, isClickable = true, isSelected = t == selected,
                bounds = Bounds(40, 350 + i * 200, 1040, 520 + i * 200))
        }
        n += ScreenNode(7, 0, 1, "android.widget.Button", text = "Seguinte", isClickable = true, bounds = Bounds(700, 2000, 1040, 2100))
        return ScreenSnapshot("com.example.webapp", n)
    }

    /** Lista de pesquisas com duração e recompensa (padrão de apps de pesquisas pagas). */
    private fun surveyList(): ScreenSnapshot {
        val n = ArrayList<ScreenNode>()
        n += ScreenNode(0, -1, 0, "android.widget.FrameLayout", childIds = listOf(1, 2), bounds = Bounds(0, 0, 1080, 2200))
        n += ScreenNode(1, 0, 1, "android.widget.TextView", text = "Surveys", bounds = Bounds(40, 80, 400, 150))
        n += ScreenNode(2, 0, 1, "androidx.recyclerview.widget.RecyclerView", childIds = listOf(3, 6, 9), isScrollable = true, bounds = Bounds(0, 200, 1080, 2200))
        for (k in 0 until 3) {
            val base = 3 + k * 3
            n += ScreenNode(base, 2, 2, "android.view.ViewGroup", viewId = "com.example.surveys:id/survey_card", isClickable = true,
                childIds = listOf(base + 1, base + 2), bounds = Bounds(20, 220 + k * 300, 1060, 500 + k * 300))
            n += ScreenNode(base + 1, base, 3, "android.widget.TextView", text = "Survey ${k + 1}", bounds = Bounds(40, 240 + k * 300, 600, 300 + k * 300))
            n += ScreenNode(base + 2, base, 3, "android.widget.TextView", text = "${5 + k * 5} min · \$0.${k + 3}0", bounds = Bounds(40, 320 + k * 300, 600, 380 + k * 300))
        }
        return ScreenSnapshot("com.example.surveys", n)
    }

    @Test fun `pesquisa em WebView com cartoes clicaveis e reconhecida`() {
        val page = SurveyAnalyzer.analyze(webViewQuestion())
        assertTrue("deveria reconhecer como pesquisa", page.isSurvey)
        val q = page.questions.single()
        assertTrue(q.generic)
        assertEquals("Com que frequência você pratica esportes?", q.text)
        assertEquals(listOf("Todos os dias", "Toda semana", "Nunca"), q.optionTexts)
        assertEquals("Seguinte", page.nextButton?.label)
        assertTrue(SurveyAnalyzer.analyze(webViewQuestion(selected = "Nunca")).questions.single().answered)
    }

    @Test fun `lista de pesquisas com recompensa e reconhecida sem depender do app`() {
        val s = surveyList()
        assertEquals(ScreenKind.SURVEY_LIST, UiSemantics.classifyScreen(s))
        val items = UiSemantics.surveyListItems(s)
        assertEquals(3, items.size)
        val page = SurveyAnalyzer.analyze(s)
        assertFalse(page.isSurvey)
        assertEquals(items.first().id, page.startSurveyButtons.first().id)
        val d = UiSemantics.describe(s, items[1])
        assertEquals(ElementRole.START_ITEM, d.role)
        assertEquals("survey_card", d.viewId)
        assertEquals(1, d.listIndex)
    }

    @Test fun `matcher encontra elemento por semantica mesmo em outra posicao e resolucao`() {
        val s = surveyList()
        val d = UiSemantics.describe(s, s.node(6)!!) // 2º cartão
        // mesma estrutura em outra resolução (coordenadas diferentes) e texto variável
        val other = ScreenSnapshot("com.example.surveys", s.nodes.map { n ->
            n.copy(bounds = Bounds(n.bounds.left / 2, n.bounds.top / 2, n.bounds.right / 2, n.bounds.bottom / 2),
                text = n.text.replace("Survey", "Pesquisa"))
        })
        val m = ElementMatcher.find(other, d, textIsVariable = true)
        assertNotNull(m)
        assertEquals("survey_card", UiSemantics.viewIdTail(m!!.node.viewId))
        assertTrue(m.score > 0.6)
    }

    @Test fun `conhecimento aprendido acha botao de avancar sem palavra conhecida`() {
        val base = webViewQuestion()
        val odd = ScreenSnapshot(base.packageName, base.nodes.map { if (it.id == 7) it.copy(text = "➜", viewId = "app:id/fab_go") else it })
        assertNull(SurveyAnalyzer.analyze(odd).nextButton)
        val k = UiKnowledge()
        val d = UiSemantics.describe(odd, odd.node(7)!!)
        repeat(3) { k.record(d, ElementRole.NEXT, "com.a", true) }
        k.record(d, ElementRole.NEXT, "com.b", true)
        val page = SurveyAnalyzer.analyze(odd, k)
        assertEquals(7, page.nextButton?.id)
        assertTrue(page.learnedButton)
    }

    @Test fun `uma unica observacao nao vira certeza`() {
        val k = UiKnowledge()
        val s = webViewQuestion()
        k.record(UiSemantics.describe(s, s.node(7)!!), ElementRole.NEXT, "com.a", true)
        assertTrue(k.roleConfidence(s, s.node(7)!!, ElementRole.NEXT) < 0.6)
    }

    @Test fun `fluxo separa partes variaveis de estruturais e ganha confianca`() {
        val s = surveyList()
        fun demo(idx: Int): List<FlowStep> {
            val node = s.visibleNodes.filter { it.viewId.endsWith("survey_card") }[idx]
            val d = UiSemantics.describe(s, node, ScreenKind.SURVEY_LIST)
            return listOf(FlowStep(ScreenKind.SURVEY_LIST, UiSemantics.fingerprint(s), d, ActionType.CLICK))
        }
        var flow = Flow("f1", "Abrir pesquisa", s.packageName, demo(0))
        assertEquals(0.5, flow.confidence, 0.01)
        assertEquals(2, flow.demonstrationsNeeded)
        flow = FlowLearner.merge(flow, demo(1))!!
        flow = FlowLearner.merge(flow, demo(2))!!
        assertEquals(3, flow.demonstrations)
        assertTrue(flow.steps[0].textVariable)           // título/recompensa mudam
        assertEquals("survey_card", flow.steps[0].target.viewId) // estrutura se mantém
        assertTrue(flow.confidence >= 0.85)
        val m = FlowLearner.match(listOf(flow), UiSemantics.screenInfo(s))
        assertNotNull(m)
    }

    @Test fun `chute nunca vira verdade e memoria prefere resposta do usuario`() {
        val mem = DecisionMemory()
        val q = SurveyQuestion("Qual sua bebida favorita?", QuestionType.SINGLE_CHOICE,
            listOf("Café", "Chá", "Suco").mapIndexed { i, o -> QuestionOption(o, i, false) })
        val g = Guesser.guess(q, null, mem, sensitiveField = false)!!
        assertEquals("guess", g.engine)
        assertTrue(g.confidence < 0.5)
        mem.add(DecisionRecord(q.key, q.text, q.optionTexts, g.answers, DecisionOrigin.GUESS, g.confidence, "x", 1, DecisionOutcome.CONTINUED))
        assertTrue("só chutes → confiança limitada", mem.recall(q.text, q.optionTexts)!!.confidence <= 0.4)
        repeat(3) { mem.add(DecisionRecord(q.key, q.text, q.optionTexts, listOf("Chá"), DecisionOrigin.USER, 1.0, "x", 2)) }
        val r = mem.recall(q.text, q.optionTexts)!!
        assertEquals(listOf("Chá"), r.answers)
        assertTrue(r.confidence > 0.6)
        assertNull("campo sensível nunca é chutado", Guesser.guess(q, null, DecisionMemory(), sensitiveField = true))
    }

    @Test fun `detector de loop`() {
        val l = LoopDetector()
        assertFalse(l.record(1, "a")); assertFalse(l.record(1, "a")); assertTrue(l.record(1, "a"))
        l.reset(); assertFalse(l.record(1, "a"))
    }

    // ── motor: retomada inteligente e chute ─────────────────────────
    class WebSurvey : ScreenDriver {
        var selected: String? = null
        var page = 1
        var hiddenState = false
        override suspend fun snapshot() = build()
        fun build(): ScreenSnapshot {
            if (page > 1) return ScreenSnapshot("com.example.webapp", listOf(
                ScreenNode(0, -1, 0, childIds = listOf(1)), ScreenNode(1, 0, 1, "android.widget.TextView", text = "Obrigado por participar!")))
            val n = ArrayList<ScreenNode>()
            n += ScreenNode(0, -1, 0, "android.webkit.WebView", childIds = listOf(1, 2, 3, 7), bounds = Bounds(0, 0, 1080, 2200))
            n += ScreenNode(1, 0, 1, "android.view.View", text = "Pergunta 1 de 1", bounds = Bounds(40, 100, 600, 150))
            n += ScreenNode(2, 0, 1, "android.view.View", text = "Qual sua cor favorita?", bounds = Bounds(40, 200, 1040, 300))
            n += ScreenNode(3, 0, 1, "android.view.View", childIds = listOf(4, 5, 6), bounds = Bounds(40, 350, 1040, 1000))
            listOf("Azul", "Verde", "Prefiro não dizer").forEachIndexed { i, t ->
                n += ScreenNode(4 + i, 3, 2, "android.view.View", text = t, isClickable = true,
                    isSelected = !hiddenState && t == selected, bounds = Bounds(40, 350 + i * 200, 1040, 520 + i * 200))
            }
            n += ScreenNode(7, 0, 1, "android.widget.Button", text = "Próximo", isClickable = true, bounds = Bounds(700, 2000, 1040, 2100))
            return ScreenSnapshot("com.example.webapp", n)
        }
        override suspend fun click(node: ScreenNode): Boolean {
            if (node.id in 4..6) selected = node.text
            if (node.id == 7 && selected != null) page = 2
            return true
        }
        override suspend fun setText(node: ScreenNode, text: String) = false
        override suspend fun setProgress(node: ScreenNode, value: Float) = false
        override suspend fun scrollForward() = false
        override suspend fun back() = true
        override suspend fun awaitChange(previous: ScreenSnapshot, timeoutMs: Long): ScreenSnapshot? =
            build().takeIf { it.signature != previous.signature }
    }

    class Host(private val guess: Boolean, val onI: (Intervention, WebSurvey) -> InterventionResult, val screen: WebSurvey) : AgentHost {
        val events = ArrayList<AgentEvent>()
        val interventions = ArrayList<Intervention>()
        val memory = DecisionMemory()
        override val ownPackage = "own"
        override fun policy() = AgentPolicy(mode = AgentMode.AUTOMATIC, useOcr = false, guessMode = guess)
        override fun isPackageAllowed(packageName: String) = true
        override suspend fun intervene(intervention: Intervention): InterventionResult { interventions += intervention; return onI(intervention, screen) }
        override fun onEvent(event: AgentEvent) { events += event }
        override fun decisions() = memory
    }

    private val emptyProvider = HybridAnswerProvider({ UserProfile() }, { AnswerEngine(QuestionClassifier()) }, { null }, { 0.85 })

    @Test fun `ja resolvi verifica a tela, detecta a resposta do usuario e continua`() = runBlocking {
        val screen = WebSurvey()
        val host = Host(false, { i, s -> if (i.reason == InterventionReason.MISSING_INFO) { s.selected = "Verde"; InterventionResult.Resume } else InterventionResult.Resume }, screen)
        val agent = AgentEngine(screen, emptyProvider, host)
        repeat(8) { if (host.events.none { it is AgentEvent.SurveyCompleted }) agent.step() }
        assertEquals(2, screen.page)
        assertEquals(1, host.interventions.count { it.reason == InterventionReason.MISSING_INFO })
        val confirmed = host.events.filterIsInstance<AgentEvent.AnswerConfirmed>().first()
        assertTrue(confirmed.detected)
        assertEquals(listOf("Verde"), confirmed.answers)
        // aprendeu a resposta para a próxima vez
        assertEquals(listOf("Verde"), host.memory.recall("Qual sua cor favorita?", listOf("Azul", "Verde", "Prefiro não dizer"))!!.answers)
    }

    @Test fun `ja resolvi sem resposta na tela nao avanca e avisa`() = runBlocking {
        val screen = WebSurvey()
        var calls = 0
        val host = Host(false, { i, _ ->
            calls++
            if (calls >= 3) InterventionResult.Stop else InterventionResult.Resume   // usuário diz "já resolvi" sem marcar nada
        }, screen)
        val agent = AgentEngine(screen, emptyProvider, host)
        agent.step()
        assertEquals(1, screen.page)                     // não avançou
        assertNull(screen.selected)
        assertTrue(host.interventions.any { it.message.contains("Não consegui identificar sua resposta") })
        assertTrue(host.events.any { it is AgentEvent.AnswerConfirmed && !it.detected })
    }

    @Test fun `modo chutar responde, registra como tentativa e avalia o resultado`() = runBlocking {
        val screen = WebSurvey()
        val host = Host(true, { _, _ -> InterventionResult.Stop }, screen)
        val agent = AgentEngine(screen, emptyProvider, host)
        repeat(6) { if (host.events.none { it is AgentEvent.SurveyCompleted }) agent.step() }
        assertEquals(2, screen.page)
        assertTrue(host.interventions.isEmpty())
        assertEquals("Prefiro não dizer", screen.selected)     // tentativa neutra, não afirma nada falso
        val rec = host.memory.records.single()
        assertEquals(DecisionOrigin.GUESS, rec.origin)
        assertEquals(DecisionOutcome.CONTINUED, rec.outcome)
        assertTrue(host.events.any { it is AgentEvent.Guess })
    }
}
