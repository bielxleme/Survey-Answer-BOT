import React, { useState } from 'react';
import {
  BarChart3,
  CheckCircle2,
  AlertTriangle,
  FileText,
  Copy,
  Check,
  Download,
  Trash2,
  Search,
  Filter,
  ShieldCheck,
  BookmarkCheck,
  Sparkles,
} from 'lucide-react';
import { AgentMetrics, AuditLogEntry, DiagnosticLogEntry } from '../../types/agent';

interface DashboardViewProps {
  metrics: AgentMetrics;
  logs: AuditLogEntry[];
  diagnosticLogs?: DiagnosticLogEntry[];
  recordDiagnosticLogs?: boolean;
  onToggleDiagnosticLogs?: () => void;
  onClearLogs: () => void;
  onExportDiagnosticSummary?: () => string;
}

export const DashboardView: React.FC<DashboardViewProps> = ({
  metrics,
  logs,
  diagnosticLogs = [],
  recordDiagnosticLogs = true,
  onToggleDiagnosticLogs,
  onClearLogs,
  onExportDiagnosticSummary,
}) => {
  const [activeSubTab, setActiveSubTab] = useState<'audit' | 'diagnostic'>('diagnostic');
  const [filterType, setFilterType] = useState<string>('ALL');
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [copiedExport, setCopiedExport] = useState(false);
  const [showExportModal, setShowExportModal] = useState(false);
  const [exportedText, setExportedText] = useState('');

  const filteredAuditLogs = logs.filter((log) => {
    if (filterType !== 'ALL' && log.status !== filterType) return false;
    if (searchTerm) {
      const term = searchTerm.toLowerCase();
      return (
        log.questionText.toLowerCase().includes(term) ||
        (log.responseGiven && log.responseGiven.toLowerCase().includes(term)) ||
        log.surveyTitle.toLowerCase().includes(term)
      );
    }
    return true;
  });

  const filteredDiagnosticLogs = diagnosticLogs.filter((d) => {
    if (searchTerm) {
      const term = searchTerm.toLowerCase();
      return (
        d.action.toLowerCase().includes(term) ||
        d.app.toLowerCase().includes(term) ||
        d.expected.toLowerCase().includes(term) ||
        d.actual.toLowerCase().includes(term) ||
        (d.errorReason && d.errorReason.toLowerCase().includes(term))
      );
    }
    return true;
  });

  const highConfidencePct =
    metrics.questionsAnswered > 0
      ? Math.round((metrics.highConfidenceCount / metrics.questionsAnswered) * 100)
      : 100;

  const handleOpenExport = () => {
    const text = onExportDiagnosticSummary ? onExportDiagnosticSummary() : 'Sem registros';
    setExportedText(text);
    setShowExportModal(true);
  };

  const handleCopyExport = () => {
    navigator.clipboard.writeText(exportedText);
    setCopiedExport(true);
    setTimeout(() => setCopiedExport(false), 2000);
  };

  const handleDownloadExport = () => {
    const blob = new Blob([exportedText], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `research_agent_diagnostic_${Date.now()}.txt`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="max-w-6xl mx-auto py-6 px-4 space-y-6">
      {/* KPI Cards */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-xl">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Pesquisas</span>
          <div className="text-2xl font-black text-emerald-400 mt-1">{metrics.surveysCompleted}</div>
          <span className="text-[10px] text-slate-500">Concluídas</span>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-xl">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Perguntas</span>
          <div className="text-2xl font-black text-cyan-400 mt-1">{metrics.questionsAnswered}</div>
          <span className="text-[10px] text-slate-500">Respondidas</span>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-xl">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Automáticas</span>
          <div className="text-2xl font-black text-teal-400 mt-1">{metrics.automaticCount}</div>
          <span className="text-[10px] text-slate-500">Zero intervenção</span>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-xl">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Intervenções</span>
          <div className="text-2xl font-black text-amber-400 mt-1">{metrics.interventionsRequired}</div>
          <span className="text-[10px] text-slate-500">Ação humana</span>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-xl">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Pendentes</span>
          <div className="text-2xl font-black text-rose-400 mt-1">{metrics.pendingQuestions}</div>
          <span className="text-[10px] text-slate-500">Dados ausentes</span>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-xl">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Alta Confiança</span>
          <div className="text-2xl font-black text-emerald-400 mt-1">{highConfidencePct}%</div>
          <span className="text-[10px] text-slate-500">Fatos comprovados</span>
        </div>
      </div>

      {/* Feature Card: Gravação e Exportação de Logs para Diagnóstico IA */}
      <div className="bg-slate-900 border border-cyan-500/30 rounded-2xl p-5 shadow-xl flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center space-x-2">
            <FileText className="w-5 h-5 text-cyan-400" />
            <h3 className="font-bold text-sm text-white">
              Gravação de Logs para Diagnóstico & Aprendizado
            </h3>
            <span className="text-[10px] bg-cyan-500/20 text-cyan-300 font-mono px-2 py-0.5 rounded border border-cyan-500/30">
              Token-Efficient AI Export
            </span>
          </div>
          <p className="text-xs text-slate-400 max-w-2xl leading-relaxed">
            Armazena em um lugar único os cliques em botões, erros de reconhecimento, eventos de rolagem e falhas na automação.
            Exporte os logs compactados para que a IA ou o engenheiro compreendam o erro consumindo o mínimo de tokens.
          </p>
        </div>

        <div className="flex items-center space-x-3 shrink-0">
          {/* Toggle Gravação */}
          <div className="flex items-center space-x-2 bg-slate-950 p-2 rounded-xl border border-slate-800">
            <span className="text-xs text-slate-300 font-medium">Gravar Logs:</span>
            <button
              onClick={onToggleDiagnosticLogs}
              className={`w-10 h-5.5 rounded-full transition-colors relative ${
                recordDiagnosticLogs ? 'bg-cyan-600' : 'bg-slate-700'
              }`}
            >
              <span
                className={`absolute top-0.5 w-4.5 h-4.5 bg-white rounded-full transition-transform ${
                  recordDiagnosticLogs ? 'left-5' : 'left-0.5'
                }`}
              />
            </button>
          </div>

          {/* Exportar Logs Button */}
          <button
            onClick={handleOpenExport}
            className="flex items-center space-x-1.5 px-3 py-2 bg-gradient-to-r from-cyan-600 to-teal-600 hover:from-cyan-500 hover:to-teal-500 text-slate-950 font-bold text-xs rounded-xl shadow-lg shadow-cyan-600/20 transition-transform active:scale-95"
            title="Exportar logs compactados para análise e correção com gasto mínimo de tokens"
          >
            <Sparkles className="w-3.5 h-3.5 fill-slate-950" />
            <span>Exportar Diagnóstico (IA)</span>
          </button>
        </div>
      </div>

      {/* Main Logs Table Container with Subtabs */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl shadow-xl overflow-hidden">
        {/* Subtabs & Search Toolbar */}
        <div className="p-4 border-b border-slate-800 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
          <div className="flex items-center space-x-2">
            <button
              onClick={() => setActiveSubTab('diagnostic')}
              className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-colors ${
                activeSubTab === 'diagnostic'
                  ? 'bg-cyan-500 text-slate-950'
                  : 'bg-slate-800 text-slate-400 hover:text-white'
              }`}
            >
              Logs de Diagnóstico ({diagnosticLogs.length})
            </button>
            <button
              onClick={() => setActiveSubTab('audit')}
              className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-colors ${
                activeSubTab === 'audit'
                  ? 'bg-emerald-500 text-slate-950'
                  : 'bg-slate-800 text-slate-400 hover:text-white'
              }`}
            >
              Histórico de Perguntas ({logs.length})
            </button>
          </div>

          <div className="flex flex-wrap items-center gap-2 w-full sm:w-auto">
            {/* Search Input */}
            <div className="relative flex-1 sm:w-48">
              <Search className="w-3.5 h-3.5 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Buscar registros..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full text-xs pl-8 pr-3 py-1.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-1 focus:ring-emerald-500"
              />
            </div>

            {/* Clear Button */}
            <button
              onClick={onClearLogs}
              className="p-2 text-slate-400 hover:text-rose-400 hover:bg-slate-800 rounded-xl transition-colors"
              title="Limpar logs"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Tab 1: Diagnostic Logs Table */}
        {activeSubTab === 'diagnostic' && (
          <div className="overflow-x-auto">
            {filteredDiagnosticLogs.length === 0 ? (
              <div className="p-8 text-center text-slate-500 text-xs">
                Nenhum evento de diagnóstico registrado ainda. As ações dos botões, tentativas de rolagem e erros aparecerão aqui.
              </div>
            ) : (
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="border-b border-slate-800 text-slate-400 text-[11px] bg-slate-950/50">
                    <th className="py-2.5 px-4 font-semibold">Hora</th>
                    <th className="py-2.5 px-4 font-semibold">App / Tela</th>
                    <th className="py-2.5 px-4 font-semibold">Ação</th>
                    <th className="py-2.5 px-4 font-semibold">Esperado</th>
                    <th className="py-2.5 px-4 font-semibold">Resultado Real</th>
                    <th className="py-2.5 px-4 font-semibold">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-sans">
                  {filteredDiagnosticLogs.map((d) => (
                    <tr key={d.id} className="hover:bg-slate-800/30 transition-colors">
                      <td className="py-2.5 px-4 font-mono text-[11px] text-slate-400 whitespace-nowrap">
                        {d.timestamp}
                      </td>
                      <td className="py-2.5 px-4">
                        <div className="font-semibold text-white">{d.app}</div>
                        <div className="text-[11px] text-slate-400">{d.surveyScreen}</div>
                      </td>
                      <td className="py-2.5 px-4 font-mono text-cyan-300 font-medium">
                        {d.action}
                      </td>
                      <td className="py-2.5 px-4 text-slate-300 max-w-xs truncate">
                        {d.expected}
                      </td>
                      <td className="py-2.5 px-4 text-slate-200 max-w-sm">
                        <div>{d.actual}</div>
                        {d.remedy && (
                          <div className="text-[10px] text-emerald-400 mt-0.5">↳ {d.remedy}</div>
                        )}
                      </td>
                      <td className="py-2.5 px-4 whitespace-nowrap">
                        <span
                          className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                            d.status === 'SUCCESS'
                              ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                              : d.status === 'SCROLLED'
                              ? 'bg-cyan-500/20 text-cyan-400 border border-cyan-500/30'
                              : 'bg-rose-500/20 text-rose-400 border border-rose-500/30'
                          }`}
                        >
                          {d.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}

        {/* Tab 2: Audit Logs Table */}
        {activeSubTab === 'audit' && (
          <div className="overflow-x-auto">
            {filteredAuditLogs.length === 0 ? (
              <div className="p-8 text-center text-slate-500 text-xs">
                Nenhum log registrado. Execute uma pesquisa no Simulador para visualizar.
              </div>
            ) : (
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="border-b border-slate-800 text-slate-400 text-[11px] bg-slate-950/50">
                    <th className="py-2.5 px-4 font-semibold">Hora</th>
                    <th className="py-2.5 px-4 font-semibold">Pesquisa / Pergunta</th>
                    <th className="py-2.5 px-4 font-semibold">Resposta Fornecida</th>
                    <th className="py-2.5 px-4 font-semibold">Confiança</th>
                    <th className="py-2.5 px-4 font-semibold">Campo do Perfil</th>
                    <th className="py-2.5 px-4 font-semibold text-right">Resultado</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-sans">
                  {filteredAuditLogs.map((log) => (
                    <tr key={log.id} className="hover:bg-slate-800/30 transition-colors">
                      <td className="py-3 px-4 font-mono text-[11px] text-slate-400 whitespace-nowrap">
                        {log.timestamp}
                      </td>
                      <td className="py-3 px-4 max-w-xs">
                        <div className="font-semibold text-white truncate">{log.surveyTitle}</div>
                        <div className="text-[11px] text-slate-400 line-clamp-1">{log.questionText}</div>
                      </td>
                      <td className="py-3 px-4">
                        {log.responseGiven ? (
                          <span className="font-medium text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20 text-[11px]">
                            {log.responseGiven}
                          </span>
                        ) : (
                          <span className="text-slate-500 italic text-[11px]">Pendente de intervenção</span>
                        )}
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex items-center space-x-1.5">
                          <span
                            className={`w-2 h-2 rounded-full ${
                              log.confidenceLevel === 'CONFIDENCE_HIGH'
                                ? 'bg-emerald-400'
                                : log.confidenceLevel === 'CONFIDENCE_MEDIUM'
                                ? 'bg-amber-400'
                                : 'bg-rose-400'
                            }`}
                          ></span>
                          <span className="text-[11px] font-mono">{Math.round(log.confidenceScore * 100)}%</span>
                        </div>
                      </td>
                      <td className="py-3 px-4 font-mono text-[11px] text-cyan-400">
                        {log.sourceField || '—'}
                      </td>
                      <td className="py-3 px-4 text-right">
                        {log.status === 'SUCCESS' && (
                          <span className="inline-flex items-center space-x-1 text-[10px] font-bold text-emerald-400 bg-emerald-500/10 border border-emerald-500/30 px-2 py-0.5 rounded-full">
                            <CheckCircle2 className="w-2.5 h-2.5" />
                            <span>AUTOMÁTICO</span>
                          </span>
                        )}
                        {log.status === 'INTERVENTION' && (
                          <span className="inline-flex items-center space-x-1 text-[10px] font-bold text-amber-400 bg-amber-500/10 border border-amber-500/30 px-2 py-0.5 rounded-full">
                            <AlertTriangle className="w-2.5 h-2.5" />
                            <span>INTERVENÇÃO</span>
                          </span>
                        )}
                        {log.status === 'LEARNED' && (
                          <span className="inline-flex items-center space-x-1 text-[10px] font-bold text-purple-400 bg-purple-500/10 border border-purple-500/30 px-2 py-0.5 rounded-full">
                            <BookmarkCheck className="w-2.5 h-2.5" />
                            <span>APRENDIDO</span>
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </div>

      {/* Export Diagnostic Modal (Minimum Tokens) */}
      {showExportModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-in fade-in duration-200">
          <div className="bg-slate-900 border border-cyan-500/40 rounded-2xl max-w-2xl w-full p-5 shadow-2xl text-white space-y-4">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div className="flex items-center space-x-2">
                <Sparkles className="w-5 h-5 text-cyan-400" />
                <h3 className="font-bold text-sm uppercase tracking-wide">
                  Exportar Diagnóstico para IA (Gasto Mínimo de Tokens)
                </h3>
              </div>
              <button
                onClick={() => setShowExportModal(false)}
                className="text-slate-400 hover:text-white p-1 rounded-lg"
              >
                ✕
              </button>
            </div>

            <p className="text-xs text-slate-300 leading-relaxed">
              Este resumo estruturado em formato tabular ultra-compacto permite que o assistente compreenda exatamente o que deu errado, onde aconteceu e como corrigir sem gastar janela de contexto excessiva.
            </p>

            <div className="relative">
              <textarea
                readOnly
                value={exportedText}
                rows={10}
                className="w-full text-[11px] font-mono p-3 bg-slate-950 border border-slate-800 rounded-xl text-cyan-200 focus:outline-none"
              />
            </div>

            <div className="flex items-center justify-between pt-2">
              <button
                onClick={handleDownloadExport}
                className="flex items-center space-x-1.5 text-xs text-slate-300 hover:text-white px-3 py-2 bg-slate-800 hover:bg-slate-700 rounded-xl transition-colors"
              >
                <Download className="w-3.5 h-3.5" />
                <span>Baixar como .txt</span>
              </button>

              <div className="flex items-center space-x-2">
                <button
                  onClick={() => setShowExportModal(false)}
                  className="px-4 py-2 text-xs font-semibold text-slate-400 hover:text-white"
                >
                  Fechar
                </button>
                <button
                  onClick={handleCopyExport}
                  className="flex items-center space-x-1.5 px-4 py-2 bg-cyan-600 hover:bg-cyan-500 text-slate-950 font-bold text-xs rounded-xl shadow-md transition-all active:scale-95"
                >
                  {copiedExport ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                  <span>{copiedExport ? 'Copiado para Clipboard!' : 'Copiar Diagnóstico'}</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
