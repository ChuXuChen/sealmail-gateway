import React from 'react';
import { Button, Card, Col, Descriptions, Divider, Form, Input, InputNumber, Row, Select, Space, Switch, Tag, Typography } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { FormInstance } from 'antd';
import type { GmEdgePolicy, QuarantinePolicy, RelayPolicy, SystemSettings } from '../../types';
import type { GmEdgePolicyFormValues, QuarantinePolicyFormValues, RelayPolicyFormValues } from './settingsUtils';
import {
  applyGmEdgeDefaults,
  configuredTag,
  formatBytes,
  getSettingsSummary,
} from './settingsUtils';

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
      <Card title="国密 Edge 策略">
        <Form
          form={gmEdgeForm}
          layout="vertical"
          onFinish={onGmEdgePolicySave}
          initialValues={applyGmEdgeDefaults(gmEdgePolicy)}
        >
          <Row gutter={12}>
            <Col xs={24} md={8}>
              <Form.Item name="enabled" label="启用 Edge" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
            <Col xs={24} md={16}>
              <Form.Item label="标准链路">
                <Text>标准 SMTP/TLS 继续由 Postfix 直接处理；这里只配置 TLCP 和国密 TLS 1.3 专用端口。</Text>
              </Form.Item>
            </Col>
          </Row>

          <Divider orientation="left">入站</Divider>
          <Row gutter={12}>
            <Col xs={24} md={6}>
              <Form.Item name={['inbound', 'enabled']} label="入站监听" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name={['inbound', 'bindAddress']} label="绑定地址" rules={[{ required: true, message: '请输入绑定地址' }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name={['inbound', 'startTlsPort']} label="STARTTLS 端口" rules={[{ required: true, message: '请输入端口' }]}>
                <InputNumber min={1} max={65535} className="full-width" />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name={['inbound', 'implicitTlsPort']} label="隐式 TLS 端口" rules={[{ required: true, message: '请输入端口' }]}>
                <InputNumber min={1} max={65535} className="full-width" />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name={['inbound', 'backlog']} label="Backlog">
                <InputNumber min={1} className="full-width" />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name={['inbound', 'maxConnections']} label="最大连接">
                <InputNumber min={1} className="full-width" />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name={['postfix', 'host']} label="Postfix 主机" rules={[{ required: true, message: '请输入 Postfix 主机' }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name={['postfix', 'port']} label="Postfix 内部端口" rules={[{ required: true, message: '请输入端口' }]}>
                <InputNumber min={1} max={65535} className="full-width" />
              </Form.Item>
            </Col>
          </Row>

          <Divider orientation="left">出站</Divider>
          <Row gutter={12}>
            <Col xs={24} md={6}>
              <Form.Item name={['outbound', 'enabled']} label="出站 Smart Host" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name={['outbound', 'bindAddress']} label="绑定地址" rules={[{ required: true, message: '请输入绑定地址' }]}>
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name={['outbound', 'smartHostPort']} label="Smart Host 端口" rules={[{ required: true, message: '请输入端口' }]}>
                <InputNumber min={1} max={65535} className="full-width" />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name={['outbound', 'maxConnections']} label="最大连接">
                <InputNumber min={1} className="full-width" />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name={['outbound', 'backlog']} label="Backlog">
                <InputNumber min={1} className="full-width" />
              </Form.Item>
            </Col>
          </Row>

          <Divider orientation="left">TLS</Divider>
          <Row gutter={12}>
            <Col xs={24} md={12}>
              <Form.Item name={['tls', 'protocols']} label="协议" rules={[{ required: true, message: '请选择协议' }]}>
                <Select
                  mode="multiple"
                  options={[
                    { label: 'TLCPv1.1', value: 'TLCPv1.1' },
                    { label: 'TLCP', value: 'TLCP' },
                    { label: 'TLSv1.3', value: 'TLSv1.3' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name={['tls', 'cipherSuites']} label="国密套件">
                <Select
                  mode="tags"
                  tokenSeparators={[',']}
                  options={[
                    { label: 'TLS_SM4_GCM_SM3', value: 'TLS_SM4_GCM_SM3' },
                    { label: 'TLS_SM4_CCM_SM3', value: 'TLS_SM4_CCM_SM3' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name={['tls', 'keyStorePath']} label="KeyStore 路径">
                <Input placeholder="/run/secrets/sealmail-gm-edge.p12" />
              </Form.Item>
            </Col>
            <Col xs={24} md={4}>
              <Form.Item name={['tls', 'keyStoreType']} label="KeyStore 类型">
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name={['tls', 'keyStorePasswordSecretRef']} label="KeyStore 密码 Secret">
                <Input placeholder="env:SEALMAIL_GM_EDGE_KEYSTORE_PASSWORD" />
              </Form.Item>
            </Col>
            <Col xs={24} md={4}>
              <Form.Item name={['tls', 'clearKeyStorePasswordSecretRef']} label="清空密码 Secret" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name={['tls', 'trustStorePath']} label="TrustStore 路径">
                <Input placeholder="/run/secrets/sealmail-gm-trust.p12" />
              </Form.Item>
            </Col>
            <Col xs={24} md={4}>
              <Form.Item name={['tls', 'trustStoreType']} label="TrustStore 类型">
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={8}>
              <Form.Item name={['tls', 'trustStorePasswordSecretRef']} label="TrustStore 密码 Secret">
                <Input placeholder="env:SEALMAIL_GM_EDGE_TRUSTSTORE_PASSWORD" />
              </Form.Item>
            </Col>
            <Col xs={24} md={4}>
              <Form.Item name={['tls', 'clearTrustStorePasswordSecretRef']} label="清空密码 Secret" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name={['tls', 'trustAll']} label="信任全部" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>

          <Divider orientation="left">限制</Divider>
          <Row gutter={12}>
            <Col xs={24} md={6}>
              <Form.Item name={['limits', 'connectTimeoutMs']} label="连接超时 ms">
                <InputNumber min={1} className="full-width" />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name={['limits', 'readTimeoutMs']} label="读超时 ms">
                <InputNumber min={1} className="full-width" />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name={['limits', 'maxMessageSizeBytes']} label="单封上限 bytes">
                <InputNumber min={1} className="full-width" />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name={['limits', 'maxRecipients']} label="最大收件人">
                <InputNumber min={1} className="full-width" />
              </Form.Item>
            </Col>
            <Col xs={24} md={6}>
              <Form.Item name={['limits', 'maxLineLengthBytes']} label="最大行 bytes">
                <InputNumber min={1} className="full-width" />
              </Form.Item>
            </Col>
          </Row>

          <Divider orientation="left">国密出站路由</Divider>
          <Form.List name="routes">
            {(fields, { add, remove }) => (
              <Space direction="vertical" size={8} className="full-width">
                {fields.map((field) => (
                  <Row key={field.key} gutter={8} align="middle">
                    <Col xs={24} md={6}>
                      <Form.Item {...field} name={[field.name, 'domainPattern']} label="域模式" rules={[{ required: true, message: '请输入域模式' }]}>
                        <Input placeholder=".partner.example.cn" />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={7}>
                      <Form.Item {...field} name={[field.name, 'targetHost']} label="目标主机" rules={[{ required: true, message: '请输入目标主机' }]}>
                        <Input />
                      </Form.Item>
                    </Col>
                    <Col xs={12} md={4}>
                      <Form.Item {...field} name={[field.name, 'targetPort']} label="端口" rules={[{ required: true, message: '请输入端口' }]}>
                        <InputNumber min={1} max={65535} className="full-width" />
                      </Form.Item>
                    </Col>
                    <Col xs={12} md={5}>
                      <Form.Item {...field} name={[field.name, 'security']} label="TLS 模式" rules={[{ required: true, message: '请选择模式' }]}>
                        <Select
                          options={[
                            { label: 'STARTTLS', value: 'STARTTLS' },
                            { label: 'IMPLICIT_TLS', value: 'IMPLICIT_TLS' },
                          ]}
                        />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={2}>
                      <Button icon={<DeleteOutlined />} onClick={() => remove(field.name)} />
                    </Col>
                  </Row>
                ))}
                <Button icon={<PlusOutlined />} onClick={() => add({ security: 'STARTTLS', targetPort: 2525 })}>
                  添加路由
                </Button>
              </Space>
            )}
          </Form.List>
          <Button type="primary" htmlType="submit" style={{ marginTop: 16 }}>
            保存国密 Edge 策略
          </Button>
        </Form>
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
      <Card title="Relay 策略">
        <Form
          form={relayForm}
          layout="vertical"
          onFinish={onRelayPolicySave}
          initialValues={{
            enabled: relayPolicy?.enabled ?? false,
            host: relayPolicy?.host ?? 'localhost',
            port: relayPolicy?.port ?? 25,
            timeoutMs: relayPolicy?.timeoutMs ?? 30000,
            allowUnconfiguredExternalRecipientDomains: relayPolicy?.allowUnconfiguredExternalRecipientDomains ?? false,
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
            <Col xs={24} md={8}>
              <Form.Item
                name="allowUnconfiguredExternalRecipientDomains"
                label="允许未配置外部收件域"
                valuePropName="checked"
                extra="开启后未知外部域会走全局 Relay，DLP、强制加密和隔离仍优先。"
              >
                <Switch />
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
