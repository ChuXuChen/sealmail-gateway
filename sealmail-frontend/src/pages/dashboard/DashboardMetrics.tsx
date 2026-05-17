import type React from 'react';
import { Card, Col, Row, Statistic, Typography } from 'antd';
import type { MetricItem } from './dashboardModel';

const { Text } = Typography;

interface DashboardMetricsProps {
  metrics: MetricItem[];
}

const DashboardMetrics: React.FC<DashboardMetricsProps> = ({ metrics }) => (
  <Row gutter={[16, 16]}>
    {metrics.map((item) => (
      <Col xs={24} sm={12} xl={6} xxl={4} key={item.title}>
        <Card className="metric-card">
          <div className="metric-card__head">
            <span className="metric-card__icon">{item.icon}</span>
            <Text type="secondary">{item.title}</Text>
          </div>
          <Statistic value={item.value} />
          <Text type="secondary" className="metric-card__hint">{item.hint}</Text>
        </Card>
      </Col>
    ))}
  </Row>
);

export default DashboardMetrics;
