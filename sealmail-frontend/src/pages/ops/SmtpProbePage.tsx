import React, { useCallback, useState } from 'react';
import { Button, Card, Col, Form, Input, Row, Segmented, Space, Typography, message } from 'antd';
import { SettingOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { mailTestApi } from '../../api/client';
import { getApiErrorMessage } from '../../api/errors';
import { PageHeader, PageShell } from '../../components/Page';
import type { TestMailValues } from '../settings/settingsUtils';
import { splitRecipients } from '../settings/settingsUtils';

const { Text } = Typography;
type ProbeMode = 'ROUTE' | 'SYSTEM';

const SmtpProbePage: React.FC = () => {
  const [form] = Form.useForm<TestMailValues>();
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState('');
  const [mode, setMode] = useState<ProbeMode>('ROUTE');

  const handleProbeRoute = useCallback(async (values: TestMailValues) => {
    const recipients = splitRecipients(values.to);
    if (recipients.length === 0) {
      message.error('请输入收件人邮箱');
      return;
    }

    setLoading(true);
    try {
      const probeResult = await mailTestApi.probeRoute({
        from: values.from,
        to: recipients,
        subject: values.subject || 'SealMail 路由探测',
        content: values.content || 'probe',
      }) || '';
      setResult(probeResult);
      if (probeResult.includes('FAILED')) {
        message.warning('SMTP 探测完成，存在失败链路');
      } else {
        message.success('SMTP 探测通过');
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, 'SMTP 探测失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  const handleProbeSystem = useCallback(async () => {
    setLoading(true);
    try {
      const probeResult = await mailTestApi.testSmtpConfig() || '';
      setResult(probeResult);
      if (probeResult.includes('FAILED')) {
        message.warning('SMTP 探测完成，存在失败链路');
      } else {
        message.success('SMTP 探测通过');
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, 'SMTP 探测失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  const handlePrimaryProbe = useCallback(() => {
    if (mode === 'ROUTE') {
      form.submit();
      return;
    }
    void handleProbeSystem();
  }, [form, handleProbeSystem, mode]);

  return (
    <PageShell>
      <PageHeader
        title="SMTP 探测"
        description="按收件人域解析当前投递路由，探测 host + 传输配置的 SMTP/TLS 链路。"
        actions={(
          <Button type="primary" icon={<ThunderboltOutlined />} loading={loading} onClick={handlePrimaryProbe}>
            开始探测
          </Button>
        )}
      />
      <Space direction="vertical" size={16} className="full-width">
        <Card title="探测输入">
          <Space direction="vertical" size={12} className="full-width">
            <Segmented<ProbeMode>
              value={mode}
              onChange={setMode}
              options={[
                { label: '当前投递路由', value: 'ROUTE' },
                { label: '系统 SMTP 配置', value: 'SYSTEM', icon: <SettingOutlined /> },
              ]}
            />
            {mode === 'ROUTE' ? (
              <Form<TestMailValues>
                form={form}
                layout="vertical"
                onFinish={handleProbeRoute}
                initialValues={{
                  from: 'test@sealmail.local',
                  subject: 'SealMail 路由探测',
                  content: 'probe',
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
                <Form.Item name="subject" hidden>
                  <Input />
                </Form.Item>
                <Form.Item name="content" hidden>
                  <Input />
                </Form.Item>
              </Form>
            ) : (
              <Text type="secondary">系统配置探测用于检查 Postfix 或 Direct Relay 基础链路。</Text>
            )}
          </Space>
        </Card>
        <Card title="探测结果">
          {result ? (
            <pre className="settings-probe-result">{result}</pre>
          ) : (
            <div className="settings-empty-result">
              <Text>尚未执行探测。</Text>
            </div>
          )}
        </Card>
      </Space>
    </PageShell>
  );
};

export default SmtpProbePage;
