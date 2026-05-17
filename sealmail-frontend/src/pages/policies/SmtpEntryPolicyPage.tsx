import React from 'react';
import { Button, Spin } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { PageHeader, PageShell } from '../../components/Page';
import { SmtpEntryPanel } from '../settings/SettingsReadOnlyPanels';
import { useSettingsSnapshot } from '../settings/useSettingsSnapshot';

const SmtpEntryPolicyPage: React.FC = () => {
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
        title="SMTP 入口配置"
        description="当前 SMTP 入口监听配置只读展示。"
        actions={(
          <Button icon={<ReloadOutlined />} onClick={() => loadSettings()} loading={refreshing}>
            刷新
          </Button>
        )}
      />
      <SmtpEntryPanel settings={settings} />
    </PageShell>
  );
};

export default SmtpEntryPolicyPage;
