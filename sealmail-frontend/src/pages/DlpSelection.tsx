import React from 'react';
import {
  Alert,
  Button,
  Space,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import {
  AdvancedSection,
  PageHeader,
  PageShell,
  SectionPanel,
  StatusSummary,
} from '../components/Page';
import { DlpSelectionModals } from './dlp-selection/DlpSelectionForms';
import {
  DlpLegacySelectionTable,
  DlpPolicyTable,
  DlpRuleGroupTable,
} from './dlp-selection/DlpSelectionTables';
import { useDlpSelection } from './dlp-selection/useDlpSelection';

const DlpSelection: React.FC = () => {
  const dlp = useDlpSelection();

  return (
    <PageShell className="dlp-selection-page">
      <PageHeader
        title="DLP 策略集"
        description="从策略视角配置适用范围、规则组、执行方式和高级条件；旧版范围保留为历史兼容入口。"
        actions={(
          <Space wrap>
            <Button icon={<ReloadOutlined />} loading={dlp.loading} onClick={dlp.loadData}>刷新</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={dlp.showCreatePolicy}>添加策略</Button>
          </Space>
        )}
      />

      <StatusSummary
        items={[
          { key: 'policies', label: '策略总数', value: dlp.policies.length, description: `${dlp.enabledPolicyCount} 条启用`, tone: 'info' },
          { key: 'enforce', label: '执行模式', value: dlp.enforcePolicyCount, description: `${dlp.policies.length - dlp.enforcePolicyCount} 条监控` },
          { key: 'groups', label: '规则组', value: dlp.groups.length, description: `${dlp.enabledGroupCount} 个启用`, tone: dlp.groups.length > 0 ? 'success' : 'default' },
          { key: 'legacy', label: '旧版兼容', value: dlp.selections.length, description: dlp.unboundGroupCount > 0 ? `${dlp.unboundGroupCount} 个规则组未绑定` : '无未绑定规则组', tone: dlp.unboundGroupCount > 0 ? 'warning' : 'success' },
        ]}
      />

      <SectionPanel
        title="策略列表"
        description="策略负责决定什么邮件进入哪些规则组，以及命中后以监控还是执行模式处理。"
      >
        <DlpPolicyTable
          data={dlp.policies}
          groupById={dlp.groupById}
          loading={dlp.loading}
          onEdit={dlp.showEditPolicy}
          onRemove={dlp.removePolicy}
        />
      </SectionPanel>

      <SectionPanel
        title="规则组构件"
        description="规则组是策略引用的构件，建议按业务场景组织，不直接表达生效范围。"
        extra={<Button icon={<PlusOutlined />} onClick={dlp.showCreateGroup}>添加规则组</Button>}
      >
        <DlpRuleGroupTable
          data={dlp.groups}
          loading={dlp.loading}
          onEdit={dlp.showEditGroup}
          onRemove={dlp.removeGroup}
        />
      </SectionPanel>

      <AdvancedSection title="旧版兼容范围" description="仅用于历史兼容。新配置应优先使用上方策略列表和规则组。" className="legacy-compat-section">
        <div className="flow-stack">
          <Alert message="这些范围仍会走原有接口保存，便于迁移旧配置；不要用它替代新策略。" showIcon type="warning" />
          <Space><Button icon={<PlusOutlined />} onClick={dlp.showCreateLegacy}>添加范围</Button></Space>
          <DlpLegacySelectionTable
            data={dlp.selections}
            loading={dlp.loading}
            onEdit={dlp.showEditLegacy}
            onRemove={dlp.removeLegacy}
          />
        </div>
      </AdvancedSection>

      <DlpSelectionModals dlp={dlp} />
    </PageShell>
  );
};

export default DlpSelection;
