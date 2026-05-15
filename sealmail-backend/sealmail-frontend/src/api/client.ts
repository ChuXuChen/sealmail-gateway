import axios from 'axios';
import type {
  ApiResponse,
  LoginRequest,
  LoginResponse,
  Certificate,
  QuarantineItem,
  QuarantineStats,
  PageResponse,
} from '../types';

const API_BASE_URL = 'http://localhost:8080';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
});

// Request interceptor: Add auth token
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor: Handle common errors
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const isLoginRequest = error.config?.url?.includes('/api/v1/auth/login');
      if (isLoginRequest) {
        return Promise.reject(error);
      }
      localStorage.removeItem('accessToken');
      localStorage.removeItem('user');
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

// Auth API
export const authApi = {
  login: (data: LoginRequest) =>
    apiClient.post<ApiResponse<LoginResponse>>('/api/v1/auth/login', data),

  logout: () =>
    apiClient.post<ApiResponse<void>>('/api/v1/auth/logout'),
};

// Certificate API
export const certificateApi = {
  list: (params: { owner?: string; page?: number; size?: number }) =>
    apiClient.get<ApiResponse<PageResponse<Certificate>>>('/api/v1/certificates', { params }),

  getById: (id: string) =>
    apiClient.get<ApiResponse<Certificate>>(`/api/v1/certificates/${id}`),

  import: (data: { pemData: string; ownerEmail: string; alias?: string; trusted?: boolean }) =>
    apiClient.post<ApiResponse<Certificate>>('/api/v1/certificates/import', data),

  trust: (id: string) =>
    apiClient.post<ApiResponse<Certificate>>(`/api/v1/certificates/${id}/trust`),

  revoke: (id: string, reason?: string) =>
    apiClient.post<ApiResponse<Certificate>>(`/api/v1/certificates/${id}/revoke`, { reason }),

  delete: (id: string) =>
    apiClient.delete<ApiResponse<void>>(`/api/v1/certificates/${id}`),
};

// Quarantine API
export const quarantineApi = {
  getStats: () =>
    apiClient.get<ApiResponse<QuarantineStats>>('/api/v1/quarantine/stats'),

  list: (params: { page?: number; size?: number; reason?: string }) =>
    apiClient.get<ApiResponse<PageResponse<QuarantineItem>>>('/api/v1/quarantine', { params }),

  getById: (id: string) =>
    apiClient.get<ApiResponse<QuarantineItem>>(`/api/v1/quarantine/${id}`),

  release: (id: string, data?: { releasedBy?: string; comment?: string }) =>
    apiClient.post<ApiResponse<QuarantineItem>>(`/api/v1/quarantine/${id}/release`, data),

  reject: (id: string, data?: { rejectedBy?: string; comment?: string }) =>
    apiClient.post<ApiResponse<QuarantineItem>>(`/api/v1/quarantine/${id}/reject`, data),

  batchRelease: (ids: string[]) =>
    apiClient.post<ApiResponse<void>>('/api/v1/quarantine/batch-release', ids),

  batchReject: (ids: string[]) =>
    apiClient.post<ApiResponse<void>>('/api/v1/quarantine/batch-reject', ids),
};

export default apiClient;
