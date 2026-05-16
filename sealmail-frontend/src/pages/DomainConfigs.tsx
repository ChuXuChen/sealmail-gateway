import React, { useEffect, useState } from 'react';
import { Button, Form, Space, Tabs } from 'antd';
import { MailOutlined, PlusOutlined } from '@ant-design/icons';
import type { DomainConfig } from '../types';
import { PageHeader, PageShell } from '../components/Page';
import DomainConfigDetailDrawer from './domain-configs/DomainConfigDetailDrawer';
import DomainConfigFormModal from './domain-configs/DomainConfigFormModal';
import DomainConfigTable from './domain-configs/DomainConfigTable';
import MailAuthSettingsPanel from './domain-configs/MailAuthSettingsPanel';
import type { DomainConfigFormValues, MailAuthFormValues } from './domain-configs/domainConfigUtils';
import { initialMailAuthValues } from './domain-configs/domainConfigUtils';
import { useDomainConfigs } from './domain-configs/useDomainConfigs';

const DomainConfigs: React.FC = () => {
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [detailVisible, setDetailVisible] = useState(false);
  const [activeTab, setActiveTab] = useState('domains');
  const [selectedDomain, setSelectedDomain] = useState<DomainConfig | null>(null);
  const [createForm] = Form.useForm<DomainConfigFormValues>();
  const [editForm] = Form.useForm<DomainConfigFormValues>();
  const [mailAuthForm] = Form.useForm<MailAuthFormValues>();
  const {
    copyText,
    createDomain,
    data,
    deleteDomain,
    dnsDomain,
    dnsRecords,
    loadData,
    loadDnsRecords,
    loadMailAuthConfig,
    loading,
    mailAuthConfig,
    mailAuthLoading,
    updateDomain,
    updateMailAuthConfig,
  } = useDomainConfigs();

  useEffect(() => {
    void loadData();
  }, [loadData]);

  useEffect(() => {
    mailAuthForm.setFieldsValue(initialMailAuthValues(mailAuthConfig));
  }, [mailAuthConfig, mailAuthForm]);

  const openCreateModal = () => {
    createForm.resetFields();
    createForm.setFieldsValue({
      localDomain: false,
      encryptionPolicy: 'ALLOW',
      preferredAlgorithm: 'AUTO',
      signingEnabled: false,
      dkimEnabled: false,
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
      active: domain.active,
    });
    setEditModalVisible(true);
  };

  const openDetail = (domain: DomainConfig) => {
    setSelectedDomain(domain);
    setDetailVisible(true);
  };

  const openMailAuthTab = async () => {
    setActiveTab('mail-auth');
    const config = mailAuthConfig || await loadMailAuthConfig();
    mailAuthForm.setFieldsValue(initialMailAuthValues(config));
    const defaultDomain = dnsDomain || data.find((item) => item.localDomain)?.domain || data[0]?.domain || '';
    if (defaultDomain) {
      await loadDnsRecords(defaultDomain);
    }
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

  const handleMailAuthUpdate = async (values: MailAuthFormValues) => {
    const ok = await updateMailAuthConfig(values);
    if (ok) {
      mailAuthForm.setFieldValue('clearDkimPrivateKeySecretRef', false);
    }
  };

  return (
    <PageShell>
      <PageHeader
        title="域名配置"
        description="维护本地域和远程域的加密策略、签名开关与邮件认证配置。"
        actions={(
          <Space>
            <Button icon={<MailOutlined />} onClick={openMailAuthTab}>
              邮件认证
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
              添加域名
            </Button>
          </Space>
        )}
      />

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'domains',
            label: '域名策略',
            children: (
              <DomainConfigTable
                data={data}
                loading={loading}
                onDelete={deleteDomain}
                onEdit={openEditModal}
                onView={openDetail}
              />
            ),
          },
          {
            key: 'mail-auth',
            label: '邮件认证',
            children: (
              <MailAuthSettingsPanel
                dnsDomain={dnsDomain}
                dnsRecords={dnsRecords}
                domains={data}
                form={mailAuthForm}
                loading={mailAuthLoading}
                onCopyText={copyText}
                onLoadDnsRecords={loadDnsRecords}
                onSubmit={handleMailAuthUpdate}
              />
            ),
          },
        ]}
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
