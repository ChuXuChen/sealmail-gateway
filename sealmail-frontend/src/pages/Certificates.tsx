import React, { useEffect, useState } from 'react';
import { Button, Dropdown, Form } from 'antd';
import {
  AuditOutlined,
  DownOutlined,
  FileAddOutlined,
  ImportOutlined,
  KeyOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import type { Certificate } from '../types';
import { PageHeader, PageShell } from '../components/Page';
import CertificateDetailDrawer from './certificates/CertificateDetailDrawer';
import {
  ImportCertificateModal,
  IssueByCaModal,
  SelfSignedCertificateModal,
} from './certificates/CertificateForms';
import CertificateTable from './certificates/CertificateTable';
import type {
  ImportCertificateValues,
  ImportCertificateMode,
  IssueByCaValues,
  SelfSignedCertificateValues,
} from './certificates/certificateUtils';
import { useCertificates } from './certificates/useCertificates';

const Certificates: React.FC = () => {
  const [importModalVisible, setImportModalVisible] = useState(false);
  const [importMode, setImportMode] = useState<ImportCertificateMode>('PUBLIC_CERTIFICATE');
  const [selfSignedModalVisible, setSelfSignedModalVisible] = useState(false);
  const [issueByCaModalVisible, setIssueByCaModalVisible] = useState(false);
  const [selectedCert, setSelectedCert] = useState<Certificate | null>(null);
  const [importForm] = Form.useForm<ImportCertificateValues>();
  const [selfSignedForm] = Form.useForm<SelfSignedCertificateValues>();
  const [issueByCaForm] = Form.useForm<IssueByCaValues>();
  const {
    bindCertificate,
    bindings,
    caCandidates,
    copyPem,
    data,
    deleteBinding,
    deleteCertificate,
    downloadPem,
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
  } = useCertificates();

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const openSelfSignedModal = () => {
    selfSignedForm.resetFields();
    selfSignedForm.setFieldsValue({
      algorithm: 'RSA',
      validityDays: 365,
      trusted: true,
    });
    setSelfSignedModalVisible(true);
  };

  const openIssueByCaModal = () => {
    issueByCaForm.resetFields();
    const firstCandidate = caCandidates[0];
    issueByCaForm.setFieldsValue({
      intermediateCaId: firstCandidate?.id,
      algorithm: firstCandidate?.algorithm === 'SM2' ? 'SM2' : 'RSA',
      validityDays: 365,
      trusted: true,
    });
    setIssueByCaModalVisible(true);
  };

  const handleIssueByCaSelectionChange = (certificateId: string) => {
    const candidate = caCandidates.find((cert) => cert.id === certificateId);
    if (candidate?.algorithm === 'RSA' || candidate?.algorithm === 'SM2') {
      issueByCaForm.setFieldValue('algorithm', candidate.algorithm);
    }
  };

  const handleImport = async (values: ImportCertificateValues) => {
    const ok = await importCertificate({
      ...values,
      privateKeyData: importMode === 'GATEWAY_MANAGED_PRIVATE_KEY' ? values.privateKeyData : undefined,
    });
    if (ok) {
      setImportModalVisible(false);
      importForm.resetFields();
    }
  };

  const handleGenerateSelfSigned = async (values: SelfSignedCertificateValues) => {
    const ok = await generateSelfSigned(values);
    if (ok) {
      setSelfSignedModalVisible(false);
      selfSignedForm.resetFields();
    }
  };

  const handleIssueByCa = async (values: IssueByCaValues) => {
    const ok = await issueByCa(values);
    if (ok) {
      setIssueByCaModalVisible(false);
      issueByCaForm.resetFields();
    }
  };

  return (
    <PageShell>
      <PageHeader
        title="终端证书"
        description="管理用户终端证书、信任状态、吊销状态和证书绑定关系。"
        actions={(
          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                {
                  key: 'import',
                  icon: <ImportOutlined />,
                  label: '导入公开证书',
                  onClick: () => {
                    setImportMode('PUBLIC_CERTIFICATE');
                    importForm.resetFields();
                    setImportModalVisible(true);
                  },
                },
                {
                  key: 'import-managed',
                  icon: <KeyOutlined />,
                  label: '导入网关托管私钥证书',
                  onClick: () => {
                    setImportMode('GATEWAY_MANAGED_PRIVATE_KEY');
                    importForm.resetFields();
                    setImportModalVisible(true);
                  },
                },
                {
                  key: 'self-signed',
                  icon: <FileAddOutlined />,
                  label: '生成本地域托管证书',
                  onClick: openSelfSignedModal,
                },
                {
                  key: 'issue-by-ca',
                  icon: <AuditOutlined />,
                  label: '通过 CA 签发本地域托管证书',
                  disabled: caCandidates.length === 0,
                  onClick: openIssueByCaModal,
                },
              ],
            }}
          >
            <Button type="primary" icon={<PlusOutlined />}>
              新增证书 <DownOutlined />
            </Button>
          </Dropdown>
        )}
      />

      <CertificateTable
        bindings={bindings}
        data={data}
        loading={loading}
        pagination={pagination}
        onBind={bindCertificate}
        onDelete={deleteCertificate}
        onDeleteBinding={deleteBinding}
        onPaginationChange={(page, size) => setPagination((prev) => ({ ...prev, page, size }))}
        onRevoke={revoke}
        onTrust={trust}
        onUntrust={untrust}
        onView={setSelectedCert}
      />

      <ImportCertificateModal
        form={importForm}
        mode={importMode}
        open={importModalVisible}
        onCancel={() => setImportModalVisible(false)}
        onFinish={handleImport}
      />
      <SelfSignedCertificateModal
        form={selfSignedForm}
        issuing={issuing}
        open={selfSignedModalVisible}
        onCancel={() => setSelfSignedModalVisible(false)}
        onFinish={handleGenerateSelfSigned}
      />
      <IssueByCaModal
        caCandidates={caCandidates}
        form={issueByCaForm}
        issuing={issuing}
        open={issueByCaModalVisible}
        onCaChange={handleIssueByCaSelectionChange}
        onCancel={() => setIssueByCaModalVisible(false)}
        onFinish={handleIssueByCa}
      />
      <CertificateDetailDrawer
        certificate={selectedCert}
        open={!!selectedCert}
        onClose={() => setSelectedCert(null)}
        onCopyPem={copyPem}
        onDownloadPem={downloadPem}
        onTrust={trust}
        onUntrust={untrust}
      />
    </PageShell>
  );
};

export default Certificates;
