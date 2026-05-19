export interface UserFormValues {
  username: string;
  email: string;
  password?: string;
  confirmPassword?: string;
  roles: string[];
  managedDomains: string[];
}

export interface ResetPasswordValues {
  newPassword: string;
  confirmPassword: string;
}
