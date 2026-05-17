import React from 'react';
import { Button, Spin } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { PageHeader, PageShell } from '../../components/Page';
import { RelayPolicyForm } from '../settings/PolicyForms';
import { useRuntimePolicyEditor } from './useRuntimePolicyEditor';

const RelayPolicyPage: React.FC = () => {
  const {
    handleRelayPolicySave,
    loadSettings,
    loading,
    refreshing,
    relayForm,
    relayPolicy,
  } = useRuntimePolicyEditor();

  if (loading) {
    return (
      <PageShell>
        <div className="page-loading">
          <Spin size="large" />
        </div>
      </PageShell>
    );
  }

  return (
    <PageShell>
      <PageHeader
        title="Relay 策略"
        description="配置受保护邮件的 SMTP Relay 主机、认证引用、超时和 Envelope From。"
        actions={(
          <Button icon={<ReloadOutlined />} onClick={() => loadSettings()} loading={refreshing}>
            刷新
          </Button>
        )}
      />
      <RelayPolicyForm form={relayForm} policy={relayPolicy} onSave={handleRelayPolicySave} />
    </PageShell>
  );
};

export default RelayPolicyPage;
