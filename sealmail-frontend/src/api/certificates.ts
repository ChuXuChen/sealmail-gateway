import type {
  ApiResponse,
  Certificate,
  CertificateBinding,
  CertificateBindingPurpose,
  CreateIntermediateCaRequest,
  CreateRootCaRequest,
  GenerateSelfSignedRequest,
  IssueEndEntityRequest,
  PageResponse,
  SignCsrRequest,
} from '../types';
import apiClient from './http';
import { unwrapApiResponse } from './response';

type CertificateImportRequest = {
  pemData: string;
  ownerEmail: string;
  alias?: string;
  trusted?: boolean;
  privateKeyData?: string;
};

export const certificateApi = {
  list: (params: { owner?: string; includeAll?: boolean; page?: number; size?: number }) =>
    unwrapApiResponse(apiClient.get<ApiResponse<PageResponse<Certificate>>>('/api/v1/certificates', { params })),

  getById: (id: string) =>
    unwrapApiResponse(apiClient.get<ApiResponse<Certificate>>(`/api/v1/certificates/${id}`)),

  pem: (id: string) =>
    apiClient.get<string>(`/api/v1/certificates/${encodeURIComponent(id)}/pem`, {
      responseType: 'text',
    }),

  import: (data: CertificateImportRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<Certificate>>('/api/v1/certificates/import', data)),

  trust: (id: string) =>
    unwrapApiResponse(apiClient.post<ApiResponse<Certificate>>(`/api/v1/certificates/${id}/trust`)),

  untrust: (id: string) =>
    unwrapApiResponse(apiClient.post<ApiResponse<Certificate>>(`/api/v1/certificates/${id}/untrust`)),

  revoke: (id: string, reason?: string) =>
    unwrapApiResponse(apiClient.post<ApiResponse<Certificate>>(`/api/v1/certificates/${id}/revoke`, { reason })),

  delete: (id: string) =>
    unwrapApiResponse(apiClient.delete<ApiResponse<void>>(`/api/v1/certificates/${id}`)),

  generateSelfSigned: (data: GenerateSelfSignedRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<Certificate>>('/api/v1/certificates/generate-self-signed', data)),

  issue: (data: IssueEndEntityRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<Certificate>>('/api/v1/certificates/issue', data)),

  signCsr: (data: SignCsrRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<Certificate>>('/api/v1/certificates/sign-csr', data)),
};

export const certificateBindingApi = {
  list: (params?: { domain?: string; owner?: string }) =>
    unwrapApiResponse(apiClient.get<ApiResponse<CertificateBinding[]>>('/api/v1/certificate-bindings', { params })),

  upsert: (data: {
    ownerEmail: string;
    certificateId: string;
    purpose: CertificateBindingPurpose;
    enabled: boolean;
  }) =>
    unwrapApiResponse(apiClient.post<ApiResponse<CertificateBinding>>('/api/v1/certificate-bindings', data)),

  delete: (id: string) =>
    unwrapApiResponse(apiClient.delete<ApiResponse<void>>(`/api/v1/certificate-bindings/${encodeURIComponent(id)}`)),
};

export const caApi = {
  list: () => unwrapApiResponse(apiClient.get<ApiResponse<Certificate[]>>('/api/v1/cas')),
  getById: (id: string) => unwrapApiResponse(apiClient.get<ApiResponse<Certificate>>(`/api/v1/cas/${id}`)),
  pem: (id: string) =>
    apiClient.get<string>(`/api/v1/cas/${encodeURIComponent(id)}/pem`, {
      responseType: 'text',
    }),
  createRoot: (data: CreateRootCaRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<Certificate>>('/api/v1/cas/root', data)),
  createIntermediate: (data: CreateIntermediateCaRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<Certificate>>('/api/v1/cas/intermediate', data)),
  import: (data: CertificateImportRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<Certificate>>('/api/v1/cas/import', data)),
  importCrl: (id: string, data: { crlPem?: string; crlDerBase64?: string }) =>
    unwrapApiResponse(apiClient.post<ApiResponse<Certificate>>(`/api/v1/cas/${encodeURIComponent(id)}/crl`, data)),
  trust: (id: string) =>
    unwrapApiResponse(apiClient.post<ApiResponse<Certificate>>(`/api/v1/cas/${id}/trust`)),
  untrust: (id: string) =>
    unwrapApiResponse(apiClient.post<ApiResponse<Certificate>>(`/api/v1/cas/${id}/untrust`)),
  revoke: (id: string, reason?: string) =>
    unwrapApiResponse(apiClient.post<ApiResponse<Certificate>>(`/api/v1/cas/${id}/revoke`, { reason })),
  delete: (id: string) =>
    unwrapApiResponse(apiClient.delete<ApiResponse<void>>(`/api/v1/cas/${id}`)),
};

export const crlUrls = {
  der: (caId: string) => `/api/v1/crl/${caId}`,
  pem: (caId: string) => `/api/v1/crl/${caId}.pem`,
};
