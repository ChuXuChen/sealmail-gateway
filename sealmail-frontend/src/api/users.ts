import type {
  ApiResponse,
  CreateUserRequest,
  ResetPasswordRequest,
  UpdateUserRequest,
  UserAccount,
  UserStatusFilter,
} from '../types';
import apiClient from './http';

export const userApi = {
  list: (status: UserStatusFilter = 'active') =>
    apiClient.get<ApiResponse<UserAccount[]>>('/api/v1/users', { params: { status } }),

  create: (data: CreateUserRequest) =>
    apiClient.post<ApiResponse<UserAccount>>('/api/v1/users', data),

  update: (userId: string, data: UpdateUserRequest) =>
    apiClient.put<ApiResponse<UserAccount>>(`/api/v1/users/${encodeURIComponent(userId)}`, data),

  disable: (userId: string) =>
    apiClient.post<ApiResponse<UserAccount>>(`/api/v1/users/${encodeURIComponent(userId)}/disable`),

  enable: (userId: string) =>
    apiClient.post<ApiResponse<UserAccount>>(`/api/v1/users/${encodeURIComponent(userId)}/enable`),

  unlock: (userId: string) =>
    apiClient.post<ApiResponse<UserAccount>>(`/api/v1/users/${encodeURIComponent(userId)}/unlock`),

  resetPassword: (userId: string, data: ResetPasswordRequest) =>
    apiClient.post<ApiResponse<UserAccount>>(`/api/v1/users/${encodeURIComponent(userId)}/reset-password`, data),
};
