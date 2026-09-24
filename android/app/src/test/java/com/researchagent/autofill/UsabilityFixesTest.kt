package com.researchagent.autofill

import com.researchagent.autofill.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Correções de usabilidade: chute usa o que já existe, adivinhar automação, nomes, passo travado, tela desconhecida. */
class UsabilityFixesTest {

    private fun q(text: String, vararg opts: String) =
        SurveyQuestion(text, QuestionType.SINGLE_CHOICE, opts.mapIndexed { i, o -> QuestionOption(o, i, false) })

    @Test fun `chute usa primeiro resposta que voce ja deu`() {
        val mem = DecisionMemory()
        val question = q("Qual seu esporte favorito?", "Futebol", "Vôlei", "Prefiro não dizer")
        mem.add(DecisionRecord(question.key, question.text, question.optionTexts, listOf("Vôlei"), DecisionOrigin.USER, 1.0, "x", 1))
        val g = Guesser.guess(question, null, mem, sensitiveField = false)!!
        assertEquals(listOf("Vôlei"), g.answers)          // não o "Prefiro não dizer"
        assertEquals("guess", g.engine)
    }

    @Test fun `chute usa dado do perfil que coincide com uma opcao`() {
        val question = q("Em qual cidade você mora?", "Rio de Janeiro", "São Paulo", "Outra")
        val g = Guesser.guess(question, null, DecisionMemory(), sensitiveField = false, known = listOf("sao paulo", "Engenheiro"))!!
        assertEquals(listOf("São Paulo"), g.answers)
    }

    @Test fun `chute usa pergunta parecida antes de opcao qualquer`() {
        val mem = DecisionMemory()
        val old = q("Com que frequência você pratica esportes durante a semana?", "Nunca", "Às vezes", "Sempre")
        mem.add(DecisionRecord(old.key, old.text, old.optionTexts, listOf("Às vezes"), DecisionOrigin.USER, 1.0, "x", 1))
        val g = Guesser.guess(q("Com que frequência você pratica esportes?", "Nunca", "Às vezes", "Sempre"), null, mem, false)!!
        assertEquals(listOf("Às vezes"), g.answers)
    }

    @Test fun `sem nada salvo o chute escolhe uma opcao e nunca chuta sensivel`() {
        val question = q("Qual sua marca preferida?", "A", "B", "C")
        assertNotNull(Guesser.guess(question, null, DecisionMemory(), false))
        assertNull(Guesser.guess(question, null, DecisionMemory(), true, known = listOf("A")))
    }

    // ── adivinhar automação pela tela ─────────────────────────────────
    @Test fun `adivinha o passo da automacao que corresponde a tela atual`() = runBlocking {
        val app = FlowRunnerTest.FakeApp(1.0, "Tarefa")
        val s0 = app.snapshot()
        val card = s0.visibleNodes.first { it.viewId.endsWith("task_card") }
        val st1 = FlowStep(ScreenKind.OTHER, UiSemantics.fingerprint(s0), UiSemantics.describe(s0, card), ActionType.CLICK, textVariable = true)
        app.click(card)
        val s1 = app.snapshot()
        val go = s1.visibleNodes.first { it.viewId.endsWith("btn_go") }
        val st2 = FlowStep(ScreenKind.OTHER, UiSemantics.fingerprint(s1), UiSemantics.describe(s1, go), ActionType.CLICK)
        val flow = Flow("f", "x", "com.example.tasks", listOf(st1, st2))
        // usuário já está na tela de detalhe (passo 2), em outra resolução
        val other = FlowRunnerTest.FakeApp(0.7, "Job").also { it.state = 1 }
        val start = FlowLearner.bestStart(listOf(flow), other.snapshot())
        assertNotNull(start)
        assertEquals(1, start!!.step)
        val r = FlowRunner(other, stepTimeoutMs = 1000).run(flow, start.step)
        assertTrue(r.message, r.success)
        assertEquals(listOf("btn_go"), other.clicked)
        // tela de outro app: nada combina
        assertNull(FlowLearner.bestStart(listOf(flow), ScreenSnapshot("com.other", s1.nodes)))
    }

    @Test fun `passo nao encontrado pergunta ao usuario em vez de parar em silencio`() = runBlocking {
        val app = FlowRunnerTest.FakeApp(1.0, "Tarefa", withStart = false)
        val s0 = app.snapshot()
        val card = s0.visibleNodes.first { it.viewId.endsWith("task_card") }
        val st1 = FlowStep(ScreenKind.OTHER, UiSemantics.fingerprint(s0), UiSemantics.describe(s0, card), ActionType.CLICK, textVariable = true)
        val fake = ElementDescriptor(ElementRole.NEXT, "comecar", "btn_go", "Button", 2, 1, -1, 0, false, false)
        val flow = Flow("f", "x", "com.example.tasks", listOf(st1, FlowStep(ScreenKind.OTHER, emptySet(), fake, ActionType.CLICK)))
        val asked = ArrayList<String>()
        val r = FlowRunner(app, stepTimeoutMs = 1000, onStuck = { step, _, msg ->
            asked += "$step:$msg"; app.state = 2; FlowRunner.StuckChoice.USER_DID_IT   // usuário fez o passo à mão
        }).run(flow)
        assertTrue(r.message, r.success)
        assertEquals(1, asked.size)
        assertTrue(asked.single().startsWith("2:"))
    }

    // ── nomes legíveis ───────────────────────────────────────────────
    @Test fun `titulo da tela vem do site ou do cabecalho e o pacote vira nome`() {
        val web = ScreenSnapshot("com.android.chrome", listOf(
            ScreenNode(0, -1, 0, childIds = listOf(1)),
            ScreenNode(1, 0, 1, "android.widget.EditText", text = "https://www.surveyjunkie.com/survey/123", viewId = "com.android.chrome:id/url_bar",
                isEditable = true, bounds = Bounds(0, 0, 1000, 100))))
        assertEquals("surveyjunkie.com", ScreenTitle.guess(web))
        val native = ScreenSnapshot("com.iproyal.pawns", listOf(
            ScreenNode(0, -1, 0, childIds = listOf(1, 2), bounds = Bounds(0, 0, 1080, 2200)),
            ScreenNode(1, 0, 1, "android.widget.TextView", text = "12:30", bounds = Bounds(0, 0, 100, 40)),
            ScreenNode(2, 0, 1, "android.widget.TextView", text = "Pesquisas disponíveis", bounds = Bounds(40, 120, 800, 180))))
        assertEquals("Pesquisas disponíveis", ScreenTitle.guess(native))
        assertEquals("Pawns", ScreenTitle.prettyPackage("com.iproyal.pawns"))
    }

    // ── tela não reconhecida: pergunta e aceita "é uma pesquisa" ──────
    @Test fun `tela nao reconhecida pergunta o que fazer e aceita tentar mesmo assim`() = runBlocking {
        // pergunta genérica sem sinais suficientes (sem progresso, sem botão de avançar)
        val snap = ScreenSnapshot("com.example.quiz", listOf(
            ScreenNode(0, -1, 0, "android.webkit.WebView", childIds = listOf(1, 2), bounds = Bounds(0, 0, 1080, 2200)),
            ScreenNode(1, 0, 1, "android.view.View", text = "Qual sua cor favorita?", bounds = Bounds(40, 200, 1040, 300)),
            ScreenNode(2, 0, 1, "android.view.View", childIds = listOf(3, 4, 5), bounds = Bounds(40, 350, 1040, 1000)),
            ScreenNode(3, 2, 2, "android.view.View", text = "Azul", isClickable = true, bounds = Bounds(40, 350, 1040, 500)),
            ScreenNode(4, 2, 2, "android.view.View", text = "Verde", isClickable = true, bounds = Bounds(40, 550, 1040, 700)),
            ScreenNode(5, 2, 2, "android.view.View", text = "Vermelho", isClickable = true, bounds = Bounds(40, 750, 1040, 900))))
        assertFalse(SurveyAnalyzer.analyze(snap).isSurvey)
        val driver = object : ScreenDriver {
            override suspend fun snapshot() = snap
            override suspend fun click(node: ScreenNode) = true
            override suspend fun setText(node: ScreenNode, text: String) = true
            override suspend fun setProgress(node: ScreenNode, value: Float) = true
            override suspend fun scrollForward() = false
            override suspend fun back() = true
            override suspend fun awaitChange(previous: ScreenSnapshot, timeoutMs: Long): ScreenSnapshot? = null
        }
        var clock = 1_000_000L
        val reasons = ArrayList<InterventionReason>()
        lateinit var engine: AgentEngine
        val host = object : AgentHost {
            override val ownPackage = "own"
            override fun now() = clock
            override fun policy() = AgentPolicy(mode = AgentMode.AUTOMATIC, useOcr = false)
            override fun isPackageAllowed(packageName: String) = true
            override suspend fun intervene(intervention: Intervention): InterventionResult {
                reasons += intervention.reason
                return if (intervention.reason == InterventionReason.UNKNOWN_SCREEN) { engine.forceSurvey("com.example.quiz"); InterventionResult.Resume }
                else InterventionResult.Stop
            }
            override fun onEvent(event: AgentEvent) {}
        }
        engine = AgentEngine(driver, HybridAnswerProvider({ UserProfile() }, { AnswerEngine(QuestionClassifier()) }, { null }, { 0.85 }), host)
        engine.step()                  // 1ª leitura: começa a contar
        clock += 10_000
        engine.step()                  // passou do limite → pergunta
        assertEquals(listOf(InterventionReason.UNKNOWN_SCREEN), reasons)
        engine.step()                  // usuário disse "é uma pesquisa" → agora trata a pergunta
        assertEquals(InterventionReason.MISSING_INFO, reasons.last())
    }
}
