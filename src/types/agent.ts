/**
 * Agent State Machine & Automation Types
 */

export type AgentState =
  | 'IDLE'
  | 'SCANNING'
  | 'RESEARCH_DETECTED'
  | 'READING_QUESTION'
  | 'UNDERSTANDING_QUESTION'
  | 'SEARCHING_PROFILE'
  | 'GENERATING_RESPONSE'
  | 'VALIDATING_RESPONSE'
  | 'FILLING_FIELD'
  | 'VERIFYING_FIELD'
  | 'NEXT_PAGE'
  | 'WAITING'
  | 'RESEARCH_COMPLETED'
  | 'SEARCHING_NEXT_RESEARCH'
  | 'USER_INTERVENTION_REQUIRED';

export type AutomationMode = 'MANUAL' | 'ASSISTIDO' | 'AUTOMATICO';

export type ConfidenceLevel = 'CONFIDENCE_HIGH' | 'CONFIDENCE_MEDIUM' | 'CONFIDENCE_LOW' | 'UNKNOWN';

export type DataOrigin = 'DADO_FORNECIDO' | 'DADO_DERIVADO' | 'DADO_NAO_DISPONIVEL';

export interface InterpretationResult {
  action: 'ANSWER' | 'ASK_USER';
  answer: string | number | boolean | string[] | null;
  confidence: number; // 0.0 to 1.0
  confidenceLevel: ConfidenceLevel;
  source: string | null;
  reason: string;
  dataOrigin: DataOrigin;
  needs_user: boolean;
}

export type InterventionType = 'CAPTCHA' | 'MISSING_DATA' | 'CONFLICT' | 'AUTH_REQUIRED' | 'LOOP_DETECTED';

export interface InterventionRequest {
  id: string;
  type: InterventionType;
  title: string;
  questionText?: string;
  questionKey?: string;
  reason: string;
  profileValue?: any;
  currentValue?: any;
  options?: string[];
  suggestedSaveField?: string;
}

export interface AuditLogEntry {
  id: string;
  timestamp: string;
  surveyId: string;
  surveyTitle: string;
  questionIndex: number;
  questionText: string;
  responseGiven: string | null;
  confidenceLevel: ConfidenceLevel;
  confidenceScore: number;
  sourceField: string | null;
  status: 'SUCCESS' | 'INTERVENTION' | 'ERROR' | 'SKIPPED' | 'LEARNED';
  mode: AutomationMode;
  durationMs: number;
  details?: string;
}

export interface AgentMetrics {
  surveysCompleted: number;
  questionsAnswered: number;
  automaticCount: number;
  interventionsRequired: number;
  pendingQuestions: number;
  highConfidenceCount: number;
  startTime: number | null;
  totalAutomatedTimeMs: number;
}
