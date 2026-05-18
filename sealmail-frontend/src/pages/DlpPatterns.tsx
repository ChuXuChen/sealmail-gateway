import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { dlpApi } from '../api/client';
import { getApiErrorMessage } from '../api/errors';
import type {
  DlpEdmDataset,
  DlpEvaluation,
  DlpFingerprintLibrary,
  DlpRule,
  DlpRuleType,
} from '../types';
import {
  AdvancedSection,
  DataTable,
  DlpActionTag,
  EnabledTag,
  FieldHint,
  PageHeader,
  PageShell,
  SectionPanel,
  StatusSummary,
  TaskSteps,
  confirmDeleteAction,
} from '../components/Page';

const { Text } = Typography;
const { TextArea } = Input;

const actionOptions = [
  { value: 'WARN', label: '告警' },
  { value: 'MUST_ENCRYPT', label: '强制加密' },
  { value: 'QUARANTINE', label: '隔离' },
  { value: 'BLOCK', label: '阻断' },
];

const typeOptions = [
  { value: 'PATTERN', label: '正则模式' },
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

const ruleTypeMeta: Record<string, { example: string; help: string; title: string }> = {
  PATTERN: {
    example: String.raw`\b\d{16,19}\b`,
    help: '适合格式稳定的敏感内容，保存前后端会校验正则表达式。',
    title: '正则模式',
  },
  KEYWORD: {
    example: '项目代号A\n报价底稿',
    help: '每行或逗号分隔一个关键词，用于简单文本命中。',
    title: '关键词',
  },
  BUILTIN: {
    example: '中国身份证 / 银行卡号 / API Key',
    help: '选择内置模板后无需手写模式，适合通用敏感类型快速启用。',
    title: '内置模板',
  },
  EDM: {
    example: '选择已导入的客户号、证件号、账号等哈希数据集',
    help: 'EDM 只保存规范化哈希，规则通过数据集 ID 进行精确匹配。',
    title: 'EDM 精确匹配',
  },
  FINGERPRINT: {
    example: '选择已生成片段指纹的合同、标书或设计文档库',
    help: '文档指纹适合发现邮件正文或附件中的敏感文档片段。',
    title: '文档指纹',
  },
};

const splitImportValues = (text?: string) => (
  text
    ? text
      .split(/[\n,，]/)
      .map((item) => item.trim())
    : []
);

const DlpPatterns: React.FC = () => {
  const [data, setData] = useState<DlpRule[]>([]);
  const [edmDatasets, setEdmDatasets] = useState<DlpEdmDataset[]>([]);
  const [fingerprintLibraries, setFingerprintLibraries] = useState<DlpFingerprintLibrary[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DlpRule | null>(null);
  const [ruleType, setRuleType] = useState<string>('PATTERN');
  const [testResult, setTestResult] = useState<DlpEvaluation | null>(null);
  const [edmImportText, setEdmImportText] = useState('');
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

  const showCreate = (type: DlpRuleType = 'PATTERN') => {
    setEditing(null);
    setRuleType(type);
    form.resetFields();
    form.setFieldsValue({
      type,
      action: 'WARN',
      severity: 5,
      priority: 100,
      minMatchCount: 1,
      maxEvidenceCount: 5,
      maskingStrategy: type === 'EDM' || type === 'FINGERPRINT' ? 'HASH_ONLY' : 'DEFAULT',
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

  const handleRuleTypeChange = (value: string) => {
    setRuleType(value);
    form.setFieldsValue({
      builtinCode: undefined,
      maskingStrategy: value === 'EDM' || value === 'FINGERPRINT' ? 'HASH_ONLY' : form.getFieldValue('maskingStrategy') || 'DEFAULT',
      pattern: undefined,
    });
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
      await dlpApi.createEdmDataset({
        description: values.description,
        enabled: values.enabled !== false,
        name: values.name,
      });
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
      setEdmImportText('');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '导入 EDM 数据失败'));
    }
  };

  const removeEdmDataset = async (id: string) => {
    try {
      await dlpApi.deleteEdmDataset(id);
      message.success('EDM 数据集已删除');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除 EDM 数据集失败'));
    }
  };

  const saveFingerprintLibrary = async (values: Partial<DlpFingerprintLibrary>) => {
    try {
      await dlpApi.createFingerprintLibrary({
        description: values.description,
        enabled: values.enabled !== false,
        name: values.name,
      });
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

  const removeFingerprintLibrary = async (id: string) => {
    try {
      await dlpApi.deleteFingerprintLibrary(id);
      message.success('文档指纹库已删除');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除文档指纹库失败'));
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
        body: values.body,
        direction: 'OUTBOUND',
        recipients: values.recipients?.split(',').map((item) => item.trim()).filter(Boolean),
        sender: values.sender,
        subject: values.subject,
      });
      setTestResult(response.data.data);
    } catch (error) {
      message.error(getApiErrorMessage(error, 'DLP 测试失败'));
    }
  };

  const edmOptions = edmDatasets.map((item) => ({ value: item.id, label: `${item.name} (${item.valueCount})` }));
  const fingerprintOptions = fingerprintLibraries.map((item) => ({ value: item.id, label: `${item.name} (${item.chunkCount})` }));

  const enabledRuleCount = data.filter((item) => item.enabled).length;
  const edmRuleCount = data.filter((item) => item.type === 'EDM').length;
  const fingerprintRuleCount = data.filter((item) => item.type === 'FINGERPRINT').length;
  const edmValueCount = edmDatasets.reduce((sum, item) => sum + item.valueCount, 0);
  const fingerprintChunkCount = fingerprintLibraries.reduce((sum, item) => sum + item.chunkCount, 0);
  const currentRuleMeta = ruleTypeMeta[ruleType] || ruleTypeMeta.PATTERN;

  const edmImportStats = useMemo(() => {
    const raw = splitImportValues(edmImportText);
    const values = raw.filter(Boolean);
    const unique = new Set(values.map((item) => item.toLowerCase()));

    return {
      duplicates: Math.max(values.length - unique.size, 0),
      empty: raw.length - values.length,
      total: values.length,
    };
  }, [edmImportText]);

  const ruleColumns: TableColumnsType<DlpRule> = [
    {
      title: '规则',
      dataIndex: 'name',
      key: 'name',
      width: 220,
      render: (_: string, record) => (
        <Space direction="vertical" size={2} className="min-width-zero">
          <Text strong ellipsis>{record.name}</Text>
          {record.description ? <Text type="secondary" className="text-micro" ellipsis>{record.description}</Text> : null}
        </Space>
      ),
    },
    { title: '类型', dataIndex: 'type', key: 'type', width: 110, render: (value: string) => <Tag>{ruleTypeMeta[value]?.title || value}</Tag> },
    {
      title: '匹配资产',
      dataIndex: 'pattern',
      key: 'pattern',
      width: 260,
      ellipsis: true,
      render: (_, record) => record.builtinCode || record.pattern || '-',
    },
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
    {
      title: '数据集',
      dataIndex: 'name',
      key: 'name',
      width: 220,
      render: (_: string, record) => (
        <Space direction="vertical" size={2} className="min-width-zero">
          <Text strong ellipsis>{record.name}</Text>
          {record.description ? <Text type="secondary" className="text-micro" ellipsis>{record.description}</Text> : null}
        </Space>
      ),
    },
    { title: '哈希值', dataIndex: 'valueCount', key: 'valueCount', width: 100 },
    { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 90, render: (enabled: boolean) => <EnabledTag enabled={enabled} /> },
    {
      title: '操作',
      key: 'actions',
      width: 110,
      render: (_: unknown, record: DlpEdmDataset) => (
        <Button
          type="link"
          size="small"
          danger
          icon={<DeleteOutlined />}
          onClick={() => confirmDeleteAction('确认删除该 EDM 数据集？', () => removeEdmDataset(record.id), record.name)}
        >
          删除
        </Button>
      ),
    },
  ];

  const fingerprintColumns: TableColumnsType<DlpFingerprintLibrary> = [
    {
      title: '指纹库',
      dataIndex: 'name',
      key: 'name',
      width: 220,
      render: (_: string, record) => (
        <Space direction="vertical" size={2} className="min-width-zero">
          <Text strong ellipsis>{record.name}</Text>
          {record.description ? <Text type="secondary" className="text-micro" ellipsis>{record.description}</Text> : null}
        </Space>
      ),
    },
    { title: '文档', dataIndex: 'documentCount', key: 'documentCount', width: 90 },
    { title: '片段', dataIndex: 'chunkCount', key: 'chunkCount', width: 90 },
    { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 90, render: (enabled: boolean) => <EnabledTag enabled={enabled} /> },
    {
      title: '操作',
      key: 'actions',
      width: 110,
      render: (_: unknown, record: DlpFingerprintLibrary) => (
        <Button
          type="link"
          size="small"
          danger
          icon={<DeleteOutlined />}
          onClick={() => confirmDeleteAction('确认删除该文档指纹库？', () => removeFingerprintLibrary(record.id), record.name)}
        >
          删除
        </Button>
      ),
    },
  ];

  const evidenceColumns: TableColumnsType<DlpEvaluation['evidence'][number]> = [
    { title: '规则', dataIndex: 'ruleName', key: 'ruleName', width: 180, ellipsis: true },
    { title: '位置', dataIndex: 'partKind', key: 'partKind', width: 120 },
    { title: '级别', dataIndex: 'severity', key: 'severity', width: 70 },
    { title: '动作', dataIndex: 'action', key: 'action', width: 110, render: (action: string) => <DlpActionTag action={action} /> },
    { title: '证据', dataIndex: 'maskedSnippet', key: 'maskedSnippet', ellipsis: true, render: (value?: string) => value || '-' },
  ];

  return (
    <PageShell className="dlp-patterns-page">
      <PageHeader
        title="DLP 规则资产中心"
        description="先维护可复用资产，再把规则绑定到策略；EDM、文档指纹和单次测试都在同一工作台完成。"
        actions={(
          <Space wrap>
            <Button icon={<ReloadOutlined />} loading={loading} onClick={loadData}>刷新</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => showCreate()}>添加规则</Button>
          </Space>
        )}
      />

      <StatusSummary
        items={[
          { key: 'rules', label: '规则总数', value: data.length, description: `${enabledRuleCount} 条启用`, tone: 'info' },
          { key: 'edm', label: 'EDM 数据集', value: edmDatasets.length, description: `${edmValueCount} 个哈希值`, tone: edmRuleCount > 0 ? 'success' : 'default' },
          { key: 'fingerprints', label: '文档指纹库', value: fingerprintLibraries.length, description: `${fingerprintChunkCount} 个片段`, tone: fingerprintRuleCount > 0 ? 'success' : 'default' },
          {
            key: 'test',
            label: '最近测试',
            value: testResult ? `${testResult.matchCount}` : '-',
            description: testResult ? `动作 ${testResult.action}，耗时 ${testResult.scanDurationMs}ms` : '尚未运行',
            tone: !testResult ? 'default' : testResult.matchCount > 0 ? 'warning' : 'success',
          },
        ]}
      />

      <SectionPanel
        title="规则清单"
        description="规则只定义识别方式和默认动作；实际生效范围在策略集中编排。"
      >
        <DataTable<DlpRule>
          rowKey="id"
          loading={loading}
          dataSource={data}
          columns={ruleColumns}
          scroll={{ x: 1160 }}
          pagination={{ pageSize: 20, total: data.length }}
        />
      </SectionPanel>

      <SectionPanel
        title="EDM 精确匹配流程"
        description="创建数据集后导入敏感值，最后在规则中选择该数据集。"
      >
        <div className="flow-stack">
          <TaskSteps
            items={[
              {
                key: 'create',
                status: edmDatasets.length > 0 ? 'done' : 'current',
                title: '创建数据集',
                description: '按数据域拆分，例如客户号、证件号或银行账号。',
              },
              {
                key: 'import',
                status: edmValueCount > 0 ? 'done' : edmDatasets.length > 0 ? 'current' : 'pending',
                title: '导入敏感值',
                description: '粘贴多行数据，前端预览条数、空行和重复值。',
              },
              {
                key: 'bind',
                status: edmRuleCount > 0 ? 'done' : edmValueCount > 0 ? 'current' : 'pending',
                title: '绑定到规则',
                description: '创建 EDM 类型规则，策略命中时只暴露脱敏证据。',
              },
            ]}
          />

          <Tabs
            className="config-tabs"
            items={[
              {
                key: 'datasets',
                label: `数据集 (${edmDatasets.length})`,
                children: (
                  <DataTable<DlpEdmDataset>
                    rowKey="id"
                    dataSource={edmDatasets}
                    columns={edmColumns}
                    pagination={{ pageSize: 10, total: edmDatasets.length }}
                  />
                ),
              },
              {
                key: 'create',
                label: '创建数据集',
                children: (
                  <SectionPanel compact>
                    <Form form={edmForm} layout="inline" onFinish={saveEdmDataset}>
                      <Form.Item name="name" rules={[{ required: true, message: '请输入名称' }]}>
                        <Input placeholder="数据集名称" />
                      </Form.Item>
                      <Form.Item name="description">
                        <Input placeholder="说明" />
                      </Form.Item>
                      <Form.Item name="enabled" valuePropName="checked" initialValue>
                        <Switch checkedChildren="启用" unCheckedChildren="停用" />
                      </Form.Item>
                      <Button type="primary" htmlType="submit">创建数据集</Button>
                    </Form>
                  </SectionPanel>
                ),
              },
              {
                key: 'import',
                label: '导入敏感值',
                children: (
                  <SectionPanel compact>
                    <Form form={edmImportForm} layout="vertical" onFinish={importEdmValues}>
                      <Form.Item name="datasetId" label="导入到" rules={[{ required: true, message: '请选择数据集' }]}>
                        <Select options={edmOptions} placeholder="选择数据集" />
                      </Form.Item>
                      <Form.Item name="text" label="精确匹配值">
                        <TextArea
                          rows={7}
                          onChange={(event) => setEdmImportText(event.target.value)}
                          placeholder="每行或逗号分隔一个敏感值；保存时只写入规范化哈希"
                        />
                      </Form.Item>
                      <StatusSummary
                        className="dlp-import-preview"
                        items={[
                          { key: 'total', label: '预览条数', value: edmImportStats.total, description: '非空值' },
                          { key: 'empty', label: '空行', value: edmImportStats.empty, description: '提交时忽略' },
                          { key: 'duplicates', label: '本次重复', value: edmImportStats.duplicates, description: '后端仍会去重', tone: edmImportStats.duplicates > 0 ? 'warning' : 'success' },
                        ]}
                      />
                      <Form.Item className="form-actions">
                        <Button type="primary" htmlType="submit" disabled={edmDatasets.length === 0}>导入哈希</Button>
                      </Form.Item>
                    </Form>
                  </SectionPanel>
                ),
              },
              {
                key: 'bind',
                label: '绑定规则',
                children: (
                  <Alert
                    action={<Button type="primary" onClick={() => showCreate('EDM')}>创建 EDM 规则</Button>}
                    message="把数据集绑定为 EDM 规则后，策略集才能引用规则组执行拦截、隔离或告警。"
                    showIcon
                    type={edmValueCount > 0 ? 'info' : 'warning'}
                  />
                ),
              },
            ]}
          />
        </div>
      </SectionPanel>

      <SectionPanel
        title="文档指纹库"
        description="导入可提取文本，为合同、标书、方案等长文档生成片段指纹。"
      >
        <div className="flow-stack">
          <DataTable<DlpFingerprintLibrary>
            rowKey="id"
            dataSource={fingerprintLibraries}
            columns={fingerprintColumns}
            pagination={{ pageSize: 10, total: fingerprintLibraries.length }}
          />
          <div className="split-grid">
            <SectionPanel compact title="创建指纹库">
              <Form form={fingerprintForm} layout="vertical" onFinish={saveFingerprintLibrary}>
                <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
                  <Input placeholder="指纹库名称" />
                </Form.Item>
                <Form.Item name="description" label="说明">
                  <Input />
                </Form.Item>
                <Form.Item name="enabled" label="启用" valuePropName="checked" initialValue>
                  <Switch />
                </Form.Item>
                <Form.Item className="form-actions">
                  <Button type="primary" htmlType="submit">创建指纹库</Button>
                </Form.Item>
              </Form>
            </SectionPanel>
            <SectionPanel compact title="导入文档文本" extra={<Button onClick={() => showCreate('FINGERPRINT')}>创建指纹规则</Button>}>
              <Form form={fingerprintImportForm} layout="vertical" onFinish={importFingerprintDocument}>
                <Form.Item name="libraryId" label="导入到" rules={[{ required: true, message: '请选择指纹库' }]}>
                  <Select options={fingerprintOptions} placeholder="选择指纹库" />
                </Form.Item>
                <Form.Item name="documentName" label="文档名称">
                  <Input placeholder="合同-2026-客户A" />
                </Form.Item>
                <Form.Item name="text" label="文档文本" rules={[{ required: true, message: '请输入可提取文本' }]}>
                  <TextArea rows={6} />
                </Form.Item>
                <Form.Item className="form-actions">
                  <Button type="primary" htmlType="submit" disabled={fingerprintLibraries.length === 0}>生成指纹</Button>
                </Form.Item>
              </Form>
            </SectionPanel>
          </div>
        </div>
      </SectionPanel>

      <SectionPanel
        title="单次测试"
        description="用当前规则和策略扫描一封出站邮件，结果按输入、命中、动作和证据拆开展示。"
      >
        <div className="split-grid">
          <Form form={testForm} layout="vertical" onFinish={runTest}>
            <Form.Item name="subject" label="主题"><Input /></Form.Item>
            <Form.Item name="sender" label="发件人"><Input placeholder="sender@example.com" /></Form.Item>
            <Form.Item name="recipients" label="收件人"><Input placeholder="a@example.com,b@example.net" /></Form.Item>
            <Form.Item name="body" label="正文"><TextArea rows={7} /></Form.Item>
            <Form.Item className="form-actions">
              <Space>
                <Button onClick={() => { testForm.resetFields(); setTestResult(null); }}>清空</Button>
                <Button type="primary" htmlType="submit">运行测试</Button>
              </Space>
            </Form.Item>
          </Form>

          <div className="flow-stack">
            {testResult ? (
              <>
                <StatusSummary
                  items={[
                    { key: 'matches', label: '命中', value: testResult.matchCount, description: '证据条数', tone: testResult.matchCount > 0 ? 'warning' : 'success' },
                    { key: 'severity', label: '最高级别', value: testResult.maxSeverity, description: '规则严重度' },
                    { key: 'action', label: '执行动作', value: <DlpActionTag action={testResult.action} />, description: testResult.monitorMode ? '监控模式' : '执行模式', tone: testResult.matchCount > 0 ? 'danger' : 'success' },
                    { key: 'duration', label: '扫描耗时', value: `${testResult.scanDurationMs}`, description: '毫秒' },
                  ]}
                />
                {testResult.warnings.length > 0 ? (
                  <Alert type="warning" showIcon message="扫描警告" description={testResult.warnings.join('；')} />
                ) : null}
                <DataTable<DlpEvaluation['evidence'][number]>
                  rowKey={(record) => record.id || `${record.ruleId}-${record.startOffset}-${record.endOffset}`}
                  dataSource={testResult.evidence}
                  columns={evidenceColumns}
                  pagination={false}
                  scroll={{ x: 680 }}
                />
              </>
            ) : (
              <Alert message="输入邮件内容后运行测试，结果会显示命中规则、执行动作和脱敏证据。" showIcon type="info" />
            )}
          </div>
        </div>
      </SectionPanel>

      <Modal title={editing ? '编辑 DLP 规则' : '添加 DLP 规则'} open={modalOpen} onCancel={() => setModalOpen(false)} footer={null} width={820}>
        <Form form={form} layout="vertical" onFinish={save}>
          <SectionPanel compact title="基础信息" description="名称用于策略组选择和审计检索，优先级越小越靠前。">
            <div className="split-grid">
              <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}><Input /></Form.Item>
              <Form.Item name="description" label="说明"><Input /></Form.Item>
            </div>
            <div className="split-grid">
              <Form.Item name="type" label="类型" rules={[{ required: true }]}>
                <Select options={typeOptions} onChange={handleRuleTypeChange} />
              </Form.Item>
              <Form.Item name="action" label="动作" rules={[{ required: true }]}>
                <Select options={actionOptions} />
              </Form.Item>
              <Form.Item name="severity" label="严重级别" rules={[{ required: true }]}><InputNumber min={1} max={10} className="full-width" /></Form.Item>
              <Form.Item name="priority" label="优先级" rules={[{ required: true }]}><InputNumber min={0} className="full-width" /></Form.Item>
            </div>
          </SectionPanel>

          <SectionPanel compact title={currentRuleMeta.title} description={currentRuleMeta.help} className="dlp-rule-editor-section">
            <FieldHint>示例：{currentRuleMeta.example}</FieldHint>
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
          </SectionPanel>

          <AdvancedSection title="内容范围与证据设置" description="默认扫描所有可提取内容；只有需要收窄范围时才配置这里。">
            <Form.Item name="contentKinds" label="内容范围">
              <Select mode="multiple" allowClear options={contentKindOptions} placeholder="为空表示扫描全部内容" />
            </Form.Item>
            <div className="split-grid">
              <Form.Item name="minMatchCount" label="最小命中"><InputNumber min={1} className="full-width" /></Form.Item>
              <Form.Item name="maxEvidenceCount" label="证据上限"><InputNumber min={1} max={100} className="full-width" /></Form.Item>
              <Form.Item name="maskingStrategy" label="脱敏策略"><Select options={maskingOptions} /></Form.Item>
              <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
            </div>
          </AdvancedSection>

          <Form.Item className="form-actions dlp-modal-actions">
            <Space><Button onClick={() => setModalOpen(false)}>取消</Button><Button type="primary" htmlType="submit">保存</Button></Space>
          </Form.Item>
        </Form>
      </Modal>
    </PageShell>
  );
};

export default DlpPatterns;
