import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Checkbox, Form, Input, InputNumber, Modal, Select, Space, Switch, Tabs, Tag, message } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { dlpApi } from '../api/client';
import { getApiErrorMessage } from '../api/errors';
import type { DlpPolicy, DlpRule, DlpRuleGroup, DlpSelection, DlpUbaSenderRisk } from '../types';
import { DataTable, EnabledTag, PageHeader, PageShell, confirmDeleteAction, formatDateTime } from '../components/Page';

const modeLabels: Record<string, string> = { MONITOR: '监控', ENFORCE: '执行' };
const scopeLabels: Record<string, string> = { GLOBAL: '全局', SENDER_DOMAIN: '发件域', RECIPIENT_DOMAIN: '收件域' };

const DlpSelection: React.FC = () => {
  const [rules, setRules] = useState<DlpRule[]>([]);
  const [groups, setGroups] = useState<DlpRuleGroup[]>([]);
  const [policies, setPolicies] = useState<DlpPolicy[]>([]);
  const [selections, setSelections] = useState<DlpSelection[]>([]);
  const [ubaRisks, setUbaRisks] = useState<DlpUbaSenderRisk[]>([]);
  const [loading, setLoading] = useState(false);
  const [groupOpen, setGroupOpen] = useState(false);
  const [policyOpen, setPolicyOpen] = useState(false);
  const [legacyOpen, setLegacyOpen] = useState(false);
  const [editingGroup, setEditingGroup] = useState<DlpRuleGroup | null>(null);
  const [editingPolicy, setEditingPolicy] = useState<DlpPolicy | null>(null);
  const [editingSelection, setEditingSelection] = useState<DlpSelection | null>(null);
  const [scopeType, setScopeType] = useState<string>('GLOBAL');
  const [patternMode, setPatternMode] = useState<'ALL' | 'SELECTED'>('ALL');
  const [groupForm] = Form.useForm();
  const [policyForm] = Form.useForm();
  const [legacyForm] = Form.useForm();

  const ruleOptions = useMemo(() => rules.map((rule) => ({ value: rule.id, label: rule.name })), [rules]);
  const groupOptions = useMemo(() => groups.map((group) => ({ value: group.id, label: group.name })), [groups]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [rulesResponse, groupsResponse, policiesResponse, selectionsResponse, ubaResponse] = await Promise.all([
        dlpApi.listRules(),
        dlpApi.listRuleGroups(),
        dlpApi.listPolicies(),
        dlpApi.listSelections(),
        dlpApi.listUbaSenderRisks({ limit: 100 }),
      ]);
      setRules(rulesResponse.data.data);
      setGroups(groupsResponse.data.data);
      setPolicies(policiesResponse.data.data);
      setSelections(selectionsResponse.data.data);
      setUbaRisks(ubaResponse.data.data);
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载 DLP 策略配置失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void Promise.resolve().then(loadData);
  }, [loadData]);

  const showCreateGroup = () => {
    setEditingGroup(null);
    groupForm.resetFields();
    groupForm.setFieldsValue({ enabled: true, priority: 100, ruleIds: [] });
    setGroupOpen(true);
  };

  const showEditGroup = (record: DlpRuleGroup) => {
    setEditingGroup(record);
    groupForm.setFieldsValue(record);
    setGroupOpen(true);
  };

  const saveGroup = async (values: Partial<DlpRuleGroup>) => {
    try {
      if (editingGroup) {
        await dlpApi.updateRuleGroup(editingGroup.id, values);
      } else {
        await dlpApi.createRuleGroup(values as Omit<DlpRuleGroup, 'id' | 'createdAt' | 'updatedAt'>);
      }
      message.success('规则组已保存');
      setGroupOpen(false);
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '保存规则组失败'));
    }
  };

  const removeGroup = async (id: string) => {
    try {
      await dlpApi.deleteRuleGroup(id);
      message.success('规则组已删除');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除规则组失败'));
    }
  };

  const showCreatePolicy = () => {
    setEditingPolicy(null);
    policyForm.resetFields();
    policyForm.setFieldsValue({
      mode: 'ENFORCE',
      enabled: true,
      priority: 100,
      attachmentRequired: false,
      senderDomains: [],
      recipientDomains: [],
      senderAddressPatterns: [],
      recipientAddressPatterns: [],
      ruleGroupIds: [],
    });
    setPolicyOpen(true);
  };

  const showEditPolicy = (record: DlpPolicy) => {
    setEditingPolicy(record);
    policyForm.setFieldsValue(record);
    setPolicyOpen(true);
  };

  const savePolicy = async (values: Partial<DlpPolicy>) => {
    const payload = {
      ...values,
      senderDomains: values.senderDomains || [],
      recipientDomains: values.recipientDomains || [],
      senderAddressPatterns: values.senderAddressPatterns || [],
      recipientAddressPatterns: values.recipientAddressPatterns || [],
      ruleGroupIds: values.ruleGroupIds || [],
      attachmentRequired: !!values.attachmentRequired,
      enabled: values.enabled !== false,
      priority: values.priority ?? 100,
      mode: values.mode || 'ENFORCE',
    };
    try {
      if (editingPolicy) {
        await dlpApi.updatePolicy(editingPolicy.id, payload);
      } else {
        await dlpApi.createPolicy(payload as Omit<DlpPolicy, 'id' | 'createdAt' | 'updatedAt'>);
      }
      message.success('策略已保存');
      setPolicyOpen(false);
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '保存策略失败'));
    }
  };

  const removePolicy = async (id: string) => {
    try {
      await dlpApi.deletePolicy(id);
      message.success('策略已删除');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除策略失败'));
    }
  };

  const showCreateLegacy = () => {
    setEditingSelection(null);
    setScopeType('GLOBAL');
    setPatternMode('ALL');
    legacyForm.resetFields();
    legacyForm.setFieldsValue({ scopeType: 'GLOBAL', patternMode: 'ALL', patternIds: [], enabled: true });
    setLegacyOpen(true);
  };

  const showEditLegacy = (record: DlpSelection) => {
    setEditingSelection(record);
    setScopeType(record.scopeType);
    const mode = record.patternIds == null ? 'ALL' : 'SELECTED';
    setPatternMode(mode);
    legacyForm.setFieldsValue({ ...record, patternMode: mode, patternIds: record.patternIds || [] });
    setLegacyOpen(true);
  };

  const saveLegacy = async (values: DlpSelection) => {
    const payload = {
      ...values,
      scopeValue: values.scopeType === 'GLOBAL' ? undefined : values.scopeValue,
      patternIds: values.patternMode === 'ALL' ? undefined : values.patternIds,
    };
    try {
      if (editingSelection) {
        await dlpApi.updateSelection(editingSelection.id, payload);
      } else {
        await dlpApi.createSelection(payload);
      }
      message.success('兼容范围已保存');
      setLegacyOpen(false);
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '保存兼容范围失败'));
    }
  };

  const removeLegacy = async (id: string) => {
    try {
      await dlpApi.deleteSelection(id);
      message.success('兼容范围已删除');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除兼容范围失败'));
    }
  };

  const groupColumns: TableColumnsType<DlpRuleGroup> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 180, ellipsis: true },
    { title: '规则数', dataIndex: 'ruleIds', key: 'ruleIds', width: 90, render: (ids: string[]) => ids.length },
    { title: '优先级', dataIndex: 'priority', key: 'priority', width: 90 },
    { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 90, render: (enabled: boolean) => <EnabledTag enabled={enabled} /> },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_: unknown, record: DlpRuleGroup) => (
        <Space size={4} wrap={false}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => showEditGroup(record)}>编辑</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => confirmDeleteAction('确认删除该规则组？', () => removeGroup(record.id), record.name)}>删除</Button>
        </Space>
      ),
    },
  ];

  const policyColumns: TableColumnsType<DlpPolicy> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 180, ellipsis: true },
    { title: '模式', dataIndex: 'mode', key: 'mode', width: 90, render: (value: string) => <Tag color={value === 'ENFORCE' ? 'red' : 'blue'}>{modeLabels[value] || value}</Tag> },
    { title: '方向', dataIndex: 'direction', key: 'direction', width: 90, render: (value?: string) => value || '*' },
    { title: '规则组', dataIndex: 'ruleGroupIds', key: 'ruleGroupIds', width: 90, render: (ids: string[]) => ids.length },
    { title: '附件', dataIndex: 'attachmentRequired', key: 'attachmentRequired', width: 80, render: (value: boolean) => value ? <Tag>需要</Tag> : '-' },
    { title: '优先级', dataIndex: 'priority', key: 'priority', width: 90 },
    { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 90, render: (enabled: boolean) => <EnabledTag enabled={enabled} /> },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_: unknown, record: DlpPolicy) => (
        <Space size={4} wrap={false}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => showEditPolicy(record)}>编辑</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => confirmDeleteAction('确认删除该策略？', () => removePolicy(record.id), record.name)}>删除</Button>
        </Space>
      ),
    },
  ];

  const legacyColumns: TableColumnsType<DlpSelection> = [
    { title: '范围', dataIndex: 'scopeType', key: 'scopeType', width: 120, render: (value: string) => scopeLabels[value] || value },
    { title: '值', dataIndex: 'scopeValue', key: 'scopeValue', width: 220, render: (value?: string) => value || '*' },
    { title: '规则', dataIndex: 'patternIds', key: 'patternIds', width: 120, render: (ids?: string[] | null) => ids == null ? <Tag color="blue">全部规则</Tag> : `${ids.length} 条` },
    { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 90, render: (enabled: boolean) => <EnabledTag enabled={enabled} /> },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      render: (_: unknown, record: DlpSelection) => (
        <Space size={4} wrap={false}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => showEditLegacy(record)}>编辑</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => confirmDeleteAction('确认删除该范围？', () => removeLegacy(record.id), record.scopeValue || scopeLabels[record.scopeType])}>删除</Button>
        </Space>
      ),
    },
  ];

  const ubaColumns: TableColumnsType<DlpUbaSenderRisk> = [
    { title: '发件人', dataIndex: 'senderEmail', key: 'senderEmail', width: 220, ellipsis: true },
    { title: '风险', dataIndex: 'riskLevel', key: 'riskLevel', width: 90, render: (value: string) => <Tag color={value === 'HIGH' ? 'red' : value === 'MEDIUM' ? 'orange' : 'green'}>{value}</Tag> },
    { title: '外发', dataIndex: 'outboundMessages', key: 'outboundMessages', width: 90 },
    { title: '外部域', dataIndex: 'externalDomainCount', key: 'externalDomainCount', width: 90 },
    { title: 'DLP 命中', dataIndex: 'dlpHitCount', key: 'dlpHitCount', width: 100 },
    { title: '高风险', dataIndex: 'highRiskCount', key: 'highRiskCount', width: 90 },
    { title: '原因', dataIndex: 'lastReasons', key: 'lastReasons', ellipsis: true, render: (values: string[]) => values.join('；') || '-' },
    { title: '最近活动', dataIndex: 'lastSeenAt', key: 'lastSeenAt', width: 170, render: formatDateTime },
  ];

  return (
    <PageShell>
      <PageHeader
        title="DLP 策略集"
        description="配置规则组和策略条件；保留旧生效范围作为兼容层。"
        actions={<Button icon={<ReloadOutlined />} loading={loading} onClick={loadData}>刷新</Button>}
      />

      <Tabs
        items={[
          {
            key: 'policies',
            label: '策略',
            children: (
              <>
                <Space style={{ marginBottom: 12 }}><Button type="primary" icon={<PlusOutlined />} onClick={showCreatePolicy}>添加策略</Button></Space>
                <DataTable<DlpPolicy> rowKey="id" loading={loading} dataSource={policies} columns={policyColumns} scroll={{ x: 980 }} pagination={{ pageSize: 20, total: policies.length }} />
              </>
            ),
          },
          {
            key: 'groups',
            label: '规则组',
            children: (
              <>
                <Space style={{ marginBottom: 12 }}><Button type="primary" icon={<PlusOutlined />} onClick={showCreateGroup}>添加规则组</Button></Space>
                <DataTable<DlpRuleGroup> rowKey="id" loading={loading} dataSource={groups} columns={groupColumns} scroll={{ x: 720 }} pagination={{ pageSize: 20, total: groups.length }} />
              </>
            ),
          },
          {
            key: 'legacy',
            label: '兼容范围',
            children: (
              <>
                <Space style={{ marginBottom: 12 }}><Button icon={<PlusOutlined />} onClick={showCreateLegacy}>添加范围</Button></Space>
                <DataTable<DlpSelection> rowKey="id" loading={loading} dataSource={selections} columns={legacyColumns} scroll={{ x: 720 }} pagination={{ pageSize: 20, total: selections.length }} />
              </>
            ),
          },
          {
            key: 'uba',
            label: 'UBA 风险',
            children: (
              <DataTable<DlpUbaSenderRisk> rowKey="senderEmail" loading={loading} dataSource={ubaRisks} columns={ubaColumns} scroll={{ x: 1040 }} pagination={{ pageSize: 20, total: ubaRisks.length }} />
            ),
          },
        ]}
      />

      <Modal title={editingGroup ? '编辑规则组' : '添加规则组'} open={groupOpen} onCancel={() => setGroupOpen(false)} footer={null} width={640}>
        <Form form={groupForm} layout="vertical" onFinish={saveGroup}>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="description" label="说明"><Input /></Form.Item>
          <Form.Item name="ruleIds" label="规则"><Select mode="multiple" options={ruleOptions} /></Form.Item>
          <Space size="large" wrap>
            <Form.Item name="priority" label="优先级"><InputNumber min={0} /></Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
          </Space>
          <Form.Item style={{ textAlign: 'right', marginBottom: 0 }}><Space><Button onClick={() => setGroupOpen(false)}>取消</Button><Button type="primary" htmlType="submit">保存</Button></Space></Form.Item>
        </Form>
      </Modal>

      <Modal title={editingPolicy ? '编辑策略' : '添加策略'} open={policyOpen} onCancel={() => setPolicyOpen(false)} footer={null} width={760}>
        <Form form={policyForm} layout="vertical" onFinish={savePolicy}>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="description" label="说明"><Input /></Form.Item>
          <Space style={{ width: '100%' }} size="large" wrap>
            <Form.Item name="mode" label="模式" rules={[{ required: true }]} style={{ minWidth: 140 }}><Select options={[{ value: 'ENFORCE', label: '执行' }, { value: 'MONITOR', label: '监控' }]} /></Form.Item>
            <Form.Item name="direction" label="方向" style={{ minWidth: 140 }}><Select allowClear options={[{ value: 'OUTBOUND', label: '出站' }, { value: 'INBOUND', label: '入站' }]} /></Form.Item>
            <Form.Item name="priority" label="优先级"><InputNumber min={0} /></Form.Item>
            <Form.Item name="attachmentRequired" valuePropName="checked"><Checkbox>仅含附件</Checkbox></Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
          </Space>
          <Form.Item name="ruleGroupIds" label="规则组" rules={[{ required: true, message: '请选择规则组' }]}><Select mode="multiple" options={groupOptions} /></Form.Item>
          <Form.Item name="senderDomains" label="发件域"><Select mode="tags" tokenSeparators={[',']} /></Form.Item>
          <Form.Item name="recipientDomains" label="收件域"><Select mode="tags" tokenSeparators={[',']} /></Form.Item>
          <Form.Item name="senderAddressPatterns" label="发件地址模式"><Select mode="tags" tokenSeparators={[',']} placeholder="*@example.com" /></Form.Item>
          <Form.Item name="recipientAddressPatterns" label="收件地址模式"><Select mode="tags" tokenSeparators={[',']} placeholder="security@*" /></Form.Item>
          <Form.Item style={{ textAlign: 'right', marginBottom: 0 }}><Space><Button onClick={() => setPolicyOpen(false)}>取消</Button><Button type="primary" htmlType="submit">保存</Button></Space></Form.Item>
        </Form>
      </Modal>

      <Modal title={editingSelection ? '编辑兼容范围' : '添加兼容范围'} open={legacyOpen} onCancel={() => setLegacyOpen(false)} footer={null} width={600}>
        <Form form={legacyForm} layout="vertical" onFinish={saveLegacy}>
          <Form.Item name="scopeType" label="范围" rules={[{ required: true }]}>
            <Select onChange={setScopeType} options={[{ value: 'GLOBAL', label: '全局' }, { value: 'SENDER_DOMAIN', label: '发件域' }, { value: 'RECIPIENT_DOMAIN', label: '收件域' }]} />
          </Form.Item>
          {scopeType !== 'GLOBAL' && <Form.Item name="scopeValue" label="域名" rules={[{ required: true }]}><Input placeholder="example.com" /></Form.Item>}
          <Form.Item name="patternMode" label="规则模式"><Select onChange={(value: 'ALL' | 'SELECTED') => setPatternMode(value)} options={[{ value: 'ALL', label: '全部规则' }, { value: 'SELECTED', label: '仅选择规则' }]} /></Form.Item>
          {patternMode === 'SELECTED' && <Form.Item name="patternIds" label="启用规则"><Select mode="multiple" options={ruleOptions} /></Form.Item>}
          <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
          <Form.Item style={{ textAlign: 'right', marginBottom: 0 }}><Space><Button onClick={() => setLegacyOpen(false)}>取消</Button><Button type="primary" htmlType="submit">保存</Button></Space></Form.Item>
        </Form>
      </Modal>
    </PageShell>
  );
};

export default DlpSelection;
