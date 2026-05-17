import type React from 'react';
import { Button, Descriptions, Space, Table, Tag } from 'antd';
import { FlagOutlined } from '@ant-design/icons';
import type { DlpEvidence, QuarantineItem } from '../../types';
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
  evidence?: DlpEvidence[];
  open: boolean;
  onClose: () => void;
  onFalsePositive?: (item: QuarantineItem) => void;
}

const DlpQuarantineDetailDrawer: React.FC<DlpQuarantineDetailDrawerProps> = ({
  item,
  evidence = [],
  open,
  onClose,
  onFalsePositive,
}) => (
  <DetailDrawer
    title="DLP 隔离邮件详情"
    open={open}
    onClose={onClose}
    extra={item && onFalsePositive && !item.falsePositive ? (
      <Button icon={<FlagOutlined />} onClick={() => onFalsePositive(item)}>标记误报</Button>
    ) : undefined}
  >
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
        <Descriptions.Item label="DLP 事件">{item.dlpEventId || '-'}</Descriptions.Item>
        <Descriptions.Item label="误报标记">
          {item.falsePositive ? (
            <Space direction="vertical" size={4}>
              <Tag color="green">误报</Tag>
              <span>{item.falsePositiveBy || '-'} {item.falsePositiveAt ? formatDateTime(item.falsePositiveAt) : ''}</span>
              <span>{item.falsePositiveComment || '-'}</span>
            </Space>
          ) : '-'}
        </Descriptions.Item>
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
    {item && (
      <Table<DlpEvidence>
        style={{ marginTop: 16 }}
        rowKey="id"
        size="small"
        dataSource={evidence}
        pagination={false}
        columns={[
          { title: '规则', dataIndex: 'ruleName', key: 'ruleName', width: 150 },
          { title: '位置', dataIndex: 'partKind', key: 'partKind', width: 120, render: (value, record) => record.fileName ? `${value} / ${record.fileName}` : value },
          { title: '脱敏证据', dataIndex: 'maskedSnippet', key: 'maskedSnippet', ellipsis: true },
        ]}
      />
    )}
  </DetailDrawer>
);

export default DlpQuarantineDetailDrawer;
