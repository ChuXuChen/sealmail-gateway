import React from 'react';
import { Button, Card, Col, Form, Input, InputNumber, Row, Switch } from 'antd';
import type { FormInstance } from 'antd';
import type { RelayPolicy } from '../../types';
import type { RelayPolicyFormValues } from './settingsUtils';

interface RelayPolicyFormProps {
  form: FormInstance<RelayPolicyFormValues>;
  policy: RelayPolicy | null;
  onSave: (values: RelayPolicyFormValues) => void | Promise<void>;
}

export const RelayPolicyForm: React.FC<RelayPolicyFormProps> = ({ form, policy, onSave }) => (
  <Card title="Relay 策略">
    <Form
      form={form}
      layout="vertical"
      onFinish={onSave}
      initialValues={{
        enabled: policy?.enabled ?? false,
        host: policy?.host ?? 'localhost',
        port: policy?.port ?? 25,
        timeoutMs: policy?.timeoutMs ?? 30000,
        allowUnconfiguredExternalRecipientDomains: policy?.allowUnconfiguredExternalRecipientDomains ?? false,
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
);
