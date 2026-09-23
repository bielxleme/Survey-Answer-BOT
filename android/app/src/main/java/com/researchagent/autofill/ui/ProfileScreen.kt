package com.researchagent.autofill.ui

import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.ScrollView
import com.researchagent.autofill.AppGraph
import com.researchagent.autofill.core.Category
import com.researchagent.autofill.core.DataOrigin
import com.researchagent.autofill.core.FieldDef
import com.researchagent.autofill.core.FieldStatus
import com.researchagent.autofill.core.FieldType
import com.researchagent.autofill.core.ProfileLearning
import com.researchagent.autofill.core.ProfileSchema
import com.researchagent.autofill.core.UserProfile
import com.researchagent.autofill.data.PendingQuestion

/** "MEUS DADOS" / "INFORMAÇÕES DO PERFIL" (Seções 4, 5, 6, 7, 34). */
object ProfileScreen {

    private val expanded = HashSet<Category>().apply { add(Category.IDENTIDADE) }

    fun build(a: MainActivity, page: LinearLayout) {
        val ui = a.ui
        val profile = AppGraph.profile.current
        val pending = AppGraph.knowledge.pending.value
        val neededKeys = pending.mapNotNull { it.fieldKey }.toSet()
        val allKeys = ProfileSchema.fields.map { it.key } + profile.customKeys
        val counts = allKeys.groupingBy { profile.status(it, neededKeys) }.eachCount()

        // Cabeçalho
        val head = ui.card(page, "Meus dados")
        head.addView(ui.muted("O agente usa SOMENTE estas informações. O que faltar será perguntado a você — nada é inventado.", 13f))
        val cr = ui.row()
        FieldStatus.values().forEach { st ->
            cr.addView(ui.text("${st.symbol} ${counts[st] ?: 0}   ", 16f, Palette.status(st), bold = true))
        }
        head.addView(cr, ui.lp(top = 10))
        head.addView(ui.muted("✓ informado · ? derivável · ! necessário · — não configurado", 11.5f, top = 2))
        ui.buttonRow(head,
            ui.button("Assistente") { a.openWizard() },
            ui.button("+ Campo", outlined = true) { customFieldDialog(a) }, top = 12)
        ui.buttonRow(head,
            ui.button("Importar JSON", outlined = true) { a.startImport() },
            ui.button("Exportar JSON", outlined = true) { a.startExport(template = false) })
        ui.fullButton(head, ui.textButton("Baixar modelo JSON em branco", Palette.Muted) { a.startExport(template = true) })

        // Informações necessárias (em vermelho)
        if (pending.isNotEmpty()) {
            val c = ui.card(page, "🔴 Informações necessárias (${pending.size})", Palette.Red)
            c.addView(ui.muted("Perguntas de pesquisas que o perfil ainda não responde.", 12.5f))
            pending.forEach { p ->
                c.addView(ui.text(p.question, 14.5f, Palette.Red, top = 12))
                val lbl = p.fieldKey?.let { profile.fieldDef(it)?.label }
                c.addView(ui.muted((lbl?.let { "Campo: $it · " } ?: "") + "vista ${p.count}x · NÃO INFORMADO", 11.5f, top = 2))
                ui.buttonRow(c,
                    ui.button("Responder", outlined = true) { pendingDialog(a, p) },
                    ui.button("Ignorar", Palette.Muted, outlined = true) { AppGraph.knowledge.removePending(p.question); a.render() },
                    top = 6)
            }
        }

        // Categorias (Seção 5)
        for (cat in Category.values()) {
            val defs: List<FieldDef> = if (cat == Category.PERSONALIZADO) {
                profile.customKeys.mapNotNull { profile.fieldDef(it) } + ProfileSchema.byCategory(cat)
            } else ProfileSchema.byCategory(cat)
            if (defs.isEmpty()) continue
            val open = cat in expanded
            val informed = defs.count { profile.status(it.key) == FieldStatus.INFORMED }
            val c = ui.card(page)
            val hr = ui.row()
            hr.addView(ui.text((if (open) "▾  " else "▸  ") + cat.label, 15.5f, Palette.Text, bold = true), ui.lp(0, weight = 1f))
            hr.addView(ui.text("$informed/${defs.size}", 12.5f, Palette.Muted), ui.lp(android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
            hr.setOnClickListener { if (open) expanded.remove(cat) else expanded.add(cat); a.render() }
            c.addView(hr)
            if (!open) continue
            defs.forEach { def -> fieldRow(a, c, def, profile, neededKeys) }
        }

        ui.fullButton(page, ui.textButton("Apagar todo o perfil", Palette.Red) {
            a.dialogOpen = true
            AlertDialog.Builder(a)
                .setTitle("Apagar perfil?")
                .setMessage("Todos os seus dados serão removidos deste aparelho. Exporte antes se quiser guardar uma cópia.")
                .setPositiveButton("APAGAR") { _, _ -> AppGraph.profile.clear() }
                .setNegativeButton("Cancelar", null)
                .setOnDismissListener { a.dialogOpen = false; a.render() }
                .show()
        }.apply { setPadding(ui.dp(24), ui.dp(16), 0, 0) }, top = 12)
    }

    private fun fieldRow(a: MainActivity, parent: LinearLayout, def: FieldDef, profile: UserProfile, needed: Set<String>) {
        val ui = a.ui
        val status = profile.status(def.key, needed)
        val r = profile.resolve(def.key)
        val row = ui.row().apply { setPadding(0, ui.dp(10), 0, ui.dp(10)) }
        row.addView(ui.text(status.symbol, 16f, Palette.status(status), bold = true), ui.lp(ui.dp(26)))
        val col = ui.column()
        col.addView(ui.text(def.label + if (def.sensitive) " 🔒" else "", 14.5f))
        val shown = when (r.origin) {
            DataOrigin.PROVIDED -> ProfileLearning.display(def, r.value)
            DataOrigin.DERIVED -> "${ProfileLearning.display(def, r.value)} (derivado)"
            DataOrigin.UNAVAILABLE -> if (status == FieldStatus.NEEDED) "NÃO INFORMADO — necessário" else "—"
        }
        col.addView(ui.text(shown, 12.5f, when (status) {
            FieldStatus.NEEDED -> Palette.Red
            FieldStatus.DERIVABLE -> Palette.Blue
            FieldStatus.INFORMED -> Palette.Green
            else -> Palette.Muted
        }, top = 2))
        row.addView(col, ui.lp(0, weight = 1f))
        row.setOnClickListener { editDialog(a, def) }
        parent.addView(row, ui.lp())
    }

    /** Scroll + padding para conteúdo de diálogos. */
    private fun dialogBody(a: MainActivity): Pair<ScrollView, LinearLayout> {
        val body = a.ui.column(20)
        val sv = ScrollView(a).apply { addView(body) }
        return sv to body
    }

    private fun show(a: MainActivity, b: AlertDialog.Builder): AlertDialog {
        a.dialogOpen = true
        val d = b.setOnDismissListener { a.dialogOpen = false; a.render() }.create()
        d.show()
        return d
    }

    fun editDialog(a: MainActivity, def: FieldDef) {
        val ui = a.ui
        val profile = AppGraph.profile.current
        val r = profile.resolve(def.key)
        val (sv, body) = dialogBody(a)
        body.addView(ui.muted(when (r.origin) {
            DataOrigin.PROVIDED -> "DADO FORNECIDO"
            DataOrigin.DERIVED -> "DADO DERIVADO: ${ProfileLearning.display(def, r.value)} (de ${r.source.removePrefix("derived:")})"
            DataOrigin.UNAVAILABLE -> "DADO NÃO DISPONÍVEL"
        }, 12f, top = 0))
        if (def.sensitive) body.addView(ui.text("🔒 Sensível: nunca é enviado a serviços de IA.", 12f, Palette.Amber, top = 4))
        val read = ui.valueInput(body, def, profile.raw(def.key))
        val b = AlertDialog.Builder(a)
            .setTitle(def.label)
            .setView(sv)
            .setPositiveButton("Salvar") { _, _ ->
                AppGraph.profile.set(def.key, read())
                AppGraph.knowledge.prune(AppGraph.profile.current)
            }
            .setNegativeButton("Cancelar", null)
        if (profile.raw(def.key) != null) {
            b.setNeutralButton(if (ProfileSchema.isCustom(def.key)) "Excluir" else "Limpar") { _, _ ->
                AppGraph.profile.update { it.without(def.key) }
            }
        }
        show(a, b)
    }

    private fun customFieldDialog(a: MainActivity) {
        val ui = a.ui
        val (sv, body) = dialogBody(a)
        var label = ""
        var value = ""
        ui.labeledEdit(body, "Nome do campo (ex.: Time de futebol)", "", "") { label = it }
        ui.labeledEdit(body, "Valor", "", "") { value = it }
        body.addView(ui.muted("Campos personalizados também são usados para responder perguntas semelhantes.", 12f, top = 8))
        show(a, AlertDialog.Builder(a)
            .setTitle("Novo campo personalizado")
            .setView(sv)
            .setPositiveButton("Adicionar") { _, _ ->
                if (label.isNotBlank() && value.isNotBlank()) AppGraph.profile.update { it.withCustom(label.trim(), value.trim()) }
                else a.toast("Preencha nome e valor")
            }
            .setNegativeButton("Cancelar", null))
    }

    /** Responder uma pergunta pendente e salvá-la no perfil (Seções 6 e 7). */
    private fun pendingDialog(a: MainActivity, p: PendingQuestion) {
        val ui = a.ui
        val profile = AppGraph.profile.current
        val candidates = (listOfNotNull(p.fieldKey) + AppGraph.classifier().classify(p.question, p.options).map { it.key })
            .distinct().mapNotNull { profile.fieldDef(it) }.filter { it.key != "observacoes" }
        val (sv, body) = dialogBody(a)
        body.addView(ui.text("\"${p.question}\"", 15f, Palette.Red, bold = true))
        if (p.options.isNotEmpty()) body.addView(ui.muted("Opções vistas: " + p.options.joinToString(" · "), 12f, top = 6))

        var target: FieldDef? = candidates.firstOrNull()
        body.addView(ui.text("Salvar em qual campo?", 13.5f, Palette.Text, bold = true, top = 12))
        val labels = candidates.map { it.label } + "Campo personalizado (esta pergunta)"
        val inputHolder = ui.column()
        var reader: () -> String? = { null }
        fun rebuildInput() {
            inputHolder.removeAllViews()
            val def = target ?: FieldDef(ProfileSchema.customKey(p.question), p.question, Category.PERSONALIZADO, FieldType.TEXT)
            if (p.options.isNotEmpty() && def.type != FieldType.LIST) {
                inputHolder.addView(ui.text("Sua resposta:", 13.5f, Palette.Text, bold = true, top = 10))
                var picked: String? = null
                ui.radioGroup(inputHolder, p.options, -1) { i -> picked = p.options[i] }
                reader = { picked?.let { o -> ProfileLearning.valueFor(def, listOf(o), profile.raw(def.key)) ?: o } }
            } else {
                reader = ui.valueInput(inputHolder, def, null)
            }
        }
        ui.radioGroup(body, labels, 0) { i -> target = candidates.getOrNull(i); rebuildInput() }
        body.addView(inputHolder, ui.lp())
        rebuildInput()

        show(a, AlertDialog.Builder(a)
            .setTitle("INFORMAÇÃO NECESSÁRIA")
            .setView(sv)
            .setPositiveButton("Salvar no perfil") { _, _ ->
                val v = reader()
                if (v.isNullOrBlank()) { a.toast("Nenhuma resposta informada"); return@setPositiveButton }
                val t = target
                if (t == null) {
                    AppGraph.profile.update { it.withCustom(p.question, v) }
                    AppGraph.knowledge.learn(p.question, ProfileSchema.customKey(p.question))
                } else {
                    val existing = AppGraph.profile.current.raw(t.key)
                    if (ProfileLearning.isConflict(t, existing, v)) {
                        conflictDialog(a, t, existing!!, v) { AppGraph.profile.set(t.key, v) }
                    } else AppGraph.profile.set(t.key, v)
                    AppGraph.knowledge.learn(p.question, t.key)
                }
                AppGraph.knowledge.removePending(p.question)
            }
            .setNegativeButton("Cancelar", null))
    }

    /** ⚠ CONFLITO DE INFORMAÇÃO (Seção 26). Nada muda sem autorização. */
    fun conflictDialog(a: android.app.Activity, def: FieldDef, existing: String, newValue: String, onUpdate: () -> Unit) {
        AlertDialog.Builder(a)
            .setTitle("⚠ CONFLITO DE INFORMAÇÃO")
            .setMessage("${def.label}\n\nPerfil: ${ProfileLearning.display(def, existing)}\nNova resposta: ${ProfileLearning.display(def, newValue)}")
            .setPositiveButton("ATUALIZAR PERFIL") { _, _ -> onUpdate() }
            .setNegativeButton("MANTER PERFIL", null)
            .show()
    }
}
