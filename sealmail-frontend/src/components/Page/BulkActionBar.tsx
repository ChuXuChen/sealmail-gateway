import React from 'react';
import { Alert, Button, Space, Typography } from 'antd';

const { Text } = Typography;

interface BulkActionBarProps {
  actionableCount?: number;
  actions: React.ReactNode;
  onClear?: () => void;
  selectedCount: number;
  unavailableText?: React.ReactNode;
}

const BulkActionBar: React.FC<BulkActionBarProps> = ({
  actionableCount,
  actions,
  onClear,
  selectedCount,
  unavailableText,
}) => {
  if (selectedCount <= 0) {
    return null;
  }

  const unavailableCount = Math.max(0, selectedCount - (actionableCount ?? selectedCount));

  return (
    <Alert
      type="info"
      showIcon
      className="bulk-action-bar"
      message={(
        <Space size={12} wrap>
          <Text>已选择 {selectedCount} 项</Text>
          {typeof actionableCount === 'number' ? (
            <Text type={actionableCount === 0 ? 'danger' : 'secondary'}>
              可操作 {actionableCount} 项
            </Text>
          ) : null}
          {unavailableCount > 0 ? (
            <Text type="secondary">
              {unavailableText || `${unavailableCount} 项当前不可执行部分操作`}
            </Text>
          ) : null}
          {onClear ? (
            <Button type="link" size="small" onClick={onClear}>
              清空选择
            </Button>
          ) : null}
        </Space>
      )}
      action={<Space wrap>{actions}</Space>}
    />
  );
};

export default BulkActionBar;
