import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Descriptions,
  Input,
  Segmented,
  Select,
  Space,
  message,
  Typography,
} from 'antd';
import type { TableColumnsType } from 'antd';
import { AuditLog } from '../types';
import { auditLogApi } from '../api/client';
import { getApiErrorMessage } from '../api/errors';
import {
  AuditResultTag,
  DataTable,
  DetailDrawer,
  FilterBar,
  PageHeader,
  PageShell,
  createListPagination,
} from '../components/Page';

const { Text } = Typography;

type CategoryKey = 'ALL' | 'AUTH' | 'USER' | 'CERTIFICATE' | 'EMAIL' | 'SYSTEM';
type StatusFilter = 'ALL' | 'SUCCESS' | 'FAILED';

interface ActiveFilterItem {
  key: string;
  label: React.ReactNode;
  onClose: () => void;
  value: React.ReactNode;
}

const categories: { value: CategoryKey; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'AUTH', label: '登录与安全' },
  { value: 'USER', label: '用户' },
  { value: 'CERTIFICATE', label: '证书' },
  { value: 'EMAIL', label: '邮件' },
  { value: 'SYSTEM', label: '系统' },
];

const eventGroups = [
  {
    label: '登录与安全',
    category: 'AUTH',
    options: [
      { value: 'USER_LOGIN', label: '登录成功' },
      { value: 'USER_LOGIN_FAILED', label: '登录失败' },
      { value: 'USER_LOGOUT', label: '退出登录' },
      { value: 'USER_PASSWORD_CHANGED', label: '修改密码' },
    ],
  },
  {
    label: '用户',
    category: 'USER',
    options: [
      { value: 'USER_CREATED', label: '创建用户' },
      { value: 'USER_UPDATED', label: '更新用户' },
      { value: 'USER_DELETED', label: '删除用户' },
      { value: 'USER_UNLOCKED', label: '解锁用户' },
      { value: 'USER_ROLE_CHANGED', label: '调整角色' },
    ],
  },
  {
    label: '证书',
    category: 'CERTIFICATE',
    options: [
      { value: 'CERTIFICATE_ISSUED', label: '签发证书' },
      { value: 'CERTIFICATE_IMPORTED', label: '导入证书' },
      { value: 'CERTIFICATE_TRUSTED', label: '信任证书' },
      { value: 'CERTIFICATE_UNTRUSTED', label: '撤销信任' },
      { value: 'CERTIFICATE_REVOKED', label: '吊销证书' },
      { value: 'CERTIFICATE_DELETED', label: '删除证书' },
    ],
  },
  {
    label: '邮件',
    category: 'EMAIL',
    options: [
      { value: 'EMAIL_RECEIVED', label: '接收邮件' },
      { value: 'EMAIL_DELIVERED', label: '投递邮件' },
      { value: 'EMAIL_QUARANTINED', label: 'DLP 隔离' },
      { value: 'EMAIL_RELEASED', label: '放行邮件' },
      { value: 'EMAIL_REJECTED', label: '拒收邮件' },
      { value: 'EMAIL_ENCRYPTED', label: '加密邮件' },
      { value: 'EMAIL_DECRYPTED', label: '解密邮件' },
      { value: 'EMAIL_SIGNED', label: '签名邮件' },
      { value: 'EMAIL_VERIFIED', label: '验签邮件' },
      { value: 'DLP_VIOLATION', label: 'DLP 命中' },
    ],
  },
  {
    label: '系统',
    category: 'SYSTEM',
    options: [
      { value: 'SYSTEM_CONFIG_CHANGED', label: '修改配置' },
      { value: 'SYSTEM_STARTUP', label: '系统启动' },
      { value: 'SYSTEM_SHUTDOWN', label: '系统关闭' },
      { value: 'OTHER', label: '其他事件' },
    ],
  },
];

const categoryTypeMap: Record<Exclude<CategoryKey, 'ALL'>, string[]> = eventGroups.reduce(
  (acc, group) => ({
    ...acc,
    [group.category]: group.options.map((option) => option.value),
  }),
  {} as Record<Exclude<CategoryKey, 'ALL'>, string[]>,
);

const statusOptions: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: '全部结果' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
];

const formatTime = (time?: string) => {
  if (!time) return '-';
  return new Date(time).toLocaleString();
};

const shortResource = (record: AuditLog) => {
  if (!record.resourceType && !record.resourceId) return '-';
  if (!record.resourceId) return record.resourceType;
  return `${record.resourceType || '资源'} · ${record.resourceId.substring(0, 12)}`;
};

const matchesFilters = (record: AuditLog, category: CategoryKey, eventType: string | undefined, status: StatusFilter) => {
  if (eventType && record.type !== eventType) {
    return false;
  }
  if (!eventType && category !== 'ALL' && !categoryTypeMap[category].includes(record.type)) {
    return false;
  }
  if (status === 'SUCCESS' && !record.success) {
    return false;
  }
  if (status === 'FAILED' && record.success) {
    return false;
  }
  return true;
};

const AuditLogs: React.FC = () => {
  const [data, setData] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ page: 1, size: 20, total: 0 });
  const [category, setCategory] = useState<CategoryKey>('ALL');
  const [eventType, setEventType] = useState<string | undefined>();
  const [status, setStatus] = useState<StatusFilter>('ALL');
  const [processingIdInput, setProcessingIdInput] = useState('');
  const [processingId, setProcessingId] = useState('');
  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null);

  const eventOptions = useMemo(() => {
    const groups = category === 'ALL'
      ? eventGroups
      : eventGroups.filter((group) => group.category === category);
    return groups.map(({ label, options }) => ({ label, options }));
  }, [category]);

  const eventLabelByValue = useMemo(
    () => new Map(eventGroups.flatMap((group) =>
      group.options.map((option) => [option.value, option.label] as const)
    )),
    [],
  );

  const hasProcessingId = processingId.trim().length > 0;
  const hasFilters = category !== 'ALL' || eventType || status !== 'ALL' || hasProcessingId;

  const loadAllLogs = useCallback(async () => {
    const pageSize = 100;
    const first = await auditLogApi.list({ page: 1, size: pageSize });
    const page = first.data.data;
    const items = [...page.items];
    const totalPages = Math.ceil(page.total / pageSize);

    for (let current = 2; current <= totalPages; current += 1) {
      const response = await auditLogApi.list({ page: current, size: pageSize });
      items.push(...response.data.data.items);
    }

    return items;
  }, []);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      if (hasProcessingId) {
        const response = await auditLogApi.getByProcessingId(processingId.trim(), {
          page: pagination.page,
          size: pagination.size,
        });
        setData(response.data.data.items);
        setPagination((prev) => ({
          ...prev,
          total: response.data.data.total,
        }));
        return;
      }

      if (hasFilters) {
        const logs = await loadAllLogs();
        const filtered = logs.filter((item) => matchesFilters(item, category, eventType, status));
        const start = (pagination.page - 1) * pagination.size;
        setData(filtered.slice(start, start + pagination.size));
        setPagination((prev) => ({
          ...prev,
          total: filtered.length,
        }));
        return;
      }

      const response = await auditLogApi.list({
        page: pagination.page,
        size: pagination.size,
      });
      setData(response.data.data.items);
      setPagination((prev) => ({
        ...prev,
        total: response.data.data.total,
      }));
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载审计日志失败'));
    } finally {
      setLoading(false);
    }
  }, [
    category,
    eventType,
    hasFilters,
    hasProcessingId,
    loadAllLogs,
    pagination.page,
    pagination.size,
    processingId,
    status,
  ]);

  useEffect(() => {
    void Promise.resolve().then(loadData);
  }, [loadData]);

  const clearFilters = () => {
    setCategory('ALL');
    setEventType(undefined);
    setStatus('ALL');
    setProcessingId('');
    setProcessingIdInput('');
    setPagination((prev) => ({ ...prev, page: 1 }));
  };

  const activeFilters = ([
    category !== 'ALL' ? {
      key: 'category',
      label: '范围',
      value: categories.find((item) => item.value === category)?.label || category,
      onClose: () => {
        setCategory('ALL');
        setEventType(undefined);
        setPagination((prev) => ({ ...prev, page: 1 }));
      },
    } : null,
    eventType ? {
      key: 'eventType',
      label: '事件',
      value: eventLabelByValue.get(eventType) || eventType,
      onClose: () => {
        setEventType(undefined);
        setPagination((prev) => ({ ...prev, page: 1 }));
      },
    } : null,
    status !== 'ALL' ? {
      key: 'status',
      label: '结果',
      value: statusOptions.find((item) => item.value === status)?.label || status,
      onClose: () => {
        setStatus('ALL');
        setPagination((prev) => ({ ...prev, page: 1 }));
      },
    } : null,
    hasProcessingId ? {
      key: 'processingId',
      label: 'processingId',
      value: processingId,
      onClose: () => {
        setProcessingId('');
        setProcessingIdInput('');
        setPagination((prev) => ({ ...prev, page: 1 }));
      },
    } : null,
  ] as Array<ActiveFilterItem | null>).filter((filter): filter is ActiveFilterItem => Boolean(filter));

  const columns: TableColumnsType<AuditLog> = [
    {
      title: '时间',
      dataIndex: 'occurredAt',
      key: 'occurredAt',
      render: (time: string) => <Text type="secondary">{formatTime(time)}</Text>,
      width: 180,
    },
    {
      title: '事件',
      key: 'event',
      render: (_: unknown, record: AuditLog) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.typeDisplayName || record.type}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>{record.action || record.type}</Text>
        </Space>
      ),
      width: 180,
    },
    {
      title: '用户与来源',
      key: 'actor',
      render: (_: unknown, record: AuditLog) => (
        <Space direction="vertical" size={0}>
          <Text>{record.username || '-'}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>{record.ipAddress || '-'}</Text>
        </Space>
      ),
      width: 160,
    },
    {
      title: '对象',
      key: 'resource',
      render: (_: unknown, record: AuditLog) => (
        <Text type="secondary">{shortResource(record)}</Text>
      ),
      width: 160,
    },
    {
      title: '详情',
      dataIndex: 'detail',
      key: 'detail',
      ellipsis: true,
      render: (detail: string, record: AuditLog) => detail || record.errorMessage || '-',
    },
    {
      title: '结果',
      key: 'success',
      render: (_: unknown, record: AuditLog) => <AuditResultTag success={record.success} />,
      width: 90,
    },
  ];

  return (
    <PageShell>
      <PageHeader
        title="审计日志"
        description="按事件范围、结果和具体行为快速定位系统操作记录。"
      />

      <FilterBar
        activeFilters={activeFilters}
        onReset={clearFilters}
        onRefresh={loadData}
        refreshLoading={loading}
        resetDisabled={!hasFilters}
      >
          <Segmented
            value={category}
            options={categories}
            onChange={(value) => {
              setCategory(value as CategoryKey);
              setEventType(undefined);
              setPagination((prev) => ({ ...prev, page: 1 }));
            }}
          />
          <Select
            style={{ width: 220 }}
            placeholder="具体事件"
            allowClear
            value={eventType}
            options={eventOptions}
            onChange={(value) => {
              setEventType(value);
              setPagination((prev) => ({ ...prev, page: 1 }));
            }}
            showSearch
            optionFilterProp="label"
          />
          <Segmented
            value={status}
            options={statusOptions}
            onChange={(value) => {
              setStatus(value as StatusFilter);
              setPagination((prev) => ({ ...prev, page: 1 }));
            }}
          />
          <Input.Search
            allowClear
            enterButton
            style={{ width: 300 }}
            placeholder="processingId"
            value={processingIdInput}
            onChange={(event) => {
              const value = event.target.value;
              setProcessingIdInput(value);
              if (!value.trim()) {
                setProcessingId('');
                setPagination((prev) => ({ ...prev, page: 1 }));
              }
            }}
            onSearch={(value) => {
              setProcessingId(value.trim());
              setPagination((prev) => ({ ...prev, page: 1 }));
            }}
          />
      </FilterBar>

      <DataTable<AuditLog>
        columns={columns}
        dataSource={data}
        rowKey="id"
        loading={loading}
        size="middle"
        onRow={(record) => ({
          onClick: () => {
            setSelectedLog(record);
            setDetailVisible(true);
          },
          style: { cursor: 'pointer' },
        })}
        pagination={createListPagination(pagination, (page, size) =>
          setPagination((prev) => ({ ...prev, page, size }))
        )}
      />

      <DetailDrawer
        title="日志详情"
        open={detailVisible}
        onClose={() => {
          setDetailVisible(false);
          setSelectedLog(null);
        }}
      >
        {selectedLog && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Space>
              <AuditResultTag success={selectedLog.success} />
              <Text strong>{selectedLog.typeDisplayName}</Text>
            </Space>
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="时间">{formatTime(selectedLog.occurredAt)}</Descriptions.Item>
              <Descriptions.Item label="用户">{selectedLog.username || '-'}</Descriptions.Item>
              <Descriptions.Item label="用户ID">{selectedLog.userId || '-'}</Descriptions.Item>
              <Descriptions.Item label="来源IP">{selectedLog.ipAddress || '-'}</Descriptions.Item>
              <Descriptions.Item label="事件代码">{selectedLog.type}</Descriptions.Item>
              <Descriptions.Item label="对象类型">{selectedLog.resourceType || '-'}</Descriptions.Item>
              <Descriptions.Item label="对象ID">{selectedLog.resourceId || '-'}</Descriptions.Item>
              <Descriptions.Item label="详情">{selectedLog.detail || '-'}</Descriptions.Item>
              {selectedLog.errorMessage && (
                <Descriptions.Item label="错误信息">
                  <Text type="danger">{selectedLog.errorMessage}</Text>
                </Descriptions.Item>
              )}
              <Descriptions.Item label="日志ID">{selectedLog.id}</Descriptions.Item>
            </Descriptions>
          </Space>
        )}
      </DetailDrawer>
    </PageShell>
  );
};

export default AuditLogs;
