import type {
  ApiResponse,
  ChangePasswordRequest,
  LoginRequest,
  LoginResponse,
  UpdateProfileRequest,
  UserContext,
} from '../types';
import apiClient from './http';

export const authApi = {
  login: (data: LoginRequest) =>
    apiClient.post<ApiResponse<LoginResponse>>('/api/v1/auth/login', data),

  logout: () =>
    apiClient.post<ApiResponse<void>>('/api/v1/auth/logout'),

  me: () =>
    apiClient.get<ApiResponse<UserContext>>('/api/v1/auth/me'),

  updateProfile: (data: UpdateProfileRequest) =>
    apiClient.put<ApiResponse<UserContext>>('/api/v1/auth/profile', data),

  changePassword: (data: ChangePasswordRequest) =>
    apiClient.post<ApiResponse<void>>('/api/v1/auth/change-password', data),
};
