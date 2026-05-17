import React, { useCallback, useEffect, useState } from 'react';
import {
  Button,
  message,
  Select,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons';
import type { DlpEvidence, QuarantineItem } from '../types';
import { dlpQuarantineApi } from '../api/client';
import { getApiErrorMessage } from '../api/errors';
import {
  BulkActionBar,
  FilterBar,
  PageHeader,
  PageShell,
  confirmAction,
} from '../components/Page';
import DlpQuarantineDetailDrawer from './dlp-quarantine/DlpQuarantineDetailDrawer';
import DlpQuarantineTable from './dlp-quarantine/DlpQuarantineTable';
import { canRelease, isDlpQuarantineActionKey, reasonOptions } from './dlp-quarantine/dlpQuarantineUtils';

const DlpQuarantine: React.FC = () => {
  const [data, setData] = useState<QuarantineItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ page: 1, size: 20, total: 0 });
  const [reasonFilter, setReasonFilter] = useState<string | undefined>();
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedItem, setSelectedItem] = useState<QuarantineItem | null>(null);
  const [selectedEvidence, setSelectedEvidence] = useState<DlpEvidence[]>([]);

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

  const handleFalsePositive = async (record: QuarantineItem) => {
    try {
      await dlpQuarantineApi.falsePositive(record.id, { comment: 'Operator marked as false positive' });
      message.success('已标记为误报');
      void loadData();
      if (selectedItem?.id === record.id) {
        setSelectedItem({ ...record, falsePositive: true, falsePositiveComment: 'Operator marked as false positive' });
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '标记误报失败'));
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
          className="filter-control-md"
          allowClear
          value={reasonFilter}
          options={reasonOptions}
          onChange={(value) => {
            setReasonFilter(value || undefined);
            setPagination((prev) => ({ ...prev, page: 1 }));
          }}
        />
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

      <DlpQuarantineTable
        data={data}
        loading={loading}
        pagination={pagination}
        selectedRowKeys={selectedRowKeys}
        onAction={handleActionClick}
        onPaginationChange={(page, size) => setPagination((prev) => ({ ...prev, page, size }))}
        onSelectionChange={setSelectedRowKeys}
        onView={async (record) => {
          setSelectedItem(record);
          setDetailVisible(true);
          try {
            const response = await dlpQuarantineApi.evidence(record.id);
            setSelectedEvidence(response.data.data);
          } catch (error) {
            setSelectedEvidence([]);
            message.error(getApiErrorMessage(error, '加载 DLP 证据失败'));
          }
        }}
      />

      <DlpQuarantineDetailDrawer
        item={selectedItem}
        evidence={selectedEvidence}
        open={detailVisible}
        onClose={() => {
          setDetailVisible(false);
          setSelectedItem(null);
          setSelectedEvidence([]);
        }}
        onFalsePositive={handleFalsePositive}
      />
    </PageShell>
  );
};

export default DlpQuarantine;
