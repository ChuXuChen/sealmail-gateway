import { useCallback, useEffect, useMemo, useState } from 'react';
import { Form, message } from 'antd';
import { dlpApi } from '../../api/client';
import { getApiErrorMessage } from '../../api/errors';
import type {
  DlpPolicy,
  DlpPolicyRequest,
  DlpEvaluation,
  DlpRule,
  DlpRuleGroup,
  DlpRuleGroupRequest,
  DlpScopeType,
  DlpSelection,
  CreateDlpSelectionRequest,
} from '../../types';
import type {
  DlpLegacySelectionFormValues,
  DlpPolicyFormValues,
  DlpPolicySimulationFormValues,
  DlpRuleGroupFormValues,
} from './dlpSelectionUtils';

export const useDlpSelection = () => {
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
  const [simulationResult, setSimulationResult] = useState<DlpEvaluation | null>(null);
  const [simulationRunning, setSimulationRunning] = useState(false);
  const [scopeType, setScopeType] = useState<DlpScopeType>('GLOBAL');
  const [patternMode, setPatternMode] = useState<'ALL' | 'SELECTED'>('ALL');
  const [groupForm] = Form.useForm<DlpRuleGroupFormValues>();
  const [policyForm] = Form.useForm<DlpPolicyFormValues>();
  const [legacyForm] = Form.useForm<DlpLegacySelectionFormValues>();
  const [simulationForm] = Form.useForm<DlpPolicySimulationFormValues>();

  const ruleOptions = useMemo(() => rules.map((rule) => ({
    label: `${rule.name} · ${rule.type}`,
    value: rule.id,
  })), [rules]);

  const groupOptions = useMemo(() => groups.map((group) => ({
    label: `${group.name} (${group.ruleIds.length})`,
    value: group.id,
  })), [groups]);

  const policyOptions = useMemo(() => policies.map((policy) => ({
    label: `${policy.name} · ${policy.mode}`,
    value: policy.id,
  })), [policies]);

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
      setRules(rulesResponse);
      setGroups(groupsResponse);
      setPolicies(policiesResponse);
      setSelections(selectionsResponse);
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载 DLP 策略配置失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void Promise.resolve().then(loadData);
  }, [loadData]);

  const showCreateGroup = useCallback(() => {
    setEditingGroup(null);
    groupForm.resetFields();
    groupForm.setFieldsValue({ enabled: true, priority: 100, ruleIds: [] });
    setGroupOpen(true);
  }, [groupForm]);

  const showEditGroup = useCallback((record: DlpRuleGroup) => {
    setEditingGroup(record);
    groupForm.setFieldsValue(record);
    setGroupOpen(true);
  }, [groupForm]);

  const closeGroup = useCallback(() => setGroupOpen(false), []);

  const saveGroup = useCallback(async (values: DlpRuleGroupFormValues) => {
    try {
      if (editingGroup) {
        await dlpApi.updateRuleGroup(editingGroup.id, values);
      } else {
        await dlpApi.createRuleGroup(values as DlpRuleGroupRequest);
      }
      message.success('规则组已保存');
      setGroupOpen(false);
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '保存规则组失败'));
    }
  }, [editingGroup, loadData]);

  const removeGroup = useCallback(async (id: string) => {
    try {
      await dlpApi.deleteRuleGroup(id);
      message.success('规则组已删除');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除规则组失败'));
    }
  }, [loadData]);

  const showCreatePolicy = useCallback(() => {
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
  }, [policyForm]);

  const showEditPolicy = useCallback((record: DlpPolicy) => {
    setEditingPolicy(record);
    policyForm.setFieldsValue(record);
    setPolicyOpen(true);
  }, [policyForm]);

  const closePolicy = useCallback(() => setPolicyOpen(false), []);

  const savePolicy = useCallback(async (values: DlpPolicyFormValues) => {
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
    } as DlpPolicyRequest;
    try {
      if (editingPolicy) {
        await dlpApi.updatePolicy(editingPolicy.id, payload);
      } else {
        await dlpApi.createPolicy(payload);
      }
      message.success('策略已保存');
      setPolicyOpen(false);
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '保存策略失败'));
    }
  }, [editingPolicy, loadData]);

  const removePolicy = useCallback(async (id: string) => {
    try {
      await dlpApi.deletePolicy(id);
      message.success('策略已删除');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除策略失败'));
    }
  }, [loadData]);

  const simulatePolicy = useCallback(async (values: DlpPolicySimulationFormValues) => {
    if (!values.policyId) {
      message.warning('请选择要仿真的策略');
      return;
    }
    const recipients = (values.recipients || '')
      .split(',')
      .map((recipient) => recipient.trim())
      .filter(Boolean);
    setSimulationRunning(true);
    try {
      const result = await dlpApi.simulatePolicy(values.policyId, {
        body: values.body,
        direction: values.direction,
        recipients,
        sender: values.sender,
        subject: values.subject,
      });
      setSimulationResult(result);
    } catch (error) {
      message.error(getApiErrorMessage(error, '策略仿真失败'));
    } finally {
      setSimulationRunning(false);
    }
  }, []);

  const clearSimulation = useCallback(() => {
    simulationForm.resetFields();
    simulationForm.setFieldsValue({ direction: 'OUTBOUND' });
    setSimulationResult(null);
  }, [simulationForm]);

  const showCreateLegacy = useCallback(() => {
    setEditingSelection(null);
    setScopeType('GLOBAL');
    setPatternMode('ALL');
    legacyForm.resetFields();
    legacyForm.setFieldsValue({ enabled: true, patternIds: [], patternMode: 'ALL', scopeType: 'GLOBAL' });
    setLegacyOpen(true);
  }, [legacyForm]);

  const showEditLegacy = useCallback((record: DlpSelection) => {
    setEditingSelection(record);
    setScopeType(record.scopeType);
    const mode = record.patternIds == null ? 'ALL' : 'SELECTED';
    setPatternMode(mode);
    legacyForm.setFieldsValue({ ...record, patternIds: record.patternIds || [], patternMode: mode });
    setLegacyOpen(true);
  }, [legacyForm]);

  const closeLegacy = useCallback(() => setLegacyOpen(false), []);

  const saveLegacy = useCallback(async (values: DlpLegacySelectionFormValues) => {
    const payload: CreateDlpSelectionRequest = {
      enabled: values.enabled !== false,
      patternMode: values.patternMode || 'ALL',
      patternIds: values.patternMode === 'ALL' ? undefined : values.patternIds,
      scopeValue: values.scopeType === 'GLOBAL' ? undefined : values.scopeValue,
      scopeType: values.scopeType || 'GLOBAL',
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
  }, [editingSelection, loadData]);

  const removeLegacy = useCallback(async (id: string) => {
    try {
      await dlpApi.deleteSelection(id);
      message.success('兼容范围已删除');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除兼容范围失败'));
    }
  }, [loadData]);

  const enabledPolicyCount = useMemo(() => policies.filter((policy) => policy.enabled).length, [policies]);
  const enforcePolicyCount = useMemo(() => policies.filter((policy) => policy.mode === 'ENFORCE').length, [policies]);
  const enabledGroupCount = useMemo(() => groups.filter((group) => group.enabled).length, [groups]);
  const unboundGroupCount = useMemo(
    () => groups.filter((group) => !policies.some((policy) => policy.ruleGroupIds.includes(group.id))).length,
    [groups, policies],
  );

  return {
    closeGroup,
    closeLegacy,
    closePolicy,
    editingGroup,
    editingPolicy,
    editingSelection,
    enabledGroupCount,
    enabledPolicyCount,
    enforcePolicyCount,
    groupById,
    groupForm,
    groupOpen,
    groupOptions,
    groups,
    legacyForm,
    legacyOpen,
    loadData,
    loading,
    patternMode,
    policies,
    policyOptions,
    policyForm,
    policyOpen,
    removeGroup,
    removeLegacy,
    removePolicy,
    ruleOptions,
    saveGroup,
    saveLegacy,
    savePolicy,
    scopeType,
    selections,
    simulationForm,
    simulationResult,
    simulationRunning,
    setPatternMode,
    setScopeType,
    showCreateGroup,
    showCreateLegacy,
    showCreatePolicy,
    showEditGroup,
    showEditLegacy,
    showEditPolicy,
    simulatePolicy,
    unboundGroupCount,
    clearSimulation,
  };
};
