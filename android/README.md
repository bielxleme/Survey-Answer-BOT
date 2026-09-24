# Research Agent — app Android nativo (Kotlin)

Agente que lê pesquisas/formulários na tela, entende as perguntas e responde **somente** com dados verdadeiros do seu perfil. Quando falta informação, há CAPTCHA, login, pagamento ou conflito, ele **pausa, toca um som e pede ajuda**.

## Por que o APK antigo não abria

O APK anterior não era este app: era um template genérico de WebView (`com.webview.myapplication`) "remendado" pelo servidor web.
O remendo trocava uma string dentro do `classes.dex`, o que quebrava a **ordenação obrigatória da tabela de strings do DEX** — o Android rejeitava o código e o app fechava na hora. Mesmo que abrisse, a página ficaria em branco (o bundle do Vite com `import.meta` era injetado como script clássico).
Este projeto substitui aquilo por um app nativo real, compilado pelo Gradle.

## Como obter o APK

**Opção A — GitHub (sem instalar nada):**
1. Envie a pasta `android/` e o arquivo `.github/workflows/android-apk.yml` para o repositório.
2. O GitHub Actions compila sozinho a cada push (ou em *Actions → APK Android → Run workflow*).
3. Baixe `ResearchAgent.apk` em **Releases** (ou no artefato da execução).

**Opção B — Android Studio:** abra a pasta `android/` → *Build → Build APK(s)*.
Pela linha de comando: `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.

## Instalação no celular

1. Abra o `ResearchAgent.apk` e permita "instalar apps desconhecidos".
   Se você tinha instalado o APK antigo, pode desinstalá-lo (ele é outro app).
2. Abra o Research Agent → preencha **Meus dados** (assistente, manual ou importando JSON).
3. **Configurações → Acessibilidade → Research Agent → Ativar.**
   Android 13+: se aparecer *"Configuração restrita"*, vá em **Informações do app → ⋮ → Permitir configurações restritas** e tente de novo.
4. Uma bolha **RA** aparece na lateral. Abra a pesquisa e toque na bolha → **ATIVAR PESQUISA**.

## Arquitetura

```
core/            ← lógica pura em Kotlin (sem Android), 100% testada na JVM
  Text             normalização multilíngue, stemming leve
  ProfileSchema    ~90 campos em 15 categorias + campos personalizados
  UserProfile      dado FORNECIDO / DERIVADO / INDISPONÍVEL, derivações lógicas (idade, nº de filhos…)
  ScreenModel      cópia imutável da árvore de acessibilidade
  SurveyAnalyzer   SurveyDetector, QuestionExtractor, botões Próximo/Enviar, conclusão, CAPTCHA/login/pagamento
  QuestionClassifier  pergunta → campo do perfil (semântico + aprendido)
  AnswerEngine     motor de respostas determinístico + ConfidenceEngine (faixas, sim/não, listas, datas)
  AnswerValidator  camada anti-alucinação para respostas de IA
  HybridAnswerProvider  regras primeiro; IA opcional, sempre validada
  AgentEngine      máquina de estados (IDLE → SCANNING → … → RESEARCH_COMPLETED), watchdog anti-loop
  ProfileLearning  salvar respostas manuais no perfil e detectar conflitos
accessibility/   SurveyAccessibilityService + AccessibilityDriver (ações semânticas, gestos só como fallback)
overlay/         bolha + painel + cartão de intervenção (TYPE_ACCESSIBILITY_OVERLAY — sem permissão de sobreposição)
ocr/             ML Kit on-device (fallback quando a tela não tem texto acessível)
ai/              Gemini e OpenAI-compatível (OpenAI, Ollama, LM Studio) — contrato JSON estrito
automation/      AgentController (ponte Android ↔ motor) e InterventionBus
data/            armazenamento criptografado (Android Keystore, AES-256-GCM), JSON, logs, estatísticas
notifications/   som + notificação quando precisa de você
ui/              telas com Views nativas (Painel, Meus dados, Logs, Ajustes, Assistente, Intervenção)
```

## Evolução autodidata (v2.1)

Menu da bolha: 🔍 Ativar pesquisa · 🧠 Ensinar automação · ⚙️ Automatizar operação · 🎯 Chutar respostas · ⏸ Pausar/▶ Continuar · ⏹ Parar · ❌ Encerrar aplicativo.

- **❌ Encerrar aplicativo** (diferente de Pausar): para a automação, cancela tarefas e observação, remove a bolha e fecha as telas do app. A bolha volta quando você abre o app de novo (opção: também desligar o serviço de acessibilidade).
- **🎯 Chutar respostas**: com confiança alta o agente executa; com confiança baixa escolhe a alternativa mais provável e registra como **TENTATIVA** (resposta, confiança, origem, resultado). Chute nunca vira verdade e dados sensíveis nunca são chutados.
- **Observação**: se a pesquisa não for reconhecida, o agente observa os seus toques (texto, tipo, hierarquia, posição relativa e mudança de tela — sem coordenadas fixas e sem campos de senha) e aprende o papel de cada elemento (avançar, iniciar, filtro, aba, lista…). O que aprende vale para qualquer app.
- **⚙️ Automatizar operação**: grave uma operação uma vez e reproduza depois. Cada passo é localizado por semântica, então funciona em outra resolução e com outros textos. Várias demonstrações separam o que é fixo do que muda e aumentam a confiança.
- **Caixa de atenção**: "Resolver e responder agora" esconde a caixa para você usar o app da pesquisa normalmente. Depois, toque na bolha → **✓ JÁ RESOLVI**. Fechar a caixa nunca fecha o app da pesquisa.
- **Já resolvi**: o agente relê a tela, compara com o estado anterior e só continua se identificar a sua resposta. Se não identificar, avisa: "Não consegui identificar sua resposta."
- **Loops**: o agente tenta outra estratégia, depois a memória e, por fim, pede ajuda. O erro fica registrado.
- **Faixas de confiança configuráveis** (padrão: 95/80/60), exibidas por pesquisa, pergunta, respostas, botão, decisão e resultado.
- **Aba 🧠 Aprender**: pesquisas observadas, fluxos, padrões, respostas aprendidas, tentativas, intervenções, confiança média e tarefa atual. Tudo pode ser consultado e excluído.

Nenhuma regra é específica de um app. O Pawns.app serve apenas como exemplo de validação.

## Correções de usabilidade (v2.2)

- **Aba ❓ Ajuda** no app: explica cada botão da bolha, as caixas de aviso e as cores. Também pode ser aberta pela bolha (❓).
- **🧠 ENSINAR PESQUISA × ⏺ GRAVAR AUTOMAÇÃO**: cada botão tem uma legenda curta, e os dois não podem ser ligados ao mesmo tempo. Ensinar = o agente aprende observando você; Gravar = atalho exato para repetir.
- **Gravação com retorno**: cada toque gravado aparece numa mensagem ("Passo 3 gravado: tocou em …"). Se a tela mudar sem toque captado, aparece um aviso. Toques sem nó localizado são gravados pelo texto, id e região informados pelo Android.
- **Nomes legíveis**: as automações usam o nome do app e do site ou tela (ex.: "Pawns · Pesquisas disponíveis") em vez de "com.iproyal…".
- **▶ REPRODUZIR AUTOMAÇÃO** na bolha, com as automações do app aberto. As marcadas com ✓ combinam com a tela atual e começam no passo certo. **🔮 Adivinhar pela tela** encontra sozinho o passo correspondente. Com o agente ativo, isso também é tentado automaticamente.
- **Passo não encontrado** na reprodução: aparece um aviso (fazer à mão + JÁ RESOLVI, IGNORAR o passo ou PARAR), sem parar em silêncio.
- **Tela não reconhecida**: logo após ATIVAR, em vez de ficar parado, aparece o aviso "Não reconheci esta tela" com as opções: é uma pesquisa (tentar mesmo assim), reproduzir automação, vou mostrar ou pausar.
- **ATIVAR/CONTINUAR responde na hora**: as esperas longas foram trocadas por esperas que acordam quando você toca na bolha. Um indicador de carregamento que nunca some não trava mais o agente. O menu não é reconstruído a cada atualização de status, que antes "engolia" toques.
- **🎯 Chutar respostas** usa primeiro o que já existe (suas respostas anteriores, dados do perfil, opções que batem com "Meus dados", perguntas parecidas) e só então uma opção qualquer.

## Segurança e privacidade

- Nunca inventa dados: sem valor no perfil → `ASK_USER`.
- Respostas da IA passam pelo `AnswerValidator`: precisam citar um campo enviado, ser uma opção existente e ser comprovadas pelo valor.
- Envio mínimo à IA: 1º só os **nomes** dos campos disponíveis; 2º só os valores relevantes. Campos 🔒 (CPF, renda, e-mail, telefone…) nunca saem do aparelho.
- CAPTCHA, login/2FA, telas de pagamento, apps bancários/mensageiros e conflitos sempre pausam.
- Perfil, logs e aprendizado criptografados; backup em nuvem desativado; telas protegidas contra captura (opcional).

## Testes

`./gradlew testReleaseUnitTest` — 40 testes (motor de respostas, validador, analisador de tela, JSON, aprendizado e um teste de integração que percorre uma pesquisa simulada de 2 páginas nos modos automático, assistido e manual).

## Assinatura

Sem configuração, o build usa `keystore/research-agent.jks` (senha `researchagent`) para que todas as versões tenham a mesma assinatura e atualizem por cima. Para distribuir a terceiros, gere sua própria chave e configure os *secrets* `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` e `SIGNING_KEY_PASSWORD`.
