import type React from 'react';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  InboxOutlined,
  StopOutlined,
} from '@ant-design/icons';
import type { ExceptionMailStats, QuarantineStats } from '../../types';

export interface MetricItem {
  title: string;
  value: number;
  hint: string;
  icon: React.ReactNode;
  tone: 'danger' | 'info' | 'neutral' | 'success' | 'warning';
}

export interface DistributionItem {
  label: string;
  count: number;
  percent: number;
  tone: 'dlp' | 'exception';
}

export interface DashboardViewModel {
  allRecords: number;
  completionRate: number;
  distribution: DistributionItem[];
  exceptionRate: number;
  exceptionTotal: number;
  handled: number;
  healthPercent: number;
  metrics: MetricItem[];
  pending: number;
  pendingRate: number;
  quarantineTotal: number;
  rejected: number;
  rejectedRate: number;
  releasing: number;
  releasingRate: number;
  released: number;
  releasedRate: number;
  systemState: string;
  systemTone: 'success' | 'warning';
}

const emptyQuarantineStats: QuarantineStats = {
  total: 0,
  pending: 0,
  releasing: 0,
  released: 0,
  rejected: 0,
  byReason: {},
};

const emptyExceptionStats: ExceptionMailStats = {
  total: 0,
  byReason: {},
};

const formatPercent = (value: number, total: number) =>
  total > 0 ? Math.round((value / total) * 100) : 0;

export const buildDashboardViewModel = (
  stats: QuarantineStats | null,
  exceptionStats: ExceptionMailStats | null,
): DashboardViewModel => {
  const quarantineStats = stats ?? emptyQuarantineStats;
  const blockedStats = exceptionStats ?? emptyExceptionStats;

  const total = quarantineStats.total;
  const pending = quarantineStats.pending;
  const releasing = quarantineStats.releasing;
  const released = quarantineStats.released;
  const rejected = quarantineStats.rejected;
  const exceptionTotal = blockedStats.total;
  const handled = released + rejected;
  const allRecords = total + exceptionTotal;

  const pendingRate = formatPercent(pending, total);
  const releasingRate = formatPercent(releasing, total);
  const releasedRate = formatPercent(released, total);
  const rejectedRate = formatPercent(rejected, total);
  const completionRate = formatPercent(handled, total);
  const exceptionRate = formatPercent(exceptionTotal, allRecords);
  const healthPercent = pending > 0 ? Math.max(0, 100 - pendingRate) : 100;

  const quarantineReasons = Object.entries(quarantineStats.byReason)
    .sort((a, b) => b[1] - a[1]);
  const exceptionReasons = Object.entries(blockedStats.byReason)
    .sort((a, b) => b[1] - a[1]);
  const topReason = quarantineReasons[0] ?? exceptionReasons[0];

  const distribution: DistributionItem[] = [
    ...quarantineReasons.map(([reason, count]) => ({
      label: `DLP / ${reason}`,
      count,
      percent: formatPercent(count, allRecords),
      tone: 'dlp' as const,
    })),
    ...exceptionReasons.map(([reason, count]) => ({
      label: `异常 / ${reason}`,
      count,
      percent: formatPercent(count, allRecords),
      tone: 'exception' as const,
    })),
  ];

  const metrics: MetricItem[] = [
    {
      title: '邮件记录',
      value: allRecords,
      hint: topReason ? `主要原因：${topReason[0]}` : '暂无异常或隔离记录',
      icon: <InboxOutlined />,
      tone: 'info',
    },
    {
      title: '待处理',
      value: pending,
      hint: `${pendingRate}% DLP 隔离待处置`,
      icon: <ClockCircleOutlined />,
      tone: pending > 0 ? 'warning' : 'success',
    },
    {
      title: '已放行',
      value: released,
      hint: `${releasedRate}% 已恢复投递`,
      icon: <CheckCircleOutlined />,
      tone: 'success',
    },
    {
      title: '异常阻断',
      value: exceptionTotal,
      hint: `${exceptionRate}% 自动阻断`,
      icon: <StopOutlined />,
      tone: exceptionTotal > 0 ? 'danger' : 'neutral',
    },
    {
      title: '已拒绝',
      value: rejected,
      hint: `${rejectedRate}% 人工拒绝`,
      icon: <CloseCircleOutlined />,
      tone: rejected > 0 ? 'danger' : 'neutral',
    },
  ];

  return {
    allRecords,
    completionRate,
    distribution,
    exceptionRate,
    exceptionTotal,
    handled,
    healthPercent,
    metrics,
    pending,
    pendingRate,
    quarantineTotal: total,
    rejected,
    rejectedRate,
    releasing,
    releasingRate,
    released,
    releasedRate,
    systemState: pending > 0 ? '待处理' : '运行正常',
    systemTone: pending > 0 ? 'warning' : 'success',
  };
};
