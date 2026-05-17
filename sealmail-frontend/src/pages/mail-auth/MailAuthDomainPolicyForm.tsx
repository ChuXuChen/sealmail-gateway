import React from 'react';
import {
  Button,
  Card,
  Col,
  Form,
  Input,
  InputNumber,
  Row,
  Select,
  Space,
  Switch,
  Tabs,
} from 'antd';
import type { FormInstance } from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import type { MailAuthDomainFormValues } from './mailAuthUtils';
import {
  alignmentOptions,
  dkimHeaderOptions,
  dmarcPolicyOptions,
  spfAllPolicyOptions,
} from './mailAuthUtils';

interface MailAuthDomainPolicyFormProps {
  form: FormInstance<MailAuthDomainFormValues>;
  loading: boolean;
  selectedDomain: string;
  onSubmit: (values: MailAuthDomainFormValues) => void | boolean | Promise<void | boolean>;
}

const renderListInput = (fieldName: keyof MailAuthDomainFormValues, placeholder: string) => (
  <Form.List name={fieldName}>
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

const MailAuthDomainPolicyForm: React.FC<MailAuthDomainPolicyFormProps> = ({
  form,
  loading,
  selectedDomain,
  onSubmit,
}) => (
  <Card title={selectedDomain ? `域名策略：${selectedDomain}` : '域名策略'} className="mail-auth-section">
    <Form form={form} layout="vertical" disabled={loading || !selectedDomain} onFinish={onSubmit}>
      <Tabs
        items={[
          {
            key: 'domain',
            label: '总开关',
            children: (
              <Row gutter={16}>
                <Col xs={24} md={8}>
                  <Form.Item name="enabled" label="启用域名策略" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Col>
              </Row>
            ),
          },
          {
            key: 'dkim',
            label: 'DKIM',
            children: (
              <Row gutter={16}>
                <Col xs={24} md={8}>
                  <Form.Item name="dkimSigningEnabled" label="启用 DKIM 签名" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item
                    name="dkimSelector"
                    label="Selector"
                    rules={[{ required: true, message: '请输入 DKIM selector' }]}
                  >
                    <Input placeholder="sealmail" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name="dkimSignedHeaders" label="签名头">
                    <Select mode="tags" options={dkimHeaderOptions} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="dkimKeySecretRef" label="私钥 Secret 引用">
                    <Input placeholder="env:SEALMAIL_DKIM_PRIVATE_KEY" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="dkimKeyPath" label="私钥路径">
                    <Input placeholder="/run/secrets/dkim.pem" />
                  </Form.Item>
                </Col>
              </Row>
            ),
          },
          {
            key: 'spf',
            label: 'SPF',
            children: (
              <Row gutter={16}>
                <Col xs={24} md={8}>
                  <Form.Item name="spfPublishEnabled" label="发布 SPF" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name="spfUseA" label="包含 A 记录" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name="spfUseMx" label="包含 MX 记录" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name="spfAllPolicy" label="All 策略">
                    <Select options={spfAllPolicyOptions} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item label="IPv4">
                    {renderListInput('spfIp4', '203.0.113.10/32')}
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item label="IPv6">
                    {renderListInput('spfIp6', '2001:db8::/32')}
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item label="Include">
                    {renderListInput('spfIncludes', 'spf.example.net')}
                  </Form.Item>
                </Col>
              </Row>
            ),
          },
          {
            key: 'dmarc',
            label: 'DMARC',
            children: (
              <Row gutter={16}>
                <Col xs={24} md={8}>
                  <Form.Item name="dmarcPublishEnabled" label="发布 DMARC" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name="dmarcPolicy" label="主域策略">
                    <Select options={dmarcPolicyOptions} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name="dmarcSubdomainPolicy" label="子域策略">
                    <Select options={dmarcPolicyOptions} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name="dmarcAdkim" label="DKIM 对齐">
                    <Select options={alignmentOptions} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name="dmarcAspf" label="SPF 对齐">
                    <Select options={alignmentOptions} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={8}>
                  <Form.Item name="dmarcPct" label="生效比例">
                    <InputNumber min={0} max={100} className="full-width" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="dmarcRua" label="RUA">
                    <Input placeholder="mailto:dmarc-aggregate@example.com" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="dmarcRuf" label="RUF">
                    <Input placeholder="mailto:dmarc-failure@example.com" />
                  </Form.Item>
                </Col>
              </Row>
            ),
          },
        ]}
      />
      <Form.Item className="form-actions">
        <Button type="primary" htmlType="submit" loading={loading} disabled={!selectedDomain}>
          保存域名策略
        </Button>
      </Form.Item>
    </Form>
  </Card>
);

export default MailAuthDomainPolicyForm;
