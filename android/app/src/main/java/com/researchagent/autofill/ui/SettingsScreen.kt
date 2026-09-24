package com.researchagent.autofill.ui

import android.text.InputType
import android.widget.LinearLayout
import com.researchagent.autofill.AppGraph
import com.researchagent.autofill.accessibility.SurveyAccessibilityService
import com.researchagent.autofill.core.AgentMode
import com.researchagent.autofill.core.QuestionType
import com.researchagent.autofill.data.AppSettings
import com.researchagent.autofill.data.LlmProvider
import kotlinx.coroutines.launch

/** Configurações: modo, confiança, privacidade, IA, apps permitidos/bloqueados (Seções 18, 21, 23, 33, 38). */
object SettingsScreen {

    private fun upd(f: (AppSettings) -> AppSettings) = AppGraph.settings.update(f)

    fun build(a: MainActivity, page: LinearLayout) {
        val ui = a.ui
        val s = AppGraph.settings.current

        // Modo (Seção 33)
        val m = ui.card(page, "Modo de operação")
        ui.radioGroup(m, AgentMode.values().map { it.label }, s.mode.ordinal, AgentMode.values().map { it.description }) { i ->
            a.setMode(AgentMode.values()[i])
        }
        m.addView(ui.muted("Mesmo no Automático, CAPTCHA, login, dado ausente, pagamento e conflitos sempre pausam.", 12f, top = 6))

        // Confiança (Seção 9 e 34)
        val conf = ui.card(page, "Confiança mínima para responder sozinho")
        val confLabel = ui.text("${(s.threshold * 100).toInt()}%", 18f, Palette.Green, bold = true)
        conf.addView(confLabel)
        ui.slider(conf, 60, 99, (s.threshold * 100).toInt()) { v ->
            confLabel.text = "$v%"
            upd { it.copy(threshold = v / 100.0) }
        }
        conf.addView(ui.muted("Respostas abaixo do limite são mostradas para você confirmar (🟡). 🟢 alta · 🟡 média · 🔴 ausente.", 12f))

        // Comportamento
        val beh = ui.card(page, "Comportamento")
        ui.switchRow(beh, "Procurar a próxima pesquisa sozinho", "Só no modo Automático; toca apenas em botões como \"Iniciar pesquisa\".",
            s.autoNextSurvey) { v -> upd { it.copy(autoNextSurvey = v) } }
        ui.switchRow(beh, "OCR quando a tela não tiver texto acessível", "Reconhecimento no próprio aparelho (Android 11+).",
            s.useOcr) { v -> upd { it.copy(useOcr = v) } }
        ui.switchRow(beh, "Som quando precisar de você", null, s.soundOnIntervention) { v -> upd { it.copy(soundOnIntervention = v) } }
        ui.switchRow(beh, "Mostrar bolha flutuante", null, s.bubbleEnabled) { v ->
            upd { it.copy(bubbleEnabled = v) }
            SurveyAccessibilityService.instance?.let { svc -> if (v) svc.showBubble() else svc.hideBubble() }
        }

        // Aprendizado e tentativas (evolução: Seções 2, 3, 11)
        val lrn = ui.card(page, "Aprendizado e tentativas")
        ui.switchRow(lrn, "🎯 Chutar respostas", "Com baixa confiança escolhe a alternativa mais provável e registra como TENTATIVA " +
            "(nunca como verdade). Dados pessoais sensíveis nunca são chutados.", s.guessMode) { v -> upd { it.copy(guessMode = v) } }
        ui.switchRow(lrn, "Observar quando não reconhecer a pesquisa", "Aprende com os seus toques (texto, tipo e posição relativa dos " +
            "elementos — nunca senhas).", s.observeUnknown) { v -> upd { it.copy(observeUnknown = v) } }
        ui.switchRow(lrn, "Desativar o serviço ao encerrar", "\"❌ Encerrar aplicativo\" também desliga a acessibilidade " +
            "(será preciso reativá-la nas configurações do Android).", s.disableServiceOnExit) { v -> upd { it.copy(disableServiceOnExit = v) } }
        lrn.addView(ui.text("Faixas de confiança", 14f, Palette.Text, bold = true, top = 10))
        val bandsLabel = ui.muted("", 12f)
        fun showBands() {
            val c = AppGraph.settings.current
            bandsLabel.text = "Alta ≥ ${(c.bandHigh * 100).toInt()}% · Boa ≥ ${(c.bandGood * 100).toInt()}% · " +
                "Intermediária ≥ ${(c.bandMid * 100).toInt()}% · Baixa abaixo"
        }
        showBands()
        lrn.addView(bandsLabel)
        lrn.addView(ui.muted("Alta", 12f, top = 6))
        ui.slider(lrn, 85, 100, (s.bandHigh * 100).toInt()) { v ->
            upd { it.copy(bandHigh = v / 100.0, bandGood = minOf(it.bandGood, (v - 1) / 100.0)) }; showBands()
        }
        lrn.addView(ui.muted("Boa", 12f))
        ui.slider(lrn, 60, 97, (s.bandGood * 100).toInt()) { v ->
            upd { it.copy(bandGood = minOf(v / 100.0, it.bandHigh - 0.01), bandMid = minOf(it.bandMid, (v - 1) / 100.0)) }; showBands()
        }
        lrn.addView(ui.muted("Intermediária", 12f))
        ui.slider(lrn, 30, 90, (s.bandMid * 100).toInt()) { v ->
            upd { it.copy(bandMid = minOf(v / 100.0, it.bandGood - 0.01)) }; showBands()
        }

        // Privacidade (Seção 21)
        val priv = ui.card(page, "Privacidade")
        ui.switchRow(priv, "Registrar respostas nos logs", "Campos sensíveis são sempre mascarados.", s.logAnswers) { v -> upd { it.copy(logAnswers = v) } }
        ui.switchRow(priv, "Bloquear capturas de tela do app", "Protege seus dados em prints e na tela de recentes.", s.secureScreens) { v ->
            upd { it.copy(secureScreens = v) }
            a.recreate()
        }
        priv.addView(ui.muted("Perfil, logs e aprendizado ficam criptografados (Android Keystore, AES-256-GCM) só neste aparelho. " +
            "Backup em nuvem desativado.", 12f, top = 8))

        // Salário mínimo
        val wage = ui.card(page, "Faixas em salários mínimos")
        ui.labeledEdit(wage, "Salário mínimo vigente (R$)", if (s.minimumWage > 0) s.minimumWage.toLong().toString() else "", "ex.: 1518",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { t ->
            upd { it.copy(minimumWage = t.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0) }
        }
        wage.addView(ui.muted("Vazio = o agente pergunta a você quando as opções vierem em \"salários mínimos\" (nada é presumido).", 12f))

        // IA (Seções 21, 23, 24, 25)
        val ai = ui.card(page, "Inteligência artificial (opcional)")
        ai.addView(ui.muted("Sem IA, o agente usa só regras locais. Com IA, ela ajuda a ENTENDER perguntas; toda resposta é validada contra o " +
            "perfil antes de ser usada. Primeiro vão só os NOMES dos campos; depois, apenas os valores relevantes. Campos 🔒 nunca saem do aparelho.", 12f))
        ui.radioGroup(ai, LlmProvider.values().map { it.label }, s.llmProvider.ordinal) { i ->
            upd { it.copy(llmProvider = LlmProvider.values()[i]) }
            a.render()
        }
        if (s.llmProvider != LlmProvider.NONE) {
            ui.labeledEdit(ai, "URL base (vazio = ${s.llmProvider.defaultUrl})", s.llmBaseUrl, s.llmProvider.defaultUrl,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI) { t -> upd { it.copy(llmBaseUrl = t.trim()) } }
            ui.labeledEdit(ai, "Modelo (vazio = ${s.llmProvider.defaultModel})", s.llmModel, s.llmProvider.defaultModel) { t ->
                upd { it.copy(llmModel = t.trim()) }
            }
            ui.labeledEdit(ai, "Chave de API" + if (s.llmProvider == LlmProvider.OPENAI_COMPATIBLE) " (Ollama local: deixe vazio)" else "",
                AppGraph.settings.apiKey, "••••••",
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) { t -> AppGraph.settings.apiKey = t }
            if (s.llmProvider == LlmProvider.OPENAI_COMPATIBLE) {
                ai.addView(ui.muted("Ollama no PC: use http://IP-DO-PC:11434/v1 e inicie o Ollama com OLLAMA_HOST=0.0.0.0.", 11.5f, top = 6))
            }
            ui.fullButton(ai, ui.button("Testar conexão", outlined = true) {
                a.toast("Testando…")
                a.scope.launch {
                    val client = AppGraph.llmClient()
                    val msg = if (client == null) "Informe a chave de API." else try {
                        val r = client.decide("Você possui filhos?", listOf("Sim", "Não"), QuestionType.SINGLE_CHOICE, mapOf("tem_filhos" to "false"))
                        if (!r.needsUser && r.answers.firstOrNull() == "Não") "Conexão OK ✓ — resposta correta"
                        else "Conectou, mas respondeu: ${r.display.ifBlank { r.reason }}"
                    } catch (e: Exception) {
                        "Falha: ${e.message?.take(160)}"
                    }
                    a.toast(msg)
                }
            }, top = 10)
        }

        // Apps (Seção 18)
        val apps = ui.card(page, "Aplicativos permitidos / bloqueados")
        ui.switchRow(apps, "Somente apps da lista de permitidos", "Mais seguro: o agente ignora qualquer outro app.", s.allowlistOnly) { v ->
            upd { it.copy(allowlistOnly = v) }
        }
        val seen = AppGraph.logs.logs.value.map { it.packageName }.filter { it.isNotBlank() && it !in s.allowedPackages }.distinct().take(6)
        if (seen.isNotEmpty()) {
            apps.addView(ui.muted("Vistos recentemente — toque para permitir:", 12f, top = 10))
            seen.forEach { pkg ->
                apps.addView(ui.textButton("+ $pkg", Palette.Blue) {
                    upd { it.copy(allowedPackages = it.allowedPackages + pkg, blockedPackages = it.blockedPackages - pkg) }
                    a.render()
                })
            }
        }
        ui.labeledEdit(apps, "Permitidos (um pacote por linha, ex.: com.android.chrome)", s.allowedPackages.sorted().joinToString("\n"), "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, multiline = true) { t ->
            upd { it.copy(allowedPackages = parsePackages(t)) }
        }
        ui.labeledEdit(apps, "Bloqueados", s.blockedPackages.sorted().joinToString("\n"), "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, multiline = true) { t ->
            upd { it.copy(blockedPackages = parsePackages(t)) }
        }
        apps.addView(ui.muted("Bancos, carteiras, mensageiros, e-mail, loja e configurações já vêm bloqueados. " +
            "Pacotes com \"bank\", \"pay\", \"wallet\"… também são ignorados, a menos que você os permita.", 12f, top = 6))

        // Aprendizado (Seção 27)
        val learn = ui.card(page, "Aprendizado")
        learn.addView(ui.muted("${AppGraph.knowledge.learned.value.size} mapeamentos pergunta → campo. Eles só indicam QUAL campo usar; " +
            "o valor sempre vem do perfil.", 12f))
        ui.buttonRow(learn,
            ui.button("Limpar mapeamentos", Palette.Muted, outlined = true) { AppGraph.knowledge.clearLearned(); a.render() },
            ui.button("Limpar pendências", Palette.Muted, outlined = true) { AppGraph.knowledge.clearPending(); a.render() })
        ui.fullButton(learn, ui.button("Zerar estatísticas", Palette.Muted, outlined = true) { AppGraph.stats.reset(); a.toast("Estatísticas zeradas") })

        // Permissões (Seção 38)
        val perm = ui.card(page, "Permissões e por quê")
        listOf(
            "Serviço de acessibilidade — ler a pesquisa, marcar opções, digitar respostas do seu perfil e tocar em \"Próximo\". Também exibe a bolha.",
            "Notificações — avisar com som quando o agente precisa de você.",
            "Internet — usada somente se você ativar um provedor de IA.",
            "Não pedimos contatos, localização, SMS, câmera, armazenamento amplo nem \"sobrepor a outros apps\"."
        ).forEach { perm.addView(ui.muted("• $it", 12.5f, top = 6)) }
        perm.addView(ui.muted("Versão ${versionName(a)}", 11f, top = 10))
    }

    private fun parsePackages(t: String): Set<String> =
        t.split('\n', ',', ';', ' ').map { it.trim() }.filter { it.contains('.') }.toSet()

    private fun versionName(a: MainActivity): String =
        runCatching { a.packageManager.getPackageInfo(a.packageName, 0).versionName }.getOrNull() ?: "?"
}
