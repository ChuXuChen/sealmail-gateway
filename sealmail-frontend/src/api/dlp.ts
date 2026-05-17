import type {
  ApiResponse,
  CreateDlpPatternRequest,
  CreateDlpSelectionRequest,
  DlpPattern,
  DlpSelection,
  ExceptionMailItem,
  ExceptionMailStats,
  PageResponse,
  QuarantineItem,
  QuarantineStats,
  UpdateDlpPatternRequest,
  UpdateDlpSelectionRequest,
} from '../types';
import apiClient from './http';

export const exceptionMailApi = {
  getStats: () =>
    apiClient.get<ApiResponse<ExceptionMailStats>>('/api/v1/exception-mails/stats'),

  list: (params: { page?: number; size?: number; reason?: string }) =>
    apiClient.get<ApiResponse<PageResponse<ExceptionMailItem>>>('/api/v1/exception-mails', { params }),

  getById: (id: string) =>
    apiClient.get<ApiResponse<ExceptionMailItem>>(`/api/v1/exception-mails/${id}`),
};

export const dlpQuarantineApi = {
  getStats: () =>
    apiClient.get<ApiResponse<QuarantineStats>>('/api/v1/dlp/quarantine/stats'),

  list: (params: { page?: number; size?: number; reason?: string }) =>
    apiClient.get<ApiResponse<PageResponse<QuarantineItem>>>('/api/v1/dlp/quarantine', { params }),

  getById: (id: string) =>
    apiClient.get<ApiResponse<QuarantineItem>>(`/api/v1/dlp/quarantine/${id}`),

  release: (id: string, data?: { releasedBy?: string; comment?: string; encryptBeforeRelease?: boolean }) =>
    apiClient.post<ApiResponse<QuarantineItem>>(`/api/v1/dlp/quarantine/${id}/release`, data),

  completeRelease: (id: string, data?: { operator?: string; comment?: string }) =>
    apiClient.post<ApiResponse<QuarantineItem>>(`/api/v1/dlp/quarantine/${id}/release/complete`, data),

  restoreRelease: (id: string, data?: { operator?: string; comment?: string }) =>
    apiClient.post<ApiResponse<QuarantineItem>>(`/api/v1/dlp/quarantine/${id}/release/restore`, data),

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

  createPattern: (data: CreateDlpPatternRequest) =>
    apiClient.post<ApiResponse<DlpPattern>>('/api/v1/dlp/patterns', data),

  updatePattern: (id: string, data: UpdateDlpPatternRequest) =>
    apiClient.put<ApiResponse<DlpPattern>>(`/api/v1/dlp/patterns/${id}`, data),

  deletePattern: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/api/v1/dlp/patterns/${id}`),

  listSelections: () =>
    apiClient.get<ApiResponse<DlpSelection[]>>('/api/v1/dlp/selections'),

  createSelection: (data: CreateDlpSelectionRequest) =>
    apiClient.post<ApiResponse<DlpSelection>>('/api/v1/dlp/selections', data),

  updateSelection: (id: string, data: UpdateDlpSelectionRequest) =>
    apiClient.put<ApiResponse<DlpSelection>>(`/api/v1/dlp/selections/${id}`, data),

  deleteSelection: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/api/v1/dlp/selections/${id}`),
};
