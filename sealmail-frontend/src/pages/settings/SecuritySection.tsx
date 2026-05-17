import React from 'react';
import { Card, Descriptions, Space, Tag, Typography } from 'antd';
import type { SystemSettings } from '../../types';
import CryptoCapabilityTags from './CryptoCapabilityTags';
import { booleanTag } from './settingsUtils';

const { Text } = Typography;

interface SecuritySectionProps {
  settings: SystemSettings | null;
}

const SecuritySection: React.FC<SecuritySectionProps> = ({ settings }) => (
  <Space direction="vertical" size={16} className="full-width">
    <Card title="证书校验">
      <Descriptions column={1} bordered className="settings-descriptions">
        <Descriptions.Item label="CRL 检查">{booleanTag(settings?.certificateValidation.crlEnabled || false)}</Descriptions.Item>
        <Descriptions.Item label="OCSP 检查">{booleanTag(settings?.certificateValidation.ocspEnabled || false)}</Descriptions.Item>
        <Descriptions.Item label="OCSP 超时">{settings?.certificateValidation.ocspTimeoutMs ?? '-'} ms</Descriptions.Item>
        <Descriptions.Item label="CRL 地址">
          <Text copyable>{settings?.internalCa.crlBaseUrl}</Text>
        </Descriptions.Item>
      </Descriptions>
    </Card>
    <Card title="算法能力">
      <CryptoCapabilityTags capabilities={settings?.cryptoCapabilities} />
    </Card>
    <Card title="国密 TLS Edge">
      <Descriptions column={1} bordered className="settings-descriptions">
        <Descriptions.Item label="运行状态">{booleanTag(settings?.gmEdge.enabled || false)}</Descriptions.Item>
        <Descriptions.Item label="协议">
          <Space size={6} wrap>
            {settings?.gmEdge.tls.protocols.map((protocol) => (
              <Tag key={protocol} color="error" className="settings-tag">
                {protocol}
              </Tag>
            ))}
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="套件">
          <Space size={6} wrap>
            {settings?.gmEdge.tls.cipherSuites.map((cipherSuite) => (
              <Tag key={cipherSuite} color="error" className="settings-tag">
                {cipherSuite}
              </Tag>
            ))}
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="KeyStore">
          <Space wrap>
            {booleanTag(settings?.gmEdge.tls.keyStoreConfigured || false, '已配置', '未配置')}
            <Text>{settings?.gmEdge.tls.keyStorePath || '-'}</Text>
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="TrustStore">
          <Space wrap>
            {booleanTag(settings?.gmEdge.tls.trustStoreConfigured || false, '已配置', '未配置')}
            <Text>{settings?.gmEdge.tls.trustStorePath || '-'}</Text>
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="信任策略">{booleanTag(settings?.gmEdge.tls.trustAll || false, '信任全部', 'TrustStore 校验')}</Descriptions.Item>
        <Descriptions.Item label="入站端口">
          {settings?.gmEdge.inbound.bindAddress}:{settings?.gmEdge.inbound.startTlsPort} / {settings?.gmEdge.inbound.implicitTlsPort}
        </Descriptions.Item>
        <Descriptions.Item label="出站 Smart Host">
          {settings?.gmEdge.outbound.bindAddress}:{settings?.gmEdge.outbound.smartHostPort}
        </Descriptions.Item>
      </Descriptions>
    </Card>
  </Space>
);

export default SecuritySection;
