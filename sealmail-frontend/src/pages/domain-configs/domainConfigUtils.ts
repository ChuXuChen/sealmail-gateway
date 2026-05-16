import type { MailAuthConfig } from '../../types';

export interface DomainConfigFormValues {
  domain: string;
  localDomain?: boolean;
  encryptionPolicy?: string;
  preferredAlgorithm?: string;
  signingEnabled?: boolean;
  dkimEnabled?: boolean;
  active?: boolean;
}

export type MailAuthFormValues = MailAuthConfig & {
  clearDkimPrivateKeySecretRef?: boolean;
};

export const domainPattern = /^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}\.?$/;

export const normalizeDomain = (value: string) =>
  value.trim().toLowerCase().replace(/\.+$/, '');

export const emptyMailAuthConfig: MailAuthConfig = {
  enabled: false,
  authservId: 'sealmail-gateway',
  skipPrivateRelay: true,
  dkimEnabled: false,
  dkimSelector: 'sealmail',
  dkimPrivateKeyPath: '',
  dkimPrivateKeySecretRef: '',
  dkimPrivateKeyConfigured: false,
  dkimSignedHeaders: ['from', 'to', 'subject', 'date', 'message-id'],
  spfEnabled: false,
  spfMaxDnsLookups: 10,
  spfUseA: true,
  spfUseMx: true,
  spfIp4: [],
  spfIp6: [],
  spfIncludes: [],
  spfAllPolicy: '~all',
  dmarcEnabled: false,
  dmarcPolicy: 'quarantine',
  dmarcAdkim: 'r',
  dmarcAspf: 'r',
  dmarcPct: 100,
  dmarcRua: '',
  dmarcRuf: '',
  dmarcFailureAction: 'APPLY_POLICY',
  dmarcQuarantineRejectPolicy: true,
};

export const initialMailAuthValues = (config: MailAuthConfig | null): MailAuthFormValues => ({
  ...emptyMailAuthConfig,
  ...config,
  clearDkimPrivateKeySecretRef: false,
});
