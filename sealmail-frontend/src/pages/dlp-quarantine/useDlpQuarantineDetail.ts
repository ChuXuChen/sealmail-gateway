import { useCallback, useState } from 'react';
import { message } from 'antd';
import { dlpQuarantineApi } from '../../api/client';
import { getApiErrorMessage } from '../../api/errors';
import type { DlpEvidence, QuarantineItem } from '../../types';

export const useDlpQuarantineDetail = () => {
  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedItem, setSelectedItem] = useState<QuarantineItem | null>(null);
  const [selectedEvidence, setSelectedEvidence] = useState<DlpEvidence[]>([]);

  const viewItem = useCallback(async (record: QuarantineItem) => {
    setSelectedItem(record);
    setDetailVisible(true);
    try {
      setSelectedEvidence(await dlpQuarantineApi.evidence(record.id));
    } catch (error) {
      setSelectedEvidence([]);
      message.error(getApiErrorMessage(error, '加载 DLP 证据失败'));
    }
  }, []);

  const closeDetail = useCallback(() => {
    setDetailVisible(false);
    setSelectedItem(null);
    setSelectedEvidence([]);
  }, []);

  const markFalsePositive = useCallback((record: QuarantineItem, comment: string) => {
    setSelectedItem((current) => (
      current?.id === record.id
        ? { ...record, falsePositive: true, falsePositiveComment: comment }
        : current
    ));
  }, []);

  return {
    closeDetail,
    detailVisible,
    markFalsePositive,
    selectedEvidence,
    selectedItem,
    viewItem,
  };
};
