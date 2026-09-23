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
import com.researchagent.autofill.core.AgentMode
import com.researchagent.autofill.core.AgentState
import com.researchagent.autofill.core.AgentStatus
import com.researchagent.autofill.core.Confidence
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
            s.intervention != null || s.state == AgentState.USER_INTERVENTION_REQUIRED -> cRed
            s.paused -> cAmber
            s.running -> cGreen
            else -> cIdle
        }
        bubbleDot.background = circle(color)
        val shouldPulse = color == cRed || (s.running && !s.paused)
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
        if (panel != null) renderPanel()
    }

    // ── Painel (Seção 19) ────────────────────────────────────────────
    private fun togglePanel() {
        if (panel != null) { removeSafely(panel); panel = null; return }
        panel = ScrollView(service)
        renderPanel()
        val params = overlayParams(dp(290), WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            val w = service.resources.displayMetrics.widthPixels
            x = if (bubbleParams.x < w / 2) dp(60) else (w - dp(290) - dp(60)).coerceAtLeast(0)
            y = (bubbleParams.y - dp(40)).coerceAtLeast(dp(24))
        }
        wm.addView(panel, params)
    }

    private fun renderPanel() {
        val root = panel as? ScrollView ?: return
        root.removeAllViews()
        val s = lastStatus
        val stats = AppGraph.stats.stats.value
        val col = column(cBg, null)

        col.addView(text("RESEARCH AGENT", 15f, cText, bold = true))
        val (dot, stateLabel) = when {
            s.intervention != null -> "🔴" to "AGUARDANDO VOCÊ"
            s.paused -> "🟡" to "PAUSADO"
            s.running -> "🟢" to s.mode.label.uppercase()
            AgentController.serviceConnected.value -> "●" to "Pronto"
            else -> "○" to "Serviço desconectado"
        }
        col.addView(text("$dot $stateLabel", 14f, cText, bold = true, top = 6))
        if (s.running) {
            col.addView(text(s.state.label + if (s.message.isNotBlank() && s.message != s.state.label) " — ${s.message}" else "", 12f, cMuted, top = 4))
            col.addView(text("Pesquisa: ${s.surveyIndex}" + (s.pageProgress?.let { "   Página: ${it.first}/${it.second}" } ?: ""), 13f, cText, top = 6))
            if (s.questionsOnPage > 0) col.addView(text("Pergunta: ${s.questionIndex.coerceAtMost(s.questionsOnPage)}/${s.questionsOnPage}", 13f, cText))
        }
        col.addView(text("Pesquisas concluídas: ${stats.surveysCompleted}", 13f, cText, top = 8))
        col.addView(text("Aguardando usuário: ${if (s.intervention != null) 1 else 0}   Pendentes no perfil: ${AppGraph.knowledge.pending.value.size}", 12f, cMuted))

        if (s.mode == AgentMode.MANUAL && s.suggestions.isNotEmpty()) {
            col.addView(text("SUGESTÕES", 12f, cMuted, bold = true, top = 10))
            s.suggestions.take(6).forEach { sg ->
                val c = when (sg.level) { Confidence.HIGH -> cGreen; Confidence.MEDIUM -> cAmber; else -> cRed }
                col.addView(text("${sg.level.emoji} ${sg.question.take(60)}", 12f, cText, top = 4))
                col.addView(text("   → ${sg.answer.take(60)}", 12f, c))
            }
        }

        // Botões: ATIVAR / PAUSAR / CONTINUAR / PARAR
        val row1 = row()
        when {
            !s.running -> row1.addView(button("ATIVAR PESQUISA", cGreen) {
                if (!AgentController.start()) toastLike("Serviço de acessibilidade indisponível")
            })
            s.paused -> row1.addView(button("CONTINUAR", cGreen) { AgentController.resume() })
            else -> row1.addView(button("PAUSAR", cAmber) { AgentController.pause() })
        }
        if (s.running) row1.addView(button("PARAR", cRed) { AgentController.stop() })
        col.addView(row1)

        if (s.intervention != null) {
            col.addView(button("CONTINUAR AUTOMAÇÃO", cGreen) { AgentController.resume() })
        }

        val row2 = row()
        row2.addView(button("MODO: ${s.mode.label}", cCard) {
            val next = AgentMode.values()[(s.mode.ordinal + 1) % AgentMode.values().size]
            AgentController.setMode(next)
        })
        col.addView(row2)
        val row3 = row()
        row3.addView(button("CONFIGURAÇÕES", cCard) { openApp(MainActivity.TAB_SETTINGS) })
        row3.addView(button("LOGS", cCard) { openApp(MainActivity.TAB_LOGS) })
        col.addView(row3)
        col.addView(button("FECHAR", cCard) { togglePanel() })
        root.addView(col)
    }

    // ── Cartão de intervenção (Seção 15) ─────────────────────────────
    private fun onIntervention(i: Intervention?) {
        removeSafely(card); card = null
        if (i == null || i.id == cardMinimizedFor) return
        val col = column(cBg, cRed)
        col.addView(text("⚠ AÇÃO NECESSÁRIA", 15f, cRed, bold = true))
        col.addView(text("O agente precisa de você.", 13f, cText, top = 2))
        col.addView(text("Motivo: ${i.reason.title}", 13f, cText, bold = true, top = 6))
        i.question?.let { col.addView(text("\"${it.text.take(140)}\"", 13f, cRed, top = 4)) }
        col.addView(text(i.message.take(220), 12f, cMuted, top = 4))

        when (i.reason) {
            InterventionReason.MISSING_INFO -> {
                col.addView(button("RESPONDER AGORA", cGreen) { openIntervention() })
                i.suggestion?.takeIf { !it.needsUser && it.answers.isNotEmpty() }?.let { sug ->
                    col.addView(button("USAR: ${sug.display.take(28)} ${sug.level.emoji}", cAmber) {
                        InterventionBus.respond(i.id, InterventionResult.Answered(sug.answers))
                    })
                }
                val r = row()
                r.addView(button("JÁ RESPONDI", cCard) { InterventionBus.respond(i.id, InterventionResult.Resume) })
                r.addView(button("IGNORAR", cCard) { InterventionBus.respond(i.id, InterventionResult.Skip) })
                col.addView(r)
            }
            InterventionReason.CONFIRM_NEXT -> {
                val r = row()
                r.addView(button("AVANÇAR", cGreen) { InterventionBus.respond(i.id, InterventionResult.Resume) })
                r.addView(button("AGUARDAR", cCard) { InterventionBus.respond(i.id, InterventionResult.Skip) })
                col.addView(r)
            }
            else -> {
                val r = row()
                r.addView(button("RESOLVER", cAmber) {
                    // minimiza o cartão para o usuário agir na tela; a bolha fica vermelha
                    cardMinimizedFor = i.id
                    removeSafely(card); card = null
                })
                r.addView(button("CONTINUAR", cGreen) { InterventionBus.respond(i.id, InterventionResult.Resume) })
                col.addView(r)
                col.addView(button("IGNORAR ESTA ETAPA", cCard) { InterventionBus.respond(i.id, InterventionResult.Skip) })
            }
        }
        col.addView(button("PARAR AUTOMAÇÃO", cRed) { AgentController.stop() })

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
