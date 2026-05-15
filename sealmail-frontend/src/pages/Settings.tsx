import React, { useEffect, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Descriptions,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  CloudServerOutlined,
  FileProtectOutlined,
  LockOutlined,
  MailOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SendOutlined,
  ThunderboltOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import { mailTestApi, systemSettingsApi } from '../api/client';
import type { SystemSettings as SystemSettingsSnapshot } from '../types';

const { Title, Text } = Typography;
const { TextArea } = Input;
const { Option } = Select;

type SectionKey = 'runtime' | 'mail' | 'security' | 'tools';

interface TestMailValues {
  from: string;
  to: string;
  subject: string;
  content: string;
  preferredAlgorithm: 'AUTO' | 'GM_ONLY' | 'STANDARD_ONLY';
}

const sections: { key: SectionKey; label: string; icon: React.ReactNode }[] = [
  { key: 'runtime', label: '运行状态', icon: <CloudServerOutlined /> },
  { key: 'mail', label: '邮件链路', icon: <MailOutlined /> },
  { key: 'security', label: '安全能力', icon: <SafetyCertificateOutlined /> },
  { key: 'tools', label: '调试工具', icon: <ToolOutlined /> },
];

const getErrorMessage = (error: unknown, fallback: string) => {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  if (
    typeof error === 'object' &&
    error !== null &&
    'response' in error &&
    typeof (error as { response?: { data?: { message?: unknown } } }).response?.data?.message === 'string'
  ) {
    return (error as { response: { data: { message: string } } }).response.data.message;
  }
  return fallback;
};

const formatBytes = (bytes: number) => {
  if (!Number.isFinite(bytes)) return '-';
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(0)} MB`;
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${bytes} B`;
};

const booleanTag = (value: boolean, activeLabel = '启用', inactiveLabel = '关闭') => (
  <Tag color={value ? 'success' : 'default'} style={{ fontSize: 14, lineHeight: '24px', paddingInline: 8 }}>
    {value ? activeLabel : inactiveLabel}
  </Tag>
);

const configuredTag = (value: boolean) => (
  <Tag
    color={value ? 'success' : 'warning'}
    icon={value ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
    style={{ fontSize: 14, lineHeight: '24px', paddingInline: 8 }}
  >
    {value ? '已配置' : '未配置'}
  </Tag>
);

const splitRecipients = (value: string) =>
  value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);

const pageTextStyle: React.CSSProperties = {
  color: '#344054',
  fontSize: 14,
  lineHeight: '22px',
};

const summaryCardStyle: React.CSSProperties = {
  height: '100%',
  borderRadius: 8,
};

const summaryLabelStyle: React.CSSProperties = {
  ...pageTextStyle,
  color: '#667085',
  display: 'block',
  marginBottom: 8,
};

const summaryValueStyle: React.CSSProperties = {
  color: '#101828',
  fontSize: 18,
  fontWeight: 700,
  lineHeight: '26px',
  wordBreak: 'break-word',
};

const navButtonStyle = (active: boolean): React.CSSProperties => ({
  width: '100%',
  height: 42,
  justifyContent: 'flex-start',
  border: active ? '1px solid #1677ff' : '1px solid transparent',
  background: active ? '#e6f4ff' : 'transparent',
  color: active ? '#0958d9' : '#344054',
  fontWeight: active ? 700 : 500,
});

const descriptionStyles = {
  label: { width: 150, color: '#475467', fontSize: 14, fontWeight: 600 },
  content: { color: '#101828', fontSize: 14, lineHeight: '22px' },
};

const Settings: React.FC = () => {
  const [settings, setSettings] = useState<SystemSettingsSnapshot | null>(null);
  const [activeSection, setActiveSection] = useState<SectionKey>('runtime');
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [probeOpen, setProbeOpen] = useState(false);
  const [probeLoading, setProbeLoading] = useState(false);
  const [probeResult, setProbeResult] = useState('');
  const [testOpen, setTestOpen] = useState(false);
  const [testLoading, setTestLoading] = useState(false);
  const [testForm] = Form.useForm<TestMailValues>();

  useEffect(() => {
    let mounted = true;

    const loadInitialSettings = async () => {
      try {
        const response = await systemSettingsApi.get();
        if (!response.data.success) {
          throw new Error(response.data.message);
        }
        if (mounted) {
          setSettings(response.data.data);
        }
      } catch (error) {
        if (mounted) {
          message.error(getErrorMessage(error, '加载系统设置失败'));
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    };

    void loadInitialSettings();

    return () => {
      mounted = false;
    };
  }, []);

  const loadSettings = async () => {
    setRefreshing(true);
    try {
      const response = await systemSettingsApi.get();
      if (!response.data.success) {
        throw new Error(response.data.message);
      }
      setSettings(response.data.data);
    } catch (error) {
      message.error(getErrorMessage(error, '加载系统设置失败'));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const handleProbe = async () => {
    setProbeLoading(true);
    try {
      const response = await mailTestApi.testSmtpConfig();
      if (!response.data.success) {
        throw new Error(response.data.message);
      }
      const result = response.data.data || '';
      setProbeResult(result);
      if (result.includes('FAILED')) {
        message.warning('SMTP 探测完成，存在失败链路');
      } else {
        message.success('SMTP 探测通过');
      }
    } catch (error) {
      message.error(getErrorMessage(error, 'SMTP 探测失败'));
    } finally {
      setProbeLoading(false);
    }
  };

  const handleSendTest = async (values: TestMailValues) => {
    const recipients = splitRecipients(values.to);
    if (recipients.length === 0) {
      message.error('请输入收件人邮箱');
      return;
    }

    setTestLoading(true);
    try {
      const response = await mailTestApi.sendEncrypted({
        from: values.from,
        to: recipients,
        subject: values.subject,
        content: values.content,
        preferredAlgorithm: values.preferredAlgorithm,
      });
      if (!response.data.success) {
        throw new Error(response.data.message || response.data.data);
      }
      message.success(response.data.data || '加密测试邮件已提交');
      testForm.resetFields();
      setTestOpen(false);
    } catch (error) {
      message.error(getErrorMessage(error, '测试邮件发送失败'));
    } finally {
      setTestLoading(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 100 }}>
        <Spin size="large" />
      </div>
    );
  }

  const smtpEndpoint = `${settings?.smtpServer.bindAddress}:${settings?.smtpServer.port}`;
  const deliveryMode = settings?.delivery.mode === 'POSTFIX' ? 'Postfix' : 'SMTP 中继';
  const deliveryEndpoint = settings?.delivery.mode === 'POSTFIX'
    ? `${settings.delivery.postfix.host}:${settings.delivery.postfix.afterFilterPort} / ${settings.delivery.postfix.outboundPort}`
    : `${settings?.delivery.directRelay.host}:${settings?.delivery.directRelay.port}`;
  const activeProfiles = settings?.runtime.activeProfiles.length ? settings.runtime.activeProfiles : ['default'];

  const renderRuntime = () => (
    <Card title="运行状态" styles={{ body: { paddingTop: 12 } }}>
      <Descriptions column={1} bordered styles={descriptionStyles}>
        <Descriptions.Item label="应用名称">{settings?.runtime.applicationName}</Descriptions.Item>
        <Descriptions.Item label="运行 Profile">
          <Space size={4} wrap>
            {activeProfiles.map((profile) => (
              <Tag key={profile} style={{ fontSize: 14, lineHeight: '24px', paddingInline: 8 }}>
                {profile}
              </Tag>
            ))}
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="配置来源">{settings?.runtime.configSource}</Descriptions.Item>
        <Descriptions.Item label="配置修改">
          <Tag color={settings?.runtime.onlineEditingSupported ? 'success' : 'warning'} style={{ fontSize: 14, lineHeight: '24px', paddingInline: 8 }}>
            {settings?.runtime.onlineEditingSupported ? '支持在线保存' : '改配置文件后重启'}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="快照时间">
          {settings?.runtime.generatedAt ? new Date(settings.runtime.generatedAt).toLocaleString() : '-'}
        </Descriptions.Item>
      </Descriptions>
    </Card>
  );

  const renderMail = () => (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card title="SMTP 入口">
        <Descriptions column={1} bordered styles={descriptionStyles}>
          <Descriptions.Item label="监听地址">{settings?.smtpServer.bindAddress}</Descriptions.Item>
          <Descriptions.Item label="监听端口">{settings?.smtpServer.port}</Descriptions.Item>
          <Descriptions.Item label="最大连接">{settings?.smtpServer.maxConnections}</Descriptions.Item>
          <Descriptions.Item label="单封邮件上限">{formatBytes(settings?.smtpServer.maxMessageSizeBytes || 0)}</Descriptions.Item>
          <Descriptions.Item label="STARTTLS">{booleanTag(settings?.smtpServer.tls.startTlsEnabled || false)}</Descriptions.Item>
          <Descriptions.Item label="强制 TLS">{booleanTag(settings?.smtpServer.tls.tlsRequired || false)}</Descriptions.Item>
        </Descriptions>
      </Card>
      <Card title="投递链路">
        <Descriptions column={1} bordered styles={descriptionStyles}>
          <Descriptions.Item label="处理后发往">
            <Space wrap>
              <Tag color={settings?.delivery.mode === 'POSTFIX' ? 'processing' : 'blue'} style={{ fontSize: 14, lineHeight: '24px', paddingInline: 8 }}>
                {deliveryMode}
              </Tag>
              <Text style={pageTextStyle}>{deliveryEndpoint}</Text>
            </Space>
          </Descriptions.Item>
          {settings?.delivery.mode === 'POSTFIX' ? (
            <>
              <Descriptions.Item label="Postfix 主机">{settings.delivery.postfix.host}</Descriptions.Item>
              <Descriptions.Item label="回注端口">
                {settings.delivery.postfix.afterFilterPort} / {settings.delivery.postfix.outboundPort}
              </Descriptions.Item>
              <Descriptions.Item label="TLS">{booleanTag(settings.delivery.postfix.useTls)}</Descriptions.Item>
              <Descriptions.Item label="超时">{settings.delivery.postfix.timeoutMs} ms</Descriptions.Item>
              <Descriptions.Item label="Envelope From">{settings.delivery.postfix.envelopeFrom || '-'}</Descriptions.Item>
            </>
          ) : (
            <>
              <Descriptions.Item label="中继主机">
                {settings?.delivery.directRelay.host}:{settings?.delivery.directRelay.port}
              </Descriptions.Item>
              <Descriptions.Item label="TLS">{booleanTag(settings?.delivery.directRelay.useTls || false)}</Descriptions.Item>
              <Descriptions.Item label="认证">
                <Space wrap>
                  <Text style={pageTextStyle}>用户名</Text>
                  {configuredTag(settings?.delivery.directRelay.usernameConfigured || false)}
                  <Text style={pageTextStyle}>密码</Text>
                  {configuredTag(settings?.delivery.directRelay.passwordConfigured || false)}
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="超时">{settings?.delivery.directRelay.timeoutMs} ms</Descriptions.Item>
            </>
          )}
        </Descriptions>
      </Card>
    </Space>
  );

  const renderSecurity = () => (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card title="TLS 与证书校验">
        <Descriptions column={1} bordered styles={descriptionStyles}>
          <Descriptions.Item label="Keystore">{configuredTag(settings?.smtpServer.tls.keystoreConfigured || false)}</Descriptions.Item>
          <Descriptions.Item label="PEM 证书">{configuredTag(settings?.smtpServer.tls.pemConfigured || false)}</Descriptions.Item>
          <Descriptions.Item label="Key Alias">{settings?.smtpServer.tls.keyAlias || '-'}</Descriptions.Item>
          <Descriptions.Item label="CRL 检查">{booleanTag(settings?.certificateValidation.crlEnabled || false)}</Descriptions.Item>
          <Descriptions.Item label="OCSP 检查">{booleanTag(settings?.certificateValidation.ocspEnabled || false)}</Descriptions.Item>
          <Descriptions.Item label="OCSP 超时">{settings?.certificateValidation.ocspTimeoutMs ?? '-'} ms</Descriptions.Item>
          <Descriptions.Item label="CRL 地址">
            <Text copyable style={pageTextStyle}>{settings?.internalCa.crlBaseUrl}</Text>
          </Descriptions.Item>
        </Descriptions>
      </Card>
      <Card title="算法能力">
        <Space size={8} wrap>
          {settings?.cryptoCapabilities.flatMap((capability) =>
            capability.algorithms.map((algorithm) => (
              <Tag
                key={`${capability.category}-${algorithm}`}
                color={algorithm.startsWith('SM') ? 'error' : 'processing'}
                icon={capability.category === '内容加密' ? <LockOutlined /> : <FileProtectOutlined />}
                style={{ fontSize: 14, lineHeight: '24px', paddingInline: 8 }}
              >
                {capability.category}: {algorithm}
              </Tag>
            )),
          )}
        </Space>
      </Card>
    </Space>
  );

  const renderTools = () => (
    <Card title="调试工具">
      <Row gutter={[16, 16]}>
        <Col xs={24} md={12}>
          <Card type="inner" title="SMTP 探测">
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <Text style={pageTextStyle}>测试当前投递链路是否能建立 SMTP 连接。</Text>
              <Button icon={<ThunderboltOutlined />} onClick={() => setProbeOpen(true)}>
                打开探测
              </Button>
            </Space>
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card type="inner" title="加密测试邮件">
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <Text style={pageTextStyle}>提交一封测试邮件，验证 S/MIME 签名、加密和投递。</Text>
              <Button type="primary" icon={<SendOutlined />} onClick={() => setTestOpen(true)}>
                发送测试
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>
    </Card>
  );

  const renderActiveSection = () => {
    switch (activeSection) {
      case 'runtime':
        return renderRuntime();
      case 'mail':
        return renderMail();
      case 'security':
        return renderSecurity();
      case 'tools':
        return renderTools();
      default:
        return renderRuntime();
    }
  };

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
        <div>
          <Title level={3} style={{ margin: 0 }}>
            系统设置
          </Title>
          <div style={{ ...pageTextStyle, marginTop: 4 }}>当前运行配置与调试入口</div>
        </div>
        <Button icon={<ReloadOutlined />} onClick={loadSettings} loading={refreshing}>
          刷新
        </Button>
      </div>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} md={12} xl={6}>
          <Card style={summaryCardStyle}>
            <span style={summaryLabelStyle}>收件地址</span>
            <div style={summaryValueStyle}>{smtpEndpoint}</div>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card style={summaryCardStyle}>
            <span style={summaryLabelStyle}>投递目标</span>
            <div style={summaryValueStyle}>{deliveryMode}</div>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card style={summaryCardStyle}>
            <span style={summaryLabelStyle}>单封邮件上限</span>
            <div style={summaryValueStyle}>{formatBytes(settings?.smtpServer.maxMessageSizeBytes || 0)}</div>
          </Card>
        </Col>
        <Col xs={24} md={12} xl={6}>
          <Card style={summaryCardStyle}>
            <span style={summaryLabelStyle}>配置方式</span>
            <div style={summaryValueStyle}>{settings?.runtime.onlineEditingSupported ? '在线保存' : '重启生效'}</div>
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={5}>
          <Card title="设置分类" styles={{ body: { padding: 12 } }}>
            <Space direction="vertical" size={8} style={{ width: '100%' }}>
              {sections.map((section) => (
                <Button
                  key={section.key}
                  type="text"
                  icon={section.icon}
                  style={navButtonStyle(activeSection === section.key)}
                  onClick={() => setActiveSection(section.key)}
                >
                  {section.label}
                </Button>
              ))}
            </Space>
          </Card>
        </Col>
        <Col xs={24} lg={19}>
          {renderActiveSection()}
        </Col>
      </Row>

      <Modal
        title="SMTP 探测"
        open={probeOpen}
        onCancel={() => setProbeOpen(false)}
        footer={[
          <Button key="close" onClick={() => setProbeOpen(false)}>
            关闭
          </Button>,
          <Button key="probe" type="primary" icon={<ThunderboltOutlined />} loading={probeLoading} onClick={handleProbe}>
            开始探测
          </Button>,
        ]}
        width={720}
      >
        {probeResult ? (
          <pre
            style={{
              margin: 0,
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
              fontSize: 14,
              lineHeight: '22px',
              color: '#101828',
              minHeight: 160,
            }}
          >
            {probeResult}
          </pre>
        ) : (
          <div style={{ minHeight: 160, display: 'flex', alignItems: 'center' }}>
            <Text style={pageTextStyle}>尚未执行探测。</Text>
          </div>
        )}
      </Modal>

      <Modal
        title="发送加密测试邮件"
        open={testOpen}
        onCancel={() => setTestOpen(false)}
        onOk={() => testForm.submit()}
        confirmLoading={testLoading}
        okText="发送"
        width={720}
      >
        <Form<TestMailValues>
          form={testForm}
          layout="vertical"
          onFinish={handleSendTest}
          initialValues={{
            from: 'test@sealmail.local',
            subject: 'SealMail 加密测试',
            content: '这是一封用于测试 S/MIME 加密和签名功能的邮件。',
            preferredAlgorithm: 'AUTO',
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

          <Row gutter={12}>
            <Col xs={24} md={12}>
              <Form.Item
                name="subject"
                label="主题"
                rules={[{ required: true, message: '请输入邮件主题' }]}
              >
                <Input />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item name="preferredAlgorithm" label="算法偏好">
                <Select>
                  <Option value="AUTO">自动选择</Option>
                  <Option value="GM_ONLY">国密优先</Option>
                  <Option value="STANDARD_ONLY">国际优先</Option>
                </Select>
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            name="content"
            label="内容"
            rules={[{ required: true, message: '请输入邮件内容' }]}
          >
            <TextArea rows={4} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default Settings;
