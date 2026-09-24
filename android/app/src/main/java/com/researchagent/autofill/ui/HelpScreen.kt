package com.researchagent.autofill.ui

import android.widget.LinearLayout

/** Aba "❓ Ajuda": o que faz cada botão da bolha e qual usar em cada situação. */
object HelpScreen {

    private class Item(val title: String, val what: String, val use: String, val tip: String? = null)

    private val bubble = listOf(
        Item("🔍 ATIVAR PESQUISA",
            "Liga o agente. Ele lê a tela, reconhece as perguntas e responde com os seus dados (Meus dados), a memória e — se ligado — os chutes.",
            "Abra a pesquisa no app ou site e toque aqui. Se a tela não for reconhecida em alguns segundos, aparece um aviso perguntando o que fazer.",
            "Se ele disser que não reconheceu a tela, toque em \"É UMA PESQUISA — TENTE MESMO ASSIM\"."),
        Item("⏸ PAUSAR / ▶ CONTINUAR",
            "Pausar congela o agente e guarda onde ele estava. Continuar retoma do mesmo ponto e relê a tela na hora.",
            "Use quando quiser mexer na tela você mesmo por um momento."),
        Item("⏹ PARAR",
            "Encerra a pesquisa atual. O aprendizado é mantido.",
            "Use para recomeçar do zero em outra pesquisa."),
        Item("🧠 ENSINAR PESQUISA (observar)",
            "NÃO cria atalho. Você responde a pesquisa normalmente e o agente OBSERVA: aprende qual botão avança, como abrir uma pesquisa na lista e quais respostas você escolhe.",
            "Use quando o agente não souber lidar com um app ou tipo de pesquisa. Faça 2–3 pesquisas assim para ele comparar e confirmar o padrão.",
            "Depois, use 🔍 ATIVAR PESQUISA normalmente: ele passa a usar o que aprendeu."),
        Item("⏺ GRAVAR AUTOMAÇÃO (operação)",
            "Cria um ATALHO: grava uma sequência de toques (ex.: abrir o app → aba Pesquisas → filtro → primeira pesquisa) para repetir depois.",
            "Toque em GRAVAR, faça a sequência e toque em SALVAR GRAVAÇÃO na bolha. Cada toque gravado aparece numa mensagem. Se não aparecer, o toque não foi captado (comum em conteúdo web): toque no TEXTO do botão.",
            "Grave começando sempre na mesma tela. Gravar a mesma sequência 2–3 vezes aumenta a confiança."),
        Item("▶ REPRODUZIR AUTOMAÇÃO",
            "Mostra as automações gravadas para o app aberto. As marcadas com ✓ combinam com a tela atual e começam no passo certo.",
            "Toque numa automação para executá-la. Se um passo não for encontrado, aparece um aviso: faça o passo você mesmo e toque em JÁ RESOLVI, ou IGNORAR para pular."),
        Item("🔮 ADIVINHAR PELA TELA",
            "Procura, entre as automações salvas, o passo que corresponde à tela atual e reproduz a partir dele.",
            "Use quando não lembrar qual automação usar. O agente também tenta isso sozinho quando está ativo e a tela não é uma pesquisa."),
        Item("🎯 CHUTAR RESPOSTAS",
            "Quando falta certeza, em vez de parar: 1) usa respostas que você já deu; 2) dados do perfil; 3) opções que batem com seus dados; 4) respostas de perguntas parecidas; 5) só então uma opção qualquer (prefere \"prefiro não dizer\").",
            "Ligue quando quiser que o agente não pare a cada pergunta desconhecida. Todo chute fica registrado como TENTATIVA e nunca vira dado seu.",
            "Dados sensíveis (CPF, renda, e-mail, telefone…) nunca são chutados."),
        Item("MODO",
            "Manual: só sugere. Assistido: responde e pede confirmação para avançar. Automático: responde e avança sozinho.",
            "Comece no Assistido. Passe para o Automático quando confiar nas respostas."),
        Item("❌ ENCERRAR APP",
            "Diferente de Pausar: para tudo, cancela automações e observação, remove a bolha e fecha as telas do app.",
            "Para usar de novo, abra o Research Agent — a bolha volta.")
    )

    private val boxes = listOf(
        "⚠ AÇÃO NECESSÁRIA — o agente precisa de você (dado que falta, CAPTCHA, login, erro na página).",
        "✋ RESOLVER E RESPONDER AGORA — esconde a caixa para você responder no próprio app. Depois toque na bolha (vermelha) → ✓ JÁ RESOLVI.",
        "✓ JÁ RESOLVI — o agente confere a tela. Se não achar sua resposta, avisa \"Não consegui identificar sua resposta\" e não avança.",
        "DIGITAR AQUI — abre uma janelinha para escrever a resposta e salvá-la no perfil. Fechar a janela não fecha o app da pesquisa.",
        "NÃO RECONHECI ESTA TELA — escolha: é uma pesquisa (tentar mesmo assim), reproduzir automação salva, vou mostrar (observar) ou pausar."
    )

    private val colors = listOf(
        "⚪ cinza — pronto, parado",
        "🟢 verde — respondendo pesquisa",
        "🟡 amarelo — pausado",
        "🔴 vermelho piscando — precisa de você",
        "🔵 azul — observando/aprendendo com você",
        "🟣 roxo — reproduzindo uma automação"
    )

    fun build(a: MainActivity, page: LinearLayout) {
        val ui = a.ui
        val intro = ui.card(page, "❓ Como usar")
        intro.addView(ui.muted("Toque na bolha \"RA\" para abrir o menu. Resumo rápido:", 13f))
        intro.addView(ui.text("• Responder uma pesquisa → 🔍 ATIVAR PESQUISA", 13.5f, Palette.Text, top = 6))
        intro.addView(ui.text("• Ensinar o agente a lidar com um app → 🧠 ENSINAR PESQUISA", 13.5f, Palette.Text, top = 2))
        intro.addView(ui.text("• Criar um atalho de toques repetitivos → ⏺ GRAVAR AUTOMAÇÃO", 13.5f, Palette.Text, top = 2))
        intro.addView(ui.text("• Repetir um atalho → ▶ REPRODUZIR AUTOMAÇÃO", 13.5f, Palette.Text, top = 2))

        val diff = ui.card(page, "Ensinar pesquisa × Gravar automação", Palette.Amber)
        diff.addView(ui.text("Use só UM de cada vez (a bolha não deixa ligar os dois).", 13.5f, Palette.Text, bold = true))
        diff.addView(ui.muted("🧠 ENSINAR = aprendizado geral. Você responde e ele aprende o SIGNIFICADO dos botões e as suas respostas. " +
            "Serve para qualquer pesquisa parecida, mesmo com textos diferentes.", 12.5f, top = 6))
        diff.addView(ui.muted("⏺ GRAVAR = atalho exato. Ele repete a MESMA sequência de toques que você gravou. " +
            "Serve para caminhos fixos, como abrir a lista de pesquisas e aplicar um filtro.", 12.5f, top = 6))
        diff.addView(ui.muted("Para ensinar a responder uma pesquisa: use 🧠 ENSINAR. Para ensinar como chegar até a pesquisa: use ⏺ GRAVAR.", 12.5f, top = 6))

        val b = ui.card(page, "Botões da bolha")
        bubble.forEach { it ->
            b.addView(ui.text(it.title, 14.5f, Palette.Green, bold = true, top = 12))
            b.addView(ui.text(it.what, 13f, Palette.Text, top = 2))
            b.addView(ui.muted("Quando usar: ${it.use}", 12.5f, top = 2))
            it.tip?.let { t -> b.addView(ui.muted("Dica: $t", 12.5f, top = 2)) }
        }

        val box = ui.card(page, "Caixas de aviso")
        boxes.forEach { box.addView(ui.muted(it, 12.5f, top = 6)) }

        val c = ui.card(page, "Cores da bolha")
        colors.forEach { c.addView(ui.muted(it, 13f, top = 4)) }

        val tr = ui.card(page, "Se algo não funcionar")
        listOf(
            "Gravação não registra um passo: o app não informou o toque. Toque no texto visível do botão. Se não houver texto, esse passo não será reproduzido — faça-o à mão quando o aviso aparecer.",
            "Ativei e nada acontece: espere alguns segundos. Se a tela não for reconhecida, aparece o aviso \"Não reconheci esta tela\" com opções.",
            "Automação parou: o passo não foi encontrado. Faça o passo e toque em JÁ RESOLVI, ou IGNORAR para pular.",
            "Nomes das automações: o agente usa o nome do app e do site/tela. Você pode renomear na aba 🧠 Aprender.",
            "Tudo o que foi observado, aprendido e chutado aparece nas abas Logs e 🧠 Aprender. Lá você também pode apagar padrões e automações."
        ).forEach { tr.addView(ui.muted("• $it", 12.5f, top = 6)) }
    }
}
