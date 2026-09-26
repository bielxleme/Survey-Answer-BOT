import React, { useState, useRef, useEffect } from 'react';
import { Check, CheckCircle2, ShieldCheck, ArrowRight, HelpCircle, Edit3, Sparkles, CheckCheck } from 'lucide-react';
import { SimulatedSurvey, SurveyQuestion } from '../../types/survey';
import { extractFullSentence } from '../../utils/smartTextExtractor';

interface SurveyScreenProps {
  survey: SimulatedSurvey;
  pageIndex: number;
  highlightedQuestionId: string | null;
  answers: Record<string, any>;
  onAnswerChange: (questionId: string, value: any) => void;
  onNextPage: () => void;
  showAccessibilityOverlay: boolean;
  onCorrectQuestion?: (questionId: string, correctedText: string) => void;
}

export const SurveyScreen: React.FC<SurveyScreenProps> = ({
  survey,
  pageIndex,
  highlightedQuestionId,
  answers,
  onAnswerChange,
  onNextPage,
  showAccessibilityOverlay,
  onCorrectQuestion,
}) => {
  const currentPage = survey.pages[pageIndex] || survey.pages[0];
  const containerRef = useRef<HTMLDivElement>(null);
  const questionRefs = useRef<Record<string, HTMLDivElement | null>>({});

  const [activeCorrectionQuestionId, setActiveCorrectionQuestionId] = useState<string | null>(null);
  const [extractedCandidate, setExtractedCandidate] = useState<string>('');
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  // Smooth auto-scroll when a question is highlighted by the agent or requires attention
  useEffect(() => {
    if (highlightedQuestionId && questionRefs.current[highlightedQuestionId]) {
      questionRefs.current[highlightedQuestionId]?.scrollIntoView({
        behavior: 'smooth',
        block: 'nearest',
      });
    }
  }, [highlightedQuestionId]);

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 3500);
  };

  const handleStartCorrection = (q: SurveyQuestion) => {
    setActiveCorrectionQuestionId(q.id);
    // Extração inicial inteligente usando a frase base
    const base = q.fullOriginalSentence || q.text;
    const extracted = extractFullSentence(base, '');
    setExtractedCandidate(extracted);
    showToast('Toque ou selecione qualquer palavra da pergunta: vou ler a frase completa.');
  };

  const handleTextSelection = (q: SurveyQuestion, e: React.MouseEvent<HTMLDivElement>) => {
    if (activeCorrectionQuestionId !== q.id) return;
    
    // Obter texto selecionado pelo cursor ou toque
    const selection = window.getSelection()?.toString() || '';
    const base = q.fullOriginalSentence || q.text;
    const smartSentence = extractFullSentence(base, selection);
    setExtractedCandidate(smartSentence);
    showToast(`Frase identificada: "${smartSentence.substring(0, 45)}..."`);
  };

  const handleConfirmCorrection = (questionId: string) => {
    if (extractedCandidate && onCorrectQuestion) {
      onCorrectQuestion(questionId, extractedCandidate);
      showToast('Pergunta corrigida e atualizada no agente com sucesso!');
    }
    setActiveCorrectionQuestionId(null);
    setExtractedCandidate('');
  };

  return (
    <div
      ref={containerRef}
      className="flex flex-col h-full bg-slate-50 text-slate-900 select-none overflow-y-auto relative scroll-smooth"
    >
      {/* Toast Alert Banner */}
      {toastMessage && (
        <div className="sticky top-12 z-30 mx-3 p-2 bg-emerald-900/95 text-emerald-200 border border-emerald-500/50 rounded-xl shadow-lg text-[11px] flex items-center space-x-1.5 animate-in fade-in slide-in-from-top-2">
          <Sparkles className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
          <span className="font-medium">{toastMessage}</span>
        </div>
      )}

      {/* Target App Header (Simulating In-App Browser or Survey App) */}
      <div className="bg-emerald-600 text-white px-4 py-3 shadow-md flex items-center justify-between sticky top-0 z-20">
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
            const isCorrecting = activeCorrectionQuestionId === q.id;
            const isMisidentified = q.needsCorrectionDemo || q.text.includes('"pergunta"');

            return (
              <div
                key={q.id}
                ref={(el) => {
                  questionRefs.current[q.id] = el;
                }}
                onMouseUp={(e) => handleTextSelection(q, e)}
                className={`p-3.5 rounded-xl border transition-all relative ${
                  isCorrecting
                    ? 'border-amber-500 bg-amber-50/80 shadow-md ring-2 ring-amber-400'
                    : isHighlighted
                    ? 'border-cyan-500 bg-cyan-50/70 shadow-md ring-2 ring-cyan-400/40 animate-pulse'
                    : isMisidentified
                    ? 'border-rose-400 bg-rose-50/40 shadow-sm'
                    : 'border-slate-200 bg-white shadow-sm'
                }`}
              >
                {/* Accessibility node label if overlay is active */}
                {showAccessibilityOverlay && (
                  <span className="absolute -top-2.5 left-2 bg-slate-800 text-cyan-300 text-[9px] font-mono px-1.5 py-0.5 rounded shadow">
                    Node: #{q.id} ({q.type})
                  </span>
                )}

                {/* Question Label Header */}
                <div className="flex items-start justify-between mb-2">
                  <div className="flex-1 pr-2">
                    <label className="text-xs font-semibold text-slate-800 leading-snug cursor-text">
                      <span className="text-emerald-600 font-bold mr-1">{idx + 1}.</span>
                      <span className={isMisidentified ? 'text-rose-600 font-bold' : ''}>
                        {q.text}
                      </span>
                      {q.required && <span className="text-rose-500 ml-1">*</span>}
                    </label>

                    {/* Exibe o texto completo subjacente para apoio na seleção inteligente */}
                    {q.fullOriginalSentence && q.fullOriginalSentence !== q.text && (
                      <p className="text-[10px] text-slate-400 italic mt-1 select-text">
                        Frase original: "{q.fullOriginalSentence}"
                      </p>
                    )}
                  </div>

                  <div className="flex flex-col items-end space-y-1 shrink-0">
                    {q.needsUserInput && (
                      <span className="text-[10px] bg-rose-100 text-rose-700 font-medium px-1.5 py-0.5 rounded flex items-center space-x-1">
                        <HelpCircle className="w-2.5 h-2.5" />
                        <span>Sem Perfil</span>
                      </span>
                    )}

                    {/* Botão "Pergunta incorreta" visível quando a pergunta está incorreta ou em destaque */}
                    {(isMisidentified || isHighlighted || isCorrecting) && (
                      <button
                        onClick={() => handleStartCorrection(q)}
                        className={`text-[10px] font-bold px-2 py-0.5 rounded flex items-center space-x-1 transition-colors ${
                          isCorrecting
                            ? 'bg-amber-600 text-white'
                            : 'bg-rose-100 hover:bg-rose-200 text-rose-700 border border-rose-300'
                        }`}
                        title="Corrigir pergunta selecionada incorretamente pelo app"
                      >
                        <Edit3 className="w-2.5 h-2.5" />
                        <span>Pergunta incorreta</span>
                      </button>
                    )}
                  </div>
                </div>

                {/* Smart Sentence Correction UI Box */}
                {isCorrecting && (
                  <div className="my-2 p-2.5 bg-amber-100/80 border border-amber-300 rounded-xl space-y-2 text-xs">
                    <div className="flex items-center space-x-1.5 text-amber-900 font-bold text-[11px]">
                      <Sparkles className="w-3.5 h-3.5 text-amber-600" />
                      <span>Leitor Inteligente de Frase:</span>
                    </div>
                    <p className="text-[11px] text-amber-800 leading-tight">
                      Selecione qualquer trecho, palavra ou letra da pergunta. O algoritmo expande para a frase inteira.
                    </p>
                    <div className="bg-white p-2 rounded-lg border border-amber-300 text-slate-900 text-xs font-medium">
                      "{extractedCandidate || q.fullOriginalSentence || q.text}"
                    </div>
                    <div className="flex items-center space-x-2 pt-1">
                      <button
                        onClick={() => handleConfirmCorrection(q.id)}
                        className="flex-1 py-1.5 px-3 bg-amber-600 hover:bg-amber-700 text-white font-bold text-xs rounded-lg flex items-center justify-center space-x-1 shadow-sm"
                      >
                        <CheckCheck className="w-3.5 h-3.5" />
                        <span>Confirmar Frase Correta</span>
                      </button>
                      <button
                        onClick={() => setActiveCorrectionQuestionId(null)}
                        className="py-1.5 px-2 bg-slate-200 hover:bg-slate-300 text-slate-700 font-semibold text-xs rounded-lg"
                      >
                        Cancelar
                      </button>
                    </div>
                  </div>
                )}

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
                            className={`w-4 h-4 rounded-full border flex items-center justify-center transition-colors shrink-0 ${
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
                            className={`w-4 h-4 rounded border flex items-center justify-center transition-colors shrink-0 ${
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
