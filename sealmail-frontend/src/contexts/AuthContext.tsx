import React, { useState, ReactNode } from 'react';
import { UserContext } from '../types';
import { authApi } from '../api/client';
import { getApiErrorMessage } from '../api/errors';
import { message } from 'antd';
import { AuthContext } from './auth-context';

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [user, setUser] = useState<UserContext | null>(() => {
    const token = localStorage.getItem('accessToken');
    const userStr = localStorage.getItem('user');
    if (token && userStr) {
      try {
        return JSON.parse(userStr) as UserContext;
      } catch {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('user');
      }
    }
    return null;
  });
  const [loading] = useState(false);

  const login = async (username: string, password: string) => {
    try {
      const response = await authApi.login({ username, password });
      const { accessToken, user } = response.data.data;
      const userData: UserContext = {
        ...user,
        role: user.roles?.[0] || 'USER',
      };

      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('user', JSON.stringify(userData));
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
    localStorage.removeItem('accessToken');
    localStorage.removeItem('user');
    setUser(null);
    message.info('已退出登录');
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        loading,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
