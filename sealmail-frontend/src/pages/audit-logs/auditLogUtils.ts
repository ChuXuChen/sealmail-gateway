import type { AuditLog } from '../../types';

export type CategoryKey = 'ALL' | 'AUTH' | 'USER' | 'CERTIFICATE' | 'EMAIL' | 'SYSTEM';
export type StatusFilter = 'ALL' | 'SUCCESS' | 'FAILED';

export const categories: { value: CategoryKey; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'AUTH', label: '登录与安全' },
  { value: 'USER', label: '用户' },
  { value: 'CERTIFICATE', label: '证书' },
  { value: 'EMAIL', label: '邮件' },
  { value: 'SYSTEM', label: '系统' },
];

export const eventGroups = [
  {
    label: '登录与安全',
    category: 'AUTH',
    options: [
      { value: 'USER_LOGIN', label: '登录成功' },
      { value: 'USER_LOGIN_FAILED', label: '登录失败' },
      { value: 'USER_LOGOUT', label: '退出登录' },
      { value: 'USER_PASSWORD_CHANGED', label: '修改密码' },
    ],
  },
  {
    label: '用户',
    category: 'USER',
    options: [
      { value: 'USER_CREATED', label: '创建用户' },
      { value: 'USER_UPDATED', label: '更新用户' },
      { value: 'USER_DELETED', label: '删除用户' },
      { value: 'USER_UNLOCKED', label: '解锁用户' },
      { value: 'USER_ROLE_CHANGED', label: '调整角色' },
    ],
  },
  {
    label: '证书',
    category: 'CERTIFICATE',
    options: [
      { value: 'CERTIFICATE_ISSUED', label: '签发证书' },
      { value: 'CERTIFICATE_IMPORTED', label: '导入证书' },
      { value: 'CERTIFICATE_TRUSTED', label: '信任证书' },
      { value: 'CERTIFICATE_UNTRUSTED', label: '撤销信任' },
      { value: 'CERTIFICATE_REVOKED', label: '吊销证书' },
      { value: 'CERTIFICATE_DELETED', label: '删除证书' },
    ],
  },
  {
    label: '邮件',
    category: 'EMAIL',
    options: [
      { value: 'EMAIL_RECEIVED', label: '接收邮件' },
      { value: 'EMAIL_DELIVERED', label: '投递邮件' },
      { value: 'EMAIL_QUARANTINED', label: 'DLP 隔离' },
      { value: 'EMAIL_RELEASED', label: '放行邮件' },
      { value: 'EMAIL_REJECTED', label: '拒收邮件' },
      { value: 'EMAIL_ENCRYPTED', label: '加密邮件' },
      { value: 'EMAIL_DECRYPTED', label: '解密邮件' },
      { value: 'EMAIL_SIGNED', label: '签名邮件' },
      { value: 'EMAIL_VERIFIED', label: '验签邮件' },
      { value: 'DLP_VIOLATION', label: 'DLP 命中' },
    ],
  },
  {
    label: '系统',
    category: 'SYSTEM',
    options: [
      { value: 'SYSTEM_CONFIG_CHANGED', label: '修改配置' },
      { value: 'SYSTEM_STARTUP', label: '系统启动' },
      { value: 'SYSTEM_SHUTDOWN', label: '系统关闭' },
      { value: 'OTHER', label: '其他事件' },
    ],
  },
] as const;

export const statusOptions: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: '全部结果' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
];

const categoryTypeMap: Record<Exclude<CategoryKey, 'ALL'>, string[]> = eventGroups.reduce(
  (acc, group) => ({
    ...acc,
    [group.category]: group.options.map((option) => option.value),
  }),
  {} as Record<Exclude<CategoryKey, 'ALL'>, string[]>,
);

export const formatAuditTime = (time?: string) => {
  if (!time) return '-';
  return new Date(time).toLocaleString();
};

export const shortResource = (record: AuditLog) => {
  if (!record.resourceType && !record.resourceId) return '-';
  if (!record.resourceId) return record.resourceType;
  return `${record.resourceType || '资源'} · ${record.resourceId.substring(0, 12)}`;
};

export const matchesFilters = (
  record: AuditLog,
  category: CategoryKey,
  eventType: string | undefined,
  status: StatusFilter,
) => {
  if (eventType && record.type !== eventType) {
    return false;
  }
  if (!eventType && category !== 'ALL' && !categoryTypeMap[category].includes(record.type)) {
    return false;
  }
  if (status === 'SUCCESS' && !record.success) {
    return false;
  }
  if (status === 'FAILED' && record.success) {
    return false;
  }
  return true;
};
