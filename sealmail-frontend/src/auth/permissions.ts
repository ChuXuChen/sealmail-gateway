import type { UserContext } from '../types';

const hasRole = (user: UserContext | null, role: string): boolean =>
  !!user?.roles?.some((item) => item.toUpperCase() === role.toUpperCase()) ||
  user?.role?.toUpperCase() === role.toUpperCase();

export const getPrimaryRole = (user: UserContext | null): string =>
  user?.roles?.[0] || user?.role || 'USER';

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

export const canViewSystemStatus = (user: UserContext | null): boolean =>
  canManageCa(user) || canViewAuditLogs(user);

export const canManageDomains = (user: UserContext | null): boolean =>
  canManageCa(user);

export const canManageMailAuth = (user: UserContext | null): boolean =>
  canManageDomains(user);

export const getPermissionSummary = (user: UserContext | null): string[] => {
  const summary = [
    canManageCa(user) ? 'PKI 管理' : null,
    canManageCertificates(user) ? '证书管理' : null,
    canManageDlp(user) ? 'DLP 管理' : null,
    canManageDomains(user) ? '域名配置' : null,
    canManageMailAuth(user) ? '邮件认证' : null,
    canViewQuarantine(user) ? '隔离查看' : null,
    canViewAuditLogs(user) ? '审计查看' : null,
    canViewSystemStatus(user) ? '状态查看' : null,
  ].filter(Boolean) as string[];

  return summary.length ? summary : ['基础访问'];
};
