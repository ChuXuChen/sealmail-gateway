import type {
  ApiResponse,
  CreateDomainConfigRequest,
  DnsRecord,
  DomainConfig,
  MailAuthConfig,
  MailAuthConfigRequest,
  MailAuthStatus,
  UpdateDomainConfigRequest,
} from '../types';
import apiClient from './http';

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

  create: (data: CreateDomainConfigRequest) =>
    apiClient.post<ApiResponse<DomainConfig>>('/api/v1/domains', data),

  update: (id: string, data: UpdateDomainConfigRequest) =>
    apiClient.put<ApiResponse<DomainConfig>>(`/api/v1/domains/${encodeURIComponent(id)}`, data),

  delete: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/api/v1/domains/${encodeURIComponent(id)}`),
};

export const mailAuthApi = {
  config: () =>
    apiClient.get<ApiResponse<MailAuthConfig>>('/api/v1/mail-auth/config'),

  updateConfig: (data: MailAuthConfigRequest) =>
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
