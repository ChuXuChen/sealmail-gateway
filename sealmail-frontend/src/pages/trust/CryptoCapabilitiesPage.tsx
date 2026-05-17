import React from 'react';
import { Button, Spin } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { PageHeader, PageShell } from '../../components/Page';
import { CryptoCapabilitiesPanel } from '../settings/SettingsReadOnlyPanels';
import { useSettingsSnapshot } from '../settings/useSettingsSnapshot';

const CryptoCapabilitiesPage: React.FC = () => {
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
        title="算法能力"
        description="当前 S/MIME、内容加密与国密算法能力快照。"
        actions={(
          <Button icon={<ReloadOutlined />} onClick={() => loadSettings()} loading={refreshing}>
            刷新
          </Button>
        )}
      />
      <CryptoCapabilitiesPanel settings={settings} />
    </PageShell>
  );
};

export default CryptoCapabilitiesPage;
