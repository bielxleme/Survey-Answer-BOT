package com.researchagent.autofill.ui

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import com.researchagent.autofill.AppGraph
import com.researchagent.autofill.automation.AgentController
import com.researchagent.autofill.automation.InterventionBus
import com.researchagent.autofill.core.FieldDef
import com.researchagent.autofill.core.Intervention
import com.researchagent.autofill.core.InterventionReason
import com.researchagent.autofill.core.InterventionResult
import com.researchagent.autofill.core.ProfileLearning
import com.researchagent.autofill.core.ProfileSchema
import com.researchagent.autofill.core.QuestionType
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Tela de intervenção (Seções 6, 7, 15, 26, 29): o usuário responde a pergunta,
 * decide se salva no perfil e resolve conflitos. O agente continua exatamente de onde parou.
 *
 * É uma JANELA DE DIÁLOGO: fechar (voltar/✕) apenas esconde a caixa — a pergunta continua pendente
 * e o app da pesquisa volta ao primeiro plano. Nunca encerra o app da pesquisa nem o agente.
 */
class InterventionActivity : Activity() {

    private val scope = MainScope()
    private lateinit var ui: Ui
    private lateinit var body: LinearLayout
    private var shownId: Long = -1
    private var returnPackage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AppGraph.settings.current.secureScreens) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setFinishOnTouchOutside(false)
        ui = Ui(this)
        body = ui.column(20).apply { setBackgroundColor(Palette.Bg) }
        val root = ScrollView(this).apply {
            setBackgroundColor(Palette.Bg)
            isFillViewport = true
            addView(body)
        }
        setContentView(root)
        window.setLayout((resources.displayMetrics.widthPixels * 0.94).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        scope.launch {
            // "Encerrar aplicativo" fecha também esta janela
            var first = true
            AgentController.exitSignal.collect { if (!first && it > 0) finish(); first = false }
        }
        scope.launch {
            InterventionBus.current.collect { p ->
                if (p == null) {
                    if (shownId >= 0) closeAndReturn() else showEmpty()
                } else if (p.intervention.id != shownId) {
                    shownId = p.intervention.id
                    returnPackage = p.intervention.packageName
                    render(p.intervention)
                }
            }
        }
    }

    /** Voltar = fechar a caixa SEM responder: a pergunta continua pendente (bolha vermelha). */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { closeAndReturn() }

    /** Fecha a janela e devolve o primeiro plano ao app da pesquisa (não ao launcher). */
    private fun closeAndReturn() {
        val pkg = returnPackage
        finish()
        if (pkg.isNotBlank() && pkg != packageName) {
            runCatching {
                packageManager.getLaunchIntentForPackage(pkg)?.let { li ->
                    // traz a tarefa existente para frente, preservando a tela em que o usuário estava
                    li.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    startActivity(li)
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun showEmpty() {
        body.removeAllViews()
        body.addView(ui.text("Nenhuma ação pendente.", 18f, Palette.Text, bold = true))
        ui.fullButton(body, ui.button("Fechar") { closeAndReturn() }, top = 16)
    }

    private fun done(i: Intervention, r: InterventionResult) {
        InterventionBus.respond(i.id, r)
        closeAndReturn()
    }

    private fun render(i: Intervention) {
        body.removeAllViews()
        body.addView(ui.text("⚠ AÇÃO NECESSÁRIA", 21f, Palette.Red, bold = true))
        body.addView(ui.text("O agente precisa de você.", 14f, Palette.Text, top = 4))
        body.addView(ui.text("Motivo: ${i.reason.title}", 15f, Palette.Text, bold = true, top = 10))
        body.addView(ui.muted(i.message, 13f))
        ui.fullButton(body, ui.button("✕ Fechar caixa (resolver na tela)", Palette.Muted, outlined = true) { closeAndReturn() }, top = 8)

        val q = i.question
        if (i.reason != InterventionReason.MISSING_INFO || q == null) {
            body.addView(ui.text(when (i.reason) {
                InterventionReason.CAPTCHA -> "O agente não tenta contornar verificações anti-robô. Resolva o desafio na tela e toque em CONTINUAR."
                InterventionReason.LOGIN -> "Faça login ou digite o código você mesmo. O agente nunca digita senhas ou códigos."
                InterventionReason.FINANCIAL -> "Tela financeira detectada. O agente não realiza ações de pagamento."
                InterventionReason.CONFIRM_NEXT -> "Confira as respostas na tela antes de avançar."
                InterventionReason.BLOCKED_APP -> "Este app está fora da lista permitida. Você pode liberá-lo em Ajustes."
                else -> "Resolva na tela e toque em CONTINUAR."
            }, 14f, Palette.Text, top = 14))
            ui.fullButton(body, ui.button(if (i.reason == InterventionReason.CONFIRM_NEXT) "AVANÇAR" else "CONTINUAR AUTOMAÇÃO") {
                done(i, InterventionResult.Resume)
            }, top = 18)
            ui.fullButton(body, ui.button("Ignorar / aguardar", Palette.Muted, outlined = true) { done(i, InterventionResult.Skip) })
            ui.fullButton(body, ui.button("PARAR AUTOMAÇÃO", Palette.Red, outlined = true) { AgentController.stop(); closeAndReturn() })
            return
        }

        // ── INFORMAÇÃO NECESSÁRIA ──────────────────────────────────────
        val profile = AppGraph.profile.current
        val card = ui.card(body, null, Palette.Red)
        card.addView(ui.text("\"${q.text}\"", 17f, Palette.Red, bold = true))
        val sug = i.suggestion
        if (sug != null && !sug.needsUser) {
            card.addView(ui.text("Sugestão ${sug.level.emoji} ${sug.display} — ${sug.reason}", 12.5f, Palette.Amber, top = 6))
        } else {
            card.addView(ui.muted("Essa informação não está no perfil.", 12.5f))
        }

        val selected = LinkedHashSet<String>()
        var text = ""
        if (q.options.isNotEmpty()) {
            sug?.takeIf { !it.needsUser }?.answers?.let { selected += it }
            if (q.type == QuestionType.MULTI_CHOICE) {
                q.options.forEach { o ->
                    card.addView(ui.checkbox(o.text, o.text in selected) { c -> if (c) selected += o.text else selected -= o.text })
                }
            } else {
                val idx = q.options.indexOfFirst { it.text in selected }
                ui.radioGroup(card, q.options.map { it.text }, idx) { k -> selected.clear(); selected += q.options[k].text }
            }
        } else {
            text = sug?.takeIf { !it.needsUser }?.answers?.firstOrNull().orEmpty()
            ui.labeledEdit(card, "Sua resposta", text, "") { t -> text = t }
        }

        // Salvar no perfil (Seção 7)
        val candidates: List<FieldDef> = (listOfNotNull(i.suggestedField) +
            AppGraph.classifier().classify(q.text, q.optionTexts).map { it.key })
            .distinct().mapNotNull { profile.fieldDef(it) }.filter { it.key != "observacoes" }
        var save = true
        var target: FieldDef? = candidates.firstOrNull()
        val saveCard = ui.card(body)
        val fieldBox = ui.column()
        saveCard.addView(ui.checkbox("Salvar esta informação no meu perfil", true) { c ->
            save = c
            fieldBox.visibility = if (c) android.view.View.VISIBLE else android.view.View.GONE
        })
        fieldBox.addView(ui.muted("Campo:", 12f, top = 6))
        ui.radioGroup(fieldBox, candidates.map { it.label } + "Campo personalizado com esta pergunta", 0) { k ->
            target = candidates.getOrNull(k)
        }
        saveCard.addView(fieldBox)

        fun answers(): List<String> =
            if (q.options.isNotEmpty()) q.options.map { it.text }.filter { it in selected }
            else listOfNotNull(text.trim().ifBlank { null })

        fun commit(updateProfile: Boolean) {
            val ans = answers()
            if (save && updateProfile) {
                val t = target
                val cur = AppGraph.profile.current
                val v = t?.let { ProfileLearning.valueFor(it, ans, cur.raw(it.key)) }
                if (t != null && v != null) {
                    AppGraph.profile.set(t.key, v)
                    AppGraph.knowledge.learn(q.text, t.key)
                } else {
                    // resposta não determina o campo com exatidão (ex.: uma faixa) → campo personalizado
                    AppGraph.profile.update { it.withCustom(q.text, ans.joinToString(", ")) }
                    AppGraph.knowledge.learn(q.text, ProfileSchema.customKey(q.text))
                }
                AppGraph.knowledge.removePending(q.text)
            }
            done(i, InterventionResult.Answered(ans))
        }

        ui.fullButton(body, ui.button("RESPONDER E CONTINUAR") {
            val ans = answers()
            if (ans.isEmpty()) { android.widget.Toast.makeText(this, "Escolha ou digite uma resposta", android.widget.Toast.LENGTH_SHORT).show(); return@button }
            val t = target
            if (save && t != null) {
                val existing = AppGraph.profile.current.raw(t.key)
                val v = ProfileLearning.valueFor(t, ans, existing)
                if (v != null && ProfileLearning.isConflict(t, existing, v)) {
                    // ⚠ CONFLITO DE INFORMAÇÃO (Seção 26)
                    android.app.AlertDialog.Builder(this)
                        .setTitle("⚠ CONFLITO DE INFORMAÇÃO")
                        .setMessage("${t.label}\n\nPerfil: ${ProfileLearning.display(t, existing)}\nNova resposta: ${ProfileLearning.display(t, v)}")
                        .setPositiveButton("ATUALIZAR PERFIL") { _, _ -> commit(updateProfile = true) }
                        .setNegativeButton("MANTER PERFIL") { _, _ -> commit(updateProfile = false) }
                        .show()
                    return@button
                }
            }
            commit(updateProfile = true)
        }, top = 14)
        ui.buttonRow(body,
            ui.button("Já respondi na tela", Palette.Muted, outlined = true) { done(i, InterventionResult.Resume) },
            ui.button("Ignorar", Palette.Muted, outlined = true) { done(i, InterventionResult.Skip) })
        ui.fullButton(body, ui.button("PARAR AUTOMAÇÃO", Palette.Red, outlined = true) { AgentController.stop(); closeAndReturn() })
    }
}
