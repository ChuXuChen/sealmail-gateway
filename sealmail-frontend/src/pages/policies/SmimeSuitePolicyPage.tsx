import React from 'react';
import { Button, Spin } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { PageHeader, PageShell } from '../../components/Page';
import { SmimeSuitePolicyForm } from '../settings/PolicyForms';
import { useRuntimePolicyEditor } from './useRuntimePolicyEditor';

const SmimeSuitePolicyPage: React.FC = () => {
  const {
    handleSmimeSuitePolicySave,
    loadSettings,
    loading,
    refreshing,
    smimeSuiteForm,
    smimeSuitePolicy,
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
        title="S/MIME 套件策略"
        description="选择标准与国密 S/MIME 加密默认套件。"
        actions={(
          <Button icon={<ReloadOutlined />} onClick={() => loadSettings()} loading={refreshing}>
            刷新
          </Button>
        )}
      />
      <SmimeSuitePolicyForm
        form={smimeSuiteForm}
        policy={smimeSuitePolicy}
        onSave={handleSmimeSuitePolicySave}
      />
    </PageShell>
  );
};

export default SmimeSuitePolicyPage;
