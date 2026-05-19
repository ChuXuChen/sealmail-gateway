import type { AxiosResponse } from 'axios';
import type { ApiResponse } from '../types';

export const unwrapApiResponse = async <T>(
  request: Promise<AxiosResponse<ApiResponse<T>>>,
) => {
  const response = await request;
  if (!response.data.success) {
    throw new Error(response.data.message || String(response.data.data ?? 'Request failed'));
  }
  return response.data.data;
};
