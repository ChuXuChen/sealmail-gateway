import React, { useMemo } from 'react';
import { Button, Dropdown, Space, Typography } from 'antd';
import type { MenuProps } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  DeleteOutlined,
  DownOutlined,
  EyeOutlined,
  LockOutlined,
  UnlockOutlined,
} from '@ant-design/icons';
import type { Certificate } from '../../types';
import { DataTable } from '../../components/Page';
import type { CaFilters, CaTableRecord, ConfirmActionType } from './caUtils';
import { formatDate, getDisplayName } from './caUtils';
import CaStatusTags, { CaAlgorithmTag, CaRoleTag } from './CaStatusTags';
import { buildCaFilterHeaders } from './CaFilterControls';

const { Text } = Typography;

interface CaTableProps {
  algorithmOptions: { value: string; label: string }[];
  data: CaTableRecord[];
  expandedRowKeys: React.Key[];
  filters: CaFilters;
  loading: boolean;
  roots: Certificate[];
  onConfirmAction: (type: ConfirmActionType, record: Certificate) => void;
  onResetFilters: () => void;
  onTrust: (id: string) => unknown | Promise<unknown>;
  onUpdateFilter: <K extends keyof CaFilters>(key: K, value: CaFilters[K]) => void;
  onView: (record: Certificate) => void;
}

const getRowActionItems = (record: CaTableRecord): MenuProps['items'] => [
  record.trusted
    ? {
      key: 'untrust',
      icon: <LockOutlined />,
      label: '撤销信任',
    }
    : {
      key: 'trust',
      icon: <UnlockOutlined />,
      label: '标记信任',
    },
  !record.revoked
    ? {
      key: 'revoke',
      icon: <LockOutlined />,
      danger: true,
      label: '吊销',
    }
    : null,
  {
    key: 'delete',
    icon: <DeleteOutlined />,
    danger: true,
    label: '删除',
  },
].filter(Boolean) as MenuProps['items'];

const CaTable: React.FC<CaTableProps> = ({
  algorithmOptions,
  data,
  expandedRowKeys,
  filters,
  loading,
  roots,
  onConfirmAction,
  onResetFilters,
  onTrust,
  onUpdateFilter,
  onView,
}) => {
  const filterHeaders = useMemo(
    () => buildCaFilterHeaders({
      algorithmOptions,
      filters,
      roots,
      onReset: onResetFilters,
      onUpdateFilter,
    }),
    [algorithmOptions, filters, onResetFilters, onUpdateFilter, roots],
  );

  const handleRowActionClick = (key: string, record: CaTableRecord) => {
    if (key === 'trust') {
      void onTrust(record.id);
      return;
    }
    if (key === 'untrust' || key === 'revoke' || key === 'delete') {
      onConfirmAction(key, record);
    }
  };

  const columns: ColumnsType<CaTableRecord> = [
    {
      title: filterHeaders.ca,
      key: 'name',
      render: (_, record) => (
        <Space direction="vertical" size={2} className="min-width-zero">
          <Space size={6} wrap>
            <Text strong ellipsis className="ca-name">
              {getDisplayName(record)}
            </Text>
            <CaRoleTag cert={record} />
          </Space>
        </Space>
      ),
    },
    {
      title: filterHeaders.algorithm,
      dataIndex: 'algorithm',
      render: (algorithm) => <CaAlgorithmTag algorithm={algorithm} />,
      width: 120,
    },
    {
      title: filterHeaders.status,
      key: 'status',
      render: (_, record) => <CaStatusTags cert={record} />,
      width: 340,
    },
    {
      title: '有效期至',
      key: 'validity',
      render: (_, record) => formatDate(record.notAfter),
      width: 130,
    },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      width: 150,
      render: (_, record) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => onView(record)}>
            查看
          </Button>
          <Dropdown
            trigger={['click']}
            menu={{
              items: getRowActionItems(record),
              onClick: ({ key }) => handleRowActionClick(key, record),
            }}
          >
            <Button type="link" size="small">
              更多 <DownOutlined />
            </Button>
          </Dropdown>
        </Space>
      ),
    },
  ];

  return (
    <DataTable
      columns={columns}
      dataSource={data}
      loading={loading}
      rowKey="id"
      size="small"
      expandable={{ expandedRowKeys }}
      scroll={{ x: 1160 }}
      pagination={{ pageSize: 10 }}
    />
  );
};

export default CaTable;
