import React from 'react';
import { Form, Input, InputNumber, Modal, Select, Switch, Typography } from 'antd';
import type { FormInstance } from 'antd';
import type { Certificate } from '../../types';
import type { ImportCertificateValues, IssueByCaValues, SelfSignedCertificateValues } from './certificateUtils';
import { certificateDisplayName } from './certificateUtils';

const { TextArea } = Input;

interface ImportCertificateModalProps {
  form: FormInstance<ImportCertificateValues>;
  open: boolean;
  onCancel: () => void;
  onFinish: (values: ImportCertificateValues) => void | Promise<void>;
}

interface SelfSignedCertificateModalProps {
  form: FormInstance<SelfSignedCertificateValues>;
  issuing: boolean;
  open: boolean;
  onCancel: () => void;
  onFinish: (values: SelfSignedCertificateValues) => void | Promise<void>;
}

interface IssueByCaModalProps {
  caCandidates: Certificate[];
  form: FormInstance<IssueByCaValues>;
  issuing: boolean;
  open: boolean;
  onCaChange?: (certificateId: string) => void;
  onCancel: () => void;
  onFinish: (values: IssueByCaValues) => void | Promise<void>;
}

const algorithmOptions = [
  { value: 'RSA', label: 'RSA 2048' },
  { value: 'SM2', label: 'SM2' },
];

const ownerEmailRules = [
  { required: true, message: '请输入所有者邮箱' },
  { type: 'email' as const, message: '请输入有效邮箱' },
];

export const ImportCertificateModal: React.FC<ImportCertificateModalProps> = ({
  form,
  open,
  onCancel,
  onFinish,
}) => (
  <Modal
    title="导入证书"
    open={open}
    onCancel={onCancel}
    onOk={() => form.submit()}
    okText="导入"
    cancelText="取消"
    width={600}
  >
    <Form form={form} onFinish={onFinish} layout="vertical">
      <Form.Item name="ownerEmail" label="所有者邮箱" rules={[{ required: true, message: '请输入所有者邮箱' }]}>
        <Input placeholder="email@example.com" />
      </Form.Item>
      <Form.Item name="alias" label="证书别名">
        <Input placeholder="可选，便于识别" />
      </Form.Item>
      <Form.Item name="pemData" label="PEM 格式证书内容" rules={[{ required: true, message: '请输入 PEM 格式证书' }]}>
        <TextArea rows={8} placeholder="-----BEGIN CERTIFICATE-----&#10;...&#10;-----END CERTIFICATE-----" />
      </Form.Item>
      <Form.Item name="privateKeyData" label="关联私钥（可选）">
        <TextArea rows={6} placeholder="-----BEGIN PRIVATE KEY-----&#10;...&#10;-----END PRIVATE KEY-----" />
      </Form.Item>
      <Form.Item name="trusted" label="标记为信任" valuePropName="checked">
        <Switch />
      </Form.Item>
    </Form>
  </Modal>
);

export const SelfSignedCertificateModal: React.FC<SelfSignedCertificateModalProps> = ({
  form,
  issuing,
  open,
  onCancel,
  onFinish,
}) => (
  <Modal
    title="生成自签名证书"
    open={open}
    onCancel={onCancel}
    onOk={() => form.submit()}
    confirmLoading={issuing}
    okText="生成"
    cancelText="取消"
    width={600}
  >
    <Form form={form} onFinish={onFinish} layout="vertical">
      <Form.Item name="ownerEmail" label="所有者邮箱" rules={ownerEmailRules}>
        <Input placeholder="email@example.com" />
      </Form.Item>
      <Form.Item name="algorithm" label="算法" rules={[{ required: true }]}>
        <Select options={algorithmOptions} />
      </Form.Item>
      <Form.Item name="subjectDn" label="Subject DN（可选）">
        <Input placeholder="留空则使用 CN=邮箱, O=SealMail, C=CN" />
      </Form.Item>
      <Form.Item name="alias" label="别名（可选）">
        <Input placeholder="便于识别的名字" />
      </Form.Item>
      <Form.Item name="validityDays" label="有效期（天）" rules={[{ required: true }]}>
        <InputNumber min={1} max={3650} className="full-width" />
      </Form.Item>
      <Form.Item name="trusted" label="标记为信任" valuePropName="checked">
        <Switch />
      </Form.Item>
      <Typography.Paragraph type="secondary" className="form-note">
        后端将自动生成密钥对，与证书一并保存（私钥仅服务端保留）。
      </Typography.Paragraph>
    </Form>
  </Modal>
);

export const IssueByCaModal: React.FC<IssueByCaModalProps> = ({
  caCandidates,
  form,
  issuing,
  open,
  onCaChange,
  onCancel,
  onFinish,
}) => (
  <Modal
    title="用 CA 证书签发新证书"
    open={open}
    onCancel={onCancel}
    onOk={() => form.submit()}
    confirmLoading={issuing}
    okText="签发"
    cancelText="取消"
    width={600}
  >
    <Form form={form} onFinish={onFinish} layout="vertical">
      <Form.Item
        name="intermediateCaId"
        label="Intermediate CA"
        rules={[{ required: true, message: '请选择 Intermediate CA' }]}
        extra="只列出 pathLen=0、带私钥且未吊销的 Intermediate CA"
      >
        <Select
          placeholder="请选择 Intermediate CA"
          onChange={onCaChange}
          options={caCandidates.map((candidate) => ({
            value: candidate.id,
            label: `${certificateDisplayName(candidate)} (${candidate.algorithm || '?'}, ${candidate.id.substring(0, 12)}...)`,
          }))}
        />
      </Form.Item>
      <Form.Item name="ownerEmail" label="新证书所有者邮箱" rules={ownerEmailRules}>
        <Input placeholder="user@example.com" />
      </Form.Item>
      <Form.Item name="algorithm" label="新证书算法" rules={[{ required: true }]}>
        <Select options={algorithmOptions} disabled />
      </Form.Item>
      <Form.Item name="subjectDn" label="Subject DN（可选）">
        <Input placeholder="留空则使用 CN=邮箱, O=SealMail, C=CN" />
      </Form.Item>
      <Form.Item name="alias" label="别名（可选）">
        <Input />
      </Form.Item>
      <Form.Item name="validityDays" label="有效期（天）" rules={[{ required: true }]}>
        <InputNumber min={1} max={3650} className="full-width" />
      </Form.Item>
      <Form.Item name="trusted" label="标记为信任" valuePropName="checked">
        <Switch />
      </Form.Item>
    </Form>
  </Modal>
);
