import React from 'react';
import { Card, Descriptions, Space, Tag, Typography } from 'antd';
import type { GmEdgePolicy, QuarantinePolicy, RelayPolicy, SystemSettings } from '../../types';
import CryptoCapabilityTags from './CryptoCapabilityTags';
import {
  booleanTag,
  configuredTag,
  formatBytes,
  getSettingsSummary,
} from './settingsUtils';

const { Text } = Typography;

interface SettingsPanelProps {
  settings: SystemSettings | null;
}

interface RuntimeStatusPanelProps extends SettingsPanelProps {
  title?: string;
}

interface RelayPolicySummaryPanelProps extends SettingsPanelProps {
  relayPolicy: RelayPolicy | null;
}

interface QuarantinePolicySummaryPanelProps extends SettingsPanelProps {
  quarantinePolicy: QuarantinePolicy | null;
}

interface GmEdgePolicySummaryPanelProps extends SettingsPanelProps {
  gmEdgePolicy: GmEdgePolicy | null;
}

const formatDateTime = (value?: string) => (value ? new Date(value).toLocaleString() : '-');

export const RuntimeStatusPanel: React.FC<RuntimeStatusPanelProps> = ({ settings, title = '运行状态' }) => {
  const { activeProfiles } = getSettingsSummary(settings);

  return (
    <Card title={title} styles={{ body: { paddingTop: 12 } }}>
      <Descriptions column={1} bordered className="settings-descriptions">
        <Descriptions.Item label="应用名称">{settings?.runtime.applicationName || '-'}</Descriptions.Item>
        <Descriptions.Item label="运行 Profile">
          <Space size={4} wrap>
            {activeProfiles.map((profile) => (
              <Tag key={profile} className="settings-tag">
                {profile}
              </Tag>
            ))}
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="配置来源">{settings?.runtime.configSource || '-'}</Descriptions.Item>
        <Descriptions.Item label="配置修改">
          <Tag color={settings?.runtime.onlineEditingSupported ? 'success' : 'warning'} className="settings-tag">
            {settings?.runtime.onlineEditingSupported ? '支持在线保存' : '重启生效'}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="快照时间">{formatDateTime(settings?.runtime.generatedAt)}</Descriptions.Item>
      </Descriptions>
    </Card>
  );
};

export const SmtpEntryPanel: React.FC<SettingsPanelProps> = ({ settings }) => (
  <Card title="SMTP 入口">
    <Descriptions column={1} bordered className="settings-descriptions">
      <Descriptions.Item label="监听地址">{settings?.smtpServer.bindAddress || '-'}</Descriptions.Item>
      <Descriptions.Item label="监听端口">{settings?.smtpServer.port ?? '-'}</Descriptions.Item>
      <Descriptions.Item label="最大连接">{settings?.smtpServer.maxConnections ?? '-'}</Descriptions.Item>
      <Descriptions.Item label="单封邮件上限">
        {formatBytes(settings?.smtpServer.maxMessageSizeBytes ?? Number.NaN)}
      </Descriptions.Item>
    </Descriptions>
  </Card>
);

export const DeliveryChainPanel: React.FC<SettingsPanelProps> = ({ settings }) => {
  const { deliveryEndpoint, deliveryMode } = getSettingsSummary(settings);

  return (
    <Card title="投递链路">
      <Descriptions column={1} bordered className="settings-descriptions">
        <Descriptions.Item label="处理后发往">
          <Space wrap>
            <Tag color={settings?.delivery.mode === 'POSTFIX' ? 'processing' : 'blue'} className="settings-tag">
              {deliveryMode}
            </Tag>
            <Text>{deliveryEndpoint}</Text>
          </Space>
        </Descriptions.Item>
        {settings?.delivery.mode === 'POSTFIX' ? (
          <>
            <Descriptions.Item label="Postfix 主机">{settings.delivery.postfix.host || '-'}</Descriptions.Item>
            <Descriptions.Item label="回注端口">
              {settings.delivery.postfix.afterFilterPort} / {settings.delivery.postfix.outboundPort}
            </Descriptions.Item>
            <Descriptions.Item label="超时">{settings.delivery.postfix.timeoutMs} ms</Descriptions.Item>
            <Descriptions.Item label="Envelope From">{settings.delivery.postfix.envelopeFrom || '-'}</Descriptions.Item>
          </>
        ) : (
          <>
            <Descriptions.Item label="中继主机">
              {settings?.delivery.directRelay.host || '-'}:{settings?.delivery.directRelay.port ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="认证配置">
              <Space wrap>
                <Text>用户名</Text>
                {configuredTag(settings?.delivery.directRelay.usernameConfigured || false)}
                <Text>密码</Text>
                {configuredTag(settings?.delivery.directRelay.passwordConfigured || false)}
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="超时">{settings?.delivery.directRelay.timeoutMs ?? '-'} ms</Descriptions.Item>
          </>
        )}
      </Descriptions>
    </Card>
  );
};

export const RelayPolicySummaryPanel: React.FC<RelayPolicySummaryPanelProps> = ({ relayPolicy, settings }) => {
  const fallbackRelay = settings?.delivery.directRelay;
  const enabled = relayPolicy?.enabled ?? settings?.delivery.mode === 'DIRECT_RELAY';

  return (
    <Card title="Relay 策略摘要">
      <Descriptions column={1} bordered className="settings-descriptions">
        <Descriptions.Item label="启用状态">{booleanTag(enabled)}</Descriptions.Item>
        <Descriptions.Item label="主机">
          {relayPolicy?.host || fallbackRelay?.host || '-'}:{relayPolicy?.port ?? fallbackRelay?.port ?? '-'}
        </Descriptions.Item>
        <Descriptions.Item label="超时">{relayPolicy?.timeoutMs ?? fallbackRelay?.timeoutMs ?? '-'} ms</Descriptions.Item>
        <Descriptions.Item label="认证配置">
          <Space wrap>
            <Text>用户名</Text>
            {configuredTag(Boolean(relayPolicy?.username || fallbackRelay?.usernameConfigured))}
            <Text>密码</Text>
            {configuredTag(Boolean(relayPolicy?.passwordConfigured || fallbackRelay?.passwordConfigured))}
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="密码 Secret">{configuredTag(Boolean(relayPolicy?.passwordSecretRef))}</Descriptions.Item>
        <Descriptions.Item label="Envelope From">{relayPolicy?.envelopeFrom || '-'}</Descriptions.Item>
        <Descriptions.Item label="更新时间">{formatDateTime(relayPolicy?.updatedAt)}</Descriptions.Item>
      </Descriptions>
    </Card>
  );
};

export const QuarantinePolicySummaryPanel: React.FC<QuarantinePolicySummaryPanelProps> = ({
  quarantinePolicy,
  settings,
}) => {
  const policy = quarantinePolicy || settings?.quarantinePolicy;

  return (
    <Card title="隔离策略摘要">
      <Descriptions column={1} bordered className="settings-descriptions">
        <Descriptions.Item label="保留天数">{policy?.maxRetentionDays ?? '-'} 天</Descriptions.Item>
        <Descriptions.Item label="通知开关">{booleanTag(policy?.notificationEnabled || false)}</Descriptions.Item>
        <Descriptions.Item label="放行前强制加密">
          {booleanTag(policy?.releaseRequiresEncryption || false, '强制加密', '不强制')}
        </Descriptions.Item>
        <Descriptions.Item label="更新时间">
          {'updatedAt' in (policy || {}) ? formatDateTime((policy as QuarantinePolicy).updatedAt) : '-'}
        </Descriptions.Item>
      </Descriptions>
    </Card>
  );
};

export const GmEdgePolicySummaryPanel: React.FC<GmEdgePolicySummaryPanelProps> = ({ gmEdgePolicy, settings }) => {
  const gmEdge = gmEdgePolicy || settings?.gmEdge;

  return (
    <Card title="国密 Edge 策略摘要">
      <Descriptions column={1} bordered className="settings-descriptions">
        <Descriptions.Item label="启用状态">{booleanTag(gmEdge?.enabled || false)}</Descriptions.Item>
        <Descriptions.Item label="入站端口">
          <Space wrap>
            {booleanTag(gmEdge?.inbound.enabled || false, '监听', '关闭')}
            <Text>
              {gmEdge?.inbound.bindAddress || '-'}:{gmEdge?.inbound.startTlsPort ?? '-'} / {gmEdge?.inbound.implicitTlsPort ?? '-'}
            </Text>
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="出站 Smart Host">
          <Space wrap>
            {booleanTag(gmEdge?.outbound.enabled || false, '启用', '关闭')}
            <Text>{gmEdge?.outbound.bindAddress || '-'}:{gmEdge?.outbound.smartHostPort ?? '-'}</Text>
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="Postfix 回注">
          {gmEdge?.postfix.host || '-'}:{gmEdge?.postfix.port ?? '-'}
        </Descriptions.Item>
        <Descriptions.Item label="路由数量">{gmEdge?.routes.length ?? 0}</Descriptions.Item>
        <Descriptions.Item label="限制参数">
          <Space size={6} wrap>
            <Tag className="settings-tag">连接 {gmEdge?.limits.connectTimeoutMs ?? '-'} ms</Tag>
            <Tag className="settings-tag">读取 {gmEdge?.limits.readTimeoutMs ?? '-'} ms</Tag>
            <Tag className="settings-tag">单封 {formatBytes(gmEdge?.limits.maxMessageSizeBytes ?? Number.NaN)}</Tag>
            <Tag className="settings-tag">收件人 {gmEdge?.limits.maxRecipients ?? '-'}</Tag>
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="KeyStore">
          <Space wrap>
            {configuredTag(gmEdge?.tls.keyStoreConfigured || false)}
            <Text>{gmEdge?.tls.keyStorePath || '-'}</Text>
            <Text type="secondary">{gmEdge?.tls.keyStoreType || '-'}</Text>
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="TrustStore">
          <Space wrap>
            {configuredTag(gmEdge?.tls.trustStoreConfigured || false)}
            <Text>{gmEdge?.tls.trustStorePath || '-'}</Text>
            <Text type="secondary">{gmEdge?.tls.trustStoreType || '-'}</Text>
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="更新时间">{formatDateTime(gmEdgePolicy?.updatedAt)}</Descriptions.Item>
      </Descriptions>
    </Card>
  );
};

export const CertificateValidationPanel: React.FC<SettingsPanelProps> = ({ settings }) => (
  <Card title="证书校验">
    <Descriptions column={1} bordered className="settings-descriptions">
      <Descriptions.Item label="CRL 检查">{booleanTag(settings?.certificateValidation.crlEnabled || false)}</Descriptions.Item>
      <Descriptions.Item label="OCSP 检查">{booleanTag(settings?.certificateValidation.ocspEnabled || false)}</Descriptions.Item>
      <Descriptions.Item label="OCSP 超时">{settings?.certificateValidation.ocspTimeoutMs ?? '-'} ms</Descriptions.Item>
      <Descriptions.Item label="CRL 地址">
        <Text copyable>{settings?.internalCa.crlBaseUrl || '-'}</Text>
      </Descriptions.Item>
    </Descriptions>
  </Card>
);

export const CryptoCapabilitiesPanel: React.FC<SettingsPanelProps> = ({ settings }) => (
  <Card title="算法能力">
    {settings?.smimeSuitePolicy ? (
      <Space size={8} wrap style={{ marginBottom: 12 }}>
        <Tag color="processing" className="settings-tag">
          STANDARD 默认: {settings.smimeSuitePolicy.defaultStandardSuite}
        </Tag>
        <Tag color="error" className="settings-tag">
          GM 默认: {settings.smimeSuitePolicy.defaultGmSuite}
        </Tag>
      </Space>
    ) : null}
    <CryptoCapabilityTags capabilities={settings?.cryptoCapabilities} />
  </Card>
);

export const GmTlsEdgePanel: React.FC<SettingsPanelProps> = ({ settings }) => (
  <Card title="国密 TLS Edge">
    <Descriptions column={1} bordered className="settings-descriptions">
      <Descriptions.Item label="运行状态">{booleanTag(settings?.gmEdge.enabled || false)}</Descriptions.Item>
      <Descriptions.Item label="协议">
        <Space size={6} wrap>
          {settings?.gmEdge.tls.protocols.map((protocol) => (
            <Tag key={protocol} color="error" className="settings-tag">
              {protocol}
            </Tag>
          )) || '-'}
        </Space>
      </Descriptions.Item>
      <Descriptions.Item label="套件">
        <Space size={6} wrap>
          {settings?.gmEdge.tls.cipherSuites.map((cipherSuite) => (
            <Tag key={cipherSuite} color="error" className="settings-tag">
              {cipherSuite}
            </Tag>
          )) || '-'}
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
      <Descriptions.Item label="信任策略">
        {booleanTag(settings?.gmEdge.tls.trustAll || false, '信任全部', 'TrustStore 校验')}
      </Descriptions.Item>
      <Descriptions.Item label="入站端口">
        {settings?.gmEdge.inbound.bindAddress || '-'}:{settings?.gmEdge.inbound.startTlsPort ?? '-'} / {settings?.gmEdge.inbound.implicitTlsPort ?? '-'}
      </Descriptions.Item>
      <Descriptions.Item label="出站 Smart Host">
        {settings?.gmEdge.outbound.bindAddress || '-'}:{settings?.gmEdge.outbound.smartHostPort ?? '-'}
      </Descriptions.Item>
    </Descriptions>
  </Card>
);
