import { Tag } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import type {
  GmEdgePolicy,
  GmEdgePolicyRequest,
  QuarantinePolicy,
  RelayPolicy,
  SmimeSuitePolicyRequest,
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

export type GmEdgePolicyFormValues = GmEdgePolicyRequest;

export type SmimeSuitePolicyFormValues = SmimeSuitePolicyRequest;

export const defaultGmEdgePolicyValues: GmEdgePolicyFormValues = {
  enabled: false,
  inbound: {
    enabled: true,
    bindAddress: '0.0.0.0',
    startTlsPort: 2525,
    implicitTlsPort: 2465,
    backlog: 128,
    maxConnections: 1024,
  },
  outbound: {
    enabled: true,
    bindAddress: '127.0.0.1',
    smartHostPort: 2526,
    backlog: 128,
    maxConnections: 512,
  },
  postfix: {
    host: '127.0.0.1',
    port: 2530,
  },
  tls: {
    protocols: ['TLCPv1.1', 'TLCP', 'TLSv1.3'],
    cipherSuites: ['TLS_SM4_GCM_SM3', 'TLS_SM4_CCM_SM3'],
    keyStoreConfigured: false,
    keyStorePasswordConfigured: false,
    keyStoreType: 'PKCS12',
    trustStoreConfigured: false,
    trustStorePasswordConfigured: false,
    trustStoreType: 'PKCS12',
    trustAll: false,
    clearKeyStorePasswordSecretRef: false,
    clearTrustStorePasswordSecretRef: false,
  },
  limits: {
    connectTimeoutMs: 10000,
    readTimeoutMs: 60000,
    maxMessageSizeBytes: 52428800,
    maxLineLengthBytes: 16384,
    maxRecipients: 100,
  },
  routes: [],
};

export const applyGmEdgeDefaults = (policy?: GmEdgePolicy | null): GmEdgePolicyFormValues => ({
  ...defaultGmEdgePolicyValues,
  ...policy,
  inbound: {
    ...defaultGmEdgePolicyValues.inbound,
    ...policy?.inbound,
  },
  outbound: {
    ...defaultGmEdgePolicyValues.outbound,
    ...policy?.outbound,
  },
  postfix: {
    ...defaultGmEdgePolicyValues.postfix,
    ...policy?.postfix,
  },
  tls: {
    ...defaultGmEdgePolicyValues.tls,
    ...policy?.tls,
    clearKeyStorePasswordSecretRef: false,
    clearTrustStorePasswordSecretRef: false,
  },
  limits: {
    ...defaultGmEdgePolicyValues.limits,
    ...policy?.limits,
  },
  routes: policy?.routes ?? [],
});

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
