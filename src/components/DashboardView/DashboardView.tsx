import React, { useState } from 'react';
import {
  BarChart3,
  CheckCircle2,
  AlertTriangle,
  Clock,
  HelpCircle,
  TrendingUp,
  Trash2,
  Search,
  Filter,
  ShieldCheck,
  BookmarkCheck,
} from 'lucide-react';
import { AgentMetrics, AuditLogEntry } from '../../types/agent';

interface DashboardViewProps {
  metrics: AgentMetrics;
  logs: AuditLogEntry[];
  onClearLogs: () => void;
}

export const DashboardView: React.FC<DashboardViewProps> = ({ metrics, logs, onClearLogs }) => {
  const [filterType, setFilterType] = useState<string>('ALL');
  const [searchTerm, setSearchTerm] = useState<string>('');

  const filteredLogs = logs.filter((log) => {
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

  const highConfidencePct =
    metrics.questionsAnswered > 0
      ? Math.round((metrics.highConfidenceCount / metrics.questionsAnswered) * 100)
      : 100;

  return (
    <div className="max-w-6xl mx-auto py-6 px-4 space-y-6">
      {/* KPI Cards (Section 42 of prompt) */}
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

      {/* Execution Log Table (Section 20 of prompt) */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl shadow-xl overflow-hidden">
        {/* Table Toolbar */}
        <div className="p-4 border-b border-slate-800 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
          <div>
            <div className="flex items-center space-x-2">
              <h3 className="font-bold text-sm text-white">Log de Auditoria & Execução</h3>
              <span className="text-xs bg-slate-800 text-slate-400 px-2 py-0.5 rounded-full font-mono">
                {logs.length} Registros
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-0.5">
              Rastreabilidade completa de perguntas, fontes do perfil e decisões da IA.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2 w-full sm:w-auto">
            {/* Search Input */}
            <div className="relative flex-1 sm:w-48">
              <Search className="w-3.5 h-3.5 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="Buscar log..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full text-xs pl-8 pr-3 py-1.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-1 focus:ring-emerald-500"
              />
            </div>

            {/* Filter buttons */}
            <div className="flex space-x-1 bg-slate-950 p-1 rounded-xl border border-slate-800">
              {['ALL', 'SUCCESS', 'INTERVENTION', 'LEARNED'].map((type) => (
                <button
                  key={type}
                  onClick={() => setFilterType(type)}
                  className={`text-[10px] font-semibold uppercase px-2 py-1 rounded-lg transition-colors ${
                    filterType === type ? 'bg-slate-800 text-white' : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  {type === 'ALL'
                    ? 'Todos'
                    : type === 'SUCCESS'
                    ? 'Sucesso'
                    : type === 'INTERVENTION'
                    ? 'Intervenções'
                    : 'Aprendidos'}
                </button>
              ))}
            </div>

            {/* Clear logs button (Section 20: APAGAR HISTÓRICO) */}
            <button
              onClick={onClearLogs}
              className="flex items-center space-x-1 text-xs text-rose-400 hover:text-rose-300 bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/30 px-2.5 py-1.5 rounded-xl transition-colors font-semibold"
            >
              <Trash2 className="w-3.5 h-3.5" />
              <span>Apagar Histórico</span>
            </button>
          </div>
        </div>

        {/* Table Content */}
        <div className="overflow-x-auto">
          {filteredLogs.length === 0 ? (
            <div className="py-12 text-center text-xs text-slate-500">
              Nenhum registro de log encontrado para os critérios selecionados.
            </div>
          ) : (
            <table className="w-full text-left text-xs text-slate-300">
              <thead className="bg-slate-950/60 text-slate-400 text-[11px] uppercase tracking-wider font-semibold border-b border-slate-800">
                <tr>
                  <th className="py-3 px-4">Horário</th>
                  <th className="py-3 px-4">Pesquisa / Pergunta</th>
                  <th className="py-3 px-4">Resposta Fornecida</th>
                  <th className="py-3 px-4">Confiança</th>
                  <th className="py-3 px-4">Fonte Comprovada</th>
                  <th className="py-3 px-4 text-right">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 font-sans">
                {filteredLogs.map((log) => (
                  <tr key={log.id} className="hover:bg-slate-800/30 transition-colors">
                    <td className="py-3 px-4 text-[11px] font-mono text-slate-400 whitespace-nowrap">
                      {log.timestamp}
                    </td>
                    <td className="py-3 px-4 max-w-xs">
                      <div className="text-[10px] font-bold text-emerald-400 uppercase tracking-wide truncate">
                        {log.surveyTitle}
                      </div>
                      <div className="text-xs text-slate-200 line-clamp-1">{log.questionText}</div>
                    </td>
                    <td className="py-3 px-4 max-w-xs">
                      {log.responseGiven ? (
                        <span className="font-semibold text-white bg-slate-800 px-2 py-0.5 rounded text-[11px]">
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
      </div>
    </div>
  );
};
