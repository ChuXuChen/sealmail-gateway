import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Switch, Tag, message } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, PlayCircleOutlined } from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { dlpApi } from '../api/client';
import { getApiErrorMessage } from '../api/errors';
import type { DlpEdmDataset, DlpEvaluation, DlpFingerprintLibrary, DlpRule } from '../types';
import {
  DataTable,
  DlpActionTag,
  EnabledTag,
  PageHeader,
  PageShell,
  confirmDeleteAction,
} from '../components/Page';

const { TextArea } = Input;

const actionOptions = [
  { value: 'WARN', label: '告警' },
  { value: 'MUST_ENCRYPT', label: '强制加密' },
  { value: 'QUARANTINE', label: '隔离' },
  { value: 'BLOCK', label: '阻断' },
];

const typeOptions = [
  { value: 'PATTERN', label: '模式' },
  { value: 'KEYWORD', label: '关键词' },
  { value: 'BUILTIN', label: '内置模板' },
  { value: 'EDM', label: 'EDM 精确匹配' },
  { value: 'FINGERPRINT', label: '文档指纹' },
];

const builtinOptions = [
  { value: 'CN_ID_CARD', label: '中国身份证' },
  { value: 'BANK_CARD', label: '银行卡号' },
  { value: 'API_KEY', label: 'API Key / Token' },
  { value: 'PRIVATE_KEY', label: '私钥块' },
  { value: 'PHONE_CN', label: '中国手机号' },
];

const contentKindOptions = [
  { value: 'SUBJECT', label: '主题' },
  { value: 'HEADERS', label: '头部' },
  { value: 'BODY_TEXT', label: '纯文本正文' },
  { value: 'BODY_HTML', label: 'HTML 正文' },
  { value: 'ATTACHMENT_TEXT', label: '文本附件' },
  { value: 'ATTACHMENT_PDF', label: 'PDF' },
  { value: 'ATTACHMENT_ZIP_ENTRY', label: 'ZIP 文本' },
  { value: 'ATTACHMENT_METADATA', label: '附件元数据' },
];

const maskingOptions = [
  { value: 'DEFAULT', label: '默认' },
  { value: 'PARTIAL', label: '部分遮盖' },
  { value: 'FULL', label: '完全遮盖' },
  { value: 'HASH_ONLY', label: '仅哈希' },
  { value: 'EMAIL', label: '邮箱' },
  { value: 'SECRET', label: '密钥' },
];

const DlpPatterns: React.FC = () => {
  const [data, setData] = useState<DlpRule[]>([]);
  const [edmDatasets, setEdmDatasets] = useState<DlpEdmDataset[]>([]);
  const [fingerprintLibraries, setFingerprintLibraries] = useState<DlpFingerprintLibrary[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [testOpen, setTestOpen] = useState(false);
  const [edmOpen, setEdmOpen] = useState(false);
  const [fingerprintOpen, setFingerprintOpen] = useState(false);
  const [editing, setEditing] = useState<DlpRule | null>(null);
  const [ruleType, setRuleType] = useState<string>('PATTERN');
  const [testResult, setTestResult] = useState<DlpEvaluation | null>(null);
  const [form] = Form.useForm();
  const [testForm] = Form.useForm();
  const [edmForm] = Form.useForm();
  const [edmImportForm] = Form.useForm();
  const [fingerprintForm] = Form.useForm();
  const [fingerprintImportForm] = Form.useForm();

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [rulesResponse, edmResponse, fingerprintResponse] = await Promise.all([
        dlpApi.listRules(),
        dlpApi.listEdmDatasets(),
        dlpApi.listFingerprintLibraries(),
      ]);
      setData(rulesResponse.data.data);
      setEdmDatasets(edmResponse.data.data);
      setFingerprintLibraries(fingerprintResponse.data.data);
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载 DLP 规则失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void Promise.resolve().then(loadData);
  }, [loadData]);

  const showCreate = () => {
    setEditing(null);
    setRuleType('PATTERN');
    form.resetFields();
    form.setFieldsValue({
      type: 'PATTERN',
      action: 'WARN',
      severity: 5,
      priority: 100,
      minMatchCount: 1,
      maxEvidenceCount: 5,
      maskingStrategy: 'DEFAULT',
      contentKinds: [],
      enabled: true,
    });
    setModalOpen(true);
  };

  const showEdit = (record: DlpRule) => {
    setEditing(record);
    setRuleType(record.type);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const save = async (values: Partial<DlpRule>) => {
    try {
      const payload = {
        ...values,
        pattern: values.type === 'BUILTIN' ? undefined : values.pattern,
      };
      if (editing) {
        await dlpApi.updateRule(editing.id, payload);
      } else {
        await dlpApi.createRule(payload as Omit<DlpRule, 'id' | 'createdAt' | 'updatedAt'>);
      }
      message.success('DLP 规则已保存');
      setModalOpen(false);
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '保存 DLP 规则失败'));
    }
  };

  const saveEdmDataset = async (values: Partial<DlpEdmDataset>) => {
    try {
      await dlpApi.createEdmDataset({ name: values.name, description: values.description, enabled: values.enabled !== false });
      message.success('EDM 数据集已创建');
      edmForm.resetFields();
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '创建 EDM 数据集失败'));
    }
  };

  const importEdmValues = async (values: { datasetId: string; text?: string }) => {
    try {
      const response = await dlpApi.importEdmDataset(values.datasetId, { text: values.text });
      message.success(`导入 ${response.data.data.importedCount} 条，重复 ${response.data.data.duplicateCount} 条`);
      edmImportForm.resetFields();
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '导入 EDM 数据失败'));
    }
  };

  const saveFingerprintLibrary = async (values: Partial<DlpFingerprintLibrary>) => {
    try {
      await dlpApi.createFingerprintLibrary({ name: values.name, description: values.description, enabled: values.enabled !== false });
      message.success('文档指纹库已创建');
      fingerprintForm.resetFields();
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '创建文档指纹库失败'));
    }
  };

  const importFingerprintDocument = async (values: { libraryId: string; documentName?: string; text?: string }) => {
    try {
      const response = await dlpApi.importFingerprintDocument(values.libraryId, {
        documentName: values.documentName,
        text: values.text,
      });
      message.success(`导入 ${response.data.data.importedCount} 个片段`);
      fingerprintImportForm.resetFields();
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '导入文档指纹失败'));
    }
  };

  const remove = async (id: string) => {
    try {
      await dlpApi.deleteRule(id);
      message.success('DLP 规则已删除');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除 DLP 规则失败'));
    }
  };

  const runTest = async (values: { subject?: string; body?: string; sender?: string; recipients?: string }) => {
    try {
      const response = await dlpApi.test({
        subject: values.subject,
        body: values.body,
        sender: values.sender,
        recipients: values.recipients?.split(',').map((item) => item.trim()).filter(Boolean),
        direction: 'OUTBOUND',
      });
      setTestResult(response.data.data);
    } catch (error) {
      message.error(getApiErrorMessage(error, 'DLP 测试失败'));
    }
  };

  const columns: TableColumnsType<DlpRule> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 170, ellipsis: true },
    { title: '类型', dataIndex: 'type', key: 'type', width: 95, render: (value: string) => <Tag>{value}</Tag> },
    { title: '模式', dataIndex: 'pattern', key: 'pattern', width: 220, ellipsis: true, render: (_, record) => record.builtinCode || record.pattern || '-' },
    {
      title: '动作',
      dataIndex: 'action',
      key: 'action',
      width: 110,
      render: (action: string) => <DlpActionTag action={action} />,
    },
    { title: '级别', dataIndex: 'severity', key: 'severity', width: 70 },
    { title: '证据', dataIndex: 'maxEvidenceCount', key: 'maxEvidenceCount', width: 70 },
    { title: '优先级', dataIndex: 'priority', key: 'priority', width: 80 },
    { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 80, render: (enabled: boolean) => <EnabledTag enabled={enabled} /> },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_: unknown, record: DlpRule) => (
        <Space size={4} wrap={false}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => showEdit(record)}>编辑</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => confirmDeleteAction('确认删除该规则？', () => remove(record.id), record.name)}>删除</Button>
        </Space>
      ),
    },
  ];

  const edmColumns: TableColumnsType<DlpEdmDataset> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 180, ellipsis: true },
    { title: '哈希值', dataIndex: 'valueCount', key: 'valueCount', width: 100 },
    { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 90, render: (enabled: boolean) => <EnabledTag enabled={enabled} /> },
  ];

  const fingerprintColumns: TableColumnsType<DlpFingerprintLibrary> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 180, ellipsis: true },
    { title: '文档', dataIndex: 'documentCount', key: 'documentCount', width: 90 },
    { title: '片段', dataIndex: 'chunkCount', key: 'chunkCount', width: 90 },
    { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 90, render: (enabled: boolean) => <EnabledTag enabled={enabled} /> },
  ];

  const edmOptions = edmDatasets.map((item) => ({ value: item.id, label: `${item.name} (${item.valueCount})` }));
  const fingerprintOptions = fingerprintLibraries.map((item) => ({ value: item.id, label: `${item.name} (${item.chunkCount})` }));

  return (
    <PageShell>
      <PageHeader
        title="DLP 规则库"
        description="维护模式、关键词、内置模板、EDM 和文档指纹规则，并配置内容范围、动作和证据脱敏。"
        actions={(
          <Space wrap>
            <Button icon={<PlayCircleOutlined />} onClick={() => { setTestResult(null); setTestOpen(true); }}>测试</Button>
            <Button onClick={() => setEdmOpen(true)}>EDM 数据集</Button>
            <Button onClick={() => setFingerprintOpen(true)}>文档指纹库</Button>
            <Button icon={<ReloadOutlined />} loading={loading} onClick={loadData}>刷新</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={showCreate}>添加规则</Button>
          </Space>
        )}
      />

      <DataTable<DlpRule>
        rowKey="id"
        loading={loading}
        dataSource={data}
        columns={columns}
        scroll={{ x: 1120 }}
        pagination={{ pageSize: 20, total: data.length }}
      />

      <Modal title={editing ? '编辑 DLP 规则' : '添加 DLP 规则'} open={modalOpen} onCancel={() => setModalOpen(false)} footer={null} width={760}>
        <Form form={form} layout="vertical" onFinish={save}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}><Input /></Form.Item>
          <Form.Item name="description" label="说明"><Input /></Form.Item>
          <Space style={{ width: '100%' }} size="large" wrap>
            <Form.Item name="type" label="类型" rules={[{ required: true }]} style={{ minWidth: 160 }}>
              <Select options={typeOptions} onChange={setRuleType} />
            </Form.Item>
            <Form.Item name="action" label="动作" rules={[{ required: true }]} style={{ minWidth: 160 }}>
              <Select options={actionOptions} />
            </Form.Item>
            <Form.Item name="severity" label="严重级别" rules={[{ required: true }]}><InputNumber min={1} max={10} /></Form.Item>
            <Form.Item name="priority" label="优先级" rules={[{ required: true }]}><InputNumber min={0} /></Form.Item>
          </Space>
          {ruleType === 'EDM' ? (
            <Form.Item name="pattern" label="EDM 数据集" rules={[{ required: true, message: '请选择 EDM 数据集' }]}>
              <Select options={edmOptions} placeholder="选择已导入哈希索引的数据集" />
            </Form.Item>
          ) : ruleType === 'FINGERPRINT' ? (
            <Form.Item name="pattern" label="文档指纹库" rules={[{ required: true, message: '请选择文档指纹库' }]}>
              <Select options={fingerprintOptions} placeholder="选择已导入片段指纹的文档库" />
            </Form.Item>
          ) : ruleType === 'BUILTIN' ? (
            <Form.Item name="builtinCode" label="内置规则" rules={[{ required: true, message: '请选择内置规则' }]}>
              <Select options={builtinOptions} />
            </Form.Item>
          ) : (
            <Form.Item name="pattern" label={ruleType === 'KEYWORD' ? '关键词' : '检测模式'} rules={[{ required: true, message: '请输入检测模式' }]}>
              <TextArea rows={4} />
            </Form.Item>
          )}
          <Form.Item name="contentKinds" label="内容范围">
            <Select mode="multiple" allowClear options={contentKindOptions} placeholder="为空表示扫描全部内容" />
          </Form.Item>
          <Space style={{ width: '100%' }} size="large" wrap>
            <Form.Item name="minMatchCount" label="最小命中"><InputNumber min={1} /></Form.Item>
            <Form.Item name="maxEvidenceCount" label="证据上限"><InputNumber min={1} max={100} /></Form.Item>
            <Form.Item name="maskingStrategy" label="脱敏策略" style={{ minWidth: 160 }}><Select options={maskingOptions} /></Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
          </Space>
          <Form.Item style={{ textAlign: 'right', marginBottom: 0 }}>
            <Space><Button onClick={() => setModalOpen(false)}>取消</Button><Button type="primary" htmlType="submit">保存</Button></Space>
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="DLP 单次测试" open={testOpen} onCancel={() => setTestOpen(false)} footer={null} width={720}>
        <Form form={testForm} layout="vertical" onFinish={runTest}>
          <Form.Item name="subject" label="主题"><Input /></Form.Item>
          <Form.Item name="sender" label="发件人"><Input placeholder="sender@example.com" /></Form.Item>
          <Form.Item name="recipients" label="收件人"><Input placeholder="a@example.com,b@example.net" /></Form.Item>
          <Form.Item name="body" label="正文"><TextArea rows={6} /></Form.Item>
          {testResult && (
            <Alert
              style={{ marginBottom: 16 }}
              type={testResult.matchCount > 0 ? 'warning' : 'success'}
              showIcon
              message={`动作 ${testResult.action}，命中 ${testResult.matchCount} 条，最高级别 ${testResult.maxSeverity}`}
              description={testResult.evidence.map((item) => `${item.ruleName}: ${item.maskedSnippet}`).join('\n') || '未命中'}
            />
          )}
          <Form.Item style={{ textAlign: 'right', marginBottom: 0 }}>
            <Space><Button onClick={() => setTestOpen(false)}>关闭</Button><Button type="primary" htmlType="submit">运行测试</Button></Space>
          </Form.Item>
        </Form>
      </Modal>

      <Modal title="EDM 数据集" open={edmOpen} onCancel={() => setEdmOpen(false)} footer={null} width={820}>
        <DataTable<DlpEdmDataset>
          rowKey="id"
          dataSource={edmDatasets}
          columns={edmColumns}
          pagination={{ pageSize: 6, total: edmDatasets.length }}
        />
        <Form form={edmForm} layout="inline" onFinish={saveEdmDataset} style={{ marginTop: 16 }}>
          <Form.Item name="name" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="数据集名称" />
          </Form.Item>
          <Form.Item name="description">
            <Input placeholder="说明" />
          </Form.Item>
          <Form.Item name="enabled" valuePropName="checked" initialValue>
            <Switch />
          </Form.Item>
          <Button type="primary" htmlType="submit">创建</Button>
        </Form>
        <Form form={edmImportForm} layout="vertical" onFinish={importEdmValues} style={{ marginTop: 16 }}>
          <Form.Item name="datasetId" label="导入到" rules={[{ required: true, message: '请选择数据集' }]}>
            <Select options={edmOptions} />
          </Form.Item>
          <Form.Item name="text" label="精确匹配值">
            <TextArea rows={5} placeholder="每行或逗号分隔一个敏感值；保存时只写入规范化哈希" />
          </Form.Item>
          <Button htmlType="submit">导入哈希</Button>
        </Form>
      </Modal>

      <Modal title="文档指纹库" open={fingerprintOpen} onCancel={() => setFingerprintOpen(false)} footer={null} width={820}>
        <DataTable<DlpFingerprintLibrary>
          rowKey="id"
          dataSource={fingerprintLibraries}
          columns={fingerprintColumns}
          pagination={{ pageSize: 6, total: fingerprintLibraries.length }}
        />
        <Form form={fingerprintForm} layout="inline" onFinish={saveFingerprintLibrary} style={{ marginTop: 16 }}>
          <Form.Item name="name" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="指纹库名称" />
          </Form.Item>
          <Form.Item name="description">
            <Input placeholder="说明" />
          </Form.Item>
          <Form.Item name="enabled" valuePropName="checked" initialValue>
            <Switch />
          </Form.Item>
          <Button type="primary" htmlType="submit">创建</Button>
        </Form>
        <Form form={fingerprintImportForm} layout="vertical" onFinish={importFingerprintDocument} style={{ marginTop: 16 }}>
          <Form.Item name="libraryId" label="导入到" rules={[{ required: true, message: '请选择指纹库' }]}>
            <Select options={fingerprintOptions} />
          </Form.Item>
          <Form.Item name="documentName" label="文档名称">
            <Input />
          </Form.Item>
          <Form.Item name="text" label="文档文本" rules={[{ required: true, message: '请输入可提取文本' }]}>
            <TextArea rows={6} />
          </Form.Item>
          <Button htmlType="submit">生成指纹</Button>
        </Form>
      </Modal>
    </PageShell>
  );
};

export default DlpPatterns;
