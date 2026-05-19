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
