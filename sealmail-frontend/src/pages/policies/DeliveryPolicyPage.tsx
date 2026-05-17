import React from 'react';
import { Button, Spin } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { PageHeader, PageShell } from '../../components/Page';
import { DeliveryChainPanel } from '../settings/SettingsReadOnlyPanels';
import { useSettingsSnapshot } from '../settings/useSettingsSnapshot';

const DeliveryPolicyPage: React.FC = () => {
  const { loadSettings, loading, refreshing, settings } = useSettingsSnapshot();

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
        title="投递链路配置"
        description="当前 Postfix 回注或 SMTP 中继投递链路只读展示。"
        actions={(
          <Button icon={<ReloadOutlined />} onClick={() => loadSettings()} loading={refreshing}>
            刷新
          </Button>
        )}
      />
      <DeliveryChainPanel settings={settings} />
    </PageShell>
  );
};

export default DeliveryPolicyPage;
