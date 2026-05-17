import React from 'react';
import { Button, Card, Col, Descriptions, Form, Input, InputNumber, Radio, Row, Space, Switch, Tag, Typography } from 'antd';
import type { FormInstance } from 'antd';
import type { QuarantinePolicy, RelayPolicy, SystemSettings } from '../../types';
import type { QuarantinePolicyFormValues, RelayPolicyFormValues } from './settingsUtils';
import {
  booleanTag,
  configuredTag,
  formatBytes,
  getSettingsSummary,
  transportSecurityTag,
} from './settingsUtils';

const { Text } = Typography;

interface MailSectionProps {
  quarantineForm: FormInstance<QuarantinePolicyFormValues>;
  quarantinePolicy: QuarantinePolicy | null;
  relayForm: FormInstance<RelayPolicyFormValues>;
  relayPolicy: RelayPolicy | null;
  settings: SystemSettings | null;
  onQuarantinePolicySave: (values: QuarantinePolicyFormValues) => void | Promise<void>;
  onRelayPolicySave: (values: RelayPolicyFormValues) => void | Promise<void>;
}

const MailSection: React.FC<MailSectionProps> = ({
  quarantineForm,
  quarantinePolicy,
  relayForm,
  relayPolicy,
  settings,
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
          <Descriptions.Item label="STARTTLS">{booleanTag(settings?.smtpServer.tls.startTlsEnabled || false)}</Descriptions.Item>
          <Descriptions.Item label="强制 TLS">{booleanTag(settings?.smtpServer.tls.tlsRequired || false)}</Descriptions.Item>
        </Descriptions>
      </Card>
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
              <Descriptions.Item label="传输安全">
                {transportSecurityTag(settings.delivery.postfix.transportSecurity)}
              </Descriptions.Item>
              <Descriptions.Item label="超时">{settings.delivery.postfix.timeoutMs} ms</Descriptions.Item>
              <Descriptions.Item label="Envelope From">{settings.delivery.postfix.envelopeFrom || '-'}</Descriptions.Item>
            </>
          ) : (
            <>
              <Descriptions.Item label="中继主机">
                {settings?.delivery.directRelay.host}:{settings?.delivery.directRelay.port}
              </Descriptions.Item>
              <Descriptions.Item label="传输安全">
                {transportSecurityTag(settings?.delivery.directRelay.transportSecurity)}
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
      <Card title="Relay 策略">
        <Form
          form={relayForm}
          layout="vertical"
          onFinish={onRelayPolicySave}
          initialValues={{
            enabled: relayPolicy?.enabled ?? false,
            host: relayPolicy?.host ?? 'localhost',
            port: relayPolicy?.port ?? 25,
            transportSecurity: relayPolicy?.transportSecurity ?? (relayPolicy?.useTls ? 'STARTTLS' : 'NONE'),
            timeoutMs: relayPolicy?.timeoutMs ?? 30000,
          }}
        >
          <Row gutter={12}>
            <Col xs={24} md={8}>
              <Form.Item name="enabled" label="启用" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
            <Col xs={24} md={10}>
              <Form.Item name="host" label="主机" rules={[{ required: true, message: '请输入主机' }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name="port" label="端口">
                <InputNumber min={1} max={65535} className="full-width" />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name="transportSecurity" label="传输安全">
                <Radio.Group
                  optionType="button"
                  buttonStyle="solid"
                  options={[
                    { label: '无 TLS', value: 'NONE' },
                    { label: 'STARTTLS', value: 'STARTTLS' },
                    { label: 'SMTPS', value: 'SMTPS' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name="username" label="用户名">
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name="passwordSecretRef" label="密码 Secret 引用">
                <Input placeholder="file:/run/secrets/sealmail_relay_password" />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name="clearPasswordSecretRef" label="清空 Secret 引用" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name="timeoutMs" label="超时 ms">
                <InputNumber min={1} className="full-width" />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name="envelopeFrom" label="Envelope From">
                <Input />
              </Form.Item>
            </Col>
          </Row>
          <Button type="primary" htmlType="submit">
            保存 Relay 策略
          </Button>
        </Form>
      </Card>
      <Card title="隔离策略">
        <Form
          form={quarantineForm}
          layout="vertical"
          onFinish={onQuarantinePolicySave}
          initialValues={{
            maxRetentionDays: quarantinePolicy?.maxRetentionDays ?? 30,
            notificationEnabled: quarantinePolicy?.notificationEnabled ?? false,
            releaseRequiresEncryption: quarantinePolicy?.releaseRequiresEncryption ?? false,
          }}
        >
          <Row gutter={12}>
            <Col xs={24} md={8}>
              <Form.Item name="maxRetentionDays" label="保留天数">
                <InputNumber min={1} className="full-width" />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name="notificationEnabled" label="通知" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name="releaseRequiresEncryption" label="放行前强制加密" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>
          <Button type="primary" htmlType="submit">
            保存隔离策略
          </Button>
        </Form>
      </Card>
    </Space>
  );
};

export default MailSection;
