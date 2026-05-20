import React from 'react';
import { Button, Card, Col, Divider, Form, Input, InputNumber, Row, Select, Space, Switch, Typography } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { FormInstance } from 'antd';
import type { GmEdgePolicy } from '../../types';
import type { GmEdgePolicyFormValues } from './settingsUtils';
import { applyGmEdgeDefaults } from './settingsUtils';

const { Text } = Typography;

interface GmEdgePolicyFormProps {
  form: FormInstance<GmEdgePolicyFormValues>;
  policy: GmEdgePolicy | null;
  onSave: (values: GmEdgePolicyFormValues) => void | Promise<void>;
}

export const GmEdgePolicyForm: React.FC<GmEdgePolicyFormProps> = ({ form, policy, onSave }) => (
  <Card title="国密 Edge 策略">
    <Form
      form={form}
      layout="vertical"
      onFinish={onSave}
      initialValues={applyGmEdgeDefaults(policy)}
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
                  <Button icon={<DeleteOutlined />} onClick={() => remove(field.name)} aria-label="删除路由" />
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
);
