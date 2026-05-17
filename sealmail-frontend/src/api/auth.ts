import type { ApiResponse, LoginRequest, LoginResponse } from '../types';
import apiClient from './http';

export const authApi = {
  login: (data: LoginRequest) =>
    apiClient.post<ApiResponse<LoginResponse>>('/api/v1/auth/login', data),

  logout: () =>
    apiClient.post<ApiResponse<void>>('/api/v1/auth/logout'),
};
