/**
 * Survey and Screen Analysis Types
 * Models AccessibilityNodeInfo and survey form questions
 */

export type ElementType = 'RADIO' | 'CHECKBOX' | 'TEXT' | 'SELECT' | 'BUTTON' | 'TITLE' | 'INSTRUCTION';

export interface AccessibilityNode {
  id: string;
  className: string;
  text: string;
  contentDescription?: string;
  resourceId?: string;
  isClickable: boolean;
  isCheckable: boolean;
  isChecked?: boolean;
  isEditable?: boolean;
  isEnabled: boolean;
  bounds: {
    left: number;
    top: number;
    right: number;
    bottom: number;
  };
  children?: AccessibilityNode[];
}

export interface SurveyQuestion {
  id: string;
  text: string;
  type: ElementType;
  options?: string[];
  placeholder?: string;
  required: boolean;
  currentValue?: any;
  profileMappingKey?: string; // e.g. "identidade.genero"
  isCaptcha?: boolean;
  needsUserInput?: boolean; // if deliberately tests missing info
}

export interface SurveyPage {
  pageNumber: number;
  totalPages: number;
  title: string;
  description?: string;
  questions: SurveyQuestion[];
  isFinalPage?: boolean;
  nextButtonLabel: string;
}

export interface SimulatedSurvey {
  id: string;
  appPackage: string;
  appName: string;
  title: string;
  description: string;
  category: string;
  pages: SurveyPage[];
  isCompleted?: boolean;
}
