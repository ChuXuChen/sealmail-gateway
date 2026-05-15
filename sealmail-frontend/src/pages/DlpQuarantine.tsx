import React, { useEffect, useState } from 'react';
import {
  Table,
  Button,
  Dropdown,
  Modal,
  Typography,
  Space,
  Tag,
  message,
  Select,
  Drawer,
  Descriptions,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  DownOutlined,
  EyeOutlined,
  LockOutlined,
} from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { QuarantineItem } from '../types';
import { dlpQuarantineApi } from '../api/client';

const { Title } = Typography;
const { Option } = Select;

const getErrorMessage = (error: unknown, fallback: string) => {
  if (
    typeof error === 'object' &&
    error !== null &&
    'response' in error &&
    typeof (error as { response?: { data?: { message?: unknown } } }).response?.data?.message === 'string'
  ) {
    return (error as { response: { data: { message: string } } }).response.data.message;
  }
  return fallback;
};

const DlpQuarantine: React.FC = () => {
  const [data, setData] = useState<QuarantineItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ page: 1, size: 20, total: 0 });
  const [reasonFilter, setReasonFilter] = useState<string | undefined>();
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedItem, setSelectedItem] = useState<QuarantineItem | null>(null);

  useEffect(() => {
    loadData();
  }, [pagination.page, pagination.size, reasonFilter]);

  const loadData = async () => {
    setLoading(true);
    try {
      const response = await dlpQuarantineApi.list({
        page: pagination.page,
        size: pagination.size,
        reason: reasonFilter,
      });
      setData(response.data.data.items);
      setPagination((prev) => ({
        ...prev,
        total: response.data.data.total,
      }));
      setSelectedRowKeys((prev) => {
        const visibleIds = new Set(response.data.data.items.map((item) => item.id));
        return prev.filter((id) => visibleIds.has(String(id)));
      });
    } catch {
      message.error('加载 DLP 隔离邮件失败');
    } finally {
      setLoading(false);
    }
  };

  const handleRelease = async (id: string, encryptBeforeRelease = false) => {
    try {
      await dlpQuarantineApi.release(id, { encryptBeforeRelease });
      message.success(encryptBeforeRelease ? '邮件已加密后放行' : '邮件已放行');
      loadData();
    } catch (error) {
      message.error(getErrorMessage(error, '操作失败'));
    }
  };

  const handleReject = async (id: string) => {
    try {
      await dlpQuarantineApi.reject(id);
      message.success('邮件已拒绝');
      loadData();
    } catch (error) {
      message.error(getErrorMessage(error, '操作失败'));
    }
  };

  const handleBatchRelease = async () => {
    const releasableIds = data
      .filter((item) => selectedRowKeys.includes(item.id) && canRelease(item))
      .map((item) => item.id);
    if (releasableIds.length === 0) return;
    try {
      await dlpQuarantineApi.batchRelease(releasableIds);
      message.success(`已放行 ${releasableIds.length} 封邮件`);
      setSelectedRowKeys([]);
      loadData();
    } catch (error) {
      message.error(getErrorMessage(error, '操作失败'));
    }
  };

  const handleBatchReject = async () => {
    if (selectedRowKeys.length === 0) return;
    try {
      await dlpQuarantineApi.batchReject(selectedRowKeys as string[]);
      message.success(`已拒绝 ${selectedRowKeys.length} 封邮件`);
      setSelectedRowKeys([]);
      loadData();
    } catch (error) {
      message.error(getErrorMessage(error, '操作失败'));
    }
  };

  const canRelease = (record: QuarantineItem) =>
    record.status === 'QUARANTINED' && record.canRelease !== false;

  const confirmRelease = (record: QuarantineItem, encryptBeforeRelease = false) => {
    Modal.confirm({
      title: encryptBeforeRelease ? '确定要加密后放行此邮件吗？' : '确定要直接放行此邮件吗？',
      okText: '确定',
      cancelText: '取消',
      onOk: () => handleRelease(record.id, encryptBeforeRelease),
    });
  };

  const confirmReject = (record: QuarantineItem) => {
    Modal.confirm({
      title: '确定要拒绝此邮件吗？',
      okText: '确定',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => handleReject(record.id),
    });
  };

  const getReasonTag = (reason: string) => {
    const colorMap: Record<string, string> = {
      POLICY_VIOLATION: 'red',
      CERTIFICATE_MISSING: 'gold',
      ENCRYPTION_FAILED: 'red',
      SCAN_ERROR: 'purple',
    };
    const labelMap: Record<string, string> = {
      POLICY_VIOLATION: '策略违规',
      CERTIFICATE_MISSING: '缺少证书',
      ENCRYPTION_FAILED: '加密失败',
      SCAN_ERROR: '扫描错误',
    };
    return <Tag color={colorMap[reason] || 'default'}>{labelMap[reason] || reason}</Tag>;
  };

  const getStatusTag = (status: string) => {
    const colorMap: Record<string, string> = {
      QUARANTINED: 'gold',
      RELEASED: 'green',
      REJECTED: 'red',
    };
    const labelMap: Record<string, string> = {
      QUARANTINED: '待处理',
      RELEASED: '已放行',
      REJECTED: '已拒绝',
    };
    return <Tag color={colorMap[status] || 'default'}>{labelMap[status] || status}</Tag>;
  };

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
      render: (_: unknown, record: QuarantineItem) => getReasonTag(record.reason),
    },
    {
      title: '状态',
      key: 'status',
      width: 100,
      render: (_: unknown, record: QuarantineItem) => getStatusTag(record.status),
    },
    {
      title: '隔离时间',
      dataIndex: 'quarantinedAt',
      key: 'quarantinedAt',
      width: 180,
      render: (date: string) => new Date(date).toLocaleString(),
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      fixed: 'right',
      render: (_: unknown, record: QuarantineItem) => {
        const isQuarantined = record.status === 'QUARANTINED';
        const releaseDisabled = !canRelease(record);

        return (
          <Space size={4} wrap={false}>
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => {
                setSelectedItem(record);
                setDetailVisible(true);
              }}
            >
              查看
            </Button>
            {isQuarantined && (
              <Dropdown
                trigger={['click']}
                menu={{
                  items: [
                    {
                      key: 'release',
                      icon: <CheckCircleOutlined />,
                      label: releaseDisabled
                        ? record.releaseUnavailableReason || '不可放行'
                        : '直接放行',
                      disabled: releaseDisabled,
                      onClick: () => confirmRelease(record),
                    },
                    {
                      key: 'release-encrypted',
                      icon: <LockOutlined />,
                      label: releaseDisabled
                        ? record.releaseUnavailableReason || '不可加密放行'
                        : '加密放行',
                      disabled: releaseDisabled,
                      onClick: () => confirmRelease(record, true),
                    },
                    {
                      type: 'divider',
                    },
                    {
                      key: 'reject',
                      danger: true,
                      icon: <CloseCircleOutlined />,
                      label: '拒绝',
                      onClick: () => confirmReject(record),
                    },
                  ],
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

  const rowSelection = {
    selectedRowKeys,
    onChange: setSelectedRowKeys,
    getCheckboxProps: (record: QuarantineItem) => ({
      disabled: record.status !== 'QUARANTINED',
    }),
  };

  const selectedReleaseCount = data.filter((item) =>
    selectedRowKeys.includes(item.id) && canRelease(item)
  ).length;

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Title level={3} style={{ margin: 0 }}>
          DLP 隔离邮件
        </Title>
        <Space>
          {selectedRowKeys.length > 0 && (
            <>
              <Button
                type="primary"
                icon={<CheckCircleOutlined />}
                onClick={handleBatchRelease}
                disabled={selectedReleaseCount === 0}
              >
                批量放行 ({selectedReleaseCount})
              </Button>
              <Button danger icon={<CloseCircleOutlined />} onClick={handleBatchReject}>
                批量拒绝 ({selectedRowKeys.length})
              </Button>
            </>
          )}
        </Space>
      </div>

      <div style={{ marginBottom: 16 }}>
        <Select
          placeholder="筛选原因"
          style={{ width: 200 }}
          allowClear
          value={reasonFilter}
          onChange={(value) => {
            setReasonFilter(value || undefined);
            setPagination((prev) => ({ ...prev, page: 1 }));
          }}
        >
          <Option value="POLICY_VIOLATION">策略违规</Option>
          <Option value="CERTIFICATE_MISSING">缺少证书</Option>
          <Option value="ENCRYPTION_FAILED">加密失败</Option>
          <Option value="SCAN_ERROR">扫描错误</Option>
        </Select>
      </div>

      <Table
        columns={columns}
        dataSource={data}
        loading={loading}
        rowKey="id"
        rowSelection={rowSelection}
        scroll={{ x: 1260 }}
        pagination={{
          current: pagination.page,
          pageSize: pagination.size,
          total: pagination.total,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (total) => `共 ${total} 条`,
          onChange: (page, size) => setPagination((prev) => ({ ...prev, page, size })),
        }}
      />

      <Drawer
        title="DLP 隔离邮件详情"
        width={600}
        open={detailVisible}
        onClose={() => {
          setDetailVisible(false);
          setSelectedItem(null);
        }}
      >
        {selectedItem && (
          <Descriptions column={1} bordered>
            <Descriptions.Item label="主题">{selectedItem.subject}</Descriptions.Item>
            <Descriptions.Item label="发件人">{selectedItem.sender}</Descriptions.Item>
            <Descriptions.Item label="收件人">
              {selectedItem.recipients?.join(', ') || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="方向">{selectedItem.direction || '-'}</Descriptions.Item>
            <Descriptions.Item label="来源地址">{selectedItem.remoteAddress || '-'}</Descriptions.Item>
            <Descriptions.Item label="隔离原因">{getReasonTag(selectedItem.reason)}</Descriptions.Item>
            <Descriptions.Item label="详情说明">{selectedItem.detail || '-'}</Descriptions.Item>
            <Descriptions.Item label="状态">{getStatusTag(selectedItem.status)}</Descriptions.Item>
            <Descriptions.Item label="可放行">
              {canRelease(selectedItem) ? (
                <Tag color="success">可以放行</Tag>
              ) : (
                <Space direction="vertical" size={4}>
                  <Tag color="default">不可放行</Tag>
                  <span>{selectedItem.releaseUnavailableReason || '当前状态不可放行'}</span>
                </Space>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="隔离时间">
              {new Date(selectedItem.quarantinedAt).toLocaleString()}
            </Descriptions.Item>
            {selectedItem.resolvedBy && (
              <Descriptions.Item label={selectedItem.status === 'RELEASED' ? '放行操作' : '拒绝操作'}>
                由 {selectedItem.resolvedBy}
                {selectedItem.resolvedAt && ` 于 ${new Date(selectedItem.resolvedAt).toLocaleString()}`}
                {selectedItem.status === 'RELEASED' ? ' 放行' : ' 拒绝'}
                {selectedItem.resolutionComment && ` (${selectedItem.resolutionComment})`}
              </Descriptions.Item>
            )}
          </Descriptions>
        )}
      </Drawer>
    </div>
  );
};

export default DlpQuarantine;
