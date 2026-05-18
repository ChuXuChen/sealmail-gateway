import type React from 'react';
import { Col, Empty, Progress, Row, Tag, Typography } from 'antd';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  SafetyCertificateOutlined,
  SecurityScanOutlined,
  StopOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import CryptoCapabilityTags from '../settings/CryptoCapabilityTags';
import { cryptoCapabilityIcon } from '../settings/cryptoCapabilityPresentation';
import type { DashboardViewModel } from './dashboardModel';
import type { SystemSettings } from '../../types';

const { Text } = Typography;

interface DashboardPanelsProps {
  cryptoCapabilities?: SystemSettings['cryptoCapabilities'];
  viewModel: DashboardViewModel;
}

const capabilityDescriptions: Record<string, string> = {
  'S/MIME STANDARD 套件': '标准 S/MIME 默认与可用套件',
  'S/MIME GM 套件': '国密 S/MIME 默认与可用套件',
  签名算法: '出站签名 / 入站验签',
  密钥交换: '收件人密钥封装',
  哈希算法: '摘要与签名散列',
};

const DashboardPanels: React.FC<DashboardPanelsProps> = ({ cryptoCapabilities, viewModel }) => {
  const handlingItems = [
    {
      label: '待处理',
      count: viewModel.pending,
      percent: viewModel.pendingRate,
      status: 'active' as const,
      tone: 'warning',
      icon: <ClockCircleOutlined />,
    },
    {
      label: '放行恢复',
      count: viewModel.released,
      percent: viewModel.releasedRate,
      status: 'success' as const,
      tone: 'success',
      icon: <CheckCircleOutlined />,
    },
    {
      label: '异常阻断',
      count: viewModel.exceptionTotal,
      percent: viewModel.exceptionRate,
      status: 'normal' as const,
      tone: 'info',
      icon: <StopOutlined />,
    },
    {
      label: '人工拒绝',
      count: viewModel.rejected,
      percent: viewModel.rejectedRate,
      status: 'exception' as const,
      tone: 'danger',
      icon: <WarningOutlined />,
    },
  ];

  const flowItems = [
    {
      label: '隔离总量',
      value: viewModel.quarantineTotal,
      meta: 'DLP 命中后进入隔离',
      tone: 'info',
    },
    {
      label: '释放中',
      value: viewModel.releasing,
      meta: `${viewModel.releasingRate}% 正在恢复投递`,
      tone: 'neutral',
    },
    {
      label: '已处置',
      value: viewModel.handled,
      meta: `${viewModel.completionRate}% 队列完成率`,
      tone: 'success',
    },
    {
      label: '异常阻断',
      value: viewModel.exceptionTotal,
      meta: '策略自动截停',
      tone: 'danger',
    },
  ];

  const distribution = viewModel.distribution.slice(0, 6);

  return (
    <Row gutter={[16, 16]} className="dashboard-panels">
    <Col xs={24} xl={15}>
      <section className="dashboard-panel dashboard-panel--flow" aria-label="邮件处置">
        <div className="dashboard-panel__head">
          <div>
            <h2 className="dashboard-panel__title">邮件处置</h2>
            <Text className="dashboard-panel__description">隔离、放行、拒绝与异常阻断的实时分布。</Text>
          </div>
          <Tag color={viewModel.pending > 0 ? 'warning' : 'success'}>{viewModel.completionRate}% 已处理</Tag>
        </div>

        <div className="dashboard-flow">
          {flowItems.map((item) => (
            <div className={`dashboard-flow__item dashboard-flow__item--${item.tone}`} key={item.label}>
              <Text className="dashboard-flow__label">{item.label}</Text>
              <Text className="dashboard-flow__value">{item.value}</Text>
              <Text className="dashboard-flow__meta">{item.meta}</Text>
            </div>
          ))}
        </div>

        <div className="dashboard-breakdown">
          {handlingItems.map((item) => (
            <div className="dashboard-breakdown__row" key={item.label}>
              <div className="dashboard-breakdown__head">
                <span className={`dashboard-breakdown__icon dashboard-breakdown__icon--${item.tone}`}>
                  {item.icon}
                </span>
                <Text className="dashboard-breakdown__label">{item.label}</Text>
                <Text className="dashboard-breakdown__count">{item.count}</Text>
              </div>
              <Progress percent={item.percent} showInfo={false} status={item.status} />
            </div>
          ))}
        </div>
      </section>
    </Col>

    <Col xs={24} xl={9}>
      <section className="dashboard-panel" aria-label="队列状态">
        <div className="dashboard-panel__head">
          <div>
            <h2 className="dashboard-panel__title">队列状态</h2>
            <Text className="dashboard-panel__description">待办压力与处置吞吐。</Text>
          </div>
        </div>

        <div className={`dashboard-queue-status dashboard-queue-status--${viewModel.pending > 0 ? 'warning' : 'ok'}`}>
          <span className="dashboard-queue-status__icon">
            {viewModel.pending > 0 ? <WarningOutlined /> : <SafetyCertificateOutlined />}
          </span>
          <div className="dashboard-queue-status__body">
            <Text className="dashboard-queue-status__title">
              {viewModel.pending > 0 ? '存在待处理邮件' : '当前无积压'}
            </Text>
            <Text className="dashboard-queue-status__copy">
              {viewModel.pending > 0 ? `${viewModel.pending} 封邮件等待处置` : '待处理队列已处理完毕'}
            </Text>
          </div>
        </div>

        <div className="dashboard-health">
          <div className="dashboard-health__head">
            <Text>运行健康度</Text>
            <Text strong>{viewModel.healthPercent}%</Text>
          </div>
          <Progress percent={viewModel.healthPercent} status={viewModel.pending > 0 ? 'active' : 'success'} showInfo={false} />
        </div>

        <div className="dashboard-mini-stats">
          <div className="dashboard-mini-stat">
            <Text className="dashboard-mini-stat__label">隔离总量</Text>
            <Text className="dashboard-mini-stat__value">{viewModel.quarantineTotal}</Text>
          </div>
          <div className="dashboard-mini-stat">
            <Text className="dashboard-mini-stat__label">已处置</Text>
            <Text className="dashboard-mini-stat__value">{viewModel.handled}</Text>
          </div>
        </div>
      </section>
    </Col>

    <Col xs={24} xl={15}>
      <section className="dashboard-panel" aria-label="原因分布">
        <div className="dashboard-panel__head">
          <div>
            <h2 className="dashboard-panel__title">原因分布</h2>
            <Text className="dashboard-panel__description">按命中原因排序，便于定位主要风险来源。</Text>
          </div>
          {viewModel.distribution.length > distribution.length ? (
            <Tag>{distribution.length} / {viewModel.distribution.length}</Tag>
          ) : null}
        </div>

        {distribution.length > 0 ? (
          <div className="dashboard-distribution">
            {distribution.map((item) => (
              <div className="dashboard-distribution__row" key={item.label}>
                <div className="dashboard-distribution__head">
                  <div className="dashboard-distribution__label-wrap">
                    <Tag color={item.tone === 'exception' ? 'error' : 'processing'}>
                      {item.tone === 'exception' ? '异常' : 'DLP'}
                    </Tag>
                    <Text className="dashboard-distribution__label" ellipsis>{item.label}</Text>
                  </div>
                  <Text className="dashboard-distribution__count">{item.count}</Text>
                </div>
                <Progress
                  percent={item.percent}
                  showInfo={false}
                  status={item.tone === 'exception' ? 'exception' : 'normal'}
                />
              </div>
            ))}
          </div>
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无异常或隔离数据" />
        )}
      </section>
    </Col>

    <Col xs={24} xl={9}>
      <section className="dashboard-panel" aria-label="算法能力">
        <div className="dashboard-panel__head">
          <div>
            <h2 className="dashboard-panel__title">算法能力</h2>
            <Text className="dashboard-panel__description">S/MIME 兼容能力与国密能力。</Text>
          </div>
          <SecurityScanOutlined className="dashboard-panel__head-icon" />
        </div>
        <div className="dashboard-algorithms">
          {cryptoCapabilities?.length ? cryptoCapabilities.map((capability) => (
            <div className="dashboard-algorithm" key={capability.category}>
              <span className="dashboard-algorithm__icon">{cryptoCapabilityIcon(capability)}</span>
              <div className="dashboard-algorithm__body">
                <Text className="dashboard-algorithm__title">{capability.category}</Text>
                <Text className="dashboard-algorithm__description">
                  {capabilityDescriptions[capability.category] || '当前运行配置返回的能力'}
                </Text>
                <div className="dashboard-algorithm__tags">
                  <CryptoCapabilityTags capabilities={[capability]} includeCategory={false} />
                </div>
              </div>
            </div>
          )) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无算法能力数据" />
          )}
        </div>
      </section>
    </Col>
  </Row>
  );
};

export default DashboardPanels;
