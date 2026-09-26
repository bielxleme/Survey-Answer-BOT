import React from 'react';
import { HelpCircle, Play, Brain, Sparkles, Smartphone, ShieldCheck, AlertTriangle, FileText, CheckCircle2 } from 'lucide-react';

export const HelpView: React.FC = () => {
  return (
    <div className="max-w-5xl mx-auto py-6 px-4 space-y-6 text-slate-100">
      {/* Header Banner */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl flex items-center justify-between">
        <div>
          <div className="flex items-center space-x-2">
            <HelpCircle className="w-5 h-5 text-emerald-400" />
            <h2 className="text-base font-bold text-white uppercase tracking-wider">
              Guia Completo de Uso do Research Agent (v2.3)
            </h2>
          </div>
          <p className="text-xs text-slate-400 mt-1">
            Entenda cada botão da bolha, as caixas de intervenção e como o agente aprende com você.
          </p>
        </div>
      </div>

      {/* Quick Summary Card */}
      <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-4 space-y-2">
        <h3 className="font-bold text-sm text-emerald-400 flex items-center space-x-1.5">
          <Play className="w-4 h-4" />
          <span>Resumo Rápido — Quando usar cada função:</span>
        </h3>
        <ul className="text-xs text-slate-300 space-y-1.5 pl-2">
          <li>• <strong className="text-white">Responder uma pesquisa</strong> → toque na bolha → <span className="text-emerald-400 font-bold">🔍 ATIVAR PESQUISA</span></li>
          <li>• <strong className="text-white">Ensinar o agente a lidar com um app/pesquisa</strong> → <span className="text-blue-400 font-bold">🧠 ENSINAR PESQUISA</span> (ele observa suas ações; toques na bolha são ignorados)</li>
          <li>• <strong className="text-white">Criar um atalho de toques repetitivos</strong> → <span className="text-amber-400 font-bold">⏺ GRAVAR AUTOMAÇÃO</span></li>
          <li>• <strong className="text-white">Repetir um atalho criado</strong> → <span className="text-purple-400 font-bold">▶ REPRODUZIR AUTOMAÇÃO</span></li>
          <li>• <strong className="text-white">Exportar relatório para o engenheiro/IA</strong> → <span className="text-cyan-400 font-bold">📋 GRAVAR LOGS</span> (mínimo de tokens)</li>
          <li>• <strong className="text-white">Corrigir leitura truncada ou incorreta</strong> → <span className="text-rose-400 font-bold">✏ PERGUNTA INCORRETA</span> (leitor inteligente de frase completa)</li>
        </ul>
      </div>

      {/* Ensinar vs Gravar */}
      <div className="bg-amber-500/10 border border-amber-500/30 rounded-2xl p-4 space-y-2">
        <h3 className="font-bold text-sm text-amber-300 flex items-center space-x-1.5">
          <Brain className="w-4 h-4 text-amber-400" />
          <span>🧠 Ensinar Pesquisa × ⏺ Gravar Automação (Diferença Crucial)</span>
        </h3>
        <p className="text-xs text-slate-300 leading-relaxed">
          Use apenas <strong>um de cada vez</strong>. A bolha e o sistema impedem ativar ambos simultaneamente.
        </p>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3 pt-1 text-xs">
          <div className="bg-slate-900/60 p-3 rounded-xl border border-slate-800">
            <span className="font-bold text-blue-400 block mb-1">🧠 ENSINAR (Observar)</span>
            <p className="text-slate-400">
              Aprendizado conceitual e semântico. Você responde normalmente e o agente aprende o significado de botões, tipos de respostas e campos. Vale para qualquer formulário similar, mesmo com textos diferentes.
            </p>
            <p className="text-[11px] text-slate-500 mt-2">
              * Toques na bolha flutuante são automaticamente ignorados para não poluir a observação.
            </p>
          </div>
          <div className="bg-slate-900/60 p-3 rounded-xl border border-slate-800">
            <span className="font-bold text-amber-400 block mb-1">⏺ GRAVAR (Atalho Fixo)</span>
            <p className="text-slate-400">
              Atalho exato de passos. Repete a mesma sequência de toques em botões específicos (ex: abrir app → aba pesquisas → filtro → iniciar). Serve para caminhos fixos de navegação.
            </p>
          </div>
        </div>
      </div>

      {/* Bubble Buttons Detail Grid */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl space-y-4">
        <h3 className="font-bold text-sm text-white border-b border-slate-800 pb-2">
          Botões da Bolha Flutuante & Funções do Painel
        </h3>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
          <div className="p-3 bg-slate-950/70 border border-slate-800/80 rounded-xl space-y-1">
            <span className="font-bold text-emerald-400">🔍 ATIVAR PESQUISA</span>
            <p className="text-slate-300">
              Inicia o agente autônomo. Ele lê a tela, reconhece perguntas e preenche com fatos do seu perfil.
            </p>
            <p className="text-slate-500 text-[11px]">
              Se a resposta não couber na tela, o agente desce e rola automaticamente procurando a opção correta.
            </p>
          </div>

          <div className="p-3 bg-slate-950/70 border border-slate-800/80 rounded-xl space-y-1">
            <span className="font-bold text-cyan-400">📋 GRAVAR LOGS (Diagnóstico IA)</span>
            <p className="text-slate-300">
              Armazena em um lugar único os cliques, erros e eventos onde a automação falhou.
            </p>
            <p className="text-slate-500 text-[11px]">
              Exporta um relatório ultra-compacto com gasto mínimo de tokens para análise e correção por IA.
            </p>
          </div>

          <div className="p-3 bg-slate-950/70 border border-slate-800/80 rounded-xl space-y-1">
            <span className="font-bold text-rose-400">✏ PERGUNTA INCORRETA</span>
            <p className="text-slate-300">
              Permite corrigir uma pergunta que o agente leu truncada ou incorreta (destacada em vermelho entre aspas).
            </p>
            <p className="text-slate-500 text-[11px]">
              Basta selecionar qualquer trecho ou letra: o leitor inteligente expande e lê a frase completa.
            </p>
          </div>

          <div className="p-3 bg-slate-950/70 border border-slate-800/80 rounded-xl space-y-1">
            <span className="font-bold text-purple-400">🎯 CHUTAR RESPOSTAS</span>
            <p className="text-slate-300">
              Em vez de travar quando falta certeza, escolhe a resposta mais provável usando respostas anteriores e perfil.
            </p>
            <p className="text-slate-500 text-[11px]">
              Fica registrado como tentativa e dados sensíveis (CPF, renda, etc.) NUNCA são chutados.
            </p>
          </div>

          <div className="p-3 bg-slate-950/70 border border-slate-800/80 rounded-xl space-y-1">
            <span className="font-bold text-amber-400">✓ JÁ RESOLVI NA TELA</span>
            <p className="text-slate-300">
              Usado quando o agente pediu sua intervenção. Ao marcar ou digitar, toque aqui.
            </p>
            <p className="text-slate-500 text-[11px]">
              No modo automático, assim que você resolve, o app clica em "Continuar/Avançar" sozinho!
            </p>
          </div>

          <div className="p-3 bg-slate-950/70 border border-slate-800/80 rounded-xl space-y-1">
            <span className="font-bold text-rose-500">❌ ENCERRAR APLICATIVO</span>
            <p className="text-slate-300">
              Diferente de pausar: cancela todas as rotinas, remove a bolha da tela e fecha o app completamente.
            </p>
          </div>
        </div>
      </div>

      {/* Bubble Colors & Status */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl space-y-3">
        <h3 className="font-bold text-sm text-white">Cores e Significados da Bolha Flutuante</h3>
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-2 text-xs">
          <div className="p-2.5 bg-slate-950/80 rounded-xl border border-slate-800 text-center">
            <div className="w-4 h-4 rounded-full bg-slate-400 mx-auto mb-1"></div>
            <span className="font-bold text-slate-300">Cinza</span>
            <p className="text-[10px] text-slate-500">Parado / Pronto</p>
          </div>
          <div className="p-2.5 bg-slate-950/80 rounded-xl border border-slate-800 text-center">
            <div className="w-4 h-4 rounded-full bg-emerald-500 mx-auto mb-1"></div>
            <span className="font-bold text-emerald-400">Verde</span>
            <p className="text-[10px] text-slate-500">Ativo / Preenchendo</p>
          </div>
          <div className="p-2.5 bg-slate-950/80 rounded-xl border border-slate-800 text-center">
            <div className="w-4 h-4 rounded-full bg-amber-500 mx-auto mb-1"></div>
            <span className="font-bold text-amber-400">Amarelo</span>
            <p className="text-[10px] text-slate-500">Pausado</p>
          </div>
          <div className="p-2.5 bg-slate-950/80 rounded-xl border border-slate-800 text-center">
            <div className="w-4 h-4 rounded-full bg-rose-500 mx-auto mb-1 animate-ping"></div>
            <span className="font-bold text-rose-400">Vermelho</span>
            <p className="text-[10px] text-slate-500">Requer Você</p>
          </div>
          <div className="p-2.5 bg-slate-950/80 rounded-xl border border-slate-800 text-center">
            <div className="w-4 h-4 rounded-full bg-cyan-500 mx-auto mb-1"></div>
            <span className="font-bold text-cyan-400">Azul/Ciano</span>
            <p className="text-[10px] text-slate-500">Escaneando / Rolando</p>
          </div>
          <div className="p-2.5 bg-slate-950/80 rounded-xl border border-slate-800 text-center">
            <div className="w-4 h-4 rounded-full bg-purple-500 mx-auto mb-1"></div>
            <span className="font-bold text-purple-400">Roxo</span>
            <p className="text-[10px] text-slate-500">Reproduzindo</p>
          </div>
        </div>
      </div>
    </div>
  );
};
