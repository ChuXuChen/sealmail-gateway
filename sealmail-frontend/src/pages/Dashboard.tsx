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
  Statistic,
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
import { PageHeader, PageShell } from '../components/Page';

const { Text } = Typography;

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
      },
      {
        title: '待处理',
        value: pending,
        hint: `${pendingRate}% DLP 隔离待处置`,
        icon: <ClockCircleOutlined />,
      },
      {
        title: '已放行',
        value: released,
        hint: `${releasedRate}% 已恢复投递`,
        icon: <CheckCircleOutlined />,
      },
      {
        title: '异常阻断',
        value: exceptionTotal,
        hint: `${exceptionRate}% 自动阻断`,
        icon: <StopOutlined />,
      },
      {
        title: '已拒绝',
        value: rejected,
        hint: `${rejectedRate}% 人工拒绝`,
        icon: <StopOutlined />,
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
      <PageShell>
        <div className="page-loading">
          <Spin size="large" />
        </div>
      </PageShell>
    );
  }

  const healthPercent = viewModel.pending > 0 ? Math.max(0, 100 - viewModel.pendingRate) : 100;

  return (
    <PageShell>
      <section className="dashboard-hero">
        <div className="dashboard-hero__main">
          <PageHeader
            title="仪表盘"
            description="邮件安全、DLP 隔离处置、异常阻断与 S/MIME 能力概览。"
            extra={<Tag color={viewModel.systemTone}>{viewModel.systemState}</Tag>}
            actions={(
              <Button icon={<ReloadOutlined />} loading={refreshing} onClick={loadStats}>
                刷新
              </Button>
            )}
          />
          <div className="dashboard-hero__status">
            <Space align="center" size={14}>
              <span className={`dashboard-hero__signal dashboard-hero__signal--${viewModel.pending > 0 ? 'warning' : 'ok'}`}>
                {viewModel.pending > 0 ? <WarningOutlined /> : <SafetyCertificateOutlined />}
              </span>
              <div>
                <Text className="dashboard-hero__status-label">
                  {viewModel.pending > 0 ? '待处置队列需要关注' : '邮件安全链路稳定'}
                </Text>
                <Text className="dashboard-hero__status-copy">
                  {viewModel.pending > 0
                    ? `${viewModel.pending} 封隔离邮件等待人工处置，当前健康度 ${healthPercent}%。`
                    : `当前无待处理隔离邮件，处置完成率 ${viewModel.completionRate}%。`}
                </Text>
              </div>
            </Space>
          </div>
        </div>
        <div className="dashboard-hero__meter">
          <Progress
            type="dashboard"
            percent={healthPercent}
            size={144}
            status={viewModel.pending > 0 ? 'active' : 'success'}
          />
          <Text type="secondary">运行健康度</Text>
        </div>
      </section>

      <Row gutter={[16, 16]}>
        {viewModel.metrics.map((item) => (
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
                  <Text strong>{healthPercent}%</Text>
                </Space>
                <Progress percent={healthPercent} status={viewModel.pending > 0 ? 'active' : 'success'} showInfo={false} />
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
    </PageShell>
  );
};

export default Dashboard;
