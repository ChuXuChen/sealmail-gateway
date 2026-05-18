import type {
  ApiResponse,
  GmEdgePolicy,
  GmEdgePolicyRequest,
  QuarantinePolicy,
  QuarantinePolicyRequest,
  RelayPolicy,
  RelayPolicyRequest,
  SendTestMailRequest,
  SmimeSuitePolicy,
  SmimeSuitePolicyRequest,
  SystemSettings,
} from '../types';
import apiClient from './http';

export const systemSettingsApi = {
  get: () =>
    apiClient.get<ApiResponse<SystemSettings>>('/api/v1/system-settings'),
};

export const runtimePolicyApi = {
  getRelay: () =>
    apiClient.get<ApiResponse<RelayPolicy>>('/api/v1/runtime-policies/relay'),

  updateRelay: (data: RelayPolicyRequest) =>
    apiClient.put<ApiResponse<RelayPolicy>>('/api/v1/runtime-policies/relay', data),

  getQuarantine: () =>
    apiClient.get<ApiResponse<QuarantinePolicy>>('/api/v1/runtime-policies/quarantine'),

  updateQuarantine: (data: QuarantinePolicyRequest) =>
    apiClient.put<ApiResponse<QuarantinePolicy>>('/api/v1/runtime-policies/quarantine', data),

  getGmEdge: () =>
    apiClient.get<ApiResponse<GmEdgePolicy>>('/api/v1/runtime-policies/gm-edge'),

  updateGmEdge: (data: GmEdgePolicyRequest) =>
    apiClient.put<ApiResponse<GmEdgePolicy>>('/api/v1/runtime-policies/gm-edge', data),

  getSmimeSuite: () =>
    apiClient.get<ApiResponse<SmimeSuitePolicy>>('/api/v1/runtime-policies/smime-suite'),

  updateSmimeSuite: (data: SmimeSuitePolicyRequest) =>
    apiClient.put<ApiResponse<SmimeSuitePolicy>>('/api/v1/runtime-policies/smime-suite', data),
};

export const mailTestApi = {
  send: (data: SendTestMailRequest) =>
    apiClient.post<ApiResponse<string>>('/api/v1/mail-test/send', data),

  sendEncrypted: (data: SendTestMailRequest) =>
    apiClient.post<ApiResponse<string>>('/api/v1/mail-test/send-encrypted', data),

  probeRoute: (data: SendTestMailRequest) =>
    apiClient.post<ApiResponse<string>>('/api/v1/mail-test/probe-route', data),

  testSmtpConfig: () =>
    apiClient.get<ApiResponse<string>>('/api/v1/mail-test/test-smtp-config'),
};
