import { AxiosError } from 'axios';

type ApiErrorPayload = {
  message?: unknown;
  errorCode?: unknown;
  fieldErrors?: Record<string, string>;
};

export const getApiErrorMessage = (error: unknown, fallback: string) => {
  if (error instanceof AxiosError) {
    const payload = error.response?.data as ApiErrorPayload | undefined;
    if (typeof payload?.message === 'string' && payload.message.trim()) {
      return payload.message;
    }
    if (payload?.fieldErrors && Object.keys(payload.fieldErrors).length > 0) {
      return Object.values(payload.fieldErrors).join('; ');
    }
    if (error.response?.status === 403) {
      return '没有权限执行此操作';
    }
    if (error.response?.status === 401) {
      return '登录状态已失效，请重新登录';
    }
  }

  if (error instanceof Error && error.message.trim()) {
    return error.message;
  }

  return fallback;
};
