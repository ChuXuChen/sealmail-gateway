import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Input,
  Segmented,
  Select,
  message,
} from 'antd';
import { AuditLog } from '../types';
import { auditLogApi } from '../api/client';
import { getApiErrorMessage } from '../api/errors';
import {
  FilterBar,
  PageHeader,
  PageShell,
} from '../components/Page';
import AuditLogDetailDrawer from './audit-logs/AuditLogDetailDrawer';
import AuditLogTable from './audit-logs/AuditLogTable';
import {
  CategoryKey,
  StatusFilter,
  categories,
  eventGroups,
  matchesFilters,
  statusOptions,
} from './audit-logs/auditLogUtils';

interface ActiveFilterItem {
  key: string;
  label: React.ReactNode;
  onClose: () => void;
  value: React.ReactNode;
}

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

  const eventLabelByValue = useMemo<Map<string, string>>(
    () => new Map(eventGroups.flatMap((group) =>
      group.options.map((option) => [option.value, option.label])
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
            className="filter-control-lg"
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
            className="filter-control-xl"
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

      <AuditLogTable
        data={data}
        loading={loading}
        pagination={pagination}
        onPageChange={(page, size) => setPagination((prev) => ({ ...prev, page, size }))}
        onSelect={(record) => {
          setSelectedLog(record);
          setDetailVisible(true);
        }}
      />

      <AuditLogDetailDrawer
        log={selectedLog}
        open={detailVisible}
        onClose={() => {
          setDetailVisible(false);
          setSelectedLog(null);
        }}
      />
    </PageShell>
  );
};

export default AuditLogs;
