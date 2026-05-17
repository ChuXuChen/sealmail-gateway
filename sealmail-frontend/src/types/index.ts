// API Response Types
export interface ApiResponse<T = unknown> {
  success: boolean;
  code: number;
  message: string;
  data: T;
  timestamp: string;
  requestId: string;
}

export interface PageResponse<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}

// Auth Types
export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserContext;
}

export interface UserContext {
  userId: string;
  username: string;
  email: string;
  role: string;
  roles?: string[];
  managedDomains?: string[];
}

// Certificate Types
export interface Certificate {
  id: string;
  thumbprint: string;
  ownerEmail: string;
  alias?: string;
  issuerDn?: string;
  subjectDn?: string;
  serialNumber?: string;
  subjectKeyIdentifier?: string;
  trusted: boolean;
  chainUsable?: boolean;
  revoked: boolean;
  revocationReason?: string;
  revocationDate?: string;
  revocationCrlReason?: string;
  createdAt: string;
  updatedAt?: string;
  notBefore: string;
  notAfter: string;
  keyUsages?: string[];
  algorithm?: string;
  suitableForSigning?: boolean;
  suitableForEncryption?: boolean;
  hasPrivateKey?: boolean;
  // CA / chain
  ca?: boolean;
  pathLenConstraint?: number;
  issuerCertId?: string;
  extendedKeyUsages?: string[];
  crlDistributionPointUrl?: string;
  importedCrlAvailable?: boolean;
}

export type CertificateBindingPurpose = 'ENCRYPTION' | 'SIGNING';

export interface CertificateBinding {
  id: string;
  domain: string;
  ownerEmail: string;
  certificateId: string;
  certificateAlias?: string;
  purpose: CertificateBindingPurpose;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

// Quarantine Types
export interface QuarantineItem {
  id: string;
  messageId: string;
  subject: string;
  sender: string;
  recipients: string[];
  direction?: 'INBOUND' | 'OUTBOUND';
  remoteAddress?: string;
  reason: string;
  detail?: string;
  status: 'QUARANTINED' | 'RELEASING' | 'RELEASED' | 'REJECTED';
  hasRawContent?: boolean;
  canRelease?: boolean;
  releaseUnavailableReason?: string;
  quarantinedAt: string;
  resolvedAt?: string;
  resolvedBy?: string;
  resolutionComment?: string;
  dlpEventId?: string;
  falsePositive?: boolean;
  falsePositiveAt?: string;
  falsePositiveBy?: string;
  falsePositiveComment?: string;
}

export interface ExceptionMailItem {
  id: string;
  messageId: string;
  subject: string;
  sender: string;
  recipients: string[];
  direction?: 'INBOUND' | 'OUTBOUND';
  remoteAddress?: string;
  reason: string;
  detail?: string;
  blockedBy: string;
  blockComment?: string;
  createdAt: string;
}

export type DlpAction = 'WARN' | 'MUST_ENCRYPT' | 'QUARANTINE' | 'BLOCK';
export type DlpScopeType = 'GLOBAL' | 'SENDER_DOMAIN' | 'RECIPIENT_DOMAIN';
export type DlpRuleType = 'REGEX' | 'KEYWORD' | 'BUILTIN' | 'DICTIONARY' | 'COMPOSITE';
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

export interface QuarantineStats {
  total: number;
  pending: number;
  releasing: number;
  released: number;
  rejected: number;
  byReason: Record<string, number>;
}

export interface ExceptionMailStats {
  total: number;
  byReason: Record<string, number>;
}

// Audit Log Types
export interface AuditLog {
  id: string;
  type: string;
  typeDisplayName: string;
  userId?: string;
  username?: string;
  ipAddress?: string;
  resourceType?: string;
  resourceId?: string;
  action?: string;
  detail?: string;
  success: boolean;
  errorMessage?: string;
  occurredAt: string;
}

// Domain Config Types
export interface DomainConfig {
  id: string;
  domain: string;
  localDomain: boolean;
  encryptionPolicy: string;
  encryptionPolicyDisplayName: string;
  preferredAlgorithm?: string;
  preferredAlgorithmDisplayName?: string;
  signingEnabled: boolean;
  dkimEnabled: boolean;
  active: boolean;
}

export interface CreateDomainConfigRequest {
  domain: string;
  localDomain: boolean;
  encryptionPolicy?: string;
  preferredAlgorithm?: string;
  signingEnabled?: boolean;
  dkimEnabled?: boolean;
  active?: boolean;
}

export interface UpdateDomainConfigRequest {
  encryptionPolicy?: string;
  preferredAlgorithm?: string;
  signingEnabled?: boolean;
  dkimEnabled?: boolean;
  active?: boolean;
}

export interface MailAuthStatus {
  enabled: boolean;
  authservId: string;
  dkimEnabled: boolean;
  dkimSelector?: string;
  spfEnabled: boolean;
  dmarcEnabled: boolean;
  dmarcQuarantineRejectPolicy: boolean;
  skipPrivateRelay: boolean;
}

export interface MailAuthConfig {
  enabled: boolean;
  authservId: string;
  skipPrivateRelay: boolean;
  dkimEnabled: boolean;
  dkimSelector: string;
  dkimPrivateKeyPath?: string;
  dkimPrivateKeySecretRef?: string;
  dkimPrivateKeyConfigured: boolean;
  dkimSignedHeaders: string[];
  spfEnabled: boolean;
  spfMaxDnsLookups: number;
  spfUseA: boolean;
  spfUseMx: boolean;
  spfIp4: string[];
  spfIp6: string[];
  spfIncludes: string[];
  spfAllPolicy: '-all' | '~all' | '?all';
  dmarcEnabled: boolean;
  dmarcPolicy: 'none' | 'quarantine' | 'reject';
  dmarcAdkim: 'r' | 's';
  dmarcAspf: 'r' | 's';
  dmarcPct: number;
  dmarcRua?: string;
  dmarcRuf?: string;
  dmarcFailureAction: 'LOG_ONLY' | 'APPLY_POLICY';
  dmarcQuarantineRejectPolicy: boolean;
  updatedAt?: string;
}

export type MailAuthConfigRequest = Partial<MailAuthConfig> & {
  clearDkimPrivateKeySecretRef?: boolean;
};

export type TrustedProxyMode = 'DISABLED' | 'TRUSTED_HEADERS' | 'XFORWARD';
export type MailAuthFailureAction = 'LOG_ONLY' | 'APPLY_POLICY' | 'FORCE_QUARANTINE' | 'FORCE_ALLOW';
export type DmarcPolicy = 'none' | 'quarantine' | 'reject';
export type DmarcAlignment = 'r' | 's';
export type SpfAllPolicy = '-all' | '~all' | '?all';

export interface MailAuthPolicy {
  id: string;
  enabled: boolean;
  authservId: string;
  trustedProxyMode: TrustedProxyMode;
  failureDefaultAction: MailAuthFailureAction;
  createdAt?: string;
  updatedAt?: string;
  version: number;
}

export interface MailAuthPolicyRequest {
  enabled?: boolean;
  authservId?: string;
  trustedProxyMode?: TrustedProxyMode;
  failureDefaultAction?: MailAuthFailureAction;
}

export interface DomainMailAuthPolicy {
  domainName: string;
  enabled: boolean;
  dkimSigningEnabled: boolean;
  dkimSelector: string;
  dkimKeySecretRef?: string;
  dkimKeyPath?: string;
  dkimKeyConfigured: boolean;
  dkimSignedHeaders: string[];
  spfPublishEnabled: boolean;
  spfUseA: boolean;
  spfUseMx: boolean;
  spfIp4: string[];
  spfIp6: string[];
  spfIncludes: string[];
  spfAllPolicy: SpfAllPolicy;
  dmarcPublishEnabled: boolean;
  dmarcPolicy: DmarcPolicy;
  dmarcSubdomainPolicy: DmarcPolicy;
  dmarcAdkim: DmarcAlignment;
  dmarcAspf: DmarcAlignment;
  dmarcPct: number;
  dmarcRua?: string;
  dmarcRuf?: string;
  createdAt?: string;
  updatedAt?: string;
  version: number;
}

export type DomainMailAuthPolicyRequest = Partial<Pick<DomainMailAuthPolicy,
  | 'enabled'
  | 'dkimSigningEnabled'
  | 'dkimSelector'
  | 'dkimKeySecretRef'
  | 'dkimKeyPath'
  | 'dkimSignedHeaders'
  | 'spfPublishEnabled'
  | 'spfUseA'
  | 'spfUseMx'
  | 'spfIp4'
  | 'spfIp6'
  | 'spfIncludes'
  | 'spfAllPolicy'
  | 'dmarcPublishEnabled'
  | 'dmarcPolicy'
  | 'dmarcSubdomainPolicy'
  | 'dmarcAdkim'
  | 'dmarcAspf'
  | 'dmarcPct'
  | 'dmarcRua'
  | 'dmarcRuf'
>>;

export interface RotateDkimSelectorRequest {
  selector: string;
  keySecretRef?: string;
  keyPath?: string;
  signedHeaders?: string[];
}

export interface MailAuthDnsProbe {
  domainName: string;
  recordType: string;
  expectedName: string;
  expectedValueHash: string;
  observedValue?: string;
  status: 'MATCH' | 'MISMATCH' | 'MISSING' | 'TEMP_ERROR' | string;
  detail?: string;
  checkedAt: string;
}

export interface MailAuthModernStatus {
  enabled: boolean;
  authservId: string;
  trustedProxyMode: TrustedProxyMode;
  failureDefaultAction: MailAuthFailureAction;
  domainPolicyCount: number;
  dkimEnabledDomainCount: number;
  recentDnsProbes: MailAuthDnsProbe[];
}

export interface DnsRecord {
  type: 'DKIM' | 'SPF' | 'DMARC' | string;
  name: string;
  value: string;
  available: boolean;
}

export interface SystemSettings {
  runtime: {
    applicationName: string;
    activeProfiles: string[];
    onlineEditingSupported: boolean;
    configSource: string;
    generatedAt: string;
  };
  smtpServer: {
    bindAddress: string;
    port: number;
    maxConnections: number;
    maxMessageSizeBytes: number;
    tls: {
      startTlsEnabled: boolean;
      tlsRequired: boolean;
      keystoreConfigured: boolean;
      pemConfigured: boolean;
      keyAlias?: string;
      engine?: string;
      provider?: string;
      protocol?: string;
      enabledProtocols?: string[];
      enabledCipherSuites?: string[];
    };
  };
  delivery: {
    mode: 'POSTFIX' | 'DIRECT_RELAY' | string;
    postfix: {
      enabled: boolean;
      host: string;
      afterFilterPort: number;
      outboundPort: number;
      useTls: boolean;
      transportSecurity: SmtpTransportSecurity;
      timeoutMs: number;
      envelopeFrom?: string;
    };
    directRelay: {
      host: string;
      port: number;
      useTls: boolean;
      transportSecurity: SmtpTransportSecurity;
      timeoutMs: number;
      usernameConfigured: boolean;
      passwordConfigured: boolean;
    };
  };
  quarantinePolicy: {
    maxRetentionDays: number;
    notificationEnabled: boolean;
    releaseRequiresEncryption: boolean;
  };
  certificateValidation: {
    crlEnabled: boolean;
    ocspEnabled: boolean;
    ocspTimeoutMs: number;
  };
  internalCa: {
    crlBaseUrl: string;
    defaultRootValidityDays: number;
    defaultIntermediateValidityDays: number;
    defaultEndEntityValidityDays: number;
  };
  cryptoCapabilities: {
    category: string;
    algorithms: string[];
  }[];
}

export type SmtpTransportSecurity = 'NONE' | 'STARTTLS' | 'SMTPS';

export interface RelayPolicy {
  enabled: boolean;
  host: string;
  port: number;
  useTls: boolean;
  transportSecurity: SmtpTransportSecurity;
  username?: string;
  passwordConfigured: boolean;
  passwordSecretRef?: string;
  timeoutMs: number;
  envelopeFrom?: string;
  updatedAt?: string;
}

export interface QuarantinePolicy {
  maxRetentionDays: number;
  notificationEnabled: boolean;
  releaseRequiresEncryption: boolean;
  updatedAt?: string;
}

export type RelayPolicyRequest = Partial<RelayPolicy> & {
  clearPasswordSecretRef?: boolean;
};

export type QuarantinePolicyRequest = Partial<QuarantinePolicy>;

export type CreateDlpPatternRequest = Omit<DlpPattern, 'id' | 'createdAt' | 'updatedAt'>;
export type UpdateDlpPatternRequest = Partial<CreateDlpPatternRequest>;

export type CreateDlpSelectionRequest = Omit<DlpSelection, 'id' | 'createdAt' | 'updatedAt'>;
export type UpdateDlpSelectionRequest = Partial<CreateDlpSelectionRequest>;
export type DlpRuleRequest = Omit<DlpRule, 'id' | 'createdAt' | 'updatedAt'>;
export type DlpRuleGroupRequest = Omit<DlpRuleGroup, 'id' | 'createdAt' | 'updatedAt'>;
export type DlpPolicyRequest = Omit<DlpPolicy, 'id' | 'createdAt' | 'updatedAt'>;
export interface DlpTestRequest {
  subject?: string;
  body?: string;
  sender?: string;
  recipients?: string[];
  direction?: 'INBOUND' | 'OUTBOUND';
}

export interface SendProtectedMailRequest {
  from: string;
  to: string[];
  subject: string;
  content: string;
}

// Certificate Issuance Types
export interface GenerateSelfSignedRequest {
  ownerEmail: string;
  algorithm: 'RSA' | 'SM2';
  subjectDn?: string;
  alias?: string;
  validityDays?: number;
  trusted?: boolean;
}

export interface CreateRootCaRequest {
  commonName: string;
  algorithm: 'RSA' | 'SM2';
  subjectDn?: string;
  alias?: string;
  validityDays?: number;
}

export interface CreateIntermediateCaRequest {
  rootCaId: string;
  commonName: string;
  algorithm: 'RSA' | 'SM2';
  subjectDn?: string;
  alias?: string;
  validityDays?: number;
}

export interface IssueEndEntityRequest {
  intermediateCaId: string;
  ownerEmail: string;
  algorithm: 'RSA' | 'SM2';
  subjectDn?: string;
  alias?: string;
  validityDays?: number;
  trusted?: boolean;
}

export interface SignCsrRequest {
  caCertId: string;
  csrPem: string;
  alias?: string;
  validityDays?: number;
  trusted?: boolean;
}
