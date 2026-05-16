import { useCallback, useState } from 'react';
import { message } from 'antd';
import { domainConfigApi, mailAuthApi } from '../../api/client';
import { getApiErrorMessage } from '../../api/errors';
import type { DnsRecord, DomainConfig, MailAuthConfigRequest } from '../../types';
import type { DomainConfigFormValues, MailAuthFormValues } from './domainConfigUtils';
import { normalizeDomain } from './domainConfigUtils';

export const useDomainConfigs = () => {
  const [data, setData] = useState<DomainConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [mailAuthConfig, setMailAuthConfig] = useState<MailAuthFormValues | null>(null);
  const [mailAuthLoading, setMailAuthLoading] = useState(false);
  const [dnsRecords, setDnsRecords] = useState<DnsRecord[]>([]);
  const [dnsDomain, setDnsDomain] = useState('');

  const loadMailAuthConfig = useCallback(async (showError = true) => {
    setMailAuthLoading(true);
    try {
      const response = await mailAuthApi.config();
      setMailAuthConfig(response.data.data);
      return response.data.data;
    } catch (error) {
      setMailAuthConfig(null);
      if (showError) {
        message.error(getApiErrorMessage(error, '加载邮件认证配置失败'));
      }
      return null;
    } finally {
      setMailAuthLoading(false);
    }
  }, []);

  const loadDnsRecords = useCallback(async (domain: string) => {
    const normalized = normalizeDomain(domain);
    if (!normalized) {
      setDnsRecords([]);
      setDnsDomain('');
      return;
    }

    try {
      const response = await mailAuthApi.dnsRecords(normalized);
      setDnsDomain(normalized);
      setDnsRecords(response.data.data);
    } catch (error) {
      message.error(getApiErrorMessage(error, 'DNS记录生成失败'));
    }
  }, []);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await domainConfigApi.findAll();
      const domains = response.data.data;
      setData(domains);
      const defaultDnsDomain = dnsDomain || domains.find((item) => item.localDomain)?.domain || domains[0]?.domain || '';
      if (defaultDnsDomain) {
        void loadDnsRecords(defaultDnsDomain);
      } else {
        setDnsRecords([]);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载域名配置失败'));
    } finally {
      setLoading(false);
    }

    await loadMailAuthConfig(false);
  }, [dnsDomain, loadDnsRecords, loadMailAuthConfig]);

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

  const updateMailAuthConfig = useCallback(async (values: MailAuthFormValues) => {
    try {
      const payload: MailAuthConfigRequest = {
        ...values,
        dmarcQuarantineRejectPolicy: values.dmarcFailureAction
          ? values.dmarcFailureAction !== 'LOG_ONLY'
          : values.dmarcQuarantineRejectPolicy,
      };
      const response = await mailAuthApi.updateConfig(payload);
      setMailAuthConfig({
        ...response.data.data,
        clearDkimPrivateKeySecretRef: false,
      });
      message.success('邮件认证配置已更新');
      if (dnsDomain) {
        await loadDnsRecords(dnsDomain);
      }
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '邮件认证配置更新失败'));
      return false;
    }
  }, [dnsDomain, loadDnsRecords]);

  const copyText = useCallback(async (value: string) => {
    if (!value) return;
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(value);
    }
    message.success('已复制');
  }, []);

  return {
    data,
    dnsDomain,
    dnsRecords,
    loading,
    mailAuthConfig,
    mailAuthLoading,
    copyText,
    createDomain,
    deleteDomain,
    loadData,
    loadDnsRecords,
    loadMailAuthConfig,
    updateDomain,
    updateMailAuthConfig,
  };
};
