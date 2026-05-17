import axios from 'axios';

export const AUTH_SESSION_EXPIRED_EVENT = 'sealmail:auth-session-expired';

const apiClient = axios.create({
  baseURL: '',
  timeout: 30000,
});

const clearStoredAuth = () => {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('user');
};

apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      const isLoginRequest = error.config?.url?.includes('/api/v1/auth/login');
      if (isLoginRequest) {
        return Promise.reject(error);
      }
      clearStoredAuth();
      window.dispatchEvent(new Event(AUTH_SESSION_EXPIRED_EVENT));
    }
    return Promise.reject(error);
  },
);

export default apiClient;
