export interface DomainConfigFormValues {
  domain: string;
  localDomain?: boolean;
  encryptionPolicy?: string;
  preferredAlgorithm?: string;
  signingEnabled?: boolean;
  dkimEnabled?: boolean;
  active?: boolean;
}

export const domainPattern = /^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}\.?$/;

export const normalizeDomain = (value: string) =>
  value.trim().toLowerCase().replace(/\.+$/, '');
