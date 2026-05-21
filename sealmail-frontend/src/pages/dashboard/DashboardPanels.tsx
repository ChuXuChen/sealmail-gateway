import type React from 'react';
import { useState } from 'react';
import { Button, Descriptions, Empty, List, Progress, Skeleton, Space, Tag, Typography, message } from 'antd';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  FileSearchOutlined,
  InboxOutlined,
  SafetyCertificateOutlined,
  SecurityScanOutlined,
  SettingOutlined,
  StopOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { mailProcessingApi } from '../../api/client';
import DetailDrawer from '../../components/Page/DetailDrawer';
import CryptoCapabilityTags from '../settings/CryptoCapabilityTags';
import type { DashboardViewModel } from './dashboardModel';
import type {
  AttachmentSecurityFinding,
  AttachmentSecuritySnapshot,
  AuditLog,
  MailAuthMechanismStatus,
  MailProcessingRecord,
  SmimeOperationSnapshot,
  SystemSettings,
} from '../../types';
import { ROUTES } from '../../router/routes';

const { Text } = Typography;

interface DashboardPanelsProps {
  cryptoCapabilities?: SystemSettings['cryptoCapabilities'];
  recentProcessing: MailProcessingRecord[];
  viewModel: DashboardViewModel;
}

const stepLabel = (stepName: string) => ({
  routing: '路由',
  'mail-auth': '认证',
  decrypt: '解密',
  'verify-signature': '验签',
  dlp: 'DLP',
  sign: '签名',
  encrypt: '加密',
  'dkim-sign': 'DKIM',
  relay: '投递',
  quarantine: '隔离',
}[stepName] ?? stepName);

const dispositionColor = (disposition: string) => ({
  DELIVERED: 'success',
  PROCESSING: 'processing',
  QUARANTINED: 'warning',
  EXCEPTION: 'error',
  FAILED: 'error',
}[disposition] ?? 'default');

const dispositionLabel = (disposition: string) => ({
  DELIVERED: '已投递',
  PROCESSING: '处理中',
  QUARANTINED: '已隔离',
  EXCEPTION: '异常',
  FAILED: '失败',
}[disposition] ?? disposition);

const snapshotStatusColor = (status?: string) => ({
  PENDING: 'processing',
  PASS: 'success',
  FAIL: 'error',
  SKIPPED: 'default',
  DELIVERED: 'success',
  QUARANTINED: 'warning',
  EXCEPTION: 'error',
}[status ?? ''] ?? 'default');

const snapshotStatusLabel = (status?: string) => ({
  PENDING: '待处理',
  PASS: '通过',
  FAIL: '失败',
  SKIPPED: '跳过',
  DELIVERED: '已投递',
  QUARANTINED: '已隔离',
  EXCEPTION: '异常',
}[status ?? ''] ?? (status || '未记录'));

const valueOrDash = (value?: string | number | boolean | null) => {
  if (value === undefined || value === null || value === '') {
    return '未记录';
  }
  if (typeof value === 'boolean') {
    return value ? '是' : '否';
  }
  return String(value);
};

const mechanismText = (mechanism?: MailAuthMechanismStatus) => {
  if (!mechanism) {
    return '未记录';
  }
  return [
    mechanism.result,
    mechanism.domain,
    mechanism.identity,
  ].filter(Boolean).join(' / ') || '未记录';
};

const formatAuditTime = (value?: string) => {
  if (!value) {
    return '未记录';
  }
  return new Date(value).toLocaleString();
};

const SnapshotTag: React.FC<{ status?: string }> = ({ status }) => (
  <Tag color={snapshotStatusColor(status)}>{snapshotStatusLabel(status)}</Tag>
);

const DetailSection: React.FC<{ title: string; children: React.ReactNode }> = ({ title, children }) => (
  <section className="mail-processing-detail-section">
    <h3 className="mail-processing-detail-section__title">{title}</h3>
    {children}
  </section>
);

const SmimeOperation: React.FC<{
  label: string;
  operation?: SmimeOperationSnapshot;
}> = ({ label, operation }) => (
  <div className="mail-processing-operation">
    <div className="mail-processing-operation__head">
      <Text strong>{label}</Text>
      <SnapshotTag status={operation?.status} />
    </div>
    <Text className="mail-processing-operation__line">
      套件：{valueOrDash(operation?.algorithmSuite)}
    </Text>
    <Text className="mail-processing-operation__line">
      证书：{valueOrDash(operation?.certificateThumbprint)}
    </Text>
    {operation?.recipientThumbprints?.length ? (
      <Text className="mail-processing-operation__line">
        收件人证书：{operation.recipientThumbprints.join(', ')}
      </Text>
    ) : null}
    {operation?.failureReason ? (
      <Text className="mail-processing-operation__failure">
        {operation.failureReason}
      </Text>
    ) : null}
  </div>
);

const attachmentActionColor = (action?: string) => ({
  ALLOW: 'success',
  WARN: 'gold',
  QUARANTINE: 'warning',
  BLOCK: 'error',
}[action ?? ''] ?? 'default');

const attachmentActionLabel = (action?: string) => ({
  ALLOW: '通过',
  WARN: '告警',
  QUARANTINE: '隔离',
  BLOCK: '阻断',
}[action ?? ''] ?? valueOrDash(action));

const formatBytes = (value?: number) => {
  if (value === undefined || value === null) {
    return '未记录';
  }
  if (value < 1024) {
    return `${value} B`;
  }
  const units = ['KB', 'MB', 'GB', 'TB'];
  let size = value / 1024;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }
  return `${size.toFixed(size >= 10 ? 0 : 1)} ${units[unitIndex]}`;
};

const attachmentFindingLabel = (finding: AttachmentSecurityFinding) => {
  if (finding.fileName) {
    return finding.fileName;
  }
  if (finding.nestedPath?.length) {
    return finding.nestedPath.join('/');
  }
  return '未命名附件';
};

const AttachmentSecuritySection: React.FC<{ security?: AttachmentSecuritySnapshot }> = ({ security }) => {
  const findings = security?.findings ?? [];
  const warnings = security?.warnings ?? [];
  const visibleFindings = findings.slice(0, 5);

  return (
    <DetailSection title="附件安全">
      <Descriptions column={1} size="small">
        <Descriptions.Item label="状态">
          <SnapshotTag status={security?.status} />
        </Descriptions.Item>
        <Descriptions.Item label="最终动作">
          <Tag color={attachmentActionColor(security?.action)}>
            {attachmentActionLabel(security?.action)}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="最高严重度">
          {valueOrDash(security?.maxSeverity)}
        </Descriptions.Item>
        <Descriptions.Item label="附件数量">
          {valueOrDash(security?.attachmentCount)}
        </Descriptions.Item>
        <Descriptions.Item label="总大小">
          {formatBytes(security?.totalBytes)}
        </Descriptions.Item>
        <Descriptions.Item label="失败原因">
          {valueOrDash(security?.failureReason)}
        </Descriptions.Item>
      </Descriptions>

      {visibleFindings.length ? (
        <div className="mail-processing-attachment-findings">
          {visibleFindings.map((finding, index) => (
            <div className="mail-processing-attachment-finding" key={`${finding.code ?? 'finding'}-${index}`}>
              <div className="mail-processing-attachment-finding__head">
                <Space size={8} className="min-width-zero" wrap>
                  <Tag color={attachmentActionColor(finding.action)}>
                    {finding.code ?? 'UNKNOWN'}
                  </Tag>
                  <Text className="mail-processing-attachment-finding__name" ellipsis>
                    {attachmentFindingLabel(finding)}
                  </Text>
                </Space>
                <Text className="mail-processing-attachment-finding__severity">
                  {valueOrDash(finding.severity)}
                </Text>
              </div>
              <Text className="mail-processing-attachment-finding__detail">
                {valueOrDash(finding.message)}
              </Text>
              <Text className="mail-processing-attachment-finding__meta">
                {[
                  valueOrDash(finding.declaredMimeType),
                  valueOrDash(finding.detectedMimeType),
                  finding.extension ? `.${finding.extension}` : null,
                  finding.archive ? '压缩包' : null,
                  finding.encrypted ? '加密' : null,
                ].filter(Boolean).join(' · ')}
              </Text>
            </div>
          ))}
          {findings.length > visibleFindings.length ? (
            <Text className="mail-processing-attachment-findings__more">
              还有 {findings.length - visibleFindings.length} 条风险项未展开
            </Text>
          ) : null}
        </div>
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无附件风险项" />
      )}

      {warnings.length ? (
        <div className="mail-processing-attachment-warnings">
          <Text strong className="mail-processing-attachment-warnings__title">解析告警</Text>
          {warnings.map((warning, index) => (
            <Text className="mail-processing-attachment-warnings__item" key={`${warning}-${index}`}>
              {warning}
            </Text>
          ))}
        </div>
      ) : null}
    </DetailSection>
  );
};

const DashboardPanels: React.FC<DashboardPanelsProps> = ({
  cryptoCapabilities,
  recentProcessing,
  viewModel,
}) => {
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedRecord, setSelectedRecord] = useState<MailProcessingRecord | null>(null);
  const [detailRecord, setDetailRecord] = useState<MailProcessingRecord | null>(null);
  const [auditTrace, setAuditTrace] = useState<AuditLog[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);

  const queueItems = [
    {
      label: '待处理隔离',
      value: viewModel.pending,
      percent: viewModel.pendingRate,
      status: 'active' as const,
      tone: 'warning',
      icon: <ClockCircleOutlined />,
      copy: '需要人工判断放行或拒绝',
    },
    {
      label: '释放中',
      value: viewModel.releasing,
      percent: viewModel.releasingRate,
      status: 'normal' as const,
      tone: 'info',
      icon: <InboxOutlined />,
      copy: '正在恢复投递流程',
    },
    {
      label: '已放行',
      value: viewModel.released,
      percent: viewModel.releasedRate,
      status: 'success' as const,
      tone: 'success',
      icon: <CheckCircleOutlined />,
      copy: '已恢复投递',
    },
    {
      label: '人工拒绝',
      value: viewModel.rejected,
      percent: viewModel.rejectedRate,
      status: 'exception' as const,
      tone: 'danger',
      icon: <StopOutlined />,
      copy: '人工确认不放行',
    },
  ];

  const distribution = viewModel.distribution.slice(0, 7);
  const currentDetailRecord = detailRecord ?? selectedRecord;
  const snapshot = currentDetailRecord?.statusSnapshot;

  const openProcessingDetail = async (record: MailProcessingRecord) => {
    setSelectedRecord(record);
    setDetailRecord(null);
    setAuditTrace([]);
    setDetailOpen(true);
    setDetailLoading(true);
    try {
      const [detail, trace] = await Promise.all([
        mailProcessingApi.getById(record.processingId),
        mailProcessingApi.getAuditTrace(record.processingId, { page: 1, size: 20 }),
      ]);
      setDetailRecord(detail);
      setAuditTrace(trace.items);
    } catch {
      message.error('加载邮件处理详情失败');
    } finally {
      setDetailLoading(false);
    }
  };

  return (
    <section className="dashboard-workbench" aria-label="仪表盘工作台">
      <div className="dashboard-workbench__main">
        <section className="dashboard-panel dashboard-panel--queue" aria-label="处置队列">
          <div className="dashboard-panel__head">
            <div>
              <h2 className="dashboard-panel__title">处置队列</h2>
              <Text className="dashboard-panel__description">按当前状态拆解隔离邮件，优先处理待办压力。</Text>
            </div>
            <Button type="primary" size="small">
              <Link to={ROUTES.dispositionDlpQuarantine}>进入隔离队列</Link>
            </Button>
          </div>

          <div className={`dashboard-queue-callout dashboard-queue-callout--${viewModel.pending > 0 ? 'warning' : 'ok'}`}>
            <span className="dashboard-queue-callout__icon">
              {viewModel.pending > 0 ? <WarningOutlined /> : <SafetyCertificateOutlined />}
            </span>
            <div className="dashboard-queue-callout__body">
              <Text className="dashboard-queue-callout__title">
                {viewModel.pending > 0 ? `${viewModel.pending} 封邮件等待处置` : '当前无待处理隔离邮件'}
              </Text>
              <Text className="dashboard-queue-callout__copy">
                {viewModel.pending > 0
                  ? `队列完成率 ${viewModel.completionRate}%，建议先查看高风险原因。`
                  : `已处置 ${viewModel.handled} 封，队列完成率 ${viewModel.completionRate}%。`}
              </Text>
            </div>
          </div>

          <div className="dashboard-queue-grid">
            {queueItems.map((item) => (
              <div className={`dashboard-queue-card dashboard-queue-card--${item.tone}`} key={item.label}>
                <div className="dashboard-queue-card__head">
                  <span className="dashboard-queue-card__icon">{item.icon}</span>
                  <Text className="dashboard-queue-card__label">{item.label}</Text>
                </div>
                <Text className="dashboard-queue-card__value">{item.value}</Text>
                <Text className="dashboard-queue-card__copy">{item.copy}</Text>
                <Progress percent={item.percent} showInfo={false} status={item.status} />
              </div>
            ))}
          </div>
        </section>

        <section className="dashboard-panel" aria-label="邮件处理链路">
          <div className="dashboard-panel__head">
            <div>
              <h2 className="dashboard-panel__title">统一处理链</h2>
              <Text className="dashboard-panel__description">最近邮件在认证、证书、S/MIME、DLP、隔离和投递上的实际状态。</Text>
            </div>
            <Button size="small">
              <Link to={ROUTES.opsAuditLogs}>查看审计</Link>
            </Button>
          </div>

          {recentProcessing.length > 0 ? (
            <div className="dashboard-processing-list">
              {recentProcessing.map((record) => (
                <div
                  role="button"
                  tabIndex={0}
                  className="dashboard-processing-row"
                  key={record.processingId}
                  onClick={() => { void openProcessingDetail(record); }}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault();
                      void openProcessingDetail(record);
                    }
                  }}
                >
                  <div className="dashboard-processing-row__head">
                    <Space size={8} className="min-width-zero">
                      <Tag color={record.direction === 'OUTBOUND' ? 'blue' : 'cyan'}>
                        {record.direction === 'OUTBOUND' ? '出站' : '入站'}
                      </Tag>
                      <Text className="dashboard-processing-row__message" ellipsis>
                        {record.messageId}
                      </Text>
                    </Space>
                    <Tag color={dispositionColor(record.disposition)}>
                      {dispositionLabel(record.disposition)}
                    </Tag>
                  </div>
                  <Text className="dashboard-processing-row__meta" ellipsis>
                    {`${record.sender} -> ${record.recipients.join(', ')}`}
                  </Text>
                  <div className="dashboard-processing-row__steps">
                    {record.steps.slice(0, 8).map((step) => (
                      <Tag
                        color={!step.completed ? 'default' : step.success ? 'success' : 'error'}
                        key={step.id}
                      >
                        {stepLabel(step.stepName)}
                      </Tag>
                    ))}
                    {record.steps.length === 0 ? <Tag>未记录步骤</Tag> : null}
                  </div>
                  {record.failureReason ? (
                    <Text className="dashboard-processing-row__failure" ellipsis>
                      {record.failedStep ? `${stepLabel(record.failedStep)}：` : ''}{record.failureReason}
                    </Text>
                  ) : null}
                </div>
              ))}
            </div>
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无邮件处理状态" />
          )}
        </section>

        <section className="dashboard-panel" aria-label="风险原因">
          <div className="dashboard-panel__head">
            <div>
              <h2 className="dashboard-panel__title">风险原因</h2>
              <Text className="dashboard-panel__description">按命中原因排序，用于判断规则噪声和主要风险来源。</Text>
            </div>
            <Button size="small">
              <Link to={ROUTES.dispositionDlpEvents}>查看 DLP 事件</Link>
            </Button>
          </div>

          {distribution.length > 0 ? (
            <div className="dashboard-risk-list">
              {distribution.map((item, index) => (
                <div className="dashboard-risk-row" key={item.label}>
                  <div className="dashboard-risk-row__head">
                    <Space size={8} className="min-width-zero">
                      <span className="dashboard-risk-row__rank">{index + 1}</span>
                      <Tag color={item.tone === 'exception' ? 'error' : 'processing'}>
                        {item.tone === 'exception' ? '异常' : 'DLP'}
                      </Tag>
                      <Text className="dashboard-risk-row__label" ellipsis>{item.label}</Text>
                    </Space>
                    <Text className="dashboard-risk-row__count">{item.count}</Text>
                  </div>
                  <Progress
                    percent={item.percent}
                    showInfo={false}
                    status={item.tone === 'exception' ? 'exception' : 'normal'}
                  />
                </div>
              ))}
            </div>
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无异常或隔离数据" />
          )}
        </section>
      </div>

      <aside className="dashboard-workbench__side" aria-label="系统能力与入口">
        <section className="dashboard-panel" aria-label="运行健康">
          <div className="dashboard-panel__head">
            <div>
              <h2 className="dashboard-panel__title">运行健康</h2>
              <Text className="dashboard-panel__description">队列积压和自动阻断的综合视图。</Text>
            </div>
          </div>
          <div className="dashboard-health-score">
            <Progress
              type="circle"
              percent={viewModel.healthPercent}
              size={118}
              status={viewModel.pending > 0 ? 'active' : 'success'}
            />
            <div className="dashboard-health-score__meta">
              <Text className="dashboard-health-score__label">健康度</Text>
              <Text className="dashboard-health-score__copy">
                {viewModel.pending > 0 ? '存在待处理隔离邮件' : '未发现队列积压'}
              </Text>
            </div>
          </div>
        </section>

        <section className="dashboard-panel" aria-label="算法能力">
          <div className="dashboard-panel__head">
            <div>
              <h2 className="dashboard-panel__title">算法能力</h2>
              <Text className="dashboard-panel__description">S/MIME 兼容能力与国密能力。</Text>
            </div>
            <SecurityScanOutlined className="dashboard-panel__head-icon" />
          </div>
          <CryptoCapabilityTags capabilities={cryptoCapabilities} compact />
        </section>

        <section className="dashboard-panel" aria-label="快捷入口">
          <div className="dashboard-panel__head">
            <div>
              <h2 className="dashboard-panel__title">快捷入口</h2>
              <Text className="dashboard-panel__description">常用排障和策略调整入口。</Text>
            </div>
          </div>
          <div className="dashboard-shortcuts">
            <Link to={ROUTES.policiesDlpRules} className="dashboard-shortcut">
              <FileSearchOutlined />
              <span>DLP 规则库</span>
            </Link>
            <Link to={ROUTES.policiesDlpPolicies} className="dashboard-shortcut">
              <SettingOutlined />
              <span>DLP 策略集</span>
            </Link>
            <Link to={ROUTES.opsProtectedTestMail} className="dashboard-shortcut">
              <SafetyCertificateOutlined />
              <span>测试邮件</span>
            </Link>
          </div>
        </section>
      </aside>

      <DetailDrawer
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        title="邮件处理详情"
        width={760}
      >
        {detailLoading && !currentDetailRecord ? (
          <Skeleton active paragraph={{ rows: 8 }} />
        ) : currentDetailRecord ? (
          <div className="mail-processing-detail">
            <div className="mail-processing-detail__summary">
              <Space size={8} wrap>
                <Tag color={currentDetailRecord.direction === 'OUTBOUND' ? 'blue' : 'cyan'}>
                  {currentDetailRecord.direction === 'OUTBOUND' ? '出站' : '入站'}
                </Tag>
                <Tag color={dispositionColor(currentDetailRecord.disposition)}>
                  {dispositionLabel(currentDetailRecord.disposition)}
                </Tag>
              </Space>
              <Text className="mail-processing-detail__message">
                {currentDetailRecord.messageId}
              </Text>
              <Text className="mail-processing-detail__meta">
                {`${currentDetailRecord.sender} -> ${currentDetailRecord.recipients.join(', ')}`}
              </Text>
            </div>

            {detailLoading ? <Skeleton active paragraph={{ rows: 3 }} /> : null}

            <DetailSection title="认证">
              <Descriptions column={1} size="small">
                <Descriptions.Item label="状态">
                  <SnapshotTag status={snapshot?.mailAuth?.status} />
                </Descriptions.Item>
                <Descriptions.Item label="SPF">
                  {mechanismText(snapshot?.mailAuth?.spf)}
                </Descriptions.Item>
                <Descriptions.Item label="DKIM">
                  {mechanismText(snapshot?.mailAuth?.dkim)}
                </Descriptions.Item>
                <Descriptions.Item label="DMARC">
                  {mechanismText(snapshot?.mailAuth?.dmarc)}
                </Descriptions.Item>
                <Descriptions.Item label="失败动作">
                  {valueOrDash(snapshot?.mailAuth?.action)}
                </Descriptions.Item>
                <Descriptions.Item label="原因">
                  {valueOrDash(snapshot?.mailAuth?.detail ?? snapshot?.mailAuth?.reason)}
                </Descriptions.Item>
              </Descriptions>
            </DetailSection>

            <DetailSection title="证书">
              <Descriptions column={1} size="small">
                <Descriptions.Item label="状态">
                  <SnapshotTag status={snapshot?.certificate?.status} />
                </Descriptions.Item>
                <Descriptions.Item label="Profile">
                  {valueOrDash(snapshot?.certificate?.cryptoProfile)}
                </Descriptions.Item>
                <Descriptions.Item label="发送方证书">
                  {snapshot?.certificate?.senderMissing
                    ? <Tag color="error">缺失</Tag>
                    : valueOrDash(snapshot?.certificate?.senderThumbprint)}
                </Descriptions.Item>
                <Descriptions.Item label="收件方证书">
                  {snapshot?.certificate?.recipients?.length ? (
                    <Space size={[6, 6]} wrap>
                      {snapshot.certificate.recipients.map((recipient) => (
                        <Tag color={recipient.selected ? 'success' : 'error'} key={recipient.email}>
                          {recipient.email}: {recipient.thumbprint || '缺失'}
                        </Tag>
                      ))}
                    </Space>
                  ) : '未记录'}
                </Descriptions.Item>
                <Descriptions.Item label="缺失收件人">
                  {snapshot?.certificate?.missingRecipients?.length
                    ? snapshot.certificate.missingRecipients.join(', ')
                    : '无'}
                </Descriptions.Item>
              </Descriptions>
            </DetailSection>

            <DetailSection title="S/MIME">
              <div className="mail-processing-operation-grid">
                <SmimeOperation label="签名" operation={snapshot?.smime?.sign} />
                <SmimeOperation label="加密" operation={snapshot?.smime?.encrypt} />
                <SmimeOperation label="验签" operation={snapshot?.smime?.verify} />
                <SmimeOperation label="解密" operation={snapshot?.smime?.decrypt} />
              </div>
            </DetailSection>

            <AttachmentSecuritySection security={snapshot?.attachmentSecurity} />

            <DetailSection title="DLP">
              <Descriptions column={1} size="small">
                <Descriptions.Item label="状态">
                  <SnapshotTag status={snapshot?.dlp?.status} />
                </Descriptions.Item>
                <Descriptions.Item label="处置动作">
                  {valueOrDash(snapshot?.dlp?.action)}
                </Descriptions.Item>
                <Descriptions.Item label="建议动作">
                  {valueOrDash(snapshot?.dlp?.recommendedAction)}
                </Descriptions.Item>
                <Descriptions.Item label="最高严重度">
                  {valueOrDash(snapshot?.dlp?.maxSeverity)}
                </Descriptions.Item>
                <Descriptions.Item label="命中数量">
                  {valueOrDash(snapshot?.dlp?.matchCount)}
                </Descriptions.Item>
                <Descriptions.Item label="命中规则">
                  {snapshot?.dlp?.ruleNames?.length
                    ? snapshot.dlp.ruleNames.join(', ')
                    : '未记录'}
                </Descriptions.Item>
                <Descriptions.Item label="事件 ID">
                  {valueOrDash(snapshot?.dlp?.eventId)}
                </Descriptions.Item>
                <Descriptions.Item label="风险状态">
                  {valueOrDash(snapshot?.dlp?.ubaRiskLevel)}
                </Descriptions.Item>
                <Descriptions.Item label="摘要">
                  {valueOrDash(snapshot?.dlp?.failureReason ?? snapshot?.dlp?.summary)}
                </Descriptions.Item>
              </Descriptions>
            </DetailSection>

            <DetailSection title="最终处置">
              <Descriptions column={1} size="small">
                <Descriptions.Item label="最终状态">
                  <SnapshotTag status={snapshot?.finalDisposition?.status ?? currentDetailRecord.disposition} />
                </Descriptions.Item>
                <Descriptions.Item label="投递状态">
                  <SnapshotTag status={snapshot?.delivery?.status} />
                </Descriptions.Item>
                <Descriptions.Item label="路由">
                  {valueOrDash(snapshot?.delivery?.route ?? currentDetailRecord.routingDecision)}
                </Descriptions.Item>
                <Descriptions.Item label="Relay">
                  {snapshot?.delivery?.relayHost
                    ? `${snapshot.delivery.relayHost}:${snapshot.delivery.relayPort ?? ''} ${snapshot.delivery.transportProfile ?? ''}`
                    : '未记录'}
                </Descriptions.Item>
                <Descriptions.Item label="隔离目标">
                  {snapshot?.delivery?.targetId
                    ? `${snapshot.delivery.targetType ?? 'target'} / ${snapshot.delivery.targetId}`
                    : '无'}
                </Descriptions.Item>
                <Descriptions.Item label="失败原因">
                  {valueOrDash(snapshot?.failure?.detail ?? currentDetailRecord.failureReason)}
                </Descriptions.Item>
              </Descriptions>
            </DetailSection>

            <DetailSection title="审计轨迹">
              {auditTrace.length ? (
                <List
                  className="mail-processing-audit-list"
                  dataSource={auditTrace}
                  renderItem={(item) => (
                    <List.Item>
                      <div className="mail-processing-audit-list__item">
                        <div className="mail-processing-audit-list__head">
                          <Space size={8} className="min-width-zero">
                            <Tag color={item.success ? 'success' : 'error'}>
                              {item.success ? '成功' : '失败'}
                            </Tag>
                            <Text strong ellipsis>{item.action || item.typeDisplayName || item.type}</Text>
                          </Space>
                          <Text className="mail-processing-audit-list__time">
                            {formatAuditTime(item.occurredAt)}
                          </Text>
                        </div>
                        <Text className="mail-processing-audit-list__detail">
                          {item.detail || item.errorMessage || '无详情'}
                        </Text>
                      </div>
                    </List.Item>
                  )}
                />
              ) : (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无审计轨迹" />
              )}
            </DetailSection>
          </div>
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择邮件处理记录" />
        )}
      </DetailDrawer>
    </section>
  );
};

export default DashboardPanels;
