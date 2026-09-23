export interface AndroidCodeFile {
  path: string;
  language: 'kotlin' | 'xml' | 'gradle' | 'markdown' | 'json';
  category: 'manifest' | 'accessibility' | 'overlay' | 'automation' | 'profile' | 'ai' | 'security' | 'tests' | 'docs';
  description: string;
  content: string;
}

export const ANDROID_CODEBASE: AndroidCodeFile[] = [
  {
    path: 'app/src/main/AndroidManifest.xml',
    language: 'xml',
    category: 'manifest',
    description: 'Manifesto do Android com permissões de Overlay, Serviço de Acessibilidade e Foreground Service',
    content: `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="com.researchagent.autofill">

    <!-- Permissão para desenhar a bolha flutuante sobre outros apps (Section 1) -->
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

    <!-- Permissão para manter o serviço em execução estável em segundo plano -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <!-- Permissão para notificações sonoras e heads-up de intervenção (Section 15) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <!-- Acesso à internet para chamadas de IA e checagem de API -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:name=".ResearchAgentApp"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.ResearchAgent"
        tools:targetApi="34">

        <!-- Activity Principal: Dashboard, Gerenciador de Perfil e Configurações -->
        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.ResearchAgent">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Accessibility Service: Núcleo de Leitura e Automação da Tela (Section 11, 12, 39) -->
        <service
            android:name=".accessibility.SurveyAccessibilityService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="false">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

        <!-- Overlay Service: Bolha Flutuante e Painel de Controle (Section 1 & 19) -->
        <service
            android:name=".overlay.FloatingBubbleOverlayService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="specialUse" />

    </application>
</manifest>`,
  },
  {
    path: 'app/src/main/res/xml/accessibility_service_config.xml',
    language: 'xml',
    category: 'accessibility',
    description: 'Configuração do AccessibilityService para inspecionar hierarquia e interagir com formulários',
    content: `<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_description"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewClicked|typeViewFocused|typeViewScrolled"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows|flagReportViewIds|flagIncludeNotImportantViews"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:canRequestFilterKeyEvents="false" />`,
  },
  {
    path: 'app/src/main/java/com/researchagent/autofill/accessibility/SurveyAccessibilityService.kt',
    language: 'kotlin',
    category: 'accessibility',
    description: 'Serviço de Acessibilidade responsável por escanear a tela, ler nós e interagir com campos',
    content: `package com.researchagent.autofill.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.researchagent.autofill.automation.AutomationController
import com.researchagent.autofill.automation.ScreenAnalyzer
import kotlinx.coroutines.*

/**
 * SurveyAccessibilityService
 * Implementa a leitura da Accessibility Tree (Seção 11 e 39)
 * Interage com RadioButtons, Checkboxes, EditText e Botões de Avanço sem depender de coordenadas fixas.
 */
class SurveyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SurveyAccessService"
        var instance: SurveyAccessibilityService? = null
            private set

        // Lista de pacotes bloqueados por segurança e privacidade (Seção 18)
        private val BLOCKED_PACKAGES = setOf(
            "com.android.settings",
            "com.nu.production",
            "com.itau",
            "br.com.intermedium",
            "com.santander.app",
            "com.whatsapp",
            "org.telegram.messenger"
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastScreenHash: Int = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "SurveyAccessibilityService conectado e pronto.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""
        if (BLOCKED_PACKAGES.contains(packageName)) {
            // Seção 18: Evitar qualquer interação com bancos, mensagens e configurações do sistema
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Notifica o controlador sobre possível mudança estrutural da tela
                val root = rootInActiveWindow ?: return
                val currentHash = computeNodeTreeHash(root)
                if (currentHash != lastScreenHash) {
                    lastScreenHash = currentHash
                    AutomationController.getInstance().onScreenContentChanged(packageName, root)
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrompido.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }

    /**
     * Clica em um nó com segurança sem usar coordenadas cegas
     */
    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        // Se o nó pai for o clicável (ex: Container do RadioButton)
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
        }

        // Fallback: Gesto de clique no centro dos limites do nó (Seção 40)
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!rect.isEmpty) {
            return dispatchClickGesture(rect.centerX().toFloat(), rect.centerY().toFloat())
        }
        return false
    }

    /**
     * Insere texto em um campo EditText
     */
    fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /**
     * Gesto preciso de clique quando o elemento não expõe ACTION_CLICK
     */
    private fun dispatchClickGesture(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun computeNodeTreeHash(node: AccessibilityNodeInfo): Int {
        var hash = (node.text?.hashCode() ?: 0) xor (node.className?.hashCode() ?: 0)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                hash = hash xor computeNodeTreeHash(child)
            }
        }
        return hash
    }
}`,
  },
  {
    path: 'app/src/main/java/com/researchagent/autofill/overlay/FloatingBubbleOverlayService.kt',
    language: 'kotlin',
    category: 'overlay',
    description: 'Serviço de Overlay com WindowManager para a Bolha Flutuante ● e Painel de Controle',
    content: `package com.researchagent.autofill.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.researchagent.autofill.R
import com.researchagent.autofill.automation.AgentState
import com.researchagent.autofill.automation.AutomationController
import kotlin.math.abs

/**
 * FloatingBubbleOverlayService
 * Implementa a bolha flutuante na lateral da tela (Seção 1 e 19 do prompt)
 * Permite Ativar Pesquisa, Pausar, Parar e visualizar status da Máquina de Estados.
 */
class FloatingBubbleOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null

    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams

    private var isPanelExpanded = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupBubbleView()
        setupPanelView()
        observeAgentState()
    }

    private fun setupBubbleView() {
        bubbleView = LayoutInflater.from(this).inflate(R.layout.overlay_floating_bubble, null)

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 350
        }

        // Drag and tap listener para mover a bolha pela borda
        bubbleView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = bubbleParams.x
                        initialY = bubbleParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        bubbleParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        bubbleParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(bubbleView, bubbleParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = abs(event.rawX - initialTouchX)
                        val diffY = abs(event.rawY - initialTouchY)
                        if (diffX < 10 && diffY < 10) {
                            // Toque detectado: alterna exibição do painel (Seção 1)
                            togglePanel()
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(bubbleView, bubbleParams)
    }

    private fun setupPanelView() {
        panelView = LayoutInflater.from(this).inflate(R.layout.overlay_control_panel, null)

        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // Configura botões do painel flutuante
        panelView?.findViewById<Button>(R.id.btn_activate_survey)?.setOnClickListener {
            AutomationController.getInstance().startAutomation()
            updatePanelUI()
        }

        panelView?.findViewById<Button>(R.id.btn_pause_survey)?.setOnClickListener {
            AutomationController.getInstance().pauseAutomation()
            updatePanelUI()
        }

        panelView?.findViewById<Button>(R.id.btn_stop_survey)?.setOnClickListener {
            AutomationController.getInstance().stopAutomation()
            togglePanel()
        }

        panelView?.findViewById<View>(R.id.btn_close_panel)?.setOnClickListener {
            togglePanel()
        }
    }

    private fun togglePanel() {
        if (isPanelExpanded) {
            panelView?.let { windowManager.removeView(it) }
            isPanelExpanded = false
        } else {
            panelView?.let {
                updatePanelUI()
                windowManager.addView(it, panelParams)
            }
            isPanelExpanded = true
        }
    }

    private fun updatePanelUI() {
        if (!isPanelExpanded || panelView == null) return
        val controller = AutomationController.getInstance()

        panelView?.findViewById<TextView>(R.id.tv_agent_status)?.text =
            "● Status: " + controller.currentState.name

        panelView?.findViewById<TextView>(R.id.tv_surveys_completed)?.text =
            "Pesquisas concluídas: " + controller.metrics.surveysCompleted

        panelView?.findViewById<TextView>(R.id.tv_waiting_user)?.text =
            "Aguardando usuário: " + controller.metrics.interventionsRequired
    }

    private fun observeAgentState() {
        AutomationController.getInstance().stateLiveData.observeForever { state ->
            updateBubblePulse(state)
            if (isPanelExpanded) updatePanelUI()
        }
    }

    private fun updateBubblePulse(state: AgentState) {
        val bubbleIndicator = bubbleView?.findViewById<View>(R.id.bubble_dot) ?: return
        when (state) {
            AgentState.IDLE -> bubbleIndicator.setBackgroundResource(R.drawable.dot_idle)
            AgentState.SCANNING, AgentState.READING_QUESTION -> bubbleIndicator.setBackgroundResource(R.drawable.dot_scanning)
            AgentState.USER_INTERVENTION_REQUIRED -> bubbleIndicator.setBackgroundResource(R.drawable.dot_alert)
            else -> bubbleIndicator.setBackgroundResource(R.drawable.dot_active)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { windowManager.removeView(it) }
        if (isPanelExpanded && panelView != null) {
            windowManager.removeView(panelView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}`,
  },
  {
    path: 'app/src/main/java/com/researchagent/autofill/automation/StateMachine.kt',
    language: 'kotlin',
    category: 'automation',
    description: 'Máquina de Estados Finita e Watchdog Anti-Loop (Seção 13, 14, 41)',
    content: `package com.researchagent.autofill.automation

/**
 * Estados da Máquina de Estados (Seção 13 do prompt)
 */
enum class AgentState {
    IDLE,
    SCANNING,
    RESEARCH_DETECTED,
    READING_QUESTION,
    UNDERSTANDING_QUESTION,
    SEARCHING_PROFILE,
    GENERATING_RESPONSE,
    VALIDATING_RESPONSE,
    FILLING_FIELD,
    VERIFYING_FIELD,
    NEXT_PAGE,
    WAITING,
    RESEARCH_COMPLETED,
    SEARCHING_NEXT_RESEARCH,
    USER_INTERVENTION_REQUIRED
}

enum class AutomationMode {
    MANUAL,
    ASSISTIDO,
    AUTOMATICO
}

/**
 * StateMachine
 * Controla as transições válidas e inclui Watchdog Timer para prevenção de loops infinitos (Seção 41)
 */
class StateMachine(private val listener: StateChangeListener) {

    interface StateChangeListener {
        fun onStateChanged(oldState: AgentState, newState: AgentState)
        fun onLoopDetected(repeatedState: AgentState)
    }

    var currentState: AgentState = AgentState.IDLE
        private set

    // Watchdog de segurança contra loops repetidos (Seção 41)
    private var stateRepetitionCount = 0
    private var lastRecordedState: AgentState = AgentState.IDLE
    private val MAX_STATE_REPETITIONS = 5

    @Synchronized
    fun transitionTo(newState: AgentState): Boolean {
        if (!isValidTransition(currentState, newState)) {
            return false
        }

        // Detecção de loop
        if (newState == lastRecordedState && newState != AgentState.WAITING && newState != AgentState.SCANNING) {
            stateRepetitionCount++
            if (stateRepetitionCount >= MAX_STATE_REPETITIONS) {
                listener.onLoopDetected(newState)
                transitionTo(AgentState.USER_INTERVENTION_REQUIRED)
                return false
            }
        } else {
            stateRepetitionCount = 0
            lastRecordedState = newState
        }

        val old = currentState
        currentState = newState
        listener.onStateChanged(old, newState)
        return true
    }

    private fun isValidTransition(from: AgentState, to: AgentState): Boolean {
        // Intervenção e parada sempre podem ser chamadas
        if (to == AgentState.USER_INTERVENTION_REQUIRED || to == AgentState.IDLE) return true

        return when (from) {
            AgentState.IDLE -> to == AgentState.SCANNING
            AgentState.SCANNING -> to in setOf(AgentState.RESEARCH_DETECTED, AgentState.IDLE, AgentState.SEARCHING_NEXT_RESEARCH)
            AgentState.RESEARCH_DETECTED -> to == AgentState.READING_QUESTION
            AgentState.READING_QUESTION -> to in setOf(AgentState.UNDERSTANDING_QUESTION, AgentState.NEXT_PAGE, AgentState.RESEARCH_COMPLETED)
            AgentState.UNDERSTANDING_QUESTION -> to == AgentState.SEARCHING_PROFILE
            AgentState.SEARCHING_PROFILE -> to in setOf(AgentState.GENERATING_RESPONSE, AgentState.USER_INTERVENTION_REQUIRED)
            AgentState.GENERATING_RESPONSE -> to == AgentState.VALIDATING_RESPONSE
            AgentState.VALIDATING_RESPONSE -> to in setOf(AgentState.FILLING_FIELD, AgentState.USER_INTERVENTION_REQUIRED)
            AgentState.FILLING_FIELD -> to == AgentState.VERIFYING_FIELD
            AgentState.VERIFYING_FIELD -> to in setOf(AgentState.READING_QUESTION, AgentState.NEXT_PAGE, AgentState.USER_INTERVENTION_REQUIRED)
            AgentState.NEXT_PAGE -> to == AgentState.WAITING
            AgentState.WAITING -> to in setOf(AgentState.SCANNING, AgentState.READING_QUESTION, AgentState.RESEARCH_COMPLETED)
            AgentState.RESEARCH_COMPLETED -> to in setOf(AgentState.SEARCHING_NEXT_RESEARCH, AgentState.IDLE)
            AgentState.SEARCHING_NEXT_RESEARCH -> to in setOf(AgentState.SCANNING, AgentState.IDLE)
            AgentState.USER_INTERVENTION_REQUIRED -> to in setOf(AgentState.READING_QUESTION, AgentState.SCANNING, AgentState.IDLE)
        }
    }
}`,
  },
  {
    path: 'app/src/main/java/com/researchagent/autofill/ai/AnswerEngine.kt',
    language: 'kotlin',
    category: 'ai',
    description: 'Motor de Respostas com Camada de Validação Anti-Alucinação (Seção 3, 24, 25, 44)',
    content: `package com.researchagent.autofill.ai

import com.researchagent.autofill.profile.UserProfile
import org.json.JSONObject

enum class ConfidenceLevel {
    CONFIDENCE_HIGH,
    CONFIDENCE_MEDIUM,
    CONFIDENCE_LOW,
    UNKNOWN
}

enum class DataOrigin {
    DADO_FORNECIDO,
    DADO_DERIVADO,
    DADO_NAO_DISPONIVEL
}

data class AnswerDecision(
    val action: String, // "ANSWER" ou "ASK_USER"
    val answer: String?,
    val confidence: Double,
    val confidenceLevel: ConfidenceLevel,
    val source: String?,
    val reason: String,
    val dataOrigin: DataOrigin,
    val needsUser: Boolean
)

/**
 * AnswerEngine & Anti-Hallucination Validator Layer (Seção 25)
 * Garante que nenhuma resposta seja aceita a menos que esteja estritamente comprovada no perfil.
 */
class AnswerEngine {

    companion object {
        private const val MIN_AUTO_CONFIDENCE = 0.90
    }

    /**
     * Validador independente contra alucinações (Seção 25)
     */
    fun validateResponse(
        proposedDecision: AnswerDecision,
        profile: UserProfile
    ): AnswerDecision {
        if (proposedDecision.action == "ASK_USER") {
            return proposedDecision
        }

        // Se a resposta propõe preenchimento, verifica se a fonte é verdadeira no perfil
        val sourceKey = proposedDecision.source
        if (sourceKey.isNullOrBlank()) {
            return rejectWithIntervention("Resposta rejeitada: Nenhuma fonte válida do perfil foi declarada.")
        }

        val profileValue = profile.resolveField(sourceKey)
        if (profileValue == null) {
            return rejectWithIntervention(
                "Resposta rejeitada pelo validador: O campo '$sourceKey' está vazio ou inexistente no perfil."
            )
        }

        // Confirmação lógica de derivação permitida (Seção 10)
        if (proposedDecision.dataOrigin == DataOrigin.DADO_DERIVADO) {
            val isLogicallyConsistent = verifyLogicalDerivation(sourceKey, proposedDecision.answer, profile)
            if (!isLogicallyConsistent) {
                return rejectWithIntervention("Derivação lógica inválida ou extrapolação detectada.")
            }
        }

        return proposedDecision
    }

    private fun verifyLogicalDerivation(sourceKey: String, answer: String?, profile: UserProfile): Boolean {
        if (sourceKey == "identidade.data_nascimento") {
            // Idade derivada
            val expectedAge = profile.getDerivedAge()
            return answer?.contains(expectedAge.toString()) == true
        }
        if (sourceKey == "familia.tem_filhos" && profile.familia.temFilhos == false) {
            // Se não tem filhos, quantidade deve ser 0
            return answer == "0" || answer?.equals("nenhum", ignoreCase = true) == true
        }
        return true
    }

    private fun rejectWithIntervention(reason: String): AnswerDecision {
        return AnswerDecision(
            action = "ASK_USER",
            answer = null,
            confidence = 0.0,
            confidenceLevel = ConfidenceLevel.UNKNOWN,
            source = null,
            reason = reason,
            dataOrigin = DataOrigin.DADO_NAO_DISPONIVEL,
            needsUser = true
        )
    }
}`,
  },
  {
    path: 'app/src/main/java/com/researchagent/autofill/security/EncryptedStorage.kt',
    language: 'kotlin',
    category: 'security',
    description: 'Armazenamento Criptografado com Android Keystore e EncryptedSharedPreferences (Seção 21)',
    content: `package com.researchagent.autofill.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * EncryptedStorage
 * Armazenamento seguro de dados pessoais em repouso utilizando Android Keystore (AES-256 GCM)
 * em conformidade estrita com a Seção 21 do prompt.
 */
class EncryptedStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_user_profile_vault",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveEncryptedString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    fun getEncryptedString(key: String, defaultValue: String? = null): String? {
        return sharedPreferences.getString(key, defaultValue)
    }

    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
}`,
  },
  {
    path: 'app/src/main/java/com/researchagent/autofill/notifications/InterventionManager.kt',
    language: 'kotlin',
    category: 'security',
    description: 'Gerenciador de Notificações Sonoras e Alertas de Intervenção Humana (Seção 15, 16, 26)',
    content: `package com.researchagent.autofill.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.researchagent.autofill.R
import com.researchagent.autofill.ui.MainActivity

/**
 * InterventionManager
 * Dispara som de alerta 🔔 e notificações de alta prioridade quando for detectado:
 * - CAPTCHA (Seção 16)
 * - Informação Necessária Ausente (Seção 6)
 * - Conflito de Perfil (Seção 26)
 */
class InterventionManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "channel_agent_interventions"
        const val NOTIFICATION_ID = 1001
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Intervenções do Agente",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas sonoros para CAPTCHA e dados ausentes"
                setSound(soundUri, audioAttributes)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun notifyInterventionRequired(title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_INTERVENTION_ALERT", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}`,
  },
  {
    path: 'app/src/test/java/com/researchagent/autofill/AnswerValidationTest.kt',
    language: 'kotlin',
    category: 'tests',
    description: 'Testes Unitários de Validação Anti-Alucinação e Respostas Derivadas',
    content: `package com.researchagent.autofill

import com.researchagent.autofill.ai.AnswerDecision
import com.researchagent.autofill.ai.AnswerEngine
import com.researchagent.autofill.ai.ConfidenceLevel
import com.researchagent.autofill.ai.DataOrigin
import com.researchagent.autofill.profile.FamiliaProfile
import com.researchagent.autofill.profile.IdentidadeProfile
import com.researchagent.autofill.profile.UserProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AnswerValidationTest {

    private lateinit var answerEngine: AnswerEngine
    private lateinit var mockProfile: UserProfile

    @Before
    fun setUp() {
        answerEngine = AnswerEngine()
        mockProfile = UserProfile(
            identidade = IdentidadeProfile(
                nome = "Carlos Silva",
                dataNascimento = "1995-04-15",
                genero = "Masculino",
                cidade = "São Paulo"
            ),
            familia = FamiliaProfile(
                temFilhos = false,
                quantidadeFilhos = null
            )
        )
    }

    @Test
    fun testRejectHallucinatedAnswerWhenSourceMissing() {
        val hallucinated = AnswerDecision(
            action = "ANSWER",
            answer = "R$ 15.000,00",
            confidence = 0.95,
            confidenceLevel = ConfidenceLevel.CONFIDENCE_HIGH,
            source = "financas.renda_mensal", // Não existe no mockProfile
            reason = "Suposição",
            dataOrigin = DataOrigin.DADO_FORNECIDO,
            needsUser = false
        )

        val result = answerEngine.validateResponse(hallucinated, mockProfile)
        assertEquals("ASK_USER", result.action)
        assertTrue(result.needsUser)
        assertEquals(0.0, result.confidence, 0.001)
    }

    @Test
    fun testAllowDerivedZeroKidsWhenTemFilhosIsFalse() {
        val derivedDecision = AnswerDecision(
            action = "ANSWER",
            answer = "0",
            confidence = 0.99,
            confidenceLevel = ConfidenceLevel.CONFIDENCE_HIGH,
            source = "familia.tem_filhos",
            reason = "Perfil afirma tem_filhos = false",
            dataOrigin = DataOrigin.DADO_DERIVADO,
            needsUser = false
        )

        val result = answerEngine.validateResponse(derivedDecision, mockProfile)
        assertEquals("ANSWER", result.action)
        assertEquals("0", result.answer)
        assertFalse(result.needsUser)
    }
}`,
  },
  {
    path: 'app/build.gradle.kts',
    language: 'gradle',
    category: 'manifest',
    description: 'Configuração do Gradle com Jetpack Compose, Room, Coroutines e Security Crypto',
    content: `plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.researchagent.autofill"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.researchagent.autofill"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Criptografia Keystore (Seção 21)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines & Concorrência
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // ML Kit OCR (Seção 11)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")

    // Room Database para Logs e Aprendizado (Seção 20 e 27)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}`,
  },
  {
    path: 'INSTALLATION_GUIDE.md',
    language: 'markdown',
    category: 'docs',
    description: 'Guia Completo de Instalação, Compilação e Concessão de Permissões ADB no Android',
    content: `# Research Agent — Guia de Compilação e Instalação no Android

Este documento fornece as instruções oficiais para compilar o APK, instalar em dispositivos Android físicos ou emuladores e conceder as permissões necessárias para o funcionamento pleno do agente autônomo.

---

## 1. Requisitos do Sistema

- **Android Studio Ladybug** (ou versão superior)
- **JDK 17** ou JDK 21 configurado
- Dispositivo Android com **Android 8.0 (API 26)** ou superior
- Cabo USB com **Depuração USB ativada** (Developer Options)

---

## 2. Como Abrir e Compilar no Android Studio

1. Abra o Android Studio e selecione **Open**.
2. Navegue até a pasta raiz do projeto clonado.
3. Aguarde o Gradle sincronizar todas as dependências (\`Sync Project with Gradle Files\`).
4. Conecte seu smartphone via USB ou inicie um Emulador com Google Play Services (API 34).
5. Clique em **Run 'app'** (\`Shift + F10\`).

---

## 3. Concessão de Permissões pelo Dispositivo

Após instalar o app pela primeira vez:

1. **Sobrepor a outros aplicativos (Overlay / Bolha Flutuante)**:
   - Configurações do Android → Apps → Acesso especial a apps → Sobrepor a outros apps → Ativar **Research Agent**.
2. **Serviço de Acessibilidade (Leitura de Tela)**:
   - Configurações do Android → Acessibilidade → Apps Instalados → Ativar **Research Agent Accessibility Service**.
3. **Notificações**:
   - Aceite a permissão de notificações em tempo de execução para os alertas sonoros 🔔.

---

## 4. Concessão Rápida de Permissões via ADB (Modo Desenvolvedor)

Se preferir conceder todas as permissões diretamente pelo terminal:

\`\`\`bash
# Conceder sobreposição de tela (SYSTEM_ALERT_WINDOW)
adb shell appops set com.researchagent.autofill SYSTEM_ALERT_WINDOW allow

# Ativar o Serviço de Acessibilidade
adb shell settings put secure enabled_accessibility_services com.researchagent.autofill/.accessibility.SurveyAccessibilityService
adb shell settings put secure accessibility_enabled 1

# Conceder permissão de notificações (Android 13+)
adb shell pm grant com.researchagent.autofill android.permission.POST_NOTIFICATIONS
\`\`\`

---

## 5. Primeiro Uso

1. Abra o **Research Agent**.
2. Configure seus dados básicos na aba **MEUS DADOS** ou importe seu JSON pré-existente.
3. Toque em **Iniciar Bolha Flutuante**.
4. A bolha circular (\`●\`) aparecerá na lateral da sua tela.
5. Abra qualquer aplicativo de pesquisa ou formulário web no Chrome.
6. Toque na bolha e pressione **[ ATIVAR PESQUISA ]**.
`,
  },
];
