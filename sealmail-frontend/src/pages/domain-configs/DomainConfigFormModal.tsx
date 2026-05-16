import React from 'react';
import { Button, Form, Input, Modal, Select, Space, Switch } from 'antd';
import type { FormInstance } from 'antd';
import type { DomainConfigFormValues } from './domainConfigUtils';
import { domainPattern } from './domainConfigUtils';

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
  { value: 'GM_ONLY', label: '国密优先' },
  { value: 'STANDARD_ONLY', label: '国际优先' },
];

const DomainConfigFormModal: React.FC<DomainConfigFormModalProps> = ({
  form,
  mode,
  open,
  onCancel,
  onFinish,
}) => {
  const isCreate = mode === 'create';

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
