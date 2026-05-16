import React, { useEffect, useState } from 'react';
import { Button, Dropdown, Form } from 'antd';
import {
  AuditOutlined,
  DownOutlined,
  FileAddOutlined,
  ImportOutlined,
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
  IssueByCaValues,
  SelfSignedCertificateValues,
} from './certificates/certificateUtils';
import { useCertificates } from './certificates/useCertificates';

const Certificates: React.FC = () => {
  const [importModalVisible, setImportModalVisible] = useState(false);
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
    issueByCaForm.setFieldsValue({
      algorithm: 'RSA',
      validityDays: 365,
      trusted: true,
    });
    setIssueByCaModalVisible(true);
  };

  const handleImport = async (values: ImportCertificateValues) => {
    const ok = await importCertificate(values);
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
                  label: '导入已有证书',
                  onClick: () => setImportModalVisible(true),
                },
                {
                  key: 'self-signed',
                  icon: <FileAddOutlined />,
                  label: '生成自签名证书',
                  onClick: openSelfSignedModal,
                },
                {
                  key: 'issue-by-ca',
                  icon: <AuditOutlined />,
                  label: '通过 Intermediate CA 签发',
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
        onCancel={() => setIssueByCaModalVisible(false)}
        onFinish={handleIssueByCa}
      />
      <CertificateDetailDrawer
        certificate={selectedCert}
        open={!!selectedCert}
        onClose={() => setSelectedCert(null)}
        onTrust={trust}
        onUntrust={untrust}
      />
    </PageShell>
  );
};

export default Certificates;
