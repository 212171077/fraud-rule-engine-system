export type UserRole = 'ADMIN' | 'FRAUD_ANALYST' | 'AUDITOR' | 'SERVICE';
export type UserStatus = 'PENDING' | 'ACTIVE' | 'DISABLED';
export type FraudDecision = 'CLEAR' | 'REVIEW' | 'HIGH_RISK' | 'UNDETERMINED';
export type ProcessingStatus = 'RECEIVED' | 'EVALUATING' | 'COMPLETED' | 'FAILED';
export type RuleOutcome = 'MATCH' | 'NO_MATCH' | 'NOT_APPLICABLE' | 'FAILED';

export interface UserProfile {
  id: string;
  name: string;
  surname: string;
  email: string;
  role: UserRole;
  status: UserStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CurrentUser {
  id: string;
  name: string;
  surname: string;
  email: string;
  role: UserRole;
  permissions: string[];
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: UserProfile;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface EvaluationListItem {
  evaluationId: string;
  eventId: string;
  evaluatedAt: string;
  occurredAt: string;
  category: string;
  decision: FraudDecision;
  status: ProcessingStatus;
  riskScore: number;
}

export interface RuleEvaluation {
  ruleCode: string;
  configurationVersion: number;
  outcome: RuleOutcome;
  reasonCode: string | null;
  evidence: Record<string, string>;
}

export interface Evaluation extends EvaluationListItem {
  amount: number;
  currency: string;
  ruleConfigurationVersion: number;
  referenceDataVersions: Record<string, number>;
  rules: RuleEvaluation[];
}

export interface EvaluationSummary {
  from: string;
  to: string;
  total: number;
  decisions: Partial<Record<string, number>>;
  decisionPercentages: Partial<Record<string, number>>;
}

export interface RuleConfiguration {
  ruleCode: string;
  version: number;
  enabled: boolean;
  effectiveFrom: string;
  effectiveUntil: string | null;
  scoreContribution: number;
  parameters: Record<string, string>;
}

export interface RuleAuditRecord {
  ruleCode: string;
  configurationVersion: number;
  action: 'CREATED' | 'UPDATED' | 'DISABLED' | 'DELETED';
  actorReference: string;
  reasonCode: string;
  occurredAt: string;
}

export interface RuleHistory {
  ruleCode: string;
  history: RuleAuditRecord[];
}

export interface CreateRuleRequest {
  ruleCode: string;
  enabled: boolean;
  scoreContribution: number;
  parameters: Record<string, string>;
  effectiveAt?: string | null;
  reasonCode: string;
}

export interface PatchRuleRequest {
  enabled?: boolean | null;
  scoreContribution?: number | null;
  parameters?: Record<string, string> | null;
  effectiveAt?: string | null;
  reasonCode: string;
}

export interface DeactivateRuleRequest {
  effectiveAt?: string | null;
  reasonCode: string;
}

export interface ProblemDetails {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  code: string;
  errors?: Record<string, string[]>;
}

export function isProblemDetails(value: unknown): value is ProblemDetails {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const problem = value as Record<string, unknown>;
  return (
    typeof problem['type'] === 'string' &&
    typeof problem['title'] === 'string' &&
    typeof problem['status'] === 'number' &&
    typeof problem['detail'] === 'string' &&
    typeof problem['code'] === 'string'
  );
}
