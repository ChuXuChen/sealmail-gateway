import axios from 'axios';
import {
  ApiResponse,
  LoginRequest,
  LoginResponse,
  Certificate,
  CertificateBinding,
  CertificateBindingPurpose,
  ExceptionMailItem,
  ExceptionMailStats,
  QuarantineItem,
  QuarantineStats,
  PageResponse,
  AuditLog,
  DomainConfig,
  MailAuthStatus,
  MailAuthConfig,
  DnsRecord,
  DlpPattern,
  DlpSelection,
  SystemSettings,
  RelayPolicy,
  QuarantinePolicy,
  GenerateSelfSignedRequest,
  CreateRootCaRequest,
  CreateIntermediateCaRequest,
  IssueEndEntityRequest,
  SignCsrRequest,
} from '../types';

const apiClient = axios.create({
  baseURL: '',
  timeout: 30000,
});

// Request interceptor: Add auth token
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor: Handle common errors
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const isLoginRequest = error.config?.url?.includes('/api/v1/auth/login');
      if (isLoginRequest) {
        return Promise.reject(error);
      }
      localStorage.removeItem('accessToken');
      localStorage.removeItem('user');
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

// Auth API
export const authApi = {
  login: (data: LoginRequest) =>
    apiClient.post<ApiResponse<LoginResponse>>('/api/v1/auth/login', data),

  logout: () =>
    apiClient.post<ApiResponse<void>>('/api/v1/auth/logout'),
};

// Audit Log API
export const auditLogApi = {
  list: (params: { page?: number; size?: number; category?: string; type?: string; success?: boolean }) =>
    apiClient.get<ApiResponse<PageResponse<AuditLog>>>('/api/v1/audit-logs', { params }),

  getByType: (type: string, params: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<AuditLog>>>(`/api/v1/audit-logs/type/${type}`, { params }),

  getByUserId: (userId: string, params: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<AuditLog>>>(`/api/v1/audit-logs/user/${userId}`, { params }),

  getByTimeRange: (startTime: string, endTime: string, params: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<AuditLog>>>(`/api/v1/audit-logs/time-range`, {
      params: { startTime, endTime, ...params },
    }),
};

// Domain Config API
export const domainConfigApi = {
  findAll: () =>
    apiClient.get<ApiResponse<DomainConfig[]>>('/api/v1/domains'),

  findAllActive: () =>
    apiClient.get<ApiResponse<DomainConfig[]>>('/api/v1/domains/active'),

  findLocalDomains: () =>
    apiClient.get<ApiResponse<DomainConfig[]>>('/api/v1/domains/local'),

  findRemoteDomains: () =>
    apiClient.get<ApiResponse<DomainConfig[]>>('/api/v1/domains/remote'),

  findById: (id: string) =>
    apiClient.get<ApiResponse<DomainConfig>>(`/api/v1/domains/${encodeURIComponent(id)}`),

  findByDomain: (domain: string) =>
    apiClient.get<ApiResponse<DomainConfig>>(`/api/v1/domains/domain/${encodeURIComponent(domain)}`),

  create: (data: { domain: string; localDomain: boolean; encryptionPolicy?: string; preferredAlgorithm?: string; signingEnabled?: boolean; dkimEnabled?: boolean; active?: boolean }) =>
    apiClient.post<ApiResponse<DomainConfig>>('/api/v1/domains', data),

  update: (id: string, data: { encryptionPolicy?: string; preferredAlgorithm?: string; signingEnabled?: boolean; dkimEnabled?: boolean; active?: boolean }) =>
    apiClient.put<ApiResponse<DomainConfig>>(`/api/v1/domains/${encodeURIComponent(id)}`, data),

  delete: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/api/v1/domains/${encodeURIComponent(id)}`),
};

export const mailAuthApi = {
  config: () =>
    apiClient.get<ApiResponse<MailAuthConfig>>('/api/v1/mail-auth/config'),

  updateConfig: (data: Partial<MailAuthConfig> & {
    clearDkimPrivateKeySecretRef?: boolean;
  }) =>
    apiClient.put<ApiResponse<MailAuthConfig>>('/api/v1/mail-auth/config', data),

  dnsRecords: (domain: string) =>
    apiClient.get<ApiResponse<DnsRecord[]>>('/api/v1/mail-auth/dns-records', {
      params: { domain },
    }),

  status: () =>
    apiClient.get<ApiResponse<MailAuthStatus>>('/api/v1/mail-auth/status'),

  update: (data: Partial<Pick<MailAuthStatus,
    'enabled' | 'dkimEnabled' | 'spfEnabled' | 'dmarcEnabled' | 'dmarcQuarantineRejectPolicy' | 'skipPrivateRelay'
  >>) =>
    apiClient.put<ApiResponse<MailAuthStatus>>('/api/v1/mail-auth/status', data),
};

export const systemSettingsApi = {
  get: () =>
    apiClient.get<ApiResponse<SystemSettings>>('/api/v1/system-settings'),
};

export const runtimePolicyApi = {
  getRelay: () =>
    apiClient.get<ApiResponse<RelayPolicy>>('/api/v1/runtime-policies/relay'),

  updateRelay: (data: Partial<RelayPolicy> & { clearPasswordSecretRef?: boolean }) =>
    apiClient.put<ApiResponse<RelayPolicy>>('/api/v1/runtime-policies/relay', data),

  getQuarantine: () =>
    apiClient.get<ApiResponse<QuarantinePolicy>>('/api/v1/runtime-policies/quarantine'),

  updateQuarantine: (data: Partial<QuarantinePolicy>) =>
    apiClient.put<ApiResponse<QuarantinePolicy>>('/api/v1/runtime-policies/quarantine', data),
};

// Certificate API (end-entity)
export const certificateApi = {
  list: (params: { owner?: string; includeAll?: boolean; page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<Certificate>>>('/api/v1/certificates', { params }),

  getById: (id: string) =>
    apiClient.get<ApiResponse<Certificate>>(`/api/v1/certificates/${id}`),

  import: (data: { pemData: string; ownerEmail: string; alias?: string; trusted?: boolean; privateKeyData?: string }) =>
    apiClient.post<ApiResponse<Certificate>>('/api/v1/certificates/import', data),

  trust: (id: string) =>
    apiClient.post<ApiResponse<Certificate>>(`/api/v1/certificates/${id}/trust`),

  untrust: (id: string) =>
    apiClient.post<ApiResponse<Certificate>>(`/api/v1/certificates/${id}/untrust`),

  revoke: (id: string, reason?: string) =>
    apiClient.post<ApiResponse<Certificate>>(`/api/v1/certificates/${id}/revoke`, { reason }),

  delete: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/api/v1/certificates/${id}`),

  generateSelfSigned: (data: GenerateSelfSignedRequest) =>
    apiClient.post<ApiResponse<Certificate>>('/api/v1/certificates/generate-self-signed', data),

  issue: (data: IssueEndEntityRequest) =>
    apiClient.post<ApiResponse<Certificate>>('/api/v1/certificates/issue', data),

  signCsr: (data: SignCsrRequest) =>
    apiClient.post<ApiResponse<Certificate>>('/api/v1/certificates/sign-csr', data),
};

export const certificateBindingApi = {
  list: (params?: { domain?: string; owner?: string }) =>
    apiClient.get<ApiResponse<CertificateBinding[]>>('/api/v1/certificate-bindings', { params }),

  upsert: (data: {
    ownerEmail: string;
    certificateId: string;
    purpose: CertificateBindingPurpose;
    enabled: boolean;
  }) =>
    apiClient.post<ApiResponse<CertificateBinding>>('/api/v1/certificate-bindings', data),

  delete: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/api/v1/certificate-bindings/${encodeURIComponent(id)}`),
};

// CA API
export const caApi = {
  list: () => apiClient.get<ApiResponse<Certificate[]>>('/api/v1/cas'),
  getById: (id: string) => apiClient.get<ApiResponse<Certificate>>(`/api/v1/cas/${id}`),
  createRoot: (data: CreateRootCaRequest) =>
    apiClient.post<ApiResponse<Certificate>>('/api/v1/cas/root', data),
  createIntermediate: (data: CreateIntermediateCaRequest) =>
    apiClient.post<ApiResponse<Certificate>>('/api/v1/cas/intermediate', data),
  import: (data: { pemData: string; ownerEmail: string; alias?: string; trusted?: boolean; privateKeyData?: string }) =>
    apiClient.post<ApiResponse<Certificate>>('/api/v1/cas/import', data),
  importCrl: (id: string, data: { crlPem?: string; crlDerBase64?: string }) =>
    apiClient.post<ApiResponse<Certificate>>(`/api/v1/cas/${encodeURIComponent(id)}/crl`, data),
  trust: (id: string) =>
    apiClient.post<ApiResponse<Certificate>>(`/api/v1/cas/${id}/trust`),
  untrust: (id: string) =>
    apiClient.post<ApiResponse<Certificate>>(`/api/v1/cas/${id}/untrust`),
  revoke: (id: string, reason?: string) =>
    apiClient.post<ApiResponse<Certificate>>(`/api/v1/cas/${id}/revoke`, { reason }),
  delete: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/api/v1/cas/${id}`),
};

// CRL download URLs (anonymous endpoint)
export const crlUrls = {
  der: (caId: string) => `/api/v1/crl/${caId}`,
  pem: (caId: string) => `/api/v1/crl/${caId}.pem`,
};

// Exception Mail API
export const exceptionMailApi = {
  getStats: () =>
    apiClient.get<ApiResponse<ExceptionMailStats>>('/api/v1/exception-mails/stats'),

  list: (params: { page?: number; size?: number; reason?: string }) =>
    apiClient.get<ApiResponse<PageResponse<ExceptionMailItem>>>('/api/v1/exception-mails', { params }),

  getById: (id: string) =>
    apiClient.get<ApiResponse<ExceptionMailItem>>(`/api/v1/exception-mails/${id}`),
};

// DLP Quarantine API
export const dlpQuarantineApi = {
  getStats: () =>
    apiClient.get<ApiResponse<QuarantineStats>>('/api/v1/dlp/quarantine/stats'),

  list: (params: { page?: number; size?: number; reason?: string }) =>
    apiClient.get<ApiResponse<PageResponse<QuarantineItem>>>('/api/v1/dlp/quarantine', { params }),

  getById: (id: string) =>
    apiClient.get<ApiResponse<QuarantineItem>>(`/api/v1/dlp/quarantine/${id}`),

  release: (id: string, data?: { releasedBy?: string; comment?: string; encryptBeforeRelease?: boolean }) =>
    apiClient.post<ApiResponse<QuarantineItem>>(`/api/v1/dlp/quarantine/${id}/release`, data),

  reject: (id: string, data?: { rejectedBy?: string; comment?: string }) =>
    apiClient.post<ApiResponse<QuarantineItem>>(`/api/v1/dlp/quarantine/${id}/reject`, data),

  batchRelease: (ids: string[]) =>
    apiClient.post<ApiResponse<void>>('/api/v1/dlp/quarantine/batch-release', ids),

  batchReject: (ids: string[]) =>
    apiClient.post<ApiResponse<void>>('/api/v1/dlp/quarantine/batch-reject', ids),
};

export const dlpApi = {
  listPatterns: () =>
    apiClient.get<ApiResponse<DlpPattern[]>>('/api/v1/dlp/patterns'),

  createPattern: (data: Omit<DlpPattern, 'id' | 'createdAt' | 'updatedAt'>) =>
    apiClient.post<ApiResponse<DlpPattern>>('/api/v1/dlp/patterns', data),

  updatePattern: (id: string, data: Partial<Omit<DlpPattern, 'id' | 'createdAt' | 'updatedAt'>>) =>
    apiClient.put<ApiResponse<DlpPattern>>(`/api/v1/dlp/patterns/${id}`, data),

  deletePattern: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/api/v1/dlp/patterns/${id}`),

  listSelections: () =>
    apiClient.get<ApiResponse<DlpSelection[]>>('/api/v1/dlp/selections'),

  createSelection: (data: Omit<DlpSelection, 'id' | 'createdAt' | 'updatedAt'>) =>
    apiClient.post<ApiResponse<DlpSelection>>('/api/v1/dlp/selections', data),

  updateSelection: (id: string, data: Partial<Omit<DlpSelection, 'id' | 'createdAt' | 'updatedAt'>>) =>
    apiClient.put<ApiResponse<DlpSelection>>(`/api/v1/dlp/selections/${id}`, data),

  deleteSelection: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/api/v1/dlp/selections/${id}`),
};

// Mail Test API
export const mailTestApi = {
  sendEncrypted: (data: {
    from: string;
    to: string[];
    subject: string;
    content: string;
    preferredAlgorithm?: string;
  }) => apiClient.post<ApiResponse<string>>('/api/v1/mail-test/send-encrypted', data),

  testSmtpConfig: () =>
    apiClient.get<ApiResponse<string>>('/api/v1/mail-test/test-smtp-config'),
};

export default apiClient;
