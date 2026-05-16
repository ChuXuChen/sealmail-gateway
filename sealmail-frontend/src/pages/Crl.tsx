import React, { useEffect, useMemo, useState } from 'react';
import { Typography, Table, Tag, Space, Button, message, Modal, Form, Input, Upload } from 'antd';
import type { UploadProps } from 'antd';
import { DownloadOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons';
import { Certificate } from '../types';
import { caApi, certificateApi, crlUrls } from '../api/client';
import { getApiErrorMessage } from '../api/errors';

const { Title, Text } = Typography;

interface ImportCrlFormValues {
  crlPem?: string;
  crlDerBase64?: string;
}

const Crl: React.FC = () => {
  const [cas, setCas] = useState<Certificate[]>([]);
  const [allCerts, setAllCerts] = useState<Certificate[]>([]);
  const [loading, setLoading] = useState(false);
  const [importing, setImporting] = useState(false);
  const [importTarget, setImportTarget] = useState<Certificate | null>(null);
  const [form] = Form.useForm();

  const load = async () => {
    setLoading(true);
    try {
      const [caRes, certRes] = await Promise.all([
        caApi.list(),
        certificateApi.list({ includeAll: true, page: 1, size: 500 }),
      ]);
      setCas(caRes.data.data);
      setAllCerts(certRes.data.data.items);
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载 CA / 证书数据失败'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void Promise.resolve().then(load);
  }, []);

  const revokedByIssuer = useMemo(() => {
    const map = new Map<string, Certificate[]>();
    for (const c of allCerts) {
      if (!c.revoked || !c.issuerCertId) continue;
      const list = map.get(c.issuerCertId) ?? [];
      list.push(c);
      map.set(c.issuerCertId, list);
    }
    return map;
  }, [allCerts]);

  const caRows = cas.map((ca) => ({
    ca,
    revokedCount: revokedByIssuer.get(ca.id)?.length ?? 0,
  }));

  const openImportCrl = (ca: Certificate) => {
    setImportTarget(ca);
    form.resetFields();
  };

  const handleImportCrl = async (values: ImportCrlFormValues) => {
    if (!importTarget) return;
    if (!values.crlPem?.trim() && !values.crlDerBase64?.trim()) {
      message.warning('请上传 .crl 文件或粘贴 PEM CRL 内容');
      return;
    }
    setImporting(true);
    try {
      await caApi.importCrl(importTarget.id, {
        crlPem: values.crlPem?.trim() || undefined,
        crlDerBase64: values.crlDerBase64?.trim() || undefined,
      });
      message.success('CRL 已导入');
      setImportTarget(null);
      form.resetFields();
      load();
    } catch (error) {
      message.error(getApiErrorMessage(error, 'CRL 导入失败'));
    } finally {
      setImporting(false);
    }
  };

  const readFileAsArrayBuffer = (file: File) =>
    new Promise<ArrayBuffer>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as ArrayBuffer);
      reader.onerror = () => reject(reader.error);
      reader.readAsArrayBuffer(file);
    });

  const arrayBufferToBase64 = (buffer: ArrayBuffer) => {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    const chunkSize = 0x8000;
    for (let offset = 0; offset < bytes.length; offset += chunkSize) {
      const chunk = bytes.subarray(offset, offset + chunkSize);
      binary += String.fromCharCode(...chunk);
    }
    return window.btoa(binary);
  };

  const uploadProps: UploadProps = {
    accept: '.crl,.der,.pem,.txt,application/pkix-crl,application/octet-stream,text/plain',
    maxCount: 1,
    beforeUpload: async (file) => {
      try {
        const buffer = await readFileAsArrayBuffer(file);
        const text = new TextDecoder('utf-8').decode(buffer).trim();
        if (text.includes('-----BEGIN X509 CRL-----') || text.includes('-----BEGIN CRL-----')) {
          form.setFieldsValue({ crlPem: text, crlDerBase64: undefined });
          message.success('PEM CRL 文件已读取');
        } else {
          form.setFieldsValue({ crlDerBase64: arrayBufferToBase64(buffer), crlPem: undefined });
          message.success('DER CRL 文件已读取');
        }
      } catch {
        message.error('读取 CRL 文件失败');
      }
      return false;
    },
  };

  const innerColumns = [
    { title: '指纹', dataIndex: 'id', render: (v: string) => <Text code style={{ fontSize: 11 }}>{v.substring(0, 24)}…</Text> },
    { title: 'Owner', dataIndex: 'ownerEmail' },
    { title: '算法', dataIndex: 'algorithm', render: (v: string) => <Tag>{v}</Tag>, width: 80 },
    { title: '吊销原因', dataIndex: 'revocationReason' },
    {
      title: '吊销时间',
      dataIndex: 'revocationDate',
      render: (v: string) => (v ? new Date(v).toLocaleString() : '-'),
    },
  ];

  const outerColumns = [
    {
      title: '类型',
      key: 'role',
      render: (_: unknown, r: { ca: Certificate }) =>
        r.ca.pathLenConstraint === 1 ? <Tag color="volcano">Root CA</Tag> : <Tag color="geekblue">Intermediate CA</Tag>,
      width: 140,
    },
    {
      title: 'CA',
      key: 'ca',
      render: (_: unknown, r: { ca: Certificate }) => (
        <Space direction="vertical" size={0}>
          <strong>{r.ca.alias || r.ca.subjectDn}</strong>
          <Text type="secondary" style={{ fontSize: 11 }}>{r.ca.id.substring(0, 24)}…</Text>
        </Space>
      ),
    },
    { title: '算法', key: 'alg', render: (_: unknown, r: { ca: Certificate }) => r.ca.algorithm, width: 100 },
    {
      title: '吊销条目',
      dataIndex: 'revokedCount',
      width: 110,
      render: (n: number) =>
        n > 0 ? <Tag color="error">{n}</Tag> : <Tag>0</Tag>,
    },
    {
      title: 'CRL 来源',
      key: 'source',
      width: 120,
      render: (_: unknown, r: { ca: Certificate }) =>
        r.ca.importedCrlAvailable ? <Tag color="processing">外部导入</Tag> : <Tag>动态生成</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      render: (_: unknown, r: { ca: Certificate }) => (
        <Space>
          <Button
            size="small"
            type="link"
            icon={<UploadOutlined />}
            onClick={() => openImportCrl(r.ca)}
          >
            导入 CRL
          </Button>
          <Button
            size="small"
            type="link"
            icon={<DownloadOutlined />}
            href={crlUrls.pem(r.ca.id)}
            target="_blank"
          >
            PEM
          </Button>
          <Button
            size="small"
            type="link"
            icon={<DownloadOutlined />}
            href={crlUrls.der(r.ca.id)}
            target="_blank"
          >
            DER
          </Button>
        </Space>
      ),
      width: 260,
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>吊销列表 (CRL)</Title>
        <Button icon={<ReloadOutlined />} onClick={load}>
          刷新
        </Button>
      </div>

      <Table
        columns={outerColumns}
        dataSource={caRows}
        loading={loading}
        rowKey={(r) => r.ca.id}
        expandable={{
          expandedRowRender: (record) => {
            const revoked = revokedByIssuer.get(record.ca.id) ?? [];
            if (revoked.length === 0) {
              return <Text type="secondary">本 CA 无吊销记录。</Text>;
            }
            return (
              <Table
                size="small"
                columns={innerColumns}
                dataSource={revoked}
                rowKey="id"
                pagination={false}
              />
            );
          },
        }}
        pagination={false}
      />

      <Modal
        title="导入外部 CRL（PEM / DER）"
        open={!!importTarget}
        onCancel={() => setImportTarget(null)}
        onOk={() => form.submit()}
        confirmLoading={importing}
        okText="导入"
        cancelText="取消"
        width={680}
      >
        <Form form={form} layout="vertical" onFinish={handleImportCrl}>
          <Form.Item label="CA">
            <Text>{importTarget?.alias || importTarget?.subjectDn || importTarget?.id}</Text>
          </Form.Item>
          <Form.Item
            label="CRL 文件"
            extra="支持 PEM 文本 CRL，也支持 DER 编码的 .crl / .der 文件。上传文件会自动填充导入内容。"
          >
            <Upload {...uploadProps}>
              <Button icon={<UploadOutlined />}>选择 .crl / .der / .pem 文件</Button>
            </Upload>
          </Form.Item>
          <Form.Item
            name="crlPem"
            label="PEM 内容"
            extra="也可以直接粘贴 PEM CRL。导入时会校验 CRL issuer 与 CA subject 一致，并用 CA 公钥校验 CRL 签名。"
          >
            <Input.TextArea
              rows={10}
              placeholder="-----BEGIN X509 CRL-----&#10;...&#10;-----END X509 CRL-----"
            />
          </Form.Item>
          <Form.Item name="crlDerBase64" hidden>
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default Crl;
