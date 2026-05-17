import React from 'react';
import { Button, Spin } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { PageHeader, PageShell } from '../../components/Page';
import { GmEdgePolicyForm } from '../settings/PolicyForms';
import { useRuntimePolicyEditor } from './useRuntimePolicyEditor';

const GmEdgePolicyPage: React.FC = () => {
  const {
    gmEdgeForm,
    gmEdgePolicy,
    handleGmEdgePolicySave,
    loadSettings,
    loading,
    refreshing,
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
        title="国密 Edge 策略"
        description="配置 TLCP / 国密 TLS 1.3 入口、出站 Smart Host、Postfix 回注和国密路由。"
        actions={(
          <Button icon={<ReloadOutlined />} onClick={() => loadSettings()} loading={refreshing}>
            刷新
          </Button>
        )}
      />
      <GmEdgePolicyForm form={gmEdgeForm} policy={gmEdgePolicy} onSave={handleGmEdgePolicySave} />
    </PageShell>
  );
};

export default GmEdgePolicyPage;
