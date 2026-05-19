import React from 'react';
import {
  Button,
  Space,
} from 'antd';
import {
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import {
  PageHeader,
  PageShell,
  SectionPanel,
  StatusSummary,
} from '../components/Page';
import {
  DlpEdmWorkflow,
  DlpFingerprintWorkflow,
  DlpRuleModal,
  DlpTestPanel,
} from './dlp-patterns/DlpPatternForms';
import {
  DlpRuleTable,
} from './dlp-patterns/DlpPatternTables';
import { useDlpPatterns } from './dlp-patterns/useDlpPatterns';

const DlpPatterns: React.FC = () => {
  const dlp = useDlpPatterns();

  return (
    <PageShell className="dlp-patterns-page">
      <PageHeader
        title="DLP 规则资产中心"
        description="先维护可复用资产，再把规则绑定到策略；EDM、文档指纹和单次测试都在同一工作台完成。"
        actions={(
          <Space wrap>
            <Button icon={<ReloadOutlined />} loading={dlp.loading} onClick={dlp.loadData}>刷新</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => dlp.showCreate()}>添加规则</Button>
          </Space>
        )}
      />

      <StatusSummary
        items={[
          { key: 'rules', label: '规则总数', value: dlp.data.length, description: `${dlp.enabledRuleCount} 条启用`, tone: 'info' },
          { key: 'edm', label: 'EDM 数据集', value: dlp.edmDatasets.length, description: `${dlp.edmValueCount} 个哈希值`, tone: dlp.edmRuleCount > 0 ? 'success' : 'default' },
          { key: 'fingerprints', label: '文档指纹库', value: dlp.fingerprintLibraries.length, description: `${dlp.fingerprintChunkCount} 个片段`, tone: dlp.fingerprintRuleCount > 0 ? 'success' : 'default' },
          {
            key: 'test',
            label: '最近测试',
            value: dlp.testResult ? `${dlp.testResult.matchCount}` : '-',
            description: dlp.testResult ? `动作 ${dlp.testResult.action}，耗时 ${dlp.testResult.scanDurationMs}ms` : '尚未运行',
            tone: !dlp.testResult ? 'default' : dlp.testResult.matchCount > 0 ? 'warning' : 'success',
          },
        ]}
      />

      <SectionPanel
        title="规则清单"
        description="规则只定义识别方式和默认动作；实际生效范围在策略集中编排。"
      >
        <DlpRuleTable
          data={dlp.data}
          loading={dlp.loading}
          onEdit={dlp.showEdit}
          onRemove={dlp.remove}
        />
      </SectionPanel>

      <SectionPanel
        title="EDM 精确匹配流程"
        description="创建数据集后导入敏感值，最后在规则中选择该数据集。"
      >
        <DlpEdmWorkflow dlp={dlp} />
      </SectionPanel>

      <SectionPanel
        title="文档指纹库"
        description="导入可提取文本，为合同、标书、方案等长文档生成片段指纹。"
      >
        <DlpFingerprintWorkflow dlp={dlp} />
      </SectionPanel>

      <SectionPanel
        title="单次测试"
        description="用当前规则和策略扫描一封出站邮件，结果按输入、命中、动作和证据拆开展示。"
      >
        <DlpTestPanel dlp={dlp} />
      </SectionPanel>

      <DlpRuleModal dlp={dlp} />
    </PageShell>
  );
};

export default DlpPatterns;
