import React from 'react';
import { Button, Spin } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { PageHeader, PageShell } from '../../components/Page';
import { CertificateValidationPanel } from '../settings/SettingsReadOnlyPanels';
import { useSettingsSnapshot } from '../settings/useSettingsSnapshot';

const CertificateValidationPage: React.FC = () => {
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
        title="证书校验状态"
        description="CRL、OCSP 与内部 CA CRL 发布地址。"
        actions={(
          <Button icon={<ReloadOutlined />} onClick={() => loadSettings()} loading={refreshing}>
            刷新
          </Button>
        )}
      />
      <CertificateValidationPanel settings={settings} />
    </PageShell>
  );
};

export default CertificateValidationPage;
