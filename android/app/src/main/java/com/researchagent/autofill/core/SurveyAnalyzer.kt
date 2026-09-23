package com.researchagent.autofill.core

enum class QuestionType { TEXT, NUMBER, DATE, SINGLE_CHOICE, MULTI_CHOICE, DROPDOWN, SLIDER }

data class QuestionOption(val text: String, val nodeId: Int, val checked: Boolean)

data class SurveyQuestion(
    val text: String,
    val type: QuestionType,
    val options: List<QuestionOption> = emptyList(),
    /** Campo de entrada (EditText, Spinner, SeekBar) quando aplicável. */
    val inputNodeId: Int? = null,
    val currentValue: String = "",
    val hint: String = "",
    val answered: Boolean = false,
    val required: Boolean = false
) {
    val key: String get() = Text.questionKey(text)
    val optionTexts: List<String> get() = options.map { it.text }
}

/** Motivos que exigem intervenção humana (Seções 15–18, 29, 33, 41). */
enum class InterventionReason(val title: String) {
    CAPTCHA("CAPTCHA / verificação humana detectada"),
    LOGIN("Login, senha ou código de verificação"),
    FINANCIAL("Tela de pagamento ou ação financeira"),
    BLOCKED_APP("Aplicativo bloqueado ou não autorizado"),
    MISSING_INFO("Informação necessária"),
    CONFLICT("Conflito de informação no perfil"),
    VALIDATION_ERROR("A página indicou erro de validação"),
    STUCK("Não consegui avançar com segurança"),
    CONFIRM_NEXT("Confirmar avanço (modo assistido)"),
    SENSITIVE("Informação sensível não autorizada")
}

data class ScreenGuard(val reason: InterventionReason, val evidence: String)

data class SurveyPage(
    val isSurvey: Boolean,
    val score: Int,
    val questions: List<SurveyQuestion>,
    val nextButton: ScreenNode?,
    val submitButton: ScreenNode?,
    val progress: Pair<Int, Int>?,
    val completed: Boolean,
    val validationError: String?,
    val guard: ScreenGuard?,
    val startSurveyButtons: List<ScreenNode>
)

/**
 * Análise semântica da tela: detecção de pesquisa, extração de perguntas,
 * botões de navegação, conclusão e sinais de risco. 100% independente do Android.
 */
object SurveyAnalyzer {

    // ── Vocabulário multilíngue (Seção 12) ──────────────────────────
    val NEXT_WORDS = listOf(
        "proximo", "proxima", "continuar", "avancar", "seguinte", "prosseguir", "next", "continue", "proceed",
        "siguiente", "continuar", "weiter", "suivant", "avanti", "ir para a proxima", "proxima pagina", "proxima pergunta",
        "next page", "next question", ">", ">>", "→"
    )
    val SUBMIT_WORDS = listOf(
        "enviar", "finalizar", "concluir", "submit", "finish", "done", "send", "terminar", "enviar respostas",
        "enviar pesquisa", "submit survey", "complete", "confirmar respostas", "entregar", "enviar formulario"
    )
    val AVOID_WORDS = listOf(
        "voltar", "anterior", "back", "previous", "cancelar", "cancel", "limpar", "clear", "sair", "exit", "logout",
        "excluir", "delete", "apagar", "comprar", "buy", "pagar", "pay", "assinar", "subscribe", "compartilhar", "share",
        "denunciar", "report", "desinstalar", "uninstall", "transferir", "sacar", "resgatar"
    )
    val COMPLETION_PHRASES = listOf(
        "pesquisa concluida", "obrigado por participar", "obrigada por participar", "respostas enviadas",
        "resposta enviada", "sua resposta foi registrada", "suas respostas foram registradas", "survey complete",
        "survey completed", "thank you for your participation", "thank you for participating", "thanks for participating",
        "thank you for completing", "submission successful", "your response has been recorded", "finished",
        "agradecemos sua participacao", "questionario concluido", "gracias por participar", "encuesta completada",
        "formulario enviado", "voce concluiu", "you have completed", "thank you for taking"
    )
    val START_SURVEY_WORDS = listOf(
        "iniciar pesquisa", "comecar pesquisa", "responder pesquisa", "comecar", "iniciar", "responder agora",
        "participar", "start survey", "take survey", "begin survey", "start", "take the survey", "nova pesquisa",
        "comenzar encuesta", "iniciar encuesta", "comecar agora"
    )
    val SURVEY_WORDS = listOf(
        "pesquisa", "questionario", "enquete", "formulario", "survey", "questionnaire", "poll", "form", "encuesta",
        "pergunta", "question", "responda", "answer", "resposta", "opiniao"
    )
    val CAPTCHA_WORDS = listOf(
        "captcha", "recaptcha", "hcaptcha", "nao sou um robo", "i m not a robot", "im not a robot", "i am not a robot",
        "verify you are human", "verifique que voce e humano", "confirme que voce e humano", "checking your browser",
        "verificando seu navegador", "cloudflare", "select all images", "selecione todas as imagens",
        "clique em cada imagem", "prove que voce e humano", "turnstile", "verificacao de seguranca", "human verification",
        "arraste a peca", "slide to verify", "deslize para verificar"
    )
    val CODE_WORDS = listOf(
        "codigo de verificacao", "codigo de seguranca", "verification code", "one time password", "otp", "2fa",
        "autenticacao de dois fatores", "two factor", "token de acesso", "digite o codigo", "enter the code",
        "codigo enviado", "biometria", "impressao digital", "fingerprint", "codigo sms"
    )
    val LOGIN_WORDS = listOf(
        "senha", "password", "contrasena", "faca login", "fazer login", "sign in", "log in", "entrar na sua conta",
        "acesse sua conta", "entrar com google", "iniciar sesion"
    )
    val FINANCIAL_WORDS = listOf(
        "numero do cartao", "card number", "cvv", "codigo de seguranca do cartao", "data de validade do cartao",
        "finalizar compra", "checkout", "pagamento", "payment", "pix copia e cola", "chave pix", "transferencia",
        "confirmar pagamento", "confirm payment", "boleto", "saldo", "extrato", "pagar agora", "comprar agora",
        "valor da transferencia", "resgate", "saque"
    )
    val ERROR_WORDS = listOf(
        "campo obrigatorio", "este campo e obrigatorio", "esta pergunta e obrigatoria", "resposta obrigatoria",
        "this is a required question", "this field is required", "required field", "is required", "please answer",
        "responda esta pergunta", "selecione uma opcao", "please select", "formato invalido", "invalid format",
        "valor invalido", "invalid value", "invalido", "invalid", "preencha este campo", "please fill",
        "fora do intervalo", "out of range", "campo requerido", "esta pregunta es obligatoria"
    )

    private val PROGRESS_REGEX = Regex("\\b(\\d{1,3})\\s*(?:de|of|/|del)\\s*(\\d{1,3})\\b")
    private val PERCENT_REGEX = Regex("\\b(\\d{1,3})\\s*%")

    fun analyze(s: ScreenSnapshot): SurveyPage {
        val text = s.normalizedText
        val guard = detectGuard(s)
        val questions = extractQuestions(s)
        val next = findButton(s, NEXT_WORDS)
        val submit = findButton(s, SUBMIT_WORDS)
        val rawText = s.visibleNodes.joinToString(" ") { it.label }.lowercase()
        val progress = PROGRESS_REGEX.find(rawText)?.let { m ->
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
            if (b in 2..200 && a in 0..b) a to b else null
        }
        val completed = COMPLETION_PHRASES.any { containsPhrase(text, it) } && questions.none { !it.answered }
        val error = ERROR_WORDS.firstOrNull { containsPhrase(text, it) }

        var score = 0
        score += questions.size * 3
        score += questions.count { it.type == QuestionType.SINGLE_CHOICE || it.type == QuestionType.MULTI_CHOICE } * 2
        if (progress != null) score += 3
        if (PERCENT_REGEX.containsMatchIn(text) && questions.isNotEmpty()) score += 1
        if (SURVEY_WORDS.any { containsPhrase(text, it) }) score += 2
        if (next != null || submit != null) score += 2
        val isSurvey = questions.isNotEmpty() && score >= 5

        val starts = s.visibleNodes.filter { n ->
            (n.isClickable || n.kind == WidgetKind.BUTTON) && n.label.length <= 40 &&
                START_SURVEY_WORDS.any { w -> Text.normalize(n.label).let { it == w || it.startsWith("$w ") } } &&
                AVOID_WORDS.none { w -> containsPhrase(Text.normalize(n.label), w) }
        }
        return SurveyPage(isSurvey, score, questions, next, submit, progress, completed, error, guard, starts)
    }

    /** Frases de erro presentes na tela — comparadas antes/depois de avançar (Seção 28). */
    fun errorPhrases(s: ScreenSnapshot): Set<String> =
        ERROR_WORDS.filter { containsPhrase(s.normalizedText, it) }.toSet()

    fun containsPhrase(normalizedText: String, phrase: String): Boolean {
        val p = Text.normalize(phrase).ifEmpty { phrase }
        if (p.isEmpty()) return false
        if (p.length <= 3) return Regex("(^|\\s)" + Regex.escape(p) + "($|\\s)").containsMatchIn(normalizedText)
        return (" $normalizedText ").contains(" $p ") || (p.contains(' ') && normalizedText.contains(p))
    }

    // ── Sinais de risco (Seções 16, 17, 18) ────────────────────────
    fun detectGuard(s: ScreenSnapshot): ScreenGuard? {
        val text = s.normalizedText
        CAPTCHA_WORDS.firstOrNull { containsPhrase(text, it) }?.let { return ScreenGuard(InterventionReason.CAPTCHA, it) }
        val lowerClasses = s.visibleNodes.map { it.className.lowercase() + " " + it.viewId.lowercase() }
        if (lowerClasses.any { it.contains("captcha") || it.contains("recaptcha") }) {
            return ScreenGuard(InterventionReason.CAPTCHA, "widget captcha")
        }
        val passwordField = s.visibleNodes.firstOrNull { it.isPassword && it.isEditable }
        if (passwordField != null) return ScreenGuard(InterventionReason.LOGIN, "campo de senha")
        val edits = s.visibleNodes.filter { it.isEditable }
        if (edits.isNotEmpty()) {
            CODE_WORDS.firstOrNull { containsPhrase(text, it) }?.let { return ScreenGuard(InterventionReason.LOGIN, it) }
            // formulário de login típico: poucos campos, nenhuma opção de pesquisa, termos de login
            val hasOptions = s.visibleNodes.any { it.isOption }
            if (!hasOptions && edits.size <= 3) {
                val hits = LOGIN_WORDS.filter { containsPhrase(text, it) }
                if (hits.any { it == "senha" || it == "password" || it == "contrasena" }) {
                    return ScreenGuard(InterventionReason.LOGIN, hits.joinToString())
                }
            }
        }
        val financialHits = FINANCIAL_WORDS.filter { containsPhrase(text, it) }
        if (financialHits.size >= 2 || financialHits.any { it in listOf("numero do cartao", "card number", "cvv", "chave pix", "pix copia e cola") }) {
            return ScreenGuard(InterventionReason.FINANCIAL, financialHits.joinToString())
        }
        return null
    }

    // ── Botões de navegação ─────────────────────────────────────────
    fun findButton(s: ScreenSnapshot, words: List<String>): ScreenNode? {
        val candidates = s.visibleNodes.filter { n ->
            n.isEnabled && (n.isClickable || n.kind == WidgetKind.BUTTON || n.fromOcr) && !n.isOption && !n.isEditable &&
                n.label.isNotBlank() && n.label.length <= 40
        }
        var best: ScreenNode? = null
        var bestScore = 0
        for (n in candidates) {
            val lbl = Text.normalize(n.label).ifEmpty { n.label.trim() }
            if (AVOID_WORDS.any { containsPhrase(lbl, it) }) continue
            var score = 0
            for (w in words) {
                val nw = Text.normalize(w).ifEmpty { w }
                score = maxOf(score, when {
                    lbl == nw -> 10
                    lbl.startsWith("$nw ") || lbl.endsWith(" $nw") -> 7
                    nw.length > 3 && containsPhrase(lbl, nw) && lbl.split(' ').size <= 4 -> 5
                    else -> 0
                })
            }
            if (score > 0 && n.kind == WidgetKind.BUTTON) score += 2
            if (score > bestScore || (score == bestScore && best != null && n.bounds.top > best.bounds.top)) {
                best = n; bestScore = score
            }
        }
        return best
    }

    // ── Extração de perguntas (Seção 12) ────────────────────────────
    fun extractQuestions(s: ScreenSnapshot): List<SurveyQuestion> {
        val visible = s.nodes.filter { it.isVisible }
        val optionNodes = visible.filter { it.isOption }
        val consumedText = HashSet<Int>()
        val result = ArrayList<Pair<Int, SurveyQuestion>>() // (ordem, pergunta)

        // 1) Agrupa opções (radio/checkbox) pelo menor ancestral comum com ≥2 opções do mesmo tipo.
        val groups = LinkedHashMap<Int, MutableList<ScreenNode>>()
        for (opt in optionNodes) {
            val kind = opt.kind
            val container = s.ancestors(opt).firstOrNull { anc ->
                s.descendants(anc).count { it.isVisible && it.isOption && sameOptionFamily(it.kind, kind) } >= 2
            }
            val gid = container?.id ?: (-opt.id - 1)
            groups.getOrPut(gid) { ArrayList() }.add(opt)
        }
        for ((gid, opts) in groups) {
            val first = opts.first()
            val options = opts.map { o ->
                val lbl = optionLabel(s, o)
                consumedText += o.id
                s.descendants(o).forEach { consumedText += it.id }
                labelSiblingIds(s, o).forEach { consumedText += it }
                QuestionOption(lbl, o.id, o.isChecked)
            }.filter { it.text.isNotBlank() }
            if (options.isEmpty()) continue
            val isRadio = opts.all { it.kind == WidgetKind.RADIO }
            val qText = questionTextBefore(s, visible, first, consumedText, gid)
            val type = if (isRadio || (opts.size == 1 && opts[0].kind != WidgetKind.CHECKBOX)) QuestionType.SINGLE_CHOICE
                       else QuestionType.MULTI_CHOICE
            val required = qText.contains('*')
            result += visible.indexOf(first) to SurveyQuestion(
                text = cleanQuestion(qText), type = type, options = options,
                answered = options.any { it.checked }, required = required
            )
        }

        // 2) Campos de texto, listas suspensas e sliders.
        for (n in visible) {
            val kind = n.kind
            if (kind != WidgetKind.EDIT && kind != WidgetKind.DROPDOWN && kind != WidgetKind.SLIDER) continue
            if (n.isPassword) continue
            val qText = questionTextBefore(s, visible, n, consumedText, null).ifBlank { n.hint.ifBlank { n.contentDescription } }
            if (qText.isBlank()) continue
            val current = when (kind) {
                WidgetKind.EDIT -> if (n.text.isNotBlank() && n.text != n.hint) n.text else ""
                WidgetKind.DROPDOWN -> n.text.ifBlank { s.descendants(n).firstOrNull { it.label.isNotBlank() }?.label.orEmpty() }
                else -> n.rangeCurrent?.toString().orEmpty()
            }
            val placeholderish = current.isBlank() || isPlaceholder(current)
            val type = when (kind) {
                WidgetKind.DROPDOWN -> QuestionType.DROPDOWN
                WidgetKind.SLIDER -> QuestionType.SLIDER
                else -> inferTextType(qText, n)
            }
            result += visible.indexOf(n) to SurveyQuestion(
                text = cleanQuestion(qText), type = type, inputNodeId = n.id,
                currentValue = if (placeholderish) "" else current, hint = n.hint,
                answered = !placeholderish && kind != WidgetKind.SLIDER,
                required = qText.contains('*')
            )
        }
        return result.sortedBy { it.first }.map { it.second }.distinctBy { it.text + it.inputNodeId + it.options.size }
    }

    private fun sameOptionFamily(a: WidgetKind, b: WidgetKind): Boolean =
        a == b || (a != WidgetKind.RADIO && b != WidgetKind.RADIO)

    private fun isPlaceholder(v: String): Boolean {
        val n = Text.normalize(v)
        return n.isEmpty() || n in setOf("selecione", "escolha", "select", "choose", "selecionar", "seleccione",
            "escolher", "selecione uma opcao", "select an option", "choose an option", "sua resposta", "your answer")
    }

    private fun inferTextType(q: String, n: ScreenNode): QuestionType {
        val t = Text.normalize(q + " " + n.hint)
        // InputType: TYPE_CLASS_NUMBER=2, TYPE_CLASS_DATETIME=4
        val cls = n.inputType and 0x0f
        return when {
            cls == 4 || t.contains("dd mm") || t.contains("data de") || t.contains("date") -> QuestionType.DATE
            cls == 2 || t.startsWith("quant") || t.contains(" quantos ") || t.contains("how many") -> QuestionType.NUMBER
            else -> QuestionType.TEXT
        }
    }

    private fun optionLabel(s: ScreenSnapshot, opt: ScreenNode): String {
        opt.label.takeIf { it.isNotBlank() }?.let { return it }
        s.descendants(opt).firstOrNull { it.label.isNotBlank() }?.let { return it.label }
        // rótulo em irmão imediatamente seguinte (padrão comum em WebViews)
        val parent = s.parent(opt) ?: return ""
        val sibs = s.children(parent)
        val idx = sibs.indexOfFirst { it.id == opt.id }
        return sibs.drop(idx + 1).firstOrNull { it.label.isNotBlank() && !it.isOption }?.label.orEmpty()
    }

    private fun labelSiblingIds(s: ScreenSnapshot, opt: ScreenNode): List<Int> {
        if (opt.label.isNotBlank()) return emptyList()
        val parent = s.parent(opt) ?: return emptyList()
        val sibs = s.children(parent)
        val idx = sibs.indexOfFirst { it.id == opt.id }
        val next = sibs.drop(idx + 1).firstOrNull { it.label.isNotBlank() && !it.isOption } ?: return emptyList()
        return listOf(next.id) + s.descendants(next).map { it.id }
    }

    /**
     * Texto da pergunta: último nó de texto "livre" antes do elemento, na ordem da árvore,
     * que não seja rótulo de opção, botão ou texto já usado.
     */
    private fun questionTextBefore(
        @Suppress("UNUSED_PARAMETER") s: ScreenSnapshot, visible: List<ScreenNode>, anchor: ScreenNode,
        consumed: Set<Int>, @Suppress("UNUSED_PARAMETER") groupId: Int?
    ): String {
        val idx = visible.indexOf(anchor)
        if (idx <= 0) return ""
        // preferir texto dentro do mesmo contêiner do grupo
        for (i in idx - 1 downTo maxOf(0, idx - 40)) {
            val n = visible[i]
            if (n.id in consumed || n.isOption || n.isEditable) continue
            if (n.kind == WidgetKind.BUTTON || n.kind == WidgetKind.PROGRESS) continue
            val lbl = n.label
            if (lbl.length < 3) continue
            val norm = Text.normalize(lbl)
            if (norm.isEmpty()) continue
            if (PROGRESS_REGEX.matches(lbl.lowercase().trim()) || norm == "obrigatorio" || norm == "required") continue
            if (ERROR_WORDS.any { norm == Text.normalize(it) }) continue
            return lbl
        }
        return ""
    }

    fun cleanQuestion(q: String): String =
        q.replace(Regex("\\s*\\*\\s*$"), "").replace(Regex("\\s+"), " ").trim()
}
