import { Tag } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import type {
  QuarantinePolicy,
  RelayPolicy,
  SystemSettings as SystemSettingsSnapshot,
} from '../../types';

export type SectionKey = 'runtime' | 'mail' | 'security' | 'tools';

export interface TestMailValues {
  from: string;
  to: string;
  subject: string;
  content: string;
}

export type RelayPolicyFormValues = Partial<RelayPolicy> & {
  clearPasswordSecretRef?: boolean;
};

export type QuarantinePolicyFormValues = Partial<QuarantinePolicy>;

export const formatBytes = (bytes: number) => {
  if (!Number.isFinite(bytes)) return '-';
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(0)} MB`;
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${bytes} B`;
};

export const splitRecipients = (value: string) =>
  value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);

export const booleanTag = (value: boolean, activeLabel = '启用', inactiveLabel = '关闭') => (
  <Tag color={value ? 'success' : 'default'} className="settings-tag">
    {value ? activeLabel : inactiveLabel}
  </Tag>
);

export const configuredTag = (value: boolean) => (
  <Tag
    color={value ? 'success' : 'warning'}
    icon={value ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
    className="settings-tag"
  >
    {value ? '已配置' : '未配置'}
  </Tag>
);

export const getSettingsSummary = (settings: SystemSettingsSnapshot | null) => {
  const smtpEndpoint = settings ? `${settings.smtpServer.bindAddress}:${settings.smtpServer.port}` : '-';
  const deliveryMode = settings?.delivery.mode === 'POSTFIX' ? 'Postfix' : 'SMTP 中继';
  const deliveryEndpoint = settings?.delivery.mode === 'POSTFIX'
    ? `${settings.delivery.postfix.host}:${settings.delivery.postfix.afterFilterPort} / ${settings.delivery.postfix.outboundPort}`
    : `${settings?.delivery.directRelay.host}:${settings?.delivery.directRelay.port}`;
  const activeProfiles = settings?.runtime.activeProfiles.length ? settings.runtime.activeProfiles : ['default'];

  return {
    activeProfiles,
    deliveryEndpoint,
    deliveryMode,
    smtpEndpoint,
  };
};
