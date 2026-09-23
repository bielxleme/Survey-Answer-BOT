package com.researchagent.autofill.ui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.researchagent.autofill.AppGraph
import com.researchagent.autofill.accessibility.SurveyAccessibilityService
import com.researchagent.autofill.automation.AgentController
import com.researchagent.autofill.core.AgentMode
import com.researchagent.autofill.data.ProfileJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tela principal: Painel, Meus dados, Logs e Ajustes (Seções 4–6, 19, 20, 35, 38, 42).
 * Interface com Views nativas — leve e sem dependências.
 */
class MainActivity : Activity() {

    val scope: CoroutineScope = MainScope()
    lateinit var ui: Ui
        private set
    private lateinit var content: ScrollView
    private lateinit var nav: LinearLayout
    private lateinit var subtitle: TextView
    private var tab = TAB_DASHBOARD
    private var mode = Mode.TABS
    private var renderJob: Job? = null
    /** Diálogos abertos suspendem re-renderizações automáticas (não perder o que o usuário digita). */
    var dialogOpen = false

    private enum class Mode { ONBOARDING, WIZARD, TABS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        applySecureFlag()
        @Suppress("DEPRECATION") run {
            window.statusBarColor = Palette.Surface
            window.navigationBarColor = Palette.Surface
        }
        tab = savedInstanceState?.getInt(EXTRA_TAB) ?: intent?.getIntExtra(EXTRA_TAB, TAB_DASHBOARD) ?: TAB_DASHBOARD
        mode = if (AppGraph.settings.current.onboardingDone) Mode.TABS else Mode.ONBOARDING
        buildShell()
        render()
        observe()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_TAB, tab)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra(EXTRA_TAB)) {
            tab = intent.getIntExtra(EXTRA_TAB, tab)
            if (mode == Mode.TABS) render()
        }
    }

    override fun onResume() {
        super.onResume()
        applySecureFlag()
        AppGraph.knowledge.prune(AppGraph.profile.current)
        if (mode == Mode.TABS && tab == TAB_DASHBOARD) render()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun applySecureFlag() {
        if (AppGraph.settings.current.secureScreens) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    // ── Estrutura: cabeçalho, conteúdo rolável, navegação inferior ────
    private fun buildShell() {
        val root = ui.column().apply { setBackgroundColor(Palette.Bg) }
        val header = ui.column(0).apply {
            setBackgroundColor(Palette.Surface)
            setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(12))
        }
        header.addView(ui.text("Research Agent", 20f, Palette.Text, bold = true))
        subtitle = ui.muted("", 12f, top = 2)
        header.addView(subtitle)
        root.addView(header, ui.lp())

        content = ScrollView(this).apply { isFillViewport = true }
        root.addView(content, ui.lp(height = 0, weight = 1f))

        nav = ui.row().apply { setBackgroundColor(Palette.Surface) }
        root.addView(nav, ui.lp())
        setContentView(root)
        ui.applySystemInsets(root)
    }

    private fun buildNav() {
        nav.removeAllViews()
        nav.visibility = if (mode == Mode.TABS) View.VISIBLE else View.GONE
        listOf("● Painel", "👤 Meus dados", "☰ Logs", "⚙ Ajustes").forEachIndexed { i, label ->
            val t = ui.text(label, 13f, if (i == tab) Palette.Green else Palette.Muted, bold = i == tab).apply {
                gravity = Gravity.CENTER
                setPadding(0, ui.dp(14), 0, ui.dp(14))
                setOnClickListener { if (tab != i) { tab = i; render(resetScroll = true) } }
            }
            nav.addView(t, ui.lp(0, weight = 1f))
        }
    }

    fun render(resetScroll: Boolean = false) {
        val y = content.scrollY
        content.removeAllViews()
        val page = ui.column().apply { setPadding(0, ui.dp(8), 0, ui.dp(24)) }
        content.addView(page, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        when (mode) {
            Mode.ONBOARDING -> Screens.onboarding(this, page)
            Mode.WIZARD -> Screens.wizard(this, page)
            Mode.TABS -> when (tab) {
                TAB_PROFILE -> ProfileScreen.build(this, page)
                TAB_LOGS -> Screens.logs(this, page)
                TAB_SETTINGS -> SettingsScreen.build(this, page)
                else -> Screens.dashboard(this, page)
            }
        }
        buildNav()
        updateSubtitle()
        if (!resetScroll) content.post { content.scrollTo(0, y) } else content.scrollTo(0, 0)
    }

    private fun updateSubtitle() {
        val s = AgentController.status.value
        subtitle.text = when {
            !isAccessibilityEnabled(this) -> "○ Serviço de acessibilidade desativado"
            s.intervention != null -> "🔴 Aguardando você — ${s.intervention?.reason?.title}"
            s.paused -> "🟡 Pausado"
            s.running -> "🟢 ${s.state.label} · modo ${s.mode.label}"
            else -> "● Pronto · modo ${AppGraph.settings.current.mode.label}"
        }
    }

    /** Re-renderiza com atraso curto, agrupando mudanças em sequência. */
    fun scheduleRender(onlyTabs: Set<Int>) {
        if (mode != Mode.TABS || dialogOpen || tab !in onlyTabs) { updateSubtitle(); return }
        renderJob?.cancel()
        renderJob = scope.launch { delay(250); render() }
    }

    private fun observe() {
        scope.launch { AgentController.status.collect { scheduleRender(setOf(TAB_DASHBOARD)) } }
        scope.launch { AgentController.serviceConnected.collect { scheduleRender(setOf(TAB_DASHBOARD)) } }
        scope.launch { AppGraph.stats.stats.collect { scheduleRender(setOf(TAB_DASHBOARD)) } }
        scope.launch { AppGraph.knowledge.pending.collect { scheduleRender(setOf(TAB_DASHBOARD, TAB_PROFILE)) } }
        scope.launch { AppGraph.profile.profile.collect { scheduleRender(setOf(TAB_PROFILE)) } }
        scope.launch { AppGraph.logs.logs.collect { scheduleRender(setOf(TAB_LOGS)) } }
    }

    // ── Navegação entre modos ────────────────────────────────────────
    /** Estado do assistente de perfil (Seção 37): fila de campos e posição atual. */
    var wizardKeys: List<String> = emptyList()
    var wizardIndex = 0

    fun finishOnboarding(openWizard: Boolean) {
        AppGraph.settings.update { it.copy(onboardingDone = true) }
        if (openWizard) openWizard() else { mode = Mode.TABS; render(resetScroll = true) }
    }

    fun openWizard() {
        val p = AppGraph.profile.current
        wizardKeys = com.researchagent.autofill.core.ProfileSchema.wizardFields.map { it.key }.distinct().filter { p.raw(it) == null }
        wizardIndex = 0
        mode = Mode.WIZARD
        render(resetScroll = true)
    }

    fun closeWizard() { mode = Mode.TABS; tab = TAB_PROFILE; render(resetScroll = true) }

    fun goToTab(t: Int) { mode = Mode.TABS; tab = t; render(resetScroll = true) }

    // ── Importar / exportar JSON (Seção 35) ──────────────────────────
    private var exportTemplate = false

    fun startImport() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "application/octet-stream"))
        try { startActivityForResult(i, REQ_IMPORT) } catch (e: Exception) { toast("Nenhum seletor de arquivos disponível") }
    }

    fun startExport(template: Boolean) {
        exportTemplate = template
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, if (template) "research_agent_modelo.json" else "research_agent_perfil.json")
        try { startActivityForResult(i, REQ_EXPORT) } catch (e: Exception) { toast("Nenhum seletor de arquivos disponível") }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_IMPORT -> {
                val txt = runCatching { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                if (txt == null) { toast("Não foi possível ler o arquivo"); return }
                try {
                    val r = ProfileJson.import(txt, AppGraph.profile.current)
                    AppGraph.profile.replace(r.profile)
                    AppGraph.knowledge.prune(r.profile)
                    toast("Importado: ${r.imported} campos e ${r.custom} personalizados")
                    if (mode == Mode.ONBOARDING) finishOnboarding(openWizard = false) else render()
                } catch (e: Exception) {
                    toast("JSON inválido: ${e.message}")
                }
            }
            REQ_EXPORT -> {
                val json = if (exportTemplate) ProfileJson.template() else ProfileJson.export(AppGraph.profile.current)
                val ok = runCatching {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) } != null
                }.getOrDefault(false)
                toast(if (ok) "Arquivo salvo" else "Falha ao salvar")
            }
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF) render()
    }

    fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            toast("Procure \"Research Agent\" em Apps instalados/baixados e ative.")
        } catch (e: Exception) { toast("Abra Configurações → Acessibilidade") }
    }

    fun openAppDetails() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } catch (_: Exception) { }
    }

    fun startAgent() {
        if (AgentController.start()) {
            toast("Agente ativo. Abra a pesquisa e acompanhe pela bolha \"RA\".")
            moveTaskToBack(true)
        } else {
            toast("Ative o serviço de acessibilidade primeiro.")
        }
    }

    fun setMode(m: AgentMode) { AgentController.setMode(m); render() }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_DASHBOARD = 0
        const val TAB_PROFILE = 1
        const val TAB_LOGS = 2
        const val TAB_SETTINGS = 3
        private const val REQ_IMPORT = 11
        private const val REQ_EXPORT = 12
        private const val REQ_NOTIF = 13
    }
}

/** Verdadeiro se o serviço de acessibilidade do app está habilitado. */
fun isAccessibilityEnabled(context: Context): Boolean {
    if (SurveyAccessibilityService.instance != null) return true
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    val me = ComponentName(context, SurveyAccessibilityService::class.java).flattenToString()
    return enabled.split(':').any { it.equals(me, ignoreCase = true) }
}

fun hasNotificationPermission(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}min ${s % 60}s"
        else -> "${s / 3600}h ${(s % 3600) / 60}min"
    }
}
