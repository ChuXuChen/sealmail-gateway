import type React from 'react';
import { Button, Progress, Tag, Typography } from 'antd';
import {
  ReloadOutlined,
  SafetyCertificateOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { PageHeader } from '../../components/Page';
import type { DashboardViewModel } from './dashboardModel';

const { Text } = Typography;

interface DashboardHeroProps {
  refreshing: boolean;
  viewModel: DashboardViewModel;
  onRefresh: () => void;
}

const DashboardHero: React.FC<DashboardHeroProps> = ({
  refreshing,
  viewModel,
  onRefresh,
}) => (
  <section className="dashboard-hero" aria-label="运行概览">
    <div className="dashboard-hero__main">
      <PageHeader
        title="仪表盘"
        description="邮件安全、DLP 隔离处置、异常阻断与 S/MIME 能力概览。"
        extra={<Tag color={viewModel.systemTone}>{viewModel.systemState}</Tag>}
        actions={(
          <Button icon={<ReloadOutlined />} loading={refreshing} onClick={onRefresh}>
            刷新
          </Button>
        )}
      />
      <div className="dashboard-hero__status">
        <span className={`dashboard-hero__signal dashboard-hero__signal--${viewModel.pending > 0 ? 'warning' : 'ok'}`}>
          {viewModel.pending > 0 ? <WarningOutlined /> : <SafetyCertificateOutlined />}
        </span>
        <div className="dashboard-hero__status-body">
          <Text className="dashboard-hero__status-label">
            {viewModel.pending > 0 ? '待处置队列需要关注' : '邮件安全链路稳定'}
          </Text>
          <Text className="dashboard-hero__status-copy">
            {viewModel.pending > 0
              ? `${viewModel.pending} 封隔离邮件等待人工处置，当前健康度 ${viewModel.healthPercent}%。`
              : `当前无待处理隔离邮件，处置完成率 ${viewModel.completionRate}%。`}
          </Text>
        </div>
      </div>
      <div className="dashboard-hero__summary" aria-label="关键摘要">
        <div className="dashboard-hero__summary-item">
          <Text className="dashboard-hero__summary-label">总记录</Text>
          <Text className="dashboard-hero__summary-value">{viewModel.allRecords}</Text>
        </div>
        <div className="dashboard-hero__summary-item">
          <Text className="dashboard-hero__summary-label">待处理</Text>
          <Text className="dashboard-hero__summary-value">{viewModel.pending}</Text>
        </div>
        <div className="dashboard-hero__summary-item">
          <Text className="dashboard-hero__summary-label">已处理</Text>
          <Text className="dashboard-hero__summary-value">{viewModel.handled}</Text>
        </div>
      </div>
    </div>
    <div className="dashboard-hero__meter">
      <Progress
        type="dashboard"
        percent={viewModel.healthPercent}
        size={144}
        status={viewModel.pending > 0 ? 'active' : 'success'}
      />
      <Text className="dashboard-hero__meter-label">运行健康度</Text>
    </div>
  </section>
);

export default DashboardHero;
