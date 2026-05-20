import React from 'react';
import {
  Alert,
  Button,
  Checkbox,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
} from 'antd';
import {
  AdvancedSection,
  DlpActionTag,
  FieldHint,
  SectionPanel,
  StatusSummary,
} from '../../components/Page';
import { DlpEvidenceTable } from '../dlp-patterns/DlpPatternTables';
import type { useDlpSelection } from './useDlpSelection';

const { TextArea } = Input;

type DlpSelectionViewModel = ReturnType<typeof useDlpSelection>;

interface DlpSelectionFormsProps {
  dlp: DlpSelectionViewModel;
}

export const DlpRuleGroupModal: React.FC<DlpSelectionFormsProps> = ({ dlp }) => (
  <Modal title={dlp.editingGroup ? '编辑规则组' : '添加规则组'} open={dlp.groupOpen} onCancel={dlp.closeGroup} footer={null} width={680}>
    <Form form={dlp.groupForm} layout="vertical" onFinish={dlp.saveGroup}>
      <SectionPanel compact title="规则组信息" description="把同类规则组合成策略构件，便于多个策略复用。">
        <div className="split-grid">
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}><Input /></Form.Item>
          <Form.Item name="description" label="说明"><Input /></Form.Item>
        </div>
        <Form.Item name="ruleIds" label="规则">
          <Select mode="multiple" options={dlp.ruleOptions} placeholder="选择规则" />
        </Form.Item>
        <div className="split-grid">
          <Form.Item name="priority" label="优先级"><InputNumber min={0} className="full-width" /></Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
        </div>
      </SectionPanel>
      <Form.Item className="form-actions dlp-modal-actions"><Space><Button onClick={dlp.closeGroup}>取消</Button><Button type="primary" htmlType="submit">保存</Button></Space></Form.Item>
    </Form>
  </Modal>
);

export const DlpPolicyModal: React.FC<DlpSelectionFormsProps> = ({ dlp }) => (
  <Modal title={dlp.editingPolicy ? '编辑策略' : '添加策略'} open={dlp.policyOpen} onCancel={dlp.closePolicy} footer={null} width={860}>
    <Form form={dlp.policyForm} layout="vertical" onFinish={dlp.savePolicy}>
      <SectionPanel compact title="基础信息" description="策略按优先级匹配邮件，优先级越小越靠前。">
        <div className="split-grid">
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}><Input /></Form.Item>
          <Form.Item name="description" label="说明"><Input /></Form.Item>
          <Form.Item name="priority" label="优先级"><InputNumber min={0} className="full-width" /></Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
        </div>
      </SectionPanel>

      <SectionPanel compact title="适用范围" description="为空表示不限制该条件。可以先按方向和域名收敛，再用地址模式处理例外。">
        <div className="split-grid">
          <Form.Item name="direction" label="方向"><Select allowClear options={[{ value: 'OUTBOUND', label: '出站' }, { value: 'INBOUND', label: '入站' }]} /></Form.Item>
          <Form.Item name="senderDomains" label="发件域"><Select mode="tags" tokenSeparators={[',']} placeholder="example.com" /></Form.Item>
          <Form.Item name="recipientDomains" label="收件域"><Select mode="tags" tokenSeparators={[',']} placeholder="example.net" /></Form.Item>
        </div>
        <FieldHint>域名只写主域名；更细的邮箱匹配放到高级条件。</FieldHint>
      </SectionPanel>

      <SectionPanel compact title="规则组" description="至少选择一个规则组。策略运行时会按规则组和规则自身优先级执行。">
        <Form.Item name="ruleGroupIds" label="规则组" rules={[{ required: true, message: '请选择规则组' }]}>
          <Select mode="multiple" options={dlp.groupOptions} placeholder="选择规则组" />
        </Form.Item>
      </SectionPanel>

      <SectionPanel compact title="执行方式" description="监控模式只记录命中；执行模式会使用规则动作进行告警、强制加密、隔离或阻断。">
        <div className="split-grid">
          <Form.Item name="mode" label="模式" rules={[{ required: true }]}>
            <Select options={[{ value: 'ENFORCE', label: '执行' }, { value: 'MONITOR', label: '监控' }]} />
          </Form.Item>
          <Form.Item name="attachmentRequired" valuePropName="checked">
            <Checkbox>仅含附件时生效</Checkbox>
          </Form.Item>
        </div>
      </SectionPanel>

      <AdvancedSection title="高级条件" description="用于匹配完整邮箱地址或通配模式，例如 *@example.com 或 security@*。">
        <Form.Item name="senderAddressPatterns" label="发件地址模式"><Select mode="tags" tokenSeparators={[',']} placeholder="*@example.com" /></Form.Item>
        <Form.Item name="recipientAddressPatterns" label="收件地址模式"><Select mode="tags" tokenSeparators={[',']} placeholder="security@*" /></Form.Item>
      </AdvancedSection>

      <Form.Item className="form-actions dlp-modal-actions"><Space><Button onClick={dlp.closePolicy}>取消</Button><Button type="primary" htmlType="submit">保存</Button></Space></Form.Item>
    </Form>
  </Modal>
);

export const DlpLegacySelectionModal: React.FC<DlpSelectionFormsProps> = ({ dlp }) => (
  <Modal title={dlp.editingSelection ? '编辑兼容范围' : '添加兼容范围'} open={dlp.legacyOpen} onCancel={dlp.closeLegacy} footer={null} width={640}>
    <Form form={dlp.legacyForm} layout="vertical" onFinish={dlp.saveLegacy}>
      <Alert className="legacy-form-alert" message="旧版兼容范围只用于历史配置迁移，新规则请使用策略列表。" showIcon type="warning" />
      <Form.Item name="scopeType" label="范围" rules={[{ required: true }]}>
        <Select onChange={dlp.setScopeType} options={[{ value: 'GLOBAL', label: '全局' }, { value: 'SENDER_DOMAIN', label: '发件域' }, { value: 'RECIPIENT_DOMAIN', label: '收件域' }]} />
      </Form.Item>
      {dlp.scopeType !== 'GLOBAL' && <Form.Item name="scopeValue" label="域名" rules={[{ required: true, message: '请输入域名' }]}><Input placeholder="example.com" /></Form.Item>}
      <Form.Item name="patternMode" label="规则模式"><Select onChange={dlp.setPatternMode} options={[{ value: 'ALL', label: '全部规则' }, { value: 'SELECTED', label: '仅选择规则' }]} /></Form.Item>
      {dlp.patternMode === 'SELECTED' && <Form.Item name="patternIds" label="启用规则"><Select mode="multiple" options={dlp.ruleOptions} /></Form.Item>}
      <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
      <Form.Item className="form-actions"><Space><Button onClick={dlp.closeLegacy}>取消</Button><Button type="primary" htmlType="submit">保存</Button></Space></Form.Item>
    </Form>
  </Modal>
);

export const DlpPolicySimulationPanel: React.FC<DlpSelectionFormsProps> = ({ dlp }) => (
  <div className="split-grid">
    <Form
      form={dlp.simulationForm}
      layout="vertical"
      onFinish={dlp.simulatePolicy}
      initialValues={{ direction: 'OUTBOUND' }}
    >
      <Form.Item name="policyId" label="策略" rules={[{ required: true, message: '请选择策略' }]}>
        <Select options={dlp.policyOptions} placeholder="选择策略" />
      </Form.Item>
      <div className="split-grid">
        <Form.Item name="direction" label="方向" rules={[{ required: true }]}>
          <Select options={[{ value: 'OUTBOUND', label: '出站' }, { value: 'INBOUND', label: '入站' }]} />
        </Form.Item>
        <Form.Item name="sender" label="发件人">
          <Input placeholder="sender@example.com" />
        </Form.Item>
      </div>
      <Form.Item name="recipients" label="收件人">
        <Input placeholder="a@example.com,b@example.net" />
      </Form.Item>
      <Form.Item name="subject" label="主题">
        <Input />
      </Form.Item>
      <Form.Item name="body" label="正文">
        <TextArea rows={7} />
      </Form.Item>
      <Form.Item className="form-actions">
        <Space>
          <Button onClick={dlp.clearSimulation}>清空</Button>
          <Button type="primary" htmlType="submit" loading={dlp.simulationRunning} disabled={dlp.policies.length === 0}>运行仿真</Button>
        </Space>
      </Form.Item>
    </Form>

    <div className="flow-stack">
      {dlp.simulationResult ? (
        <>
          <StatusSummary
            items={[
              { key: 'matches', label: '命中', value: dlp.simulationResult.matchCount, description: '证据条数', tone: dlp.simulationResult.matchCount > 0 ? 'warning' : 'success' },
              { key: 'severity', label: '最高级别', value: dlp.simulationResult.maxSeverity, description: '规则严重度' },
              { key: 'action', label: '执行动作', value: <DlpActionTag action={dlp.simulationResult.action} />, description: dlp.simulationResult.monitorMode ? '监控模式' : '执行模式', tone: dlp.simulationResult.matchCount > 0 ? 'danger' : 'success' },
              { key: 'duration', label: '扫描耗时', value: `${dlp.simulationResult.scanDurationMs}`, description: '毫秒' },
            ]}
          />
          {dlp.simulationResult.warnings.length > 0 ? (
            <Alert type="warning" showIcon message="仿真警告" description={dlp.simulationResult.warnings.join('；')} />
          ) : null}
          <DlpEvidenceTable data={dlp.simulationResult.evidence} />
        </>
      ) : (
        <Alert message="选择策略并输入样本邮件后运行仿真，结果会显示策略命中、执行动作和脱敏证据。" showIcon type="info" />
      )}
    </div>
  </div>
);

export const DlpSelectionModals: React.FC<DlpSelectionFormsProps> = ({ dlp }) => (
  <>
    <DlpRuleGroupModal dlp={dlp} />
    <DlpPolicyModal dlp={dlp} />
    <DlpLegacySelectionModal dlp={dlp} />
  </>
);
