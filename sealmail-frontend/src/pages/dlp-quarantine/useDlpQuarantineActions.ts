import { useCallback } from 'react';
import type React from 'react';
import { message } from 'antd';
import { dlpQuarantineApi } from '../../api/client';
import { getApiErrorMessage } from '../../api/errors';
import { confirmAction } from '../../components/Page';
import type { QuarantineItem } from '../../types';
import { canReject, canRelease, isDlpQuarantineActionKey } from './dlpQuarantineUtils';

interface UseDlpQuarantineActionsOptions {
  data: QuarantineItem[];
  loadData: () => void | Promise<void>;
  onFalsePositiveMarked: (record: QuarantineItem, comment: string) => void;
  selectedRejectCount: number;
  selectedReleaseCount: number;
  selectedRowKeys: React.Key[];
  setSelectedRowKeys: (keys: React.Key[]) => void;
}

const FALSE_POSITIVE_COMMENT = 'Operator marked as false positive';
const COMPLETE_RELEASE_COMMENT = 'Operator confirmed mail was already delivered';
const RESTORE_RELEASE_COMMENT = 'Operator confirmed mail was not delivered';

export const useDlpQuarantineActions = ({
  data,
  loadData,
  onFalsePositiveMarked,
  selectedRejectCount,
  selectedReleaseCount,
  selectedRowKeys,
  setSelectedRowKeys,
}: UseDlpQuarantineActionsOptions) => {
  const refresh = useCallback(() => {
    void loadData();
  }, [loadData]);

  const handleRelease = useCallback(async (id: string, encryptBeforeRelease = false) => {
    try {
      await dlpQuarantineApi.release(id, { encryptBeforeRelease });
      message.success(encryptBeforeRelease ? '邮件已加密后放行' : '邮件已放行');
      refresh();
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  }, [refresh]);

  const handleReject = useCallback(async (id: string) => {
    try {
      await dlpQuarantineApi.reject(id);
      message.success('邮件已拒绝');
      refresh();
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  }, [refresh]);

  const handleFalsePositive = useCallback(async (record: QuarantineItem) => {
    try {
      await dlpQuarantineApi.falsePositive(record.id, { comment: FALSE_POSITIVE_COMMENT });
      message.success('已标记为误报');
      refresh();
      onFalsePositiveMarked(record, FALSE_POSITIVE_COMMENT);
    } catch (error) {
      message.error(getApiErrorMessage(error, '标记误报失败'));
    }
  }, [onFalsePositiveMarked, refresh]);

  const handleCompleteRelease = useCallback(async (id: string) => {
    try {
      await dlpQuarantineApi.completeRelease(id, { comment: COMPLETE_RELEASE_COMMENT });
      message.success('已确认邮件完成投递');
      refresh();
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  }, [refresh]);

  const handleRestoreRelease = useCallback(async (id: string) => {
    try {
      await dlpQuarantineApi.restoreRelease(id, { comment: RESTORE_RELEASE_COMMENT });
      message.success('已恢复为待处理');
      refresh();
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  }, [refresh]);

  const handleBatchRelease = useCallback(async () => {
    const releasableIds = data
      .filter((item) => selectedRowKeys.includes(item.id) && canRelease(item))
      .map((item) => item.id);
    if (releasableIds.length === 0) {
      message.warning('当前选择中没有可放行的邮件');
      return;
    }
    try {
      const result = await dlpQuarantineApi.batchRelease(releasableIds);
      if (result.failureCount > 0) {
        message.warning(`已放行 ${result.successCount} 封，${result.failureCount} 封失败`);
      } else {
        message.success(`已放行 ${result.successCount} 封邮件`);
      }
      setSelectedRowKeys([]);
      refresh();
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  }, [data, refresh, selectedRowKeys, setSelectedRowKeys]);

  const handleBatchReject = useCallback(async () => {
    const rejectableIds = data
      .filter((item) => selectedRowKeys.includes(item.id) && canReject(item))
      .map((item) => item.id);
    if (rejectableIds.length === 0) {
      message.warning('当前选择中没有可拒绝的邮件');
      return;
    }
    try {
      await dlpQuarantineApi.batchReject(rejectableIds);
      message.success(`已拒绝 ${rejectableIds.length} 封邮件`);
      setSelectedRowKeys([]);
      refresh();
    } catch (error) {
      message.error(getApiErrorMessage(error, '操作失败'));
    }
  }, [data, refresh, selectedRowKeys, setSelectedRowKeys]);

  const confirmRelease = useCallback((record: QuarantineItem, encryptBeforeRelease = false) => {
    confirmAction({
      title: encryptBeforeRelease ? '确定要加密后放行此邮件吗？' : '确定要直接放行此邮件吗？',
      content: record.subject,
      okText: encryptBeforeRelease ? '加密放行' : '直接放行',
      onOk: () => handleRelease(record.id, encryptBeforeRelease),
    });
  }, [handleRelease]);

  const confirmReject = useCallback((record: QuarantineItem) => {
    confirmAction({
      title: '确定要拒绝此邮件吗？',
      content: record.subject,
      danger: true,
      okText: '拒绝',
      onOk: () => handleReject(record.id),
    });
  }, [handleReject]);

  const confirmCompleteRelease = useCallback((record: QuarantineItem) => {
    confirmAction({
      title: '确认此邮件已完成投递？',
      content: record.subject,
      okText: '确认已投递',
      onOk: () => handleCompleteRelease(record.id),
    });
  }, [handleCompleteRelease]);

  const confirmRestoreRelease = useCallback((record: QuarantineItem) => {
    confirmAction({
      title: '确认此邮件未投递并恢复为待处理？',
      content: record.subject,
      okText: '恢复待处理',
      danger: true,
      onOk: () => handleRestoreRelease(record.id),
    });
  }, [handleRestoreRelease]);

  const handleActionClick = useCallback((key: string, record: QuarantineItem) => {
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
  }, [confirmCompleteRelease, confirmReject, confirmRelease, confirmRestoreRelease]);

  const confirmBatchRelease = useCallback(() => {
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
  }, [handleBatchRelease, selectedReleaseCount, selectedRowKeys.length]);

  const confirmBatchReject = useCallback(() => {
    if (selectedRejectCount === 0) {
      message.warning('当前选择中没有可拒绝的邮件');
      return;
    }

    confirmAction({
      title: '确定要批量拒绝所选邮件吗？',
      content: `将拒绝 ${selectedRejectCount} 封邮件。`,
      danger: true,
      okText: '批量拒绝',
      onOk: handleBatchReject,
    });
  }, [handleBatchReject, selectedRejectCount]);

  return {
    confirmBatchReject,
    confirmBatchRelease,
    handleActionClick,
    handleFalsePositive,
  };
};
