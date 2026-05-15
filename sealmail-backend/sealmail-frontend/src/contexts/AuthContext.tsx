import React, { createContext, useContext, useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import type { UserContext as UserContextType } from '../types';
import { authApi } from '../api/client';
import { message } from 'antd';

interface AuthContextType {
  user: UserContextType | null;
  isAuthenticated: boolean;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider: React.FC<AuthProviderProps> = ({ children }) => {
  const [user, setUser] = useState<UserContextType | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    const userStr = localStorage.getItem('user');
    if (token && userStr) {
      try {
        const userData = JSON.parse(userStr);
        setUser(userData);
      } catch {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('user');
      }
    }
    setLoading(false);
  }, []);

  const login = async (username: string, password: string) => {
    const response = await authApi.login({ username, password });
    const { accessToken, user } = response.data.data as {
      accessToken: string;
      user: UserContextType & { roles?: string[]; managedDomains?: string[] };
    };
    const userData: UserContextType = {
      ...user,
      role: user.roles?.[0] || 'USER',
    };

    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('user', JSON.stringify(userData));
    setUser(userData);
    message.success('登录成功');
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
