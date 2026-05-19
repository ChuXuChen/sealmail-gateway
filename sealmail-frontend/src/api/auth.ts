import type {
  ApiResponse,
  ChangePasswordRequest,
  LoginRequest,
  LoginResponse,
  UpdateProfileRequest,
  UserContext,
} from '../types';
import apiClient from './http';
import { unwrapApiResponse } from './response';

export const authApi = {
  login: (data: LoginRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<LoginResponse>>('/api/v1/auth/login', data)),

  logout: () =>
    unwrapApiResponse(apiClient.post<ApiResponse<void>>('/api/v1/auth/logout')),

  me: () =>
    unwrapApiResponse(apiClient.get<ApiResponse<UserContext>>('/api/v1/auth/me')),

  updateProfile: (data: UpdateProfileRequest) =>
    unwrapApiResponse(apiClient.put<ApiResponse<UserContext>>('/api/v1/auth/profile', data)),

  changePassword: (data: ChangePasswordRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<void>>('/api/v1/auth/change-password', data)),
};
