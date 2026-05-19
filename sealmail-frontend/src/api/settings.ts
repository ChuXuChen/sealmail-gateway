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
import { unwrapApiResponse } from './response';

export const systemSettingsApi = {
  get: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<SystemSettings>>('/api/v1/system-settings')),
};

export const runtimePolicyApi = {
  getRelay: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<RelayPolicy>>('/api/v1/runtime-policies/relay')),

  updateRelay: (data: RelayPolicyRequest) =>
    unwrapApiResponse(apiClient.put<ApiResponse<RelayPolicy>>('/api/v1/runtime-policies/relay', data)),

  getQuarantine: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<QuarantinePolicy>>('/api/v1/runtime-policies/quarantine')),

  updateQuarantine: (data: QuarantinePolicyRequest) =>
    unwrapApiResponse(apiClient.put<ApiResponse<QuarantinePolicy>>('/api/v1/runtime-policies/quarantine', data)),

  getGmEdge: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<GmEdgePolicy>>('/api/v1/runtime-policies/gm-edge')),

  updateGmEdge: (data: GmEdgePolicyRequest) =>
    unwrapApiResponse(apiClient.put<ApiResponse<GmEdgePolicy>>('/api/v1/runtime-policies/gm-edge', data)),

  getSmimeSuite: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<SmimeSuitePolicy>>('/api/v1/runtime-policies/smime-suite')),

  updateSmimeSuite: (data: SmimeSuitePolicyRequest) =>
    unwrapApiResponse(apiClient.put<ApiResponse<SmimeSuitePolicy>>('/api/v1/runtime-policies/smime-suite', data)),
};

export const mailTestApi = {
  send: (data: SendTestMailRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<string>>('/api/v1/mail-test/send', data)),

  sendEncrypted: (data: SendTestMailRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<string>>('/api/v1/mail-test/send-encrypted', data)),

  probeRoute: (data: SendTestMailRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<string>>('/api/v1/mail-test/probe-route', data)),

  testSmtpConfig: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<string>>('/api/v1/mail-test/test-smtp-config')),
};
