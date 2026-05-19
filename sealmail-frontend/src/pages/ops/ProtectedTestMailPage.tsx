import React, { useCallback, useState } from 'react';
import { Alert, Button, Card, Col, Form, Input, Row, Segmented, Space, message } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import { mailTestApi } from '../../api/client';
import { getApiErrorMessage } from '../../api/errors';
import { PageHeader, PageShell } from '../../components/Page';
import type { TestMailValues } from '../settings/settingsUtils';
import { splitRecipients } from '../settings/settingsUtils';

const { TextArea } = Input;
type TestMode = 'CONFIGURED' | 'PROTECTED';

const ProtectedTestMailPage: React.FC = () => {
  const [form] = Form.useForm<TestMailValues>();
  const [loading, setLoading] = useState(false);
  const [mode, setMode] = useState<TestMode>('CONFIGURED');

  const handleSendTest = useCallback(async (values: TestMailValues) => {
    const recipients = splitRecipients(values.to);
    if (recipients.length === 0) {
      message.error('请输入收件人邮箱');
      return;
    }

    setLoading(true);
    try {
      const payload = {
        from: values.from,
        to: recipients,
        subject: values.subject,
        content: values.content,
      };
      const result = mode === 'PROTECTED'
        ? await mailTestApi.sendEncrypted(payload)
        : await mailTestApi.send(payload);
      message.success(result || '测试邮件已提交');
      form.resetFields();
    } catch (error) {
      message.error(getApiErrorMessage(error, '测试邮件发送失败'));
    } finally {
      setLoading(false);
    }
  }, [form, mode]);

  return (
    <PageShell>
      <PageHeader
        title="测试邮件"
        description="默认按当前域名配置发送；高级模式会强制 S/MIME 签名和加密。"
      />
      <Card title="邮件内容">
        <Space direction="vertical" size={12} className="full-width">
          <Segmented<TestMode>
            value={mode}
            onChange={setMode}
            options={[
              { label: '按当前配置发送测试邮件', value: 'CONFIGURED' },
              { label: '强制 S/MIME', value: 'PROTECTED' },
            ]}
          />
          {mode === 'PROTECTED' ? (
            <Alert type="warning" showIcon message="高级入口会强制 S/MIME 签名和加密。" />
          ) : null}
          <Form<TestMailValues>
            form={form}
            layout="vertical"
            onFinish={handleSendTest}
            initialValues={{
              from: 'test@sealmail.local',
              subject: 'SealMail 测试邮件',
              content: '这是一封用于验证当前域名策略和投递链路的测试邮件。',
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
              <TextArea rows={6} />
            </Form.Item>
            <Button type="primary" icon={<SendOutlined />} htmlType="submit" loading={loading}>
              {mode === 'PROTECTED' ? '发送受保护测试邮件' : '按当前配置发送测试邮件'}
            </Button>
          </Form>
        </Space>
      </Card>
    </PageShell>
  );
};

export default ProtectedTestMailPage;
