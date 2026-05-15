import React, { useState, useEffect } from 'react';
import {
  Table,
  Button,
  Typography,
  Space,
  Tag,
  message,
  Popconfirm,
  Select,
  Drawer,
  Descriptions,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import type { QuarantineItem } from '../types';
import { quarantineApi } from '../api/client';

const { Title } = Typography;
const { Option } = Select;

const Quarantine: React.FC = () => {
  const [data, setData] = useState<QuarantineItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedItem, setSelectedItem] = useState<QuarantineItem | null>(null);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const response = await quarantineApi.list({ page: 1, size: 50 });
      setData(response.data.data.items);
    } catch {
      message.error('加载隔离邮件列表失败');
    } finally {
      setLoading(false);
    }
  };

  const handleRelease = async (id: string) => {
    try {
      await quarantineApi.release(id);
      message.success('邮件已放行');
      loadData();
    } catch {
      message.error('操作失败');
    }
  };

  const handleReject = async (id: string) => {
    try {
      await quarantineApi.reject(id);
      message.success('邮件已拒绝');
      loadData();
    } catch {
      message.error('操作失败');
    }
  };

  const handleBatchRelease = async () => {
    if (selectedRowKeys.length === 0) return;
    try {
      await quarantineApi.batchRelease(selectedRowKeys as string[]);
      message.success(`已放行 ${selectedRowKeys.length} 封邮件`);
      setSelectedRowKeys([]);
      loadData();
    } catch {
      message.error('操作失败');
    }
  };

  const handleBatchReject = async () => {
    if (selectedRowKeys.length === 0) return;
    try {
      await quarantineApi.batchReject(selectedRowKeys as string[]);
      message.success(`已拒绝 ${selectedRowKeys.length} 封邮件`);
      setSelectedRowKeys([]);
      loadData();
    } catch {
      message.error('操作失败');
    }
  };

  const getReasonTag = (reason: string) => {
    const colorMap: Record<string, string> = {
      POLICY_VIOLATION: 'red',
      DECRYPTION_FAILED: 'orange',
      CERTIFICATE_MISSING: 'gold',
      SIGNATURE_INVALID: 'orange',
      ENCRYPTION_FAILED: 'red',
      SCAN_ERROR: 'purple',
      CERTIFICATE_REVOKED: 'red',
    };
    const labelMap: Record<string, string> = {
      POLICY_VIOLATION: '策略违规',
      DECRYPTION_FAILED: '解密失败',
      CERTIFICATE_MISSING: '缺少证书',
      SIGNATURE_INVALID: '签名无效',
      ENCRYPTION_FAILED: '加密失败',
      SCAN_ERROR: '扫描错误',
      CERTIFICATE_REVOKED: '证书已吊销',
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

  const columns = [
    {
      title: '主题',
      dataIndex: 'subject',
      key: 'subject',
      ellipsis: true,
    },
    {
      title: '发件人',
      dataIndex: 'sender',
      key: 'sender',
    },
    {
      title: '收件人',
      dataIndex: 'recipients',
      key: 'recipients',
      render: (addresses: string[]) => addresses?.join(', ') || '-',
    },
    {
      title: '隔离原因',
      key: 'reason',
      render: (_: any, record: QuarantineItem) => getReasonTag(record.reason),
    },
    {
      title: '状态',
      key: 'status',
      render: (_: any, record: QuarantineItem) => getStatusTag(record.status),
    },
    {
      title: '隔离时间',
      dataIndex: 'quarantinedAt',
      key: 'quarantinedAt',
      render: (date: string) => new Date(date).toLocaleString(),
    },
    {
      title: '操作',
      key: 'actions',
      render: (_: any, record: QuarantineItem) => (
        <Space>
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
          {record.status === 'QUARANTINED' && (
            <>
              <Popconfirm
                title="确定要放行此邮件吗？"
                onConfirm={() => handleRelease(record.id)}
                okText="确定"
                cancelText="取消"
              >
                <Button
                  type="link"
                  size="small"
                  icon={<CheckCircleOutlined />}
                >
                  放行
                </Button>
              </Popconfirm>
              <Popconfirm
                title="确定要拒绝此邮件吗？"
                onConfirm={() => handleReject(record.id)}
                okText="确定"
                cancelText="取消"
              >
                <Button
                  type="link"
                  size="small"
                  danger
                  icon={<CloseCircleOutlined />}
                >
                  拒绝
                </Button>
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ];

  const rowSelection = {
    selectedRowKeys,
    onChange: setSelectedRowKeys,
    getCheckboxProps: (record: QuarantineItem) => ({
      disabled: record.status !== 'QUARANTINED',
    }),
  };

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 24,
        }}
      >
        <Title level={3} style={{ margin: 0 }}>
          隔离邮件
        </Title>
        <Space>
          {selectedRowKeys.length > 0 && (
            <>
              <Button
                type="primary"
                icon={<CheckCircleOutlined />}
                onClick={handleBatchRelease}
              >
                批量放行 ({selectedRowKeys.length})
              </Button>
              <Button
                danger
                icon={<CloseCircleOutlined />}
                onClick={handleBatchReject}
              >
                批量拒绝 ({selectedRowKeys.length})
              </Button>
            </>
          )}
        </Space>
      </div>

      <div style={{ marginBottom: 16 }}>
        <Select placeholder="筛选原因" style={{ width: 200 }} allowClear>
          <Option value="POLICY_VIOLATION">策略违规</Option>
          <Option value="DECRYPTION_FAILED">解密失败</Option>
          <Option value="CERTIFICATE_MISSING">缺少证书</Option>
          <Option value="SIGNATURE_INVALID">签名无效</Option>
          <Option value="CERTIFICATE_REVOKED">证书已吊销</Option>
        </Select>
      </div>

      <Table
        columns={columns}
        dataSource={data}
        loading={loading}
        rowKey="id"
        rowSelection={rowSelection}
      />

      <Drawer
        title="邮件详情"
        width={600}
        open={detailVisible}
        onClose={() => setDetailVisible(false)}
      >
        {selectedItem && (
          <Descriptions column={1} bordered>
            <Descriptions.Item label="主题">
              {selectedItem.subject}
            </Descriptions.Item>
            <Descriptions.Item label="发件人">
              {selectedItem.sender}
            </Descriptions.Item>
            <Descriptions.Item label="收件人">
              {selectedItem.recipients?.join(', ')}
            </Descriptions.Item>
            <Descriptions.Item label="来源地址">
              {selectedItem.remoteAddress || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="隔离原因">
              {getReasonTag(selectedItem.reason)}
            </Descriptions.Item>
            <Descriptions.Item label="详情说明">
              {selectedItem.detail || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              {getStatusTag(selectedItem.status)}
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

export default Quarantine;
