import type React from 'react';
import { Descriptions, Space, Typography } from 'antd';
import type { AuditLog } from '../../types';
import { AuditResultTag, DetailDrawer } from '../../components/Page';
import { formatAuditTime } from './auditLogUtils';

const { Text } = Typography;

interface AuditLogDetailDrawerProps {
  log: AuditLog | null;
  open: boolean;
  onClose: () => void;
}

const AuditLogDetailDrawer: React.FC<AuditLogDetailDrawerProps> = ({
  log,
  open,
  onClose,
}) => (
  <DetailDrawer title="日志详情" open={open} onClose={onClose}>
    {log && (
      <Space direction="vertical" size={16} className="full-width">
        <Space>
          <AuditResultTag success={log.success} />
          <Text strong>{log.typeDisplayName}</Text>
        </Space>
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="时间">{formatAuditTime(log.occurredAt)}</Descriptions.Item>
          <Descriptions.Item label="用户">{log.username || '-'}</Descriptions.Item>
          <Descriptions.Item label="用户ID">{log.userId || '-'}</Descriptions.Item>
          <Descriptions.Item label="来源IP">{log.ipAddress || '-'}</Descriptions.Item>
          <Descriptions.Item label="事件代码">{log.type}</Descriptions.Item>
          <Descriptions.Item label="对象类型">{log.resourceType || '-'}</Descriptions.Item>
          <Descriptions.Item label="对象ID">{log.resourceId || '-'}</Descriptions.Item>
          <Descriptions.Item label="详情">{log.detail || '-'}</Descriptions.Item>
          {log.errorMessage && (
            <Descriptions.Item label="错误信息">
              <Text type="danger">{log.errorMessage}</Text>
            </Descriptions.Item>
          )}
          <Descriptions.Item label="日志ID">{log.id}</Descriptions.Item>
        </Descriptions>
      </Space>
    )}
  </DetailDrawer>
);

export default AuditLogDetailDrawer;
