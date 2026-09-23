import { AgentState, AutomationMode, AuditLogEntry, AgentMetrics, InterpretationResult, InterventionRequest } from '../types/agent';
import { UserProfile } from '../types/profile';
import { SimulatedSurvey, SurveyQuestion } from '../types/survey';
import { SemanticEngine } from './semanticEngine';
import { audioService } from './audioService';
import { ProfileService } from './profileService';

export interface AgentControllerCallbacks {
  onStateChange: (state: AgentState) => void;
  onMetricsUpdate: (metrics: AgentMetrics) => void;
  onLogAdded: (entry: AuditLogEntry) => void;
  onInterventionRequired: (req: InterventionRequest) => void;
  onQuestionHighlight: (questionId: string | null) => void;
  onQuestionAnswered: (questionId: string, value: any) => void;
  onPageAdvance: () => void;
  onSurveyCompleted: (surveyId: string) => void;
}

export class AgentController {
  private state: AgentState = 'IDLE';
  private mode: AutomationMode = 'AUTOMATICO';
  private currentSurvey: SimulatedSurvey | null = null;
  private currentPageIndex: number = 0;
  private currentQuestionIndex: number = 0;
  private profile: UserProfile;
  private isRunning: boolean = false;
  private isPaused: boolean = false;
  private callbacks: AgentControllerCallbacks;

  private metrics: AgentMetrics = {
    surveysCompleted: 0,
    questionsAnswered: 0,
    automaticCount: 0,
    interventionsRequired: 0,
    pendingQuestions: 0,
    highConfidenceCount: 0,
    startTime: null,
    totalAutomatedTimeMs: 0,
  };

  private currentIntervention: InterventionRequest | null = null;
  private loopCounter: number = 0;
  private lastQuestionId: string | null = null;

  constructor(profile: UserProfile, callbacks: AgentControllerCallbacks) {
    this.profile = profile;
    this.callbacks = callbacks;
  }

  public updateProfile(newProfile: UserProfile) {
    this.profile = newProfile;
  }

  public setMode(mode: AutomationMode) {
    this.mode = mode;
  }

  public getMode(): AutomationMode {
    return this.mode;
  }

  public getState(): AgentState {
    return this.state;
  }

  public getMetrics(): AgentMetrics {
    return { ...this.metrics };
  }

  public setSurvey(survey: SimulatedSurvey, pageIndex: number = 0) {
    this.currentSurvey = survey;
    this.currentPageIndex = pageIndex;
    this.currentQuestionIndex = 0;
  }

  private transitionTo(newState: AgentState) {
    this.state = newState;
    this.callbacks.onStateChange(newState);
  }

  /**
   * User clicks [ ATIVAR PESQUISA ]
   */
  public async start() {
    if (this.isRunning && !this.isPaused) return;

    this.isRunning = true;
    this.isPaused = false;
    if (!this.metrics.startTime) {
      this.metrics.startTime = Date.now();
    }

    if (this.state === 'IDLE' || this.state === 'USER_INTERVENTION_REQUIRED') {
      this.transitionTo('SCANNING');
    }

    this.runAutomationLoop();
  }

  public pause() {
    this.isPaused = true;
    this.transitionTo('IDLE');
  }

  public stop() {
    this.isRunning = false;
    this.isPaused = false;
    this.currentQuestionIndex = 0;
    this.callbacks.onQuestionHighlight(null);
    this.transitionTo('IDLE');
  }

  /**
   * Main automation loop executing the Section 13 State Machine sequence
   */
  private async runAutomationLoop() {
    while (this.isRunning && !this.isPaused) {
      if (!this.currentSurvey) {
        this.transitionTo('SCANNING');
        await this.delay(600);
        continue;
      }

      const currentPage = this.currentSurvey.pages[this.currentPageIndex];
      if (!currentPage) {
        this.stop();
        break;
      }

      // Check if this is the final confirmation page
      if (currentPage.isFinalPage || currentPage.questions.length === 0) {
        this.transitionTo('RESEARCH_COMPLETED');
        audioService.playSuccessSound();
        this.metrics.surveysCompleted++;
        this.callbacks.onMetricsUpdate({ ...this.metrics });
        this.callbacks.onSurveyCompleted(this.currentSurvey.id);

        this.addLog({
          id: `log-${Date.now()}`,
          timestamp: new Date().toLocaleTimeString(),
          surveyId: this.currentSurvey.id,
          surveyTitle: this.currentSurvey.title,
          questionIndex: -1,
          questionText: 'Pesquisa concluída com sucesso',
          responseGiven: 'Finalizada',
          confidenceLevel: 'CONFIDENCE_HIGH',
          confidenceScore: 1.0,
          sourceField: null,
          status: 'SUCCESS',
          mode: this.mode,
          durationMs: 400,
          details: 'Pesquisa finalizada. Todas as páginas foram processadas.',
        });

        await this.delay(1200);

        if (this.mode === 'AUTOMATICO') {
          this.transitionTo('SEARCHING_NEXT_RESEARCH');
          await this.delay(1500);
          this.transitionTo('IDLE');
        } else {
          this.transitionTo('IDLE');
        }
        this.isRunning = false;
        break;
      }

      // Step: SCANNING
      this.transitionTo('SCANNING');
      await this.delay(400);

      // Step: RESEARCH_DETECTED
      this.transitionTo('RESEARCH_DETECTED');
      await this.delay(350);

      // Iterate through questions on current page
      let pageHasUnanswered = false;

      for (let i = this.currentQuestionIndex; i < currentPage.questions.length; i++) {
        if (!this.isRunning || this.isPaused) return;

        this.currentQuestionIndex = i;
        const question = currentPage.questions[i];
        this.callbacks.onQuestionHighlight(question.id);

        // Loop safety watchdog (Section 41)
        if (this.lastQuestionId === question.id) {
          this.loopCounter++;
          if (this.loopCounter > 4) {
            this.handleLoopDetected(question);
            return;
          }
        } else {
          this.lastQuestionId = question.id;
          this.loopCounter = 0;
        }

        // Step: READING_QUESTION
        this.transitionTo('READING_QUESTION');
        await this.delay(350);

        // Step: UNDERSTANDING_QUESTION
        this.transitionTo('UNDERSTANDING_QUESTION');
        await this.delay(400);

        // Step: SEARCHING_PROFILE
        this.transitionTo('SEARCHING_PROFILE');
        await this.delay(350);

        // Step: GENERATING_RESPONSE
        this.transitionTo('GENERATING_RESPONSE');
        const startTime = Date.now();
        const decision = await SemanticEngine.interpretQuestion(question, this.profile);

        // Step: VALIDATING_RESPONSE (Anti-Hallucination Guard)
        this.transitionTo('VALIDATING_RESPONSE');
        await this.delay(300);

        // If intervention is required (e.g. CAPTCHA, Missing info)
        if (decision.needs_user || decision.action === 'ASK_USER') {
          this.handleIntervention(question, decision);
          return; // Pause execution until user answers
        }

        // Mode Check:
        // In MANUAL mode, we show suggestion and wait for user
        if (this.mode === 'MANUAL') {
          this.handleManualConfirmation(question, decision);
          return;
        }

        // In ASSISTIDO mode, if confidence < 0.90, ask user confirmation
        if (this.mode === 'ASSISTIDO' && decision.confidence < 0.9) {
          this.handleAssistedConfirmation(question, decision);
          return;
        }

        // Step: FILLING_FIELD
        this.transitionTo('FILLING_FIELD');
        audioService.playStepSound();
        this.callbacks.onQuestionAnswered(question.id, decision.answer);
        await this.delay(400);

        // Step: VERIFYING_FIELD
        this.transitionTo('VERIFYING_FIELD');
        await this.delay(300);

        // Record metrics and logs
        this.metrics.questionsAnswered++;
        this.metrics.automaticCount++;
        if (decision.confidence >= 0.9) {
          this.metrics.highConfidenceCount++;
        }
        this.callbacks.onMetricsUpdate({ ...this.metrics });

        this.addLog({
          id: `log-${Date.now()}-${i}`,
          timestamp: new Date().toLocaleTimeString(),
          surveyId: this.currentSurvey.id,
          surveyTitle: this.currentSurvey.title,
          questionIndex: i + 1,
          questionText: question.text,
          responseGiven: Array.isArray(decision.answer) ? decision.answer.join(', ') : String(decision.answer),
          confidenceLevel: decision.confidenceLevel,
          confidenceScore: decision.confidence,
          sourceField: decision.source,
          status: 'SUCCESS',
          mode: this.mode,
          durationMs: Date.now() - startTime,
          details: decision.reason,
        });

        await this.delay(300);
      }

      this.callbacks.onQuestionHighlight(null);

      // Finished questions on this page -> NEXT_PAGE
      if (this.currentPageIndex < this.currentSurvey.pages.length - 1) {
        this.transitionTo('NEXT_PAGE');
        audioService.playStepSound();
        await this.delay(500);

        // Step: WAITING (Intelligent waiting for DOM transition, Section 14)
        this.transitionTo('WAITING');
        this.callbacks.onPageAdvance();
        this.currentPageIndex++;
        this.currentQuestionIndex = 0;
        await this.delay(700);
      } else {
        // Last page reached
        break;
      }
    }
  }

  private handleIntervention(question: SurveyQuestion, decision: InterpretationResult) {
    this.isPaused = true;
    this.transitionTo('USER_INTERVENTION_REQUIRED');
    audioService.playAlertSound(); // Ding-Dong Notification

    this.metrics.interventionsRequired++;
    this.metrics.pendingQuestions++;
    this.callbacks.onMetricsUpdate({ ...this.metrics });

    const isCaptcha = question.isCaptcha || question.text.toLowerCase().includes('captcha');
    const interventionReq: InterventionRequest = {
      id: `int-${Date.now()}`,
      type: isCaptcha ? 'CAPTCHA' : 'MISSING_DATA',
      title: isCaptcha ? 'Verificação de Segurança Detectada' : 'INFORMAÇÃO NECESSÁRIA',
      questionText: question.text,
      questionKey: question.profileMappingKey,
      reason: isCaptcha
        ? 'CAPTCHA detectado na tela. A automação foi pausada para que você possa resolver o desafio com segurança.'
        : `A pergunta "${question.text}" não possui dados correspondentes no seu perfil. Nunca inventamos informações pessoais.`,
      options: question.options,
      suggestedSaveField: question.profileMappingKey,
    };

    this.currentIntervention = interventionReq;
    this.callbacks.onInterventionRequired(interventionReq);

    this.addLog({
      id: `log-int-${Date.now()}`,
      timestamp: new Date().toLocaleTimeString(),
      surveyId: this.currentSurvey?.id || 'unknown',
      surveyTitle: this.currentSurvey?.title || 'Pesquisa',
      questionIndex: this.currentQuestionIndex + 1,
      questionText: question.text,
      responseGiven: null,
      confidenceLevel: 'UNKNOWN',
      confidenceScore: 0,
      sourceField: null,
      status: 'INTERVENTION',
      mode: this.mode,
      durationMs: 0,
      details: interventionReq.reason,
    });
  }

  private handleManualConfirmation(question: SurveyQuestion, decision: InterpretationResult) {
    this.isPaused = true;
    this.transitionTo('USER_INTERVENTION_REQUIRED');
    audioService.playAlertSound();

    const interventionReq: InterventionRequest = {
      id: `manual-${Date.now()}`,
      type: 'MISSING_DATA',
      title: 'MODO MANUAL: Confirmação',
      questionText: question.text,
      reason: `Sugestão baseada no perfil: "${decision.answer}". Confirme para preencher.`,
      options: question.options,
      currentValue: decision.answer,
    };
    this.callbacks.onInterventionRequired(interventionReq);
  }

  private handleAssistedConfirmation(question: SurveyQuestion, decision: InterpretationResult) {
    this.isPaused = true;
    this.transitionTo('USER_INTERVENTION_REQUIRED');
    audioService.playAlertSound();

    const interventionReq: InterventionRequest = {
      id: `assist-${Date.now()}`,
      type: 'MISSING_DATA',
      title: 'MODO ASSISTIDO: Confirmação de Resposta',
      questionText: question.text,
      reason: `Confiança média (${Math.round(decision.confidence * 100)}%). Confirme o valor "${decision.answer}".`,
      options: question.options,
      currentValue: decision.answer,
    };
    this.callbacks.onInterventionRequired(interventionReq);
  }

  private handleLoopDetected(question: SurveyQuestion) {
    this.isPaused = true;
    this.transitionTo('USER_INTERVENTION_REQUIRED');
    audioService.playAlertSound();

    const req: InterventionRequest = {
      id: `loop-${Date.now()}`,
      type: 'LOOP_DETECTED',
      title: 'Proteção Anti-Loop Ativada',
      questionText: question.text,
      reason: 'O agente detectou repetição excessiva no mesmo elemento da tela. Pausado para segurança.',
    };
    this.callbacks.onInterventionRequired(req);
  }

  /**
   * Called when user resolves the intervention dialog
   */
  public resumeAfterIntervention(userAnswer?: any, shouldSaveToProfile: boolean = false) {
    if (this.currentSurvey && userAnswer !== undefined) {
      const currentPage = this.currentSurvey.pages[this.currentPageIndex];
      if (currentPage && currentPage.questions[this.currentQuestionIndex]) {
        const question = currentPage.questions[this.currentQuestionIndex];

        // Fill user response
        this.callbacks.onQuestionAnswered(question.id, userAnswer);
        this.metrics.questionsAnswered++;
        if (this.metrics.pendingQuestions > 0) this.metrics.pendingQuestions--;
        this.callbacks.onMetricsUpdate({ ...this.metrics });

        // Save to profile if requested (Section 7: Aprendizado do Perfil)
        if (shouldSaveToProfile && question.profileMappingKey) {
          this.applyLearnedDataToProfile(question.profileMappingKey, userAnswer);
          ProfileService.saveLearnedMapping(question.text, question.profileMappingKey, userAnswer);

          this.addLog({
            id: `learn-${Date.now()}`,
            timestamp: new Date().toLocaleTimeString(),
            surveyId: this.currentSurvey.id,
            surveyTitle: this.currentSurvey.title,
            questionIndex: this.currentQuestionIndex + 1,
            questionText: question.text,
            responseGiven: String(userAnswer),
            confidenceLevel: 'CONFIDENCE_HIGH',
            confidenceScore: 1.0,
            sourceField: question.profileMappingKey,
            status: 'LEARNED',
            mode: this.mode,
            durationMs: 0,
            details: `Informação salva permanentemente no perfil (${question.profileMappingKey}). Reutilizável em pesquisas futuras.`,
          });
        }
      }
    }

    // Advance to next question
    this.currentQuestionIndex++;
    this.isPaused = false;
    this.currentIntervention = null;
    this.transitionTo('SCANNING');
    this.runAutomationLoop();
  }

  private applyLearnedDataToProfile(keyPath: string, value: any) {
    const parts = keyPath.split('.');
    if (parts.length === 2) {
      const [category, field] = parts;
      const cat = (this.profile as any)[category];
      if (cat) {
        cat[field] = value;
        ProfileService.saveProfile(this.profile);
      }
    }
  }

  private addLog(entry: AuditLogEntry) {
    this.callbacks.onLogAdded(entry);
  }

  private delay(ms: number) {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
}
