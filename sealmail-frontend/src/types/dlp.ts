export type DlpAction = 'WARN' | 'MUST_ENCRYPT' | 'QUARANTINE' | 'BLOCK';
export type DlpScopeType = 'GLOBAL' | 'SENDER_DOMAIN' | 'RECIPIENT_DOMAIN';
export type DlpRuleType = 'PATTERN' | 'EDM' | 'FINGERPRINT' | 'REGEX' | 'KEYWORD' | 'BUILTIN' | 'DICTIONARY' | 'COMPOSITE';
export type DlpPolicyMode = 'MONITOR' | 'ENFORCE';
export type DlpContentKind =
  | 'SUBJECT'
  | 'HEADERS'
  | 'BODY_TEXT'
  | 'BODY_HTML'
  | 'ATTACHMENT_TEXT'
  | 'ATTACHMENT_PDF'
  | 'ATTACHMENT_ZIP_ENTRY'
  | 'ATTACHMENT_METADATA';
export type DlpMaskingStrategy = 'DEFAULT' | 'PARTIAL' | 'FULL' | 'HASH_ONLY' | 'EMAIL' | 'SECRET';

export interface DlpPattern {
  id: string;
  name: string;
  description?: string;
  regex: string;
  type?: DlpRuleType;
  builtinCode?: string;
  contentKinds?: DlpContentKind[];
  minMatchCount?: number;
  maxEvidenceCount?: number;
  maskingStrategy?: DlpMaskingStrategy;
  action: DlpAction;
  severity: number;
  priority: number;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface DlpRule {
  id: string;
  name: string;
  description?: string;
  type: DlpRuleType;
  pattern?: string;
  builtinCode?: string;
  contentKinds: DlpContentKind[];
  minMatchCount: number;
  maxEvidenceCount: number;
  maskingStrategy: DlpMaskingStrategy;
  action: DlpAction;
  severity: number;
  priority: number;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface DlpRuleGroup {
  id: string;
  name: string;
  description?: string;
  enabled: boolean;
  priority: number;
  ruleIds: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface DlpPolicy {
  id: string;
  name: string;
  description?: string;
  mode: DlpPolicyMode;
  direction?: 'INBOUND' | 'OUTBOUND';
  senderDomains: string[];
  recipientDomains: string[];
  senderAddressPatterns: string[];
  recipientAddressPatterns: string[];
  attachmentRequired: boolean;
  enabled: boolean;
  priority: number;
  ruleGroupIds: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface DlpEvidence {
  id: string;
  eventId: string;
  ruleId?: string;
  ruleName: string;
  ruleType: DlpRuleType;
  partId: string;
  partKind: DlpContentKind;
  fileName?: string;
  contentType?: string;
  maskedSnippet?: string;
  matchHash: string;
  startOffset: number;
  endOffset: number;
  severity: number;
  action: DlpAction;
  createdAt: string;
}

export interface DlpEvent {
  id: string;
  messageId?: string;
  processingId?: string;
  direction?: 'INBOUND' | 'OUTBOUND';
  senderEmail?: string;
  recipients: string[];
  subject?: string;
  remoteAddress?: string;
  policyIds: string[];
  ruleGroupIds: string[];
  action: DlpAction;
  maxSeverity: number;
  matchCount: number;
  extractionWarnings: string[];
  monitorMode: boolean;
  scanDurationMs: number;
  ubaRiskLevel?: 'LOW' | 'MEDIUM' | 'HIGH';
  ubaRiskReasons: string[];
  ubaActionUpgraded: boolean;
  quarantineId?: string;
  falsePositive: boolean;
  falsePositiveAt?: string;
  falsePositiveBy?: string;
  falsePositiveComment?: string;
  createdAt: string;
}

export interface DlpEvaluation {
  eventId?: string;
  action: DlpAction;
  recommendedAction: DlpAction;
  maxSeverity: number;
  matchCount: number;
  policyIds: string[];
  ruleGroupIds: string[];
  monitorMode: boolean;
  scanDurationMs: number;
  warnings: string[];
  evidence: DlpEvidence[];
  ubaRiskLevel?: 'LOW' | 'MEDIUM' | 'HIGH';
  ubaRiskReasons: string[];
  ubaActionUpgraded: boolean;
}

export interface DlpSelection {
  id: string;
  scopeType: DlpScopeType;
  scopeValue?: string;
  patternIds?: string[] | null;
  patternMode?: 'ALL' | 'SELECTED';
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface DlpEdmDataset {
  id: string;
  name: string;
  description?: string;
  enabled: boolean;
  valueCount: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface DlpFingerprintLibrary {
  id: string;
  name: string;
  description?: string;
  enabled: boolean;
  documentCount: number;
  chunkCount: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface DlpImportResult {
  importedCount: number;
  duplicateCount: number;
  ignoredCount: number;
  totalCount: number;
}

export interface DlpUbaSenderRisk {
  senderEmail: string;
  totalMessages: number;
  outboundMessages: number;
  externalDomainCount: number;
  dlpHitCount: number;
  highRiskCount: number;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  lastReasons: string[];
  firstSeenAt?: string;
  lastSeenAt?: string;
  updatedAt?: string;
}

export interface BatchOperationItem {
  id: string;
  success: boolean;
  errorCode?: string;
  message?: string;
}

export interface BatchOperationResult {
  requestedCount: number;
  successCount: number;
  failureCount: number;
  items: BatchOperationItem[];
}

export type CreateDlpPatternRequest = Omit<DlpPattern, 'id' | 'createdAt' | 'updatedAt'>;
export type UpdateDlpPatternRequest = Partial<CreateDlpPatternRequest>;

export type CreateDlpSelectionRequest = Omit<DlpSelection, 'id' | 'createdAt' | 'updatedAt'>;
export type UpdateDlpSelectionRequest = Partial<CreateDlpSelectionRequest>;
export type DlpRuleRequest = Omit<DlpRule, 'id' | 'createdAt' | 'updatedAt'>;
export type DlpRuleGroupRequest = Omit<DlpRuleGroup, 'id' | 'createdAt' | 'updatedAt'>;
export type DlpPolicyRequest = Omit<DlpPolicy, 'id' | 'createdAt' | 'updatedAt'>;
export type DlpDatasetRequest = {
  name?: string;
  description?: string;
  enabled?: boolean;
};
export type DlpImportValuesRequest = {
  values?: string[];
  text?: string;
};
export type DlpFingerprintImportRequest = {
  documentName?: string;
  text?: string;
};
export interface DlpTestRequest {
  subject?: string;
  body?: string;
  sender?: string;
  recipients?: string[];
  direction?: 'INBOUND' | 'OUTBOUND';
}
