import type { UserStatusFilter } from '../../types';

export const roleOptions = [
  { value: 'USER', label: 'USER' },
  { value: 'AUDITOR', label: 'AUDITOR' },
  { value: 'DOMAIN_MANAGER', label: 'DOMAIN_MANAGER' },
  { value: 'DOMAIN_ADMIN', label: 'DOMAIN_ADMIN' },
  { value: 'ADMIN', label: 'ADMIN' },
  { value: 'PKI_ADMIN', label: 'PKI_ADMIN' },
  { value: 'SUPER_ADMIN', label: 'SUPER_ADMIN' },
];

export const statusTabs: Array<{ key: UserStatusFilter; label: string }> = [
  { key: 'active', label: '正常用户' },
  { key: 'disabled', label: '停用用户' },
];

export const normalizeText = (value?: string) => value?.trim() || '';

export const normalizeList = (values?: string[]) => (values || [])
  .map((value) => value.trim())
  .filter(Boolean);

export const hasRole = (roles: string[] | undefined, role: string) =>
  !!roles?.some((item) => item.toUpperCase() === role.toUpperCase());
