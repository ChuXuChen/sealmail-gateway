import { createContext } from 'react';
import { UserContext } from '../types';

export interface AuthContextType {
  user: UserContext | null;
  isAuthenticated: boolean;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);
