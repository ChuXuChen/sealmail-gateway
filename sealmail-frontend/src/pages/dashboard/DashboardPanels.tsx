import type React from 'react';
import { Card, Col, Empty, Progress, Row, Space, Statistic, Tag, Typography } from 'antd';
import {
  SafetyCertificateOutlined,
  SecurityScanOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { algorithmList } from './dashboardModel';
import type { DashboardViewModel } from './dashboardModel';

const { Text } = Typography;

interface DashboardPanelsProps {
  viewModel: DashboardViewModel;
}

const DashboardPanels: React.FC<DashboardPanelsProps> = ({ viewModel }) => (
  <Row gutter={[16, 16]}>
    <Col xs={24} xl={15}>
      <Card
        className="dashboard-card"
        title="邮件处置"
        extra={<Tag color={viewModel.pending > 0 ? 'warning' : 'success'}>{viewModel.completionRate}% 已处理</Tag>}
      >
        <Row gutter={[16, 16]} align="middle">
          <Col xs={24} md={8}>
            <Progress
              type="dashboard"
              percent={viewModel.completionRate}
              status={viewModel.pending > 0 ? 'active' : 'success'}
            />
            <div>
              <Text type="secondary">隔离邮件处置完成率</Text>
            </div>
          </Col>

          <Col xs={24} md={16}>
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              {[
                { label: '待处理', count: viewModel.pending, percent: viewModel.pendingRate, status: 'active' as const },
                { label: '已放行', count: viewModel.released, percent: viewModel.releasedRate, status: 'success' as const },
                { label: '异常阻断', count: viewModel.exceptionTotal, percent: viewModel.exceptionRate, status: 'normal' as const },
                { label: '已拒绝', count: viewModel.rejected, percent: viewModel.rejectedRate, status: 'exception' as const },
              ].map((item) => (
                <div key={item.label}>
                  <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                    <Text>{item.label}</Text>
                    <Text strong>{item.count}</Text>
                  </Space>
                  <Progress percent={item.percent} showInfo={false} status={item.status} />
                </div>
              ))}
            </Space>
          </Col>
        </Row>
      </Card>
    </Col>

    <Col xs={24} xl={9}>
      <Card title="队列状态" className="dashboard-card">
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Space>
            {viewModel.pending > 0 ? <WarningOutlined /> : <SafetyCertificateOutlined />}
            <Space direction="vertical" size={0}>
              <Text strong>{viewModel.pending > 0 ? '存在待处理邮件' : '当前无积压'}</Text>
              <Text type="secondary">
                {viewModel.pending > 0 ? `${viewModel.pending} 封邮件等待处置` : '待处理队列已处理完毕'}
              </Text>
            </Space>
          </Space>

          <div>
            <Space style={{ width: '100%', justifyContent: 'space-between' }}>
              <Text type="secondary">运行健康度</Text>
              <Text strong>{viewModel.healthPercent}%</Text>
            </Space>
            <Progress percent={viewModel.healthPercent} status={viewModel.pending > 0 ? 'active' : 'success'} showInfo={false} />
          </div>

          <Row gutter={16}>
            <Col span={12}>
              <Statistic title="隔离总量" value={viewModel.quarantineTotal} />
            </Col>
            <Col span={12}>
              <Statistic title="已处置" value={viewModel.handled} />
            </Col>
          </Row>
        </Space>
      </Card>
    </Col>

    <Col xs={24} xl={15}>
      <Card title="原因分布" className="dashboard-card">
        {viewModel.distribution.length > 0 ? (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            {viewModel.distribution.map((item) => (
              <div key={item.label}>
                <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                  <Text ellipsis>{item.label}</Text>
                  <Text strong>{item.count}</Text>
                </Space>
                <Progress
                  percent={item.percent}
                  showInfo={false}
                  status={item.tone === 'exception' ? 'exception' : 'normal'}
                />
              </div>
            ))}
          </Space>
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无异常或隔离数据" />
        )}
      </Card>
    </Col>

    <Col xs={24} xl={9}>
      <Card
        className="dashboard-card"
        title="算法能力"
        extra={<SecurityScanOutlined />}
      >
        <Space direction="vertical" size={16}>
          {algorithmList.map((item) => (
            <Space key={item.title} align="start">
              {item.icon}
              <Space direction="vertical" size={4}>
                <Text strong>{item.title}</Text>
                <Text type="secondary">{item.description}</Text>
                <Space size={[6, 6]} wrap>
                  {item.tags.map((tag) => (
                    <Tag key={tag.label} color={tag.color}>
                      {tag.label}
                    </Tag>
                  ))}
                </Space>
              </Space>
            </Space>
          ))}
        </Space>
      </Card>
    </Col>
  </Row>
);

export default DashboardPanels;
