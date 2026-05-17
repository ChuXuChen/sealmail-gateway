import type React from 'react';
import { Descriptions, Space } from 'antd';
import type { QuarantineItem } from '../../types';
import {
  DetailDrawer,
  EnabledTag,
  QuarantineStatusTag,
  ReasonTag,
  formatDateTime,
} from '../../components/Page';
import { canRelease } from './dlpQuarantineUtils';

interface DlpQuarantineDetailDrawerProps {
  item: QuarantineItem | null;
  open: boolean;
  onClose: () => void;
}

const DlpQuarantineDetailDrawer: React.FC<DlpQuarantineDetailDrawerProps> = ({
  item,
  open,
  onClose,
}) => (
  <DetailDrawer title="DLP 隔离邮件详情" open={open} onClose={onClose}>
    {item && (
      <Descriptions column={1} bordered size="small">
        <Descriptions.Item label="主题">{item.subject}</Descriptions.Item>
        <Descriptions.Item label="Message-ID">{item.messageId}</Descriptions.Item>
        <Descriptions.Item label="发件人">{item.sender}</Descriptions.Item>
        <Descriptions.Item label="收件人">
          {item.recipients?.join(', ') || '-'}
        </Descriptions.Item>
        <Descriptions.Item label="方向">{item.direction || '-'}</Descriptions.Item>
        <Descriptions.Item label="来源地址">{item.remoteAddress || '-'}</Descriptions.Item>
        <Descriptions.Item label="隔离原因"><ReasonTag reason={item.reason} /></Descriptions.Item>
        <Descriptions.Item label="详情说明">{item.detail || '-'}</Descriptions.Item>
        <Descriptions.Item label="状态"><QuarantineStatusTag status={item.status} /></Descriptions.Item>
        <Descriptions.Item label="可放行">
          {canRelease(item) ? (
            <EnabledTag enabled enabledText="可以放行" />
          ) : (
            <Space direction="vertical" size={4}>
              <EnabledTag enabled={false} disabledText="不可放行" />
              <span>{item.releaseUnavailableReason || '当前状态不可放行'}</span>
            </Space>
          )}
        </Descriptions.Item>
        <Descriptions.Item label="隔离时间">
          {formatDateTime(item.quarantinedAt)}
        </Descriptions.Item>
        {item.resolvedBy && (
          <Descriptions.Item label={item.status === 'RELEASED' ? '放行操作' : '拒绝操作'}>
            由 {item.resolvedBy}
            {item.resolvedAt && ` 于 ${formatDateTime(item.resolvedAt)}`}
            {item.status === 'RELEASED' ? ' 放行' : ' 拒绝'}
            {item.resolutionComment && ` (${item.resolutionComment})`}
          </Descriptions.Item>
        )}
      </Descriptions>
    )}
  </DetailDrawer>
);

export default DlpQuarantineDetailDrawer;
