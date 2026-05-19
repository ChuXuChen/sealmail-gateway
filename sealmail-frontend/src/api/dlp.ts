import type {
  ApiResponse,
  CreateDlpPatternRequest,
  CreateDlpSelectionRequest,
  DlpDatasetRequest,
  DlpEdmDataset,
  DlpEvaluation,
  DlpEvent,
  DlpEvidence,
  DlpFingerprintImportRequest,
  DlpFingerprintLibrary,
  DlpImportResult,
  DlpImportValuesRequest,
  DlpPattern,
  DlpPolicy,
  DlpPolicyRequest,
  DlpRule,
  DlpRuleGroup,
  DlpRuleGroupRequest,
  DlpRuleRequest,
  DlpSelection,
  DlpTestRequest,
  DlpUbaSenderRisk,
  ExceptionMailItem,
  ExceptionMailStats,
  PageResponse,
  QuarantineItem,
  QuarantineStats,
  UpdateDlpPatternRequest,
  UpdateDlpSelectionRequest,
} from '../types';
import apiClient from './http';
import { unwrapApiResponse } from './response';

export const exceptionMailApi = {
  getStats: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<ExceptionMailStats>>('/api/v1/exception-mails/stats')),

  list: (params: { page?: number; size?: number; reason?: string }) =>
    unwrapApiResponse(apiClient.get<ApiResponse<PageResponse<ExceptionMailItem>>>(
      '/api/v1/exception-mails',
      { params },
    )),

  getById: (id: string) =>
    unwrapApiResponse(apiClient.get<ApiResponse<ExceptionMailItem>>(`/api/v1/exception-mails/${id}`)),
};

export const dlpQuarantineApi = {
  getStats: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<QuarantineStats>>('/api/v1/dlp/quarantine/stats')),

  list: (params: { page?: number; size?: number; reason?: string }) =>
    unwrapApiResponse(apiClient.get<ApiResponse<PageResponse<QuarantineItem>>>(
      '/api/v1/dlp/quarantine',
      { params },
    )),

  getById: (id: string) =>
    unwrapApiResponse(apiClient.get<ApiResponse<QuarantineItem>>(`/api/v1/dlp/quarantine/${id}`)),

  release: (id: string, data?: { releasedBy?: string; comment?: string; encryptBeforeRelease?: boolean }) =>
    unwrapApiResponse(apiClient.post<ApiResponse<QuarantineItem>>(`/api/v1/dlp/quarantine/${id}/release`, data)),

  completeRelease: (id: string, data?: { operator?: string; comment?: string }) =>
    unwrapApiResponse(apiClient.post<ApiResponse<QuarantineItem>>(
      `/api/v1/dlp/quarantine/${id}/release/complete`,
      data,
    )),

  restoreRelease: (id: string, data?: { operator?: string; comment?: string }) =>
    unwrapApiResponse(apiClient.post<ApiResponse<QuarantineItem>>(
      `/api/v1/dlp/quarantine/${id}/release/restore`,
      data,
    )),

  reject: (id: string, data?: { rejectedBy?: string; comment?: string }) =>
    unwrapApiResponse(apiClient.post<ApiResponse<QuarantineItem>>(`/api/v1/dlp/quarantine/${id}/reject`, data)),

  batchRelease: (ids: string[]) =>
    unwrapApiResponse(apiClient.post<ApiResponse<void>>('/api/v1/dlp/quarantine/batch-release', ids)),

  batchReject: (ids: string[]) =>
    unwrapApiResponse(apiClient.post<ApiResponse<void>>('/api/v1/dlp/quarantine/batch-reject', ids)),

  evidence: (id: string) =>
    unwrapApiResponse(apiClient.get<ApiResponse<DlpEvidence[]>>(`/api/v1/dlp/quarantine/${id}/evidence`)),

  falsePositive: (id: string, data?: { comment?: string }) =>
    unwrapApiResponse(apiClient.post<ApiResponse<void>>(`/api/v1/dlp/quarantine/${id}/false-positive`, data)),
};

export const dlpApi = {
  listPatterns: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<DlpPattern[]>>('/api/v1/dlp/patterns')),

  createPattern: (data: CreateDlpPatternRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<DlpPattern>>('/api/v1/dlp/patterns', data)),

  updatePattern: (id: string, data: UpdateDlpPatternRequest) =>
    unwrapApiResponse(apiClient.put<ApiResponse<DlpPattern>>(`/api/v1/dlp/patterns/${id}`, data)),

  deletePattern: (id: string) =>
    unwrapApiResponse(apiClient.delete<ApiResponse<void>>(`/api/v1/dlp/patterns/${id}`)),

  listSelections: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<DlpSelection[]>>('/api/v1/dlp/selections')),

  createSelection: (data: CreateDlpSelectionRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<DlpSelection>>('/api/v1/dlp/selections', data)),

  updateSelection: (id: string, data: UpdateDlpSelectionRequest) =>
    unwrapApiResponse(apiClient.put<ApiResponse<DlpSelection>>(`/api/v1/dlp/selections/${id}`, data)),

  deleteSelection: (id: string) =>
    unwrapApiResponse(apiClient.delete<ApiResponse<void>>(`/api/v1/dlp/selections/${id}`)),

  listRules: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<DlpRule[]>>('/api/v1/dlp/rules')),

  createRule: (data: DlpRuleRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<DlpRule>>('/api/v1/dlp/rules', data)),

  updateRule: (id: string, data: Partial<DlpRuleRequest>) =>
    unwrapApiResponse(apiClient.put<ApiResponse<DlpRule>>(`/api/v1/dlp/rules/${id}`, data)),

  deleteRule: (id: string) =>
    unwrapApiResponse(apiClient.delete<ApiResponse<void>>(`/api/v1/dlp/rules/${id}`)),

  listRuleGroups: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<DlpRuleGroup[]>>('/api/v1/dlp/rule-groups')),

  createRuleGroup: (data: DlpRuleGroupRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<DlpRuleGroup>>('/api/v1/dlp/rule-groups', data)),

  updateRuleGroup: (id: string, data: Partial<DlpRuleGroupRequest>) =>
    unwrapApiResponse(apiClient.put<ApiResponse<DlpRuleGroup>>(`/api/v1/dlp/rule-groups/${id}`, data)),

  deleteRuleGroup: (id: string) =>
    unwrapApiResponse(apiClient.delete<ApiResponse<void>>(`/api/v1/dlp/rule-groups/${id}`)),

  listPolicies: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<DlpPolicy[]>>('/api/v1/dlp/policies')),

  createPolicy: (data: DlpPolicyRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<DlpPolicy>>('/api/v1/dlp/policies', data)),

  updatePolicy: (id: string, data: Partial<DlpPolicyRequest>) =>
    unwrapApiResponse(apiClient.put<ApiResponse<DlpPolicy>>(`/api/v1/dlp/policies/${id}`, data)),

  deletePolicy: (id: string) =>
    unwrapApiResponse(apiClient.delete<ApiResponse<void>>(`/api/v1/dlp/policies/${id}`)),

  test: (data: DlpTestRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<DlpEvaluation>>('/api/v1/dlp/test', data)),

  simulatePolicy: (id: string, data: DlpTestRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<DlpEvaluation>>(`/api/v1/dlp/policies/${id}/simulate`, data)),

  listEvents: (params: { page?: number; size?: number; action?: string; minSeverity?: number; rule?: string; domain?: string }) =>
    unwrapApiResponse(apiClient.get<ApiResponse<PageResponse<DlpEvent>>>('/api/v1/dlp/events', { params })),

  eventEvidence: (id: string) =>
    unwrapApiResponse(apiClient.get<ApiResponse<DlpEvidence[]>>(`/api/v1/dlp/events/${id}/evidence`)),

  listEdmDatasets: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<DlpEdmDataset[]>>('/api/v1/dlp/edm-datasets')),

  createEdmDataset: (data: DlpDatasetRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<DlpEdmDataset>>('/api/v1/dlp/edm-datasets', data)),

  updateEdmDataset: (id: string, data: DlpDatasetRequest) =>
    unwrapApiResponse(apiClient.put<ApiResponse<DlpEdmDataset>>(`/api/v1/dlp/edm-datasets/${id}`, data)),

  importEdmDataset: (id: string, data: DlpImportValuesRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<DlpImportResult>>(`/api/v1/dlp/edm-datasets/${id}/import`, data)),

  deleteEdmDataset: (id: string) =>
    unwrapApiResponse(apiClient.delete<ApiResponse<void>>(`/api/v1/dlp/edm-datasets/${id}`)),

  listFingerprintLibraries: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<DlpFingerprintLibrary[]>>('/api/v1/dlp/fingerprint-libraries')),

  createFingerprintLibrary: (data: DlpDatasetRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<DlpFingerprintLibrary>>('/api/v1/dlp/fingerprint-libraries', data)),

  updateFingerprintLibrary: (id: string, data: DlpDatasetRequest) =>
    unwrapApiResponse(apiClient.put<ApiResponse<DlpFingerprintLibrary>>(`/api/v1/dlp/fingerprint-libraries/${id}`, data)),

  importFingerprintDocument: (id: string, data: DlpFingerprintImportRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<DlpImportResult>>(`/api/v1/dlp/fingerprint-libraries/${id}/import`, data)),

  deleteFingerprintLibrary: (id: string) =>
    unwrapApiResponse(apiClient.delete<ApiResponse<void>>(`/api/v1/dlp/fingerprint-libraries/${id}`)),

  listUbaSenderRisks: (params?: { limit?: number }) =>
    unwrapApiResponse(apiClient.get<ApiResponse<DlpUbaSenderRisk[]>>('/api/v1/dlp/uba/senders', { params })),
};
