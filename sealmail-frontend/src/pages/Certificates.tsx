import React, { useCallback, useMemo, useState } from 'react';
import {
  Table,
  Button,
  Typography,
  Space,
  Tag,
  Modal,
  Form,
  Input,
  InputNumber,
  Select,
  message,
  Popconfirm,
  Switch,
  Descriptions,
  Drawer,
  Dropdown,
} from 'antd';
import {
  PlusOutlined,
  EyeOutlined,
  LockOutlined,
  UnlockOutlined,
  SafetyOutlined,
  FileProtectOutlined,
  KeyOutlined,
  DeleteOutlined,
  ImportOutlined,
  FileAddOutlined,
  AuditOutlined,
  DownOutlined,
  LinkOutlined,
} from '@ant-design/icons';
import { Certificate, CertificateBinding, CertificateBindingPurpose } from '../types';
import { certificateApi, caApi, certificateBindingApi } from '../api/client';
import { getApiErrorMessage } from '../api/errors';

const { Title } = Typography;
const { TextArea } = Input;

const AlgorithmTag: React.FC<{ algorithm?: string }> = ({ algorithm }) => {
  switch (algorithm) {
    case 'SM2':
      return <Tag color="error">SM2</Tag>;
    case 'RSA':
      return <Tag color="processing">RSA</Tag>;
    default:
      return <Tag>{algorithm || '未知'}</Tag>;
  }
};

const StatusTags: React.FC<{ record: Certificate }> = ({ record }) => (
  <Space>
    <Tag color={record.trusted ? 'success' : 'default'}>
      {record.trusted ? '信任' : '未信'}
    </Tag>
    <Tag color={record.revoked ? 'error' : 'processing'}>
      {record.revoked ? '吊销' : '有效'}
    </Tag>
    {!record.revoked && record.chainUsable === false ? (
      <Tag color="warning">链断</Tag>
    ) : null}
  </Space>
);

const Certificates: React.FC = () => {
  const [data, setData] = useState<Certificate[]>([]);
  const [cas, setCas] = useState<Certificate[]>([]);
  const [bindings, setBindings] = useState<CertificateBinding[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ page: 1, size: 10, total: 0 });
  const [importModalVisible, setImportModalVisible] = useState(false);
  const [selfSignedModalVisible, setSelfSignedModalVisible] = useState(false);
  const [issueByCaModalVisible, setIssueByCaModalVisible] = useState(false);
  const [issuing, setIssuing] = useState(false);
  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedCert, setSelectedCert] = useState<Certificate | null>(null);
  const [form] = Form.useForm();
  const [selfSignedForm] = Form.useForm();
  const [issueByCaForm] = Form.useForm();

  // Only Intermediate CAs (pathLen=0) with a private key can sign end-entity certs.
  const caCandidates = useMemo(
    () => cas.filter((c) => c.pathLenConstraint === 0 && c.hasPrivateKey && !c.revoked && c.chainUsable),
    [cas],
  );

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [certRes, caRes, bindingRes] = await Promise.all([
        certificateApi.list({ page: pagination.page, size: pagination.size }),
        caApi.list(),
        certificateBindingApi.list(),
      ]);
      setData(certRes.data.data.items);
      setCas(caRes.data.data);
      setBindings(bindingRes.data.data);
      setPagination((prev) => ({
        ...prev,
        total: certRes.data.data.total,
      }));
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载证书列表失败'));
    } finally {
      setLoading(false);
    }
  }, [pagination.page, pagination.size]);

  React.useEffect(() => {
    void Promise.resolve().then(loadData);
  }, [loadData]);

  const handleImport = async (values: {
    pemData: string;
    ownerEmail: string;
    alias?: string;
    trusted?: boolean;
    privateKeyData?: string;
  }) => {
    try {
      await certificateApi.import(values);
      message.success('证书导入成功');
      setImportModalVisible(false);
      form.resetFields();
      loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '证书导入失败'));
    }
  };

  const handleGenerateSelfSigned = async (values: {
    ownerEmail: string;
    algorithm: 'RSA' | 'SM2';
    subjectDn?: string;
    alias?: string;
    validityDays?: number;
    trusted?: boolean;
  }) => {
    setIssuing(true);
    try {
      await certificateApi.generateSelfSigned({
        ownerEmail: values.ownerEmail,
        algorithm: values.algorithm,
        subjectDn: values.subjectDn,
        alias: values.alias,
        validityDays: values.validityDays,
        trusted: values.trusted,
      });
      message.success('自签名证书已生成');
      setSelfSignedModalVisible(false);
      selfSignedForm.resetFields();
      loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '自签名证书生成失败'));
    } finally {
      setIssuing(false);
    }
  };

  const handleIssueByCa = async (values: {
    intermediateCaId: string;
    ownerEmail: string;
    algorithm: 'RSA' | 'SM2';
    subjectDn?: string;
    alias?: string;
    validityDays?: number;
    trusted?: boolean;
  }) => {
    setIssuing(true);
    try {
      await certificateApi.issue(values);
      message.success('证书已签发');
      setIssueByCaModalVisible(false);
      issueByCaForm.resetFields();
      loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, 'CA 签发失败'));
    } finally {
      setIssuing(false);
    }
  };

  const handleTrust = async (id: string) => {
    try {
      await certificateApi.trust(id);
      message.success('证书已标记为信任');
      loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  };

  const handleUntrust = async (id: string) => {
    try {
      await certificateApi.untrust(id);
      message.success('已撤销该证书的信任');
      loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  };

  const handleRevoke = async (id: string) => {
    try {
      await certificateApi.revoke(id, '管理员手动吊销');
      message.success('证书已吊销');
      loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await certificateApi.delete(id);
      message.success('证书已删除');
      loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '证书删除失败'));
    }
  };

  const bindingFor = (record: Certificate, purpose: CertificateBindingPurpose) =>
    bindings.find((binding) => binding.ownerEmail === record.ownerEmail && binding.purpose === purpose);

  const handleBind = async (record: Certificate, purpose: CertificateBindingPurpose) => {
    try {
      await certificateBindingApi.upsert({
        ownerEmail: record.ownerEmail,
        certificateId: record.id,
        purpose,
        enabled: true,
      });
      message.success(purpose === 'ENCRYPTION' ? '加密证书绑定已更新' : '签名证书绑定已更新');
      loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '证书绑定失败'));
    }
  };

  const handleDeleteBinding = async (id: string) => {
    try {
      await certificateBindingApi.delete(id);
      message.success('证书绑定已删除');
      loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除证书绑定失败'));
    }
  };

  const columns = [
    {
      title: '别名',
      dataIndex: 'alias',
      key: 'alias',
      render: (text: string, record: Certificate) =>
        text || (record.subjectDn ? record.subjectDn.substring(0, 30) + '...' : '-'),
    },
    {
      title: '所有者邮箱',
      dataIndex: 'ownerEmail',
      key: 'ownerEmail',
    },
    {
      title: '算法类型',
      dataIndex: 'algorithm',
      key: 'algorithm',
      render: (alg: string) => <AlgorithmTag algorithm={alg} />,
    },
    {
      title: '私钥',
      key: 'hasPrivateKey',
      render: (_: unknown, record: Certificate) =>
        record.hasPrivateKey ? (
          <Tag color="success" icon={<KeyOutlined />}>私钥</Tag>
        ) : (
          <Tag>无钥</Tag>
        ),
    },
    {
      title: '状态',
      key: 'status',
      render: (_: unknown, record: Certificate) => <StatusTags record={record} />,
    },
    {
      title: '绑定',
      key: 'binding',
      render: (_: unknown, record: Certificate) => {
        const encryptionBinding = bindingFor(record, 'ENCRYPTION');
        const signingBinding = bindingFor(record, 'SIGNING');
        return (
          <Space wrap>
            {encryptionBinding?.certificateId === record.id && encryptionBinding.enabled ? (
              <Popconfirm
                title="删除此加密绑定？"
                onConfirm={() => handleDeleteBinding(encryptionBinding.id)}
                okText="删除"
                cancelText="取消"
              >
                <Tag color="success" icon={<LinkOutlined />}>加密</Tag>
              </Popconfirm>
            ) : (
              <Button
                size="small"
                icon={<LinkOutlined />}
                disabled={!record.suitableForEncryption}
                onClick={() => handleBind(record, 'ENCRYPTION')}
              >
                绑加密
              </Button>
            )}
            {signingBinding?.certificateId === record.id && signingBinding.enabled ? (
              <Popconfirm
                title="删除此签名绑定？"
                onConfirm={() => handleDeleteBinding(signingBinding.id)}
                okText="删除"
                cancelText="取消"
              >
                <Tag color="processing" icon={<LinkOutlined />}>签名</Tag>
              </Popconfirm>
            ) : (
              <Button
                size="small"
                icon={<LinkOutlined />}
                disabled={!record.suitableForSigning || !record.hasPrivateKey}
                onClick={() => handleBind(record, 'SIGNING')}
              >
                绑签名
              </Button>
            )}
          </Space>
        );
      },
    },
    {
      title: '有效期至',
      dataIndex: 'notAfter',
      key: 'notAfter',
      render: (date: string) => new Date(date).toLocaleDateString(),
    },
    {
      title: '导入时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => new Date(date).toLocaleDateString(),
    },
    {
      title: '操作',
      key: 'actions',
      render: (_: unknown, record: Certificate) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => {
              setSelectedCert(record);
              setDetailVisible(true);
            }}
          >
            查看
          </Button>
          {record.trusted ? (
            <Popconfirm
              title="撤销此证书的信任？"
              onConfirm={() => handleUntrust(record.id)}
              okText="确认"
              cancelText="取消"
            >
              <Button type="link" size="small" icon={<LockOutlined />}>
                撤销信任
              </Button>
            </Popconfirm>
          ) : (
            <Button
              type="link"
              size="small"
              icon={<UnlockOutlined />}
              onClick={() => handleTrust(record.id)}
            >
              信任
            </Button>
          )}
          {!record.revoked && (
            <Popconfirm
              title="确定要吊销此证书吗？"
              onConfirm={() => handleRevoke(record.id)}
              okText="确定"
              cancelText="取消"
            >
              <Button
                type="link"
                size="small"
                danger
                icon={<LockOutlined />}
              >
                吊销
              </Button>
            </Popconfirm>
          )}
          <Popconfirm
            title="确定要删除此证书吗？关联的密钥将自动解除关联。"
            onConfirm={() => handleDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button
              type="link"
              size="small"
              danger
              icon={<DeleteOutlined />}
            >
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 24,
        }}
      >
        <Title level={3} style={{ margin: 0 }}>
          终端证书
        </Title>
        <Dropdown
          trigger={['click']}
          menu={{
            items: [
              {
                key: 'import',
                icon: <ImportOutlined />,
                label: '导入已有证书',
                onClick: () => setImportModalVisible(true),
              },
              {
                key: 'self-signed',
                icon: <FileAddOutlined />,
                label: '生成自签名证书',
                onClick: () => {
                  selfSignedForm.resetFields();
                  selfSignedForm.setFieldsValue({
                    algorithm: 'RSA',
                    validityDays: 365,
                    trusted: true,
                  });
                  setSelfSignedModalVisible(true);
                },
              },
              {
                key: 'issue-by-ca',
                icon: <AuditOutlined />,
                label: '通过 Intermediate CA 签发',
                disabled: caCandidates.length === 0,
                onClick: () => {
                  issueByCaForm.resetFields();
                  issueByCaForm.setFieldsValue({
                    algorithm: 'RSA',
                    validityDays: 365,
                    trusted: true,
                  });
                  setIssueByCaModalVisible(true);
                },
              },
            ],
          }}
        >
          <Button type="primary" icon={<PlusOutlined />}>
            新增证书 <DownOutlined />
          </Button>
        </Dropdown>
      </div>

      <Table
        columns={columns}
        dataSource={data}
        loading={loading}
        rowKey="id"
        pagination={{
          current: pagination.page,
          pageSize: pagination.size,
          total: pagination.total,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `共 ${total} 条`,
          onChange: (page, size) => setPagination((prev) => ({ ...prev, page, size })),
        }}
      />

      <Modal
        title="导入证书"
        open={importModalVisible}
        onCancel={() => setImportModalVisible(false)}
        onOk={() => form.submit()}
        okText="导入"
        cancelText="取消"
        width={600}
      >
        <Form form={form} onFinish={handleImport} layout="vertical">
          <Form.Item
            name="ownerEmail"
            label="所有者邮箱"
            rules={[{ required: true, message: '请输入所有者邮箱' }]}
          >
            <Input placeholder="email@example.com" />
          </Form.Item>

          <Form.Item name="alias" label="证书别名">
            <Input placeholder="可选，便于识别" />
          </Form.Item>

          <Form.Item
            name="pemData"
            label="PEM 格式证书内容"
            rules={[{ required: true, message: '请输入 PEM 格式证书' }]}
          >
            <TextArea
              rows={8}
              placeholder="-----BEGIN CERTIFICATE-----&#10;...&#10;-----END CERTIFICATE-----"
            />
          </Form.Item>

          <Form.Item
            name="privateKeyData"
            label="关联私钥（可选）"
          >
            <TextArea
              rows={6}
              placeholder="-----BEGIN PRIVATE KEY-----&#10;...&#10;-----END PRIVATE KEY-----"
            />
          </Form.Item>

          <Form.Item
            name="trusted"
            label="标记为信任"
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="生成自签名证书"
        open={selfSignedModalVisible}
        onCancel={() => setSelfSignedModalVisible(false)}
        onOk={() => selfSignedForm.submit()}
        confirmLoading={issuing}
        okText="生成"
        cancelText="取消"
        width={600}
      >
        <Form form={selfSignedForm} onFinish={handleGenerateSelfSigned} layout="vertical">
          <Form.Item
            name="ownerEmail"
            label="所有者邮箱"
            rules={[
              { required: true, message: '请输入所有者邮箱' },
              { type: 'email', message: '请输入有效邮箱' },
            ]}
          >
            <Input placeholder="email@example.com" />
          </Form.Item>
          <Form.Item name="algorithm" label="算法" rules={[{ required: true }]}>
            <Select>
              <Select.Option value="RSA">RSA 2048</Select.Option>
              <Select.Option value="SM2">SM2</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="subjectDn" label="Subject DN（可选）">
            <Input placeholder="留空则使用 CN=邮箱, O=SealMail, C=CN" />
          </Form.Item>
          <Form.Item name="alias" label="别名（可选）">
            <Input placeholder="便于识别的名字" />
          </Form.Item>
          <Form.Item name="validityDays" label="有效期（天）" rules={[{ required: true }]}>
            <InputNumber min={1} max={3650} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="trusted" label="标记为信任" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Typography.Paragraph type="secondary" style={{ fontSize: 12 }}>
            后端将自动生成密钥对，与证书一并保存（私钥仅服务端保留）。
          </Typography.Paragraph>
        </Form>
      </Modal>

      <Modal
        title="用 CA 证书签发新证书"
        open={issueByCaModalVisible}
        onCancel={() => setIssueByCaModalVisible(false)}
        onOk={() => issueByCaForm.submit()}
        confirmLoading={issuing}
        okText="签发"
        cancelText="取消"
        width={600}
      >
        <Form form={issueByCaForm} onFinish={handleIssueByCa} layout="vertical">
          <Form.Item
            name="intermediateCaId"
            label="Intermediate CA"
            rules={[{ required: true, message: '请选择 Intermediate CA' }]}
            extra="只列出 pathLen=0、带私钥且未吊销的 Intermediate CA"
          >
            <Select
              placeholder="请选择 Intermediate CA"
              options={caCandidates.map((c) => ({
                value: c.id,
                label: `${c.alias || c.subjectDn} (${c.algorithm || '?'}, ${c.id.substring(0, 12)}…)`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="ownerEmail"
            label="新证书所有者邮箱"
            rules={[
              { required: true, message: '请输入所有者邮箱' },
              { type: 'email', message: '请输入有效邮箱' },
            ]}
          >
            <Input placeholder="user@example.com" />
          </Form.Item>
          <Form.Item name="algorithm" label="新证书算法" rules={[{ required: true }]}>
            <Select>
              <Select.Option value="RSA">RSA 2048</Select.Option>
              <Select.Option value="SM2">SM2</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="subjectDn" label="Subject DN（可选）">
            <Input placeholder="留空则使用 CN=邮箱, O=SealMail, C=CN" />
          </Form.Item>
          <Form.Item name="alias" label="别名（可选）">
            <Input />
          </Form.Item>
          <Form.Item name="validityDays" label="有效期（天）" rules={[{ required: true }]}>
            <InputNumber min={1} max={3650} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="trusted" label="标记为信任" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title="证书详情"
        width={600}
        open={detailVisible}
        onClose={() => {
          setDetailVisible(false);
          setSelectedCert(null);
        }}
      >
        {selectedCert && (
          <Descriptions column={1} bordered>
            <Descriptions.Item label="别名">
              {selectedCert.alias || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="所有者邮箱">
              {selectedCert.ownerEmail}
            </Descriptions.Item>
            <Descriptions.Item label="算法类型">
              <AlgorithmTag algorithm={selectedCert.algorithm} />
            </Descriptions.Item>
            <Descriptions.Item label="颁发者DN">
              {selectedCert.issuerDn || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="主体DN">
              {selectedCert.subjectDn || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="序列号">
              {selectedCert.serialNumber || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="指纹">
              <Typography.Text copyable>{selectedCert.thumbprint}</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="有效期">
              {new Date(selectedCert.notBefore).toLocaleDateString()} -{' '}
              {new Date(selectedCert.notAfter).toLocaleDateString()}
            </Descriptions.Item>
            <Descriptions.Item label="密钥用途">
              {selectedCert.keyUsages?.join(', ') || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="签名能力">
              {selectedCert.suitableForSigning ? (
                <Tag color="success" icon={<FileProtectOutlined />}>可签</Tag>
              ) : (
                <Tag>禁签</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="加密能力">
              {selectedCert.suitableForEncryption ? (
                <Tag color="success" icon={<SafetyOutlined />}>可加密</Tag>
              ) : (
                <Tag>禁加密</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="关联私钥">
              {selectedCert.hasPrivateKey ? (
                <Tag color="success" icon={<KeyOutlined />}>私钥</Tag>
              ) : (
                <Tag>无钥</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              <Space>
                <StatusTags record={selectedCert} />
                {selectedCert.trusted ? (
                  <Popconfirm
                    title="撤销此证书的信任？"
                    onConfirm={async () => {
                      await handleUntrust(selectedCert.id);
                      setDetailVisible(false);
                    }}
                  >
                    <Button size="small" icon={<LockOutlined />}>撤销信任</Button>
                  </Popconfirm>
                ) : (
                  <Button
                    size="small"
                    icon={<UnlockOutlined />}
                    onClick={async () => {
                      await handleTrust(selectedCert.id);
                      setDetailVisible(false);
                    }}
                  >
                    标记信任
                  </Button>
                )}
              </Space>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </div>
  );
};

export default Certificates;
