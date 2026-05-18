import type React from 'react';
import { Button, Progress, Space, Tag, Typography } from 'antd';
import {
  CheckCircleOutlined,
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
  <>
    <PageHeader
      title="运维仪表盘"
      description="邮件处置、异常阻断、DLP 风险来源与加密能力的当前视图。"
      extra={<Tag color={viewModel.systemTone}>{viewModel.systemState}</Tag>}
      actions={(
        <Button icon={<ReloadOutlined />} loading={refreshing} onClick={onRefresh}>
          刷新
        </Button>
      )}
    />
    <section className={`dashboard-hero dashboard-hero--${viewModel.pending > 0 ? 'warning' : 'ok'}`} aria-label="运行概览">
      <div className="dashboard-hero__status">
        <span className="dashboard-hero__signal">
          {viewModel.pending > 0 ? <WarningOutlined /> : <SafetyCertificateOutlined />}
        </span>
        <div className="dashboard-hero__status-body">
          <Text className="dashboard-hero__eyebrow">当前状态</Text>
          <Text className="dashboard-hero__status-label">
            {viewModel.pending > 0 ? '待处置队列需要关注' : '邮件安全链路稳定'}
          </Text>
          <Text className="dashboard-hero__status-copy">
            {viewModel.pending > 0
              ? `${viewModel.pending} 封隔离邮件等待人工处置，健康度 ${viewModel.healthPercent}%。`
              : `当前无待处理隔离邮件，处置完成率 ${viewModel.completionRate}%。`}
          </Text>
        </div>
      </div>

      <div className="dashboard-hero__meter">
        <Progress
          percent={viewModel.healthPercent}
          size={[190, 10]}
          status={viewModel.pending > 0 ? 'active' : 'success'}
          showInfo={false}
        />
        <Space size={8} wrap>
          <Tag icon={<CheckCircleOutlined />} color="success">{viewModel.completionRate}% 已处理</Tag>
          <Tag color={viewModel.exceptionTotal > 0 ? 'error' : 'default'}>{viewModel.exceptionTotal} 异常阻断</Tag>
        </Space>
      </div>
    </section>
  </>
);

export default DashboardHero;
