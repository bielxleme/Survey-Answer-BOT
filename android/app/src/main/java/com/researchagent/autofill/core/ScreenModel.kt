package com.researchagent.autofill.core

/** Retângulo em coordenadas de tela. */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val isEmpty: Boolean get() = right <= left || bottom <= top
    companion object { val EMPTY = Bounds(0, 0, 0, 0) }
}

/** Tipo de widget inferido a partir da classe e dos atributos do nó. */
enum class WidgetKind { TEXT, EDIT, RADIO, CHECKBOX, SWITCH, BUTTON, DROPDOWN, SLIDER, CONTAINER, IMAGE, PROGRESS, OTHER }

/**
 * Cópia imutável e independente do Android de um nó da árvore de acessibilidade
 * (ou de uma linha reconhecida por OCR). Permite testar toda a lógica na JVM.
 */
data class ScreenNode(
    val id: Int,
    val parentId: Int,
    val depth: Int,
    val className: String = "",
    val text: String = "",
    val contentDescription: String = "",
    val hint: String = "",
    val viewId: String = "",
    val stateDescription: String = "",
    val isClickable: Boolean = false,
    val isCheckable: Boolean = false,
    val isChecked: Boolean = false,
    val isEditable: Boolean = false,
    val isEnabled: Boolean = true,
    val isVisible: Boolean = true,
    val isScrollable: Boolean = false,
    val isPassword: Boolean = false,
    val isSelected: Boolean = false,
    val inputType: Int = 0,
    val rangeCurrent: Float? = null,
    val rangeMin: Float? = null,
    val rangeMax: Float? = null,
    val bounds: Bounds = Bounds.EMPTY,
    val childIds: List<Int> = emptyList(),
    val fromOcr: Boolean = false
) {
    /** Rótulo legível do nó. */
    val label: String
        get() = text.ifBlank { contentDescription }.ifBlank { hint }.trim()

    val kind: WidgetKind
        get() {
            val c = className.lowercase()
            return when {
                isEditable || c.endsWith("edittext") || c.contains("autocompletetextview") -> WidgetKind.EDIT
                c.contains("radiobutton") -> WidgetKind.RADIO
                c.contains("checkbox") || c.contains("checkedtextview") -> WidgetKind.CHECKBOX
                c.contains("switch") || c.contains("togglebutton") -> WidgetKind.SWITCH
                c.contains("spinner") || c.contains("combobox") -> WidgetKind.DROPDOWN
                c.contains("seekbar") || c.contains("slider") || (rangeMax != null && !c.contains("progressbar")) -> WidgetKind.SLIDER
                c.contains("progressbar") -> WidgetKind.PROGRESS
                isCheckable -> WidgetKind.CHECKBOX
                c.endsWith("button") || c.contains("imagebutton") -> WidgetKind.BUTTON
                c.contains("image") -> WidgetKind.IMAGE
                label.isNotBlank() -> WidgetKind.TEXT
                childIds.isNotEmpty() -> WidgetKind.CONTAINER
                else -> WidgetKind.OTHER
            }
        }

    val isOption: Boolean get() = kind == WidgetKind.RADIO || kind == WidgetKind.CHECKBOX || kind == WidgetKind.SWITCH
}

class ScreenSnapshot(
    val packageName: String,
    val nodes: List<ScreenNode>,
    val windowTitle: String = "",
    val url: String = ""
) {
    private val byId: Map<Int, ScreenNode> = nodes.associateBy { it.id }

    fun node(id: Int): ScreenNode? = byId[id]

    fun children(node: ScreenNode): List<ScreenNode> = node.childIds.mapNotNull { byId[it] }

    fun parent(node: ScreenNode): ScreenNode? = byId[node.parentId]

    fun ancestors(node: ScreenNode): List<ScreenNode> {
        val out = ArrayList<ScreenNode>()
        var p = parent(node)
        var guard = 0
        while (p != null && guard++ < 100) { out += p; p = parent(p) }
        return out
    }

    fun descendants(node: ScreenNode): List<ScreenNode> {
        val out = ArrayList<ScreenNode>()
        val stack = ArrayDeque<ScreenNode>()
        children(node).asReversed().forEach { stack.addLast(it) }
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            out += n
            children(n).asReversed().forEach { stack.addLast(it) }
        }
        return out
    }

    fun isDescendantOf(node: ScreenNode, ancestorId: Int): Boolean = ancestors(node).any { it.id == ancestorId }

    val visibleNodes: List<ScreenNode> get() = nodes.filter { it.isVisible }

    /** Texto visível completo, normalizado (para detecção de CAPTCHA/login/conclusão). */
    val normalizedText: String by lazy {
        Text.normalize(visibleNodes.joinToString(" ") { it.label + " " + it.stateDescription } + " " + windowTitle)
    }

    /**
     * Assinatura estrutural da tela — muda quando a página muda, ignora foco/cursor.
     * Inclui estado de marcação para detectar se um campo aceitou a resposta.
     */
    val signature: Int by lazy {
        var h = packageName.hashCode()
        for (n in nodes) {
            if (!n.isVisible) continue
            h = 31 * h + n.className.hashCode()
            h = 31 * h + n.label.hashCode()
            h = 31 * h + (if (n.isChecked) 1 else 0)
        }
        h
    }

    /** Assinatura apenas do conteúdo textual (ignora marcações) — detecta troca de página. */
    val contentSignature: Int by lazy {
        var h = packageName.hashCode()
        for (n in nodes) {
            if (!n.isVisible || n.isEditable) continue
            if (n.label.isNotBlank()) h = 31 * h + n.label.hashCode()
        }
        h
    }

    val textNodeCount: Int get() = visibleNodes.count { it.label.isNotBlank() }
}
