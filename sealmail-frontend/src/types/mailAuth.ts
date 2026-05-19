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
