import type { UserContext } from '../types';

const hasRole = (user: UserContext | null, role: string): boolean =>
  !!user?.roles?.some((item) => item.toUpperCase() === role.toUpperCase()) ||
  user?.role?.toUpperCase() === role.toUpperCase();

export const canManageCa = (user: UserContext | null): boolean =>
  hasRole(user, 'SUPER_ADMIN') || hasRole(user, 'PKI_ADMIN') || hasRole(user, 'ADMIN');

export const canManageCertificates = (user: UserContext | null): boolean =>
  canManageCa(user) || hasRole(user, 'DOMAIN_ADMIN') || hasRole(user, 'DOMAIN_MANAGER');

export const canReviewCsr = (user: UserContext | null): boolean =>
  canManageCa(user);

export const canViewCrl = (user: UserContext | null): boolean =>
  canManageCa(user) || hasRole(user, 'AUDITOR');

export const canViewQuarantine = (user: UserContext | null): boolean =>
  canManageCa(user) || hasRole(user, 'AUDITOR');

export const canManageDlp = (user: UserContext | null): boolean =>
  canManageCa(user);

export const canViewAuditLogs = (user: UserContext | null): boolean =>
  canManageCa(user) || hasRole(user, 'AUDITOR');

export const canManageDomains = (user: UserContext | null): boolean =>
  canManageCa(user);
