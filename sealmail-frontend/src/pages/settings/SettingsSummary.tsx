import React from 'react';
import { Card, Col, Row } from 'antd';
import type { SystemSettings } from '../../types';
import { formatBytes, getSettingsSummary } from './settingsUtils';

interface SettingsSummaryProps {
  settings: SystemSettings | null;
}

const SettingsSummary: React.FC<SettingsSummaryProps> = ({ settings }) => {
  const { deliveryMode, smtpEndpoint } = getSettingsSummary(settings);

  return (
    <Row gutter={[16, 16]}>
      <Col xs={24} md={12} xl={6}>
        <Card className="settings-summary-card">
          <span className="settings-summary-label">收件地址</span>
          <div className="settings-summary-value">{smtpEndpoint}</div>
        </Card>
      </Col>
      <Col xs={24} md={12} xl={6}>
        <Card className="settings-summary-card">
          <span className="settings-summary-label">投递目标</span>
          <div className="settings-summary-value">{deliveryMode}</div>
        </Card>
      </Col>
      <Col xs={24} md={12} xl={6}>
        <Card className="settings-summary-card">
          <span className="settings-summary-label">单封邮件上限</span>
          <div className="settings-summary-value">{formatBytes(settings?.smtpServer.maxMessageSizeBytes || 0)}</div>
        </Card>
      </Col>
      <Col xs={24} md={12} xl={6}>
        <Card className="settings-summary-card">
          <span className="settings-summary-label">国密 Edge</span>
          <div className="settings-summary-value">{settings?.gmEdge.enabled ? '启用' : '关闭'}</div>
        </Card>
      </Col>
    </Row>
  );
};

export default SettingsSummary;
