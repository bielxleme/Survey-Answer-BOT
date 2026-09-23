package com.researchagent.autofill.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.researchagent.autofill.core.Confidence
import com.researchagent.autofill.core.FieldDef
import com.researchagent.autofill.core.FieldStatus
import com.researchagent.autofill.core.FieldType
import com.researchagent.autofill.core.Values

/** Paleta visual (tema escuro, igual ao app web). */
object Palette {
    val Bg = Color.parseColor("#0B1120")
    val Surface = Color.parseColor("#111827")
    val Card = Color.parseColor("#1E293B")
    val Text = Color.parseColor("#E2E8F0")
    val Muted = Color.parseColor("#94A3B8")
    val Green = Color.parseColor("#10B981")
    val Amber = Color.parseColor("#F59E0B")
    val Red = Color.parseColor("#EF4444")
    val Blue = Color.parseColor("#38BDF8")
    val Border = Color.parseColor("#334155")

    fun status(s: FieldStatus): Int = when (s) {
        FieldStatus.INFORMED -> Green
        FieldStatus.DERIVABLE -> Blue
        FieldStatus.NEEDED -> Red
        FieldStatus.NOT_CONFIGURED -> Muted
    }

    fun confidence(c: Confidence?): Int = when (c) {
        Confidence.HIGH -> Green
        Confidence.MEDIUM, Confidence.LOW -> Amber
        Confidence.UNKNOWN -> Red
        null -> Muted
    }
}

/**
 * Mini-toolkit de interface com Views nativas (sem dependências externas),
 * para telas consistentes, rápidas e leves.
 */
class Ui(val ctx: Context) {
    private val density = ctx.resources.displayMetrics.density
    fun dp(v: Int): Int = (v * density).toInt()

    fun rounded(color: Int, radius: Int = 14, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(color)
        if (stroke != null) setStroke(dp(1), stroke)
    }

    fun column(padding: Int = 0): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    }

    fun row(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun lp(width: Int = ViewGroup.LayoutParams.MATCH_PARENT, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
           weight: Float = 0f, top: Int = 0, bottom: Int = 0, left: Int = 0, right: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(width, height, weight).apply { setMargins(dp(left), dp(top), dp(right), dp(bottom)) }

    /** Cartão de seção com título opcional. Retorna o container interno. */
    fun card(parent: LinearLayout, title: String? = null, border: Int? = null): LinearLayout {
        val c = column(16).apply { background = rounded(Palette.Card, 14, border) }
        parent.addView(c, lp(top = 6, bottom = 6, left = 12, right = 12))
        if (title != null) c.addView(text(title, 16f, Palette.Text, bold = true))
        return c
    }

    fun text(t: CharSequence, size: Float = 14f, color: Int = Palette.Text, bold: Boolean = false, top: Int = 0): TextView =
        TextView(ctx).apply {
            text = t
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(top), 0, 0)
            setLineSpacing(0f, 1.1f)
        }

    fun muted(t: CharSequence, size: Float = 12.5f, top: Int = 4): TextView = text(t, size, Palette.Muted, top = top)

    fun button(label: String, color: Int = Palette.Green, textColor: Int = Color.WHITE, outlined: Boolean = false,
               onClick: () -> Unit): Button = Button(ctx).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(if (outlined) color else textColor)
        background = if (outlined) rounded(Color.TRANSPARENT, 12, color) else rounded(color, 12)
        minHeight = dp(44)
        minimumHeight = dp(44)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setOnClickListener { onClick() }
    }

    fun textButton(label: String, color: Int = Palette.Green, onClick: () -> Unit): TextView = text(label, 14f, color, bold = true).apply {
        setPadding(dp(8), dp(10), dp(8), dp(10))
        setOnClickListener { onClick() }
    }

    /** Linha com botões dividindo a largura. */
    fun buttonRow(parent: LinearLayout, vararg buttons: View, top: Int = 8) {
        val r = row()
        buttons.forEachIndexed { i, b ->
            r.addView(b, lp(0, weight = 1f, left = if (i == 0) 0 else 4, right = if (i == buttons.size - 1) 0 else 4))
        }
        parent.addView(r, lp(top = top))
    }

    fun fullButton(parent: LinearLayout, b: View, top: Int = 8) = parent.addView(b, lp(top = top))

    fun editText(value: String?, hint: String, type: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                 multiline: Boolean = false, onChange: (String) -> Unit): EditText = EditText(ctx).apply {
        setText(value.orEmpty())
        this.hint = hint
        setHintTextColor(Palette.Muted)
        setTextColor(Palette.Text)
        textSize = 15f
        inputType = if (multiline) type or InputType.TYPE_TEXT_FLAG_MULTI_LINE else type
        if (!multiline) setSingleLine(true)
        background = rounded(Palette.Surface, 10, Palette.Border)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { onChange(s?.toString().orEmpty()) }
        })
    }

    fun labeledEdit(parent: LinearLayout, label: String, value: String?, hint: String = "",
                    type: Int = InputType.TYPE_CLASS_TEXT, multiline: Boolean = false, onChange: (String) -> Unit): EditText {
        parent.addView(muted(label, 12.5f, top = 8))
        val e = editText(value, hint, type, multiline, onChange)
        parent.addView(e, lp(top = 4))
        return e
    }

    private val tint = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(Palette.Green, Palette.Muted)
    )

    fun radio(label: String, subtitle: String? = null): RadioButton = RadioButton(ctx).apply {
        text = if (subtitle != null) "$label\n$subtitle" else label
        setTextColor(Palette.Text)
        textSize = 14.5f
        buttonTintList = tint
        setPadding(dp(4), dp(6), 0, dp(6))
    }

    /** Grupo de rádio; [selected] = índice selecionado (-1 nenhum). */
    fun radioGroup(parent: LinearLayout, labels: List<String>, selected: Int, subtitles: List<String?>? = null,
                   onSelect: (Int) -> Unit): RadioGroup {
        val g = RadioGroup(ctx)
        labels.forEachIndexed { i, l ->
            val rb = radio(l, subtitles?.getOrNull(i))
            rb.id = View.generateViewId()
            g.addView(rb)
            if (i == selected) rb.isChecked = true
            rb.setOnCheckedChangeListener { _, checked -> if (checked) onSelect(i) }
        }
        parent.addView(g, lp(top = 4))
        return g
    }

    fun checkbox(label: String, checked: Boolean, onChange: (Boolean) -> Unit): CheckBox = CheckBox(ctx).apply {
        text = label
        isChecked = checked
        setTextColor(Palette.Text)
        textSize = 14.5f
        buttonTintList = tint
        setOnCheckedChangeListener { _: CompoundButton, c: Boolean -> onChange(c) }
    }

    @Suppress("DEPRECATION")
    fun switchRow(parent: LinearLayout, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
        val r = row()
        val texts = column()
        texts.addView(text(title, 14.5f))
        if (subtitle != null) texts.addView(muted(subtitle, 12f, top = 2))
        r.addView(texts, lp(0, weight = 1f))
        val sw = Switch(ctx).apply {
            isChecked = checked
            thumbTintList = tint
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }
        r.addView(sw, lp(ViewGroup.LayoutParams.WRAP_CONTENT))
        parent.addView(r, lp(top = 8))
    }

    fun slider(parent: LinearLayout, min: Int, max: Int, value: Int, onChange: (Int) -> Unit): SeekBar {
        val sb = SeekBar(ctx).apply {
            this.max = max - min
            progress = (value - min).coerceIn(0, max - min)
            progressTintList = ColorStateList.valueOf(Palette.Green)
            thumbTintList = ColorStateList.valueOf(Palette.Green)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p + min) }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        parent.addView(sb, lp(top = 6))
        return sb
    }

    /** Android 15 (targetSdk 35) desenha de ponta a ponta: aplica as margens das barras do sistema e do teclado. */
    @Suppress("DEPRECATION")
    fun applySystemInsets(v: View) {
        v.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val b = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.ime())
                view.setPadding(b.left, b.top, b.right, b.bottom)
            } else {
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        v.requestApplyInsets()
    }

    fun divider(parent: LinearLayout) {
        parent.addView(View(ctx).apply { setBackgroundColor(Palette.Border) }, lp(height = dp(1).coerceAtLeast(1), top = 8, bottom = 8))
    }

    fun statRow(parent: LinearLayout, label: String, value: String, valueColor: Int = Palette.Text) {
        val r = row()
        r.addView(text(label, 14f, Palette.Muted), lp(0, weight = 1f))
        r.addView(text(value, 14f, valueColor, bold = true), lp(ViewGroup.LayoutParams.WRAP_CONTENT))
        parent.addView(r, lp(top = 4))
    }

    /**
     * Editor de valor conforme o tipo do campo. Retorna um "leitor" do valor no formato de armazenamento
     * (booleanos "true"/"false", datas ISO, listas separadas por ";"), null = vazio.
     */
    fun valueInput(parent: LinearLayout, def: FieldDef, initial: String?): () -> String? {
        var current: String? = initial
        when (def.type) {
            FieldType.BOOLEAN -> {
                val b = Values.parseBoolean(initial)
                radioGroup(parent, listOf("Sim", "Não", "Não informar"), when (b) { true -> 0; false -> 1; null -> 2 }) { i ->
                    current = when (i) { 0 -> "true"; 1 -> "false"; else -> null }
                }
            }
            FieldType.CHOICE -> {
                val idx = def.options.indexOf(initial)
                var other: EditText? = null
                radioGroup(parent, def.options, idx) { i -> current = def.options[i]; other?.setText("") }
                other = labeledEdit(parent, "Outro (digite)", if (idx < 0) initial else "", "") { t ->
                    if (t.isNotBlank()) current = t.trim()
                }
            }
            FieldType.DATE -> {
                val shown = Values.parseDate(initial)?.let { Values.formatDate(it) } ?: initial.orEmpty()
                labeledEdit(parent, "Data (dd/mm/aaaa)", shown, "01/01/1990", InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE) { t ->
                    current = Values.toIsoDate(t) ?: t.trim().ifBlank { null }
                }
            }
            FieldType.NUMBER -> labeledEdit(parent, def.label, initial, "Número",
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { t -> current = t.trim().ifBlank { null } }
            FieldType.LIST -> labeledEdit(parent, "Itens separados por ;", initial, "Ex.: Netflix; Prime Video", multiline = true) { t ->
                current = t.trim().ifBlank { null }
            }
            FieldType.TEXT -> labeledEdit(parent, def.label, initial, "") { t -> current = t.trim().ifBlank { null } }
        }
        return { current }
    }
}
