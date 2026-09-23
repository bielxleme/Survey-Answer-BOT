package com.researchagent.autofill.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.researchagent.autofill.core.Bounds
import com.researchagent.autofill.core.ScreenDriver
import com.researchagent.autofill.core.ScreenNode
import com.researchagent.autofill.core.ScreenSnapshot
import com.researchagent.autofill.core.WidgetKind
import com.researchagent.autofill.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Implementação Android do [ScreenDriver]: lê a árvore de acessibilidade, age por ações
 * semânticas (ACTION_CLICK, ACTION_SET_TEXT…) e só usa gestos por coordenadas como fallback.
 */
class AccessibilityDriver(private val service: SurveyAccessibilityService) : ScreenDriver {

    @Volatile private var nodeMap: Map<Int, AccessibilityNodeInfo> = emptyMap()
    private val ocr by lazy { OcrEngine(service) }

    // ── Leitura ───────────────────────────────────────────────────────
    override suspend fun snapshot(): ScreenSnapshot? = withContext(Dispatchers.Default) {
        val root = pickRoot() ?: return@withContext null
        val list = ArrayList<ScreenNode?>()
        val map = HashMap<Int, AccessibilityNodeInfo>()
        runCatching { traverse(root, -1, 0, list, map) }
        nodeMap = map
        val pkg = root.packageName?.toString().orEmpty()
        ScreenSnapshot(pkg, list.filterNotNull())
    }

    private fun pickRoot(): AccessibilityNodeInfo? {
        service.rootInActiveWindow?.let { return it }
        return runCatching {
            service.windows
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .sortedByDescending { (if (it.isActive) 2 else 0) + (if (it.isFocused) 1 else 0) }
                .firstNotNullOfOrNull { it.root }
        }.getOrNull()
    }

    private fun traverse(
        node: AccessibilityNodeInfo, parentId: Int, depth: Int,
        out: ArrayList<ScreenNode?>, map: HashMap<Int, AccessibilityNodeInfo>
    ): Int {
        val id = out.size
        out.add(null)
        map[id] = node
        val childIds = ArrayList<Int>()
        if (depth < MAX_DEPTH) {
            for (i in 0 until node.childCount) {
                if (out.size >= MAX_NODES) break
                val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                childIds += traverse(child, id, depth + 1, out, map)
            }
        }
        val r = Rect()
        node.getBoundsInScreen(r)
        val range = node.rangeInfo
        out[id] = ScreenNode(
            id = id,
            parentId = parentId,
            depth = depth,
            className = node.className?.toString().orEmpty(),
            text = node.text?.toString().orEmpty(),
            contentDescription = node.contentDescription?.toString().orEmpty(),
            hint = node.hintText?.toString().orEmpty(),
            viewId = node.viewIdResourceName.orEmpty(),
            stateDescription = if (Build.VERSION.SDK_INT >= 30) node.stateDescription?.toString().orEmpty() else "",
            isClickable = node.isClickable,
            isCheckable = node.isCheckable,
            isChecked = node.isChecked,
            isEditable = node.isEditable,
            isEnabled = node.isEnabled,
            isVisible = node.isVisibleToUser,
            isScrollable = node.isScrollable,
            isPassword = node.isPassword,
            isSelected = node.isSelected,
            inputType = node.inputType,
            rangeCurrent = range?.current,
            rangeMin = range?.min,
            rangeMax = range?.max,
            bounds = Bounds(r.left, r.top, r.right, r.bottom),
            childIds = childIds
        )
        return id
    }

    override suspend fun ocrSnapshot(): ScreenSnapshot? {
        if (Build.VERSION.SDK_INT < 30) return null
        val lines = runCatching { ocr.captureAndRecognize(service) }.getOrNull() ?: return null
        if (lines.isEmpty()) return null
        val nodes = ArrayList<ScreenNode>()
        nodes += ScreenNode(OCR_BASE, -1, 0, "ocr.Root", childIds = lines.indices.map { OCR_BASE + 1 + it })
        lines.forEachIndexed { i, l ->
            nodes += ScreenNode(OCR_BASE + 1 + i, OCR_BASE, 1, "ocr.Text", text = l.text, bounds = l.bounds, fromOcr = true)
        }
        return ScreenSnapshot(service.lastPackage, nodes)
    }

    // ── Ações ─────────────────────────────────────────────────────────
    private fun live(node: ScreenNode): AccessibilityNodeInfo? =
        nodeMap[node.id]?.takeIf { runCatching { it.refresh() }.getOrDefault(false) }

    override suspend fun click(node: ScreenNode): Boolean = withContext(Dispatchers.Default) {
        if (node.fromOcr) return@withContext tap(node.bounds)
        val info = live(node) ?: return@withContext tap(node.bounds)
        if (info.isClickable && info.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return@withContext true
        var p = info.parent
        var guard = 0
        while (p != null && guard++ < 6) {
            if (p.isClickable) return@withContext p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            p = p.parent
        }
        val r = Rect().also { info.getBoundsInScreen(it) }
        tap(Bounds(r.left, r.top, r.right, r.bottom))
    }

    override suspend fun setText(node: ScreenNode, text: String): Boolean = withContext(Dispatchers.Default) {
        val info = live(node) ?: return@withContext false
        info.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        if (!info.isFocused) info.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (info.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return@withContext true
        // Fallback: colar via área de transferência
        withContext(Dispatchers.Main) {
            val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("research-agent", text))
        }
        info.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    override suspend fun setProgress(node: ScreenNode, value: Float): Boolean = withContext(Dispatchers.Default) {
        val info = live(node) ?: return@withContext false
        val args = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value) }
        info.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, args)
    }

    override suspend fun scrollForward(): Boolean {
        val before = snapshot() ?: return false
        val scrollable = nodeMap.values
            .filter { runCatching { it.isScrollable && it.isVisibleToUser }.getOrDefault(false) }
            .maxByOrNull { n -> Rect().also { n.getBoundsInScreen(it) }.let { it.width() * it.height() } }
        val acted = withContext(Dispatchers.Default) {
            scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
        }
        if (!acted) swipeUp()
        delay(700)
        val after = snapshot() ?: return false
        return after.contentSignature != before.contentSignature
    }

    override suspend fun back(): Boolean = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

    // ── Espera baseada em eventos (Seção 14) ─────────────────────────
    override suspend fun awaitChange(previous: ScreenSnapshot, timeoutMs: Long): ScreenSnapshot? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastCheck = 0L
        while (System.currentTimeMillis() < deadline) {
            val remaining = deadline - System.currentTimeMillis()
            val gotEvent = withTimeoutOrNull(minOf(500L, remaining.coerceAtLeast(1))) { service.events.first() } != null
            val now = System.currentTimeMillis()
            if (!gotEvent && now - lastCheck < 2500) continue
            lastCheck = now
            val s = snapshot() ?: continue
            if (s.signature == previous.signature) continue
            // estabilidade: a tela precisa parar de mudar e não mostrar carregamento
            delay(300)
            val s2 = snapshot() ?: s
            if (s2.signature == s.signature && !isLoading(s2)) return s2
        }
        return null
    }

    private fun isLoading(s: ScreenSnapshot): Boolean =
        s.visibleNodes.any { it.kind == WidgetKind.PROGRESS && it.rangeMax == null && !it.bounds.isEmpty }

    // ── Gestos (fallback) ────────────────────────────────────────────
    private suspend fun tap(b: Bounds): Boolean {
        if (b.isEmpty) return false
        val path = Path().apply { moveTo(b.centerX.toFloat(), b.centerY.toFloat()) }
        return gesture(GestureDescription.StrokeDescription(path, 0, 60))
    }

    private suspend fun swipeUp(): Boolean {
        val dm = service.resources.displayMetrics
        val x = dm.widthPixels / 2f
        val path = Path().apply { moveTo(x, dm.heightPixels * 0.72f); lineTo(x, dm.heightPixels * 0.32f) }
        return gesture(GestureDescription.StrokeDescription(path, 0, 350))
    }

    private suspend fun gesture(stroke: GestureDescription.StrokeDescription): Boolean =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val g = GestureDescription.Builder().addStroke(stroke).build()
                val ok = service.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                    override fun onCancelled(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(false) }
                }, null)
                if (!ok && cont.isActive) cont.resume(false)
            }
        }

    companion object {
        private const val MAX_NODES = 2500
        private const val MAX_DEPTH = 80
        private const val OCR_BASE = 1_000_000
    }
}
