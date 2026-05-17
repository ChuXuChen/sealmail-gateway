import type React from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Spin, message } from 'antd';
import { dlpQuarantineApi, exceptionMailApi } from '../api/client';
import type { ExceptionMailStats, QuarantineStats } from '../types';
import { PageShell } from '../components/Page';
import DashboardHero from './dashboard/DashboardHero';
import DashboardMetrics from './dashboard/DashboardMetrics';
import DashboardPanels from './dashboard/DashboardPanels';
import { buildDashboardViewModel } from './dashboard/dashboardModel';

const Dashboard: React.FC = () => {
  const [stats, setStats] = useState<QuarantineStats | null>(null);
  const [exceptionStats, setExceptionStats] = useState<ExceptionMailStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const loadStats = useCallback(async () => {
    setRefreshing(true);
    try {
      const [quarantineResponse, exceptionResponse] = await Promise.all([
        dlpQuarantineApi.getStats(),
        exceptionMailApi.getStats(),
      ]);
      setStats(quarantineResponse.data.data);
      setExceptionStats(exceptionResponse.data.data);
    } catch {
      message.error('加载统计数据失败');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    // Initial remote synchronization for dashboard data.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadStats();
  }, [loadStats]);

  const viewModel = useMemo(
    () => buildDashboardViewModel(stats, exceptionStats),
    [exceptionStats, stats],
  );

  if (loading) {
    return (
      <PageShell>
        <div className="page-loading">
          <Spin size="large" />
        </div>
      </PageShell>
    );
  }

  return (
    <PageShell>
      <DashboardHero
        refreshing={refreshing}
        viewModel={viewModel}
        onRefresh={loadStats}
      />
      <DashboardMetrics metrics={viewModel.metrics} />
      <DashboardPanels viewModel={viewModel} />
    </PageShell>
  );
};

export default Dashboard;
