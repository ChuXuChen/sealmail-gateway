import React from 'react';
import { Button, Spin } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { PageHeader, PageShell, SectionPanel, StatusSummary } from '../../components/Page';
import CryptoCapabilityTags from '../settings/CryptoCapabilityTags';
import { useSettingsSnapshot } from '../settings/useSettingsSnapshot';

const CryptoCapabilitiesPage: React.FC = () => {
  const { loadSettings, loading, refreshing, settings } = useSettingsSnapshot();
  const capabilities = settings?.cryptoCapabilities || [];
  const totalAlgorithms = capabilities.reduce((sum, capability) => sum + capability.algorithms.length, 0);
  const gmCount = capabilities.filter((capability) => {
    const value = `${capability.category} ${capability.algorithms.join(' ')}`.toUpperCase();
    return value.includes('GM') || value.includes('SM2') || value.includes('SM3') || value.includes('SM4');
  }).length;

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
        description="当前运行实例暴露的 S/MIME 套件、签名、密钥交换和哈希能力。"
        actions={(
          <Button icon={<ReloadOutlined />} onClick={() => loadSettings()} loading={refreshing}>
            刷新
          </Button>
        )}
      />
      <StatusSummary
        items={[
          { key: 'groups', label: '能力分组', value: capabilities.length, description: '运行配置返回' },
          { key: 'algorithms', label: '算法项', value: totalAlgorithms, description: '可用算法总数', tone: 'info' },
          { key: 'standard', label: '标准默认', value: settings?.smimeSuitePolicy?.defaultStandardSuite || '-', description: 'S/MIME STANDARD' },
          { key: 'gm', label: '国密默认', value: settings?.smimeSuitePolicy?.defaultGmSuite || '-', description: `${gmCount} 个国密相关分组`, tone: gmCount > 0 ? 'danger' : 'default' },
        ]}
      />
      <SectionPanel
        title="能力清单"
        description="按能力域展示用途、算法列表和数量；具体默认套件由 S/MIME 套件策略决定。"
      >
        <CryptoCapabilityTags capabilities={capabilities} />
      </SectionPanel>
    </PageShell>
  );
};

export default CryptoCapabilitiesPage;
