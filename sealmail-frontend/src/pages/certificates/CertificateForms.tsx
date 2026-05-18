import React from 'react';
import { Alert, Form, Input, InputNumber, Modal, Select, Switch, Typography } from 'antd';
import type { FormInstance } from 'antd';
import type { Certificate } from '../../types';
import type {
  ImportCertificateMode,
  ImportCertificateValues,
  IssueByCaValues,
  SelfSignedCertificateValues,
} from './certificateUtils';
import { certificateDisplayName } from './certificateUtils';

const { TextArea } = Input;

interface ImportCertificateModalProps {
  form: FormInstance<ImportCertificateValues>;
  mode: ImportCertificateMode;
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
  mode,
  open,
  onCancel,
  onFinish,
}) => {
  const managedImport = mode === 'GATEWAY_MANAGED_PRIVATE_KEY';

  return (
  <Modal
    title={managedImport ? '导入网关托管私钥证书' : '导入公开证书'}
    open={open}
    onCancel={onCancel}
    onOk={() => form.submit()}
    okText="导入"
    cancelText="取消"
    width={600}
  >
    <Form form={form} onFinish={onFinish} layout="vertical">
      <Alert
        type="info"
        showIcon
        className="form-note"
        message={managedImport
          ? '仅本地域收件人或本地签名证书应导入为网关托管；托管后私钥不可导出。'
          : '对端网关只导入公开证书；不会上传或保存私钥。'}
      />
      <Form.Item name="ownerEmail" label="所有者邮箱" rules={[{ required: true, message: '请输入所有者邮箱' }]}>
        <Input placeholder="email@example.com" />
      </Form.Item>
      <Form.Item name="alias" label="证书别名">
        <Input placeholder="可选，便于识别" />
      </Form.Item>
      <Form.Item name="pemData" label="PEM 格式证书内容" rules={[{ required: true, message: '请输入 PEM 格式证书' }]}>
        <TextArea rows={8} placeholder="-----BEGIN CERTIFICATE-----&#10;...&#10;-----END CERTIFICATE-----" />
      </Form.Item>
      {managedImport ? (
        <Form.Item
          name="privateKeyData"
          label="网关托管私钥"
          rules={[{ required: true, message: '请输入私钥 PEM' }]}
          extra="仅用于本地域收件人或本地签名证书；托管后私钥不可导出。"
        >
          <TextArea rows={6} placeholder="-----BEGIN PRIVATE KEY-----&#10;...&#10;-----END PRIVATE KEY-----" />
        </Form.Item>
      ) : null}
      <Form.Item name="trusted" label="标记为信任" valuePropName="checked">
        <Switch />
      </Form.Item>
    </Form>
  </Modal>
  );
};

export const SelfSignedCertificateModal: React.FC<SelfSignedCertificateModalProps> = ({
  form,
  issuing,
  open,
  onCancel,
  onFinish,
}) => (
  <Modal
    title="生成本地域托管证书"
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
        网关将自动生成并托管私钥；私钥不可导出，只能导出公开证书 PEM。
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
    title="通过 CA 签发本地域托管证书"
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
        extra="只列出 pathLen=0、带托管私钥且未吊销的 Intermediate CA"
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
