import React, { useEffect, useState } from 'react';
import { Button, Dropdown, Form, Modal, Space } from 'antd';
import {
  ApartmentOutlined,
  DownOutlined,
  FileProtectOutlined,
  ReloadOutlined,
  SafetyOutlined,
} from '@ant-design/icons';
import type { Certificate } from '../types';
import { PageHeader, PageShell } from '../components/Page';
import CaDetailDrawer from './certificate-authorities/CaDetailDrawer';
import { IntermediateCaModal, RootCaModal, SignCsrModal } from './certificate-authorities/CaForms';
import CaTable from './certificate-authorities/CaTable';
import type {
  ConfirmAction,
  ConfirmActionType,
  CreateIntermediateCaValues,
  CreateRootCaValues,
  SignCsrValues,
} from './certificate-authorities/caUtils';
import { roleOf } from './certificate-authorities/caUtils';
import { useCertificateAuthorities } from './certificate-authorities/useCertificateAuthorities';

const CertificateAuthorities: React.FC = () => {
  const [rootModal, setRootModal] = useState(false);
  const [intModal, setIntModal] = useState(false);
  const [signCsrModal, setSignCsrModal] = useState(false);
  const [detail, setDetail] = useState<Certificate | null>(null);
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [rootForm] = Form.useForm<CreateRootCaValues>();
  const [intForm] = Form.useForm<CreateIntermediateCaValues>();
  const [signCsrForm] = Form.useForm<SignCsrValues>();
  const {
    algorithmOptions,
    createIntermediate,
    createRoot,
    deleteCa,
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
  } = useCertificateAuthorities();

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const openRootModal = () => {
    rootForm.resetFields();
    rootForm.setFieldsValue({ algorithm: 'RSA', validityDays: 3650 });
    setRootModal(true);
  };

  const openIntermediateModal = () => {
    intForm.resetFields();
    const firstCandidate = rootCandidates[0];
    intForm.setFieldsValue({
      rootCaId: firstCandidate?.id,
      algorithm: firstCandidate?.algorithm === 'SM2' ? 'SM2' : 'RSA',
      validityDays: 1825,
    });
    setIntModal(true);
  };

  const handleRootSelectionChange = (certificateId: string) => {
    const candidate = rootCandidates.find((cert) => cert.id === certificateId);
    if (candidate?.algorithm === 'RSA' || candidate?.algorithm === 'SM2') {
      intForm.setFieldValue('algorithm', candidate.algorithm);
    }
  };

  const openSignCsrModal = () => {
    signCsrForm.resetFields();
    signCsrForm.setFieldsValue({
      validityDays: 365,
      trusted: true,
    });
    setSignCsrModal(true);
  };

  const openConfirm = (type: ConfirmActionType, record: Certificate, closeDetail = false) => {
    setConfirmAction({ type, record, closeDetail });
  };

  const handleCreateRoot = async (values: CreateRootCaValues) => {
    const ok = await createRoot(values);
    if (ok) {
      setRootModal(false);
      rootForm.resetFields();
    }
  };

  const handleCreateIntermediate = async (values: CreateIntermediateCaValues) => {
    const ok = await createIntermediate(values);
    if (ok) {
      setIntModal(false);
      intForm.resetFields();
    }
  };

  const handleSignCsr = async (values: SignCsrValues) => {
    const ok = await signCsr(values);
    if (ok) {
      setSignCsrModal(false);
      signCsrForm.resetFields();
    }
  };

  const handleConfirmOk = async () => {
    if (!confirmAction) return;

    setConfirming(true);
    try {
      if (confirmAction.type === 'untrust') {
        await untrust(confirmAction.record.id);
      }
      if (confirmAction.type === 'revoke') {
        await revoke(confirmAction.record.id);
      }
      if (confirmAction.type === 'delete') {
        await deleteCa(confirmAction.record.id);
      }
      if (confirmAction.closeDetail) {
        setDetail(null);
      }
      setConfirmAction(null);
    } finally {
      setConfirming(false);
    }
  };

  const confirmTitle = confirmAction?.type === 'untrust'
    ? '撤销此 CA 的信任？'
    : confirmAction?.type === 'revoke'
      ? `确定吊销此 ${roleOf(confirmAction.record)}？`
      : '确定删除此 CA？';

  const confirmContent = confirmAction?.type === 'revoke'
    ? '由它签发的所有子证书将级联吊销。'
    : confirmAction?.type === 'delete'
      ? '若仍有下级证书，后端会阻止删除。'
      : undefined;

  return (
    <PageShell>
      <PageHeader
        title="CA 证书"
        description="管理 Root CA、Intermediate CA、CSR 签发与 CA 信任状态。"
        actions={(
          <Space>
            <Button icon={<ReloadOutlined />} loading={loading} onClick={loadData}>
              刷新
            </Button>
            <Dropdown
              trigger={['click']}
              menu={{
                items: [
                  {
                    key: 'csr',
                    icon: <FileProtectOutlined />,
                    label: '签发 CSR',
                    disabled: signingCaCandidates.length === 0,
                    onClick: openSignCsrModal,
                  },
                  {
                    key: 'root',
                    icon: <SafetyOutlined />,
                    label: '签发 Root CA',
                    onClick: openRootModal,
                  },
                  {
                    key: 'intermediate',
                    icon: <ApartmentOutlined />,
                    label: '签发 Intermediate CA',
                    disabled: rootCandidates.length === 0,
                    onClick: openIntermediateModal,
                  },
                ],
              }}
            >
              <Button type="primary" icon={<FileProtectOutlined />}>
                签发 <DownOutlined />
              </Button>
            </Dropdown>
          </Space>
        )}
      />

      <CaTable
        algorithmOptions={algorithmOptions}
        data={tableData}
        expandedRowKeys={expandedRowKeys}
        filters={filters}
        loading={loading}
        roots={roots}
        onConfirmAction={openConfirm}
        onResetFilters={resetFilters}
        onTrust={trust}
        onUpdateFilter={updateFilter}
        onView={setDetail}
      />

      <RootCaModal
        form={rootForm}
        issuing={issuing}
        open={rootModal}
        onCancel={() => setRootModal(false)}
        onFinish={handleCreateRoot}
      />
      <IntermediateCaModal
        form={intForm}
        issuing={issuing}
        open={intModal}
        rootCandidates={rootCandidates}
        onRootChange={handleRootSelectionChange}
        onCancel={() => setIntModal(false)}
        onFinish={handleCreateIntermediate}
      />
      <SignCsrModal
        form={signCsrForm}
        issuing={issuing}
        open={signCsrModal}
        signingCaCandidates={signingCaCandidates}
        onCancel={() => setSignCsrModal(false)}
        onFinish={handleSignCsr}
      />
      <CaDetailDrawer
        detail={detail}
        rootById={rootById}
        onClose={() => setDetail(null)}
        onConfirmAction={openConfirm}
        onTrust={trust}
      />

      <Modal
        title={confirmTitle}
        open={!!confirmAction}
        onCancel={() => setConfirmAction(null)}
        onOk={handleConfirmOk}
        confirmLoading={confirming}
        okText="确认"
        cancelText="取消"
        okButtonProps={{ danger: confirmAction?.type === 'revoke' || confirmAction?.type === 'delete' }}
      >
        {confirmContent}
      </Modal>
    </PageShell>
  );
};

export default CertificateAuthorities;
