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
  status: 'QUARANTINED' | 'RELEASED' | 'REJECTED';
  hasRawContent?: boolean;
  canRelease?: boolean;
  releaseUnavailableReason?: string;
  quarantinedAt: string;
  resolvedAt?: string;
  resolvedBy?: string;
  resolutionComment?: string;
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

export interface DlpPattern {
  id: string;
  name: string;
  description?: string;
  regex: string;
  action: DlpAction;
  severity: number;
  priority: number;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
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
      timeoutMs: number;
      envelopeFrom?: string;
    };
    directRelay: {
      host: string;
      port: number;
      useTls: boolean;
      timeoutMs: number;
      usernameConfigured: boolean;
      passwordConfigured: boolean;
    };
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
