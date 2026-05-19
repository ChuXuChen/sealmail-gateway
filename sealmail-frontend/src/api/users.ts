import type {
  ApiResponse,
  CreateUserRequest,
  ResetPasswordRequest,
  UpdateUserRequest,
  UserAccount,
  UserStatusFilter,
} from '../types';
import apiClient from './http';
import { unwrapApiResponse } from './response';

export const userApi = {
  list: (status: UserStatusFilter = 'active') =>
    unwrapApiResponse(apiClient.get<ApiResponse<UserAccount[]>>('/api/v1/users', { params: { status } })),

  create: (data: CreateUserRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<UserAccount>>('/api/v1/users', data)),

  update: (userId: string, data: UpdateUserRequest) =>
    unwrapApiResponse(apiClient.put<ApiResponse<UserAccount>>(`/api/v1/users/${encodeURIComponent(userId)}`, data)),

  disable: (userId: string) =>
    unwrapApiResponse(apiClient.post<ApiResponse<UserAccount>>(`/api/v1/users/${encodeURIComponent(userId)}/disable`)),

  enable: (userId: string) =>
    unwrapApiResponse(apiClient.post<ApiResponse<UserAccount>>(`/api/v1/users/${encodeURIComponent(userId)}/enable`)),

  unlock: (userId: string) =>
    unwrapApiResponse(apiClient.post<ApiResponse<UserAccount>>(`/api/v1/users/${encodeURIComponent(userId)}/unlock`)),

  resetPassword: (userId: string, data: ResetPasswordRequest) =>
    unwrapApiResponse(apiClient.post<ApiResponse<UserAccount>>(
      `/api/v1/users/${encodeURIComponent(userId)}/reset-password`,
      data,
    )),
};
