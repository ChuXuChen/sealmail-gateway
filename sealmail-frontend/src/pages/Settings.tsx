import React from 'react';
import { Button, Col, Row, Space, Spin } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { PageHeader, PageShell } from '../components/Page';
import SettingsSummary from './settings/SettingsSummary';
import {
  CertificateValidationPanel,
  CryptoCapabilitiesPanel,
  DeliveryChainPanel,
  GmEdgePolicySummaryPanel,
  GmTlsEdgePanel,
  QuarantinePolicySummaryPanel,
  RelayPolicySummaryPanel,
  RuntimeStatusPanel,
  SmtpEntryPanel,
} from './settings/SettingsReadOnlyPanels';
import { useSettingsSnapshot } from './settings/useSettingsSnapshot';

const Settings: React.FC = () => {
  const {
    gmEdgePolicy,
    loadSettings,
    loading,
    quarantinePolicy,
    refreshing,
    relayPolicy,
    settings,
  } = useSettingsSnapshot();

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
        title="配置总览"
        description="当前运行配置、邮件链路、策略摘要与证书信任状态快照。"
        actions={(
          <Button icon={<ReloadOutlined />} onClick={() => loadSettings()} loading={refreshing}>
            刷新
          </Button>
        )}
      />

      <Space direction="vertical" size={16} className="full-width">
        <SettingsSummary settings={settings} />

        <Row gutter={[16, 16]}>
          <Col xs={24} xl={12}>
            <RuntimeStatusPanel settings={settings} />
          </Col>
          <Col xs={24} xl={12}>
            <SmtpEntryPanel settings={settings} />
          </Col>
          <Col xs={24} xl={12}>
            <DeliveryChainPanel settings={settings} />
          </Col>
          <Col xs={24} xl={12}>
            <RelayPolicySummaryPanel relayPolicy={relayPolicy} settings={settings} />
          </Col>
          <Col xs={24} xl={12}>
            <QuarantinePolicySummaryPanel quarantinePolicy={quarantinePolicy} settings={settings} />
          </Col>
          <Col xs={24} xl={12}>
            <GmEdgePolicySummaryPanel gmEdgePolicy={gmEdgePolicy} settings={settings} />
          </Col>
          <Col xs={24} xl={12}>
            <CertificateValidationPanel settings={settings} />
          </Col>
          <Col xs={24} xl={12}>
            <GmTlsEdgePanel settings={settings} />
          </Col>
          <Col xs={24}>
            <CryptoCapabilitiesPanel settings={settings} />
          </Col>
        </Row>
      </Space>
    </PageShell>
  );
};

export default Settings;
