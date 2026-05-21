import type { ApiResponse, AuditLog, MailProcessingRecord, PageResponse } from '../types';
import apiClient from './http';
import { unwrapApiResponse } from './response';

export const mailProcessingApi = {
  listRecent: (params: { page?: number; size?: number }) =>
    unwrapApiResponse(apiClient.get<ApiResponse<PageResponse<MailProcessingRecord>>>(
      '/api/v1/mail-processing',
      { params },
    )),

  getById: (processingId: string) =>
    unwrapApiResponse(apiClient.get<ApiResponse<MailProcessingRecord>>(
      `/api/v1/mail-processing/${encodeURIComponent(processingId)}`,
    )),

  getAuditTrace: (processingId: string, params: { page?: number; size?: number }) =>
    unwrapApiResponse(apiClient.get<ApiResponse<PageResponse<AuditLog>>>(
      `/api/v1/mail-processing/${encodeURIComponent(processingId)}/audit-trace`,
      { params },
    )),
};
