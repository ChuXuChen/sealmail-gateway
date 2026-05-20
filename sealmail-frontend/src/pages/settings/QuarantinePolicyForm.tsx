import React from 'react';
import { Button, Card, Col, Form, InputNumber, Row, Switch } from 'antd';
import type { FormInstance } from 'antd';
import type { QuarantinePolicy } from '../../types';
import type { QuarantinePolicyFormValues } from './settingsUtils';

interface QuarantinePolicyFormProps {
  form: FormInstance<QuarantinePolicyFormValues>;
  policy: QuarantinePolicy | null;
  onSave: (values: QuarantinePolicyFormValues) => void | Promise<void>;
}

export const QuarantinePolicyForm: React.FC<QuarantinePolicyFormProps> = ({ form, policy, onSave }) => (
  <Card title="隔离策略">
    <Form
      form={form}
      layout="vertical"
      onFinish={onSave}
      initialValues={{
        maxRetentionDays: policy?.maxRetentionDays ?? 30,
        notificationEnabled: policy?.notificationEnabled ?? false,
        releaseRequiresEncryption: policy?.releaseRequiresEncryption ?? false,
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
);
