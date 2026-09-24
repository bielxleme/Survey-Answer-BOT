package com.researchagent.autofill.overlay

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.researchagent.autofill.AppGraph
import com.researchagent.autofill.accessibility.SurveyAccessibilityService
import com.researchagent.autofill.automation.AgentController
import com.researchagent.autofill.automation.InterventionBus
import com.researchagent.autofill.automation.Observer
import com.researchagent.autofill.core.AgentMode
import com.researchagent.autofill.core.AgentState
import com.researchagent.autofill.core.AgentStatus
import com.researchagent.autofill.core.Confidence
import com.researchagent.autofill.core.ConfidenceBands
import com.researchagent.autofill.core.Intervention
import com.researchagent.autofill.core.InterventionReason
import com.researchagent.autofill.core.InterventionResult
import com.researchagent.autofill.ui.InterventionActivity
import com.researchagent.autofill.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Bolha flutuante + painel de controle + cartão de intervenção (Seções 1, 15 e 19).
 * Usa TYPE_ACCESSIBILITY_OVERLAY — disponível somente para o serviço de acessibilidade.
 */
class OverlayController(private val service: SurveyAccessibilityService) {

    private val wm = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private lateinit var bubble: FrameLayout
    private lateinit var bubbleDot: View
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var panel: View? = null
    private var card: View? = null
    private var cardMinimizedFor: Long = -1
    private var pulse: ObjectAnimator? = null
    private val jobs = ArrayList<Job>()
    private var lastStatus = AgentStatus()

    // ── cores ─────────────────────────────────────────────────────────
    private val cBg = Color.parseColor("#F20F172A")
    private val cCard = Color.parseColor("#FF1E293B")
    private val cText = Color.parseColor("#FFE2E8F0")
    private val cMuted = Color.parseColor("#FF94A3B8")
    private val cGreen = Color.parseColor("#FF10B981")
    private val cAmber = Color.parseColor("#FFF59E0B")
    private val cRed = Color.parseColor("#FFEF4444")
    private val cIdle = Color.parseColor("#FF64748B")
    private val cBlue = Color.parseColor("#FF3B82F6")
    private val cPurple = Color.parseColor("#FF8B5CF6")

    private fun overlayParams(w: Int, h: Int, focusable: Boolean = false) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    fun show() {
        createBubble()
        jobs += service.scope.launch { AgentController.status.collect { onStatus(it) } }
        jobs += service.scope.launch { InterventionBus.current.collect { onIntervention(it?.intervention) } }
        jobs += service.scope.launch { Observer.mode.collect { onStatus(lastStatus) } }
        jobs += service.scope.launch { Observer.recordedCount.collect { if (panel != null && !confirmingExit) renderPanel() } }
        jobs += service.scope.launch { AgentController.flowProgress.collect { onStatus(lastStatus) } }
    }

    fun dispose() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        pulse?.cancel()
        removeSafely(card); card = null
        removeSafely(panel); panel = null
        if (::bubble.isInitialized) removeSafely(bubble)
    }

    private fun removeSafely(v: View?) {
        if (v == null) return
        try { wm.removeView(v) } catch (_: Exception) { }
    }

    // ── Bolha ─────────────────────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private fun createBubble() {
        bubble = FrameLayout(service)
        bubbleDot = View(service).apply { background = circle(cIdle) }
        val label = TextView(service).apply {
            text = "RA"; setTextColor(Color.WHITE); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        bubble.addView(bubbleDot, FrameLayout.LayoutParams(dp(52), dp(52)))
        bubble.addView(label, FrameLayout.LayoutParams(dp(52), dp(52)))
        bubble.contentDescription = "Research Agent"
        bubbleParams = overlayParams(dp(52), dp(52)).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = service.resources.displayMetrics.heightPixels / 3
        }
        bubble.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { sx = bubbleParams.x; sy = bubbleParams.y; tx = e.rawX; ty = e.rawY; moved = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - tx; val dy = e.rawY - ty
                        if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                        if (moved) {
                            bubbleParams.x = sx + dx.toInt(); bubbleParams.y = sy + dy.toInt()
                            try { wm.updateViewLayout(bubble, bubbleParams) } catch (_: Exception) { }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) togglePanel() else snapToEdge()
                    }
                }
                return true
            }
        })
        wm.addView(bubble, bubbleParams)
    }

    private fun snapToEdge() {
        val w = service.resources.displayMetrics.widthPixels
        val target = if (bubbleParams.x + dp(26) < w / 2) 0 else w - dp(52)
        ValueAnimator.ofInt(bubbleParams.x, target).apply {
            duration = 180
            addUpdateListener {
                bubbleParams.x = it.animatedValue as Int
                try { wm.updateViewLayout(bubble, bubbleParams) } catch (_: Exception) { }
            }
            start()
        }
    }

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(2), Color.parseColor("#66FFFFFF"))
    }

    private fun rounded(color: Int, stroke: Int? = null) = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setColor(color)
        if (stroke != null) setStroke(dp(2), stroke)
    }

    private fun onStatus(s: AgentStatus) {
        lastStatus = s
        val color = when {
            s.intervention != null || s.state == AgentState.USER_INTERVENTION_REQUIRED || InterventionBus.current.value != null -> cRed
            AgentController.flowProgress.value != null -> cPurple
            s.paused -> cAmber
            s.running -> cGreen
            Observer.mode.value != Observer.Mode.OFF -> cBlue
            else -> cIdle
        }
        bubbleDot.background = circle(color)
        val shouldPulse = color == cRed || color == cPurple || (s.running && !s.paused)
        if (shouldPulse && pulse == null) {
            pulse = ObjectAnimator.ofFloat(bubbleDot, "alpha", 1f, 0.45f).apply {
                duration = if (color == cRed) 450 else 900
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        } else if (!shouldPulse) {
            pulse?.cancel(); pulse = null; bubbleDot.alpha = 1f
        }
        if (panel != null && !confirmingExit) renderPanel()
    }

    // ── Painel / menu da bolha (Seções 19 e 20) ──────────────────────
    private var panelParams: WindowManager.LayoutParams? = null

    private fun togglePanel() {
        if (panel != null) { removeSafely(panel); panel = null; panelParams = null; return }
        panel = ScrollView(service)
        val params = overlayParams(dp(300), WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            val w = service.resources.displayMetrics.widthPixels
            x = if (bubbleParams.x < w / 2) dp(60) else (w - dp(300) - dp(60)).coerceAtLeast(0)
            y = dp(24)
        }
        panelParams = params
        renderPanel()
        try { wm.addView(panel, params) } catch (_: Exception) { panel = null; panelParams = null }
    }

    /** Intervenção cujo cartão foi ocultado para o usuário resolver na tela. */
    private fun minimizedIntervention(): Intervention? =
        InterventionBus.current.value?.intervention?.takeIf { it.id == cardMinimizedFor }

    private fun renderPanel() {
        val root = panel as? ScrollView ?: return
        root.removeAllViews()
        val s = lastStatus
        val stats = AppGraph.stats.stats.value
        val settings = AppGraph.settings.current
        val obsMode = Observer.mode.value
        val flowMsg = AgentController.flowProgress.value
        val col = column(cBg, null)

        col.addView(text("RESEARCH AGENT", 15f, cText, bold = true))
        val (dot, stateLabel) = when {
            s.intervention != null -> "🔴" to "AGUARDANDO VOCÊ"
            flowMsg != null -> "🟣" to "EXECUTANDO AUTOMAÇÃO"
            s.paused -> "🟡" to "PAUSADO"
            s.running -> "🟢" to s.mode.label.uppercase()
            obsMode != Observer.Mode.OFF -> "🔵" to "OBSERVANDO (${obsMode.label})"
            AgentController.serviceConnected.value -> "●" to "Pronto"
            else -> "○" to "Serviço desconectado"
        }
        col.addView(text("$dot $stateLabel", 14f, cText, bold = true, top = 6))

        // ── Resolver na tela: o cartão foi ocultado ──
        minimizedIntervention()?.let { i ->
            col.addView(text("⚠ ${i.reason.title}", 13f, cRed, bold = true, top = 8))
            i.question?.let { q -> col.addView(text("\"${q.text.take(100)}\"", 12f, cText)) }
            col.addView(text("Responda no app e toque em JÁ RESOLVI — vou conferir a tela antes de continuar.", 12f, cMuted))
            col.addView(button("✓ JÁ RESOLVI", cGreen) { InterventionBus.respond(i.id, InterventionResult.Resume); closePanel() })
            val r = row()
            r.addView(button("MOSTRAR AVISO", cCard) { cardMinimizedFor = -1; closePanel(); onIntervention(i) })
            r.addView(button("IGNORAR", cCard) { InterventionBus.respond(i.id, InterventionResult.Skip) })
            col.addView(r)
        }

        if (s.running) {
            col.addView(text(s.state.label + if (s.message.isNotBlank() && s.message != s.state.label) " — ${s.message}" else "", 12f, cMuted, top = 4))
            col.addView(text("Pesquisa: ${s.surveyIndex}" + (s.pageProgress?.let { "   Página: ${it.first}/${it.second}" } ?: ""), 13f, cText, top = 6))
            if (s.questionsOnPage > 0) col.addView(text("Pergunta: ${s.questionIndex.coerceAtMost(s.questionsOnPage)}/${s.questionsOnPage}", 13f, cText))
            val conf = s.confidence.asMap().filterValues { it > 0 }
            if (conf.isNotEmpty()) {
                val bands = ConfidenceBands(settings.bandHigh, settings.bandGood, settings.bandMid)
                col.addView(text("Confiança: " + conf.entries.joinToString("  ") { "${it.key} ${"%.0f".format(it.value * 100)}% (${bands.label(it.value)})" }, 11f, cMuted, top = 4))
            }
        }
        flowMsg?.let { col.addView(text("⚙️ $it", 12f, cText, top = 4)) }
        if (obsMode != Observer.Mode.OFF) {
            val last = Observer.lastLearned.value
            if (obsMode == Observer.Mode.RECORD) col.addView(text("⏺ Gravando: ${Observer.recordedCount.value} ação(ões)", 12f, cRed, top = 4))
            if (last.isNotBlank()) col.addView(text("🧠 $last", 11f, cMuted, top = 2))
        }
        col.addView(text("Concluídas: ${stats.surveysCompleted}   Pendentes no perfil: ${AppGraph.knowledge.pending.value.size}", 12f, cMuted, top = 6))

        if (s.mode == AgentMode.MANUAL && s.suggestions.isNotEmpty()) {
            col.addView(text("SUGESTÕES", 12f, cMuted, bold = true, top = 10))
            s.suggestions.take(6).forEach { sg ->
                val c = when (sg.level) { Confidence.HIGH -> cGreen; Confidence.MEDIUM -> cAmber; else -> cRed }
                col.addView(text("${sg.level.emoji} ${sg.question.take(60)}", 12f, cText, top = 4))
                col.addView(text("   → ${sg.answer.take(60)}", 12f, c))
            }
        }

        // 🔍 Ativar pesquisa / ⏸ Pausar / ▶ Continuar
        when {
            !s.running -> col.addView(button("🔍 ATIVAR PESQUISA", cGreen) {
                if (!AgentController.start()) toastLike("Serviço de acessibilidade indisponível")
            })
            s.paused -> col.addView(button("▶ CONTINUAR", cGreen) { AgentController.resume() })
            else -> col.addView(button("⏸ PAUSAR (mantém o progresso)", cAmber) { AgentController.pause() })
        }
        if (s.intervention != null && minimizedIntervention() == null) {
            col.addView(button("CONTINUAR AUTOMAÇÃO", cGreen) { AgentController.resume() })
        }

        // 🧠 Ensinar automação
        if (obsMode == Observer.Mode.TEACH || obsMode == Observer.Mode.AUTO) {
            col.addView(button("⏹ PARAR DE OBSERVAR", cBlue) { AgentController.stopObserving(); toastLike("Observação encerrada") })
        } else if (obsMode == Observer.Mode.OFF) {
            col.addView(button("🧠 ENSINAR AUTOMAÇÃO", cBlue) {
                AgentController.startTeach()
                toastLike("Observando: faça a pesquisa normalmente. Vou aprender com seus toques.")
                closePanel()
            })
        }

        // ⚙️ Automatizar operação
        if (obsMode == Observer.Mode.RECORD) {
            col.addView(button("⏹ SALVAR OPERAÇÃO (${Observer.recordedCount.value})", cRed) {
                val name = AgentController.stopObserving()
                toastLike(if (name != null) "Automação salva: $name" else "Nada foi gravado")
            })
        } else if (AgentController.isFlowRunning) {
            col.addView(button("⏹ CANCELAR AUTOMAÇÃO", cRed) { AgentController.cancelFlow() })
        } else {
            col.addView(button("⚙️ AUTOMATIZAR OPERAÇÃO (gravar)", cPurple) {
                AgentController.startRecord()
                toastLike("Gravando: execute a operação no app. Depois toque na bolha → SALVAR OPERAÇÃO.")
                closePanel()
            })
            val flows = AgentController.flowsForCurrentApp()
            flows.take(4).forEach { f ->
                col.addView(button("▶ ${f.name.take(26)} · ${"%.0f".format(f.confidence * 100)}%", cCard) {
                    closePanel()
                    if (!AgentController.runFlow(f)) toastLike("Não foi possível iniciar a automação")
                })
            }
        }

        // 🎯 Chutar respostas
        col.addView(button("🎯 CHUTAR RESPOSTAS: ${if (settings.guessMode) "LIGADO" else "DESLIGADO"}", if (settings.guessMode) cAmber else cCard) {
            val on = AgentController.toggleGuess()
            toastLike(if (on) "Chutes serão registrados como TENTATIVA" else "Chutar respostas desligado")
            renderPanel()
        })

        val row2 = row()
        row2.addView(button("MODO: ${s.mode.label}", cCard) {
            val next = AgentMode.values()[(s.mode.ordinal + 1) % AgentMode.values().size]
            AgentController.setMode(next)
        })
        if (s.running) row2.addView(button("⏹ PARAR", cRed) { AgentController.stop() })
        col.addView(row2)
        val row3 = row()
        row3.addView(button("AJUSTES", cCard) { openApp(MainActivity.TAB_SETTINGS) })
        row3.addView(button("LOGS", cCard) { openApp(MainActivity.TAB_LOGS) })
        row3.addView(button("🧠", cCard) { openApp(MainActivity.TAB_LEARNING) })
        col.addView(row3)
        val row4 = row()
        row4.addView(button("FECHAR MENU", cCard) { closePanel() })
        row4.addView(button("❌ ENCERRAR APP", cRed) { confirmShutdown() })
        col.addView(row4)
        root.addView(col)
        fitPanel(col)
    }

    /** Limita a altura do menu à tela (rolável) sem bloquear toques fora dele. */
    private fun fitPanel(col: View) {
        val params = panelParams ?: return
        col.measure(
            View.MeasureSpec.makeMeasureSpec(dp(300), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val maxH = (service.resources.displayMetrics.heightPixels * 0.85).toInt()
        params.height = if (col.measuredHeight > maxH) maxH else WindowManager.LayoutParams.WRAP_CONTENT
        panel?.let { p -> if (p.isAttachedToWindow) try { wm.updateViewLayout(p, params) } catch (_: Exception) { } }
    }

    private fun closePanel() { if (panel != null) togglePanel() }

    /** ❌ ENCERRAR APLICATIVO — pede confirmação no próprio menu (sem diálogos do sistema). */
    private var confirmingExit = false
    private fun confirmShutdown() {
        val root = panel as? ScrollView ?: return
        if (confirmingExit) return
        confirmingExit = true
        root.removeAllViews()
        val col = column(cBg, cRed)
        col.addView(text("Encerrar o aplicativo?", 15f, cText, bold = true))
        col.addView(text("A automação para, tarefas pendentes são canceladas, a observação é desligada e a bolha some. " +
            "Para usar de novo, abra o Research Agent.", 12f, cMuted, top = 4))
        val r = row()
        r.addView(button("CANCELAR", cCard) { confirmingExit = false; renderPanel() })
        r.addView(button("ENCERRAR", cRed) {
            confirmingExit = false
            AgentController.shutdown() // remove a bolha (dispose) e fecha as telas do app
        })
        col.addView(r)
        root.addView(col)
        fitPanel(col)
    }

    // ── Cartão de intervenção (Seções 12, 13, 15) ─────────────────────
    private fun onIntervention(i: Intervention?) {
        removeSafely(card); card = null
        if (i == null) { cardMinimizedFor = -1; if (panel != null) renderPanel(); return }
        if (i.id == cardMinimizedFor) return
        val col = column(cBg, cRed)
        val header = row()
        header.addView(text("⚠ AÇÃO NECESSÁRIA", 15f, cRed, bold = true))
        col.addView(header)
        col.addView(text("Motivo: ${i.reason.title}", 13f, cText, bold = true, top = 4))
        i.question?.let { col.addView(text("\"${it.text.take(140)}\"", 13f, cRed, top = 4)) }
        col.addView(text(i.message.take(220), 12f, cMuted, top = 4))

        when (i.reason) {
            InterventionReason.MISSING_INFO -> {
                // Resolver direto no app da pesquisa: esconde a caixa, o app fica 100% utilizável
                col.addView(button("✋ RESOLVER E RESPONDER AGORA (na tela)", cGreen) { minimize(i) })
                i.suggestion?.takeIf { !it.needsUser && it.answers.isNotEmpty() }?.let { sug ->
                    col.addView(button("USAR: ${sug.display.take(28)} ${sug.level.emoji}", cAmber) {
                        InterventionBus.respond(i.id, InterventionResult.Answered(sug.answers))
                    })
                }
                val r = row()
                r.addView(button("✓ JÁ RESOLVI", cCard) { InterventionBus.respond(i.id, InterventionResult.Resume) })
                r.addView(button("DIGITAR AQUI", cCard) { minimize(i, silent = true); openIntervention() })
                col.addView(r)
                col.addView(button("IGNORAR ESTA PERGUNTA", cCard) { InterventionBus.respond(i.id, InterventionResult.Skip) })
            }
            InterventionReason.CONFIRM_NEXT -> {
                val r = row()
                r.addView(button("AVANÇAR", cGreen) { InterventionBus.respond(i.id, InterventionResult.Resume) })
                r.addView(button("AGUARDAR", cCard) { InterventionBus.respond(i.id, InterventionResult.Skip) })
                col.addView(r)
            }
            else -> {
                col.addView(button("✋ RESOLVER NA TELA", cAmber) { minimize(i) })
                val r = row()
                r.addView(button("✓ JÁ RESOLVI", cGreen) { InterventionBus.respond(i.id, InterventionResult.Resume) })
                r.addView(button("IGNORAR", cCard) { InterventionBus.respond(i.id, InterventionResult.Skip) })
                col.addView(r)
            }
        }
        val r2 = row()
        r2.addView(button("✕ FECHAR AVISO", cCard) { minimize(i) })
        r2.addView(button("PARAR", cRed) { AgentController.stop() })
        col.addView(r2)

        val params = overlayParams(
            (service.resources.displayMetrics.widthPixels - dp(24)).coerceAtMost(dp(420)),
            WindowManager.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(36)
        }
        card = col
        try { wm.addView(col, params) } catch (_: Exception) { card = null }
    }

    /**
     * Oculta a caixa TEMPORARIAMENTE (não responde, não para, não fecha nenhum app).
     * A bolha fica vermelha; tocar nela mostra "✓ JÁ RESOLVI".
     */
    private fun minimize(i: Intervention, silent: Boolean = false) {
        cardMinimizedFor = i.id
        removeSafely(card); card = null
        if (!silent) toastLike("Resolva no app. Depois toque na bolha → ✓ JÁ RESOLVI")
        onStatus(lastStatus)
    }

    private fun openIntervention() {
        service.startActivity(Intent(service, InterventionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openApp(tab: Int) {
        if (panel != null) togglePanel()
        service.startActivity(
            Intent(service, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(MainActivity.EXTRA_TAB, tab)
        )
    }

    private fun toastLike(msg: String) {
        android.widget.Toast.makeText(service, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    // ── fábrica de views ─────────────────────────────────────────────
    private fun column(bg: Int, stroke: Int?) = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(bg, stroke)
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }

    /** Linha horizontal: os botões adicionados dividem a largura igualmente. */
    private inner class Row(ctx: android.content.Context) : LinearLayout(ctx) {
        init {
            orientation = HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        override fun addView(child: View) {
            super.addView(child, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(3), dp(8), dp(3), 0)
            })
        }
    }

    private fun row(): LinearLayout = Row(service)

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0) = TextView(service).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(top), 0, 0)
    }

    private fun button(label: String, color: Int, onClick: () -> Unit) = Button(service).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(color)
        minHeight = dp(40)
        minimumHeight = dp(40)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(3), dp(8), dp(3), 0)
        }
    }
}
