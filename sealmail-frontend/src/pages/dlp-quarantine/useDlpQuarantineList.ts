import { useCallback, useEffect, useState } from 'react';
import type React from 'react';
import { message } from 'antd';
import { dlpQuarantineApi } from '../../api/client';
import { getApiErrorMessage } from '../../api/errors';
import type { QuarantineItem } from '../../types';
import { canReject, canRelease, reasonOptions } from './dlpQuarantineUtils';

interface DlpQuarantinePagination {
  page: number;
  size: number;
  total: number;
}

export const useDlpQuarantineList = () => {
  const [data, setData] = useState<QuarantineItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState<DlpQuarantinePagination>({ page: 1, size: 20, total: 0 });
  const [reasonFilter, setReasonFilter] = useState<string | undefined>();
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const page = await dlpQuarantineApi.list({
        page: pagination.page,
        size: pagination.size,
        reason: reasonFilter,
      });
      setData(page.items);
      setPagination((prev) => ({
        ...prev,
        total: page.total,
      }));
      setSelectedRowKeys((prev) => {
        const visibleIds = new Set(page.items.map((item) => item.id));
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

  const clearFilters = useCallback(() => {
    setReasonFilter(undefined);
    setPagination((prev) => ({ ...prev, page: 1 }));
  }, []);

  const changeReasonFilter = useCallback((value?: string) => {
    setReasonFilter(value || undefined);
    setPagination((prev) => ({ ...prev, page: 1 }));
  }, []);

  const changePagination = useCallback((page: number, size: number) => {
    setPagination((prev) => ({ ...prev, page, size }));
  }, []);

  const selectedReleaseCount = data.filter((item) =>
    selectedRowKeys.includes(item.id) && canRelease(item)
  ).length;
  const selectedRejectCount = data.filter((item) =>
    selectedRowKeys.includes(item.id) && canReject(item)
  ).length;
  const reasonLabel = reasonOptions.find((option) => option.value === reasonFilter)?.label || reasonFilter;

  return {
    clearFilters,
    data,
    loading,
    loadData,
    pagination,
    reasonFilter,
    reasonLabel,
    selectedRejectCount,
    selectedReleaseCount,
    selectedRowKeys,
    setSelectedRowKeys,
    changePagination,
    changeReasonFilter,
  };
};
