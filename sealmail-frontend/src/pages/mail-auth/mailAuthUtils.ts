import type {
  DmarcAlignment,
  DmarcPolicy,
  DomainMailAuthPolicy,
  DomainMailAuthPolicyRequest,
  MailAuthFailureAction,
  MailAuthPolicy,
  MailAuthPolicyRequest,
  SpfAllPolicy,
  TrustedProxyMode,
} from '../../types';

export interface MailAuthGlobalFormValues {
  enabled: boolean;
  authservId: string;
  trustedProxyMode: TrustedProxyMode;
  failureDefaultAction: MailAuthFailureAction;
}

export interface MailAuthDomainFormValues {
  enabled: boolean;
  dkimSigningEnabled: boolean;
  dkimSelector: string;
  dkimKeySecretRef?: string;
  dkimKeyPath?: string;
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
}

export interface RotateDkimFormValues {
  selector: string;
  keySecretRef?: string;
  keyPath?: string;
  signedHeaders: string[];
}

export const normalizeDomain = (value: string) =>
  value.trim().toLowerCase().replace(/\.+$/, '');

export const dkimHeaderOptions = [
  'from',
  'to',
  'cc',
  'reply-to',
  'subject',
  'date',
  'message-id',
  'mime-version',
  'content-type',
].map((value) => ({ value, label: value }));

export const trustedProxyModeOptions = [
  { value: 'DISABLED', label: '禁用覆盖' },
  { value: 'TRUSTED_HEADERS', label: '可信中继头' },
  { value: 'XFORWARD', label: 'XFORWARD' },
];

export const failureActionOptions = [
  { value: 'LOG_ONLY', label: '仅记录' },
  { value: 'APPLY_POLICY', label: '按 DMARC 策略' },
  { value: 'FORCE_QUARANTINE', label: '强制隔离' },
  { value: 'FORCE_ALLOW', label: '强制放行' },
];

export const spfAllPolicyOptions = [
  { value: '-all', label: '-all' },
  { value: '~all', label: '~all' },
  { value: '?all', label: '?all' },
  { value: '+all', label: '+all' },
];

export const dmarcPolicyOptions = [
  { value: 'none', label: 'none' },
  { value: 'quarantine', label: 'quarantine' },
  { value: 'reject', label: 'reject' },
];

export const alignmentOptions = [
  { value: 'r', label: 'relaxed' },
  { value: 's', label: 'strict' },
];

export const emptyGlobalPolicy: MailAuthGlobalFormValues = {
  enabled: true,
  authservId: 'sealmail-gateway',
  trustedProxyMode: 'DISABLED',
  failureDefaultAction: 'LOG_ONLY',
};

export const emptyDomainPolicy: MailAuthDomainFormValues = {
  enabled: true,
  dkimSigningEnabled: false,
  dkimSelector: 'sealmail',
  dkimKeySecretRef: '',
  dkimKeyPath: '',
  dkimSignedHeaders: ['from', 'to', 'subject', 'date', 'message-id'],
  spfPublishEnabled: true,
  spfUseA: true,
  spfUseMx: true,
  spfIp4: [],
  spfIp6: [],
  spfIncludes: [],
  spfAllPolicy: '~all',
  dmarcPublishEnabled: true,
  dmarcPolicy: 'none',
  dmarcSubdomainPolicy: 'none',
  dmarcAdkim: 'r',
  dmarcAspf: 'r',
  dmarcPct: 100,
  dmarcRua: '',
  dmarcRuf: '',
};

export const globalFormValues = (policy: MailAuthPolicy | null): MailAuthGlobalFormValues => ({
  ...emptyGlobalPolicy,
  ...policy,
});

export const domainFormValues = (policy: DomainMailAuthPolicy | null): MailAuthDomainFormValues => ({
  ...emptyDomainPolicy,
  ...policy,
  dkimKeySecretRef: policy?.dkimKeySecretRef || '',
  dkimKeyPath: policy?.dkimKeyPath || '',
  dmarcRua: policy?.dmarcRua || '',
  dmarcRuf: policy?.dmarcRuf || '',
});

export const globalPayload = (values: MailAuthGlobalFormValues): MailAuthPolicyRequest => ({
  enabled: values.enabled,
  authservId: values.authservId,
  trustedProxyMode: values.trustedProxyMode,
  failureDefaultAction: values.failureDefaultAction,
});

export const domainPayload = (values: MailAuthDomainFormValues): DomainMailAuthPolicyRequest => ({
  enabled: values.enabled,
  dkimSigningEnabled: values.dkimSigningEnabled,
  dkimSelector: values.dkimSelector,
  dkimKeySecretRef: values.dkimKeySecretRef?.trim() || undefined,
  dkimKeyPath: values.dkimKeyPath?.trim() || undefined,
  dkimSignedHeaders: values.dkimSignedHeaders || [],
  spfPublishEnabled: values.spfPublishEnabled,
  spfUseA: values.spfUseA,
  spfUseMx: values.spfUseMx,
  spfIp4: cleanList(values.spfIp4),
  spfIp6: cleanList(values.spfIp6),
  spfIncludes: cleanList(values.spfIncludes),
  spfAllPolicy: values.spfAllPolicy,
  dmarcPublishEnabled: values.dmarcPublishEnabled,
  dmarcPolicy: values.dmarcPolicy,
  dmarcSubdomainPolicy: values.dmarcSubdomainPolicy,
  dmarcAdkim: values.dmarcAdkim,
  dmarcAspf: values.dmarcAspf,
  dmarcPct: values.dmarcPct,
  dmarcRua: values.dmarcRua?.trim() || undefined,
  dmarcRuf: values.dmarcRuf?.trim() || undefined,
});

export const cleanList = (values?: string[]) =>
  (values || [])
    .map((value) => value.trim())
    .filter(Boolean);
