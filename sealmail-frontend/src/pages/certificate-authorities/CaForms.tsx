import React from 'react';
import { Form, Input, InputNumber, Modal, Select, Switch } from 'antd';
import type { FormInstance } from 'antd';
import type { Certificate } from '../../types';
import type { CreateIntermediateCaValues, CreateRootCaValues, SignCsrValues } from './caUtils';
import { getDisplayName } from './caUtils';

interface RootCaModalProps {
  form: FormInstance<CreateRootCaValues>;
  issuing: boolean;
  open: boolean;
  onCancel: () => void;
  onFinish: (values: CreateRootCaValues) => void | Promise<void>;
}

interface IntermediateCaModalProps {
  form: FormInstance<CreateIntermediateCaValues>;
  issuing: boolean;
  open: boolean;
  rootCandidates: Certificate[];
  onRootChange?: (certificateId: string) => void;
  onCancel: () => void;
  onFinish: (values: CreateIntermediateCaValues) => void | Promise<void>;
}

interface SignCsrModalProps {
  form: FormInstance<SignCsrValues>;
  issuing: boolean;
  open: boolean;
  signingCaCandidates: Certificate[];
  onCancel: () => void;
  onFinish: (values: SignCsrValues) => void | Promise<void>;
}

const algorithmOptions = [
  { value: 'RSA', label: 'RSA 2048' },
  { value: 'SM2', label: 'SM2' },
];

export const RootCaModal: React.FC<RootCaModalProps> = ({
  form,
  issuing,
  open,
  onCancel,
  onFinish,
}) => (
  <Modal
    title="新建 Root CA"
    open={open}
    onCancel={onCancel}
    onOk={() => form.submit()}
    confirmLoading={issuing}
    okText="创建"
    width={560}
  >
    <Form form={form} layout="vertical" onFinish={onFinish}>
      <Form.Item name="commonName" label="Common Name" rules={[{ required: true }]}>
        <Input placeholder="例如：SealMail Root CA" />
      </Form.Item>
      <Form.Item name="algorithm" label="算法" rules={[{ required: true }]}>
        <Select options={algorithmOptions} />
      </Form.Item>
      <Form.Item name="subjectDn" label="Subject DN (可选)">
        <Input placeholder="留空使用 CN=..., O=SealMail, C=CN" />
      </Form.Item>
      <Form.Item name="alias" label="别名">
        <Input />
      </Form.Item>
      <Form.Item name="validityDays" label="有效期（天）" rules={[{ required: true }]}>
        <InputNumber min={1} max={10000} className="full-width" />
      </Form.Item>
    </Form>
  </Modal>
);

export const IntermediateCaModal: React.FC<IntermediateCaModalProps> = ({
  form,
  issuing,
  open,
  rootCandidates,
  onRootChange,
  onCancel,
  onFinish,
}) => (
  <Modal
    title="新建 Intermediate CA"
    open={open}
    onCancel={onCancel}
    onOk={() => form.submit()}
    confirmLoading={issuing}
    okText="创建"
    width={560}
  >
    <Form form={form} layout="vertical" onFinish={onFinish}>
      <Form.Item
        name="rootCaId"
        label="父 Root CA"
        rules={[{ required: true }]}
        extra="只列出有私钥且未吊销的 Root CA"
      >
        <Select
          placeholder="请选择 Root CA"
          onChange={onRootChange}
          options={rootCandidates.map((candidate) => ({
            value: candidate.id,
            label: `${getDisplayName(candidate)} (${candidate.algorithm}, ${candidate.id.substring(0, 12)}...)`,
          }))}
        />
      </Form.Item>
      <Form.Item name="commonName" label="Common Name" rules={[{ required: true }]}>
        <Input placeholder="例如：SealMail Intermediate CA" />
      </Form.Item>
      <Form.Item name="algorithm" label="算法" rules={[{ required: true }]}>
        <Select options={algorithmOptions} disabled />
      </Form.Item>
      <Form.Item name="subjectDn" label="Subject DN (可选)">
        <Input />
      </Form.Item>
      <Form.Item name="alias" label="别名">
        <Input />
      </Form.Item>
      <Form.Item name="validityDays" label="有效期（天）" rules={[{ required: true }]}>
        <InputNumber min={1} max={10000} className="full-width" />
      </Form.Item>
    </Form>
  </Modal>
);

export const SignCsrModal: React.FC<SignCsrModalProps> = ({
  form,
  issuing,
  open,
  signingCaCandidates,
  onCancel,
  onFinish,
}) => (
  <Modal
    title="签发 CSR"
    open={open}
    onCancel={onCancel}
    onOk={() => form.submit()}
    confirmLoading={issuing}
    okText="签发"
    cancelText="取消"
    width={640}
  >
    <Form form={form} layout="vertical" onFinish={onFinish}>
      <Form.Item
        name="caCertId"
        label="Intermediate CA"
        rules={[{ required: true, message: '请选择 Intermediate CA' }]}
        extra="只列出 pathLen=0、带私钥、未吊销且链路可信的 Intermediate CA"
      >
        <Select
          placeholder="请选择 Intermediate CA"
          options={signingCaCandidates.map((candidate) => ({
            value: candidate.id,
            label: `${getDisplayName(candidate)} (${candidate.algorithm || '?'}, ${candidate.id.substring(0, 12)}...)`,
          }))}
        />
      </Form.Item>
      <Form.Item
        name="csrPem"
        label="PKCS#10 CSR (PEM)"
        rules={[{ required: true, message: '请粘贴 CSR PEM 内容' }]}
        extra="所有者邮箱将从 CSR Subject 中的 emailAddress / CN 自动解析"
      >
        <Input.TextArea
          rows={8}
          placeholder="-----BEGIN CERTIFICATE REQUEST-----&#10;...&#10;-----END CERTIFICATE REQUEST-----"
        />
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
