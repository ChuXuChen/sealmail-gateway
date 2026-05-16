import { useCallback, useMemo, useState } from 'react';
import { message } from 'antd';
import { caApi, certificateApi, certificateBindingApi } from '../../api/client';
import { getApiErrorMessage } from '../../api/errors';
import type { Certificate, CertificateBinding, CertificateBindingPurpose } from '../../types';
import type {
  CertificatePagination,
  ImportCertificateValues,
  IssueByCaValues,
  SelfSignedCertificateValues,
} from './certificateUtils';

export const useCertificates = () => {
  const [data, setData] = useState<Certificate[]>([]);
  const [cas, setCas] = useState<Certificate[]>([]);
  const [bindings, setBindings] = useState<CertificateBinding[]>([]);
  const [loading, setLoading] = useState(false);
  const [issuing, setIssuing] = useState(false);
  const [pagination, setPagination] = useState<CertificatePagination>({ page: 1, size: 10, total: 0 });

  const caCandidates = useMemo(
    () => cas.filter((candidate) => candidate.pathLenConstraint === 0 && candidate.hasPrivateKey && !candidate.revoked && candidate.chainUsable),
    [cas],
  );

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [certRes, caRes, bindingRes] = await Promise.all([
        certificateApi.list({ page: pagination.page, size: pagination.size }),
        caApi.list(),
        certificateBindingApi.list(),
      ]);
      setData(certRes.data.data.items);
      setCas(caRes.data.data);
      setBindings(bindingRes.data.data);
      setPagination((prev) => ({
        ...prev,
        total: certRes.data.data.total,
      }));
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载证书列表失败'));
    } finally {
      setLoading(false);
    }
  }, [pagination.page, pagination.size]);

  const importCertificate = useCallback(async (values: ImportCertificateValues) => {
    try {
      await certificateApi.import(values);
      message.success('证书导入成功');
      await loadData();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '证书导入失败'));
      return false;
    }
  }, [loadData]);

  const generateSelfSigned = useCallback(async (values: SelfSignedCertificateValues) => {
    setIssuing(true);
    try {
      await certificateApi.generateSelfSigned({
        ownerEmail: values.ownerEmail,
        algorithm: values.algorithm,
        subjectDn: values.subjectDn,
        alias: values.alias,
        validityDays: values.validityDays,
        trusted: values.trusted,
      });
      message.success('自签名证书已生成');
      await loadData();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '自签名证书生成失败'));
      return false;
    } finally {
      setIssuing(false);
    }
  }, [loadData]);

  const issueByCa = useCallback(async (values: IssueByCaValues) => {
    setIssuing(true);
    try {
      await certificateApi.issue(values);
      message.success('证书已签发');
      await loadData();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, 'CA 签发失败'));
      return false;
    } finally {
      setIssuing(false);
    }
  }, [loadData]);

  const trust = useCallback(async (id: string) => {
    try {
      await certificateApi.trust(id);
      message.success('证书已标记为信任');
      await loadData();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
      return false;
    }
  }, [loadData]);

  const untrust = useCallback(async (id: string) => {
    try {
      await certificateApi.untrust(id);
      message.success('已撤销该证书的信任');
      await loadData();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
      return false;
    }
  }, [loadData]);

  const revoke = useCallback(async (id: string) => {
    try {
      await certificateApi.revoke(id, '管理员手动吊销');
      message.success('证书已吊销');
      await loadData();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
      return false;
    }
  }, [loadData]);

  const deleteCertificate = useCallback(async (id: string) => {
    try {
      await certificateApi.delete(id);
      message.success('证书已删除');
      await loadData();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '证书删除失败'));
      return false;
    }
  }, [loadData]);

  const bindCertificate = useCallback(async (record: Certificate, purpose: CertificateBindingPurpose) => {
    try {
      await certificateBindingApi.upsert({
        ownerEmail: record.ownerEmail,
        certificateId: record.id,
        purpose,
        enabled: true,
      });
      message.success(purpose === 'ENCRYPTION' ? '加密证书绑定已更新' : '签名证书绑定已更新');
      await loadData();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '证书绑定失败'));
      return false;
    }
  }, [loadData]);

  const deleteBinding = useCallback(async (id: string) => {
    try {
      await certificateBindingApi.delete(id);
      message.success('证书绑定已删除');
      await loadData();
      return true;
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除证书绑定失败'));
      return false;
    }
  }, [loadData]);

  return {
    bindCertificate,
    bindings,
    caCandidates,
    data,
    deleteBinding,
    deleteCertificate,
    generateSelfSigned,
    importCertificate,
    issueByCa,
    issuing,
    loadData,
    loading,
    pagination,
    revoke,
    setPagination,
    trust,
    untrust,
  };
};
