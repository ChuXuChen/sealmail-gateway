import { useCallback, useEffect, useState } from 'react';
import { message } from 'antd';
import { runtimePolicyApi, systemSettingsApi } from '../../api/client';
import { getApiErrorMessage } from '../../api/errors';
import type {
  GmEdgePolicy,
  QuarantinePolicy,
  RelayPolicy,
  SmimeSuitePolicy,
  SystemSettings as SystemSettingsSnapshot,
} from '../../types';

export const useSettingsSnapshot = () => {
  const [settings, setSettings] = useState<SystemSettingsSnapshot | null>(null);
  const [gmEdgePolicy, setGmEdgePolicy] = useState<GmEdgePolicy | null>(null);
  const [relayPolicy, setRelayPolicy] = useState<RelayPolicy | null>(null);
  const [quarantinePolicy, setQuarantinePolicy] = useState<QuarantinePolicy | null>(null);
  const [smimeSuitePolicy, setSmimeSuitePolicy] = useState<SmimeSuitePolicy | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const loadSettings = useCallback(async (initial = false) => {
    if (initial) {
      setLoading(true);
    } else {
      setRefreshing(true);
    }

    try {
      setSettings(await systemSettingsApi.get());

      const [relayResponse, quarantineResponse, gmEdgeResponse, smimeSuiteResponse] = await Promise.allSettled([
        runtimePolicyApi.getRelay(),
        runtimePolicyApi.getQuarantine(),
        runtimePolicyApi.getGmEdge(),
        runtimePolicyApi.getSmimeSuite(),
      ]);

      if (relayResponse.status === 'fulfilled') {
        setRelayPolicy(relayResponse.value);
      } else {
        setRelayPolicy(null);
      }
      if (quarantineResponse.status === 'fulfilled') {
        setQuarantinePolicy(quarantineResponse.value);
      } else {
        setQuarantinePolicy(null);
      }
      if (gmEdgeResponse.status === 'fulfilled') {
        setGmEdgePolicy(gmEdgeResponse.value);
      } else {
        setGmEdgePolicy(null);
      }
      if (smimeSuiteResponse.status === 'fulfilled') {
        setSmimeSuitePolicy(smimeSuiteResponse.value);
      } else {
        setSmimeSuitePolicy(null);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载配置总览失败'));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    let mounted = true;

    const loadInitialSettings = async () => {
      if (!mounted) return;
      await loadSettings(true);
    };

    void loadInitialSettings();

    return () => {
      mounted = false;
    };
  }, [loadSettings]);

  return {
    gmEdgePolicy,
    loadSettings,
    loading,
    quarantinePolicy,
    refreshing,
    relayPolicy,
    settings,
    smimeSuitePolicy,
  };
};
