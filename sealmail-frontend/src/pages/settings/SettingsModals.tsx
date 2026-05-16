import React from 'react';
import { Button, Col, Form, Input, Modal, Row, Typography } from 'antd';
import type { FormInstance } from 'antd';
import { ThunderboltOutlined } from '@ant-design/icons';
import type { TestMailValues } from './settingsUtils';
import { splitRecipients } from './settingsUtils';

const { Text } = Typography;
const { TextArea } = Input;

interface ProbeModalProps {
  loading: boolean;
  open: boolean;
  result: string;
  onCancel: () => void;
  onProbe: () => void | Promise<void>;
}

interface TestMailModalProps {
  form: FormInstance<TestMailValues>;
  loading: boolean;
  open: boolean;
  onCancel: () => void;
  onFinish: (values: TestMailValues) => void | Promise<void>;
}

export const ProbeModal: React.FC<ProbeModalProps> = ({
  loading,
  open,
  result,
  onCancel,
  onProbe,
}) => (
  <Modal
    title="SMTP 探测"
    open={open}
    onCancel={onCancel}
    footer={[
      <Button key="close" onClick={onCancel}>
        关闭
      </Button>,
      <Button key="probe" type="primary" icon={<ThunderboltOutlined />} loading={loading} onClick={onProbe}>
        开始探测
      </Button>,
    ]}
    width={720}
  >
    {result ? (
      <pre className="settings-probe-result">{result}</pre>
    ) : (
      <div className="settings-empty-result">
        <Text>尚未执行探测。</Text>
      </div>
    )}
  </Modal>
);

export const TestMailModal: React.FC<TestMailModalProps> = ({
  form,
  loading,
  open,
  onCancel,
  onFinish,
}) => (
  <Modal
    title="发送受保护测试邮件"
    open={open}
    onCancel={onCancel}
    onOk={() => form.submit()}
    confirmLoading={loading}
    okText="发送"
    width={720}
  >
    <Form<TestMailValues>
      form={form}
      layout="vertical"
      onFinish={onFinish}
      initialValues={{
        from: 'test@sealmail.local',
        subject: 'SealMail 加密测试',
        content: '这是一封用于验证当前域名策略、证书绑定和投递链路的测试邮件。',
      }}
    >
      <Row gutter={12}>
        <Col xs={24} md={12}>
          <Form.Item
            name="from"
            label="发件人"
            rules={[
              { required: true, message: '请输入发件人邮箱' },
              { type: 'email', message: '请输入有效邮箱' },
            ]}
          >
            <Input placeholder="test@sealmail.local" />
          </Form.Item>
        </Col>
        <Col xs={24} md={12}>
          <Form.Item
            name="to"
            label="收件人"
            rules={[
              { required: true, message: '请输入收件人邮箱' },
              {
                validator: (_, value: string) => {
                  const recipients = splitRecipients(value || '');
                  const valid = recipients.length > 0 && recipients.every((item) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(item));
                  return valid ? Promise.resolve() : Promise.reject(new Error('请输入有效邮箱，多个地址用逗号分隔'));
                },
              },
            ]}
          >
            <Input placeholder="recipient@example.com" />
          </Form.Item>
        </Col>
      </Row>

      <Form.Item name="subject" label="主题" rules={[{ required: true, message: '请输入邮件主题' }]}>
        <Input />
      </Form.Item>
      <Form.Item name="content" label="内容" rules={[{ required: true, message: '请输入邮件内容' }]}>
        <TextArea rows={4} />
      </Form.Item>
    </Form>
  </Modal>
);
