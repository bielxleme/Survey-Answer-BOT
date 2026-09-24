package com.researchagent.autofill.ui

import android.app.AlertDialog
import android.widget.EditText
import android.widget.LinearLayout
import com.researchagent.autofill.AppGraph
import com.researchagent.autofill.automation.AgentController
import com.researchagent.autofill.automation.Observer
import com.researchagent.autofill.core.ConfidenceBands
import com.researchagent.autofill.core.DecisionOrigin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Painel de aprendizado (Seção 21): o que o agente observou, aprendeu e automatizou.
 * Permite ver e excluir padrões, fluxos e respostas aprendidas.
 */
object LearningScreen {

    fun build(a: MainActivity, page: LinearLayout) {
        val ui = a.ui
        val learning = AppGraph.learning
        val st = learning.stats.value
        val settings = AppGraph.settings.current
        val bands = ConfidenceBands(settings.bandHigh, settings.bandGood, settings.bandMid)

        // ── Resumo ──
        val sum = ui.card(page, "🧠 Aprendizado")
        fun line(label: String, v: String) {
            val r = ui.row()
            r.addView(ui.text(label, 13.5f, Palette.Text), ui.lp(0, weight = 1f))
            r.addView(ui.text(v, 13.5f, Palette.Green, bold = true), ui.lp(android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
            sum.addView(r, ui.lp(top = 6))
        }
        line("Pesquisas observadas", st.observedSurveys.toString())
        line("Ações do usuário observadas", st.observedActions.toString())
        line("Fluxos aprendidos / automações", st.flows.toString())
        line("Padrões de interface reconhecidos", st.patterns.toString())
        line("Respostas aprendidas", st.learnedAnswers.toString())
        line("Tentativas (chutes)", st.guesses.toString())
        line("Intervenções", st.interventions.toString())
        line("Confiança média", "${"%.0f".format(st.averageConfidence * 100)}% (${bands.label(st.averageConfidence)})")
        val obs = Observer.mode.value
        sum.addView(ui.muted(if (obs == Observer.Mode.OFF) "Observação desligada. Use a bolha → 🧠 ENSINAR AUTOMAÇÃO para me ensinar."
            else "Observando agora: ${obs.label}", 12f, top = 10))
        Observer.lastLearned.value.takeIf { it.isNotBlank() }?.let { sum.addView(ui.muted("Último: $it", 12f)) }

        // ── Tarefa atual (estado persistente) ──
        val t = learning.task.value
        if (t.status != "idle") {
            val tc = ui.card(page, "Tarefa atual")
            tc.addView(ui.text("Status: ${t.status}", 13.5f, Palette.Text))
            if (t.packageName.isNotBlank()) tc.addView(ui.muted("App: ${Observer.appLabel(t.packageName)}", 12f))
            tc.addView(ui.muted("Pesquisa ${t.surveyIndex} · etapa ${t.step}", 12f))
            if (t.question.isNotBlank()) tc.addView(ui.muted("Pergunta: ${t.question.take(120)}", 12f))
            if (t.detectedAnswers.isNotEmpty() && settings.logAnswers) tc.addView(ui.muted("Respostas detectadas: ${t.detectedAnswers.joinToString()}", 12f))
            if (t.userIntervention) tc.addView(ui.muted("Aguardando intervenção do usuário", 12f))
            if (t.nextAction.isNotBlank()) tc.addView(ui.muted("Próxima ação: ${t.nextAction}", 12f))
        }

        // ── Fluxos / automações ──
        val flows = learning.flows.value
        val fc = ui.card(page, "Fluxos e automações (${flows.size})")
        if (flows.isEmpty()) fc.addView(ui.muted("Nenhum ainda. Grave uma operação pela bolha (⚙️ AUTOMATIZAR OPERAÇÃO) " +
            "ou ensine como abrir uma pesquisa (🧠 ENSINAR AUTOMAÇÃO).", 12f))
        flows.sortedByDescending { it.createdAt }.forEach { f ->
            fc.addView(ui.text(f.name, 14f, Palette.Text, bold = true, top = 10))
            fc.addView(ui.muted("${Observer.appLabel(f.packageName)} · ${f.steps.size} passos · ${f.demonstrations} demonstração(ões) · " +
                "✓${f.successes} ✗${f.failures} · confiança ${"%.0f".format(f.confidence * 100)}%", 12f, top = 2))
            if (f.demonstrationsNeeded > 0) fc.addView(ui.muted("Demonstre mais ${f.demonstrationsNeeded} vez(es) para confirmar o padrão.", 12f, top = 2))
            val steps = f.steps.take(8).mapIndexed { i, s ->
                "${i + 1}. ${s.target.role.label} " + (if (s.textVariable) "(variável)" else "\"${s.target.text.take(24)}\"")
            }.joinToString("\n")
            fc.addView(ui.muted(steps, 11.5f, top = 2))
            ui.buttonRow(fc,
                ui.button("Executar", Palette.Green) {
                    if (AgentController.runFlow(f)) { a.toast("Abra o app ${Observer.appLabel(f.packageName)} — executando…"); a.moveTaskToBack(true) }
                    else a.toast("Ative o serviço de acessibilidade primeiro")
                },
                ui.button("Renomear", Palette.Muted, outlined = true) { rename(a, f.id, f.name) },
                ui.button("Excluir", Palette.Red, outlined = true) {
                    confirm(a, "Excluir \"${f.name}\"?") { learning.deleteFlow(f.id) }
                })
        }

        // ── Padrões reconhecidos (interface) ──
        val ev = learning.knowledge.evidence.values.sortedByDescending { it.confidence }
        val pc = ui.card(page, "Padrões de interface (${ev.size})")
        pc.addView(ui.muted("Elementos que aprendi a reconhecer pela FUNÇÃO (avançar, iniciar, filtro, aba…) — valem para qualquer app.", 12f))
        ev.take(40).forEach { e ->
            val r = ui.row()
            r.addView(ui.text("${e.role.label}: ${e.token.substringAfter(':').take(30)}  ${"%.0f".format(e.confidence * 100)}% · ${e.apps.size} app(s)",
                12.5f, Palette.Text), ui.lp(0, weight = 1f))
            r.addView(ui.textButton("✕", Palette.Red) { learning.deletePattern(e.token); a.render() },
                ui.lp(android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
            pc.addView(r, ui.lp(top = 6))
        }
        if (ev.size > 40) pc.addView(ui.muted("… e mais ${ev.size - 40}", 12f))

        // ── Respostas aprendidas ──
        val recs = synchronized(learning.decisions.records) { learning.decisions.records.toList() }
        val byQ = recs.groupBy { it.questionKey }
        val dc = ui.card(page, "Respostas aprendidas (${byQ.size})")
        dc.addView(ui.muted("Chutes aparecem como TENTATIVA e têm peso baixo — nunca viram verdade sozinhos. " +
            "Uma correção isolada não vira regra universal.", 12f))
        byQ.entries.sortedByDescending { e -> e.value.maxOf { it.time } }.take(40).forEach { (key, list) ->
            val last = list.maxByOrNull { it.time }!!
            val origins = list.groupingBy { it.origin }.eachCount().entries.joinToString(" ") { "${originLabel(it.key)}×${it.value}" }
            val r = ui.row()
            val col = ui.column()
            col.addView(ui.text(last.question.take(90), 12.5f, Palette.Text))
            col.addView(ui.muted("→ ${if (settings.logAnswers) last.answers.joinToString() else "••••"} · $origins · ${last.outcome.name.lowercase()}", 11.5f, top = 1))
            r.addView(col, ui.lp(0, weight = 1f))
            r.addView(ui.textButton("✕", Palette.Red) { learning.deleteDecisions(key); a.render() },
                ui.lp(android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
            dc.addView(r, ui.lp(top = 8))
        }

        // ── Reset ──
        val rc = ui.card(page, "Manutenção")
        val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        if (t.updatedAt > 0) rc.addView(ui.muted("Última atualização: ${fmt.format(Date(t.updatedAt))}", 12f))
        ui.fullButton(rc, ui.button("APAGAR TODO O APRENDIZADO", Palette.Red, outlined = true) {
            confirm(a, "Apagar padrões, fluxos, respostas aprendidas e estado da tarefa? O perfil (Meus dados) não é afetado.") {
                learning.resetAll()
            }
        }, top = 10)
    }

    private fun originLabel(o: DecisionOrigin) = when (o) {
        DecisionOrigin.USER -> "usuário"
        DecisionOrigin.CORRECTION -> "correção"
        DecisionOrigin.PROFILE -> "perfil"
        DecisionOrigin.GUESS -> "tentativa"
    }

    private fun confirm(a: MainActivity, msg: String, action: () -> Unit) {
        a.dialogOpen = true
        AlertDialog.Builder(a)
            .setMessage(msg)
            .setPositiveButton("CONFIRMAR") { _, _ -> action() }
            .setNegativeButton("Cancelar", null)
            .setOnDismissListener { a.dialogOpen = false; a.render() }
            .show()
    }

    private fun rename(a: MainActivity, id: String, current: String) {
        a.dialogOpen = true
        val edit = EditText(a).apply { setText(current); setSelection(current.length) }
        AlertDialog.Builder(a)
            .setTitle("Nome da automação")
            .setView(edit)
            .setPositiveButton("SALVAR") { _, _ -> edit.text.toString().trim().takeIf { it.isNotEmpty() }?.let { AppGraph.learning.renameFlow(id, it) } }
            .setNegativeButton("Cancelar", null)
            .setOnDismissListener { a.dialogOpen = false; a.render() }
            .show()
    }
}
