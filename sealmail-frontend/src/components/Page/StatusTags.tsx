import React from 'react';
import { CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { Space, Tag } from 'antd';
import type { Certificate, DlpAction, DlpUbaSenderRisk, QuarantineItem } from '../../types';

const reasonColors: Record<string, string> = {
  POLICY_VIOLATION: 'error',
  DECRYPTION_FAILED: 'orange',
  CERTIFICATE_MISSING: 'gold',
  SIGNATURE_INVALID: 'orange',
  ENCRYPTION_FAILED: 'error',
  EMAIL_AUTH_FAILED: 'volcano',
  DOMAIN_NOT_CONFIGURED: 'orange',
  SCAN_ERROR: 'purple',
  CERTIFICATE_REVOKED: 'error',
};

const reasonLabels: Record<string, string> = {
  POLICY_VIOLATION: '策略违规',
  DECRYPTION_FAILED: '解密失败',
  CERTIFICATE_MISSING: '缺少证书',
  SIGNATURE_INVALID: '签名无效',
  ENCRYPTION_FAILED: '加密失败',
  EMAIL_AUTH_FAILED: '认证失败',
  DOMAIN_NOT_CONFIGURED: '域名未配置',
  SCAN_ERROR: '扫描错误',
  CERTIFICATE_REVOKED: '证书已吊销',
};

const dlpActionColors: Record<DlpAction, string> = {
  WARN: 'processing',
  MUST_ENCRYPT: 'gold',
  QUARANTINE: 'orange',
  BLOCK: 'error',
};

const dlpActionLabels: Record<DlpAction, string> = {
  WARN: '告警',
  MUST_ENCRYPT: '强制加密',
  QUARANTINE: '隔离',
  BLOCK: '阻断',
};

const quarantineStatusColors: Record<QuarantineItem['status'], string> = {
  QUARANTINED: 'gold',
  RELEASING: 'processing',
  RELEASED: 'success',
  REJECTED: 'error',
};

const quarantineStatusLabels: Record<QuarantineItem['status'], string> = {
  QUARANTINED: '待处理',
  RELEASING: '释放确认中',
  RELEASED: '已放行',
  REJECTED: '已拒绝',
};

const dlpRiskColors: Record<DlpUbaSenderRisk['riskLevel'], string> = {
  LOW: 'success',
  MEDIUM: 'warning',
  HIGH: 'error',
};

const dlpRiskLabels: Record<DlpUbaSenderRisk['riskLevel'], string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
};

export const ReasonTag: React.FC<{ reason?: string }> = ({ reason }) => (
  <Tag color={reason ? reasonColors[reason] || 'default' : 'default'}>
    {reason ? reasonLabels[reason] || reason : '未知'}
  </Tag>
);

export const QuarantineStatusTag: React.FC<{ status: QuarantineItem['status'] }> = ({ status }) => (
  <Tag color={quarantineStatusColors[status] || 'default'}>
    {quarantineStatusLabels[status] || status}
  </Tag>
);

export const DlpActionTag: React.FC<{ action: string }> = ({ action }) => (
  <Tag color={dlpActionColors[action as DlpAction] || 'default'}>
    {dlpActionLabels[action as DlpAction] || action}
  </Tag>
);

export const DlpRiskTag: React.FC<{ risk?: DlpUbaSenderRisk['riskLevel'] | string }> = ({ risk }) => (
  <Tag color={risk ? dlpRiskColors[risk as DlpUbaSenderRisk['riskLevel']] || 'default' : 'default'}>
    {risk ? dlpRiskLabels[risk as DlpUbaSenderRisk['riskLevel']] || risk : '低'}
  </Tag>
);

export const EnabledTag: React.FC<{ enabled: boolean; enabledText?: string; disabledText?: string }> = ({
  disabledText = '停用',
  enabled,
  enabledText = '启用',
}) => (
  <Tag color={enabled ? 'success' : 'default'}>{enabled ? enabledText : disabledText}</Tag>
);

export const AuditResultTag: React.FC<{ success: boolean }> = ({ success }) => (
  <Tag icon={success ? <CheckCircleOutlined /> : <CloseCircleOutlined />} color={success ? 'success' : 'error'}>
    {success ? '成功' : '失败'}
  </Tag>
);

export const AlgorithmTag: React.FC<{ algorithm?: string }> = ({ algorithm }) => {
  if (algorithm === 'SM2') return <Tag color="volcano">SM2</Tag>;
  if (algorithm === 'RSA') return <Tag color="processing">RSA</Tag>;
  return <Tag>{algorithm || '未知'}</Tag>;
};

export const CertificateRoleTag: React.FC<{ cert: Certificate }> = ({ cert }) => (
  <Tag color={cert.pathLenConstraint === 1 ? 'purple' : 'cyan'}>
    {cert.pathLenConstraint === 1 ? 'Root CA' : 'Intermediate CA'}
  </Tag>
);

export const CertificateStateTags: React.FC<{ cert: Certificate }> = ({ cert }) => (
  <Space size={[0, 4]} wrap>
    <Tag color={cert.trusted ? 'success' : 'default'}>{cert.trusted ? '信任' : '未信'}</Tag>
    <Tag color={cert.revoked ? 'error' : 'processing'}>{cert.revoked ? '吊销' : '有效'}</Tag>
    {cert.chainUsable === false ? <Tag color="orange">链断</Tag> : null}
  </Space>
);
