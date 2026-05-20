import React from 'react';
import {
  Button,
  Select,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons';
import {
  BulkActionBar,
  FilterBar,
  PageHeader,
  PageShell,
} from '../components/Page';
import DlpQuarantineDetailDrawer from './dlp-quarantine/DlpQuarantineDetailDrawer';
import DlpQuarantineTable from './dlp-quarantine/DlpQuarantineTable';
import { reasonOptions } from './dlp-quarantine/dlpQuarantineUtils';
import { useDlpQuarantineActions } from './dlp-quarantine/useDlpQuarantineActions';
import { useDlpQuarantineDetail } from './dlp-quarantine/useDlpQuarantineDetail';
import { useDlpQuarantineList } from './dlp-quarantine/useDlpQuarantineList';

const DlpQuarantine: React.FC = () => {
  const {
    changePagination,
    changeReasonFilter,
    clearFilters,
    data,
    loadData,
    loading,
    pagination,
    reasonFilter,
    reasonLabel,
    selectedRejectCount,
    selectedReleaseCount,
    selectedRowKeys,
    setSelectedRowKeys,
  } = useDlpQuarantineList();
  const {
    closeDetail,
    detailVisible,
    markFalsePositive,
    selectedEvidence,
    selectedItem,
    viewItem,
  } = useDlpQuarantineDetail();
  const {
    confirmBatchReject,
    confirmBatchRelease,
    handleActionClick,
    handleFalsePositive,
  } = useDlpQuarantineActions({
    data,
    loadData,
    onFalsePositiveMarked: markFalsePositive,
    selectedRejectCount,
    selectedReleaseCount,
    selectedRowKeys,
    setSelectedRowKeys,
  });

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
          onChange={changeReasonFilter}
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
            <Button danger icon={<CloseCircleOutlined />} onClick={confirmBatchReject} disabled={selectedRejectCount === 0}>
              批量拒绝 ({selectedRejectCount})
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
        onPaginationChange={changePagination}
        onSelectionChange={setSelectedRowKeys}
        onView={viewItem}
      />

      <DlpQuarantineDetailDrawer
        item={selectedItem}
        evidence={selectedEvidence}
        open={detailVisible}
        onClose={closeDetail}
        onFalsePositive={handleFalsePositive}
      />
    </PageShell>
  );
};

export default DlpQuarantine;
