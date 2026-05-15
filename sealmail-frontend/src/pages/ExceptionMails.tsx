import React, { useEffect, useState } from 'react';
import {
  Table,
  Typography,
  Space,
  Tag,
  message,
  Select,
  Drawer,
  Descriptions,
  Button,
} from 'antd';
import { EyeOutlined } from '@ant-design/icons';
import { ExceptionMailItem } from '../types';
import { exceptionMailApi } from '../api/client';

const { Title } = Typography;
const { Option } = Select;

const getReasonTag = (reason: string) => {
  const colorMap: Record<string, string> = {
    POLICY_VIOLATION: 'red',
    DECRYPTION_FAILED: 'orange',
    CERTIFICATE_MISSING: 'gold',
    SIGNATURE_INVALID: 'orange',
    ENCRYPTION_FAILED: 'red',
    EMAIL_AUTH_FAILED: 'volcano',
    DOMAIN_NOT_CONFIGURED: 'orange',
    SCAN_ERROR: 'purple',
    CERTIFICATE_REVOKED: 'red',
  };
  const labelMap: Record<string, string> = {
    POLICY_VIOLATION: '策略违规',
    DECRYPTION_FAILED: '解密失败',
    CERTIFICATE_MISSING: '缺少证书',
    SIGNATURE_INVALID: '签名无效',
    ENCRYPTION_FAILED: '加密失败',
    EMAIL_AUTH_FAILED: '认证失败',
    DOMAIN_NOT_CONFIGURED: '域名未配置',
    SCAN_ERROR: '扫描错误',
    CERTIFICATE_REVOKED: '证书已吊销',
  };
  return <Tag color={colorMap[reason] || 'default'}>{labelMap[reason] || reason}</Tag>;
};

const ExceptionMails: React.FC = () => {
  const [data, setData] = useState<ExceptionMailItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ page: 1, size: 20, total: 0 });
  const [reasonFilter, setReasonFilter] = useState<string | undefined>();
  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedItem, setSelectedItem] = useState<ExceptionMailItem | null>(null);

  useEffect(() => {
    loadData();
  }, [pagination.page, pagination.size, reasonFilter]);

  const loadData = async () => {
    setLoading(true);
    try {
      const response = await exceptionMailApi.list({
        page: pagination.page,
        size: pagination.size,
        reason: reasonFilter,
      });
      setData(response.data.data.items);
      setPagination((prev) => ({
        ...prev,
        total: response.data.data.total,
      }));
    } catch {
      message.error('加载异常邮件失败');
    } finally {
      setLoading(false);
    }
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
      title: '异常原因',
      key: 'reason',
      render: (_: unknown, record: ExceptionMailItem) => getReasonTag(record.reason),
    },
    {
      title: '记录时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => new Date(date).toLocaleString(),
    },
    {
      title: '操作',
      key: 'actions',
      render: (_: unknown, record: ExceptionMailItem) => (
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
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Title level={3} style={{ margin: 0 }}>
          异常邮件
        </Title>
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
          <Option value="EMAIL_AUTH_FAILED">认证失败</Option>
          <Option value="DOMAIN_NOT_CONFIGURED">域名未配置</Option>
          <Option value="DECRYPTION_FAILED">解密失败</Option>
          <Option value="CERTIFICATE_MISSING">缺少证书</Option>
          <Option value="SIGNATURE_INVALID">签名无效</Option>
          <Option value="ENCRYPTION_FAILED">加密失败</Option>
          <Option value="SCAN_ERROR">扫描错误</Option>
          <Option value="CERTIFICATE_REVOKED">证书已吊销</Option>
        </Select>
      </div>

      <Table
        columns={columns}
        dataSource={data}
        loading={loading}
        rowKey="id"
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
        title="异常邮件详情"
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
            <Descriptions.Item label="异常原因">{getReasonTag(selectedItem.reason)}</Descriptions.Item>
            <Descriptions.Item label="详情说明">{selectedItem.detail || '-'}</Descriptions.Item>
            <Descriptions.Item label="阻断方式">
              <Space>
                <Tag color="red">自动阻断</Tag>
                <span>{selectedItem.blockComment || '-'}</span>
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="记录时间">
              {new Date(selectedItem.createdAt).toLocaleString()}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </div>
  );
};

export default ExceptionMails;
