import React from 'react';
import { Button, Form, Input, InputNumber, Modal, Select, Space, Switch, Tag } from 'antd';
import type { FormInstance } from 'antd';
import type { DomainConfigFormValues } from './domainConfigUtils';
import { deliveryProfilePort, deliveryTransportProfiles, domainPattern } from './domainConfigUtils';

interface DomainConfigFormModalProps {
  form: FormInstance<DomainConfigFormValues>;
  mode: 'create' | 'edit';
  open: boolean;
  onCancel: () => void;
  onFinish: (values: DomainConfigFormValues) => void | Promise<void>;
}

const encryptionPolicyOptions = [
  { value: 'MANDATORY', label: '强制加密' },
  { value: 'ALLOW', label: '允许加密' },
  { value: 'NO_ENCRYPTION', label: '不加密' },
];

const algorithmOptions = [
  { value: 'AUTO', label: '自动选择' },
  { value: 'GM_ONLY', label: '仅国密' },
  { value: 'STANDARD_ONLY', label: '仅国际' },
];

const decryptionModeOptions = [
  { value: 'GATEWAY_TERMINATED', label: '网关代理解密' },
  { value: 'END_TO_END_PASSTHROUGH', label: '端到端透传' },
];

const DomainConfigFormModal: React.FC<DomainConfigFormModalProps> = ({
  form,
  mode,
  open,
  onCancel,
  onFinish,
}) => {
  const isCreate = mode === 'create';
  const localDomain = Form.useWatch('localDomain', form);
  const deliveryTransportProfile = Form.useWatch('deliveryTransportProfile', form);
  const deliveryPort = Form.useWatch('deliveryPort', form);
  const deliveryRouteDisabled = Boolean(localDomain);
  const defaultPort = deliveryProfilePort(deliveryTransportProfile);
  const resolvedPort = deliveryPort ?? defaultPort;

  return (
    <Modal
      title={isCreate ? '添加域名配置' : '编辑域名配置'}
      open={open}
      onCancel={onCancel}
      footer={null}
      width={640}
    >
      <Form form={form} layout="vertical" onFinish={onFinish}>
        {isCreate ? (
          <>
            <Form.Item
              name="domain"
              label="域名"
              rules={[
                { required: true, message: '请输入域名' },
                { type: 'string', pattern: domainPattern, message: '请输入有效的域名' },
              ]}
            >
              <Input placeholder="例如: example.com" />
            </Form.Item>
            <Form.Item name="localDomain" label="本地域名" valuePropName="checked">
              <Switch
                onChange={(checked) => {
                  form.setFieldValue('encryptionPolicy', checked ? 'MANDATORY' : 'ALLOW');
                  if (checked) {
                    form.setFieldsValue({ deliveryHost: undefined, deliveryPort: undefined });
                  }
                }}
              />
            </Form.Item>
          </>
        ) : null}

        <Form.Item
          name="encryptionPolicy"
          label="加密策略"
          rules={[{ required: true, message: '请选择加密策略' }]}
        >
          <Select placeholder="请选择加密策略" options={encryptionPolicyOptions} />
        </Form.Item>
        <Form.Item
          name="preferredAlgorithm"
          label="算法偏好"
          rules={[{ required: true, message: '请选择算法偏好' }]}
        >
          <Select placeholder="请选择算法偏好" options={algorithmOptions} />
        </Form.Item>
        <Form.Item name="signingEnabled" label="启用邮件签名" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item name="dkimEnabled" label="启用DKIM签名" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item
          name="decryptionMode"
          label="入站解密模式"
          rules={[{ required: true, message: '请选择入站解密模式' }]}
        >
          <Select placeholder="请选择入站解密模式" options={decryptionModeOptions} />
        </Form.Item>
        <Space size={12} align="start" className="full-width">
          <Form.Item
            name="deliveryHost"
            label="外部发送主机"
            extra="非本地域名可配置固定投递目标，留空则使用默认出站投递。"
            className="full-width"
            rules={[
              { max: 255, message: '主机长度不能超过 255 个字符' },
              { pattern: /^\S*$/, message: '主机不能包含空白字符' },
            ]}
          >
            <Input disabled={deliveryRouteDisabled} placeholder="例如: 10.0.0.12 或 smtp.example.com" />
          </Form.Item>
          <Form.Item
            name="deliveryTransportProfile"
            label="传输配置"
            rules={[
              ({ getFieldValue }) => ({
                validator: (_, value) => {
                  const host = getFieldValue('deliveryHost');
                  if (!host) {
                    return Promise.resolve();
                  }
                  return value ? Promise.resolve() : Promise.reject(new Error('请选择传输配置'));
                },
              }),
            ]}
          >
            <Select
              disabled={deliveryRouteDisabled}
              placeholder="请选择传输配置"
              style={{ width: 240 }}
              onChange={(profile) => {
                form.setFieldValue('deliveryPort', deliveryProfilePort(profile));
              }}
              options={deliveryTransportProfiles.map((profile) => ({
                value: profile.value,
                label: `${profile.label} (默认 ${profile.port})`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="deliveryPort"
            label="端口"
            extra="端口可按对端网关调整；传输配置决定是否明文、STARTTLS 或隐式 TLS。"
            rules={[
              ({ getFieldValue }) => ({
                validator: (_, value) => {
                  const host = getFieldValue('deliveryHost');
                  if (!host || value === undefined || value === null) {
                    return Promise.resolve();
                  }
                  return value >= 1 && value <= 65535
                    ? Promise.resolve()
                    : Promise.reject(new Error('端口必须在 1 到 65535 之间'));
                },
              }),
            ]}
          >
            <InputNumber
              disabled={deliveryRouteDisabled}
              min={1}
              max={65535}
              placeholder={`${defaultPort}`}
              style={{ width: 120 }}
            />
          </Form.Item>
          <Form.Item label="实际端口">
            <Tag color={deliveryRouteDisabled ? 'default' : 'processing'}>
              {deliveryRouteDisabled ? '-' : resolvedPort}
            </Tag>
          </Form.Item>
        </Space>
        <Form.Item name="active" label="启用配置" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item className="form-actions">
          <Space>
            <Button onClick={onCancel}>取消</Button>
            <Button type="primary" htmlType="submit">
              {isCreate ? '创建' : '保存'}
            </Button>
          </Space>
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default DomainConfigFormModal;
