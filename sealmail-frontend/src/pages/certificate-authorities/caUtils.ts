import type { Certificate } from '../../types';

export type CaTableRecord = Certificate & {
  children?: CaTableRecord[];
};

export type CaTypeFilter = 'ALL' | 'ROOT' | 'INTERMEDIATE';
export type TrustFilter = 'ALL' | 'TRUSTED' | 'UNTRUSTED';
export type StatusFilter = 'ALL' | 'VALID' | 'REVOKED' | 'CHAIN_BROKEN';
export type KeyFilter = 'ALL' | 'WITH_KEY' | 'WITHOUT_KEY';
export type IssuableFilter = 'ALL' | 'ISSUABLE';
export type ConfirmActionType = 'untrust' | 'revoke' | 'delete';

export interface CaFilters {
  type: CaTypeFilter;
  rootId: string;
  trust: TrustFilter;
  status: StatusFilter;
  key: KeyFilter;
  algorithm: string;
  issuable: IssuableFilter;
}

export interface ConfirmAction {
  type: ConfirmActionType;
  record: Certificate;
  closeDetail?: boolean;
}

export interface CreateRootCaValues {
  commonName: string;
  algorithm: 'RSA' | 'SM2';
  subjectDn?: string;
  alias?: string;
  validityDays?: number;
}

export interface CreateIntermediateCaValues extends CreateRootCaValues {
  rootCaId: string;
}

export interface SignCsrValues {
  caCertId: string;
  csrPem: string;
  alias?: string;
  validityDays?: number;
  trusted?: boolean;
}

export const defaultCaFilters: CaFilters = {
  type: 'ALL',
  rootId: 'ALL',
  trust: 'ALL',
  status: 'ALL',
  key: 'ALL',
  algorithm: 'ALL',
  issuable: 'ALL',
};

export const roleOf = (cert: Certificate): string => {
  if (cert.pathLenConstraint === 1) return 'Root CA';
  if (cert.pathLenConstraint === 0) return 'Intermediate CA';
  return 'CA';
};

export const getUnavailableSigningReason = (cert: Certificate): string | null => {
  if (cert.pathLenConstraint !== 0) return '不是 Intermediate CA';
  if (!cert.hasPrivateKey) return '未关联托管私钥';
  if (cert.revoked) return '已吊销';
  if (!cert.trusted) return '未信任';
  if (cert.chainUsable === false) return '链路失效';
  return null;
};

export const formatDate = (value?: string) => {
  if (!value) return '-';
  return new Date(value).toLocaleDateString();
};

export const getDisplayName = (cert?: Certificate | null) =>
  cert?.alias || cert?.subjectDn || cert?.ownerEmail || cert?.id || '-';

export const matchesCertificateFilters = (cert: Certificate, filters: CaFilters) => {
  if (filters.type === 'ROOT' && cert.pathLenConstraint !== 1) return false;
  if (filters.type === 'INTERMEDIATE' && cert.pathLenConstraint !== 0) return false;

  if (filters.rootId !== 'ALL' && cert.id !== filters.rootId && cert.issuerCertId !== filters.rootId) {
    return false;
  }

  if (filters.trust === 'TRUSTED' && !cert.trusted) return false;
  if (filters.trust === 'UNTRUSTED' && cert.trusted) return false;

  if (filters.status === 'VALID' && cert.revoked) return false;
  if (filters.status === 'REVOKED' && !cert.revoked) return false;
  if (filters.status === 'CHAIN_BROKEN' && cert.chainUsable !== false) return false;

  if (filters.key === 'WITH_KEY' && !cert.hasPrivateKey) return false;
  if (filters.key === 'WITHOUT_KEY' && cert.hasPrivateKey) return false;

  if (filters.algorithm !== 'ALL' && cert.algorithm !== filters.algorithm) return false;
  if (filters.issuable === 'ISSUABLE' && getUnavailableSigningReason(cert)) return false;

  return true;
};
