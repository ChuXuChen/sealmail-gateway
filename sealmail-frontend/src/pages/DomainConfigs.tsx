import React, { useEffect, useState } from 'react';
import { Button, Form } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { DomainConfig } from '../types';
import { PageHeader, PageShell } from '../components/Page';
import DomainConfigDetailDrawer from './domain-configs/DomainConfigDetailDrawer';
import DomainConfigFormModal from './domain-configs/DomainConfigFormModal';
import DomainConfigTable from './domain-configs/DomainConfigTable';
import type { DomainConfigFormValues } from './domain-configs/domainConfigUtils';
import { useDomainConfigs } from './domain-configs/useDomainConfigs';

const DomainConfigs: React.FC = () => {
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedDomain, setSelectedDomain] = useState<DomainConfig | null>(null);
  const [createForm] = Form.useForm<DomainConfigFormValues>();
  const [editForm] = Form.useForm<DomainConfigFormValues>();
  const {
    createDomain,
    data,
    deleteDomain,
    loadData,
    loading,
    updateDomain,
  } = useDomainConfigs();

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const openCreateModal = () => {
    createForm.resetFields();
    createForm.setFieldsValue({
      localDomain: false,
      encryptionPolicy: 'ALLOW',
      preferredAlgorithm: 'AUTO',
      signingEnabled: false,
      dkimEnabled: false,
      deliveryHost: undefined,
      deliveryPort: 25,
      active: true,
    });
    setCreateModalVisible(true);
  };

  const openEditModal = (domain: DomainConfig) => {
    setSelectedDomain(domain);
    editForm.setFieldsValue({
      encryptionPolicy: domain.encryptionPolicy,
      preferredAlgorithm: domain.preferredAlgorithm || 'AUTO',
      signingEnabled: domain.signingEnabled,
      dkimEnabled: domain.dkimEnabled,
      localDomain: domain.localDomain,
      deliveryHost: domain.deliveryHost,
      deliveryPort: domain.deliveryPort || 25,
      active: domain.active,
    });
    setEditModalVisible(true);
  };

  const openDetail = (domain: DomainConfig) => {
    setSelectedDomain(domain);
    setDetailVisible(true);
  };

  const handleCreate = async (values: DomainConfigFormValues) => {
    const ok = await createDomain(values);
    if (ok) {
      setCreateModalVisible(false);
      createForm.resetFields();
    }
  };

  const handleEdit = async (values: DomainConfigFormValues) => {
    if (!selectedDomain) return;
    const ok = await updateDomain(selectedDomain.id, values);
    if (ok) {
      setEditModalVisible(false);
      setSelectedDomain(null);
    }
  };

  return (
    <PageShell>
      <PageHeader
        title="域名配置"
        description="维护本地域和远程域的加密策略、签名开关、固定投递目标与启用状态。"
        actions={(
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
            添加域名
          </Button>
        )}
      />

      <DomainConfigTable
        data={data}
        loading={loading}
        onDelete={deleteDomain}
        onEdit={openEditModal}
        onView={openDetail}
      />

      <DomainConfigFormModal
        form={createForm}
        mode="create"
        open={createModalVisible}
        onCancel={() => setCreateModalVisible(false)}
        onFinish={handleCreate}
      />
      <DomainConfigFormModal
        form={editForm}
        mode="edit"
        open={editModalVisible}
        onCancel={() => {
          setEditModalVisible(false);
          setSelectedDomain(null);
        }}
        onFinish={handleEdit}
      />
      <DomainConfigDetailDrawer
        domain={selectedDomain}
        open={detailVisible}
        onClose={() => {
          setDetailVisible(false);
          setSelectedDomain(null);
        }}
      />
    </PageShell>
  );
};

export default DomainConfigs;
