import React, { useEffect, useRef, useState, ReactNode } from 'react';
import { UserContext } from '../types';
import { AUTH_SESSION_EXPIRED_EVENT, authApi } from '../api/client';
import { getApiErrorMessage } from '../api/errors';
import { message } from 'antd';
import { AuthContext } from './auth-context';

interface AuthProviderProps {
  children: ReactNode;
}

const clearStoredAuth = () => {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('user');
};

const normalizeUser = (user: UserContext): UserContext => ({
  ...user,
  role: user.roles?.[0] || user.role || 'USER',
});

const storeUser = (user: UserContext) => {
  const userData = normalizeUser(user);
  localStorage.setItem('user', JSON.stringify(userData));
  return userData;
};

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const sessionExpiredNotified = useRef(false);
  const [user, setUser] = useState<UserContext | null>(() => {
    const token = localStorage.getItem('accessToken');
    const userStr = localStorage.getItem('user');
    if (token && userStr) {
      try {
        return JSON.parse(userStr) as UserContext;
      } catch {
        clearStoredAuth();
      }
    }
    return null;
  });
  const [loading] = useState(false);

  useEffect(() => {
    const handleSessionExpired = () => {
      clearStoredAuth();
      setUser(null);
      if (!sessionExpiredNotified.current) {
        sessionExpiredNotified.current = true;
        message.warning('会话已失效，请重新登录');
      }
    };

    window.addEventListener(AUTH_SESSION_EXPIRED_EVENT, handleSessionExpired);
    return () => {
      window.removeEventListener(AUTH_SESSION_EXPIRED_EVENT, handleSessionExpired);
    };
  }, []);

  const login = async (username: string, password: string) => {
    try {
      const response = await authApi.login({ username, password });
      const { accessToken, user } = response.data.data;
      const userData = normalizeUser(user);

      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('user', JSON.stringify(userData));
      sessionExpiredNotified.current = false;
      setUser(userData);
      message.success('登录成功');
    } catch (error) {
      message.error(getApiErrorMessage(error, '登录失败'));
      throw error;
    }
  };

  const logout = async () => {
    try {
      await authApi.logout();
    } catch {
      // ignore logout request failure and clear local state anyway
    }
    clearStoredAuth();
    sessionExpiredNotified.current = false;
    setUser(null);
    message.info('已退出登录');
  };

  const refreshUser = async () => {
    const response = await authApi.me();
    const userData = storeUser(response.data.data);
    setUser(userData);
    return userData;
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        loading,
        login,
        logout,
        refreshUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
