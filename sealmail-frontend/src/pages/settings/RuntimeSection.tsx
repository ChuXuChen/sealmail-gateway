import React from 'react';
import { Card, Descriptions, Space, Tag } from 'antd';
import type { SystemSettings } from '../../types';
import { getSettingsSummary } from './settingsUtils';

interface RuntimeSectionProps {
  settings: SystemSettings | null;
}

const RuntimeSection: React.FC<RuntimeSectionProps> = ({ settings }) => {
  const { activeProfiles } = getSettingsSummary(settings);

  return (
    <Card title="运行状态" styles={{ body: { paddingTop: 12 } }}>
      <Descriptions column={1} bordered className="settings-descriptions">
        <Descriptions.Item label="应用名称">{settings?.runtime.applicationName}</Descriptions.Item>
        <Descriptions.Item label="运行 Profile">
          <Space size={4} wrap>
            {activeProfiles.map((profile) => (
              <Tag key={profile} className="settings-tag">
                {profile}
              </Tag>
            ))}
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="配置来源">{settings?.runtime.configSource}</Descriptions.Item>
        <Descriptions.Item label="配置修改">
          <Tag color={settings?.runtime.onlineEditingSupported ? 'success' : 'warning'} className="settings-tag">
            {settings?.runtime.onlineEditingSupported ? '支持在线保存' : '改配置文件后重启'}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="快照时间">
          {settings?.runtime.generatedAt ? new Date(settings.runtime.generatedAt).toLocaleString() : '-'}
        </Descriptions.Item>
      </Descriptions>
    </Card>
  );
};

export default RuntimeSection;
