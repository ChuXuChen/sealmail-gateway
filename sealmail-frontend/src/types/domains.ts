export type DeliveryTransportProfile =
  | 'SMTP_CLEAR'
  | 'SMTP_STARTTLS_STANDARD'
  | 'SMTP_IMPLICIT_TLS_STANDARD'
  | 'SMTP_STARTTLS_GM'
  | 'SMTP_IMPLICIT_TLS_GM';

export type DecryptionMode = 'GATEWAY_TERMINATED' | 'END_TO_END_PASSTHROUGH';

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
  deliveryHost?: string;
  deliveryTransportProfile?: DeliveryTransportProfile;
  deliveryTransportProfileDisplayName?: string;
  deliveryPort?: number;
  decryptionMode?: DecryptionMode;
  decryptionModeDisplayName?: string;
  active: boolean;
}

export interface CreateDomainConfigRequest {
  domain: string;
  localDomain: boolean;
  encryptionPolicy?: string;
  preferredAlgorithm?: string;
  signingEnabled?: boolean;
  dkimEnabled?: boolean;
  deliveryHost?: string;
  deliveryTransportProfile?: DeliveryTransportProfile;
  deliveryPort?: number;
  decryptionMode?: DecryptionMode;
  active?: boolean;
}

export interface UpdateDomainConfigRequest {
  encryptionPolicy?: string;
  preferredAlgorithm?: string;
  signingEnabled?: boolean;
  dkimEnabled?: boolean;
  deliveryHost?: string;
  deliveryTransportProfile?: DeliveryTransportProfile;
  deliveryPort?: number;
  decryptionMode?: DecryptionMode;
  active?: boolean;
}
