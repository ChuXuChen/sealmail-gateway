import type React from 'react';
import { Button, Empty, Progress, Space, Tag, Typography } from 'antd';
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
import CryptoCapabilityTags from '../settings/CryptoCapabilityTags';
import type { DashboardViewModel } from './dashboardModel';
import type { SystemSettings } from '../../types';
import { ROUTES } from '../../router/routes';

const { Text } = Typography;

interface DashboardPanelsProps {
  cryptoCapabilities?: SystemSettings['cryptoCapabilities'];
  viewModel: DashboardViewModel;
}

const DashboardPanels: React.FC<DashboardPanelsProps> = ({ cryptoCapabilities, viewModel }) => {
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
    </section>
  );
};

export default DashboardPanels;
