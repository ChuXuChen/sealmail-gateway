import React from 'react';
import { Button, Tag, Typography } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { MailAuthDnsProbe } from '../../types';
import { DataTable, formatDateTime } from '../../components/Page';

const { Text } = Typography;

interface MailAuthProbeTableProps {
  loading: boolean;
  probes: MailAuthDnsProbe[];
  onProbe: () => void | Promise<void>;
}

const statusColor = (status: string) => {
  if (status === 'MATCH') return 'green';
  if (status === 'MISMATCH') return 'orange';
  if (status === 'MISSING') return 'red';
  if (status === 'TEMP_ERROR') return 'volcano';
  return 'default';
};

const columns: ColumnsType<MailAuthDnsProbe> = [
  {
    title: '类型',
    dataIndex: 'recordType',
    width: 96,
    render: (value: string) => <Tag>{value}</Tag>,
  },
  {
    title: '记录名',
    dataIndex: 'expectedName',
    ellipsis: true,
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 120,
    render: (value: string) => <Tag color={statusColor(value)}>{value}</Tag>,
  },
  {
    title: '最近检查',
    dataIndex: 'checkedAt',
    width: 180,
    render: (value: string) => formatDateTime(value),
  },
  {
    title: '详情',
    dataIndex: 'detail',
    render: (value?: string) => <Text type="secondary">{value || '-'}</Text>,
  },
];

const MailAuthProbeTable: React.FC<MailAuthProbeTableProps> = ({ loading, probes, onProbe }) => (
  <DataTable<MailAuthDnsProbe>
    rowKey={(record) => `${record.recordType}-${record.expectedName}-${record.checkedAt}`}
    columns={columns}
    dataSource={probes}
    loading={loading}
    pagination={false}
    title={() => (
      <Button icon={<SearchOutlined />} loading={loading} onClick={onProbe}>
        探测 DNS
      </Button>
    )}
  />
);

export default MailAuthProbeTable;
