import React from 'react';
import { Check, CheckCircle2, ShieldCheck, ArrowRight, HelpCircle } from 'lucide-react';
import { SimulatedSurvey, SurveyQuestion } from '../../types/survey';

interface SurveyScreenProps {
  survey: SimulatedSurvey;
  pageIndex: number;
  highlightedQuestionId: string | null;
  answers: Record<string, any>;
  onAnswerChange: (questionId: string, value: any) => void;
  onNextPage: () => void;
  showAccessibilityOverlay: boolean;
}

export const SurveyScreen: React.FC<SurveyScreenProps> = ({
  survey,
  pageIndex,
  highlightedQuestionId,
  answers,
  onAnswerChange,
  onNextPage,
  showAccessibilityOverlay,
}) => {
  const currentPage = survey.pages[pageIndex] || survey.pages[0];

  return (
    <div className="flex flex-col h-full bg-slate-50 text-slate-900 select-none overflow-y-auto">
      {/* Target App Header (Simulating In-App Browser or Survey App) */}
      <div className="bg-emerald-600 text-white px-4 py-3 shadow-md flex items-center justify-between sticky top-0 z-10">
        <div>
          <div className="flex items-center space-x-1.5">
            <span className="text-xs uppercase tracking-wider font-bold bg-emerald-700/60 px-1.5 py-0.5 rounded">
              {survey.appName}
            </span>
          </div>
          <h2 className="font-semibold text-sm line-clamp-1">{survey.title}</h2>
        </div>
        <div className="text-right">
          <span className="text-xs bg-emerald-700 px-2 py-0.5 rounded-full font-mono font-medium">
            {currentPage.pageNumber}/{currentPage.totalPages}
          </span>
        </div>
      </div>

      {/* Survey Body */}
      <div className="p-4 space-y-5 flex-1 pb-16">
        {/* Page Title & Instructions */}
        <div className="border-b border-slate-200 pb-2">
          <h3 className="font-bold text-base text-slate-800">{currentPage.title}</h3>
          {currentPage.description && (
            <p className="text-xs text-slate-500 mt-0.5">{currentPage.description}</p>
          )}
        </div>

        {/* If Final Completion Page */}
        {currentPage.isFinalPage && (
          <div className="py-8 flex flex-col items-center justify-center text-center space-y-3">
            <div className="w-16 h-16 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center shadow-inner">
              <CheckCircle2 className="w-10 h-10" />
            </div>
            <h4 className="font-bold text-lg text-slate-800">Pesquisa Concluída!</h4>
            <p className="text-xs text-slate-600 max-w-xs">
              Todas as respostas foram validadas com base no perfil oficial do usuário e enviadas com sucesso.
            </p>
          </div>
        )}

        {/* Question List */}
        <div className="space-y-4">
          {currentPage.questions.map((q, idx) => {
            const isHighlighted = highlightedQuestionId === q.id;
            const currentVal = answers[q.id];

            return (
              <div
                key={q.id}
                className={`p-3.5 rounded-xl border transition-all relative ${
                  isHighlighted
                    ? 'border-cyan-500 bg-cyan-50/70 shadow-md ring-2 ring-cyan-400/40 animate-pulse'
                    : 'border-slate-200 bg-white shadow-sm'
                }`}
              >
                {/* Accessibility node label if overlay is active */}
                {showAccessibilityOverlay && (
                  <span className="absolute -top-2.5 left-2 bg-slate-800 text-cyan-300 text-[9px] font-mono px-1.5 py-0.5 rounded shadow">
                    Node: #{q.id} ({q.type})
                  </span>
                )}

                <div className="flex items-start justify-between mb-2">
                  <label className="text-xs font-semibold text-slate-800 leading-snug">
                    <span className="text-emerald-600 font-bold mr-1">{idx + 1}.</span>
                    {q.text}
                    {q.required && <span className="text-rose-500 ml-1">*</span>}
                  </label>
                  {q.needsUserInput && (
                    <span className="text-[10px] bg-rose-100 text-rose-700 font-medium px-1.5 py-0.5 rounded flex items-center space-x-1 shrink-0">
                      <HelpCircle className="w-2.5 h-2.5" />
                      <span>Sem Perfil</span>
                    </span>
                  )}
                </div>

                {/* Render Question Inputs based on Type */}
                {q.type === 'RADIO' && q.options && (
                  <div className="space-y-1.5 mt-2">
                    {q.options.map((opt) => {
                      const isSelected = currentVal === opt;
                      return (
                        <label
                          key={opt}
                          onClick={() => onAnswerChange(q.id, opt)}
                          className={`flex items-center space-x-2.5 p-2 rounded-lg cursor-pointer text-xs border transition-colors ${
                            isSelected
                              ? 'border-emerald-500 bg-emerald-50/60 font-semibold text-emerald-900'
                              : 'border-slate-100 hover:bg-slate-50 text-slate-700'
                          }`}
                        >
                          <div
                            className={`w-4 h-4 rounded-full border flex items-center justify-center transition-colors ${
                              isSelected ? 'border-emerald-600 bg-emerald-600' : 'border-slate-300'
                            }`}
                          >
                            {isSelected && <div className="w-1.5 h-1.5 rounded-full bg-white"></div>}
                          </div>
                          <span>{opt}</span>
                        </label>
                      );
                    })}
                  </div>
                )}

                {q.type === 'CHECKBOX' && q.options && (
                  <div className="space-y-1.5 mt-2">
                    {q.options.map((opt) => {
                      const selectedList = Array.isArray(currentVal) ? currentVal : [];
                      const isChecked = selectedList.includes(opt);

                      return (
                        <label
                          key={opt}
                          onClick={() => {
                            const updated = isChecked
                              ? selectedList.filter((item: string) => item !== opt)
                              : [...selectedList, opt];
                            onAnswerChange(q.id, updated);
                          }}
                          className={`flex items-center space-x-2.5 p-2 rounded-lg cursor-pointer text-xs border transition-colors ${
                            isChecked
                              ? 'border-emerald-500 bg-emerald-50/60 font-semibold text-emerald-900'
                              : 'border-slate-100 hover:bg-slate-50 text-slate-700'
                          }`}
                        >
                          <div
                            className={`w-4 h-4 rounded border flex items-center justify-center transition-colors ${
                              isChecked ? 'border-emerald-600 bg-emerald-600 text-white' : 'border-slate-300'
                            }`}
                          >
                            {isChecked && <Check className="w-3 h-3 stroke-[3]" />}
                          </div>
                          <span>{opt}</span>
                        </label>
                      );
                    })}
                  </div>
                )}

                {q.type === 'SELECT' && q.options && (
                  <select
                    value={currentVal || ''}
                    onChange={(e) => onAnswerChange(q.id, e.target.value)}
                    className="w-full text-xs p-2 rounded-lg border border-slate-300 bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  >
                    <option value="" disabled>
                      Selecione uma opção...
                    </option>
                    {q.options.map((opt) => (
                      <option key={opt} value={opt}>
                        {opt}
                      </option>
                    ))}
                  </select>
                )}

                {q.type === 'TEXT' && (
                  <input
                    type="text"
                    placeholder={q.placeholder || 'Digite sua resposta...'}
                    value={currentVal || ''}
                    onChange={(e) => onAnswerChange(q.id, e.target.value)}
                    className="w-full text-xs p-2 rounded-lg border border-slate-300 bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  />
                )}

                {q.isCaptcha && (
                  <div className="p-3 bg-amber-50 border border-amber-200 rounded-xl space-y-2 mt-2">
                    <div className="flex items-center space-x-2 text-amber-800 text-xs font-semibold">
                      <ShieldCheck className="w-4 h-4 text-amber-600" />
                      <span>Desafio Anti-Robô (CAPTCHA)</span>
                    </div>
                    <p className="text-[11px] text-amber-700">
                      O robô autônomo está proibido de resolver este desafio sozinho. Requer intervenção humana.
                    </p>
                    <button
                      onClick={() => onAnswerChange(q.id, 'CAPTCHA_RESOLVIDO')}
                      className={`w-full py-2 px-3 text-xs font-bold rounded-lg border flex items-center justify-center space-x-1.5 transition-colors ${
                        currentVal === 'CAPTCHA_RESOLVIDO'
                          ? 'bg-emerald-600 text-white border-emerald-600'
                          : 'bg-white text-slate-800 border-slate-300 hover:bg-slate-50'
                      }`}
                    >
                      {currentVal === 'CAPTCHA_RESOLVIDO' ? (
                        <>
                          <Check className="w-3.5 h-3.5" />
                          <span>Verificado pelo Usuário</span>
                        </>
                      ) : (
                        <span>[ Resolver CAPTCHA Manualmente ]</span>
                      )}
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Action Button: Next / Submit */}
        <div className="pt-2">
          <button
            onClick={onNextPage}
            className="w-full py-3 px-4 bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-xs rounded-xl shadow-md flex items-center justify-center space-x-2 transition-colors active:scale-95"
          >
            <span>{currentPage.nextButtonLabel}</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>
    </div>
  );
};
