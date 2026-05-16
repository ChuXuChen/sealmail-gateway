import type { Certificate, CertificateBinding, CertificateBindingPurpose } from '../../types';

export interface CertificatePagination {
  page: number;
  size: number;
  total: number;
}

export interface ImportCertificateValues {
  pemData: string;
  ownerEmail: string;
  alias?: string;
  trusted?: boolean;
  privateKeyData?: string;
}

export interface SelfSignedCertificateValues {
  ownerEmail: string;
  algorithm: 'RSA' | 'SM2';
  subjectDn?: string;
  alias?: string;
  validityDays?: number;
  trusted?: boolean;
}

export interface IssueByCaValues extends SelfSignedCertificateValues {
  intermediateCaId: string;
}

export const formatDate = (value?: string) => {
  if (!value) return '-';
  return new Date(value).toLocaleDateString();
};

export const certificateDisplayName = (cert: Certificate) =>
  cert.alias || cert.subjectDn || cert.ownerEmail || cert.id;

export const findBindingFor = (
  bindings: CertificateBinding[],
  record: Certificate,
  purpose: CertificateBindingPurpose,
) => bindings.find((binding) => binding.ownerEmail === record.ownerEmail && binding.purpose === purpose);
