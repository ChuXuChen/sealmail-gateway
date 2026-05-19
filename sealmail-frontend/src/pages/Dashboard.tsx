import type React from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Spin, message } from 'antd';
import { dlpQuarantineApi, exceptionMailApi, systemSettingsApi } from '../api/client';
import type { ExceptionMailStats, QuarantineStats, SystemSettings } from '../types';
import { PageShell } from '../components/Page';
import DashboardHero from './dashboard/DashboardHero';
import DashboardMetrics from './dashboard/DashboardMetrics';
import DashboardPanels from './dashboard/DashboardPanels';
import { buildDashboardViewModel } from './dashboard/dashboardModel';

const Dashboard: React.FC = () => {
  const [stats, setStats] = useState<QuarantineStats | null>(null);
  const [exceptionStats, setExceptionStats] = useState<ExceptionMailStats | null>(null);
  const [settings, setSettings] = useState<SystemSettings | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const loadStats = useCallback(async () => {
    setRefreshing(true);
    try {
      const [quarantineResponse, exceptionResponse, settingsResponse] = await Promise.allSettled([
        dlpQuarantineApi.getStats(),
        exceptionMailApi.getStats(),
        systemSettingsApi.get(),
      ]);
      if (quarantineResponse.status === 'rejected' || exceptionResponse.status === 'rejected') {
        throw new Error('failed to load dashboard statistics');
      }
      setStats(quarantineResponse.value);
      setExceptionStats(exceptionResponse.value);
      if (settingsResponse.status === 'fulfilled') {
        setSettings(settingsResponse.value);
      }
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
    <PageShell className="dashboard-page">
      <DashboardHero
        refreshing={refreshing}
        viewModel={viewModel}
        onRefresh={loadStats}
      />
      <DashboardMetrics metrics={viewModel.metrics} />
      <DashboardPanels cryptoCapabilities={settings?.cryptoCapabilities} viewModel={viewModel} />
    </PageShell>
  );
};

export default Dashboard;
