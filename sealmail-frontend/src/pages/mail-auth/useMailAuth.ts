import { useCallback, useMemo, useState } from 'react';
import { message } from 'antd';
import { domainConfigApi, mailAuthApi } from '../../api/client';
import { getApiErrorMessage } from '../../api/errors';
import type {
  DnsRecord,
  DomainConfig,
  DomainMailAuthPolicy,
  MailAuthDnsProbe,
  MailAuthModernStatus,
  MailAuthPolicy,
} from '../../types';
import type {
  MailAuthDomainFormValues,
  MailAuthGlobalFormValues,
  RotateDkimFormValues,
} from './mailAuthUtils';
import { cleanList, domainPayload, globalPayload, normalizeDomain } from './mailAuthUtils';

export const useMailAuth = () => {
  const [domains, setDomains] = useState<DomainConfig[]>([]);
  const [globalPolicy, setGlobalPolicy] = useState<MailAuthPolicy | null>(null);
  const [status, setStatus] = useState<MailAuthModernStatus | null>(null);
  const [domainPolicy, setDomainPolicy] = useState<DomainMailAuthPolicy | null>(null);
  const [dnsRecords, setDnsRecords] = useState<DnsRecord[]>([]);
  const [probeResults, setProbeResults] = useState<MailAuthDnsProbe[]>([]);
  const [selectedDomain, setSelectedDomain] = useState('');
  const [loading, setLoading] = useState(false);
  const [savingGlobal, setSavingGlobal] = useState(false);
  const [savingDomain, setSavingDomain] = useState(false);
  const [probing, setProbing] = useState(false);
  const [rotating, setRotating] = useState(false);

  const loadDomainArtifacts = useCallback(async (domain: string) => {
    const normalized = normalizeDomain(domain);
    if (!normalized) {
      setSelectedDomain('');
      setDomainPolicy(null);
      setDnsRecords([]);
      setProbeResults([]);
      return null;
    }

    const [policyResponse, recordsResponse] = await Promise.all([
      mailAuthApi.domainPolicy(normalized),
      mailAuthApi.dnsRecords(normalized),
    ]);
    setSelectedDomain(normalized);
    setDomainPolicy(policyResponse.data.data);
    setDnsRecords(recordsResponse.data.data);
    return policyResponse.data.data;
  }, []);

  const loadInitial = useCallback(async () => {
    setLoading(true);
    try {
      const [policyResponse, statusResponse, domainsResponse] = await Promise.all([
        mailAuthApi.policy(),
        mailAuthApi.status(),
        domainConfigApi.findAll(),
      ]);
      const allDomains = domainsResponse.data.data;
      setGlobalPolicy(policyResponse.data.data);
      setStatus(statusResponse.data.data);
      setDomains(allDomains);
      const defaultDomain = selectedDomain
        || allDomains.find((item) => item.localDomain)?.domain
        || allDomains[0]?.domain
        || '';
      if (defaultDomain) {
        await loadDomainArtifacts(defaultDomain);
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载邮件认证配置失败'));
    } finally {
      setLoading(false);
    }
  }, [loadDomainArtifacts, selectedDomain]);

  const selectDomain = useCallback(async (domain: string) => {
    setLoading(true);
    try {
      await loadDomainArtifacts(domain);
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载域名邮件认证策略失败'));
    } finally {
      setLoading(false);
    }
  }, [loadDomainArtifacts]);

  const refreshStatus = useCallback(async () => {
    try {
      const response = await mailAuthApi.status();
      setStatus(response.data.data);
    } catch (error) {
      message.error(getApiErrorMessage(error, '刷新邮件认证状态失败'));
    }
  }, []);

  const saveGlobalPolicy = useCallback(async (values: MailAuthGlobalFormValues) => {
    setSavingGlobal(true);
    try {
      const response = await mailAuthApi.updatePolicy(globalPayload(values));
      setGlobalPolicy(response.data.data);
      await refreshStatus();
      message.success('全局邮件认证策略已保存');
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '保存全局邮件认证策略失败'));
      return false;
    } finally {
      setSavingGlobal(false);
    }
  }, [refreshStatus]);

  const saveDomainPolicy = useCallback(async (values: MailAuthDomainFormValues) => {
    if (!selectedDomain) {
      message.warning('请先选择域名');
      return false;
    }
    setSavingDomain(true);
    try {
      const response = await mailAuthApi.updateDomainPolicy(selectedDomain, domainPayload(values));
      const recordsResponse = await mailAuthApi.dnsRecords(selectedDomain);
      setDomainPolicy(response.data.data);
      setDnsRecords(recordsResponse.data.data);
      await refreshStatus();
      message.success('域名邮件认证策略已保存');
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '保存域名邮件认证策略失败'));
      return false;
    } finally {
      setSavingDomain(false);
    }
  }, [refreshStatus, selectedDomain]);

  const probeDns = useCallback(async () => {
    if (!selectedDomain) {
      message.warning('请先选择域名');
      return;
    }
    setProbing(true);
    try {
      const response = await mailAuthApi.dnsProbe(selectedDomain);
      setProbeResults(response.data.data);
      await refreshStatus();
      message.success('DNS 探测已完成');
    } catch (error) {
      message.error(getApiErrorMessage(error, 'DNS 探测失败'));
    } finally {
      setProbing(false);
    }
  }, [refreshStatus, selectedDomain]);

  const rotateDkimSelector = useCallback(async (values: RotateDkimFormValues) => {
    if (!selectedDomain) {
      message.warning('请先选择域名');
      return false;
    }
    setRotating(true);
    try {
      const response = await mailAuthApi.rotateDkimSelector(selectedDomain, {
        selector: values.selector,
        keySecretRef: values.keySecretRef?.trim() || undefined,
        keyPath: values.keyPath?.trim() || undefined,
        signedHeaders: cleanList(values.signedHeaders),
      });
      const recordsResponse = await mailAuthApi.dnsRecords(selectedDomain);
      setDomainPolicy(response.data.data);
      setDnsRecords(recordsResponse.data.data);
      await refreshStatus();
      message.success('DKIM selector 已轮换');
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, 'DKIM selector 轮换失败'));
      return false;
    } finally {
      setRotating(false);
    }
  }, [refreshStatus, selectedDomain]);

  const copyText = useCallback(async (value: string) => {
    if (!value) return;
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(value);
    }
    message.success('已复制');
  }, []);

  const localDomains = useMemo(
    () => domains.filter((item) => item.localDomain),
    [domains],
  );

  return {
    copyText,
    dnsRecords,
    domainPolicy,
    domains,
    globalPolicy,
    loadInitial,
    loading,
    localDomains,
    probeDns,
    probeResults,
    probing,
    refreshStatus,
    rotateDkimSelector,
    rotating,
    saveDomainPolicy,
    saveGlobalPolicy,
    savingDomain,
    savingGlobal,
    selectDomain,
    selectedDomain,
    status,
  };
};
