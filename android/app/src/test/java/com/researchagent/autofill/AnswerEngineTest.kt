package com.researchagent.autofill

import com.researchagent.autofill.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AnswerEngineTest {

    private val today = LocalDate.of(2026, 9, 23)
    private val engine = AnswerEngine(QuestionClassifier(), today = today)

    private val profile = UserProfile()
        .with("profissao", "Consultor de TI")
        .with("area_profissional", "Tecnologia da Informação")
        .with("tem_filhos", "false")
        .with("data_nascimento", "1995-01-01")
        .with("genero", "Masculino")
        .with("estado_civil", "Casado(a)")
        .with("streaming", "Netflix; Max")
        .with("possui_carro", "true")
        .with("possui_moto", "false")
        .with("possui_bicicleta", "true")
        .with("renda_mensal", "4.500")
        .with("cidade", "Campinas")

    private fun single(text: String, vararg opts: String) =
        SurveyQuestion(text, QuestionType.SINGLE_CHOICE, opts.mapIndexed { i, o -> QuestionOption(o, i, false) })

    private fun multi(text: String, vararg opts: String) =
        SurveyQuestion(text, QuestionType.MULTI_CHOICE, opts.mapIndexed { i, o -> QuestionOption(o, i, false) })

    private fun open(text: String, type: QuestionType = QuestionType.TEXT) = SurveyQuestion(text, type, inputNodeId = 1)

    @Test fun `booleano do perfil responde Nao com alta confianca`() {
        val d = engine.answer(single("Você possui filhos?", "Sim", "Não"), profile)
        assertFalse(d.needsUser)
        assertEquals(listOf("Não"), d.answers)
        assertEquals(Confidence.HIGH, d.level)
        assertEquals("tem_filhos", d.fieldKey)
    }

    @Test fun `quantidade de filhos derivada de tem_filhos false`() {
        val d = engine.answer(open("Quantos filhos você tem?", QuestionType.NUMBER), profile)
        assertEquals(listOf("0"), d.answers)
        assertTrue(d.source!!.startsWith("derived:"))
    }

    @Test fun `idade calculada pela data de nascimento e faixa escolhida`() {
        val d = engine.answer(single("Qual a sua faixa etária?", "18 a 24 anos", "25 a 34 anos", "35 a 44 anos", "45 anos ou mais"), profile)
        assertEquals(listOf("25 a 34 anos"), d.answers)
        assertFalse(d.needsUser)
    }

    @Test fun `variacoes semanticas de profissao`() {
        for (q in listOf("Qual é sua ocupação?", "Com o que você trabalha?", "Qual sua profissão?")) {
            val d = engine.answer(open(q), profile)
            assertEquals(q, listOf("Consultor de TI"), d.answers)
        }
    }

    @Test fun `opcao TI casa com profissao Consultor de TI`() {
        val d = engine.answer(single("Qual sua profissão?", "TI", "Marketing", "Finanças", "Saúde", "Outro"), profile)
        assertEquals(listOf("TI"), d.answers)
    }

    @Test fun `nunca inventa dado ausente`() {
        val d = engine.answer(open("Qual seu gasto mensal com restaurantes?"), profile)
        assertTrue(d.needsUser)
        assertTrue(d.answers.isEmpty())
        val d2 = engine.answer(single("Você tem animais de estimação?", "Sim", "Não"), profile)
        assertTrue(d2.needsUser)
        assertEquals("possui_animais", d2.fieldKey)
    }

    @Test fun `renda cai na faixa correta`() {
        val d = engine.answer(single("Qual sua renda mensal pessoal?", "Até R$ 2.000", "R$ 2.001 a R$ 5.000", "Acima de R$ 5.000"), profile)
        assertEquals(listOf("R$ 2.001 a R$ 5.000"), d.answers)
    }

    @Test fun `faixas em salarios minimos exigem configuracao`() {
        val d = engine.answer(single("Qual sua renda mensal?", "Até 2 salários mínimos", "De 2 a 5 salários mínimos", "Mais de 5 salários mínimos"), profile)
        assertTrue(d.needsUser)
        val e2 = AnswerEngine(QuestionClassifier(), minimumWage = 1500.0, today = today)
        val d2 = e2.answer(single("Qual sua renda mensal?", "Até 2 salários mínimos", "De 2 a 5 salários mínimos", "Mais de 5 salários mínimos"), profile)
        assertEquals(listOf("De 2 a 5 salários mínimos"), d2.answers)
    }

    @Test fun `multipla escolha de lista`() {
        val d = engine.answer(multi("Quais serviços de streaming você assina?", "Netflix", "Prime Video", "Max", "Disney+", "Nenhum"), profile)
        assertEquals(listOf("Netflix", "Max"), d.answers)
    }

    @Test fun `multipla escolha com fatos booleanos por opcao`() {
        val d = engine.answer(multi("Quais destes itens você possui?", "Carro", "Moto", "Bicicleta", "Nenhum"), profile)
        assertEquals(listOf("Carro", "Bicicleta"), d.answers)
    }

    @Test fun `estado civil e estado UF nao se confundem`() {
        val d = engine.answer(single("Qual o seu estado civil?", "Solteiro", "Casado", "Divorciado"), profile)
        assertEquals(listOf("Casado"), d.answers)
        val d2 = engine.answer(open("Em qual estado você mora?"), profile)
        assertTrue(d2.needsUser)
        assertEquals("estado", d2.fieldKey)
    }

    @Test fun `ano de nascimento formatado`() {
        val d = engine.answer(open("Qual o seu ano de nascimento?"), profile)
        assertEquals(listOf("1995"), d.answers)
    }

    @Test fun `mapeamento aprendido aponta o campo mas valor vem do perfil`() {
        val q = "Onde fica sua residência atual?"
        val e = AnswerEngine(QuestionClassifier(learned = mapOf(Text.questionKey(q) to "cidade")), today = today)
        val d = e.answer(open(q), profile)
        assertEquals(listOf("Campinas"), d.answers)
    }

    @Test fun `contexto da pesquisa reutiliza resposta manual`() {
        val ctx = SurveyContext()
        val q = single("Qual seu time de futebol?", "A", "B")
        ctx.remember(q, AnswerDecision(AnswerAction.ANSWER, listOf("B"), 1.0, "user"))
        assertEquals(listOf("B"), engine.answer(q, profile, ctx).answers)
    }

    @Test fun `numero em formatos brasileiros`() {
        assertEquals(4500.0, Values.parseNumber("4.500")!!, 0.0)
        assertEquals(1500.5, Values.parseNumber("1.500,50")!!, 0.001)
        assertEquals(3000.0, Values.parseNumber("R$ 3 mil")!!, 0.0)
        val r = NumberRange.parse("R$ 2.001 a R$ 5.000")!!
        assertTrue(4500.0 in r)
        assertTrue(NumberRange.parse("65+")!!.contains(70.0))
        assertFalse(NumberRange.parse("Menos de 18")!!.contains(18.0))
    }

    @Test fun `formatacao de mascaras`() {
        assertEquals("123.456.789-09", Values.formatForField("cpf", "12345678909"))
        assertEquals("13000-000", Values.formatForField("cep", "13000000"))
    }
}
