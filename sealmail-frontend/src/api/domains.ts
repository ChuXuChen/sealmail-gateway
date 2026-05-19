import type {
  ApiResponse,
  CreateDomainConfigRequest,
  DomainConfig,
  UpdateDomainConfigRequest,
} from '../types';
import apiClient from './http';
import { unwrapApiResponse } from './response';

export const domainConfigApi = {
  findAll: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<DomainConfig[]>>('/api/v1/domains')),

  findAllActive: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<DomainConfig[]>>('/api/v1/domains/active')),

  findLocalDomains: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<DomainConfig[]>>('/api/v1/domains/local')),

  findRemoteDomains: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<DomainConfig[]>>('/api/v1/domains/remote')),

  findById: (id: string) =>
    unwrapApiResponse(apiClient.get<ApiResponse<DomainConfig>>(`/api/v1/domains/${encodeURIComponent(id)}`)),

  findByDomain: (domain: string) =>
    unwrapApiResponse(apiClient.get<ApiResponse<DomainConfig>>(
      `/api/v1/domains/domain/${encodeURIComponent(domain)}`,
    )),

  create: (data: CreateDomainConfigRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<DomainConfig>>('/api/v1/domains', data)),

  update: (id: string, data: UpdateDomainConfigRequest) =>
    unwrapApiResponse(apiClient.put<ApiResponse<DomainConfig>>(`/api/v1/domains/${encodeURIComponent(id)}`, data)),

  delete: (id: string) =>
    unwrapApiResponse(apiClient.delete<ApiResponse<void>>(`/api/v1/domains/${encodeURIComponent(id)}`)),
};
