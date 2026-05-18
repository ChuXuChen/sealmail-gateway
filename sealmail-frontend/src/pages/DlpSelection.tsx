import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Checkbox,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
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
import type { DlpPolicy, DlpRule, DlpRuleGroup, DlpSelection } from '../types';
import {
  AdvancedSection,
  DataTable,
  EnabledTag,
  FieldHint,
  PageHeader,
  PageShell,
  SectionPanel,
  StatusSummary,
  confirmDeleteAction,
} from '../components/Page';

const { Text } = Typography;

const modeLabels: Record<string, string> = { MONITOR: '监控', ENFORCE: '执行' };
const scopeLabels: Record<string, string> = { GLOBAL: '全局', SENDER_DOMAIN: '发件域', RECIPIENT_DOMAIN: '收件域' };
const directionLabels: Record<string, string> = { OUTBOUND: '出站', INBOUND: '入站' };

const joinScope = (values?: string[]) => {
  if (!values || values.length === 0) {
    return '全部';
  }
  return values.join('、');
};

const DlpSelection: React.FC = () => {
  const [rules, setRules] = useState<DlpRule[]>([]);
  const [groups, setGroups] = useState<DlpRuleGroup[]>([]);
  const [policies, setPolicies] = useState<DlpPolicy[]>([]);
  const [selections, setSelections] = useState<DlpSelection[]>([]);
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

  const ruleOptions = useMemo(() => rules.map((rule) => ({
    label: `${rule.name} · ${rule.type}`,
    value: rule.id,
  })), [rules]);

  const groupOptions = useMemo(() => groups.map((group) => ({
    label: `${group.name} (${group.ruleIds.length})`,
    value: group.id,
  })), [groups]);

  const groupById = useMemo(() => new Map(groups.map((group) => [group.id, group])), [groups]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [rulesResponse, groupsResponse, policiesResponse, selectionsResponse] = await Promise.all([
        dlpApi.listRules(),
        dlpApi.listRuleGroups(),
        dlpApi.listPolicies(),
        dlpApi.listSelections(),
      ]);
      setRules(rulesResponse.data.data);
      setGroups(groupsResponse.data.data);
      setPolicies(policiesResponse.data.data);
      setSelections(selectionsResponse.data.data);
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
      attachmentRequired: false,
      enabled: true,
      mode: 'ENFORCE',
      priority: 100,
      recipientAddressPatterns: [],
      recipientDomains: [],
      ruleGroupIds: [],
      senderAddressPatterns: [],
      senderDomains: [],
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
      attachmentRequired: !!values.attachmentRequired,
      enabled: values.enabled !== false,
      mode: values.mode || 'ENFORCE',
      priority: values.priority ?? 100,
      recipientAddressPatterns: values.recipientAddressPatterns || [],
      recipientDomains: values.recipientDomains || [],
      ruleGroupIds: values.ruleGroupIds || [],
      senderAddressPatterns: values.senderAddressPatterns || [],
      senderDomains: values.senderDomains || [],
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
    legacyForm.setFieldsValue({ enabled: true, patternIds: [], patternMode: 'ALL', scopeType: 'GLOBAL' });
    setLegacyOpen(true);
  };

  const showEditLegacy = (record: DlpSelection) => {
    setEditingSelection(record);
    setScopeType(record.scopeType);
    const mode = record.patternIds == null ? 'ALL' : 'SELECTED';
    setPatternMode(mode);
    legacyForm.setFieldsValue({ ...record, patternIds: record.patternIds || [], patternMode: mode });
    setLegacyOpen(true);
  };

  const saveLegacy = async (values: DlpSelection) => {
    const payload = {
      ...values,
      patternIds: values.patternMode === 'ALL' ? undefined : values.patternIds,
      scopeValue: values.scopeType === 'GLOBAL' ? undefined : values.scopeValue,
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

  const enabledPolicyCount = policies.filter((policy) => policy.enabled).length;
  const enforcePolicyCount = policies.filter((policy) => policy.mode === 'ENFORCE').length;
  const enabledGroupCount = groups.filter((group) => group.enabled).length;
  const unboundGroupCount = groups.filter((group) => !policies.some((policy) => policy.ruleGroupIds.includes(group.id))).length;

  const groupColumns: TableColumnsType<DlpRuleGroup> = [
    {
      title: '规则组',
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
    {
      title: '策略',
      dataIndex: 'name',
      key: 'name',
      width: 230,
      render: (_: string, record) => (
        <Space direction="vertical" size={2} className="min-width-zero">
          <Space size={6} wrap>
            <Text strong ellipsis>{record.name}</Text>
            <EnabledTag enabled={record.enabled} />
          </Space>
          {record.description ? <Text type="secondary" className="text-micro" ellipsis>{record.description}</Text> : null}
        </Space>
      ),
    },
    {
      title: '适用范围',
      key: 'scope',
      width: 260,
      render: (_: unknown, record) => (
        <Space direction="vertical" size={2} className="min-width-zero">
          <Text className="text-micro" ellipsis>发件域：{joinScope(record.senderDomains)}</Text>
          <Text className="text-micro" ellipsis>收件域：{joinScope(record.recipientDomains)}</Text>
          <Text className="text-micro" ellipsis>方向：{record.direction ? directionLabels[record.direction] || record.direction : '全部'}</Text>
        </Space>
      ),
    },
    {
      title: '执行方式',
      dataIndex: 'mode',
      key: 'mode',
      width: 110,
      render: (value: string) => <Tag color={value === 'ENFORCE' ? 'red' : 'blue'}>{modeLabels[value] || value}</Tag>,
    },
    {
      title: '规则组',
      dataIndex: 'ruleGroupIds',
      key: 'ruleGroupIds',
      width: 180,
      render: (ids: string[]) => (
        <Space size={[0, 4]} wrap>
          {ids.length === 0 ? '-' : ids.slice(0, 2).map((id) => <Tag key={id}>{groupById.get(id)?.name || id}</Tag>)}
          {ids.length > 2 ? <Tag>+{ids.length - 2}</Tag> : null}
        </Space>
      ),
    },
    { title: '优先级', dataIndex: 'priority', key: 'priority', width: 90 },
    { title: '附件', dataIndex: 'attachmentRequired', key: 'attachmentRequired', width: 80, render: (value: boolean) => value ? <Tag>需要</Tag> : '-' },
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

  return (
    <PageShell className="dlp-selection-page">
      <PageHeader
        title="DLP 策略集"
        description="从策略视角配置适用范围、规则组、执行方式和高级条件；旧版范围保留为历史兼容入口。"
        actions={(
          <Space wrap>
            <Button icon={<ReloadOutlined />} loading={loading} onClick={loadData}>刷新</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={showCreatePolicy}>添加策略</Button>
          </Space>
        )}
      />

      <StatusSummary
        items={[
          { key: 'policies', label: '策略总数', value: policies.length, description: `${enabledPolicyCount} 条启用`, tone: 'info' },
          { key: 'enforce', label: '执行模式', value: enforcePolicyCount, description: `${policies.length - enforcePolicyCount} 条监控` },
          { key: 'groups', label: '规则组', value: groups.length, description: `${enabledGroupCount} 个启用`, tone: groups.length > 0 ? 'success' : 'default' },
          { key: 'legacy', label: '旧版兼容', value: selections.length, description: unboundGroupCount > 0 ? `${unboundGroupCount} 个规则组未绑定` : '无未绑定规则组', tone: unboundGroupCount > 0 ? 'warning' : 'success' },
        ]}
      />

      <SectionPanel
        title="策略列表"
        description="策略负责决定什么邮件进入哪些规则组，以及命中后以监控还是执行模式处理。"
      >
        <DataTable<DlpPolicy>
          rowKey="id"
          loading={loading}
          dataSource={policies}
          columns={policyColumns}
          scroll={{ x: 1080 }}
          pagination={{ pageSize: 20, total: policies.length }}
        />
      </SectionPanel>

      <SectionPanel
        title="规则组构件"
        description="规则组是策略引用的构件，建议按业务场景组织，不直接表达生效范围。"
        extra={<Button icon={<PlusOutlined />} onClick={showCreateGroup}>添加规则组</Button>}
      >
        <DataTable<DlpRuleGroup>
          rowKey="id"
          loading={loading}
          dataSource={groups}
          columns={groupColumns}
          scroll={{ x: 760 }}
          pagination={{ pageSize: 20, total: groups.length }}
        />
      </SectionPanel>

      <AdvancedSection title="旧版兼容范围" description="仅用于历史兼容。新配置应优先使用上方策略列表和规则组。" className="legacy-compat-section">
        <div className="flow-stack">
          <Alert message="这些范围仍会走原有接口保存，便于迁移旧配置；不要用它替代新策略。" showIcon type="warning" />
          <Space><Button icon={<PlusOutlined />} onClick={showCreateLegacy}>添加范围</Button></Space>
          <DataTable<DlpSelection>
            rowKey="id"
            loading={loading}
            dataSource={selections}
            columns={legacyColumns}
            scroll={{ x: 720 }}
            pagination={{ pageSize: 20, total: selections.length }}
          />
        </div>
      </AdvancedSection>

      <Modal title={editingGroup ? '编辑规则组' : '添加规则组'} open={groupOpen} onCancel={() => setGroupOpen(false)} footer={null} width={680}>
        <Form form={groupForm} layout="vertical" onFinish={saveGroup}>
          <SectionPanel compact title="规则组信息" description="把同类规则组合成策略构件，便于多个策略复用。">
            <div className="split-grid">
              <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}><Input /></Form.Item>
              <Form.Item name="description" label="说明"><Input /></Form.Item>
            </div>
            <Form.Item name="ruleIds" label="规则">
              <Select mode="multiple" options={ruleOptions} placeholder="选择规则" />
            </Form.Item>
            <div className="split-grid">
              <Form.Item name="priority" label="优先级"><InputNumber min={0} className="full-width" /></Form.Item>
              <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
            </div>
          </SectionPanel>
          <Form.Item className="form-actions dlp-modal-actions"><Space><Button onClick={() => setGroupOpen(false)}>取消</Button><Button type="primary" htmlType="submit">保存</Button></Space></Form.Item>
        </Form>
      </Modal>

      <Modal title={editingPolicy ? '编辑策略' : '添加策略'} open={policyOpen} onCancel={() => setPolicyOpen(false)} footer={null} width={860}>
        <Form form={policyForm} layout="vertical" onFinish={savePolicy}>
          <SectionPanel compact title="基础信息" description="策略按优先级匹配邮件，优先级越小越靠前。">
            <div className="split-grid">
              <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}><Input /></Form.Item>
              <Form.Item name="description" label="说明"><Input /></Form.Item>
              <Form.Item name="priority" label="优先级"><InputNumber min={0} className="full-width" /></Form.Item>
              <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
            </div>
          </SectionPanel>

          <SectionPanel compact title="适用范围" description="为空表示不限制该条件。可以先按方向和域名收敛，再用地址模式处理例外。">
            <div className="split-grid">
              <Form.Item name="direction" label="方向"><Select allowClear options={[{ value: 'OUTBOUND', label: '出站' }, { value: 'INBOUND', label: '入站' }]} /></Form.Item>
              <Form.Item name="senderDomains" label="发件域"><Select mode="tags" tokenSeparators={[',']} placeholder="example.com" /></Form.Item>
              <Form.Item name="recipientDomains" label="收件域"><Select mode="tags" tokenSeparators={[',']} placeholder="example.net" /></Form.Item>
            </div>
            <FieldHint>域名只写主域名；更细的邮箱匹配放到高级条件。</FieldHint>
          </SectionPanel>

          <SectionPanel compact title="规则组" description="至少选择一个规则组。策略运行时会按规则组和规则自身优先级执行。">
            <Form.Item name="ruleGroupIds" label="规则组" rules={[{ required: true, message: '请选择规则组' }]}>
              <Select mode="multiple" options={groupOptions} placeholder="选择规则组" />
            </Form.Item>
          </SectionPanel>

          <SectionPanel compact title="执行方式" description="监控模式只记录命中；执行模式会使用规则动作进行告警、强制加密、隔离或阻断。">
            <div className="split-grid">
              <Form.Item name="mode" label="模式" rules={[{ required: true }]}>
                <Select options={[{ value: 'ENFORCE', label: '执行' }, { value: 'MONITOR', label: '监控' }]} />
              </Form.Item>
              <Form.Item name="attachmentRequired" valuePropName="checked">
                <Checkbox>仅含附件时生效</Checkbox>
              </Form.Item>
            </div>
          </SectionPanel>

          <AdvancedSection title="高级条件" description="用于匹配完整邮箱地址或通配模式，例如 *@example.com 或 security@*。">
            <Form.Item name="senderAddressPatterns" label="发件地址模式"><Select mode="tags" tokenSeparators={[',']} placeholder="*@example.com" /></Form.Item>
            <Form.Item name="recipientAddressPatterns" label="收件地址模式"><Select mode="tags" tokenSeparators={[',']} placeholder="security@*" /></Form.Item>
          </AdvancedSection>

          <Form.Item className="form-actions dlp-modal-actions"><Space><Button onClick={() => setPolicyOpen(false)}>取消</Button><Button type="primary" htmlType="submit">保存</Button></Space></Form.Item>
        </Form>
      </Modal>

      <Modal title={editingSelection ? '编辑兼容范围' : '添加兼容范围'} open={legacyOpen} onCancel={() => setLegacyOpen(false)} footer={null} width={640}>
        <Form form={legacyForm} layout="vertical" onFinish={saveLegacy}>
          <Alert className="legacy-form-alert" message="旧版兼容范围只用于历史配置迁移，新规则请使用策略列表。" showIcon type="warning" />
          <Form.Item name="scopeType" label="范围" rules={[{ required: true }]}>
            <Select onChange={setScopeType} options={[{ value: 'GLOBAL', label: '全局' }, { value: 'SENDER_DOMAIN', label: '发件域' }, { value: 'RECIPIENT_DOMAIN', label: '收件域' }]} />
          </Form.Item>
          {scopeType !== 'GLOBAL' && <Form.Item name="scopeValue" label="域名" rules={[{ required: true, message: '请输入域名' }]}><Input placeholder="example.com" /></Form.Item>}
          <Form.Item name="patternMode" label="规则模式"><Select onChange={(value: 'ALL' | 'SELECTED') => setPatternMode(value)} options={[{ value: 'ALL', label: '全部规则' }, { value: 'SELECTED', label: '仅选择规则' }]} /></Form.Item>
          {patternMode === 'SELECTED' && <Form.Item name="patternIds" label="启用规则"><Select mode="multiple" options={ruleOptions} /></Form.Item>}
          <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
          <Form.Item className="form-actions"><Space><Button onClick={() => setLegacyOpen(false)}>取消</Button><Button type="primary" htmlType="submit">保存</Button></Space></Form.Item>
        </Form>
      </Modal>
    </PageShell>
  );
};

export default DlpSelection;
