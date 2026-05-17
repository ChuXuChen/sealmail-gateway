import type {
  ApiResponse,
  CreateDomainConfigRequest,
  DomainConfig,
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
