export interface MailProcessingStep {
  id: string;
  stepName: string;
  completed: boolean;
  success: boolean;
  errorMessage?: string;
  startedAt: string;
  completedAt?: string;
}

export type MailProcessingSnapshotStatus =
  | 'PENDING'
  | 'PASS'
  | 'FAIL'
  | 'SKIPPED'
  | 'DELIVERED'
  | 'QUARANTINED'
  | 'EXCEPTION'
  | string;

export interface MailAuthMechanismStatus {
  result?: string;
  domain?: string;
  identity?: string;
  detail?: string;
}

export interface MailAuthSnapshot {
  status: MailProcessingSnapshotStatus;
  spf?: MailAuthMechanismStatus;
  dkim?: MailAuthMechanismStatus;
  dmarc?: MailAuthMechanismStatus;
  action?: string;
  reason?: string;
  detail?: string;
}

export interface RecipientCertificateSnapshot {
  email: string;
  thumbprint?: string;
  selected: boolean;
}

export interface CertificateSnapshot {
  status: MailProcessingSnapshotStatus;
  cryptoProfile?: string;
  senderThumbprint?: string;
  recipients: RecipientCertificateSnapshot[];
  missingRecipients: string[];
  senderMissing: boolean;
  recipientMissing: boolean;
  failureReason?: string;
}

export interface SmimeOperationSnapshot {
  status: MailProcessingSnapshotStatus;
  algorithmSuite?: string;
  certificateThumbprint?: string;
  recipientThumbprints: string[];
  recipients: string[];
  failureReason?: string;
}

export interface SmimeSnapshot {
  sign: SmimeOperationSnapshot;
  encrypt: SmimeOperationSnapshot;
  verify: SmimeOperationSnapshot;
  decrypt: SmimeOperationSnapshot;
}

export interface DlpSnapshot {
  status: MailProcessingSnapshotStatus;
  action?: string;
  recommendedAction?: string;
  maxSeverity?: number;
  matchCount?: number;
  ruleNames: string[];
  eventId?: string;
  monitorMode?: boolean;
  ubaRiskLevel?: string;
  ubaActionUpgraded?: boolean;
  summary?: string;
  failureReason?: string;
}

export type AttachmentSecurityAction = 'ALLOW' | 'WARN' | 'QUARANTINE' | 'BLOCK' | string;

export interface AttachmentSecurityFinding {
  code?: string;
  severity?: number;
  action?: AttachmentSecurityAction;
  message?: string;
  fileName?: string;
  extension?: string;
  declaredMimeType?: string;
  detectedMimeType?: string;
  archive?: boolean;
  encrypted?: boolean;
  nestedPath: string[];
}

export interface AttachmentSecuritySnapshot {
  status: MailProcessingSnapshotStatus;
  action?: AttachmentSecurityAction;
  maxSeverity?: number;
  attachmentCount?: number;
  totalBytes?: number;
  findings: AttachmentSecurityFinding[];
  warnings: string[];
  failureReason?: string;
}

export interface DeliverySnapshot {
  status: MailProcessingSnapshotStatus;
  route?: string;
  relayHost?: string;
  relayPort?: number;
  transportProfile?: string;
  targetId?: string;
  targetType?: string;
  reason?: string;
  detail?: string;
}

export interface FinalDispositionSnapshot {
  status: MailProcessingSnapshotStatus;
  result?: string;
  action?: string;
  reason?: string;
  detail?: string;
}

export interface FailureSnapshot {
  status: MailProcessingSnapshotStatus;
  step?: string;
  errorType?: string;
  reason?: string;
  detail?: string;
  retryable?: boolean;
}

export interface MailProcessingStatusSnapshot {
  mailAuth: MailAuthSnapshot;
  certificate: CertificateSnapshot;
  smime: SmimeSnapshot;
  dlp: DlpSnapshot;
  attachmentSecurity?: AttachmentSecuritySnapshot;
  delivery: DeliverySnapshot;
  finalDisposition: FinalDispositionSnapshot;
  failure: FailureSnapshot;
}

export interface MailProcessingRecord {
  id: string;
  processingId: string;
  messageId: string;
  direction: 'INBOUND' | 'OUTBOUND' | string;
  sender: string;
  recipients: string[];
  remoteHost?: string;
  helo?: string;
  receivedAt: string;
  routingDecision: string;
  result?: 'SUCCESS' | 'FAILED' | 'EXCEPTION' | string;
  disposition: 'PROCESSING' | 'DELIVERED' | 'QUARANTINED' | 'EXCEPTION' | 'FAILED' | string;
  failedStep?: string;
  failureReason?: string;
  statusSnapshot?: MailProcessingStatusSnapshot;
  steps: MailProcessingStep[];
}
