package com.researchagent.autofill.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.researchagent.autofill.AppGraph
import com.researchagent.autofill.automation.AgentController
import com.researchagent.autofill.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * SurveyAccessibilityService (Seção 39).
 * Observa mudanças na tela, expõe a árvore de acessibilidade ao agente e hospeda a bolha
 * flutuante como TYPE_ACCESSIBILITY_OVERLAY (não exige a permissão "Sobrepor a outros apps").
 * Só age quando o usuário ativa o agente pela bolha.
 */
class SurveyAccessibilityService : AccessibilityService() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _events = MutableSharedFlow<Int>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    /** Pulsos de eventos de tela (usados para espera baseada em eventos — Seção 14). */
    val events: SharedFlow<Int> = _events.asSharedFlow()

    @Volatile var lastPackage: String = ""
        private set

    lateinit var driver: AccessibilityDriver
        private set
    private var overlay: OverlayController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        driver = AccessibilityDriver(this)
        if (AppGraph.settings.current.bubbleEnabled) showBubble()
        AgentController.onServiceConnected(this)
        Log.i(TAG, "Serviço de acessibilidade conectado")
    }

    fun showBubble() {
        if (overlay == null) overlay = OverlayController(this).also { it.show() }
    }

    fun hideBubble() {
        overlay?.dispose()
        overlay = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isNotEmpty() && pkg != packageName) lastPackage = pkg
        // Só repassa pulsos enquanto o agente estiver ativo (economia de bateria)
        if (AgentController.isActive) _events.tryEmit(event.eventType)
    }

    override fun onInterrupt() {
        Log.w(TAG, "Serviço interrompido")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        if (instance === this) instance = null
        AgentController.onServiceDisconnected()
        hideBubble()
        scope.cancel()
    }

    companion object {
        private const val TAG = "SurveyA11yService"
        @Volatile var instance: SurveyAccessibilityService? = null
            private set
    }
}
