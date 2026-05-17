import React, { useState } from 'react';
import { Button, Col, Form, Row, Spin } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { PageHeader, PageShell } from '../components/Page';
import MailSection from './settings/MailSection';
import RuntimeSection from './settings/RuntimeSection';
import SecuritySection from './settings/SecuritySection';
import SettingsNavigation from './settings/SettingsNavigation';
import SettingsSummary from './settings/SettingsSummary';
import { ProbeModal, TestMailModal } from './settings/SettingsModals';
import ToolsSection from './settings/ToolsSection';
import type {
  GmEdgePolicyFormValues,
  QuarantinePolicyFormValues,
  RelayPolicyFormValues,
  SectionKey,
  TestMailValues,
} from './settings/settingsUtils';
import { useSettings } from './settings/useSettings';

const Settings: React.FC = () => {
  const [activeSection, setActiveSection] = useState<SectionKey>('runtime');
  const [probeOpen, setProbeOpen] = useState(false);
  const [testOpen, setTestOpen] = useState(false);
  const [testForm] = Form.useForm<TestMailValues>();
  const [relayForm] = Form.useForm<RelayPolicyFormValues>();
  const [quarantineForm] = Form.useForm<QuarantinePolicyFormValues>();
  const [gmEdgeForm] = Form.useForm<GmEdgePolicyFormValues>();
  const {
    gmEdgePolicy,
    handleGmEdgePolicySave,
    handleProbe,
    handleQuarantinePolicySave,
    handleRelayPolicySave,
    handleSendTest,
    loadSettings,
    loading,
    probeLoading,
    probeResult,
    quarantinePolicy,
    refreshing,
    relayPolicy,
    settings,
    testLoading,
  } = useSettings({
    gmEdgeForm,
    quarantineForm,
    relayForm,
    testForm,
  });

  if (loading) {
    return (
      <PageShell>
        <div className="page-loading">
          <Spin size="large" />
        </div>
      </PageShell>
    );
  }

  const renderActiveSection = () => {
    switch (activeSection) {
      case 'runtime':
        return <RuntimeSection settings={settings} />;
      case 'mail':
        return (
          <MailSection
            quarantineForm={quarantineForm}
            quarantinePolicy={quarantinePolicy}
            gmEdgeForm={gmEdgeForm}
            gmEdgePolicy={gmEdgePolicy}
            relayForm={relayForm}
            relayPolicy={relayPolicy}
            settings={settings}
            onQuarantinePolicySave={handleQuarantinePolicySave}
            onGmEdgePolicySave={handleGmEdgePolicySave}
            onRelayPolicySave={handleRelayPolicySave}
          />
        );
      case 'security':
        return <SecuritySection settings={settings} />;
      case 'tools':
        return (
          <ToolsSection
            onOpenProbe={() => setProbeOpen(true)}
            onOpenTest={() => setTestOpen(true)}
          />
        );
      default:
        return <RuntimeSection settings={settings} />;
    }
  };

  return (
    <PageShell>
      <PageHeader
        title="系统设置"
        description="当前运行配置与调试入口。"
        actions={(
          <Button icon={<ReloadOutlined />} onClick={() => loadSettings()} loading={refreshing}>
            刷新
          </Button>
        )}
      />

      <SettingsSummary settings={settings} />

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={5}>
          <SettingsNavigation activeSection={activeSection} onChange={setActiveSection} />
        </Col>
        <Col xs={24} lg={19}>
          {renderActiveSection()}
        </Col>
      </Row>

      <ProbeModal
        loading={probeLoading}
        open={probeOpen}
        result={probeResult}
        onCancel={() => setProbeOpen(false)}
        onProbe={handleProbe}
      />
      <TestMailModal
        form={testForm}
        loading={testLoading}
        open={testOpen}
        onCancel={() => setTestOpen(false)}
        onFinish={async (values) => {
          const ok = await handleSendTest(values);
          if (ok) {
            setTestOpen(false);
          }
        }}
      />
    </PageShell>
  );
};

export default Settings;
