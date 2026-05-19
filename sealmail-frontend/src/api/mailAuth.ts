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
import { unwrapApiResponse } from './response';

export const mailAuthApi = {
  policy: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<MailAuthPolicy>>('/api/v1/mail-auth/policy')),

  updatePolicy: (data: MailAuthPolicyRequest) =>
    unwrapApiResponse(apiClient.put<ApiResponse<MailAuthPolicy>>('/api/v1/mail-auth/policy', data)),

  status: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<MailAuthModernStatus>>('/api/v1/mail-auth/status')),

  domainPolicy: (domain: string) =>
    unwrapApiResponse(apiClient.get<ApiResponse<DomainMailAuthPolicy>>(
      `/api/v1/mail-auth/domains/${encodeURIComponent(domain)}/policy`,
    )),

  updateDomainPolicy: (domain: string, data: DomainMailAuthPolicyRequest) =>
    unwrapApiResponse(apiClient.put<ApiResponse<DomainMailAuthPolicy>>(
      `/api/v1/mail-auth/domains/${encodeURIComponent(domain)}/policy`,
      data,
    )),

  dnsRecords: (domain: string) =>
    unwrapApiResponse(apiClient.get<ApiResponse<DnsRecord[]>>(
      `/api/v1/mail-auth/domains/${encodeURIComponent(domain)}/dns-records`,
    )),

  dnsProbe: (domain: string) =>
    unwrapApiResponse(apiClient.post<ApiResponse<MailAuthDnsProbe[]>>(
      `/api/v1/mail-auth/domains/${encodeURIComponent(domain)}/dns-probe`,
    )),

  rotateDkimSelector: (domain: string, data: RotateDkimSelectorRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<DomainMailAuthPolicy>>(
      `/api/v1/mail-auth/domains/${encodeURIComponent(domain)}/dkim/rotate-selector`,
      data,
    )),
};
