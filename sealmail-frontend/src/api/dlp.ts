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

  evidence: (id: string) =>
    apiClient.get<ApiResponse<DlpEvidence[]>>(`/api/v1/dlp/quarantine/${id}/evidence`),

  falsePositive: (id: string, data?: { comment?: string }) =>
    apiClient.post<ApiResponse<void>>(`/api/v1/dlp/quarantine/${id}/false-positive`, data),
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

  listRules: () =>
    apiClient.get<ApiResponse<DlpRule[]>>('/api/v1/dlp/rules'),

  createRule: (data: DlpRuleRequest) =>
    apiClient.post<ApiResponse<DlpRule>>('/api/v1/dlp/rules', data),

  updateRule: (id: string, data: Partial<DlpRuleRequest>) =>
    apiClient.put<ApiResponse<DlpRule>>(`/api/v1/dlp/rules/${id}`, data),

  deleteRule: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/api/v1/dlp/rules/${id}`),

  listRuleGroups: () =>
    apiClient.get<ApiResponse<DlpRuleGroup[]>>('/api/v1/dlp/rule-groups'),

  createRuleGroup: (data: DlpRuleGroupRequest) =>
    apiClient.post<ApiResponse<DlpRuleGroup>>('/api/v1/dlp/rule-groups', data),

  updateRuleGroup: (id: string, data: Partial<DlpRuleGroupRequest>) =>
    apiClient.put<ApiResponse<DlpRuleGroup>>(`/api/v1/dlp/rule-groups/${id}`, data),

  deleteRuleGroup: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/api/v1/dlp/rule-groups/${id}`),

  listPolicies: () =>
    apiClient.get<ApiResponse<DlpPolicy[]>>('/api/v1/dlp/policies'),

  createPolicy: (data: DlpPolicyRequest) =>
    apiClient.post<ApiResponse<DlpPolicy>>('/api/v1/dlp/policies', data),

  updatePolicy: (id: string, data: Partial<DlpPolicyRequest>) =>
    apiClient.put<ApiResponse<DlpPolicy>>(`/api/v1/dlp/policies/${id}`, data),

  deletePolicy: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/api/v1/dlp/policies/${id}`),

  test: (data: DlpTestRequest) =>
    apiClient.post<ApiResponse<DlpEvaluation>>('/api/v1/dlp/test', data),

  simulatePolicy: (id: string, data: DlpTestRequest) =>
    apiClient.post<ApiResponse<DlpEvaluation>>(`/api/v1/dlp/policies/${id}/simulate`, data),

  listEvents: (params: { page?: number; size?: number; action?: string; minSeverity?: number; rule?: string; domain?: string }) =>
    apiClient.get<ApiResponse<PageResponse<DlpEvent>>>('/api/v1/dlp/events', { params }),

  eventEvidence: (id: string) =>
    apiClient.get<ApiResponse<DlpEvidence[]>>(`/api/v1/dlp/events/${id}/evidence`),

  listEdmDatasets: () =>
    apiClient.get<ApiResponse<DlpEdmDataset[]>>('/api/v1/dlp/edm-datasets'),

  createEdmDataset: (data: DlpDatasetRequest) =>
    apiClient.post<ApiResponse<DlpEdmDataset>>('/api/v1/dlp/edm-datasets', data),

  updateEdmDataset: (id: string, data: DlpDatasetRequest) =>
    apiClient.put<ApiResponse<DlpEdmDataset>>(`/api/v1/dlp/edm-datasets/${id}`, data),

  importEdmDataset: (id: string, data: DlpImportValuesRequest) =>
    apiClient.post<ApiResponse<DlpImportResult>>(`/api/v1/dlp/edm-datasets/${id}/import`, data),

  deleteEdmDataset: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/api/v1/dlp/edm-datasets/${id}`),

  listFingerprintLibraries: () =>
    apiClient.get<ApiResponse<DlpFingerprintLibrary[]>>('/api/v1/dlp/fingerprint-libraries'),

  createFingerprintLibrary: (data: DlpDatasetRequest) =>
    apiClient.post<ApiResponse<DlpFingerprintLibrary>>('/api/v1/dlp/fingerprint-libraries', data),

  updateFingerprintLibrary: (id: string, data: DlpDatasetRequest) =>
    apiClient.put<ApiResponse<DlpFingerprintLibrary>>(`/api/v1/dlp/fingerprint-libraries/${id}`, data),

  importFingerprintDocument: (id: string, data: DlpFingerprintImportRequest) =>
    apiClient.post<ApiResponse<DlpImportResult>>(`/api/v1/dlp/fingerprint-libraries/${id}/import`, data),

  deleteFingerprintLibrary: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/api/v1/dlp/fingerprint-libraries/${id}`),

  listUbaSenderRisks: (params?: { limit?: number }) =>
    apiClient.get<ApiResponse<DlpUbaSenderRisk[]>>('/api/v1/dlp/uba/senders', { params }),
};
