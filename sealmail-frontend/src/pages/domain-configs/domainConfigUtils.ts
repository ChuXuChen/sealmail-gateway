import type { DeliveryTransportProfile } from '../../types';

export interface DomainConfigFormValues {
  domain: string;
  localDomain?: boolean;
  encryptionPolicy?: string;
  preferredAlgorithm?: string;
  signingEnabled?: boolean;
  dkimEnabled?: boolean;
  deliveryHost?: string;
  deliveryTransportProfile?: DeliveryTransportProfile;
  decryptionMode?: 'GATEWAY_TERMINATED' | 'END_TO_END_PASSTHROUGH';
  active?: boolean;
}

export const domainPattern = /^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}\.?$/;

export const deliveryTransportProfiles: Array<{
  value: DeliveryTransportProfile;
  label: string;
  port: number;
}> = [
  { value: 'SMTP_CLEAR', label: 'SMTP 明文', port: 25 },
  { value: 'SMTP_STARTTLS_STANDARD', label: 'SMTP STARTTLS 国际 TLS', port: 587 },
  { value: 'SMTP_IMPLICIT_TLS_STANDARD', label: 'SMTP 隐式国际 TLS', port: 465 },
  { value: 'SMTP_STARTTLS_GM', label: 'SMTP STARTTLS 国密', port: 2525 },
  { value: 'SMTP_IMPLICIT_TLS_GM', label: 'SMTP 隐式国密 TLS', port: 2465 },
];

export const deliveryProfilePort = (profile?: string) =>
  deliveryTransportProfiles.find((item) => item.value === profile)?.port ?? 25;

export const deliveryProfileLabel = (profile?: string, displayName?: string) =>
  displayName || deliveryTransportProfiles.find((item) => item.value === profile)?.label || profile || 'SMTP 明文';

export const algorithmPreferenceLabel = (algorithm?: string, displayName?: string) => {
  if (algorithm === 'GM_ONLY') return '仅国密';
  if (algorithm === 'STANDARD_ONLY') return '仅国际';
  return displayName || '自动选择';
};

export const normalizeDomain = (value: string) =>
  value.trim().toLowerCase().replace(/\.+$/, '');

export const normalizeDeliveryHost = (value?: string) => {
  const normalized = value?.trim();
  return normalized || undefined;
};
