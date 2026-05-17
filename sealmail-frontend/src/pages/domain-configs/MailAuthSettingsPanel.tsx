import React from 'react';
import {
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Switch,
  Tabs,
} from 'antd';
import type { FormInstance } from 'antd';
import { CopyOutlined, MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import type { DnsRecord, DomainConfig, MailAuthConfig } from '../../types';
import type { MailAuthFormValues } from './domainConfigUtils';

interface MailAuthSettingsPanelProps {
  dnsDomain: string;
  dnsRecords: DnsRecord[];
  domains: DomainConfig[];
  form: FormInstance<MailAuthFormValues>;
  loading: boolean;
  onCopyText: (value: string) => void | Promise<void>;
  onLoadDnsRecords: (domain: string) => void | Promise<void>;
  onSubmit: (values: MailAuthFormValues) => void | Promise<void>;
}

const dkimHeaderOptions = [
  'from',
  'to',
  'subject',
  'date',
  'message-id',
  'mime-version',
  'content-type',
].map((value) => ({ value, label: value }));

const spfAllPolicyOptions = [
  { value: '-all', label: '-all' },
  { value: '~all', label: '~all' },
  { value: '?all', label: '?all' },
];

const alignmentOptions = [
  { value: 'r', label: 'r' },
  { value: 's', label: 's' },
];

const renderListInput = (fieldName: keyof MailAuthConfig, placeholder: string) => (
  <Form.List name={fieldName as string}>
    {(fields, { add, remove }) => (
      <Space direction="vertical" className="full-width">
        {fields.map((field) => (
          <Space key={field.key} align="baseline" className="form-list-row">
            <Form.Item {...field} className="form-list-row__item">
              <Input placeholder={placeholder} />
            </Form.Item>
            <Button type="text" icon={<MinusCircleOutlined />} onClick={() => remove(field.name)} />
          </Space>
        ))}
        <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
          添加
        </Button>
      </Space>
    )}
  </Form.List>
);

const MailAuthSettingsPanel: React.FC<MailAuthSettingsPanelProps> = ({
  dnsDomain,
  dnsRecords,
  domains,
  form,
  loading,
  onCopyText,
  onLoadDnsRecords,
  onSubmit,
}) => {
  const renderDnsRecords = (type?: string) => {
    const records = dnsRecords.filter((record) => !type || record.type === type);

    return (
      <Space direction="vertical" className="full-width" size="middle">
        <Space.Compact className="full-width">
          <Select
            showSearch
            value={dnsDomain || undefined}
            placeholder="选择域名"
            onChange={(value) => onLoadDnsRecords(value)}
            className="dns-domain-select"
            options={domains.map((item) => ({
              value: item.domain,
              label: item.domain,
            }))}
          />
          <Button onClick={() => dnsDomain && onLoadDnsRecords(dnsDomain)}>刷新</Button>
        </Space.Compact>
        {records.map((record) => (
          <Descriptions key={`${record.type}-${record.name}`} bordered column={1} size="small">
            <Descriptions.Item label={`${record.type}主机名`}>
              <Space>
                <span>{record.name}</span>
                <Button
                  type="text"
                  size="small"
                  icon={<CopyOutlined />}
                  onClick={() => onCopyText(record.name)}
                />
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label={`${record.type} TXT`}>
              <Space direction="vertical" className="full-width">
                <Input.TextArea value={record.value} autoSize readOnly disabled={!record.available} />
                {record.available ? (
                  <Button icon={<CopyOutlined />} onClick={() => onCopyText(record.value)}>
                    复制
                  </Button>
                ) : null}
              </Space>
            </Descriptions.Item>
          </Descriptions>
        ))}
      </Space>
    );
  };

  return (
    <Card className="mail-auth-card">
      <Form form={form} layout="vertical" onFinish={onSubmit} disabled={loading}>
        <Tabs
          items={[
            {
              key: 'runtime',
              label: '运行开关',
              children: (
                <>
                  <Form.Item name="enabled" label="邮件认证" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  <Form.Item name="dkimEnabled" label="DKIM" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  <Form.Item name="spfEnabled" label="SPF" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  <Form.Item name="dmarcEnabled" label="DMARC" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  <Form.Item name="skipPrivateRelay" label="跳过内网中继SPF" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  <Form.Item
                    name="authservId"
                    label="认证服务标识"
                    rules={[{ required: true, message: '请输入认证服务标识' }]}
                  >
                    <Input />
                  </Form.Item>
                </>
              ),
            },
            {
              key: 'dkim',
              label: 'DKIM',
              children: (
                <>
                  <Form.Item
                    name="dkimSelector"
                    label="Selector"
                    rules={[{ required: true, message: '请输入Selector' }]}
                  >
                    <Input />
                  </Form.Item>
                  <Form.Item name="dkimSignedHeaders" label="签名头">
                    <Select mode="tags" options={dkimHeaderOptions} />
                  </Form.Item>
                  <Form.Item name="dkimPrivateKeyPath" label="私钥路径">
                    <Input />
                  </Form.Item>
                  <Form.Item name="dkimPrivateKeySecretRef" label="私钥 Secret 引用">
                    <Input placeholder="env:SEALMAIL_DKIM_PRIVATE_KEY 或 file:/run/secrets/dkim.pem" />
                  </Form.Item>
                  <Form.Item name="clearDkimPrivateKeySecretRef" label="清空 Secret 引用" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  {renderDnsRecords('DKIM')}
                </>
              ),
            },
            {
              key: 'spf',
              label: 'SPF',
              children: (
                <>
                  <Form.Item name="spfMaxDnsLookups" label="DNS查询上限">
                    <InputNumber min={0} max={50} className="full-width" />
                  </Form.Item>
                  <Form.Item name="spfUseA" label="A" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  <Form.Item name="spfUseMx" label="MX" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  <Form.Item label="IPv4">{renderListInput('spfIp4', '192.0.2.10')}</Form.Item>
                  <Form.Item label="IPv6">{renderListInput('spfIp6', '2001:db8::1')}</Form.Item>
                  <Form.Item label="Include">{renderListInput('spfIncludes', 'example.net')}</Form.Item>
                  <Form.Item name="spfAllPolicy" label="All策略">
                    <Select options={spfAllPolicyOptions} />
                  </Form.Item>
                  {renderDnsRecords('SPF')}
                </>
              ),
            },
            {
              key: 'dmarc',
              label: 'DMARC',
              children: (
                <>
                  <Form.Item name="dmarcPolicy" label="策略">
                    <Select
                      options={[
                        { value: 'none', label: 'none' },
                        { value: 'quarantine', label: 'quarantine' },
                        { value: 'reject', label: 'reject' },
                      ]}
                    />
                  </Form.Item>
                  <Form.Item name="dmarcAdkim" label="DKIM对齐">
                    <Select options={alignmentOptions} />
                  </Form.Item>
                  <Form.Item name="dmarcAspf" label="SPF对齐">
                    <Select options={alignmentOptions} />
                  </Form.Item>
                  <Form.Item name="dmarcPct" label="生效比例">
                    <InputNumber min={0} max={100} className="full-width" />
                  </Form.Item>
                  <Form.Item name="dmarcRua" label="RUA">
                    <Input />
                  </Form.Item>
                  <Form.Item name="dmarcRuf" label="RUF">
                    <Input />
                  </Form.Item>
                  <Form.Item name="dmarcFailureAction" label="失败处理">
                    <Select
                      options={[
                        { value: 'APPLY_POLICY', label: '按策略处理' },
                        { value: 'LOG_ONLY', label: '仅记录' },
                      ]}
                    />
                  </Form.Item>
                  {renderDnsRecords('DMARC')}
                </>
              ),
            },
          ]}
        />
        <Form.Item className="form-actions">
          <Button type="primary" htmlType="submit" loading={loading}>
            保存邮件认证配置
          </Button>
        </Form.Item>
      </Form>
    </Card>
  );
};

export default MailAuthSettingsPanel;
