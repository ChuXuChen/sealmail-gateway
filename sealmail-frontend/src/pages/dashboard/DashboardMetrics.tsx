import type React from 'react';
import { Typography } from 'antd';
import type { MetricItem } from './dashboardModel';

const { Text } = Typography;

interface DashboardMetricsProps {
  metrics: MetricItem[];
}

const DashboardMetrics: React.FC<DashboardMetricsProps> = ({ metrics }) => (
  <section className="dashboard-metrics" aria-label="核心指标">
    {metrics.map((item) => (
      <article className={`metric-card metric-card--${item.tone}`} key={item.title}>
        <div className="metric-card__body">
          <span className="metric-card__icon">{item.icon}</span>
          <Text className="metric-card__label">{item.title}</Text>
          <Text className="metric-card__value">{item.value}</Text>
          <Text className="metric-card__hint">{item.hint}</Text>
        </div>
      </article>
    ))}
  </section>
);

export default DashboardMetrics;
