import React, { useState, useEffect, useRef } from 'react';
import { Header } from './components/Header';
import { AndroidDevice } from './components/DeviceSimulator/AndroidDevice';
import { InterventionDialog } from './components/DeviceSimulator/InterventionDialog';
import { ProfileManagerView } from './components/ProfileView/ProfileManagerView';
import { ProfileWizard } from './components/ProfileView/ProfileWizard';
import { JsonEditorModal } from './components/ProfileView/JsonEditorModal';
import { DashboardView } from './components/DashboardView/DashboardView';
import { PrivacyVaultView } from './components/PrivacyView/PrivacyVaultView';
import { AndroidCodeExplorer } from './components/CodeExplorerView/AndroidCodeExplorer';
import { OfflineIndicator } from './components/OfflineIndicator';

import { UserProfile } from './types/profile';
import { AgentState, AutomationMode, AgentMetrics, AuditLogEntry, InterventionRequest } from './types/agent';
import { SimulatedSurvey } from './types/survey';
import { SAMPLE_SURVEYS } from './data/sampleSurveys';
import { ProfileService } from './services/profileService';
import { AgentController } from './services/agentController';
import { audioService } from './services/audioService';

export default function App() {
  const [activeTab, setActiveTab] = useState<'simulator' | 'profile' | 'dashboard' | 'privacy' | 'code'>('simulator');
  const [profile, setProfile] = useState<UserProfile>(() => ProfileService.loadProfile());
  const [surveys, setSurveys] = useState<SimulatedSurvey[]>(SAMPLE_SURVEYS);
  const [currentSurveyIndex, setCurrentSurveyIndex] = useState(0);
  const [pageIndex, setPageIndex] = useState(0);
  const [answers, setAnswers] = useState<Record<string, any>>({});
  const [highlightedQuestionId, setHighlightedQuestionId] = useState<string | null>(null);

  const [agentState, setAgentState] = useState<AgentState>('IDLE');
  const [mode, setMode] = useState<AutomationMode>('AUTOMATICO');
  const [metrics, setMetrics] = useState<AgentMetrics>({
    surveysCompleted: 0,
    questionsAnswered: 0,
    automaticCount: 0,
    interventionsRequired: 0,
    pendingQuestions: 0,
    highConfidenceCount: 0,
    startTime: null,
    totalAutomatedTimeMs: 0,
  });

  const [logs, setLogs] = useState<AuditLogEntry[]>([]);
  const [currentIntervention, setCurrentIntervention] = useState<InterventionRequest | null>(null);
  const [showWizard, setShowWizard] = useState(false);
  const [showJsonModal, setShowJsonModal] = useState(false);
  const [isMuted, setIsMuted] = useState(false);

  const controllerRef = useRef<AgentController | null>(null);

  // Initialize Agent Controller
  useEffect(() => {
    const controller = new AgentController(profile, {
      onStateChange: (state) => setAgentState(state),
      onMetricsUpdate: (updated) => setMetrics(updated),
      onLogAdded: (entry) => setLogs((prev) => [entry, ...prev]),
      onInterventionRequired: (req) => setCurrentIntervention(req),
      onQuestionHighlight: (qid) => setHighlightedQuestionId(qid),
      onQuestionAnswered: (qid, val) => {
        setAnswers((prev) => ({ ...prev, [qid]: val }));
      },
      onPageAdvance: () => {
        setPageIndex((prev) => prev + 1);
      },
      onSurveyCompleted: (surveyId) => {
        setSurveys((prev) =>
          prev.map((s) => (s.id === surveyId ? { ...s, isCompleted: true } : s))
        );
      },
    });

    controller.setSurvey(surveys[currentSurveyIndex], pageIndex);
    controller.setMode(mode);
    controllerRef.current = controller;

    return () => {
      controller.stop();
    };
  }, []);

  // Update controller survey & page when changed
  useEffect(() => {
    if (controllerRef.current) {
      controllerRef.current.setSurvey(surveys[currentSurveyIndex], pageIndex);
    }
  }, [currentSurveyIndex, pageIndex, surveys]);

  // Update controller profile when changed
  useEffect(() => {
    if (controllerRef.current) {
      controllerRef.current.updateProfile(profile);
    }
  }, [profile]);

  // Update controller mode when changed
  useEffect(() => {
    if (controllerRef.current) {
      controllerRef.current.setMode(mode);
    }
  }, [mode]);

  const handleSelectSurvey = (idx: number) => {
    controllerRef.current?.stop();
    setCurrentSurveyIndex(idx);
    setPageIndex(0);
    setAnswers({});
    setHighlightedQuestionId(null);
  };

  const handleAnswerChange = (questionId: string, value: any) => {
    setAnswers((prev) => ({ ...prev, [questionId]: value }));
  };

  const handleNextPage = () => {
    const cur = surveys[currentSurveyIndex];
    if (pageIndex < cur.pages.length - 1) {
      setPageIndex((p) => p + 1);
    }
  };

  const handleResetSurvey = () => {
    controllerRef.current?.stop();
    setPageIndex(0);
    setAnswers({});
    setHighlightedQuestionId(null);
    if (controllerRef.current) {
      controllerRef.current.setSurvey(surveys[currentSurveyIndex], 0);
    }
  };

  const handleActivateAgent = () => {
    controllerRef.current?.start();
  };

  const handlePauseAgent = () => {
    controllerRef.current?.pause();
  };

  const handleStopAgent = () => {
    controllerRef.current?.stop();
  };

  const handleResolveIntervention = (userAnswer: any, shouldSaveToProfile: boolean) => {
    setCurrentIntervention(null);
    controllerRef.current?.resumeAfterIntervention(userAnswer, shouldSaveToProfile);

    // If saved to profile, reload state
    if (shouldSaveToProfile) {
      setProfile(ProfileService.loadProfile());
    }
  };

  const handleIgnoreIntervention = () => {
    setCurrentIntervention(null);
    controllerRef.current?.resumeAfterIntervention(undefined, false);
  };

  const handleToggleMute = () => {
    const next = !isMuted;
    setIsMuted(next);
    audioService.setMuted(next);
  };

  const handleClearLogs = () => {
    setLogs([]);
  };

  const handleResetProfile = () => {
    if (window.confirm('Tem certeza de que deseja restaurar o perfil padrão de testes?')) {
      const reset = ProfileService.resetProfile();
      setProfile(reset);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-emerald-500 selection:text-slate-950">
      {/* Top Application Header */}
      <Header
        activeTab={activeTab}
        onTabChange={setActiveTab}
        mode={mode}
        onModeChange={setMode}
        isMuted={isMuted}
        onToggleMute={handleToggleMute}
        onExportJson={() => setShowJsonModal(true)}
      />

      {/* Main Tab Views */}
      <main className="flex-1 overflow-x-hidden">
        {activeTab === 'simulator' && (
          <AndroidDevice
            surveys={surveys}
            currentSurveyIndex={currentSurveyIndex}
            onSelectSurvey={handleSelectSurvey}
            pageIndex={pageIndex}
            highlightedQuestionId={highlightedQuestionId}
            answers={answers}
            onAnswerChange={handleAnswerChange}
            onNextPage={handleNextPage}
            onResetSurvey={handleResetSurvey}
            agentState={agentState}
            automationMode={mode}
            metrics={metrics}
            onActivate={handleActivateAgent}
            onPause={handlePauseAgent}
            onStop={handleStopAgent}
            onModeChange={setMode}
          />
        )}

        {activeTab === 'profile' && (
          <ProfileManagerView
            profile={profile}
            onUpdateProfile={setProfile}
            onOpenWizard={() => setShowWizard(true)}
            onOpenJsonEditor={() => setShowJsonModal(true)}
            onResetProfile={handleResetProfile}
          />
        )}

        {activeTab === 'dashboard' && (
          <DashboardView metrics={metrics} logs={logs} onClearLogs={handleClearLogs} />
        )}

        {activeTab === 'privacy' && <PrivacyVaultView profile={profile} />}

        {activeTab === 'code' && <AndroidCodeExplorer />}
      </main>

      {/* Intervention Dialog (Section 6, 15, 16, 26) */}
      {currentIntervention && (
        <InterventionDialog
          request={currentIntervention}
          onResolve={handleResolveIntervention}
          onIgnore={handleIgnoreIntervention}
        />
      )}

      {/* Profile Wizard Modal (Section 37) */}
      {showWizard && (
        <ProfileWizard
          profile={profile}
          onSave={(updated) => {
            setProfile(updated);
            ProfileService.saveProfile(updated);
          }}
          onClose={() => setShowWizard(false)}
        />
      )}

      {/* JSON Import/Export Modal (Section 35 & 36) */}
      {showJsonModal && (
        <JsonEditorModal
          profile={profile}
          onSave={(updated) => {
            setProfile(updated);
            ProfileService.saveProfile(updated);
          }}
          onClose={() => setShowJsonModal(false)}
        />
      )}
      {/* Offline Connectivity Indicator */}
      <OfflineIndicator />
    </div>
  );
}
