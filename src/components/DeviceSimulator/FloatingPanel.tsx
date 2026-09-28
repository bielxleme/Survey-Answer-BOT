import React, { useState } from 'react';
import { Play, Pause, Square, X, Eye, FileText, Check, Copy, Power, Brain, Wand2, Info } from 'lucide-react';
import { AgentState, AutomationMode, AgentMetrics } from '../../types/agent';

interface FloatingPanelProps {
  state: AgentState;
  mode: AutomationMode;
  metrics: AgentMetrics;
  onActivate: () => void;
  onPause: () => void;
  onStop: () => void;
  onClose: () => void;
  onModeChange: (mode: AutomationMode) => void;
  showAccessibilityOverlay: boolean;
  onToggleAccessibilityOverlay: () => void;
  currentSurveyIndex: number;
  totalSurveys: number;
  currentQuestionIndex: number;
  totalQuestions: number;
  recordDiagnosticLogs?: boolean;
  onToggleDiagnosticLogs?: () => void;
  onExportDiagnosticSummary?: () => void;
  guessMode?: boolean;
  onToggleGuessMode?: () => void;
  onShutdownApp?: () => void;
  onViewHistory?: () => void;
}

export const FloatingPanel: React.FC<FloatingPanelProps> = ({
  state,
  mode,
  metrics,
  onActivate,
  onPause,
  onStop,
  onClose,
  onModeChange,
  showAccessibilityOverlay,
  onToggleAccessibilityOverlay,
  currentSurveyIndex,
  totalSurveys,
  currentQuestionIndex,
  totalQuestions,
  recordDiagnosticLogs = true,
  onToggleDiagnosticLogs,
  onExportDiagnosticSummary,
  guessMode = false,
  onToggleGuessMode,
  onShutdownApp,
  onViewHistory,
}) => {
  const [copiedDiag, setCopiedDiag] = useState(false);
  const [teachingMode, setTeachingMode] = useState(false);
  const isRunning = state !== 'IDLE' && state !== 'USER_INTERVENTION_REQUIRED';

  const getStateDescription = () => {
    switch (state) {
      case 'IDLE':
        return { text: '● Pronto', color: 'text-emerald-400' };
      case 'SCANNING':
        return { text: '● Escaneando / Rolando Tela', color: 'text-cyan-400' };
      case 'RESEARCH_DETECTED':
        return { text: '● Pesquisa Detectada', color: 'text-cyan-400' };
      case 'READING_QUESTION':
        return { text: '● Lendo Pergunta', color: 'text-indigo-400' };
      case 'UNDERSTANDING_QUESTION':
        return { text: '● Compreendendo Significado', color: 'text-indigo-400' };
      case 'SEARCHING_PROFILE':
        return { text: '● Consultando Perfil', color: 'text-amber-400' };
      case 'GENERATING_RESPONSE':
        return { text: '● Gerando Resposta', color: 'text-amber-400' };
      case 'VALIDATING_RESPONSE':
        return { text: '● Validando Anti-Alucinação', color: 'text-emerald-400' };
      case 'FILLING_FIELD':
        return { text: '● Preenchendo Campo', color: 'text-emerald-400' };
      case 'VERIFYING_FIELD':
        return { text: '● Verificando Campo', color: 'text-emerald-400' };
      case 'NEXT_PAGE':
        return { text: '● Avançando Página', color: 'text-blue-400' };
      case 'WAITING':
        return { text: '● Aguardando Carregamento', color: 'text-slate-400' };
      case 'RESEARCH_COMPLETED':
        return { text: '● Pesquisa Concluída', color: 'text-purple-400' };
      case 'SEARCHING_NEXT_RESEARCH':
        return { text: '● Buscando Próxima Pesquisa', color: 'text-cyan-400' };
      case 'USER_INTERVENTION_REQUIRED':
        return { text: '● Ação Necessária do Usuário', color: 'text-rose-400' };
    }
  };

  const statusInfo = getStateDescription();

  const handleExportDiag = () => {
    if (onExportDiagnosticSummary) {
      onExportDiagnosticSummary();
      setCopiedDiag(true);
      setTimeout(() => setCopiedDiag(false), 2000);
    }
  };

  return (
    <div className="absolute top-16 right-3 z-40 w-72 bg-slate-900/95 backdrop-blur-md border border-slate-700/80 rounded-2xl shadow-2xl p-3.5 text-white font-sans transition-all max-h-[620px] overflow-y-auto">
      {/* Header */}
      <div className="flex items-center justify-between border-b border-slate-800 pb-2 mb-2.5">
        <div className="flex items-center space-x-2">
          <div className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-pulse"></div>
          <span className="font-bold text-xs tracking-wider text-slate-200">RESEARCH AGENT v2.3</span>
        </div>
        <button
          onClick={onClose}
          className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition-colors"
        >
          <X className="w-3.5 h-3.5" />
        </button>
      </div>

      {/* Mode Badge & State Line */}
      <div className="bg-slate-950/70 rounded-xl p-2.5 mb-2.5 border border-slate-800/80">
        <div className="flex items-center justify-between text-xs mb-1">
          <span className="font-semibold text-slate-400">Modo:</span>
          <span
            className={`px-2 py-0.5 rounded font-bold uppercase text-[10px] ${
              mode === 'AUTOMATICO'
                ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                : mode === 'ASSISTIDO'
                ? 'bg-blue-500/20 text-blue-400 border border-blue-500/30'
                : 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
            }`}
          >
            {mode}
          </span>
        </div>

        <div className="flex items-center space-x-1.5 mt-1.5">
          <span className={`text-xs font-semibold ${statusInfo.color}`}>{statusInfo.text}</span>
        </div>

        {isRunning && (
          <div className="mt-2 pt-2 border-t border-slate-800/80 text-[11px] text-slate-300 space-y-0.5">
            <div className="flex justify-between">
              <span className="text-slate-400">Pesquisa:</span>
              <span className="font-mono text-emerald-400 font-semibold">
                {currentSurveyIndex + 1}/{totalSurveys}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-400">Pergunta:</span>
              <span className="font-mono text-cyan-400 font-semibold">
                {currentQuestionIndex + 1}/{totalQuestions || 1}
              </span>
            </div>
          </div>
        )}
      </div>

      {/* Primary Action Buttons */}
      <div className="space-y-1.5 mb-2.5">
        {!isRunning ? (
          <button
            onClick={onActivate}
            className="w-full py-2.5 px-4 bg-gradient-to-r from-emerald-500 to-teal-500 hover:from-emerald-400 hover:to-teal-400 text-slate-950 font-bold text-xs rounded-xl shadow-lg shadow-emerald-500/20 flex items-center justify-center space-x-2 transition-transform active:scale-95"
          >
            <Play className="w-4 h-4 fill-slate-950" />
            <span>ATIVAR PESQUISA</span>
          </button>
        ) : (
          <div className="grid grid-cols-2 gap-2">
            <button
              onClick={onPause}
              className="py-2 px-3 bg-amber-500/20 hover:bg-amber-500/30 border border-amber-500/40 text-amber-300 font-bold text-xs rounded-xl flex items-center justify-center space-x-1.5 transition-colors"
            >
              <Pause className="w-3.5 h-3.5" />
              <span>PAUSAR</span>
            </button>
            <button
              onClick={onStop}
              className="py-2 px-3 bg-rose-500/20 hover:bg-rose-500/30 border border-rose-500/40 text-rose-300 font-bold text-xs rounded-xl flex items-center justify-center space-x-1.5 transition-colors"
            >
              <Square className="w-3.5 h-3.5" />
              <span>PARAR</span>
            </button>
          </div>
        )}
      </div>

      {/* Ver Histórico Button */}
      {onViewHistory && (
        <button
          onClick={onViewHistory}
          className="w-full mb-2.5 py-2 px-3 bg-emerald-950/70 hover:bg-emerald-900/80 border border-emerald-700/60 text-emerald-300 font-bold text-xs rounded-xl flex items-center justify-center space-x-2 transition-all shadow-sm active:scale-98"
          title="Ver histórico de respostas e registros no painel"
        >
          <FileText className="w-3.5 h-3.5 text-emerald-400" />
          <span>Ver Histórico ({metrics.questionsAnswered} · máx 1.000.000)</span>
        </button>
      )}

      {/* v2.2 & v2.3 Advanced Tools: Gravar Logs, Ensinar, Chutar */}
      <div className="bg-slate-950/60 rounded-xl p-2 border border-slate-800/80 space-y-2 mb-2.5 text-xs">
        {/* Gravar Logs de Diagnóstico (Requisito do usuário) */}
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-1.5 text-slate-300">
            <FileText className="w-3.5 h-3.5 text-cyan-400" />
            <span className="text-[11px] font-medium">Gravar Logs (Diagnóstico)</span>
          </div>
          <button
            onClick={onToggleDiagnosticLogs}
            className={`w-9 h-5 rounded-full transition-colors relative ${
              recordDiagnosticLogs ? 'bg-cyan-600' : 'bg-slate-700'
            }`}
          >
            <span
              className={`absolute top-0.5 w-4 h-4 bg-white rounded-full transition-transform ${
                recordDiagnosticLogs ? 'left-4.5' : 'left-0.5'
              }`}
            />
          </button>
        </div>

        {/* Exportar Diagnóstico para Análise Rápida (Consumo Mínimo de Tokens) */}
        {recordDiagnosticLogs && onExportDiagnosticSummary && (
          <button
            onClick={handleExportDiag}
            className="w-full py-1.5 px-2 bg-cyan-950 hover:bg-cyan-900 border border-cyan-800/60 text-cyan-300 text-[10px] font-semibold rounded-lg flex items-center justify-center space-x-1.5 transition-colors"
            title="Exportar logs compactos para o engenheiro/IA analisar erros com gasto mínimo de tokens"
          >
            {copiedDiag ? <Check className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
            <span>{copiedDiag ? 'Copiado para Clipboard!' : 'Exportar Logs (Tokens Mínimos)'}</span>
          </button>
        )}

        {/* Ensinar Pesquisa / Observar (Nota: Toques na bolha são ignorados) */}
        <div className="pt-1.5 border-t border-slate-800/80 flex items-center justify-between">
          <div className="flex flex-col">
            <span className="text-[11px] text-slate-300 font-medium">🧠 Ensinar (Observar)</span>
            <span className="text-[9px] text-slate-500">Toques na bolha são ignorados</span>
          </div>
          <button
            onClick={() => setTeachingMode(!teachingMode)}
            className={`px-2 py-0.5 text-[10px] font-bold rounded ${
              teachingMode ? 'bg-blue-600 text-white' : 'bg-slate-800 text-slate-400'
            }`}
          >
            {teachingMode ? 'Ativo' : 'Ligar'}
          </button>
        </div>

        {/* Chutar Respostas */}
        <div className="flex items-center justify-between">
          <span className="text-[11px] text-slate-300 font-medium">🎯 Chutar respostas</span>
          <button
            onClick={onToggleGuessMode}
            className={`px-2 py-0.5 text-[10px] font-bold rounded ${
              guessMode ? 'bg-purple-600 text-white' : 'bg-slate-800 text-slate-400'
            }`}
          >
            {guessMode ? 'Ativo' : 'Desligado'}
          </button>
        </div>
      </div>

      {/* Counters */}
      <div className="bg-slate-800/40 rounded-xl p-2 border border-slate-800 text-[11px] space-y-1 mb-2.5">
        <div className="flex justify-between items-center text-slate-300">
          <span>Pesquisas concluídas:</span>
          <span className="font-mono font-bold text-emerald-400 bg-emerald-500/10 px-1.5 py-0.5 rounded">
            {metrics.surveysCompleted}
          </span>
        </div>
        <div className="flex justify-between items-center text-slate-300">
          <span>Aguardando usuário:</span>
          <span
            className={`font-mono font-bold px-1.5 py-0.5 rounded ${
              metrics.interventionsRequired > 0
                ? 'text-rose-400 bg-rose-500/10 animate-pulse'
                : 'text-slate-400 bg-slate-800'
            }`}
          >
            {metrics.interventionsRequired}
          </span>
        </div>
      </div>

      {/* Bottom Controls: Accessibility Overlay & Mode Toggle & Shutdown */}
      <div className="flex items-center justify-between pt-2 border-t border-slate-800 text-[10px]">
        <button
          onClick={onToggleAccessibilityOverlay}
          className={`flex items-center space-x-1 px-1.5 py-1 rounded transition-colors ${
            showAccessibilityOverlay
              ? 'bg-cyan-500/20 text-cyan-300 font-semibold'
              : 'text-slate-400 hover:text-slate-200'
          }`}
          title="Ver hierarquia de AccessibilityNodeInfo"
        >
          <Eye className="w-3 h-3" />
          <span>A11y</span>
        </button>

        <div className="flex space-x-1">
          {(['MANUAL', 'ASSISTIDO', 'AUTOMATICO'] as AutomationMode[]).map((m) => (
            <button
              key={m}
              onClick={() => onModeChange(m)}
              className={`px-1.5 py-0.5 rounded uppercase font-semibold transition-colors ${
                mode === m ? 'bg-slate-700 text-white font-bold' : 'text-slate-500 hover:text-slate-300'
              }`}
            >
              {m.substring(0, 3)}
            </button>
          ))}
        </div>

        {onShutdownApp && (
          <button
            onClick={onShutdownApp}
            className="text-rose-400 hover:text-rose-300 p-1 rounded hover:bg-rose-500/10 transition-colors"
            title="Encerrar aplicativo (desativa bolha e automação)"
          >
            <Power className="w-3.5 h-3.5" />
          </button>
        )}
      </div>
    </div>
  );
};
