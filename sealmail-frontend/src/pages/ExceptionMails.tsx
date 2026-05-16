import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Space,
  Tag,
  message,
  Select,
  Descriptions,
  Button,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { EyeOutlined } from '@ant-design/icons';
import { ExceptionMailItem } from '../types';
import { exceptionMailApi } from '../api/client';
import { getApiErrorMessage } from '../api/errors';
import {
  DataTable,
  DetailDrawer,
  FilterBar,
  PageHeader,
  PageShell,
  ReasonTag,
  createListPagination,
  formatDateTime,
} from '../components/Page';

const { Option } = Select;

const reasonOptions = [
  { value: 'POLICY_VIOLATION', label: '策略违规' },
  { value: 'EMAIL_AUTH_FAILED', label: '认证失败' },
  { value: 'DOMAIN_NOT_CONFIGURED', label: '域名未配置' },
  { value: 'DECRYPTION_FAILED', label: '解密失败' },
  { value: 'CERTIFICATE_MISSING', label: '缺少证书' },
  { value: 'SIGNATURE_INVALID', label: '签名无效' },
  { value: 'ENCRYPTION_FAILED', label: '加密失败' },
  { value: 'SCAN_ERROR', label: '扫描错误' },
  { value: 'CERTIFICATE_REVOKED', label: '证书已吊销' },
];

const ExceptionMails: React.FC = () => {
  const [data, setData] = useState<ExceptionMailItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ page: 1, size: 20, total: 0 });
  const [reasonFilter, setReasonFilter] = useState<string | undefined>();
  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedItem, setSelectedItem] = useState<ExceptionMailItem | null>(null);

  const loadData = useCallback(async () => {
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
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载异常邮件失败'));
    } finally {
      setLoading(false);
    }
  }, [pagination.page, pagination.size, reasonFilter]);

  useEffect(() => {
    void Promise.resolve().then(loadData);
  }, [loadData]);

  const reasonLabel = useMemo(
    () => reasonOptions.find((option) => option.value === reasonFilter)?.label || reasonFilter,
    [reasonFilter],
  );

  const clearFilters = () => {
    setReasonFilter(undefined);
    setPagination((prev) => ({ ...prev, page: 1 }));
  };

  const columns: TableColumnsType<ExceptionMailItem> = [
    {
      title: '主题',
      dataIndex: 'subject',
      key: 'subject',
      width: 240,
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
      title: '异常原因',
      key: 'reason',
      width: 120,
      render: (_: unknown, record: ExceptionMailItem) => <ReasonTag reason={record.reason} />,
    },
    {
      title: '记录时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: formatDateTime,
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      fixed: 'right',
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
    <PageShell>
      <PageHeader
        title="异常邮件"
        description="查看因策略、证书或邮件认证失败而被自动阻断的邮件记录。"
      />

      <FilterBar
        activeFilters={reasonFilter ? [{
          key: 'reason',
          label: '异常原因',
          value: reasonLabel,
          onClose: clearFilters,
        }] : undefined}
        onRefresh={loadData}
        onReset={clearFilters}
        refreshLoading={loading}
        resetDisabled={!reasonFilter}
      >
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
          {reasonOptions.map((option) => (
            <Option key={option.value} value={option.value}>{option.label}</Option>
          ))}
        </Select>
      </FilterBar>

      <DataTable<ExceptionMailItem>
        columns={columns}
        dataSource={data}
        loading={loading}
        rowKey="id"
        scroll={{ x: 1120 }}
        pagination={createListPagination(pagination, (page, size) =>
          setPagination((prev) => ({ ...prev, page, size }))
        )}
      />

      <DetailDrawer
        title="异常邮件详情"
        open={detailVisible}
        onClose={() => {
          setDetailVisible(false);
          setSelectedItem(null);
        }}
      >
        {selectedItem && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="主题">{selectedItem.subject}</Descriptions.Item>
            <Descriptions.Item label="Message-ID">{selectedItem.messageId}</Descriptions.Item>
            <Descriptions.Item label="发件人">{selectedItem.sender}</Descriptions.Item>
            <Descriptions.Item label="收件人">
              {selectedItem.recipients?.join(', ') || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="方向">{selectedItem.direction || '-'}</Descriptions.Item>
            <Descriptions.Item label="来源地址">{selectedItem.remoteAddress || '-'}</Descriptions.Item>
            <Descriptions.Item label="异常原因"><ReasonTag reason={selectedItem.reason} /></Descriptions.Item>
            <Descriptions.Item label="详情说明">{selectedItem.detail || '-'}</Descriptions.Item>
            <Descriptions.Item label="阻断方式">
              <Space>
                <Tag color="red">自动阻断</Tag>
                <span>{selectedItem.blockComment || '-'}</span>
              </Space>
            </Descriptions.Item>
            <Descriptions.Item label="记录时间">
              {formatDateTime(selectedItem.createdAt)}
            </Descriptions.Item>
          </Descriptions>
        )}
      </DetailDrawer>
    </PageShell>
  );
};

export default ExceptionMails;
