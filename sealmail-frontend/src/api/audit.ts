import type { ApiResponse, AuditLog, PageResponse } from '../types';
import apiClient from './http';

export const auditLogApi = {
  list: (params: { page?: number; size?: number; category?: string; type?: string; success?: boolean }) =>
    apiClient.get<ApiResponse<PageResponse<AuditLog>>>('/api/v1/audit-logs', { params }),

  getByType: (type: string, params: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<AuditLog>>>(`/api/v1/audit-logs/type/${type}`, { params }),

  getByUserId: (userId: string, params: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<AuditLog>>>(`/api/v1/audit-logs/user/${userId}`, { params }),

  getByResource: (resourceType: string, resourceId: string, params: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<AuditLog>>>(
      `/api/v1/audit-logs/resource/${encodeURIComponent(resourceType)}/${encodeURIComponent(resourceId)}`,
      { params },
    ),

  getByProcessingId: (processingId: string, params: { page?: number; size?: number }) =>
    auditLogApi.getByResource('MAIL_PROCESSING', processingId, params),

  getByTimeRange: (startTime: string, endTime: string, params: { page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<AuditLog>>>('/api/v1/audit-logs/time-range', {
      params: { startTime, endTime, ...params },
    }),
};
