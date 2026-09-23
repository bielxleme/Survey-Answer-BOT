package com.researchagent.autofill.ui

import android.app.AlertDialog
import android.os.Build
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.content.res.ColorStateList
import com.researchagent.autofill.AppGraph
import com.researchagent.autofill.automation.AgentController
import com.researchagent.autofill.core.AgentMode
import com.researchagent.autofill.core.ProfileSchema
import com.researchagent.autofill.data.LogType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Screens {

    // ═══════════════════════════════════════════════════════════════
    // Primeira execução (Seção 35)
    // ═══════════════════════════════════════════════════════════════
    fun onboarding(a: MainActivity, page: LinearLayout) {
        val ui = a.ui
        val c = ui.column(28).apply { gravity = Gravity.CENTER_HORIZONTAL }
        page.addView(c, ui.lp())
        c.addView(ui.text("●", 56f, Palette.Green).apply { gravity = Gravity.CENTER }, ui.lp(top = 24))
        c.addView(ui.text("Bem-vindo ao Research Agent.", 24f, Palette.Text, bold = true).apply { gravity = Gravity.CENTER }, ui.lp(top = 8))
        c.addView(ui.text("Para funcionar corretamente, configure seu perfil.", 16f, Palette.Muted).apply { gravity = Gravity.CENTER }, ui.lp(top = 8))
        c.addView(ui.muted(
            "O agente preenche pesquisas usando SOMENTE informações verdadeiras que você fornecer. " +
                "Quando algo não estiver no perfil, ele pergunta — nunca inventa. Seus dados ficam criptografados apenas neste aparelho.",
            13f, top = 12
        ).apply { gravity = Gravity.CENTER })
        ui.fullButton(c, ui.button("PREENCHER PERFIL") { a.finishOnboarding(openWizard = true) }, top = 28)
        ui.fullButton(c, ui.button("IMPORTAR JSON", outlined = true) { a.startImport() })
        ui.fullButton(c, ui.button("PREENCHER DEPOIS", Palette.Muted, outlined = true) { a.finishOnboarding(openWizard = false) })
    }

    // ═══════════════════════════════════════════════════════════════
    // Assistente de perfil (Seção 37) — pode ser interrompido e retomado
    // ═══════════════════════════════════════════════════════════════
    fun wizard(a: MainActivity, page: LinearLayout) {
        val ui = a.ui
        val c = ui.column(20)
        page.addView(c, ui.lp())
        c.addView(ui.text("Vamos configurar seu perfil.", 22f, Palette.Text, bold = true))
        val keys = a.wizardKeys
        if (a.wizardIndex >= keys.size) {
            c.addView(ui.muted("Pronto! As perguntas principais foram vistas. Complete outros campos em \"Meus dados\" quando quiser.", 14f, top = 12))
            ui.fullButton(c, ui.button("CONCLUIR") { a.closeWizard() }, top = 20)
            return
        }
        val def = ProfileSchema.field(keys[a.wizardIndex]) ?: run { a.wizardIndex++; a.render(); return }
        val bar = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = keys.size
            progress = a.wizardIndex
            progressTintList = ColorStateList.valueOf(Palette.Green)
        }
        c.addView(bar, ui.lp(top = 14))
        c.addView(ui.muted("${a.wizardIndex + 1} de ${keys.size} · ${def.category.label}", 12f))
        c.addView(ui.text(def.wizardQuestion ?: "${def.label}?", 19f, Palette.Text, bold = true, top = 16))
        if (def.sensitive) c.addView(ui.text("🔒 Informação sensível — fica só no aparelho e nunca é enviada à IA.", 12f, Palette.Amber, top = 4))
        val read = ui.valueInput(c, def, AppGraph.profile.current.raw(def.key))
        ui.buttonRow(c,
            ui.button("Pular", Palette.Muted, outlined = true) { a.wizardIndex++; a.render(resetScroll = true) },
            ui.button("Salvar e continuar") {
                read()?.let { AppGraph.profile.set(def.key, it) }
                a.wizardIndex++
                a.render(resetScroll = true)
            },
            top = 20
        )
        ui.fullButton(c, ui.textButton("Continuar depois", Palette.Muted) { a.closeWizard() }.apply { gravity = Gravity.CENTER })
    }

    // ═══════════════════════════════════════════════════════════════
    // Painel (Seções 1, 19, 33, 38, 42)
    // ═══════════════════════════════════════════════════════════════
    fun dashboard(a: MainActivity, page: LinearLayout) {
        val ui = a.ui
        val status = AgentController.status.value
        val stats = AppGraph.stats.stats.value
        val pending = AppGraph.knowledge.pending.value
        val settings = AppGraph.settings.current
        val a11y = isAccessibilityEnabled(a)

        if (!a11y) {
            val c = ui.card(page, "1. Ative o serviço de acessibilidade", Palette.Amber)
            c.addView(ui.muted("O Research Agent lê a pesquisa na tela e toca nos campos através do Serviço de Acessibilidade. " +
                "Ele só age quando você toca em ATIVAR PESQUISA e nunca em bancos, mensagens ou configurações.", 13f))
            ui.fullButton(c, ui.button("Abrir configurações de acessibilidade") { a.openAccessibilitySettings() }, top = 12)
            if (Build.VERSION.SDK_INT >= 33) {
                c.addView(ui.text("Android 13+: se a opção estiver bloqueada (\"Configuração restrita\"), abra Informações do app → menu ⋮ " +
                    "→ \"Permitir configurações restritas\" e tente de novo.", 12.5f, Palette.Amber, top = 12))
                ui.fullButton(c, ui.button("Abrir informações do app", Palette.Amber, outlined = true) { a.openAppDetails() })
            }
        }
        if (!hasNotificationPermission(a)) {
            val c = ui.card(page, "Notificações", Palette.Border)
            c.addView(ui.muted("Permita notificações para ser avisado com som quando o agente precisar de você.", 13f))
            ui.fullButton(c, ui.button("Permitir notificações", outlined = true) { a.requestNotificationPermission() })
        }

        // Controle do agente
        val ctl = ui.card(page, "Agente")
        val (dot, label) = when {
            !a11y -> "○" to "Serviço de acessibilidade desativado"
            status.intervention != null -> "🔴" to "Aguardando você: ${status.intervention?.reason?.title}"
            status.paused -> "🟡" to "Pausado"
            status.running -> "🟢" to "${status.state.label}"
            else -> "●" to "Pronto"
        }
        ctl.addView(ui.text("$dot $label", 15f, Palette.Text, bold = true, top = 6))
        if (status.running && status.message.isNotBlank() && status.message != status.state.label) ctl.addView(ui.muted(status.message, 13f))
        if (status.running) {
            ctl.addView(ui.muted("Pesquisa: ${status.surveyIndex}" +
                (status.pageProgress?.let { "   ·   Página: ${it.first}/${it.second}" } ?: "") +
                (if (status.questionsOnPage > 0) "   ·   Pergunta: ${status.questionIndex.coerceAtMost(status.questionsOnPage)}/${status.questionsOnPage}" else ""), 13f))
        }
        ctl.addView(ui.muted("Modo de operação:", 12.5f, top = 10))
        ui.radioGroup(ctl, AgentMode.values().map { it.label }, settings.mode.ordinal, AgentMode.values().map { it.description }) { i ->
            a.setMode(AgentMode.values()[i])
        }
        when {
            !status.running -> ui.fullButton(ctl, ui.button("ATIVAR PESQUISA") { a.startAgent() }, top = 12)
            else -> ui.buttonRow(ctl,
                if (status.paused) ui.button("CONTINUAR") { AgentController.resume(); a.render() }
                else ui.button("PAUSAR", Palette.Amber) { AgentController.pause(); a.render() },
                ui.button("PARAR", Palette.Red) { AgentController.stop(); a.render() },
                top = 12)
        }
        if (a11y && !settings.bubbleEnabled) ctl.addView(ui.text("A bolha flutuante está oculta (Ajustes).", 12f, Palette.Amber, top = 8))

        // Resumo (Seção 42)
        val st = ui.card(page, "Resumo")
        ui.statRow(st, "Pesquisas concluídas", stats.surveysCompleted.toString())
        ui.statRow(st, "Perguntas respondidas", stats.questionsAnswered.toString())
        ui.statRow(st, "Automáticas", stats.autoAnswered.toString())
        ui.statRow(st, "Respondidas por você", stats.userAnswered.toString())
        ui.statRow(st, "Intervenções", stats.interventions.toString())
        ui.statRow(st, "Pendentes no perfil", pending.size.toString(), if (pending.isNotEmpty()) Palette.Red else Palette.Text)
        ui.statRow(st, "Perguntas desconhecidas", stats.unknownQuestions.toString())
        ui.statRow(st, "Taxa de alta confiança", "${stats.highConfidenceRate}%")
        ui.statRow(st, "Tempo automatizado", formatDuration(stats.automatedMs))

        if (pending.isNotEmpty()) {
            val c = ui.card(page, "🔴 ${pending.size} pergunta(s) sem resposta no perfil", Palette.Red)
            pending.take(4).forEach { c.addView(ui.text("• ${it.question}", 13.5f, Palette.Red, top = 4)) }
            ui.fullButton(c, ui.button("Responder em Meus dados", outlined = true) { a.goToTab(MainActivity.TAB_PROFILE) }, top = 10)
        }

        val how = ui.card(page, "Como usar")
        listOf(
            "Preencha \"Meus dados\" (ou importe um JSON). O agente só responde com o que estiver lá.",
            "Ative o serviço de acessibilidade. Uma bolha \"RA\" aparece na lateral da tela.",
            "Abra a pesquisa (navegador ou app), toque na bolha → ATIVAR PESQUISA.",
            "Se faltar informação, houver CAPTCHA, login ou pagamento, o agente pausa, toca um som e pede sua ajuda.",
            "Comece no modo Assistido; quando confiar nas respostas, use o Automático."
        ).forEachIndexed { i, s -> how.addView(ui.muted("${i + 1}. $s", 13f, top = 6)) }
    }

    // ═══════════════════════════════════════════════════════════════
    // Logs (Seção 20)
    // ═══════════════════════════════════════════════════════════════
    fun logs(a: MainActivity, page: LinearLayout) {
        val ui = a.ui
        val logs = AppGraph.logs.logs.value
        val head = ui.card(page, "Histórico")
        head.addView(ui.muted("${logs.size} registros (máx. 600). Respostas de campos sensíveis são mascaradas.", 13f))
        ui.fullButton(head, ui.button("APAGAR HISTÓRICO", Palette.Red, outlined = true) {
            a.dialogOpen = true
            AlertDialog.Builder(a)
                .setTitle("Apagar histórico?")
                .setMessage("Todos os registros de pesquisas e respostas serão removidos.")
                .setPositiveButton("APAGAR") { _, _ -> AppGraph.logs.clear() }
                .setNegativeButton("Cancelar", null)
                .setOnDismissListener { a.dialogOpen = false; a.render() }
                .show()
        }, top = 10)
        if (logs.isEmpty()) {
            page.addView(ui.muted("Nenhum registro ainda.", 14f, top = 16).apply { setPadding(ui.dp(24), ui.dp(16), 0, 0) })
            return
        }
        val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
        val list = ui.column().apply { setPadding(ui.dp(20), ui.dp(4), ui.dp(20), 0) }
        page.addView(list, ui.lp())
        logs.take(250).forEach { e ->
            val color = when (e.type) {
                LogType.COMPLETED -> Palette.Green
                LogType.ANSWER -> Palette.confidence(e.confidence)
                LogType.USER_ANSWER -> Palette.Blue
                LogType.UNKNOWN, LogType.ERROR -> Palette.Red
                LogType.INTERVENTION -> Palette.Amber
                else -> Palette.Muted
            }
            val r = ui.row()
            r.addView(ui.text(e.type.label.uppercase() + (e.confidence?.let { " ${it.emoji}" } ?: ""), 11f, color, bold = true), ui.lp(0, weight = 1f))
            r.addView(ui.text(fmt.format(Date(e.time)), 11f, Palette.Muted), ui.lp(android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
            list.addView(r, ui.lp(top = 12))
            list.addView(ui.text(e.message, 13.5f, Palette.Text))
            if (e.detail.isNotBlank()) list.addView(ui.muted(e.detail, 12f, top = 2))
            if (e.packageName.isNotBlank()) list.addView(ui.muted(e.packageName, 10.5f, top = 1))
        }
    }
}
