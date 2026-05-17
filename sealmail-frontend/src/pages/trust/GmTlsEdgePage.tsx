import React from 'react';
import { Button, Spin } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { PageHeader, PageShell } from '../../components/Page';
import { GmTlsEdgePanel } from '../settings/SettingsReadOnlyPanels';
import { useSettingsSnapshot } from '../settings/useSettingsSnapshot';

const GmTlsEdgePage: React.FC = () => {
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
        title="国密 TLS Edge"
        description="国密 TLS Edge 协议、套件、端口和信任策略状态。"
        actions={(
          <Button icon={<ReloadOutlined />} onClick={() => loadSettings()} loading={refreshing}>
            刷新
          </Button>
        )}
      />
      <GmTlsEdgePanel settings={settings} />
    </PageShell>
  );
};

export default GmTlsEdgePage;
