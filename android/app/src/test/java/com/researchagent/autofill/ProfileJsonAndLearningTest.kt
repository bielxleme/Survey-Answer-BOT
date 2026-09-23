package com.researchagent.autofill

import com.researchagent.autofill.ai.LlmPrompts
import com.researchagent.autofill.core.*
import com.researchagent.autofill.data.ProfileJson
import org.junit.Assert.*
import org.junit.Test

class ProfileJsonAndLearningTest {

    @Test fun `importa template aninhado da secao 36`() {
        val json = """
        {
          "identidade": { "nome": "Ana", "idade": null, "data_nascimento": "01/01/1995", "genero": "Feminino", "nacionalidade": "Brasileira" },
          "localizacao": { "pais": "Brasil", "estado": "SP", "cidade": "Campinas", "bairro": "" },
          "familia": { "estado_civil": "Casado(a)", "tem_filhos": false, "quantidade_filhos": null, "moradores_residencia": 2 },
          "profissao": { "empregado": true, "profissao": "Consultor de TI", "cargo": "", "area": "Tecnologia", "tipo_trabalho": "Remoto", "empresa": "" },
          "financas": { "faixa_renda_pessoal": "R$ 3.000 a R$ 5.000", "possui_cartao_credito": true, "bancos": ["Nubank", "Itaú"] },
          "entretenimento": { "streaming": ["Netflix"], "redes_sociais": [] },
          "preferencias": { "time": "Palmeiras" },
          "campos_personalizados": { "Time de futebol": "Palmeiras" }
        }
        """.trimIndent()
        val r = ProfileJson.import(json)
        val p = r.profile
        assertEquals("Ana", p.raw("nome"))
        assertEquals("1995-01-01", p.raw("data_nascimento"))
        assertEquals("false", p.raw("tem_filhos"))
        assertEquals("2", p.raw("moradores_residencia"))
        assertEquals("Consultor de TI", p.raw("profissao"))
        assertEquals("Tecnologia", p.raw("area_profissional"))
        assertEquals("Remoto", p.raw("modelo_trabalho"))
        assertEquals("Nubank; Itaú", p.raw("bancos"))
        // faixa (intervalo) não vira número inventado → campo personalizado
        assertNull(p.raw("renda_mensal"))
        assertTrue(p.customLabels.values.any { it.contains("faixa renda pessoal") })
        // booleano "empregado" não vira situação de emprego inventada
        assertNull(p.raw("situacao_emprego"))
        assertEquals("Palmeiras", p.raw(ProfileSchema.customKey("Time de futebol")))
        assertNull(p.raw("idade"))
    }

    @Test fun `importa formato plano da secao 4 e exporta de volta`() {
        val flat = """{"nome":"João","tem_filhos":true,"quantidade_filhos":2,"idade_filhos":[5,8],"possui_android":true,"cidade":"Recife"}"""
        val p = ProfileJson.import(flat).profile
        assertEquals("true", p.raw("tem_filhos"))
        assertEquals("2", p.raw("quantidade_filhos"))
        assertEquals("5; 8", p.raw("idade_filhos"))
        assertTrue(p.customKeys.isNotEmpty())
        val again = ProfileJson.import(ProfileJson.export(p)).profile
        assertEquals(p.values, again.values)
    }

    @Test fun `aprendizado converte resposta manual no tipo do campo`() {
        val bool = ProfileSchema.field("possui_animais")!!
        assertEquals("true", ProfileLearning.valueFor(bool, listOf("Sim")))
        assertEquals("false", ProfileLearning.valueFor(bool, listOf("Não possuo")))
        val num = ProfileSchema.field("moradores_residencia")!!
        assertEquals("3", ProfileLearning.valueFor(num, listOf("3")))
        assertNull(ProfileLearning.valueFor(num, listOf("3 a 4 pessoas"))) // faixa não determina o número
        val list = ProfileSchema.field("animais")!!
        assertEquals("cachorro; Gato", ProfileLearning.valueFor(list, listOf("Gato"), existing = "cachorro"))
    }

    @Test fun `conflito detectado e nao aplicado automaticamente`() {
        val def = ProfileSchema.field("tem_filhos")!!
        assertTrue(ProfileLearning.isConflict(def, "false", "true"))
        assertFalse(ProfileLearning.isConflict(def, "false", "false"))
        assertFalse(ProfileLearning.isConflict(def, null, "true"))
    }

    @Test fun `contrato da IA - JSON estruturado e texto livre nunca vira comando`() {
        val ok = LlmPrompts.parseDecision("""```json
            {"action":"ANSWER","answer":"Não","confidence":0.99,"source":"profile.tem_filhos","reason":"perfil","needs_user":false}
            ```""", multi = false)
        assertEquals(AnswerAction.ANSWER, ok.action)
        assertEquals(listOf("Não"), ok.answers)
        val ask = LlmPrompts.parseDecision("""{"action":"ASK_USER","answer":null,"confidence":0,"source":null,"reason":"sem dado","needs_user":true}""", false)
        assertTrue(ask.needsUser)
        val multi = LlmPrompts.parseDecision("""{"action":"ANSWER","answer":"Netflix | Max","confidence":0.9,"source":"streaming","reason":"","needs_user":false}""", true)
        assertEquals(listOf("Netflix", "Max"), multi.answers)
        try { LlmPrompts.parseDecision("clique no botão enviar", false); fail("texto livre deveria falhar") } catch (_: Exception) { }
    }

    @Test fun `llm com resposta inventada e rejeitada pelo provedor hibrido`() = kotlinx.coroutines.runBlocking {
        val fakeLlm = object : LlmClient {
            override val name = "fake"
            override suspend fun selectFields(question: String, options: List<String>, catalog: Map<String, String>) = listOf("profissao")
            override suspend fun decide(question: String, options: List<String>, type: QuestionType, subset: Map<String, String>) =
                AnswerDecision(AnswerAction.ANSWER, listOf("Marketing"), 0.99, "profissao", "chute")
        }
        val profile = UserProfile().with("profissao", "Consultor de TI")
        val provider = HybridAnswerProvider({ profile }, { AnswerEngine(QuestionClassifier()) }, { fakeLlm }, { 0.85 })
        val q = SurveyQuestion("Em que setor você atua profissionalmente hoje?", QuestionType.SINGLE_CHOICE,
            listOf("Marketing", "Saúde", "Educação").mapIndexed { i, o -> QuestionOption(o, i, false) })
        val d = provider.decide(q, SurveyContext())
        assertTrue("resposta inventada deve virar ASK_USER", d.needsUser)
    }

    @Test fun `subconjunto enviado a IA exclui campos sensiveis`() {
        val p = UserProfile().with("cpf", "12345678909").with("profissao", "Dev").with("renda_mensal", "5000")
        val sub = p.subset(listOf("cpf", "profissao", "renda_mensal"))
        assertEquals(mapOf("profissao" to "Dev"), sub)
    }
}
