import type React from 'react';
import { Space, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import type { AuditLog } from '../../types';
import { AuditResultTag, DataTable, createListPagination } from '../../components/Page';
import { formatAuditTime, shortResource } from './auditLogUtils';

const { Text } = Typography;

interface AuditLogTableProps {
  data: AuditLog[];
  loading: boolean;
  pagination: { page: number; size: number; total: number };
  onPageChange: (page: number, size: number) => void;
  onSelect: (record: AuditLog) => void;
}

const columns: TableColumnsType<AuditLog> = [
  {
    title: '时间',
    dataIndex: 'occurredAt',
    key: 'occurredAt',
    render: (time: string) => <Text type="secondary">{formatAuditTime(time)}</Text>,
    width: 180,
  },
  {
    title: '事件',
    key: 'event',
    render: (_, record) => (
      <Space direction="vertical" size={0}>
        <Text strong>{record.typeDisplayName || record.type}</Text>
        <Text type="secondary" className="text-micro">{record.action || record.type}</Text>
      </Space>
    ),
    width: 180,
  },
  {
    title: '用户与来源',
    key: 'actor',
    render: (_, record) => (
      <Space direction="vertical" size={0}>
        <Text>{record.username || '-'}</Text>
        <Text type="secondary" className="text-micro mono-small">{record.ipAddress || '-'}</Text>
      </Space>
    ),
    width: 160,
  },
  {
    title: '对象',
    key: 'resource',
    render: (_, record) => (
      <Text type="secondary">{shortResource(record)}</Text>
    ),
    width: 160,
  },
  {
    title: '详情',
    dataIndex: 'detail',
    key: 'detail',
    ellipsis: true,
    render: (detail: string, record) => detail || record.errorMessage || '-',
  },
  {
    title: '结果',
    key: 'success',
    render: (_, record) => <AuditResultTag success={record.success} />,
    width: 90,
  },
];

const AuditLogTable: React.FC<AuditLogTableProps> = ({
  data,
  loading,
  pagination,
  onPageChange,
  onSelect,
}) => (
  <DataTable<AuditLog>
    columns={columns}
    dataSource={data}
    rowKey="id"
    loading={loading}
    size="middle"
    onRow={(record) => ({
      onClick: () => onSelect(record),
      style: { cursor: 'pointer' },
    })}
    pagination={createListPagination(pagination, onPageChange)}
  />
);

export default AuditLogTable;
