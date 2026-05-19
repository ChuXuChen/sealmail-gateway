import { useCallback, useEffect, useMemo, useState } from 'react';
import { Form, message } from 'antd';
import { dlpApi } from '../../api/client';
import { getApiErrorMessage } from '../../api/errors';
import type {
  DlpEdmDataset,
  DlpEvaluation,
  DlpFingerprintLibrary,
  DlpRule,
  DlpRuleRequest,
  DlpRuleType,
} from '../../types';
import type {
  DlpDatasetFormValues,
  DlpRuleFormValues,
  DlpTestValues,
  EdmImportValues,
  FingerprintImportValues,
} from './dlpPatternUtils';
import { ruleTypeMeta, splitImportValues } from './dlpPatternUtils';

export const useDlpPatterns = () => {
  const [data, setData] = useState<DlpRule[]>([]);
  const [edmDatasets, setEdmDatasets] = useState<DlpEdmDataset[]>([]);
  const [fingerprintLibraries, setFingerprintLibraries] = useState<DlpFingerprintLibrary[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DlpRule | null>(null);
  const [ruleType, setRuleType] = useState<string>('PATTERN');
  const [testResult, setTestResult] = useState<DlpEvaluation | null>(null);
  const [edmImportText, setEdmImportText] = useState('');
  const [form] = Form.useForm<DlpRuleFormValues>();
  const [testForm] = Form.useForm<DlpTestValues>();
  const [edmForm] = Form.useForm<DlpDatasetFormValues>();
  const [edmImportForm] = Form.useForm<EdmImportValues>();
  const [fingerprintForm] = Form.useForm<DlpDatasetFormValues>();
  const [fingerprintImportForm] = Form.useForm<FingerprintImportValues>();

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [rulesResponse, edmResponse, fingerprintResponse] = await Promise.all([
        dlpApi.listRules(),
        dlpApi.listEdmDatasets(),
        dlpApi.listFingerprintLibraries(),
      ]);
      setData(rulesResponse);
      setEdmDatasets(edmResponse);
      setFingerprintLibraries(fingerprintResponse);
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载 DLP 规则失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void Promise.resolve().then(loadData);
  }, [loadData]);

  const showCreate = useCallback((type: DlpRuleType = 'PATTERN') => {
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
  }, [form]);

  const showEdit = useCallback((record: DlpRule) => {
    setEditing(record);
    setRuleType(record.type);
    form.setFieldsValue(record);
    setModalOpen(true);
  }, [form]);

  const closeRuleModal = useCallback(() => {
    setModalOpen(false);
  }, []);

  const handleRuleTypeChange = useCallback((value: string) => {
    setRuleType(value);
    form.setFieldsValue({
      builtinCode: undefined,
      maskingStrategy: value === 'EDM' || value === 'FINGERPRINT'
        ? 'HASH_ONLY'
        : form.getFieldValue('maskingStrategy') || 'DEFAULT',
      pattern: undefined,
    });
  }, [form]);

  const save = useCallback(async (values: DlpRuleFormValues) => {
    try {
      const payload = {
        ...values,
        pattern: values.type === 'BUILTIN' ? undefined : values.pattern,
      } as DlpRuleRequest;
      if (editing) {
        await dlpApi.updateRule(editing.id, payload);
      } else {
        await dlpApi.createRule(payload);
      }
      message.success('DLP 规则已保存');
      setModalOpen(false);
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '保存 DLP 规则失败'));
    }
  }, [editing, loadData]);

  const saveEdmDataset = useCallback(async (values: DlpDatasetFormValues) => {
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
  }, [edmForm, loadData]);

  const importEdmValues = useCallback(async (values: EdmImportValues) => {
    try {
      const result = await dlpApi.importEdmDataset(values.datasetId, { text: values.text });
      message.success(`导入 ${result.importedCount} 条，重复 ${result.duplicateCount} 条`);
      edmImportForm.resetFields();
      setEdmImportText('');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '导入 EDM 数据失败'));
    }
  }, [edmImportForm, loadData]);

  const removeEdmDataset = useCallback(async (id: string) => {
    try {
      await dlpApi.deleteEdmDataset(id);
      message.success('EDM 数据集已删除');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除 EDM 数据集失败'));
    }
  }, [loadData]);

  const saveFingerprintLibrary = useCallback(async (values: DlpDatasetFormValues) => {
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
  }, [fingerprintForm, loadData]);

  const importFingerprintDocument = useCallback(async (values: FingerprintImportValues) => {
    try {
      const result = await dlpApi.importFingerprintDocument(values.libraryId, {
        documentName: values.documentName,
        text: values.text,
      });
      message.success(`导入 ${result.importedCount} 个片段`);
      fingerprintImportForm.resetFields();
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '导入文档指纹失败'));
    }
  }, [fingerprintImportForm, loadData]);

  const removeFingerprintLibrary = useCallback(async (id: string) => {
    try {
      await dlpApi.deleteFingerprintLibrary(id);
      message.success('文档指纹库已删除');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除文档指纹库失败'));
    }
  }, [loadData]);

  const remove = useCallback(async (id: string) => {
    try {
      await dlpApi.deleteRule(id);
      message.success('DLP 规则已删除');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除 DLP 规则失败'));
    }
  }, [loadData]);

  const runTest = useCallback(async (values: DlpTestValues) => {
    try {
      const result = await dlpApi.test({
        body: values.body,
        direction: 'OUTBOUND',
        recipients: values.recipients?.split(',').map((item) => item.trim()).filter(Boolean),
        sender: values.sender,
        subject: values.subject,
      });
      setTestResult(result);
    } catch (error) {
      message.error(getApiErrorMessage(error, 'DLP 测试失败'));
    }
  }, []);

  const clearTest = useCallback(() => {
    testForm.resetFields();
    setTestResult(null);
  }, [testForm]);

  const edmOptions = useMemo(
    () => edmDatasets.map((item) => ({ value: item.id, label: `${item.name} (${item.valueCount})` })),
    [edmDatasets],
  );
  const fingerprintOptions = useMemo(
    () => fingerprintLibraries.map((item) => ({ value: item.id, label: `${item.name} (${item.chunkCount})` })),
    [fingerprintLibraries],
  );
  const enabledRuleCount = useMemo(() => data.filter((item) => item.enabled).length, [data]);
  const edmRuleCount = useMemo(() => data.filter((item) => item.type === 'EDM').length, [data]);
  const fingerprintRuleCount = useMemo(() => data.filter((item) => item.type === 'FINGERPRINT').length, [data]);
  const edmValueCount = useMemo(() => edmDatasets.reduce((sum, item) => sum + item.valueCount, 0), [edmDatasets]);
  const fingerprintChunkCount = useMemo(
    () => fingerprintLibraries.reduce((sum, item) => sum + item.chunkCount, 0),
    [fingerprintLibraries],
  );
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

  return {
    clearTest,
    closeRuleModal,
    currentRuleMeta,
    data,
    editing,
    edmDatasets,
    edmForm,
    edmImportForm,
    edmImportStats,
    edmOptions,
    edmRuleCount,
    edmValueCount,
    enabledRuleCount,
    fingerprintChunkCount,
    fingerprintForm,
    fingerprintImportForm,
    fingerprintLibraries,
    fingerprintOptions,
    fingerprintRuleCount,
    form,
    handleRuleTypeChange,
    importEdmValues,
    importFingerprintDocument,
    loadData,
    loading,
    modalOpen,
    remove,
    removeEdmDataset,
    removeFingerprintLibrary,
    ruleType,
    runTest,
    save,
    saveEdmDataset,
    saveFingerprintLibrary,
    setEdmImportText,
    showCreate,
    showEdit,
    testForm,
    testResult,
  };
};
