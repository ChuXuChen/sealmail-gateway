import React from 'react';
import { Button, Card, Col, Form, Row, Select, Typography } from 'antd';
import type { FormInstance } from 'antd';
import type { SmimeSuitePolicy } from '../../types';
import type { SmimeSuitePolicyFormValues } from './settingsUtils';

const { Text } = Typography;

interface SmimeSuitePolicyFormProps {
  form: FormInstance<SmimeSuitePolicyFormValues>;
  policy: SmimeSuitePolicy | null;
  onSave: (values: SmimeSuitePolicyFormValues) => void | Promise<void>;
}

export const SmimeSuitePolicyForm: React.FC<SmimeSuitePolicyFormProps> = ({ form, policy, onSave }) => (
  <Card title="S/MIME 套件策略">
    <Form
      form={form}
      layout="vertical"
      onFinish={onSave}
      disabled={!policy}
    >
      <Row gutter={12}>
        <Col xs={24} md={12}>
          <Form.Item name="defaultStandardSuite" label="标准 S/MIME 默认套件" rules={[{ required: true, message: '请选择标准套件' }]}>
            <Select
              placeholder="请选择标准套件"
              options={(policy?.standardSuites ?? []).map((suite) => ({
                label: `${suite.displayName} (${suite.id})`,
                value: suite.id,
              }))}
            />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item name="defaultGmSuite" label="国密 S/MIME 默认套件" rules={[{ required: true, message: '请选择国密套件' }]}>
            <Select
              placeholder="请选择国密套件"
              options={(policy?.gmSuites ?? []).map((suite) => ({
                label: `${suite.displayName} (${suite.id})`,
                value: suite.id,
              }))}
            />
          </Form.Item>
        </Col>
      </Row>
      <Text type="secondary">
        保存后新发起的 S/MIME 加密会使用新的默认套件；已加密邮件不受影响。
      </Text>
      <div style={{ marginTop: 16 }}>
        <Button type="primary" htmlType="submit" disabled={!policy}>
          保存 S/MIME 套件策略
        </Button>
      </div>
    </Form>
  </Card>
);
