import React from 'react';
import { Button, Popconfirm, Space, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  GlobalOutlined,
  LockOutlined,
  SafetyOutlined,
} from '@ant-design/icons';
import type { DomainConfig } from '../../types';
import { DataTable } from '../../components/Page';
import { algorithmPreferenceLabel, deliveryProfileLabel } from './domainConfigUtils';

interface DomainConfigTableProps {
  data: DomainConfig[];
  loading: boolean;
  onDelete: (id: string) => unknown | Promise<unknown>;
  onEdit: (domain: DomainConfig) => void;
  onView: (domain: DomainConfig) => void;
}

const DomainConfigTable: React.FC<DomainConfigTableProps> = ({
  data,
  loading,
  onDelete,
  onEdit,
  onView,
}) => {
  const columns: ColumnsType<DomainConfig> = [
    {
      title: '域名',
      dataIndex: 'domain',
      key: 'domain',
      render: (text: string, record) => (
        <Space>
          <GlobalOutlined />
          <strong>{text}</strong>
          {record.localDomain ? <Tag color="blue">本地</Tag> : null}
        </Space>
      ),
    },
    {
      title: '加密策略',
      dataIndex: 'encryptionPolicyDisplayName',
      key: 'encryptionPolicy',
      render: (text: string, record) => {
        const colorMap: Record<string, string> = {
          MANDATORY: 'red',
          ALLOW: 'green',
          NO_ENCRYPTION: 'default',
        };
        return (
          <Tag color={colorMap[record.encryptionPolicy] || 'default'}>
            <LockOutlined /> {text}
          </Tag>
        );
      },
    },
    {
      title: '算法偏好',
      dataIndex: 'preferredAlgorithmDisplayName',
      key: 'preferredAlgorithm',
      render: (_: string, record) => {
        const colorMap: Record<string, string> = {
          AUTO: 'blue',
          GM_ONLY: 'error',
          STANDARD_ONLY: 'processing',
        };
        return (
          <Tag color={colorMap[record.preferredAlgorithm || 'AUTO'] || 'default'}>
            {algorithmPreferenceLabel(record.preferredAlgorithm, record.preferredAlgorithmDisplayName)}
          </Tag>
        );
      },
    },
    {
      title: '签名',
      dataIndex: 'signingEnabled',
      key: 'signingEnabled',
      render: (enabled: boolean) =>
        enabled ? (
          <Tag color="success" icon={<SafetyOutlined />}>已启用</Tag>
        ) : (
          <Tag color="default">未启用</Tag>
        ),
    },
    {
      title: 'DKIM',
      dataIndex: 'dkimEnabled',
      key: 'dkimEnabled',
      render: (enabled: boolean) => (enabled ? <Tag color="success">已启用</Tag> : <Tag color="default">未启用</Tag>),
    },
    {
      title: '外部发送地址',
      key: 'deliveryRoute',
      render: (_, record) => {
        if (record.localDomain) {
          return <Tag color="default">本地</Tag>;
        }
        return record.deliveryHost && record.deliveryPort ? (
          <Space direction="vertical" size={2}>
            <Tag color="processing">{record.deliveryHost}:{record.deliveryPort}</Tag>
            <Tag color="default">
              {deliveryProfileLabel(record.deliveryTransportProfile, record.deliveryTransportProfileDisplayName)}
            </Tag>
          </Space>
        ) : (
          <Tag color="default">默认</Tag>
        );
      },
    },
    {
      title: '解密模式',
      dataIndex: 'decryptionModeDisplayName',
      key: 'decryptionMode',
      render: (_: string, record) => (
        <Tag color={record.decryptionMode === 'END_TO_END_PASSTHROUGH' ? 'warning' : 'success'}>
          {record.decryptionModeDisplayName || '网关代理解密'}
        </Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'active',
      key: 'active',
      render: (active: boolean) => (active ? <Tag color="green">启用</Tag> : <Tag color="red">禁用</Tag>),
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => (
        <Space size="small">
          <Button type="text" icon={<EyeOutlined />} size="small" onClick={() => onView(record)}>
            详情
          </Button>
          <Button type="text" icon={<EditOutlined />} size="small" onClick={() => onEdit(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确认删除该域名配置？"
            onConfirm={() => onDelete(record.id)}
            okText="确认"
            cancelText="取消"
          >
            <Button type="text" danger icon={<DeleteOutlined />} size="small">
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <DataTable
      columns={columns}
      dataSource={data}
      rowKey="id"
      loading={loading}
      pagination={{ pageSize: 10 }}
    />
  );
};

export default DomainConfigTable;
