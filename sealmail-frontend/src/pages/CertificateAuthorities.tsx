import React, { useEffect, useMemo, useState } from 'react';
import {
  Table,
  Button,
  Typography,
  Space,
  Popover,
  Modal,
  Form,
  Input,
  InputNumber,
  Select,
  message,
  Dropdown,
  Drawer,
  Descriptions,
  Switch,
  Tag,
} from 'antd';
import type { MenuProps } from 'antd';
import {
  DownOutlined,
  EyeOutlined,
  DeleteOutlined,
  LockOutlined,
  UnlockOutlined,
  SafetyOutlined,
  FileProtectOutlined,
  ApartmentOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { Certificate } from '../types';
import { caApi, certificateApi } from '../api/client';

const { Title, Text } = Typography;

type CaTableRecord = Certificate & {
  children?: CaTableRecord[];
};

type CaTypeFilter = 'ALL' | 'ROOT' | 'INTERMEDIATE';
type TrustFilter = 'ALL' | 'TRUSTED' | 'UNTRUSTED';
type StatusFilter = 'ALL' | 'VALID' | 'REVOKED' | 'CHAIN_BROKEN';
type KeyFilter = 'ALL' | 'WITH_KEY' | 'WITHOUT_KEY';
type IssuableFilter = 'ALL' | 'ISSUABLE';
type ConfirmActionType = 'untrust' | 'revoke' | 'delete';

interface Filters {
  type: CaTypeFilter;
  rootId: string;
  trust: TrustFilter;
  status: StatusFilter;
  key: KeyFilter;
  algorithm: string;
  issuable: IssuableFilter;
}

interface ConfirmAction {
  type: ConfirmActionType;
  record: Certificate;
  closeDetail?: boolean;
}

const defaultFilters: Filters = {
  type: 'ALL',
  rootId: 'ALL',
  trust: 'ALL',
  status: 'ALL',
  key: 'ALL',
  algorithm: 'ALL',
  issuable: 'ALL',
};

const roleOf = (cert: Certificate): string => {
  if (cert.pathLenConstraint === 1) return 'Root CA';
  if (cert.pathLenConstraint === 0) return 'Intermediate CA';
  return 'CA';
};

const getUnavailableSigningReason = (cert: Certificate): string | null => {
  if (cert.pathLenConstraint !== 0) return '不是 Intermediate CA';
  if (!cert.hasPrivateKey) return '未关联私钥';
  if (cert.revoked) return '已吊销';
  if (!cert.trusted) return '未信任';
  if (cert.chainUsable === false) return '链路失效';
  return null;
};

const formatDate = (value?: string) => {
  if (!value) return '-';
  return new Date(value).toLocaleDateString();
};

const getDisplayName = (cert: Certificate) =>
  cert.alias || cert.subjectDn || cert.ownerEmail || cert.id;

const matchesCertificateFilters = (cert: Certificate, filters: Filters) => {
  if (filters.type === 'ROOT' && cert.pathLenConstraint !== 1) return false;
  if (filters.type === 'INTERMEDIATE' && cert.pathLenConstraint !== 0) return false;

  if (filters.rootId !== 'ALL' && cert.id !== filters.rootId && cert.issuerCertId !== filters.rootId) {
    return false;
  }

  if (filters.trust === 'TRUSTED' && !cert.trusted) return false;
  if (filters.trust === 'UNTRUSTED' && cert.trusted) return false;

  if (filters.status === 'VALID' && cert.revoked) return false;
  if (filters.status === 'REVOKED' && !cert.revoked) return false;
  if (filters.status === 'CHAIN_BROKEN' && cert.chainUsable !== false) return false;

  if (filters.key === 'WITH_KEY' && !cert.hasPrivateKey) return false;
  if (filters.key === 'WITHOUT_KEY' && cert.hasPrivateKey) return false;

  if (filters.algorithm !== 'ALL' && cert.algorithm !== filters.algorithm) return false;
  if (filters.issuable === 'ISSUABLE' && getUnavailableSigningReason(cert)) return false;

  return true;
};

const AlgorithmTag: React.FC<{ algorithm?: string }> = ({ algorithm }) => {
  if (algorithm === 'SM2') return <Tag color="magenta">SM2</Tag>;
  if (algorithm === 'RSA') return <Tag color="blue">RSA</Tag>;
  return <Tag>{algorithm || '未知'}</Tag>;
};

const RoleTag: React.FC<{ cert: Certificate }> = ({ cert }) => (
  <Tag color={cert.pathLenConstraint === 1 ? 'purple' : 'cyan'}>{roleOf(cert)}</Tag>
);

const TrustTag: React.FC<{ cert: Certificate }> = ({ cert }) => (
  <Tag color={cert.trusted ? 'green' : 'default'}>{cert.trusted ? '信任' : '未信'}</Tag>
);

const RevocationTag: React.FC<{ cert: Certificate }> = ({ cert }) => (
  <Tag color={cert.revoked ? 'red' : 'processing'}>{cert.revoked ? '吊销' : '有效'}</Tag>
);

const PrivateKeyTag: React.FC<{ cert: Certificate }> = ({ cert }) => (
  <Tag color={cert.hasPrivateKey ? 'green' : 'default'}>{cert.hasPrivateKey ? '私钥' : '无钥'}</Tag>
);

const ChainTag: React.FC<{ cert: Certificate }> = ({ cert }) => (
  <Tag color={cert.chainUsable === false ? 'orange' : 'green'}>
    {cert.chainUsable === false ? '链断' : '链通'}
  </Tag>
);

const IssuableTag: React.FC<{ cert: Certificate }> = ({ cert }) => {
  if (cert.pathLenConstraint === 1) {
    return <Tag color="purple">可签</Tag>;
  }

  const reason = getUnavailableSigningReason(cert);
  return reason ? <Tag color="default">禁签</Tag> : <Tag color="success">可签</Tag>;
};

const StatusTags: React.FC<{ cert: Certificate }> = ({ cert }) => (
  <Space size={[0, 4]} wrap>
    <TrustTag cert={cert} />
    <RevocationTag cert={cert} />
    <PrivateKeyTag cert={cert} />
    <ChainTag cert={cert} />
    <IssuableTag cert={cert} />
  </Space>
);

const CertificateAuthorities: React.FC = () => {
  const [data, setData] = useState<Certificate[]>([]);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<Filters>(defaultFilters);
  const [rootModal, setRootModal] = useState(false);
  const [intModal, setIntModal] = useState(false);
  const [signCsrModal, setSignCsrModal] = useState(false);
  const [expandedRowKeys, setExpandedRowKeys] = useState<string[]>([]);
  const [issuing, setIssuing] = useState(false);
  const [detail, setDetail] = useState<Certificate | null>(null);
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [rootForm] = Form.useForm();
  const [intForm] = Form.useForm();
  const [signCsrForm] = Form.useForm();

  const loadData = async () => {
    setLoading(true);
    try {
      const res = await caApi.list();
      setData(res.data.data);
    } catch {
      message.error('加载 CA 列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const roots = useMemo(
    () => data.filter((c) => c.pathLenConstraint === 1),
    [data],
  );

  const rootById = useMemo(
    () => new Map(roots.map((root) => [root.id, root])),
    [roots],
  );

  const rootCandidates = useMemo(
    () => roots.filter((c) => c.hasPrivateKey && !c.revoked && c.chainUsable),
    [roots],
  );

  const signingCaCandidates = useMemo(
    () => data.filter((c) => c.pathLenConstraint === 0 && !getUnavailableSigningReason(c)),
    [data],
  );

  const algorithmOptions = useMemo(() => {
    const algorithms = Array.from(new Set(data.map((item) => item.algorithm).filter(Boolean)));
    return [
      { value: 'ALL', label: '全部算法' },
      ...algorithms.map((algorithm) => ({ value: algorithm as string, label: algorithm as string })),
    ];
  }, [data]);

  const filteredFlatData = useMemo(
    () => data.filter((cert) => matchesCertificateFilters(cert, filters)),
    [data, filters],
  );

  const tableData = useMemo<CaTableRecord[]>(() => {
    const matchingIds = new Set(filteredFlatData.map((cert) => cert.id));
    const childrenByRootId = new Map<string, CaTableRecord[]>();
    const orphanIntermediates: CaTableRecord[] = [];

    data
      .filter((cert) => cert.pathLenConstraint === 0 && matchingIds.has(cert.id))
      .forEach((cert) => {
        const record: CaTableRecord = { ...cert };
        if (cert.issuerCertId) {
          const children = childrenByRootId.get(cert.issuerCertId) || [];
          children.push(record);
          childrenByRootId.set(cert.issuerCertId, children);
        } else {
          orphanIntermediates.push(record);
        }
      });

    const rootRecords = roots.reduce<CaTableRecord[]>((acc, root) => {
      const rootMatches = matchingIds.has(root.id);
      const children = childrenByRootId.get(root.id) || [];

      if (!rootMatches && children.length === 0) {
        return acc;
      }

      acc.push({
        ...root,
        children: children.length > 0 ? children : undefined,
      });
      return acc;
    }, []);

    if (filters.type !== 'ROOT') {
      rootRecords.push(...orphanIntermediates);
    }

    return rootRecords;
  }, [data, filteredFlatData, filters.type, roots]);

  useEffect(() => {
    setExpandedRowKeys(tableData.filter((record) => record.children?.length).map((record) => record.id));
  }, [tableData]);

  const handleCreateRoot = async (values: {
    commonName: string;
    algorithm: 'RSA' | 'SM2';
    subjectDn?: string;
    alias?: string;
    validityDays?: number;
  }) => {
    setIssuing(true);
    try {
      await caApi.createRoot(values);
      message.success('Root CA 已创建');
      setRootModal(false);
      rootForm.resetFields();
      loadData();
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Root CA 创建失败');
    } finally {
      setIssuing(false);
    }
  };

  const handleCreateIntermediate = async (values: {
    rootCaId: string;
    commonName: string;
    algorithm: 'RSA' | 'SM2';
    subjectDn?: string;
    alias?: string;
    validityDays?: number;
  }) => {
    setIssuing(true);
    try {
      await caApi.createIntermediate(values);
      message.success('Intermediate CA 已创建');
      setIntModal(false);
      intForm.resetFields();
      loadData();
    } catch (err: any) {
      message.error(err.response?.data?.message || 'Intermediate CA 创建失败');
    } finally {
      setIssuing(false);
    }
  };

  const handleSignCsr = async (values: {
    caCertId: string;
    csrPem: string;
    alias?: string;
    validityDays?: number;
    trusted?: boolean;
  }) => {
    setIssuing(true);
    try {
      await certificateApi.signCsr(values);
      message.success('CSR 已签发为终端证书');
      setSignCsrModal(false);
      signCsrForm.resetFields();
    } catch (err: any) {
      message.error(err.response?.data?.message || 'CSR 签发失败');
    } finally {
      setIssuing(false);
    }
  };

  const handleRevoke = async (id: string) => {
    try {
      await caApi.revoke(id, '管理员手动吊销');
      message.success('CA 已吊销（已签发的子证书已级联吊销）');
      loadData();
    } catch {
      message.error('吊销失败');
    }
  };

  const handleTrust = async (id: string) => {
    try {
      await caApi.trust(id);
      message.success('已标记为信任');
      loadData();
    } catch {
      message.error('操作失败');
    }
  };

  const handleUntrust = async (id: string) => {
    try {
      await caApi.untrust(id);
      message.success('已撤销信任');
      loadData();
    } catch {
      message.error('操作失败');
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await caApi.delete(id);
      message.success('CA 已删除');
      loadData();
    } catch {
      message.error('删除失败');
    }
  };

  const openRootModal = () => {
    rootForm.resetFields();
    rootForm.setFieldsValue({ algorithm: 'RSA', validityDays: 3650 });
    setRootModal(true);
  };

  const openIntermediateModal = () => {
    intForm.resetFields();
    intForm.setFieldsValue({ algorithm: 'RSA', validityDays: 1825 });
    setIntModal(true);
  };

  const openSignCsrModal = () => {
    signCsrForm.resetFields();
    signCsrForm.setFieldsValue({
      validityDays: 365,
      trusted: true,
    });
    setSignCsrModal(true);
  };

  const updateFilter = <K extends keyof Filters>(key: K, value: Filters[K]) => {
    setFilters((prev) => ({ ...prev, [key]: value }));
  };

  const resetFilters = () => {
    setFilters(defaultFilters);
  };

  const openConfirm = (type: ConfirmActionType, record: Certificate, closeDetail = false) => {
    setConfirmAction({ type, record, closeDetail });
  };

  const handleConfirmOk = async () => {
    if (!confirmAction) return;

    setConfirming(true);
    try {
      if (confirmAction.type === 'untrust') {
        await handleUntrust(confirmAction.record.id);
      }
      if (confirmAction.type === 'revoke') {
        await handleRevoke(confirmAction.record.id);
      }
      if (confirmAction.type === 'delete') {
        await handleDelete(confirmAction.record.id);
      }
      if (confirmAction.closeDetail) {
        setDetail(null);
      }
      setConfirmAction(null);
    } finally {
      setConfirming(false);
    }
  };

  const handleRowActionClick = (key: string, record: CaTableRecord) => {
    if (key === 'trust') {
      handleTrust(record.id);
      return;
    }
    if (key === 'untrust') {
      openConfirm('untrust', record);
      return;
    }
    if (key === 'revoke') {
      openConfirm('revoke', record);
      return;
    }
    if (key === 'delete') {
      openConfirm('delete', record);
    }
  };

  const getRowActionItems = (record: CaTableRecord): MenuProps['items'] => [
    record.trusted
      ? {
        key: 'untrust',
        icon: <LockOutlined />,
        label: '撤销信任',
      }
      : {
        key: 'trust',
        icon: <UnlockOutlined />,
        label: '标记信任',
      },
    !record.revoked
      ? {
        key: 'revoke',
        icon: <LockOutlined />,
        danger: true,
        label: '吊销',
      }
      : null,
    {
      key: 'delete',
      icon: <DeleteOutlined />,
      danger: true,
      label: '删除',
    },
  ].filter(Boolean) as MenuProps['items'];

  const confirmTitle = confirmAction?.type === 'untrust'
    ? '撤销此 CA 的信任？'
    : confirmAction?.type === 'revoke'
      ? `确定吊销此 ${roleOf(confirmAction.record)}？`
      : '确定删除此 CA？';

  const confirmContent = confirmAction?.type === 'revoke'
    ? '由它签发的所有子证书将级联吊销。'
    : confirmAction?.type === 'delete'
      ? '若仍有下级证书，后端会阻止删除。'
      : undefined;

  const filterSection = (title: string, hint: string, control: React.ReactNode) => (
    <div className="ca-filter-section">
      <div className="ca-filter-section-head">
        <Text strong style={{ fontSize: 12 }}>{title}</Text>
        <div className="ca-filter-hint">{hint}</div>
      </div>
      {control}
    </div>
  );

  const filterHeader = (label: string, active: boolean, content: React.ReactNode) => (
    <Popover
      trigger="click"
      placement="bottomLeft"
      overlayClassName="ca-filter-popover"
      content={<div className="ca-filter-panel">{content}</div>}
    >
      <Button type="text" size="small" className={`ca-filter-trigger${active ? ' is-active' : ''}`}>
        <Space size={4}>
          <span>{label}</span>
          <DownOutlined />
        </Space>
      </Button>
    </Popover>
  );

  const caHeaderFilter = (
    <div>
      <div className="ca-filter-panel-header">
        <Text strong>CA 过滤</Text>
        <div className="ca-filter-hint">按层级和归属筛选。</div>
      </div>
      {filterSection(
        '类型',
        'Root / Intermediate',
        <Select
          value={filters.type}
          onChange={(value) => updateFilter('type', value)}
          style={{ width: '100%' }}
          options={[
            { value: 'ALL', label: '全部类型' },
            { value: 'ROOT', label: 'Root CA' },
            { value: 'INTERMEDIATE', label: 'Intermediate CA' },
          ]}
        />
      )}
      {filterSection(
        '所属 Root',
        'Intermediate 归属',
        <Select
          value={filters.rootId}
          onChange={(value) => updateFilter('rootId', value)}
          style={{ width: '100%' }}
          options={[
            { value: 'ALL', label: '全部 Root 归属' },
            ...roots.map((root) => ({
              value: root.id,
              label: getDisplayName(root),
            })),
          ]}
        />
      )}
      <div className="ca-filter-panel-footer">
        <Button size="small" onClick={resetFilters}>重置</Button>
      </div>
    </div>
  );

  const algorithmHeaderFilter = (
    <div>
      <div className="ca-filter-panel-header">
        <Text strong>算法过滤</Text>
        <div className="ca-filter-hint">按证书算法筛选。</div>
      </div>
      {filterSection(
        '算法',
        'RSA / SM2',
        <Select
          value={filters.algorithm}
          onChange={(value) => updateFilter('algorithm', value)}
          style={{ width: '100%' }}
          options={algorithmOptions}
        />
      )}
      <div className="ca-filter-panel-footer">
        <Button size="small" onClick={resetFilters}>重置</Button>
      </div>
    </div>
  );

  const statusHeaderFilter = (
    <div>
      <div className="ca-filter-panel-header">
        <Text strong>状态过滤</Text>
        <div className="ca-filter-hint">信任、吊销与可签发状态。</div>
      </div>
      {filterSection(
        '证书状态',
        '有效 / 吊销 / 链路',
        <Select
          value={filters.status}
          onChange={(value) => updateFilter('status', value)}
          style={{ width: '100%' }}
          options={[
            { value: 'ALL', label: '全部状态' },
            { value: 'VALID', label: '有效' },
            { value: 'REVOKED', label: '已吊销' },
            { value: 'CHAIN_BROKEN', label: '链路失效' },
          ]}
        />
      )}
      {filterSection(
        '信任',
        '受信任状态',
        <Select
          value={filters.trust}
          onChange={(value) => updateFilter('trust', value)}
          style={{ width: '100%' }}
          options={[
            { value: 'ALL', label: '全部信任' },
            { value: 'TRUSTED', label: '已信任' },
            { value: 'UNTRUSTED', label: '未信任' },
          ]}
        />
      )}
      {filterSection(
        '私钥',
        '是否已关联私钥',
        <Select
          value={filters.key}
          onChange={(value) => updateFilter('key', value)}
          style={{ width: '100%' }}
          options={[
            { value: 'ALL', label: '全部私钥' },
            { value: 'WITH_KEY', label: '有私钥' },
            { value: 'WITHOUT_KEY', label: '无私钥' },
          ]}
        />
      )}
      {filterSection(
        '签发能力',
        '是否可用于 CSR',
        <Select
          value={filters.issuable}
          onChange={(value) => updateFilter('issuable', value)}
          style={{ width: '100%' }}
          options={[
            { value: 'ALL', label: '全部' },
            { value: 'ISSUABLE', label: '可签发 CSR' },
          ]}
        />
      )}
      <div className="ca-filter-panel-footer">
        <Button size="small" onClick={resetFilters}>重置</Button>
      </div>
    </div>
  );

  const columns: ColumnsType<CaTableRecord> = [
    {
      title: filterHeader('CA', filters.type !== 'ALL' || filters.rootId !== 'ALL', caHeaderFilter),
      key: 'name',
      render: (_, r) => (
        <Space direction="vertical" size={2} style={{ minWidth: 0 }}>
          <Space size={6} wrap>
            <Text strong ellipsis style={{ maxWidth: 360 }}>
              {getDisplayName(r)}
            </Text>
            <RoleTag cert={r} />
          </Space>
        </Space>
      ),
    },
    {
      title: filterHeader('算法', filters.algorithm !== 'ALL', algorithmHeaderFilter),
      dataIndex: 'algorithm',
      render: (alg) => <AlgorithmTag algorithm={alg} />,
      width: 120,
    },
    {
      title: filterHeader(
        '状态',
        filters.status !== 'ALL' || filters.trust !== 'ALL' || filters.key !== 'ALL' || filters.issuable !== 'ALL',
        statusHeaderFilter,
      ),
      key: 'status',
      render: (_, r) => <StatusTags cert={r} />,
      width: 340,
    },
    {
      title: '有效期至',
      key: 'validity',
      render: (_, r) => formatDate(r.notAfter),
      width: 130,
    },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 150,
      render: (_, r) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => setDetail(r)}>
            查看
          </Button>
          <Dropdown
            trigger={['click']}
            menu={{
              items: getRowActionItems(r),
              onClick: ({ key }) => handleRowActionClick(key, r),
            }}
          >
            <Button type="link" size="small">
              更多 <DownOutlined />
            </Button>
          </Dropdown>
        </Space>
      ),
    },
  ];

  return (
    <div className="ca-page">
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          flexWrap: 'wrap',
          gap: 16,
          marginBottom: 18,
        }}
      >
        <div>
          <Title level={3} style={{ margin: 0, letterSpacing: 0 }}>
            CA 证书
          </Title>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} loading={loading} onClick={loadData}>
            刷新
          </Button>
          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                {
                  key: 'csr',
                  icon: <FileProtectOutlined />,
                  label: '签发 CSR',
                  disabled: signingCaCandidates.length === 0,
                  onClick: openSignCsrModal,
                },
                {
                  key: 'root',
                  icon: <SafetyOutlined />,
                  label: '签发 Root CA',
                  onClick: openRootModal,
                },
                {
                  key: 'intermediate',
                  icon: <ApartmentOutlined />,
                  label: '签发 Intermediate CA',
                  disabled: rootCandidates.length === 0,
                  onClick: openIntermediateModal,
                },
              ],
            }}
          >
            <Button
              type="primary"
              icon={<FileProtectOutlined />}
            >
              签发 <DownOutlined />
            </Button>
          </Dropdown>
        </Space>
      </div>

      <Table
        columns={columns}
        dataSource={tableData}
        loading={loading}
        rowKey="id"
        size="small"
        expandable={{
          expandedRowKeys,
          onExpandedRowsChange: (keys) => setExpandedRowKeys(keys.map(String)),
        }}
        scroll={{ x: 1160 }}
        pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
      />

      <Modal
        title="新建 Root CA"
        open={rootModal}
        onCancel={() => setRootModal(false)}
        onOk={() => rootForm.submit()}
        confirmLoading={issuing}
        okText="创建"
        width={560}
      >
        <Form form={rootForm} layout="vertical" onFinish={handleCreateRoot}>
          <Form.Item name="commonName" label="Common Name" rules={[{ required: true }]}>
            <Input placeholder="例如：SealMail Root CA" />
          </Form.Item>
          <Form.Item name="algorithm" label="算法" rules={[{ required: true }]}>
            <Select>
              <Select.Option value="RSA">RSA 2048</Select.Option>
              <Select.Option value="SM2">SM2</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="subjectDn" label="Subject DN (可选)">
            <Input placeholder="留空使用 CN=..., O=SealMail, C=CN" />
          </Form.Item>
          <Form.Item name="alias" label="别名">
            <Input />
          </Form.Item>
          <Form.Item name="validityDays" label="有效期（天）" rules={[{ required: true }]}>
            <InputNumber min={1} max={10000} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="新建 Intermediate CA"
        open={intModal}
        onCancel={() => setIntModal(false)}
        onOk={() => intForm.submit()}
        confirmLoading={issuing}
        okText="创建"
        width={560}
      >
        <Form form={intForm} layout="vertical" onFinish={handleCreateIntermediate}>
          <Form.Item
            name="rootCaId"
            label="父 Root CA"
            rules={[{ required: true }]}
            extra="只列出有私钥且未吊销的 Root CA"
          >
            <Select
              placeholder="请选择 Root CA"
              options={rootCandidates.map((c) => ({
                value: c.id,
                label: `${getDisplayName(c)} (${c.algorithm}, ${c.id.substring(0, 12)}...)`,
              }))}
            />
          </Form.Item>
          <Form.Item name="commonName" label="Common Name" rules={[{ required: true }]}>
            <Input placeholder="例如：SealMail Intermediate CA" />
          </Form.Item>
          <Form.Item name="algorithm" label="算法" rules={[{ required: true }]}>
            <Select>
              <Select.Option value="RSA">RSA 2048</Select.Option>
              <Select.Option value="SM2">SM2</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item name="subjectDn" label="Subject DN (可选)">
            <Input />
          </Form.Item>
          <Form.Item name="alias" label="别名">
            <Input />
          </Form.Item>
          <Form.Item name="validityDays" label="有效期（天）" rules={[{ required: true }]}>
            <InputNumber min={1} max={10000} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="签发 CSR"
        open={signCsrModal}
        onCancel={() => setSignCsrModal(false)}
        onOk={() => signCsrForm.submit()}
        confirmLoading={issuing}
        okText="签发"
        cancelText="取消"
        width={640}
      >
        <Form form={signCsrForm} layout="vertical" onFinish={handleSignCsr}>
          <Form.Item
            name="caCertId"
            label="Intermediate CA"
            rules={[{ required: true, message: '请选择 Intermediate CA' }]}
            extra="只列出 pathLen=0、带私钥、未吊销且链路可信的 Intermediate CA"
          >
            <Select
              placeholder="请选择 Intermediate CA"
              options={signingCaCandidates.map((c) => ({
                value: c.id,
                label: `${getDisplayName(c)} (${c.algorithm || '?'}, ${c.id.substring(0, 12)}...)`,
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
            <InputNumber min={1} max={3650} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="trusted" label="标记为信任" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title="CA 详情"
        width={640}
        open={!!detail}
        onClose={() => setDetail(null)}
      >
        {detail && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="类型"><RoleTag cert={detail} /></Descriptions.Item>
              <Descriptions.Item label="算法"><AlgorithmTag algorithm={detail.algorithm} /></Descriptions.Item>
              <Descriptions.Item label="状态"><StatusTags cert={detail} /></Descriptions.Item>
              <Descriptions.Item label="别名">{detail.alias || '-'}</Descriptions.Item>
              <Descriptions.Item label="Subject DN">{detail.subjectDn}</Descriptions.Item>
              <Descriptions.Item label="Issuer DN">{detail.issuerDn}</Descriptions.Item>
              <Descriptions.Item label="所属 Root">
                {detail.pathLenConstraint === 1
                  ? '自身'
                  : detail.issuerCertId
                    ? getDisplayName(rootById.get(detail.issuerCertId) || detail)
                    : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="父 CA 指纹">
                {detail.issuerCertId || 'self-signed'}
              </Descriptions.Item>
              <Descriptions.Item label="证书指纹">
                <Text copyable style={{ fontSize: 12 }}>{detail.thumbprint}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="序列号">{detail.serialNumber}</Descriptions.Item>
              <Descriptions.Item label="SKI">{detail.subjectKeyIdentifier || '-'}</Descriptions.Item>
              <Descriptions.Item label="pathLen">{detail.pathLenConstraint ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="KeyUsage">{detail.keyUsages?.join(', ') || '-'}</Descriptions.Item>
              <Descriptions.Item label="EKU">{detail.extendedKeyUsages?.join(', ') || '-'}</Descriptions.Item>
              <Descriptions.Item label="有效期">
                {formatDate(detail.notBefore)} - {formatDate(detail.notAfter)}
              </Descriptions.Item>
              <Descriptions.Item label="操作">
                <Space>
                  {detail.trusted ? (
                    <Button
                      size="small"
                      icon={<LockOutlined />}
                      onClick={() => openConfirm('untrust', detail, true)}
                    >
                      撤销信任
                    </Button>
                  ) : (
                    <Button
                      size="small"
                      icon={<UnlockOutlined />}
                      onClick={async () => {
                        await handleTrust(detail.id);
                        setDetail(null);
                      }}
                    >
                      标记信任
                    </Button>
                  )}
                </Space>
              </Descriptions.Item>
            </Descriptions>
          </Space>
        )}
      </Drawer>

      <Modal
        title={confirmTitle}
        open={!!confirmAction}
        onCancel={() => setConfirmAction(null)}
        onOk={handleConfirmOk}
        confirmLoading={confirming}
        okText="确认"
        cancelText="取消"
        okButtonProps={{ danger: confirmAction?.type === 'revoke' || confirmAction?.type === 'delete' }}
      >
        {confirmContent}
      </Modal>
    </div>
  );
};

export default CertificateAuthorities;
