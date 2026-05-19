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
  list: async (status: UserStatusFilter = 'active') => {
    const response = await apiClient.get<ApiResponse<UserAccount[]>>('/api/v1/users', { params: { status } });
    return response.data.data;
  },

  create: async (data: CreateUserRequest) => {
    const response = await apiClient.post<ApiResponse<UserAccount>>('/api/v1/users', data);
    return response.data.data;
  },

  update: async (userId: string, data: UpdateUserRequest) => {
    const response = await apiClient.put<ApiResponse<UserAccount>>(`/api/v1/users/${encodeURIComponent(userId)}`, data);
    return response.data.data;
  },

  disable: async (userId: string) => {
    const response = await apiClient.post<ApiResponse<UserAccount>>(`/api/v1/users/${encodeURIComponent(userId)}/disable`);
    return response.data.data;
  },

  enable: async (userId: string) => {
    const response = await apiClient.post<ApiResponse<UserAccount>>(`/api/v1/users/${encodeURIComponent(userId)}/enable`);
    return response.data.data;
  },

  unlock: async (userId: string) => {
    const response = await apiClient.post<ApiResponse<UserAccount>>(`/api/v1/users/${encodeURIComponent(userId)}/unlock`);
    return response.data.data;
  },

  resetPassword: async (userId: string, data: ResetPasswordRequest) => {
    const response = await apiClient.post<ApiResponse<UserAccount>>(
      `/api/v1/users/${encodeURIComponent(userId)}/reset-password`,
      data,
    );
    return response.data.data;
  },
};
