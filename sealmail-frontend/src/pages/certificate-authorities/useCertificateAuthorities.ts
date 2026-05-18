import { useCallback, useMemo, useState } from 'react';
import { message } from 'antd';
import { caApi, certificateApi } from '../../api/client';
import { getApiErrorMessage } from '../../api/errors';
import type { Certificate } from '../../types';
import { copyTextToClipboard, downloadTextFile, safePemFilename } from '../../utils/pemExport';
import type { CaFilters, CaTableRecord, CreateIntermediateCaValues, CreateRootCaValues, SignCsrValues } from './caUtils';
import { defaultCaFilters, getUnavailableSigningReason, matchesCertificateFilters } from './caUtils';

export const useCertificateAuthorities = () => {
  const [data, setData] = useState<Certificate[]>([]);
  const [filters, setFilters] = useState<CaFilters>(defaultCaFilters);
  const [loading, setLoading] = useState(false);
  const [issuing, setIssuing] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await caApi.list();
      setData(res.data.data);
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载 CA 列表失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  const roots = useMemo(
    () => data.filter((c) => c.pathLenConstraint === 1),
    [data],
  );

  const rootById = useMemo(
    () => new Map(roots.map((root) => [root.id, root])),
    [roots],
  );

  const rootCandidates = useMemo(
    () => roots.filter((c) => c.hasPrivateKey && !c.revoked && c.chainUsable),
    [roots],
  );

  const signingCaCandidates = useMemo(
    () => data.filter((c) => c.pathLenConstraint === 0 && !getUnavailableSigningReason(c)),
    [data],
  );

  const algorithmOptions = useMemo(() => {
    const algorithms = Array.from(new Set(data.map((item) => item.algorithm).filter(Boolean)));
    return [
      { value: 'ALL', label: '全部算法' },
      ...algorithms.map((algorithm) => ({ value: algorithm as string, label: algorithm as string })),
    ];
  }, [data]);

  const filteredFlatData = useMemo(
    () => data.filter((cert) => matchesCertificateFilters(cert, filters)),
    [data, filters],
  );

  const tableData = useMemo<CaTableRecord[]>(() => {
    const matchingIds = new Set(filteredFlatData.map((cert) => cert.id));
    const childrenByRootId = new Map<string, CaTableRecord[]>();
    const orphanIntermediates: CaTableRecord[] = [];

    data
      .filter((cert) => cert.pathLenConstraint === 0 && matchingIds.has(cert.id))
      .forEach((cert) => {
        const record: CaTableRecord = { ...cert };
        if (cert.issuerCertId) {
          const children = childrenByRootId.get(cert.issuerCertId) || [];
          children.push(record);
          childrenByRootId.set(cert.issuerCertId, children);
        } else {
          orphanIntermediates.push(record);
        }
      });

    const rootRecords = roots.reduce<CaTableRecord[]>((acc, root) => {
      const rootMatches = matchingIds.has(root.id);
      const children = childrenByRootId.get(root.id) || [];

      if (!rootMatches && children.length === 0) {
        return acc;
      }

      acc.push({
        ...root,
        children: children.length > 0 ? children : undefined,
      });
      return acc;
    }, []);

    if (filters.type !== 'ROOT') {
      rootRecords.push(...orphanIntermediates);
    }

    return rootRecords;
  }, [data, filteredFlatData, filters.type, roots]);

  const expandedRowKeys = useMemo(
    () => tableData.filter((record) => record.children?.length).map((record) => record.id),
    [tableData],
  );

  const createRoot = useCallback(async (values: CreateRootCaValues) => {
    setIssuing(true);
    try {
      await caApi.createRoot(values);
      message.success('Root CA 已创建');
      await loadData();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, 'Root CA 创建失败'));
      return false;
    } finally {
      setIssuing(false);
    }
  }, [loadData]);

  const createIntermediate = useCallback(async (values: CreateIntermediateCaValues) => {
    setIssuing(true);
    try {
      await caApi.createIntermediate(values);
      message.success('Intermediate CA 已创建');
      await loadData();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, 'Intermediate CA 创建失败'));
      return false;
    } finally {
      setIssuing(false);
    }
  }, [loadData]);

  const signCsr = useCallback(async (values: SignCsrValues) => {
    setIssuing(true);
    try {
      await certificateApi.signCsr(values);
      message.success('CSR 已签发为终端证书');
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, 'CSR 签发失败'));
      return false;
    } finally {
      setIssuing(false);
    }
  }, []);

  const revoke = useCallback(async (id: string) => {
    try {
      await caApi.revoke(id, '管理员手动吊销');
      message.success('CA 已吊销（已签发的子证书已级联吊销）');
      await loadData();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '吊销失败'));
      return false;
    }
  }, [loadData]);

  const trust = useCallback(async (id: string) => {
    try {
      await caApi.trust(id);
      message.success('已标记为信任');
      await loadData();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
      return false;
    }
  }, [loadData]);

  const untrust = useCallback(async (id: string) => {
    try {
      await caApi.untrust(id);
      message.success('已撤销信任');
      await loadData();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
      return false;
    }
  }, [loadData]);

  const deleteCa = useCallback(async (id: string) => {
    try {
      await caApi.delete(id);
      message.success('CA 已删除');
      await loadData();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除失败'));
      return false;
    }
  }, [loadData]);

  const fetchPem = useCallback(async (id: string) => {
    const response = await caApi.pem(id);
    return response.data;
  }, []);

  const copyPem = useCallback(async (record: Certificate) => {
    try {
      const pem = await fetchPem(record.id);
      await copyTextToClipboard(pem);
      message.success('CA 公开 PEM 已复制');
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '复制 CA PEM 失败'));
      return false;
    }
  }, [fetchPem]);

  const downloadPem = useCallback(async (record: Certificate) => {
    try {
      const pem = await fetchPem(record.id);
      downloadTextFile(safePemFilename(record.alias || record.subjectDn || record.id, 'ca'), pem);
      message.success('CA 公开 PEM 已下载');
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '下载 CA PEM 失败'));
      return false;
    }
  }, [fetchPem]);

  const updateFilter = useCallback(<K extends keyof CaFilters>(key: K, value: CaFilters[K]) => {
    setFilters((prev) => ({ ...prev, [key]: value }));
  }, []);

  const resetFilters = useCallback(() => {
    setFilters(defaultCaFilters);
  }, []);

  return {
    algorithmOptions,
    createIntermediate,
    createRoot,
    copyPem,
    data,
    deleteCa,
    downloadPem,
    expandedRowKeys,
    filters,
    issuing,
    loading,
    loadData,
    resetFilters,
    revoke,
    rootById,
    rootCandidates,
    roots,
    signCsr,
    signingCaCandidates,
    tableData,
    trust,
    untrust,
    updateFilter,
  };
};
