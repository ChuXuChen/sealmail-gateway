export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserContext;
}

export interface UserContext {
  userId: string;
  username: string;
  email: string;
  role: string;
  roles?: string[];
  managedDomains?: string[];
  active?: boolean;
  locked?: boolean;
}

export interface UserAccount {
  userId: string;
  username: string;
  email: string;
  roles: string[];
  managedDomains: string[];
  active: boolean;
  locked: boolean;
  failedLoginAttempts: number;
  lastLoginAt?: string;
  lastPasswordChangedAt?: string;
}

export type UserStatusFilter = 'active' | 'disabled' | 'all';

export interface CreateUserRequest {
  username: string;
  email: string;
  password: string;
  roles: string[];
  managedDomains: string[];
}

export interface UpdateUserRequest {
  username: string;
  email: string;
  roles: string[];
  managedDomains: string[];
}

export interface UpdateProfileRequest {
  username: string;
  email: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface ResetPasswordRequest {
  newPassword: string;
}
