import React from 'react';
import { Card, Descriptions, Space, Tag, Typography } from 'antd';
import type { FormInstance } from 'antd';
import type { GmEdgePolicy, QuarantinePolicy, RelayPolicy, SystemSettings } from '../../types';
import type { GmEdgePolicyFormValues, QuarantinePolicyFormValues, RelayPolicyFormValues } from './settingsUtils';
import {
  configuredTag,
  formatBytes,
  getSettingsSummary,
} from './settingsUtils';
import {
  GmEdgePolicyForm,
  QuarantinePolicyForm,
  RelayPolicyForm,
} from './PolicyForms';

const { Text } = Typography;

interface MailSectionProps {
  gmEdgeForm: FormInstance<GmEdgePolicyFormValues>;
  gmEdgePolicy: GmEdgePolicy | null;
  quarantineForm: FormInstance<QuarantinePolicyFormValues>;
  quarantinePolicy: QuarantinePolicy | null;
  relayForm: FormInstance<RelayPolicyFormValues>;
  relayPolicy: RelayPolicy | null;
  settings: SystemSettings | null;
  onGmEdgePolicySave: (values: GmEdgePolicyFormValues) => void | Promise<void>;
  onQuarantinePolicySave: (values: QuarantinePolicyFormValues) => void | Promise<void>;
  onRelayPolicySave: (values: RelayPolicyFormValues) => void | Promise<void>;
}

const MailSection: React.FC<MailSectionProps> = ({
  gmEdgeForm,
  gmEdgePolicy,
  quarantineForm,
  quarantinePolicy,
  relayForm,
  relayPolicy,
  settings,
  onGmEdgePolicySave,
  onQuarantinePolicySave,
  onRelayPolicySave,
}) => {
  const { deliveryEndpoint, deliveryMode } = getSettingsSummary(settings);

  return (
    <Space direction="vertical" size={16} className="full-width">
      <Card title="SMTP 入口">
        <Descriptions column={1} bordered className="settings-descriptions">
          <Descriptions.Item label="监听地址">{settings?.smtpServer.bindAddress}</Descriptions.Item>
          <Descriptions.Item label="监听端口">{settings?.smtpServer.port}</Descriptions.Item>
          <Descriptions.Item label="最大连接">{settings?.smtpServer.maxConnections}</Descriptions.Item>
          <Descriptions.Item label="单封邮件上限">{formatBytes(settings?.smtpServer.maxMessageSizeBytes || 0)}</Descriptions.Item>
        </Descriptions>
      </Card>

      <GmEdgePolicyForm form={gmEdgeForm} policy={gmEdgePolicy} onSave={onGmEdgePolicySave} />

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
              <Descriptions.Item label="Postfix 主机">{settings.delivery.postfix.host}</Descriptions.Item>
              <Descriptions.Item label="回注端口">
                {settings.delivery.postfix.afterFilterPort} / {settings.delivery.postfix.outboundPort}
              </Descriptions.Item>
              <Descriptions.Item label="超时">{settings.delivery.postfix.timeoutMs} ms</Descriptions.Item>
              <Descriptions.Item label="Envelope From">{settings.delivery.postfix.envelopeFrom || '-'}</Descriptions.Item>
            </>
          ) : (
            <>
              <Descriptions.Item label="中继主机">
                {settings?.delivery.directRelay.host}:{settings?.delivery.directRelay.port}
              </Descriptions.Item>
              <Descriptions.Item label="认证">
                <Space wrap>
                  <Text>用户名</Text>
                  {configuredTag(settings?.delivery.directRelay.usernameConfigured || false)}
                  <Text>密码</Text>
                  {configuredTag(settings?.delivery.directRelay.passwordConfigured || false)}
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="超时">{settings?.delivery.directRelay.timeoutMs} ms</Descriptions.Item>
            </>
          )}
        </Descriptions>
      </Card>

      <RelayPolicyForm form={relayForm} policy={relayPolicy} onSave={onRelayPolicySave} />
      <QuarantinePolicyForm form={quarantineForm} policy={quarantinePolicy} onSave={onQuarantinePolicySave} />
    </Space>
  );
};

export default MailSection;
