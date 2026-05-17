import React, { useCallback, useState } from 'react';
import { Button, Card, Col, Form, Input, Row, message } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import { mailTestApi } from '../../api/client';
import { getApiErrorMessage } from '../../api/errors';
import { PageHeader, PageShell } from '../../components/Page';
import type { TestMailValues } from '../settings/settingsUtils';
import { splitRecipients } from '../settings/settingsUtils';

const { TextArea } = Input;

const ProtectedTestMailPage: React.FC = () => {
  const [form] = Form.useForm<TestMailValues>();
  const [loading, setLoading] = useState(false);

  const handleSendTest = useCallback(async (values: TestMailValues) => {
    const recipients = splitRecipients(values.to);
    if (recipients.length === 0) {
      message.error('请输入收件人邮箱');
      return;
    }

    setLoading(true);
    try {
      const response = await mailTestApi.sendEncrypted({
        from: values.from,
        to: recipients,
        subject: values.subject,
        content: values.content,
      });
      if (!response.data.success) {
        throw new Error(response.data.message || response.data.data);
      }
      message.success(response.data.data || '加密测试邮件已提交');
      form.resetFields();
    } catch (error) {
      message.error(getApiErrorMessage(error, '测试邮件发送失败'));
    } finally {
      setLoading(false);
    }
  }, [form]);

  return (
    <PageShell>
      <PageHeader
        title="受保护测试邮件"
        description="提交一封测试邮件，验证当前域名策略、证书绑定和投递链路。"
      />
      <Card title="邮件内容">
        <Form<TestMailValues>
          form={form}
          layout="vertical"
          onFinish={handleSendTest}
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
            <TextArea rows={6} />
          </Form.Item>
          <Button type="primary" icon={<SendOutlined />} htmlType="submit" loading={loading}>
            发送测试邮件
          </Button>
        </Form>
      </Card>
    </PageShell>
  );
};

export default ProtectedTestMailPage;
