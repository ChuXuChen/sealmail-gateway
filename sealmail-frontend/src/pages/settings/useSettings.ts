import { useCallback, useEffect, useState } from 'react';
import { message } from 'antd';
import type { FormInstance } from 'antd';
import { mailTestApi, runtimePolicyApi, systemSettingsApi } from '../../api/client';
import { getApiErrorMessage } from '../../api/errors';
import type {
  GmEdgePolicy,
  QuarantinePolicy,
  RelayPolicy,
  SmimeSuitePolicy,
  SystemSettings as SystemSettingsSnapshot,
} from '../../types';
import type {
  GmEdgePolicyFormValues,
  QuarantinePolicyFormValues,
  RelayPolicyFormValues,
  SmimeSuitePolicyFormValues,
  TestMailValues,
} from './settingsUtils';
import { applyGmEdgeDefaults, splitRecipients } from './settingsUtils';

interface UseSettingsParams {
  gmEdgeForm: FormInstance<GmEdgePolicyFormValues>;
  quarantineForm: FormInstance<QuarantinePolicyFormValues>;
  relayForm: FormInstance<RelayPolicyFormValues>;
  smimeSuiteForm?: FormInstance<SmimeSuitePolicyFormValues>;
  testForm: FormInstance<TestMailValues>;
}

export const useSettings = ({ gmEdgeForm, quarantineForm, relayForm, smimeSuiteForm, testForm }: UseSettingsParams) => {
  const [settings, setSettings] = useState<SystemSettingsSnapshot | null>(null);
  const [gmEdgePolicy, setGmEdgePolicy] = useState<GmEdgePolicy | null>(null);
  const [relayPolicy, setRelayPolicy] = useState<RelayPolicy | null>(null);
  const [quarantinePolicy, setQuarantinePolicy] = useState<QuarantinePolicy | null>(null);
  const [smimeSuitePolicy, setSmimeSuitePolicy] = useState<SmimeSuitePolicy | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [probeLoading, setProbeLoading] = useState(false);
  const [probeResult, setProbeResult] = useState('');
  const [testLoading, setTestLoading] = useState(false);

  const applyPolicyForms = useCallback((
    relay: RelayPolicy,
    quarantine: QuarantinePolicy,
    gmEdge: GmEdgePolicy,
    smimeSuite?: SmimeSuitePolicy | null,
  ) => {
    relayForm.setFieldsValue({
      ...relay,
      clearPasswordSecretRef: false,
    });
    quarantineForm.setFieldsValue(quarantine);
    gmEdgeForm.setFieldsValue(applyGmEdgeDefaults(gmEdge));
    if (smimeSuite && smimeSuiteForm) {
      smimeSuiteForm.setFieldsValue({
        defaultStandardSuite: smimeSuite.defaultStandardSuite,
        defaultGmSuite: smimeSuite.defaultGmSuite,
      });
    }
  }, [gmEdgeForm, quarantineForm, relayForm, smimeSuiteForm]);

  const loadSettings = useCallback(async (initial = false) => {
    if (initial) {
      setLoading(true);
    } else {
      setRefreshing(true);
    }

    try {
      const [response, relayResponse, quarantineResponse, gmEdgeResponse, smimeSuiteResponse] = await Promise.all([
        systemSettingsApi.get(),
        runtimePolicyApi.getRelay(),
        runtimePolicyApi.getQuarantine(),
        runtimePolicyApi.getGmEdge(),
        runtimePolicyApi.getSmimeSuite(),
      ]);
      if (!response.data.success) {
        throw new Error(response.data.message);
      }
      setSettings(response.data.data);
      setRelayPolicy(relayResponse.data.data);
      setQuarantinePolicy(quarantineResponse.data.data);
      setGmEdgePolicy(gmEdgeResponse.data.data);
      setSmimeSuitePolicy(smimeSuiteResponse.data.data);
      applyPolicyForms(
        relayResponse.data.data,
        quarantineResponse.data.data,
        gmEdgeResponse.data.data,
        smimeSuiteResponse.data.data,
      );
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载系统设置失败'));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [applyPolicyForms]);

  useEffect(() => {
    let mounted = true;

    const loadInitialSettings = async () => {
      try {
        const [response, relayResponse, quarantineResponse, gmEdgeResponse, smimeSuiteResponse] = await Promise.all([
          systemSettingsApi.get(),
          runtimePolicyApi.getRelay(),
          runtimePolicyApi.getQuarantine(),
          runtimePolicyApi.getGmEdge(),
          runtimePolicyApi.getSmimeSuite(),
        ]);
        if (!response.data.success) throw new Error(response.data.message);
        if (mounted) {
          setSettings(response.data.data);
          setRelayPolicy(relayResponse.data.data);
          setQuarantinePolicy(quarantineResponse.data.data);
          setGmEdgePolicy(gmEdgeResponse.data.data);
          setSmimeSuitePolicy(smimeSuiteResponse.data.data);
          applyPolicyForms(
            relayResponse.data.data,
            quarantineResponse.data.data,
            gmEdgeResponse.data.data,
            smimeSuiteResponse.data.data,
          );
        }
      } catch (error) {
        if (mounted) {
          message.error(getApiErrorMessage(error, '加载系统设置失败'));
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    };

    void loadInitialSettings();

    return () => {
      mounted = false;
    };
  }, [applyPolicyForms]);

  const handleProbe = useCallback(async () => {
    setProbeLoading(true);
    try {
      const response = await mailTestApi.testSmtpConfig();
      if (!response.data.success) {
        throw new Error(response.data.message);
      }
      const result = response.data.data || '';
      setProbeResult(result);
      if (result.includes('FAILED')) {
        message.warning('SMTP 探测完成，存在失败链路');
      } else {
        message.success('SMTP 探测通过');
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, 'SMTP 探测失败'));
    } finally {
      setProbeLoading(false);
    }
  }, []);

  const handleSendTest = useCallback(async (values: TestMailValues) => {
    const recipients = splitRecipients(values.to);
    if (recipients.length === 0) {
      message.error('请输入收件人邮箱');
      return;
    }

    setTestLoading(true);
    try {
      const response = await mailTestApi.sendEncrypted({
        from: values.from,
        to: recipients,
        subject: values.subject,
        content: values.content,
      });
      if (!response.data.success) {
        throw new Error(response.data.message || response.data.data);
      }
      message.success(response.data.data || '加密测试邮件已提交');
      testForm.resetFields();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '测试邮件发送失败'));
      return false;
    } finally {
      setTestLoading(false);
    }
  }, [testForm]);

  const handleRelayPolicySave = useCallback(async (values: RelayPolicyFormValues) => {
    try {
      const response = await runtimePolicyApi.updateRelay(values);
      if (!response.data.success) throw new Error(response.data.message);
      setRelayPolicy(response.data.data);
      relayForm.setFieldsValue({
        ...response.data.data,
        clearPasswordSecretRef: false,
      });
      const settingsResponse = await systemSettingsApi.get();
      if (settingsResponse.data.success) {
        setSettings(settingsResponse.data.data);
      }
      message.success('Relay 策略已保存');
    } catch (error) {
      message.error(getApiErrorMessage(error, 'Relay 策略保存失败'));
    }
  }, [relayForm]);

  const handleQuarantinePolicySave = useCallback(async (values: QuarantinePolicyFormValues) => {
    try {
      const response = await runtimePolicyApi.updateQuarantine(values);
      if (!response.data.success) throw new Error(response.data.message);
      setQuarantinePolicy(response.data.data);
      quarantineForm.setFieldsValue(response.data.data);
      const settingsResponse = await systemSettingsApi.get();
      if (settingsResponse.data.success) {
        setSettings(settingsResponse.data.data);
      }
      message.success('隔离策略已保存');
    } catch (error) {
      message.error(getApiErrorMessage(error, '隔离策略保存失败'));
    }
  }, [quarantineForm]);

  const handleGmEdgePolicySave = useCallback(async (values: GmEdgePolicyFormValues) => {
    try {
      const response = await runtimePolicyApi.updateGmEdge(values);
      if (!response.data.success) throw new Error(response.data.message);
      setGmEdgePolicy(response.data.data);
      gmEdgeForm.setFieldsValue(applyGmEdgeDefaults(response.data.data));
      const settingsResponse = await systemSettingsApi.get();
      if (settingsResponse.data.success) {
        setSettings(settingsResponse.data.data);
      }
      message.success('国密 Edge 策略已保存');
    } catch (error) {
      message.error(getApiErrorMessage(error, '国密 Edge 策略保存失败'));
    }
  }, [gmEdgeForm]);

  const handleSmimeSuitePolicySave = useCallback(async (values: SmimeSuitePolicyFormValues) => {
    try {
      const response = await runtimePolicyApi.updateSmimeSuite(values);
      if (!response.data.success) throw new Error(response.data.message);
      setSmimeSuitePolicy(response.data.data);
      smimeSuiteForm?.setFieldsValue({
        defaultStandardSuite: response.data.data.defaultStandardSuite,
        defaultGmSuite: response.data.data.defaultGmSuite,
      });
      const settingsResponse = await systemSettingsApi.get();
      if (settingsResponse.data.success) {
        setSettings(settingsResponse.data.data);
      }
      message.success('S/MIME 套件策略已保存');
    } catch (error) {
      message.error(getApiErrorMessage(error, 'S/MIME 套件策略保存失败'));
    }
  }, [smimeSuiteForm]);

  return {
    gmEdgePolicy,
    handleGmEdgePolicySave,
    handleProbe,
    handleQuarantinePolicySave,
    handleRelayPolicySave,
    handleSendTest,
    handleSmimeSuitePolicySave,
    loadSettings,
    loading,
    probeLoading,
    probeResult,
    quarantinePolicy,
    refreshing,
    relayPolicy,
    smimeSuitePolicy,
    settings,
    testLoading,
  };
};
