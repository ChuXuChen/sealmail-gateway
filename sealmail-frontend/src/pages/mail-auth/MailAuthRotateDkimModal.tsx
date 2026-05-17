import React from 'react';
import { Form, Input, Modal, Select } from 'antd';
import type { FormInstance } from 'antd';
import type { RotateDkimFormValues } from './mailAuthUtils';
import { dkimHeaderOptions } from './mailAuthUtils';

interface MailAuthRotateDkimModalProps {
  form: FormInstance<RotateDkimFormValues>;
  loading: boolean;
  open: boolean;
  onCancel: () => void;
  onSubmit: (values: RotateDkimFormValues) => void | Promise<void>;
}

const MailAuthRotateDkimModal: React.FC<MailAuthRotateDkimModalProps> = ({
  form,
  loading,
  open,
  onCancel,
  onSubmit,
}) => (
  <Modal
    title="轮换 DKIM Selector"
    open={open}
    onCancel={onCancel}
    onOk={() => form.submit()}
    confirmLoading={loading}
    destroyOnHidden
  >
    <Form form={form} layout="vertical" onFinish={onSubmit}>
      <Form.Item
        name="selector"
        label="新 Selector"
        rules={[{ required: true, message: '请输入新的 DKIM selector' }]}
      >
        <Input placeholder="sealmail2026" />
      </Form.Item>
      <Form.Item name="keySecretRef" label="新私钥 Secret 引用">
        <Input placeholder="env:SEALMAIL_DKIM_PRIVATE_KEY_NEXT" />
      </Form.Item>
      <Form.Item name="keyPath" label="新私钥路径">
        <Input placeholder="/run/secrets/dkim-next.pem" />
      </Form.Item>
      <Form.Item name="signedHeaders" label="签名头">
        <Select mode="tags" options={dkimHeaderOptions} />
      </Form.Item>
    </Form>
  </Modal>
);

export default MailAuthRotateDkimModal;
