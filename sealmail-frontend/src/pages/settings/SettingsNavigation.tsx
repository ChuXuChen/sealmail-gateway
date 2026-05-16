import React from 'react';
import { Card, Space, Button } from 'antd';
import {
  CloudServerOutlined,
  MailOutlined,
  SafetyCertificateOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import type { SectionKey } from './settingsUtils';

interface SettingsNavigationProps {
  activeSection: SectionKey;
  onChange: (section: SectionKey) => void;
}

const sections: { key: SectionKey; label: string; icon: React.ReactNode }[] = [
  { key: 'runtime', label: '运行状态', icon: <CloudServerOutlined /> },
  { key: 'mail', label: '邮件链路', icon: <MailOutlined /> },
  { key: 'security', label: '安全能力', icon: <SafetyCertificateOutlined /> },
  { key: 'tools', label: '运维工具', icon: <ToolOutlined /> },
];

const SettingsNavigation: React.FC<SettingsNavigationProps> = ({ activeSection, onChange }) => (
  <Card title="设置分类" styles={{ body: { padding: 12 } }}>
    <Space direction="vertical" size={8} className="full-width">
      {sections.map((section) => (
        <Button
          key={section.key}
          type="text"
          icon={section.icon}
          className={`settings-nav-button${activeSection === section.key ? ' is-active' : ''}`}
          onClick={() => onChange(section.key)}
        >
          {section.label}
        </Button>
      ))}
    </Space>
  </Card>
);

export default SettingsNavigation;
