import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Switch, Tag, message } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, PlayCircleOutlined } from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { dlpApi } from '../api/client';
import { getApiErrorMessage } from '../api/errors';
import type { DlpEvaluation, DlpRule } from '../types';
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
  { value: 'REGEX', label: '正则' },
  { value: 'KEYWORD', label: '关键词' },
  { value: 'BUILTIN', label: '内置' },
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
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [testOpen, setTestOpen] = useState(false);
  const [editing, setEditing] = useState<DlpRule | null>(null);
  const [ruleType, setRuleType] = useState<string>('REGEX');
  const [testResult, setTestResult] = useState<DlpEvaluation | null>(null);
  const [form] = Form.useForm();
  const [testForm] = Form.useForm();

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await dlpApi.listRules();
      setData(response.data.data);
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
    setRuleType('REGEX');
    form.resetFields();
    form.setFieldsValue({
      type: 'REGEX',
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

  return (
    <PageShell>
      <PageHeader
        title="DLP 规则库"
        description="维护正则、关键词和内置检测规则，并配置内容范围、动作和证据脱敏。"
        actions={(
          <Space wrap>
            <Button icon={<PlayCircleOutlined />} onClick={() => { setTestResult(null); setTestOpen(true); }}>测试</Button>
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
          {ruleType === 'BUILTIN' ? (
            <Form.Item name="builtinCode" label="内置规则" rules={[{ required: true, message: '请选择内置规则' }]}>
              <Select options={builtinOptions} />
            </Form.Item>
          ) : (
            <Form.Item name="pattern" label={ruleType === 'KEYWORD' ? '关键词' : '正则表达式'} rules={[{ required: true, message: '请输入检测模式' }]}>
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
    </PageShell>
  );
};

export default DlpPatterns;
