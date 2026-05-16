import React, { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Dropdown,
  Space,
  message,
  Select,
  Descriptions,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  DownOutlined,
  EyeOutlined,
  LockOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { MenuProps, TableColumnsType } from 'antd';
import { QuarantineItem } from '../types';
import { dlpQuarantineApi } from '../api/client';
import { getApiErrorMessage } from '../api/errors';
import {
  BulkActionBar,
  DataTable,
  DetailDrawer,
  EnabledTag,
  FilterBar,
  PageHeader,
  PageShell,
  QuarantineStatusTag,
  ReasonTag,
  confirmAction,
  createListPagination,
  formatDateTime,
} from '../components/Page';

const { Option } = Select;
type DlpQuarantineActionKey = 'release' | 'release-encrypted' | 'reject' | 'complete-release' | 'restore-release';

const reasonOptions = [
  { value: 'POLICY_VIOLATION', label: '策略违规' },
  { value: 'CERTIFICATE_MISSING', label: '缺少证书' },
  { value: 'ENCRYPTION_FAILED', label: '加密失败' },
  { value: 'SCAN_ERROR', label: '扫描错误' },
];

const isDlpQuarantineActionKey = (key: string): key is DlpQuarantineActionKey =>
  key === 'release'
  || key === 'release-encrypted'
  || key === 'reject'
  || key === 'complete-release'
  || key === 'restore-release';

const DlpQuarantine: React.FC = () => {
  const [data, setData] = useState<QuarantineItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ page: 1, size: 20, total: 0 });
  const [reasonFilter, setReasonFilter] = useState<string | undefined>();
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedItem, setSelectedItem] = useState<QuarantineItem | null>(null);

  const loadData = useCallback(async () => {
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
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载 DLP 隔离邮件失败'));
    } finally {
      setLoading(false);
    }
  }, [pagination.page, pagination.size, reasonFilter]);

  useEffect(() => {
    void Promise.resolve().then(loadData);
  }, [loadData]);

  const handleRelease = async (id: string, encryptBeforeRelease = false) => {
    try {
      await dlpQuarantineApi.release(id, { encryptBeforeRelease });
      message.success(encryptBeforeRelease ? '邮件已加密后放行' : '邮件已放行');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  };

  const handleReject = async (id: string) => {
    try {
      await dlpQuarantineApi.reject(id);
      message.success('邮件已拒绝');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  };

  const handleCompleteRelease = async (id: string) => {
    try {
      await dlpQuarantineApi.completeRelease(id, { comment: 'Operator confirmed mail was already delivered' });
      message.success('已确认邮件完成投递');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  };

  const handleRestoreRelease = async (id: string) => {
    try {
      await dlpQuarantineApi.restoreRelease(id, { comment: 'Operator confirmed mail was not delivered' });
      message.success('已恢复为待处理');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  };

  const handleBatchRelease = async () => {
    const releasableIds = data
      .filter((item) => selectedRowKeys.includes(item.id) && canRelease(item))
      .map((item) => item.id);
    if (releasableIds.length === 0) {
      message.warning('当前选择中没有可放行的邮件');
      return;
    }
    try {
      await dlpQuarantineApi.batchRelease(releasableIds);
      message.success(`已放行 ${releasableIds.length} 封邮件`);
      setSelectedRowKeys([]);
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  };

  const handleBatchReject = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择待处理邮件');
      return;
    }
    try {
      await dlpQuarantineApi.batchReject(selectedRowKeys as string[]);
      message.success(`已拒绝 ${selectedRowKeys.length} 封邮件`);
      setSelectedRowKeys([]);
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  };

  const canRelease = (record: QuarantineItem) =>
    record.status === 'QUARANTINED' && record.canRelease !== false;

  const handleActionClick = (key: string, record: QuarantineItem) => {
    if (!isDlpQuarantineActionKey(key)) {
      return;
    }

    const releaseDisabled = !canRelease(record);
    if ((key === 'release' || key === 'release-encrypted') && releaseDisabled) {
      return;
    }

    if (key === 'complete-release') {
      confirmCompleteRelease(record);
      return;
    }

    if (key === 'restore-release') {
      confirmRestoreRelease(record);
      return;
    }

    if (key === 'reject') {
      confirmReject(record);
      return;
    }

    confirmRelease(record, key === 'release-encrypted');
  };

  const confirmRelease = (record: QuarantineItem, encryptBeforeRelease = false) => {
    confirmAction({
      title: encryptBeforeRelease ? '确定要加密后放行此邮件吗？' : '确定要直接放行此邮件吗？',
      content: record.subject,
      okText: encryptBeforeRelease ? '加密放行' : '直接放行',
      onOk: () => handleRelease(record.id, encryptBeforeRelease),
    });
  };

  const confirmReject = (record: QuarantineItem) => {
    confirmAction({
      title: '确定要拒绝此邮件吗？',
      content: record.subject,
      danger: true,
      okText: '拒绝',
      onOk: () => handleReject(record.id),
    });
  };

  const confirmCompleteRelease = (record: QuarantineItem) => {
    confirmAction({
      title: '确认此邮件已完成投递？',
      content: record.subject,
      okText: '确认已投递',
      onOk: () => handleCompleteRelease(record.id),
    });
  };

  const confirmRestoreRelease = (record: QuarantineItem) => {
    confirmAction({
      title: '确认此邮件未投递并恢复为待处理？',
      content: record.subject,
      okText: '恢复待处理',
      danger: true,
      onOk: () => handleRestoreRelease(record.id),
    });
  };

  const confirmBatchRelease = () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择待处理邮件');
      return;
    }

    if (selectedReleaseCount === 0) {
      message.warning('当前选择中没有可放行的邮件');
      return;
    }

    confirmAction({
      title: '确定要批量放行所选邮件吗？',
      content: `将直接放行 ${selectedReleaseCount} 封邮件。`,
      okText: '批量放行',
      onOk: handleBatchRelease,
    });
  };

  const confirmBatchReject = () => {
    if (selectedRowKeys.length === 0) {
      message.warning('请先选择待处理邮件');
      return;
    }

    confirmAction({
      title: '确定要批量拒绝所选邮件吗？',
      content: `将拒绝 ${selectedRowKeys.length} 封邮件。`,
      danger: true,
      okText: '批量拒绝',
      onOk: handleBatchReject,
    });
  };

  const getActionItems = (
    record: QuarantineItem,
    releaseDisabled: boolean
  ): MenuProps['items'] => {
    if (record.status === 'RELEASING') {
      return [
        {
          key: 'complete-release',
          icon: <CheckCircleOutlined />,
          label: '确认已投递',
        },
        {
          key: 'restore-release',
          danger: true,
          icon: <ReloadOutlined />,
          label: '恢复待处理',
        },
      ];
    }

    return [
      {
        key: 'release',
        icon: <CheckCircleOutlined />,
        label: releaseDisabled
          ? record.releaseUnavailableReason || '不可放行'
          : '直接放行',
        disabled: releaseDisabled,
      },
      {
        key: 'release-encrypted',
        icon: <LockOutlined />,
        label: releaseDisabled
          ? record.releaseUnavailableReason || '不可加密放行'
          : '加密放行',
        disabled: releaseDisabled,
      },
      {
        type: 'divider',
      },
      {
        key: 'reject',
        danger: true,
        icon: <CloseCircleOutlined />,
        label: '拒绝',
      },
    ];
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
      render: (_: unknown, record: QuarantineItem) => <ReasonTag reason={record.reason} />,
    },
    {
      title: '状态',
      key: 'status',
      width: 100,
      render: (_: unknown, record: QuarantineItem) => <QuarantineStatusTag status={record.status} />,
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
      render: (_: unknown, record: QuarantineItem) => {
        const isActionable = record.status === 'QUARANTINED' || record.status === 'RELEASING';
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
            {isActionable && (
              <Dropdown
                trigger={['click']}
                menu={{
                  items: getActionItems(record, releaseDisabled),
                  onClick: ({ key }) => handleActionClick(key, record),
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
  const reasonLabel = reasonOptions.find((option) => option.value === reasonFilter)?.label || reasonFilter;
  const clearFilters = () => {
    setReasonFilter(undefined);
    setPagination((prev) => ({ ...prev, page: 1 }));
  };

  return (
    <PageShell>
      <PageHeader
        title="DLP 隔离邮件"
        description="查看 DLP 命中的隔离邮件，并执行放行、加密放行或拒绝处理。"
      />

      <FilterBar
        activeFilters={reasonFilter ? [{
          key: 'reason',
          label: '隔离原因',
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

      <BulkActionBar
        selectedCount={selectedRowKeys.length}
        actionableCount={selectedReleaseCount}
        onClear={() => setSelectedRowKeys([])}
        unavailableText="部分邮件当前不可放行，仍可执行拒绝。"
        actions={(
          <>
            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              onClick={confirmBatchRelease}
              disabled={selectedReleaseCount === 0}
            >
              批量放行 ({selectedReleaseCount})
            </Button>
            <Button danger icon={<CloseCircleOutlined />} onClick={confirmBatchReject}>
              批量拒绝 ({selectedRowKeys.length})
            </Button>
          </>
        )}
      />

      <DataTable<QuarantineItem>
        columns={columns}
        dataSource={data}
        loading={loading}
        rowKey="id"
        rowSelection={rowSelection}
        scroll={{ x: 1260 }}
        pagination={createListPagination(pagination, (page, size) =>
          setPagination((prev) => ({ ...prev, page, size }))
        )}
      />

      <DetailDrawer
        title="DLP 隔离邮件详情"
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
            <Descriptions.Item label="隔离原因"><ReasonTag reason={selectedItem.reason} /></Descriptions.Item>
            <Descriptions.Item label="详情说明">{selectedItem.detail || '-'}</Descriptions.Item>
            <Descriptions.Item label="状态"><QuarantineStatusTag status={selectedItem.status} /></Descriptions.Item>
            <Descriptions.Item label="可放行">
              {canRelease(selectedItem) ? (
                <EnabledTag enabled enabledText="可以放行" />
              ) : (
                <Space direction="vertical" size={4}>
                  <EnabledTag enabled={false} disabledText="不可放行" />
                  <span>{selectedItem.releaseUnavailableReason || '当前状态不可放行'}</span>
                </Space>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="隔离时间">
              {formatDateTime(selectedItem.quarantinedAt)}
            </Descriptions.Item>
            {selectedItem.resolvedBy && (
              <Descriptions.Item label={selectedItem.status === 'RELEASED' ? '放行操作' : '拒绝操作'}>
                由 {selectedItem.resolvedBy}
                {selectedItem.resolvedAt && ` 于 ${formatDateTime(selectedItem.resolvedAt)}`}
                {selectedItem.status === 'RELEASED' ? ' 放行' : ' 拒绝'}
                {selectedItem.resolutionComment && ` (${selectedItem.resolutionComment})`}
              </Descriptions.Item>
            )}
          </Descriptions>
        )}
      </DetailDrawer>
    </PageShell>
  );
};

export default DlpQuarantine;
