import React from 'react';
import { Button, Card, Col, Form, Input, Row, Select, Switch } from 'antd';
import type { FormInstance } from 'antd';
import type { MailAuthGlobalFormValues } from './mailAuthUtils';
import { failureActionOptions, trustedProxyModeOptions } from './mailAuthUtils';

interface MailAuthGlobalPolicyFormProps {
  form: FormInstance<MailAuthGlobalFormValues>;
  loading: boolean;
  onSubmit: (values: MailAuthGlobalFormValues) => void | boolean | Promise<void | boolean>;
}

const MailAuthGlobalPolicyForm: React.FC<MailAuthGlobalPolicyFormProps> = ({
  form,
  loading,
  onSubmit,
}) => (
  <Card title="全局策略" className="mail-auth-section">
    <Form form={form} layout="vertical" disabled={loading} onFinish={onSubmit}>
      <Row gutter={16}>
        <Col xs={24} md={8}>
          <Form.Item name="enabled" label="邮件认证" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item
            name="authservId"
            label="认证服务标识"
            rules={[{ required: true, message: '请输入认证服务标识' }]}
          >
            <Input placeholder="sealmail-gateway" />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name="trustedProxyMode" label="来源 IP 模式">
            <Select options={trustedProxyModeOptions} />
          </Form.Item>
        </Col>
        <Col xs={24} md={8}>
          <Form.Item name="failureDefaultAction" label="认证失败动作">
            <Select options={failureActionOptions} />
          </Form.Item>
        </Col>
      </Row>
      <Form.Item className="form-actions">
        <Button type="primary" htmlType="submit" loading={loading}>
          保存全局策略
        </Button>
      </Form.Item>
    </Form>
  </Card>
);

export default MailAuthGlobalPolicyForm;
