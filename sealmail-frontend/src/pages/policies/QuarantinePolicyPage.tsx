import React from 'react';
import { Button, Spin } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { PageHeader, PageShell } from '../../components/Page';
import { QuarantinePolicyForm } from '../settings/PolicyForms';
import { useRuntimePolicyEditor } from './useRuntimePolicyEditor';

const QuarantinePolicyPage: React.FC = () => {
  const {
    handleQuarantinePolicySave,
    loadSettings,
    loading,
    quarantineForm,
    quarantinePolicy,
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
        title="隔离策略"
        description="配置 DLP 隔离保留天数、通知开关和放行前加密要求。"
        actions={(
          <Button icon={<ReloadOutlined />} onClick={() => loadSettings()} loading={refreshing}>
            刷新
          </Button>
        )}
      />
      <QuarantinePolicyForm form={quarantineForm} policy={quarantinePolicy} onSave={handleQuarantinePolicySave} />
    </PageShell>
  );
};

export default QuarantinePolicyPage;
