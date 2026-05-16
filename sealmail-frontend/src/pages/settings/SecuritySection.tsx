import React from 'react';
import { Card, Descriptions, Space, Tag, Typography } from 'antd';
import { FileProtectOutlined, LockOutlined } from '@ant-design/icons';
import type { SystemSettings } from '../../types';
import { booleanTag, configuredTag } from './settingsUtils';

const { Text } = Typography;

interface SecuritySectionProps {
  settings: SystemSettings | null;
}

const SecuritySection: React.FC<SecuritySectionProps> = ({ settings }) => (
  <Space direction="vertical" size={16} className="full-width">
    <Card title="TLS 与证书校验">
      <Descriptions column={1} bordered className="settings-descriptions">
        <Descriptions.Item label="Keystore">{configuredTag(settings?.smtpServer.tls.keystoreConfigured || false)}</Descriptions.Item>
        <Descriptions.Item label="PEM 证书">{configuredTag(settings?.smtpServer.tls.pemConfigured || false)}</Descriptions.Item>
        <Descriptions.Item label="Key Alias">{settings?.smtpServer.tls.keyAlias || '-'}</Descriptions.Item>
        <Descriptions.Item label="CRL 检查">{booleanTag(settings?.certificateValidation.crlEnabled || false)}</Descriptions.Item>
        <Descriptions.Item label="OCSP 检查">{booleanTag(settings?.certificateValidation.ocspEnabled || false)}</Descriptions.Item>
        <Descriptions.Item label="OCSP 超时">{settings?.certificateValidation.ocspTimeoutMs ?? '-'} ms</Descriptions.Item>
        <Descriptions.Item label="CRL 地址">
          <Text copyable>{settings?.internalCa.crlBaseUrl}</Text>
        </Descriptions.Item>
      </Descriptions>
    </Card>
    <Card title="算法能力">
      <Space size={8} wrap>
        {settings?.cryptoCapabilities.flatMap((capability) =>
          capability.algorithms.map((algorithm) => (
            <Tag
              key={`${capability.category}-${algorithm}`}
              color={algorithm.startsWith('SM') ? 'error' : 'processing'}
              icon={capability.category === '内容加密' ? <LockOutlined /> : <FileProtectOutlined />}
              className="settings-tag"
            >
              {capability.category}: {algorithm}
            </Tag>
          )),
        )}
      </Space>
    </Card>
  </Space>
);

export default SecuritySection;
