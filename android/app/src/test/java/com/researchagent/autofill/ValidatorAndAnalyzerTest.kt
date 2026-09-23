package com.researchagent.autofill

import com.researchagent.autofill.core.*
import org.junit.Assert.*
import org.junit.Test

class ValidatorAndAnalyzerTest {

    private val q = SurveyQuestion("Qual sua profissão?", QuestionType.SINGLE_CHOICE,
        listOf("TI", "Marketing", "Saúde").mapIndexed { i, o -> QuestionOption(o, i, false) })
    private val subset = mapOf("profissao" to "Consultor de TI")

    @Test fun `aceita resposta comprovada pelo perfil`() {
        val d = AnswerDecision(AnswerAction.ANSWER, listOf("TI"), 0.98, "profile.profissao")
        val r = AnswerValidator.validate(q, d, subset, 0.85)
        assertTrue(r.accepted)
        assertEquals(listOf("TI"), r.decision.answers)
    }

    @Test fun `rejeita opcao inexistente`() {
        val d = AnswerDecision(AnswerAction.ANSWER, listOf("Engenharia"), 0.99, "profile.profissao")
        assertFalse(AnswerValidator.validate(q, d, subset, 0.85).accepted)
    }

    @Test fun `rejeita alucinacao sem fonte`() {
        val d = AnswerDecision(AnswerAction.ANSWER, listOf("Marketing"), 0.99, "profile.hobbies")
        val r = AnswerValidator.validate(q, d, subset, 0.85)
        assertFalse(r.accepted)
        assertTrue(r.decision.needsUser)
    }

    @Test fun `rejeita opcao nao sustentada pelo valor`() {
        val d = AnswerDecision(AnswerAction.ANSWER, listOf("Marketing"), 0.99, "profile.profissao")
        assertFalse(AnswerValidator.validate(q, d, subset, 0.85).accepted)
    }

    @Test fun `rejeita texto livre inventado`() {
        val open = SurveyQuestion("Qual sua renda?", QuestionType.TEXT, inputNodeId = 3)
        val d = AnswerDecision(AnswerAction.ANSWER, listOf("5000"), 0.99, "profile.renda_mensal")
        assertFalse(AnswerValidator.validate(open, d, mapOf("renda_mensal" to "4500"), 0.85).accepted)
        assertTrue(AnswerValidator.validate(open, d.copy(answers = listOf("4.500")), mapOf("renda_mensal" to "4500"), 0.85).accepted)
    }

    @Test fun `abaixo do limite de confianca nao e aceito`() {
        val d = AnswerDecision(AnswerAction.ANSWER, listOf("TI"), 0.6, "profissao")
        assertFalse(AnswerValidator.validate(q, d, subset, 0.85).accepted)
    }

    // ── Analisador de tela ──────────────────────────────────────────
    private fun survey(checkedNo: Boolean = false): ScreenSnapshot {
        val nodes = listOf(
            ScreenNode(0, -1, 0, "android.webkit.WebView", childIds = listOf(1, 2, 3, 6, 7, 8)),
            ScreenNode(1, 0, 1, "android.widget.TextView", text = "Pesquisa de hábitos — 1 de 5"),
            ScreenNode(2, 0, 1, "android.widget.TextView", text = "Você possui filhos? *"),
            ScreenNode(3, 0, 1, "android.view.View", childIds = listOf(4, 5)),
            ScreenNode(4, 3, 2, "android.widget.RadioButton", text = "Sim", isClickable = true, isCheckable = true),
            ScreenNode(5, 3, 2, "android.widget.RadioButton", text = "Não", isClickable = true, isCheckable = true, isChecked = checkedNo),
            ScreenNode(6, 0, 1, "android.widget.TextView", text = "Qual sua profissão?"),
            ScreenNode(7, 0, 1, "android.widget.EditText", hint = "Sua resposta", isEditable = true, isClickable = true),
            ScreenNode(8, 0, 1, "android.widget.Button", text = "Próxima", isClickable = true)
        )
        return ScreenSnapshot("com.android.chrome", nodes)
    }

    @Test fun `detecta pesquisa, perguntas e botao proximo`() {
        val page = SurveyAnalyzer.analyze(survey())
        assertTrue(page.isSurvey)
        assertEquals(2, page.questions.size)
        val radio = page.questions[0]
        assertEquals("Você possui filhos?", radio.text)
        assertEquals(QuestionType.SINGLE_CHOICE, radio.type)
        assertEquals(listOf("Sim", "Não"), radio.optionTexts)
        assertTrue(radio.required)
        assertEquals("Qual sua profissão?", page.questions[1].text)
        assertEquals(7, page.questions[1].inputNodeId)
        assertEquals("Próxima", page.nextButton?.label)
        assertEquals(1 to 5, page.progress)
        assertNull(page.guard)
    }

    @Test fun `pergunta marcada conta como respondida`() {
        assertTrue(SurveyAnalyzer.analyze(survey(checkedNo = true)).questions[0].answered)
    }

    @Test fun `detecta captcha, login e pagamento`() {
        fun screen(vararg texts: String, password: Boolean = false) = ScreenSnapshot("x", listOf(
            ScreenNode(0, -1, 0, "android.view.View", childIds = texts.indices.map { it + 1 } + listOf(99))
        ) + texts.mapIndexed { i, t -> ScreenNode(i + 1, 0, 1, "android.widget.TextView", text = t) } +
            ScreenNode(99, 0, 1, "android.widget.EditText", isEditable = true, isPassword = password))
        assertEquals(InterventionReason.CAPTCHA, SurveyAnalyzer.detectGuard(screen("Não sou um robô"))?.reason)
        assertEquals(InterventionReason.LOGIN, SurveyAnalyzer.detectGuard(screen("Entrar", password = true))?.reason)
        assertEquals(InterventionReason.LOGIN, SurveyAnalyzer.detectGuard(screen("Digite o código enviado por SMS"))?.reason)
        assertEquals(InterventionReason.FINANCIAL, SurveyAnalyzer.detectGuard(screen("Número do cartão", "CVV"))?.reason)
        assertNull(SurveyAnalyzer.detectGuard(screen("Qual sua cidade?")))
    }

    @Test fun `botao voltar nunca e escolhido`() {
        val s = ScreenSnapshot("x", listOf(
            ScreenNode(0, -1, 0, childIds = listOf(1, 2)),
            ScreenNode(1, 0, 1, "android.widget.Button", text = "Voltar", isClickable = true),
            ScreenNode(2, 0, 1, "android.widget.Button", text = "Continuar", isClickable = true)
        ))
        assertEquals("Continuar", SurveyAnalyzer.findButton(s, SurveyAnalyzer.NEXT_WORDS)?.label)
    }

    @Test fun `detecta conclusao`() {
        val s = ScreenSnapshot("x", listOf(
            ScreenNode(0, -1, 0, childIds = listOf(1)),
            ScreenNode(1, 0, 1, "android.widget.TextView", text = "Obrigado por participar!")
        ))
        assertTrue(SurveyAnalyzer.analyze(s).completed)
    }

    @Test fun `classificador descarta estado civil para UF`() {
        val c = QuestionClassifier().classify("Qual o seu estado civil?")
        assertEquals("estado_civil", c.first().key)
        assertTrue(c.none { it.key == "estado" })
    }
}
