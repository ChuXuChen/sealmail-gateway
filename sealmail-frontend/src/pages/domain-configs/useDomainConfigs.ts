import { useCallback, useState } from 'react';
import { message } from 'antd';
import { domainConfigApi } from '../../api/client';
import { getApiErrorMessage } from '../../api/errors';
import type { DomainConfig } from '../../types';
import type { DomainConfigFormValues } from './domainConfigUtils';
import { normalizeDomain } from './domainConfigUtils';

export const useDomainConfigs = () => {
  const [data, setData] = useState<DomainConfig[]>([]);
  const [loading, setLoading] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await domainConfigApi.findAll();
      setData(response.data.data);
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载域名配置失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  const createDomain = useCallback(async (values: DomainConfigFormValues) => {
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
      await loadData();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '域名配置创建失败'));
      return false;
    }
  }, [loadData]);

  const updateDomain = useCallback(async (
    id: string,
    values: Omit<DomainConfigFormValues, 'domain' | 'localDomain'>,
  ) => {
    try {
      await domainConfigApi.update(id, {
        encryptionPolicy: values.encryptionPolicy,
        preferredAlgorithm: values.preferredAlgorithm,
        signingEnabled: values.signingEnabled,
        dkimEnabled: values.dkimEnabled,
        active: values.active,
      });
      message.success('域名配置更新成功');
      await loadData();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '域名配置更新失败'));
      return false;
    }
  }, [loadData]);

  const deleteDomain = useCallback(async (id: string) => {
    try {
      await domainConfigApi.delete(id);
      message.success('域名配置已删除');
      await loadData();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '域名配置删除失败'));
      return false;
    }
  }, [loadData]);

  return {
    data,
    loading,
    createDomain,
    deleteDomain,
    loadData,
    updateDomain,
  };
};
