import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Empty,
  Progress,
  Row,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  FileProtectOutlined,
  InboxOutlined,
  KeyOutlined,
  LockOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SecurityScanOutlined,
  StopOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { dlpQuarantineApi, exceptionMailApi } from '../api/client';
import { ExceptionMailStats, QuarantineStats } from '../types';
import './Dashboard.css';

const { Text, Title } = Typography;

interface AlgorithmInfo {
  icon: React.ReactNode;
  title: string;
  description: string;
  tags: { label: string; color: string }[];
}

interface MetricItem {
  title: string;
  value: number;
  hint: string;
  icon: React.ReactNode;
  tone: 'blue' | 'amber' | 'green' | 'purple' | 'red';
}

interface DistributionItem {
  label: string;
  count: number;
  percent: number;
  tone: 'dlp' | 'exception';
}

const algorithmList: AlgorithmInfo[] = [
  {
    icon: <FileProtectOutlined />,
    title: '签名算法',
    description: '出站签名 / 入站验签',
    tags: [
      { label: 'SM3withSM2', color: 'red' },
      { label: 'SHA256withRSA', color: 'blue' },
    ],
  },
  {
    icon: <LockOutlined />,
    title: '内容加密',
    description: 'S/MIME 内容加密',
    tags: [
      { label: 'SM4-CBC', color: 'red' },
      { label: 'AES-256-CBC', color: 'blue' },
    ],
  },
  {
    icon: <KeyOutlined />,
    title: '密钥交换',
    description: '收件人密钥封装',
    tags: [
      { label: 'SM2 KeyAgreement', color: 'red' },
      { label: 'RSA KeyTransport', color: 'blue' },
    ],
  },
];

const emptyQuarantineStats: QuarantineStats = {
  total: 0,
  pending: 0,
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

  const viewModel = useMemo(() => {
    const quarantineStats = stats ?? emptyQuarantineStats;
    const blockedStats = exceptionStats ?? emptyExceptionStats;

    const total = quarantineStats.total;
    const pending = quarantineStats.pending;
    const released = quarantineStats.released;
    const rejected = quarantineStats.rejected;
    const exceptionTotal = blockedStats.total;
    const handled = released + rejected;
    const allRecords = total + exceptionTotal;

    const pendingRate = formatPercent(pending, total);
    const releasedRate = formatPercent(released, total);
    const rejectedRate = formatPercent(rejected, total);
    const completionRate = formatPercent(handled, total);
    const exceptionRate = formatPercent(exceptionTotal, allRecords);

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
        tone: 'blue',
      },
      {
        title: '待处理',
        value: pending,
        hint: `${pendingRate}% DLP 隔离待处置`,
        icon: <ClockCircleOutlined />,
        tone: 'amber',
      },
      {
        title: '已放行',
        value: released,
        hint: `${releasedRate}% 已恢复投递`,
        icon: <CheckCircleOutlined />,
        tone: 'green',
      },
      {
        title: '异常阻断',
        value: exceptionTotal,
        hint: `${exceptionRate}% 自动阻断`,
        icon: <StopOutlined />,
        tone: 'purple',
      },
      {
        title: '已拒绝',
        value: rejected,
        hint: `${rejectedRate}% 人工拒绝`,
        icon: <StopOutlined />,
        tone: 'red',
      },
    ];

    return {
      allRecords,
      completionRate,
      distribution,
      exceptionRate,
      exceptionTotal,
      handled,
      metrics,
      pending,
      pendingRate,
      quarantineTotal: total,
      rejected,
      rejectedRate,
      released,
      releasedRate,
      systemState: pending > 0 ? '待处理' : '运行正常',
      systemTone: pending > 0 ? 'warning' : 'success',
    };
  }, [exceptionStats, stats]);

  if (loading) {
    return (
      <div className="dashboard-loading">
        <Spin size="large" />
      </div>
    );
  }

  const healthPercent = viewModel.pending > 0 ? Math.max(0, 100 - viewModel.pendingRate) : 100;
  const healthColor = viewModel.pending > 0 ? '#d97706' : '#059669';

  return (
    <div className="dashboard-page">
      <section className="dashboard-hero">
        <div className="dashboard-hero__content">
          <Space size={10} align="center" wrap className="dashboard-hero__title-row">
            <Title level={2}>仪表盘</Title>
            <Tag color={viewModel.systemTone}>{viewModel.systemState}</Tag>
          </Space>
          <Text className="dashboard-hero__summary">
            邮件安全、DLP 隔离处置、异常阻断与 S/MIME 能力概览
          </Text>
        </div>

        <div className="dashboard-hero__actions">
          <div className="dashboard-health">
            <span className="dashboard-health__label">安全态势</span>
            <strong>{healthPercent}%</strong>
          </div>
          <Button icon={<ReloadOutlined />} loading={refreshing} onClick={loadStats}>
            刷新
          </Button>
        </div>
      </section>

      <Row gutter={[16, 16]} className="dashboard-metrics">
        {viewModel.metrics.map((item) => (
          <Col xs={24} sm={12} xl={6} xxl={4} key={item.title}>
            <Card className={`dashboard-card dashboard-metric dashboard-metric--${item.tone}`} bordered={false}>
              <div className="dashboard-metric__top">
                <div>
                  <Text className="dashboard-metric__label">{item.title}</Text>
                  <div className="dashboard-metric__value">{item.value}</div>
                </div>
                <div className="dashboard-metric__icon">{item.icon}</div>
              </div>
              <Text className="dashboard-metric__hint">{item.hint}</Text>
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={15}>
          <Card
            className="dashboard-card dashboard-disposal"
            bordered={false}
            title="邮件处置"
            extra={<Tag color={viewModel.pending > 0 ? 'warning' : 'success'}>{viewModel.completionRate}% 已处理</Tag>}
          >
            <div className="dashboard-disposal__grid">
              <div className="dashboard-disposal__chart">
                <Progress
                  type="dashboard"
                  percent={viewModel.completionRate}
                  size={150}
                  strokeColor={viewModel.pending > 0 ? '#1677ff' : '#059669'}
                />
                <Text type="secondary">隔离邮件处置完成率</Text>
              </div>

              <div className="dashboard-status-list">
                {[
                  { label: '待处理', count: viewModel.pending, percent: viewModel.pendingRate, color: '#d97706' },
                  { label: '已放行', count: viewModel.released, percent: viewModel.releasedRate, color: '#059669' },
                  { label: '异常阻断', count: viewModel.exceptionTotal, percent: viewModel.exceptionRate, color: '#7c3aed' },
                  { label: '已拒绝', count: viewModel.rejected, percent: viewModel.rejectedRate, color: '#dc2626' },
                ].map((item) => (
                  <div className="dashboard-status" key={item.label}>
                    <div className="dashboard-status__head">
                      <Space>
                        <span className="dashboard-status__dot" style={{ background: item.color }} />
                        <Text>{item.label}</Text>
                      </Space>
                      <Text strong>{item.count}</Text>
                    </div>
                    <div className="dashboard-status__track">
                      <span style={{ width: `${item.percent}%`, background: item.color }} />
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </Card>
        </Col>

        <Col xs={24} xl={9}>
          <Card className="dashboard-card dashboard-queue" bordered={false} title="队列状态">
            <div className="dashboard-queue__state">
              <div
                className="dashboard-queue__icon"
                style={{
                  background: viewModel.pending > 0 ? '#fffbeb' : '#ecfdf5',
                  color: viewModel.pending > 0 ? '#d97706' : '#059669',
                }}
              >
                {viewModel.pending > 0 ? <WarningOutlined /> : <SafetyCertificateOutlined />}
              </div>
              <div>
                <Text strong>{viewModel.pending > 0 ? '存在待处理邮件' : '当前无积压'}</Text>
                <Text type="secondary">
                  {viewModel.pending > 0 ? `${viewModel.pending} 封邮件等待处置` : '待处理队列已处理完毕'}
                </Text>
              </div>
            </div>

            <div className="dashboard-queue__health">
              <div className="dashboard-queue__health-head">
                <Text type="secondary">运行健康度</Text>
                <Text strong>{healthPercent}%</Text>
              </div>
              <Progress percent={healthPercent} strokeColor={healthColor} showInfo={false} />
            </div>

            <div className="dashboard-queue__tiles">
              <div>
                <Text type="secondary">隔离总量</Text>
                <strong>{viewModel.quarantineTotal}</strong>
              </div>
              <div>
                <Text type="secondary">已处置</Text>
                <strong>{viewModel.handled}</strong>
              </div>
            </div>
          </Card>
        </Col>

        <Col xs={24} xl={15}>
          <Card className="dashboard-card" bordered={false} title="原因分布">
            {viewModel.distribution.length > 0 ? (
              <div className="dashboard-reasons">
                {viewModel.distribution.map((item) => (
                  <div className={`dashboard-reason dashboard-reason--${item.tone}`} key={item.label}>
                    <div className="dashboard-reason__head">
                      <Text ellipsis>{item.label}</Text>
                      <Text strong>{item.count}</Text>
                    </div>
                    <div className="dashboard-reason__track">
                      <span style={{ width: `${item.percent}%` }} />
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无异常或隔离数据" />
            )}
          </Card>
        </Col>

        <Col xs={24} xl={9}>
          <Card
            className="dashboard-card dashboard-algorithms"
            bordered={false}
            title="算法能力"
            extra={<SecurityScanOutlined />}
          >
            {algorithmList.map((item) => (
              <div className="dashboard-algorithm" key={item.title}>
                <div className="dashboard-algorithm__icon">{item.icon}</div>
                <div>
                  <Text strong>{item.title}</Text>
                  <Text type="secondary">{item.description}</Text>
                  <Space size={[6, 6]} wrap className="dashboard-algorithm__tags">
                    {item.tags.map((tag) => (
                      <Tag key={tag.label} color={tag.color}>
                        {tag.label}
                      </Tag>
                    ))}
                  </Space>
                </div>
              </div>
            ))}
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Dashboard;
