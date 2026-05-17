import type React from 'react';
import { Button, Dropdown, Space } from 'antd';
import { DownOutlined, EyeOutlined } from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import type { QuarantineItem } from '../../types';
import {
  DataTable,
  QuarantineStatusTag,
  ReasonTag,
  createListPagination,
  formatDateTime,
} from '../../components/Page';
import { canRelease, getActionItems, isDlpQuarantineActionKey } from './dlpQuarantineUtils';

interface DlpQuarantineTableProps {
  data: QuarantineItem[];
  loading: boolean;
  pagination: { page: number; size: number; total: number };
  selectedRowKeys: React.Key[];
  onAction: (key: string, record: QuarantineItem) => void;
  onPaginationChange: (page: number, size: number) => void;
  onSelectionChange: (keys: React.Key[]) => void;
  onView: (record: QuarantineItem) => void;
}

const DlpQuarantineTable: React.FC<DlpQuarantineTableProps> = ({
  data,
  loading,
  pagination,
  selectedRowKeys,
  onAction,
  onPaginationChange,
  onSelectionChange,
  onView,
}) => {
  const columns: TableColumnsType<QuarantineItem> = [
    {
      title: '主题',
      dataIndex: 'subject',
      key: 'subject',
      width: 220,
      ellipsis: true,
    },
    {
      title: '发件人',
      dataIndex: 'sender',
      key: 'sender',
      width: 220,
      ellipsis: true,
    },
    {
      title: '收件人',
      dataIndex: 'recipients',
      key: 'recipients',
      width: 260,
      ellipsis: true,
      render: (addresses: string[]) => addresses?.join(', ') || '-',
    },
    {
      title: '隔离原因',
      key: 'reason',
      width: 120,
      render: (_, record) => <ReasonTag reason={record.reason} />,
    },
    {
      title: '状态',
      key: 'status',
      width: 100,
      render: (_, record) => <QuarantineStatusTag status={record.status} />,
    },
    {
      title: '隔离时间',
      dataIndex: 'quarantinedAt',
      key: 'quarantinedAt',
      width: 180,
      render: formatDateTime,
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      fixed: 'right',
      render: (_, record) => {
        const isActionable = record.status === 'QUARANTINED' || record.status === 'RELEASING';
        const releaseDisabled = !canRelease(record);

        return (
          <Space size={4} wrap={false}>
            <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => onView(record)}>
              查看
            </Button>
            {isActionable && (
              <Dropdown
                trigger={['click']}
                menu={{
                  items: getActionItems(record, releaseDisabled),
                  onClick: ({ key }) => {
                    if (isDlpQuarantineActionKey(key)) {
                      onAction(key, record);
                    }
                  },
                }}
              >
                <Button size="small">
                  处理 <DownOutlined />
                </Button>
              </Dropdown>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <DataTable<QuarantineItem>
      columns={columns}
      dataSource={data}
      loading={loading}
      rowKey="id"
      rowSelection={{
        selectedRowKeys,
        onChange: onSelectionChange,
        getCheckboxProps: (record) => ({
          disabled: record.status !== 'QUARANTINED',
        }),
      }}
      scroll={{ x: 1260 }}
      pagination={createListPagination(pagination, onPaginationChange)}
    />
  );
};

export default DlpQuarantineTable;
