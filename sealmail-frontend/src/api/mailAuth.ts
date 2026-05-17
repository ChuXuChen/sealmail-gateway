import type {
  ApiResponse,
  DnsRecord,
  DomainMailAuthPolicy,
  DomainMailAuthPolicyRequest,
  MailAuthDnsProbe,
  MailAuthModernStatus,
  MailAuthPolicy,
  MailAuthPolicyRequest,
  RotateDkimSelectorRequest,
} from '../types';
import apiClient from './http';

export const mailAuthApi = {
  policy: () =>
    apiClient.get<ApiResponse<MailAuthPolicy>>('/api/v1/mail-auth/policy'),

  updatePolicy: (data: MailAuthPolicyRequest) =>
    apiClient.put<ApiResponse<MailAuthPolicy>>('/api/v1/mail-auth/policy', data),

  status: () =>
    apiClient.get<ApiResponse<MailAuthModernStatus>>('/api/v1/mail-auth/status'),

  domainPolicy: (domain: string) =>
    apiClient.get<ApiResponse<DomainMailAuthPolicy>>(
      `/api/v1/mail-auth/domains/${encodeURIComponent(domain)}/policy`,
    ),

  updateDomainPolicy: (domain: string, data: DomainMailAuthPolicyRequest) =>
    apiClient.put<ApiResponse<DomainMailAuthPolicy>>(
      `/api/v1/mail-auth/domains/${encodeURIComponent(domain)}/policy`,
      data,
    ),

  dnsRecords: (domain: string) =>
    apiClient.get<ApiResponse<DnsRecord[]>>(
      `/api/v1/mail-auth/domains/${encodeURIComponent(domain)}/dns-records`,
    ),

  dnsProbe: (domain: string) =>
    apiClient.post<ApiResponse<MailAuthDnsProbe[]>>(
      `/api/v1/mail-auth/domains/${encodeURIComponent(domain)}/dns-probe`,
    ),

  rotateDkimSelector: (domain: string, data: RotateDkimSelectorRequest) =>
    apiClient.post<ApiResponse<DomainMailAuthPolicy>>(
      `/api/v1/mail-auth/domains/${encodeURIComponent(domain)}/dkim/rotate-selector`,
      data,
    ),
};
