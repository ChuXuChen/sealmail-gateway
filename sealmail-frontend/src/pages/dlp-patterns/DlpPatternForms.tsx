import React from 'react';
import {
  Alert,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Switch,
  Tabs,
} from 'antd';
import {
  AdvancedSection,
  DlpActionTag,
  FieldHint,
  SectionPanel,
  StatusSummary,
  TaskSteps,
} from '../../components/Page';
import {
  DlpEdmDatasetTable,
  DlpEvidenceTable,
  DlpFingerprintLibraryTable,
} from './DlpPatternTables';
import {
  actionOptions,
  builtinOptions,
  contentKindOptions,
  maskingOptions,
  typeOptions,
} from './dlpPatternUtils';
import type { useDlpPatterns } from './useDlpPatterns';

const { TextArea } = Input;

type DlpPatternsViewModel = ReturnType<typeof useDlpPatterns>;

interface DlpPatternFormProps {
  dlp: DlpPatternsViewModel;
}

export const DlpEdmWorkflow: React.FC<DlpPatternFormProps> = ({ dlp }) => (
  <div className="flow-stack">
    <TaskSteps
      items={[
        {
          key: 'create',
          status: dlp.edmDatasets.length > 0 ? 'done' : 'current',
          title: '创建数据集',
          description: '按数据域拆分，例如客户号、证件号或银行账号。',
        },
        {
          key: 'import',
          status: dlp.edmValueCount > 0 ? 'done' : dlp.edmDatasets.length > 0 ? 'current' : 'pending',
          title: '导入敏感值',
          description: '粘贴多行数据，前端预览条数、空行和重复值。',
        },
        {
          key: 'bind',
          status: dlp.edmRuleCount > 0 ? 'done' : dlp.edmValueCount > 0 ? 'current' : 'pending',
          title: '绑定到规则',
          description: '创建 EDM 类型规则，策略命中时只暴露脱敏证据。',
        },
      ]}
    />

    <Tabs
      className="config-tabs"
      items={[
        {
          key: 'datasets',
          label: `数据集 (${dlp.edmDatasets.length})`,
          children: (
            <DlpEdmDatasetTable
              data={dlp.edmDatasets}
              onRemove={dlp.removeEdmDataset}
            />
          ),
        },
        {
          key: 'create',
          label: '创建数据集',
          children: (
            <SectionPanel compact>
              <Form form={dlp.edmForm} layout="inline" onFinish={dlp.saveEdmDataset}>
                <Form.Item name="name" rules={[{ required: true, message: '请输入名称' }]}>
                  <Input placeholder="数据集名称" />
                </Form.Item>
                <Form.Item name="description">
                  <Input placeholder="说明" />
                </Form.Item>
                <Form.Item name="enabled" valuePropName="checked" initialValue>
                  <Switch checkedChildren="启用" unCheckedChildren="停用" />
                </Form.Item>
                <Button type="primary" htmlType="submit">创建数据集</Button>
              </Form>
            </SectionPanel>
          ),
        },
        {
          key: 'import',
          label: '导入敏感值',
          children: (
            <SectionPanel compact>
              <Form form={dlp.edmImportForm} layout="vertical" onFinish={dlp.importEdmValues}>
                <Form.Item name="datasetId" label="导入到" rules={[{ required: true, message: '请选择数据集' }]}>
                  <Select options={dlp.edmOptions} placeholder="选择数据集" />
                </Form.Item>
                <Form.Item name="text" label="精确匹配值">
                  <TextArea
                    rows={7}
                    onChange={(event) => dlp.setEdmImportText(event.target.value)}
                    placeholder="每行或逗号分隔一个敏感值；保存时只写入规范化哈希"
                  />
                </Form.Item>
                <StatusSummary
                  className="dlp-import-preview"
                  items={[
                    { key: 'total', label: '预览条数', value: dlp.edmImportStats.total, description: '非空值' },
                    { key: 'empty', label: '空行', value: dlp.edmImportStats.empty, description: '提交时忽略' },
                    { key: 'duplicates', label: '本次重复', value: dlp.edmImportStats.duplicates, description: '后端仍会去重', tone: dlp.edmImportStats.duplicates > 0 ? 'warning' : 'success' },
                  ]}
                />
                <Form.Item className="form-actions">
                  <Button type="primary" htmlType="submit" disabled={dlp.edmDatasets.length === 0}>导入哈希</Button>
                </Form.Item>
              </Form>
            </SectionPanel>
          ),
        },
        {
          key: 'bind',
          label: '绑定规则',
          children: (
            <Alert
              action={<Button type="primary" onClick={() => dlp.showCreate('EDM')}>创建 EDM 规则</Button>}
              message="把数据集绑定为 EDM 规则后，策略集才能引用规则组执行拦截、隔离或告警。"
              showIcon
              type={dlp.edmValueCount > 0 ? 'info' : 'warning'}
            />
          ),
        },
      ]}
    />
  </div>
);

export const DlpFingerprintWorkflow: React.FC<DlpPatternFormProps> = ({ dlp }) => (
  <div className="flow-stack">
    <DlpFingerprintLibraryTable
      data={dlp.fingerprintLibraries}
      onRemove={dlp.removeFingerprintLibrary}
    />
    <div className="split-grid">
      <SectionPanel compact title="创建指纹库">
        <Form form={dlp.fingerprintForm} layout="vertical" onFinish={dlp.saveFingerprintLibrary}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="指纹库名称" />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked" initialValue>
            <Switch />
          </Form.Item>
          <Form.Item className="form-actions">
            <Button type="primary" htmlType="submit">创建指纹库</Button>
          </Form.Item>
        </Form>
      </SectionPanel>
      <SectionPanel compact title="导入文档文本" extra={<Button onClick={() => dlp.showCreate('FINGERPRINT')}>创建指纹规则</Button>}>
        <Form form={dlp.fingerprintImportForm} layout="vertical" onFinish={dlp.importFingerprintDocument}>
          <Form.Item name="libraryId" label="导入到" rules={[{ required: true, message: '请选择指纹库' }]}>
            <Select options={dlp.fingerprintOptions} placeholder="选择指纹库" />
          </Form.Item>
          <Form.Item name="documentName" label="文档名称">
            <Input placeholder="合同-2026-客户A" />
          </Form.Item>
          <Form.Item name="text" label="文档文本" rules={[{ required: true, message: '请输入可提取文本' }]}>
            <TextArea rows={6} />
          </Form.Item>
          <Form.Item className="form-actions">
            <Button type="primary" htmlType="submit" disabled={dlp.fingerprintLibraries.length === 0}>生成指纹</Button>
          </Form.Item>
        </Form>
      </SectionPanel>
    </div>
  </div>
);

export const DlpTestPanel: React.FC<DlpPatternFormProps> = ({ dlp }) => (
  <div className="split-grid">
    <Form form={dlp.testForm} layout="vertical" onFinish={dlp.runTest}>
      <Form.Item name="subject" label="主题"><Input /></Form.Item>
      <Form.Item name="sender" label="发件人"><Input placeholder="sender@example.com" /></Form.Item>
      <Form.Item name="recipients" label="收件人"><Input placeholder="a@example.com,b@example.net" /></Form.Item>
      <Form.Item name="body" label="正文"><TextArea rows={7} /></Form.Item>
      <Form.Item className="form-actions">
        <Space>
          <Button onClick={dlp.clearTest}>清空</Button>
          <Button type="primary" htmlType="submit">运行测试</Button>
        </Space>
      </Form.Item>
    </Form>

    <div className="flow-stack">
      {dlp.testResult ? (
        <>
          <StatusSummary
            items={[
              { key: 'matches', label: '命中', value: dlp.testResult.matchCount, description: '证据条数', tone: dlp.testResult.matchCount > 0 ? 'warning' : 'success' },
              { key: 'severity', label: '最高级别', value: dlp.testResult.maxSeverity, description: '规则严重度' },
              { key: 'action', label: '执行动作', value: <DlpActionTag action={dlp.testResult.action} />, description: dlp.testResult.monitorMode ? '监控模式' : '执行模式', tone: dlp.testResult.matchCount > 0 ? 'danger' : 'success' },
              { key: 'duration', label: '扫描耗时', value: `${dlp.testResult.scanDurationMs}`, description: '毫秒' },
            ]}
          />
          {dlp.testResult.warnings.length > 0 ? (
            <Alert type="warning" showIcon message="扫描警告" description={dlp.testResult.warnings.join('；')} />
          ) : null}
          <DlpEvidenceTable data={dlp.testResult.evidence} />
        </>
      ) : (
        <Alert message="输入邮件内容后运行测试，结果会显示命中规则、执行动作和脱敏证据。" showIcon type="info" />
      )}
    </div>
  </div>
);

export const DlpRuleModal: React.FC<DlpPatternFormProps> = ({ dlp }) => (
  <Modal title={dlp.editing ? '编辑 DLP 规则' : '添加 DLP 规则'} open={dlp.modalOpen} onCancel={dlp.closeRuleModal} footer={null} width={820}>
    <Form form={dlp.form} layout="vertical" onFinish={dlp.save}>
      <SectionPanel compact title="基础信息" description="名称用于策略组选择和审计检索，优先级越小越靠前。">
        <div className="split-grid">
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}><Input /></Form.Item>
          <Form.Item name="description" label="说明"><Input /></Form.Item>
        </div>
        <div className="split-grid">
          <Form.Item name="type" label="类型" rules={[{ required: true }]}>
            <Select options={typeOptions} onChange={dlp.handleRuleTypeChange} />
          </Form.Item>
          <Form.Item name="action" label="动作" rules={[{ required: true }]}>
            <Select options={actionOptions} />
          </Form.Item>
          <Form.Item name="severity" label="严重级别" rules={[{ required: true }]}><InputNumber min={1} max={10} className="full-width" /></Form.Item>
          <Form.Item name="priority" label="优先级" rules={[{ required: true }]}><InputNumber min={0} className="full-width" /></Form.Item>
        </div>
      </SectionPanel>

      <SectionPanel compact title={dlp.currentRuleMeta.title} description={dlp.currentRuleMeta.help} className="dlp-rule-editor-section">
        <FieldHint>示例：{dlp.currentRuleMeta.example}</FieldHint>
        {dlp.ruleType === 'EDM' ? (
          <Form.Item name="pattern" label="EDM 数据集" rules={[{ required: true, message: '请选择 EDM 数据集' }]}>
            <Select options={dlp.edmOptions} placeholder="选择已导入哈希索引的数据集" />
          </Form.Item>
        ) : dlp.ruleType === 'FINGERPRINT' ? (
          <Form.Item name="pattern" label="文档指纹库" rules={[{ required: true, message: '请选择文档指纹库' }]}>
            <Select options={dlp.fingerprintOptions} placeholder="选择已导入片段指纹的文档库" />
          </Form.Item>
        ) : dlp.ruleType === 'BUILTIN' ? (
          <Form.Item name="builtinCode" label="内置规则" rules={[{ required: true, message: '请选择内置规则' }]}>
            <Select options={builtinOptions} />
          </Form.Item>
        ) : (
          <Form.Item name="pattern" label={dlp.ruleType === 'KEYWORD' ? '关键词' : '检测模式'} rules={[{ required: true, message: '请输入检测模式' }]}>
            <TextArea rows={4} />
          </Form.Item>
        )}
      </SectionPanel>

      <AdvancedSection title="内容范围与证据设置" description="默认扫描所有可提取内容；只有需要收窄范围时才配置这里。">
        <Form.Item name="contentKinds" label="内容范围">
          <Select mode="multiple" allowClear options={contentKindOptions} placeholder="为空表示扫描全部内容" />
        </Form.Item>
        <div className="split-grid">
          <Form.Item name="minMatchCount" label="最小命中"><InputNumber min={1} className="full-width" /></Form.Item>
          <Form.Item name="maxEvidenceCount" label="证据上限"><InputNumber min={1} max={100} className="full-width" /></Form.Item>
          <Form.Item name="maskingStrategy" label="脱敏策略"><Select options={maskingOptions} /></Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
        </div>
      </AdvancedSection>

      <Form.Item className="form-actions dlp-modal-actions">
        <Space><Button onClick={dlp.closeRuleModal}>取消</Button><Button type="primary" htmlType="submit">保存</Button></Space>
      </Form.Item>
    </Form>
  </Modal>
);
