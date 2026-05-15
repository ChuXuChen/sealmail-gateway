import React, { useEffect, useState } from 'react';
import {
  Card,
  Row,
  Col,
  Typography,
  Spin,
  message,
  Tag,
  Space,
  Button,
  Progress,
  Empty,
} from 'antd';
import {
  InboxOutlined,
  CheckCircleOutlined,
  StopOutlined,
  SafetyCertificateOutlined,
  SecurityScanOutlined,
  FileProtectOutlined,
  LockOutlined,
  KeyOutlined,
  ReloadOutlined,
  ClockCircleOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { dlpQuarantineApi, exceptionMailApi } from '../api/client';
import { ExceptionMailStats, QuarantineStats } from '../types';

const { Title, Text } = Typography;

interface AlgorithmInfo {
  icon: React.ReactNode;
  title: string;
  description: string;
  tags: { label: string; color: string }[];
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

const cardStyle: React.CSSProperties = {
  borderRadius: 8,
  borderColor: '#e5e7eb',
  boxShadow: '0 1px 2px rgba(15, 23, 42, 0.04)',
};

const metricCardStyle: React.CSSProperties = {
  ...cardStyle,
  height: '100%',
};

const formatPercent = (value: number, total: number) =>
  total > 0 ? Math.round((value / total) * 100) : 0;

const Dashboard: React.FC = () => {
  const [stats, setStats] = useState<QuarantineStats | null>(null);
  const [exceptionStats, setExceptionStats] = useState<ExceptionMailStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    loadStats();
  }, []);

  const loadStats = async () => {
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
  };

  if (loading) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: 360 }}>
        <Spin size="large" />
      </div>
    );
  }

  const total = stats?.total || 0;
  const pending = stats?.pending || 0;
  const exceptionTotal = exceptionStats?.total || 0;
  const released = stats?.released || 0;
  const rejected = stats?.rejected || 0;
  const completed = released + rejected;
  const completionRate = formatPercent(completed, total);
  const pendingRate = formatPercent(pending, total);
  const exceptionRate = formatPercent(exceptionTotal, total + exceptionTotal);
  const releasedRate = formatPercent(released, total);
  const rejectedRate = formatPercent(rejected, total);
  const quarantineReasons = Object.entries(stats?.byReason || {})
    .sort((a, b) => b[1] - a[1]);
  const exceptionReasons = Object.entries(exceptionStats?.byReason || {})
    .sort((a, b) => b[1] - a[1]);
  const topReason = quarantineReasons[0] || exceptionReasons[0];
  const systemState = pending > 0 ? '待处理' : '运行正常';
  const stateColor = pending > 0 ? 'warning' : 'success';

  const metrics = [
    {
      title: '邮件记录',
      value: total + exceptionTotal,
      hint: topReason ? `主要原因：${topReason[0]}` : '暂无异常或隔离记录',
      icon: <InboxOutlined />,
      color: '#2563eb',
      bg: '#eff6ff',
    },
    {
      title: '待处理',
      value: pending,
      hint: `${pendingRate}% DLP 隔离待处置`,
      icon: <ClockCircleOutlined />,
      color: '#d97706',
      bg: '#fffbeb',
    },
    {
      title: '已放行',
      value: released,
      hint: `${releasedRate}% 已恢复投递`,
      icon: <CheckCircleOutlined />,
      color: '#059669',
      bg: '#ecfdf5',
    },
    {
      title: '已阻断',
      value: exceptionTotal,
      hint: `${exceptionRate}% 异常邮件自动阻断`,
      icon: <StopOutlined />,
      color: '#7c3aed',
      bg: '#f5f3ff',
    },
    {
      title: '已拒绝',
      value: rejected,
      hint: `${rejectedRate}% 已阻断`,
      icon: <StopOutlined />,
      color: '#dc2626',
      bg: '#fef2f2',
    },
  ];

  return (
    <div style={{ background: '#f8fafc', margin: -24, padding: 24, minHeight: 'calc(100vh - 112px)' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          gap: 16,
          alignItems: 'flex-start',
          marginBottom: 20,
        }}
      >
        <div>
          <Space size={10} align="center" style={{ marginBottom: 6 }}>
            <Title level={3} style={{ margin: 0, letterSpacing: 0 }}>
              仪表盘
            </Title>
            <Tag color={stateColor} style={{ margin: 0 }}>
              {systemState}
            </Tag>
          </Space>
          <Text type="secondary">邮件安全、异常记录、DLP 隔离处置与 S/MIME 能力概览</Text>
        </div>
        <Button icon={<ReloadOutlined />} loading={refreshing} onClick={loadStats}>
          刷新
        </Button>
      </div>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        {metrics.map((item) => (
          <Col xs={24} sm={12} xl={6} key={item.title}>
            <Card style={metricCardStyle} styles={{ body: { padding: 18 } }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}>
                <div>
                  <Text type="secondary" style={{ fontSize: 13 }}>
                    {item.title}
                  </Text>
                  <div style={{ color: '#111827', fontSize: 30, lineHeight: '38px', fontWeight: 650 }}>
                    {item.value}
                  </div>
                </div>
                <div
                  style={{
                    width: 38,
                    height: 38,
                    borderRadius: 8,
                    display: 'grid',
                    placeItems: 'center',
                    color: item.color,
                    background: item.bg,
                    fontSize: 19,
                    flex: '0 0 auto',
                  }}
                >
                  {item.icon}
                </div>
              </div>
              <Text type="secondary" style={{ display: 'block', marginTop: 10, fontSize: 12 }}>
                {item.hint}
              </Text>
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={16}>
          <Card
            title="邮件处置"
            style={cardStyle}
            styles={{ body: { padding: 20 } }}
            extra={<Tag color={pending > 0 ? 'warning' : 'success'}>{completionRate}% 已处理</Tag>}
          >
            <div style={{ display: 'flex', gap: 24, alignItems: 'center', flexWrap: 'wrap' }}>
              <Progress
                type="dashboard"
                percent={completionRate}
                size={132}
                strokeColor={pending > 0 ? '#2563eb' : '#059669'}
              />
              <div style={{ flex: 1, minWidth: 260 }}>
                <div style={{ display: 'grid', gap: 12 }}>
                  {[
                    { label: '待处理', count: pending, percent: pendingRate, color: '#d97706' },
                    { label: '已放行', count: released, percent: releasedRate, color: '#059669' },
                    { label: '异常邮件', count: exceptionTotal, percent: exceptionRate, color: '#7c3aed' },
                    { label: '已拒绝', count: rejected, percent: rejectedRate, color: '#dc2626' },
                  ].map((item) => (
                    <div key={item.label}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                        <Space>
                          <span style={{ width: 8, height: 8, borderRadius: 4, background: item.color }} />
                          <Text>{item.label}</Text>
                        </Space>
                        <Text strong>{item.count}</Text>
                      </div>
                      <div style={{ height: 8, background: '#e5e7eb', borderRadius: 999, overflow: 'hidden' }}>
                        <div
                          style={{
                            width: `${item.percent}%`,
                            height: '100%',
                            background: item.color,
                            borderRadius: 999,
                          }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </Card>
        </Col>

        <Col xs={24} xl={8}>
          <Card title="队列状态" style={cardStyle} styles={{ body: { padding: 20 } }}>
            <Space direction="vertical" size={14} style={{ width: '100%' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <div
                  style={{
                    width: 42,
                    height: 42,
                    borderRadius: 8,
                    display: 'grid',
                    placeItems: 'center',
                    background: pending > 0 ? '#fffbeb' : '#ecfdf5',
                    color: pending > 0 ? '#d97706' : '#059669',
                    fontSize: 20,
                  }}
                >
                  {pending > 0 ? <WarningOutlined /> : <SafetyCertificateOutlined />}
                </div>
                <div>
                  <Text strong>{pending > 0 ? '存在待处理邮件' : '当前无积压'}</Text>
                  <div>
                    <Text type="secondary">
                      {pending > 0 ? `${pending} 封邮件等待处置` : '待处理队列已处理完毕'}
                    </Text>
                  </div>
                </div>
              </div>
              <div style={{ borderTop: '1px solid #eef2f7', paddingTop: 14 }}>
                <Text type="secondary">处置完成率</Text>
                <Progress percent={completionRate} strokeColor="#2563eb" />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                <div style={{ padding: 12, background: '#f8fafc', borderRadius: 8 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>放行占比</Text>
                  <div style={{ fontSize: 20, fontWeight: 650 }}>{releasedRate}%</div>
                </div>
                <div style={{ padding: 12, background: '#f8fafc', borderRadius: 8 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>拒绝占比</Text>
                  <div style={{ fontSize: 20, fontWeight: 650 }}>{rejectedRate}%</div>
                </div>
              </div>
            </Space>
          </Card>
        </Col>

        <Col xs={24} xl={16}>
          <Card title="原因分布" style={cardStyle} styles={{ body: { padding: 20 } }}>
            {quarantineReasons.length + exceptionReasons.length > 0 ? (
              <div style={{ display: 'grid', gap: 14 }}>
                {[
                  ...quarantineReasons.map(([reason, count]) => [`DLP: ${reason}`, count] as const),
                  ...exceptionReasons.map(([reason, count]) => [`异常: ${reason}`, count] as const),
                ].map(([reason, count]) => {
                  const percent = formatPercent(count, total + exceptionTotal);
                  return (
                    <div key={reason}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, marginBottom: 6 }}>
                        <Text ellipsis style={{ maxWidth: '70%' }}>{reason}</Text>
                        <Text strong>{count}</Text>
                      </div>
                      <div style={{ height: 10, background: '#e5e7eb', borderRadius: 999, overflow: 'hidden' }}>
                        <div
                          style={{
                            width: `${percent}%`,
                            height: '100%',
                            background: '#2563eb',
                            borderRadius: 999,
                          }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无异常或隔离数据" />
            )}
          </Card>
        </Col>

        <Col xs={24} xl={8}>
          <Card
            title="算法能力"
            extra={<SecurityScanOutlined style={{ color: '#2563eb' }} />}
            style={cardStyle}
            styles={{ body: { padding: 0 } }}
          >
            {algorithmList.map((item, index) => (
              <div
                key={item.title}
                style={{
                  padding: 16,
                  borderBottom: index === algorithmList.length - 1 ? 'none' : '1px solid #eef2f7',
                }}
              >
                <div style={{ display: 'flex', gap: 12 }}>
                  <div
                    style={{
                      width: 34,
                      height: 34,
                      borderRadius: 8,
                      display: 'grid',
                      placeItems: 'center',
                      background: '#eff6ff',
                      color: '#2563eb',
                      flex: '0 0 auto',
                    }}
                  >
                    {item.icon}
                  </div>
                  <div style={{ minWidth: 0 }}>
                    <Text strong>{item.title}</Text>
                    <div>
                      <Text type="secondary" style={{ fontSize: 12 }}>{item.description}</Text>
                    </div>
                    <Space size={[6, 6]} wrap style={{ marginTop: 8 }}>
                      {item.tags.map((tag) => (
                        <Tag key={tag.label} color={tag.color} style={{ margin: 0 }}>
                          {tag.label}
                        </Tag>
                      ))}
                    </Space>
                  </div>
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
