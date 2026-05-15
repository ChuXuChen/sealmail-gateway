import React, { useEffect, useState } from 'react';
import {
  Card,
  Row,
  Col,
  Statistic,
  Typography,
  Spin,
  message,
} from 'antd';
import {
  InboxOutlined,
  CheckCircleOutlined,
  StopOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { quarantineApi } from '../api/client';
import type { QuarantineStats } from '../types';

const { Title } = Typography;

const Dashboard: React.FC = () => {
  const [stats, setStats] = useState<QuarantineStats | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadStats();
  }, []);

  const loadStats = async () => {
    try {
      const response = await quarantineApi.getStats();
      setStats(response.data.data);
    } catch {
      message.error('加载统计数据失败');
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 100 }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div>
      <Title level={3} style={{ marginBottom: 24 }}>
        仪表盘
      </Title>

      <Row gutter={16}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="邮件总数"
              value={stats?.total || 0}
              prefix={<InboxOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="待处理邮件"
              value={stats?.pending || 0}
              prefix={<SafetyCertificateOutlined />}
              valueStyle={{ color: '#faad14' }}
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="已放行邮件"
              value={stats?.released || 0}
              prefix={<CheckCircleOutlined />}
              valueStyle={{ color: '#52c41a' }}
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="已拒绝邮件"
              value={stats?.rejected || 0}
              prefix={<StopOutlined />}
              valueStyle={{ color: '#ff4d4f' }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} style={{ marginTop: 24 }}>
        <Col span={24}>
          <Card title="隔离原因分布">
            {Object.entries(stats?.byReason || {}).map(([reason, count]) => (
              <div key={reason} style={{ marginBottom: 8 }}>
                <span style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span>{reason}</span>
                  <span>{count}</span>
                </span>
              </div>
            ))}
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Dashboard;
