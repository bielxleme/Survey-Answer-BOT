import React, { useState } from 'react';
import { Wifi, BatteryMedium, Signal, RefreshCw, Layers, ShieldCheck, ChevronRight } from 'lucide-react';
import { SimulatedSurvey } from '../../types/survey';
import { AgentState, AutomationMode, AgentMetrics } from '../../types/agent';
import { SurveyScreen } from './SurveyScreen';
import { FloatingBubble } from './FloatingBubble';
import { FloatingPanel } from './FloatingPanel';

interface AndroidDeviceProps {
  surveys: SimulatedSurvey[];
  currentSurveyIndex: number;
  onSelectSurvey: (index: number) => void;
  pageIndex: number;
  highlightedQuestionId: string | null;
  answers: Record<string, any>;
  onAnswerChange: (questionId: string, value: any) => void;
  onNextPage: () => void;
  onResetSurvey: () => void;
  agentState: AgentState;
  automationMode: AutomationMode;
  metrics: AgentMetrics;
  onActivate: () => void;
  onPause: () => void;
  onStop: () => void;
  onModeChange: (mode: AutomationMode) => void;
}

export const AndroidDevice: React.FC<AndroidDeviceProps> = ({
  surveys,
  currentSurveyIndex,
  onSelectSurvey,
  pageIndex,
  highlightedQuestionId,
  answers,
  onAnswerChange,
  onNextPage,
  onResetSurvey,
  agentState,
  automationMode,
  metrics,
  onActivate,
  onPause,
  onStop,
  onModeChange,
}) => {
  const [isPanelOpen, setIsPanelOpen] = useState(true);
  const [showAccessibilityOverlay, setShowAccessibilityOverlay] = useState(false);

  const currentSurvey = surveys[currentSurveyIndex] || surveys[0];
  const currentPage = currentSurvey.pages[pageIndex] || currentSurvey.pages[0];

  return (
    <div className="flex flex-col lg:flex-row gap-6 items-start justify-center max-w-6xl mx-auto py-6 px-4">
      {/* Side Column: Survey Picker & Scenario Tester */}
      <div className="w-full lg:w-72 space-y-4 shrink-0">
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow-xl">
          <div className="flex items-center justify-between mb-3 border-b border-slate-800 pb-2">
            <h3 className="font-bold text-xs uppercase tracking-wider text-slate-300">Cenários de Teste</h3>
            <span className="text-[10px] bg-slate-800 text-emerald-400 px-2 py-0.5 rounded font-mono">
              {surveys.length} Formulários
            </span>
          </div>

          <div className="space-y-2">
            {surveys.map((s, idx) => {
              const isSelected = idx === currentSurveyIndex;
              return (
                <button
                  key={s.id}
                  onClick={() => onSelectSurvey(idx)}
                  className={`w-full text-left p-3 rounded-xl border transition-all text-xs ${
                    isSelected
                      ? 'bg-emerald-500/10 border-emerald-500/40 text-emerald-300 shadow-sm'
                      : 'bg-slate-950/60 border-slate-800/80 text-slate-400 hover:text-slate-200 hover:border-slate-700'
                  }`}
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="font-bold line-clamp-1">{s.title}</span>
                    <ChevronRight className={`w-3.5 h-3.5 ${isSelected ? 'text-emerald-400' : 'text-slate-600'}`} />
                  </div>
                  <p className="text-[11px] text-slate-500 line-clamp-2">{s.description}</p>
                  <div className="mt-2 flex items-center space-x-2 text-[10px]">
                    <span className="bg-slate-800 px-1.5 py-0.5 rounded text-slate-400">{s.category}</span>
                    <span className="text-slate-500">{s.pages.length} páginas</span>
                  </div>
                </button>
              );
            })}
          </div>

          <div className="mt-4 pt-3 border-t border-slate-800 flex items-center justify-between">
            <button
              onClick={onResetSurvey}
              className="flex items-center space-x-1.5 text-xs text-slate-400 hover:text-white transition-colors"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              <span>Reiniciar Formulário</span>
            </button>

            <button
              onClick={() => setShowAccessibilityOverlay(!showAccessibilityOverlay)}
              className={`flex items-center space-x-1 text-xs px-2 py-1 rounded transition-colors ${
                showAccessibilityOverlay
                  ? 'bg-cyan-500/20 text-cyan-300 font-semibold'
                  : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Layers className="w-3.5 h-3.5" />
              <span>{showAccessibilityOverlay ? 'A11y Ativo' : 'Ver A11y'}</span>
            </button>
          </div>
        </div>

        {/* Info Box: Real Accessibility Flow */}
        <div className="bg-slate-900/60 border border-slate-800/80 rounded-2xl p-4 text-xs text-slate-400 space-y-2">
          <div className="flex items-center space-x-1.5 text-slate-200 font-semibold">
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
            <span>Fluxo de Leitura Semântica</span>
          </div>
          <p className="text-[11px] leading-relaxed">
            O agente utiliza a hierarquia de <strong className="text-slate-200">AccessibilityNodeInfo</strong>, evitando
            coordenadas fixas. Cada pergunta é mapeada semanticamente aos dados reais do usuário.
          </p>
        </div>
      </div>

      {/* Main Mockup: Realistic Android Smartphone */}
      <div className="relative mx-auto w-full max-w-[380px] h-[740px] bg-slate-950 rounded-[48px] p-3.5 shadow-2xl ring-1 ring-slate-800 border-4 border-slate-800 flex flex-col shrink-0">
        {/* Device Outer Frame Details: Speaker & Camera */}
        <div className="absolute top-6 left-1/2 -translate-x-1/2 z-30 flex items-center space-x-2">
          <div className="w-12 h-1 bg-slate-800 rounded-full"></div>
          <div className="w-3 h-3 bg-slate-900 border border-slate-700 rounded-full"></div>
        </div>

        {/* Android Screen Bezel */}
        <div className="relative w-full h-full bg-slate-900 rounded-[38px] overflow-hidden flex flex-col border border-slate-800 shadow-inner">
          {/* Android Status Bar */}
          <div className="h-7 bg-slate-950 text-slate-400 text-[11px] font-mono px-6 flex items-center justify-between select-none z-20 shrink-0">
            <span>12:45</span>
            <div className="flex items-center space-x-2">
              <Signal className="w-3 h-3" />
              <Wifi className="w-3 h-3" />
              <div className="flex items-center space-x-0.5">
                <span>98%</span>
                <BatteryMedium className="w-3.5 h-3.5" />
              </div>
            </div>
          </div>

          {/* Screen Content Viewport */}
          <div className="relative flex-1 overflow-hidden">
            <SurveyScreen
              survey={currentSurvey}
              pageIndex={pageIndex}
              highlightedQuestionId={highlightedQuestionId}
              answers={answers}
              onAnswerChange={onAnswerChange}
              onNextPage={onNextPage}
              showAccessibilityOverlay={showAccessibilityOverlay}
            />

            {/* Floating Bubble Component (Section 1) */}
            <FloatingBubble
              state={agentState}
              onClick={() => setIsPanelOpen(!isPanelOpen)}
              isOpen={isPanelOpen}
            />

            {/* Floating Panel Popup (Section 1 & 19) */}
            {isPanelOpen && (
              <FloatingPanel
                state={agentState}
                mode={automationMode}
                metrics={metrics}
                onActivate={onActivate}
                onPause={onPause}
                onStop={onStop}
                onClose={() => setIsPanelOpen(false)}
                onModeChange={onModeChange}
                showAccessibilityOverlay={showAccessibilityOverlay}
                onToggleAccessibilityOverlay={() => setShowAccessibilityOverlay(!showAccessibilityOverlay)}
                currentSurveyIndex={currentSurveyIndex}
                totalSurveys={surveys.length}
                currentQuestionIndex={0}
                totalQuestions={currentPage.questions.length}
              />
            )}
          </div>

          {/* Android Navigation Pill / Home Gesture Bar */}
          <div className="h-5 bg-slate-950 flex items-center justify-center shrink-0 z-20">
            <div className="w-28 h-1 bg-slate-600 rounded-full"></div>
          </div>
        </div>
      </div>
    </div>
  );
};
