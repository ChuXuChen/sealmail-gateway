import type { AxiosResponse } from 'axios';
import type { ApiResponse } from '../types';

export const unwrapApiResponse = async <T>(
  request: Promise<AxiosResponse<ApiResponse<T>>>,
) => {
  const response = await request;
  return response.data.data;
};
