import React, { useState, useEffect } from 'react';
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
  Switch,
  Select,
  Tabs,
  message,
  Popconfirm,
  Drawer,
  Descriptions,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  GlobalOutlined,
  LockOutlined,
  SafetyOutlined,
  MailOutlined,
  CopyOutlined,
  MinusCircleOutlined,
} from '@ant-design/icons';
import { DnsRecord, DomainConfig, MailAuthConfig } from '../types';
import { domainConfigApi, mailAuthApi } from '../api/client';
import { getApiErrorMessage } from '../api/errors';

const { Title } = Typography;
const { Option } = Select;

const normalizeDomain = (value: string) =>
  value.trim().toLowerCase().replace(/\.+$/, '');

const emptyMailAuthConfig: MailAuthConfig = {
  enabled: false,
  authservId: 'sealmail-gateway',
  skipPrivateRelay: true,
  dkimEnabled: false,
  dkimSelector: 'sealmail',
  dkimPrivateKeyPath: '',
  dkimPrivateKeySecretRef: '',
  dkimPrivateKeyConfigured: false,
  dkimSignedHeaders: ['from', 'to', 'subject', 'date', 'message-id'],
  spfEnabled: false,
  spfMaxDnsLookups: 10,
  spfUseA: true,
  spfUseMx: true,
  spfIp4: [],
  spfIp6: [],
  spfIncludes: [],
  spfAllPolicy: '~all',
  dmarcEnabled: false,
  dmarcPolicy: 'quarantine',
  dmarcAdkim: 'r',
  dmarcAspf: 'r',
  dmarcPct: 100,
  dmarcRua: '',
  dmarcRuf: '',
  dmarcFailureAction: 'APPLY_POLICY',
  dmarcQuarantineRejectPolicy: true,
};

const DomainConfigs: React.FC = () => {
  const [data, setData] = useState<DomainConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [mailAuthModalVisible, setMailAuthModalVisible] = useState(false);
  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedDomain, setSelectedDomain] = useState<DomainConfig | null>(null);
  const [mailAuthConfig, setMailAuthConfig] = useState<MailAuthConfig | null>(null);
  const [mailAuthLoading, setMailAuthLoading] = useState(false);
  const [dnsRecords, setDnsRecords] = useState<DnsRecord[]>([]);
  const [dnsDomain, setDnsDomain] = useState('');
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [mailAuthForm] = Form.useForm();

  const loadData = async () => {
    setLoading(true);
    try {
      const domainResponse = await domainConfigApi.findAll();
      setData(domainResponse.data.data);
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载域名配置失败'));
    } finally {
      setLoading(false);
    }

    await loadMailAuthConfig(false);
  };

  const loadMailAuthConfig = async (showError = true) => {
    setMailAuthLoading(true);
    try {
      const mailAuthResponse = await mailAuthApi.config();
      const config = mailAuthResponse.data.data;
      setMailAuthConfig(config);
      return config;
    } catch (error) {
      setMailAuthConfig(null);
      if (showError) {
        message.error(getApiErrorMessage(error, '加载邮件认证配置失败'));
      }
      return null;
    } finally {
      setMailAuthLoading(false);
    }
  };

  const initialMailAuthValues = (config: MailAuthConfig | null) => ({
    ...emptyMailAuthConfig,
    ...config,
    clearDkimPrivateKeySecretRef: false,
  });

  useEffect(() => {
    void Promise.resolve().then(loadData);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleCreate = async (values: {
    domain: string;
    localDomain?: boolean;
    encryptionPolicy?: string;
    preferredAlgorithm?: string;
    signingEnabled?: boolean;
    dkimEnabled?: boolean;
    active?: boolean;
  }) => {
    try {
      await domainConfigApi.create({
        domain: normalizeDomain(values.domain),
        localDomain: values.localDomain || false,
        encryptionPolicy: values.encryptionPolicy,
        preferredAlgorithm: values.preferredAlgorithm,
        signingEnabled: values.signingEnabled || false,
        dkimEnabled: values.dkimEnabled || false,
        active: values.active ?? true,
      });
      message.success('域名配置创建成功');
      setCreateModalVisible(false);
      createForm.resetFields();
      loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '域名配置创建失败'));
    }
  };

  const handleEdit = async (values: {
    encryptionPolicy?: string;
    preferredAlgorithm?: string;
    signingEnabled?: boolean;
    dkimEnabled?: boolean;
    active?: boolean;
  }) => {
    if (!selectedDomain) return;
    try {
      await domainConfigApi.update(selectedDomain.id, {
        encryptionPolicy: values.encryptionPolicy,
        preferredAlgorithm: values.preferredAlgorithm,
        signingEnabled: values.signingEnabled,
        dkimEnabled: values.dkimEnabled,
        active: values.active,
      });
      message.success('域名配置更新成功');
      setEditModalVisible(false);
      loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '域名配置更新失败'));
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await domainConfigApi.delete(id);
      message.success('域名配置已删除');
      loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '域名配置删除失败'));
    }
  };

  const showEditModal = (domain: DomainConfig) => {
    setSelectedDomain(domain);
    editForm.setFieldsValue({
      encryptionPolicy: domain.encryptionPolicy,
      preferredAlgorithm: domain.preferredAlgorithm || 'AUTO',
      signingEnabled: domain.signingEnabled,
      dkimEnabled: domain.dkimEnabled,
      active: domain.active,
    });
    setEditModalVisible(true);
  };

  const showDetail = (domain: DomainConfig) => {
    setSelectedDomain(domain);
    setDetailVisible(true);
  };

  const showCreateModal = () => {
    createForm.resetFields();
    createForm.setFieldsValue({
      localDomain: false,
      encryptionPolicy: 'ALLOW',
      preferredAlgorithm: 'AUTO',
      signingEnabled: false,
      dkimEnabled: false,
      active: true,
    });
    setCreateModalVisible(true);
  };

  const showMailAuthModal = async () => {
    setMailAuthModalVisible(true);
    const config = mailAuthConfig || await loadMailAuthConfig();
    mailAuthForm.setFieldsValue(initialMailAuthValues(config));
    const defaultDomain = dnsDomain || data.find((item) => item.localDomain)?.domain || data[0]?.domain || '';
    if (defaultDomain) {
      await loadDnsRecords(defaultDomain);
    } else {
      setDnsRecords([]);
    }
  };

  const handleMailAuthUpdate = async (values: MailAuthConfig & {
    clearDkimPrivateKeySecretRef?: boolean;
  }) => {
    try {
      const payload = {
        ...values,
        dmarcQuarantineRejectPolicy: values.dmarcFailureAction
          ? values.dmarcFailureAction !== 'LOG_ONLY'
          : values.dmarcQuarantineRejectPolicy,
      };
      const response = await mailAuthApi.updateConfig(payload);
      setMailAuthConfig(response.data.data);
      mailAuthForm.setFieldsValue(initialMailAuthValues(response.data.data));
      message.success('邮件认证配置已更新');
      if (dnsDomain) {
        await loadDnsRecords(dnsDomain);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '邮件认证配置更新失败'));
    }
  };

  const loadDnsRecords = async (domain: string) => {
    const normalized = normalizeDomain(domain);
    if (!normalized) {
      setDnsRecords([]);
      return;
    }
    try {
      const response = await mailAuthApi.dnsRecords(normalized);
      setDnsDomain(normalized);
      setDnsRecords(response.data.data);
    } catch (error) {
      message.error(getApiErrorMessage(error, 'DNS记录生成失败'));
    }
  };

  const copyText = async (value: string) => {
    if (!value) return;
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(value);
    }
    message.success('已复制');
  };

  const renderListInput = (fieldName: keyof MailAuthConfig, placeholder: string) => (
    <Form.List name={fieldName as string}>
      {(fields, { add, remove }) => (
        <Space direction="vertical" style={{ width: '100%' }}>
          {fields.map((field) => (
            <Space key={field.key} align="baseline" style={{ display: 'flex' }}>
              <Form.Item {...field} style={{ flex: 1, marginBottom: 0 }}>
                <Input placeholder={placeholder} />
              </Form.Item>
              <Button
                type="text"
                icon={<MinusCircleOutlined />}
                onClick={() => remove(field.name)}
              />
            </Space>
          ))}
          <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
            添加
          </Button>
        </Space>
      )}
    </Form.List>
  );

  const renderDnsRecords = (type?: string) => (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Space.Compact style={{ width: '100%' }}>
        <Select
          showSearch
          value={dnsDomain || undefined}
          placeholder="选择域名"
          onChange={(value) => loadDnsRecords(value)}
          style={{ minWidth: 220 }}
          options={data.map((item) => ({
            value: item.domain,
            label: item.domain,
          }))}
        />
        <Button onClick={() => dnsDomain && loadDnsRecords(dnsDomain)}>刷新</Button>
      </Space.Compact>
      {dnsRecords.filter((record) => !type || record.type === type).map((record) => (
        <Descriptions key={record.type} bordered column={1} size="small">
          <Descriptions.Item label={`${record.type}主机名`}>
            <Space>
              <span>{record.name}</span>
              <Button
                type="text"
                size="small"
                icon={<CopyOutlined />}
                onClick={() => copyText(record.name)}
              />
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label={`${record.type} TXT`}>
            <Space direction="vertical" style={{ width: '100%' }}>
              <Input.TextArea
                value={record.value}
                autoSize
                readOnly
                disabled={!record.available}
              />
              {record.available && (
                <Button
                  icon={<CopyOutlined />}
                  onClick={() => copyText(record.value)}
                >
                  复制
                </Button>
              )}
            </Space>
          </Descriptions.Item>
        </Descriptions>
      ))}
    </Space>
  );

  const columns = [
    {
      title: '域名',
      dataIndex: 'domain',
      key: 'domain',
      render: (text: string, record: DomainConfig) => (
        <Space>
          <GlobalOutlined />
          <strong>{text}</strong>
          {record.localDomain && <Tag color="blue">本地</Tag>}
        </Space>
      ),
    },
    {
      title: '加密策略',
      dataIndex: 'encryptionPolicyDisplayName',
      key: 'encryptionPolicy',
      render: (text: string, record: DomainConfig) => {
        const colorMap: Record<string, string> = {
          MANDATORY: 'red',
          ALLOW: 'green',
          NO_ENCRYPTION: 'default',
        };
        return (
          <Tag color={colorMap[record.encryptionPolicy] || 'default'}>
            <LockOutlined /> {text}
          </Tag>
        );
      },
    },
    {
      title: '算法偏好',
      dataIndex: 'preferredAlgorithmDisplayName',
      key: 'preferredAlgorithm',
      render: (_: string, record: DomainConfig) => {
        const colorMap: Record<string, string> = {
          AUTO: 'blue',
          GM_ONLY: 'error',
          STANDARD_ONLY: 'processing',
        };
        return (
          <Tag color={colorMap[record.preferredAlgorithm || 'AUTO'] || 'default'}>
            {record.preferredAlgorithmDisplayName || '自动选择'}
          </Tag>
        );
      },
    },
    {
      title: '签名',
      dataIndex: 'signingEnabled',
      key: 'signingEnabled',
      render: (enabled: boolean) =>
        enabled ? (
          <Tag color="success" icon={<SafetyOutlined />}>已启用</Tag>
        ) : (
          <Tag color="default">未启用</Tag>
        ),
    },
    {
      title: 'DKIM',
      dataIndex: 'dkimEnabled',
      key: 'dkimEnabled',
      render: (enabled: boolean) =>
        enabled ? (
          <Tag color="success">已启用</Tag>
        ) : (
          <Tag color="default">未启用</Tag>
        ),
    },
    {
      title: '状态',
      dataIndex: 'active',
      key: 'active',
      render: (active: boolean) =>
        active ? (
          <Tag color="green">启用</Tag>
        ) : (
          <Tag color="red">禁用</Tag>
        ),
    },
    {
      title: '操作',
      key: 'actions',
      render: (_: unknown, record: DomainConfig) => (
        <Space size="small">
          <Button
            type="text"
            icon={<EyeOutlined />}
            size="small"
            onClick={() => showDetail(record)}
          >
            详情
          </Button>
          <Button
            type="text"
            icon={<EditOutlined />}
            size="small"
            onClick={() => showEditModal(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确认删除该域名配置？"
            onConfirm={() => handleDelete(record.id)}
            okText="确认"
            cancelText="取消"
          >
            <Button type="text" danger icon={<DeleteOutlined />} size="small">
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Title level={3} style={{ margin: 0 }}>
          域名配置
        </Title>
        <Space>
          <Button icon={<MailOutlined />} onClick={showMailAuthModal}>
            邮件认证
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={showCreateModal}>
            添加域名
          </Button>
        </Space>
      </div>

      <Table
        columns={columns}
        dataSource={data}
        rowKey="id"
        loading={loading}
        pagination={{
          pageSize: 10,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`,
        }}
      />

      <Modal
        title="邮件认证"
        open={mailAuthModalVisible}
        onCancel={() => setMailAuthModalVisible(false)}
        footer={null}
        width={760}
      >
        <Form
          form={mailAuthForm}
          layout="vertical"
          onFinish={handleMailAuthUpdate}
          disabled={mailAuthLoading}
        >
          <Tabs
            items={[
              {
                key: 'runtime',
                label: '运行开关',
                children: (
                  <>
                    <Form.Item name="enabled" label="邮件认证" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                    <Form.Item name="dkimEnabled" label="DKIM" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                    <Form.Item name="spfEnabled" label="SPF" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                    <Form.Item name="dmarcEnabled" label="DMARC" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                    <Form.Item name="skipPrivateRelay" label="跳过内网中继SPF" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                    <Form.Item
                      name="authservId"
                      label="认证服务标识"
                      rules={[{ required: true, message: '请输入认证服务标识' }]}
                    >
                      <Input />
                    </Form.Item>
                  </>
                ),
              },
              {
                key: 'dkim',
                label: 'DKIM',
                children: (
                  <>
                    <Form.Item
                      name="dkimSelector"
                      label="Selector"
                      rules={[{ required: true, message: '请输入Selector' }]}
                    >
                      <Input />
                    </Form.Item>
                    <Form.Item name="dkimSignedHeaders" label="签名头">
                      <Select
                        mode="tags"
                        options={[
                          'from',
                          'to',
                          'subject',
                          'date',
                          'message-id',
                          'mime-version',
                          'content-type',
                        ].map((value) => ({ value, label: value }))}
                      />
                    </Form.Item>
                    <Form.Item name="dkimPrivateKeyPath" label="私钥路径">
                      <Input />
                    </Form.Item>
                    <Form.Item name="dkimPrivateKeySecretRef" label="私钥 Secret 引用">
                      <Input placeholder="env:SEALMAIL_DKIM_PRIVATE_KEY 或 file:/run/secrets/dkim.pem" />
                    </Form.Item>
                    <Form.Item name="clearDkimPrivateKeySecretRef" label="清空 Secret 引用" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                    {renderDnsRecords('DKIM')}
                  </>
                ),
              },
              {
                key: 'spf',
                label: 'SPF',
                children: (
                  <>
                    <Form.Item name="spfMaxDnsLookups" label="DNS查询上限">
                      <InputNumber min={0} max={50} style={{ width: '100%' }} />
                    </Form.Item>
                    <Form.Item name="spfUseA" label="A" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                    <Form.Item name="spfUseMx" label="MX" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                    <Form.Item label="IPv4">{renderListInput('spfIp4', '192.0.2.10')}</Form.Item>
                    <Form.Item label="IPv6">{renderListInput('spfIp6', '2001:db8::1')}</Form.Item>
                    <Form.Item label="Include">{renderListInput('spfIncludes', 'example.net')}</Form.Item>
                    <Form.Item name="spfAllPolicy" label="All策略">
                      <Select>
                        <Option value="-all">-all</Option>
                        <Option value="~all">~all</Option>
                        <Option value="?all">?all</Option>
                      </Select>
                    </Form.Item>
                    {renderDnsRecords('SPF')}
                  </>
                ),
              },
              {
                key: 'dmarc',
                label: 'DMARC',
                children: (
                  <>
                    <Form.Item name="dmarcPolicy" label="策略">
                      <Select>
                        <Option value="none">none</Option>
                        <Option value="quarantine">quarantine</Option>
                        <Option value="reject">reject</Option>
                      </Select>
                    </Form.Item>
                    <Form.Item name="dmarcAdkim" label="DKIM对齐">
                      <Select>
                        <Option value="r">r</Option>
                        <Option value="s">s</Option>
                      </Select>
                    </Form.Item>
                    <Form.Item name="dmarcAspf" label="SPF对齐">
                      <Select>
                        <Option value="r">r</Option>
                        <Option value="s">s</Option>
                      </Select>
                    </Form.Item>
                    <Form.Item name="dmarcPct" label="生效比例">
                      <InputNumber min={0} max={100} style={{ width: '100%' }} />
                    </Form.Item>
                    <Form.Item name="dmarcRua" label="RUA">
                      <Input />
                    </Form.Item>
                    <Form.Item name="dmarcRuf" label="RUF">
                      <Input />
                    </Form.Item>
                    <Form.Item name="dmarcFailureAction" label="失败处理">
                      <Select>
                        <Option value="APPLY_POLICY">按策略处理</Option>
                        <Option value="LOG_ONLY">仅记录</Option>
                      </Select>
                    </Form.Item>
                    {renderDnsRecords('DMARC')}
                  </>
                ),
              },
            ]}
          />
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setMailAuthModalVisible(false)}>取消</Button>
              <Button type="primary" htmlType="submit">
                保存
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Create Domain Modal */}
      <Modal
        title="添加域名配置"
        open={createModalVisible}
        onCancel={() => setCreateModalVisible(false)}
        footer={null}
        width={640}
      >
        <Form form={createForm} layout="vertical" onFinish={handleCreate}>
          <Form.Item
            name="domain"
            label="域名"
            rules={[
              { required: true, message: '请输入域名' },
              { type: 'string', pattern: /^([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}\.?$/, message: '请输入有效的域名' }
            ]}
          >
            <Input placeholder="例如: example.com" />
          </Form.Item>
          <Form.Item
            name="localDomain"
            label="本地域名"
            valuePropName="checked"
          >
            <Switch
              onChange={(checked) => {
                createForm.setFieldValue('encryptionPolicy', checked ? 'MANDATORY' : 'ALLOW');
              }}
            />
          </Form.Item>
          <Form.Item
            name="encryptionPolicy"
            label="加密策略"
            rules={[{ required: true, message: '请选择加密策略' }]}
          >
            <Select placeholder="请选择加密策略">
              <Option value="MANDATORY">强制加密</Option>
              <Option value="ALLOW">允许加密</Option>
              <Option value="NO_ENCRYPTION">不加密</Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="preferredAlgorithm"
            label="算法偏好"
            rules={[{ required: true, message: '请选择算法偏好' }]}
          >
            <Select placeholder="请选择算法偏好">
              <Option value="AUTO">自动选择</Option>
              <Option value="GM_ONLY">国密优先</Option>
              <Option value="STANDARD_ONLY">国际优先</Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="signingEnabled"
            label="启用邮件签名"
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
          <Form.Item
            name="dkimEnabled"
            label="启用DKIM签名"
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
          <Form.Item
            name="active"
            label="启用配置"
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setCreateModalVisible(false)}>取消</Button>
              <Button type="primary" htmlType="submit">
                创建
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Edit Domain Modal */}
      <Modal
        title="编辑域名配置"
        open={editModalVisible}
        onCancel={() => setEditModalVisible(false)}
        footer={null}
        width={640}
      >
        <Form form={editForm} layout="vertical" onFinish={handleEdit}>
          <Form.Item
            name="encryptionPolicy"
            label="加密策略"
            rules={[{ required: true, message: '请选择加密策略' }]}
          >
            <Select placeholder="请选择加密策略">
              <Option value="MANDATORY">强制加密</Option>
              <Option value="ALLOW">允许加密</Option>
              <Option value="NO_ENCRYPTION">不加密</Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="preferredAlgorithm"
            label="算法偏好"
            rules={[{ required: true, message: '请选择算法偏好' }]}
          >
            <Select placeholder="请选择算法偏好">
              <Option value="AUTO">自动选择</Option>
              <Option value="GM_ONLY">国密优先</Option>
              <Option value="STANDARD_ONLY">国际优先</Option>
            </Select>
          </Form.Item>
          <Form.Item
            name="signingEnabled"
            label="启用邮件签名"
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
          <Form.Item
            name="dkimEnabled"
            label="启用DKIM签名"
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
          <Form.Item
            name="active"
            label="启用配置"
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setEditModalVisible(false)}>取消</Button>
              <Button type="primary" htmlType="submit">
                保存
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Detail Drawer */}
      <Drawer
        title="域名配置详情"
        width={600}
        open={detailVisible}
        onClose={() => {
          setDetailVisible(false);
          setSelectedDomain(null);
        }}
      >
        {selectedDomain && (
          <Descriptions column={1} bordered>
            <Descriptions.Item label="域名">{selectedDomain.domain}</Descriptions.Item>
            <Descriptions.Item label="域名类型">
              {selectedDomain.localDomain ? (
                <Tag color="blue">本地域名</Tag>
              ) : (
                <Tag color="default">远程域名</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="加密策略">
              <Tag color={selectedDomain.encryptionPolicy === 'MANDATORY' ? 'red' : selectedDomain.encryptionPolicy === 'ALLOW' ? 'green' : 'default'}>
                {selectedDomain.encryptionPolicyDisplayName}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="算法偏好">
              <Tag color={selectedDomain.preferredAlgorithm === 'GM_ONLY' ? 'error' : selectedDomain.preferredAlgorithm === 'STANDARD_ONLY' ? 'processing' : 'blue'}>
                {selectedDomain.preferredAlgorithmDisplayName || '自动选择'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="邮件签名">
              {selectedDomain.signingEnabled ? (
                <Tag color="success">已启用</Tag>
              ) : (
                <Tag color="default">未启用</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="DKIM签名">
              {selectedDomain.dkimEnabled ? (
                <Tag color="success">已启用</Tag>
              ) : (
                <Tag color="default">未启用</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="配置状态">
              {selectedDomain.active ? (
                <Tag color="green">启用</Tag>
              ) : (
                <Tag color="red">禁用</Tag>
              )}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </div>
  );
};

export default DomainConfigs;
