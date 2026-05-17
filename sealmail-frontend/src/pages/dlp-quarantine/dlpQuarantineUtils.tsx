import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  LockOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import type { QuarantineItem } from '../../types';

export type DlpQuarantineActionKey =
  'release'
  | 'release-encrypted'
  | 'reject'
  | 'complete-release'
  | 'restore-release';

export const reasonOptions = [
  { value: 'POLICY_VIOLATION', label: '策略违规' },
  { value: 'CERTIFICATE_MISSING', label: '缺少证书' },
  { value: 'ENCRYPTION_FAILED', label: '加密失败' },
  { value: 'SCAN_ERROR', label: '扫描错误' },
];

export const isDlpQuarantineActionKey = (key: string): key is DlpQuarantineActionKey =>
  key === 'release'
  || key === 'release-encrypted'
  || key === 'reject'
  || key === 'complete-release'
  || key === 'restore-release';

export const canRelease = (record: QuarantineItem) =>
  record.status === 'QUARANTINED' && record.canRelease !== false;

export const getActionItems = (
  record: QuarantineItem,
  releaseDisabled: boolean,
): MenuProps['items'] => {
  if (record.status === 'RELEASING') {
    return [
      {
        key: 'complete-release',
        icon: <CheckCircleOutlined />,
        label: '确认已投递',
      },
      {
        key: 'restore-release',
        danger: true,
        icon: <ReloadOutlined />,
        label: '恢复待处理',
      },
    ];
  }

  return [
    {
      key: 'release',
      icon: <CheckCircleOutlined />,
      label: releaseDisabled
        ? record.releaseUnavailableReason || '不可放行'
        : '直接放行',
      disabled: releaseDisabled,
    },
    {
      key: 'release-encrypted',
      icon: <LockOutlined />,
      label: releaseDisabled
        ? record.releaseUnavailableReason || '不可加密放行'
        : '加密放行',
      disabled: releaseDisabled,
    },
    {
      type: 'divider',
    },
    {
      key: 'reject',
      danger: true,
      icon: <CloseCircleOutlined />,
      label: '拒绝',
    },
  ];
};
