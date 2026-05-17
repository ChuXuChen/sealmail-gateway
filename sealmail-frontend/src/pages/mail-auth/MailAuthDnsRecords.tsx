import React from 'react';
import { Button, Descriptions, Empty, Input, Space, Tag } from 'antd';
import { CopyOutlined } from '@ant-design/icons';
import type { DnsRecord } from '../../types';

interface MailAuthDnsRecordsProps {
  records: DnsRecord[];
  onCopyText: (value: string) => void | Promise<void>;
}

const recordColor = (record: DnsRecord) => {
  if (!record.available) return 'default';
  if (record.type === 'DKIM') return 'blue';
  if (record.type === 'SPF') return 'green';
  if (record.type === 'DMARC') return 'purple';
  return 'cyan';
};

const MailAuthDnsRecords: React.FC<MailAuthDnsRecordsProps> = ({ records, onCopyText }) => {
  if (records.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无 DNS 记录" />;
  }

  return (
    <Space direction="vertical" className="full-width" size="middle">
      {records.map((record) => (
        <Descriptions
          key={`${record.type}-${record.name}`}
          bordered
          column={1}
          size="small"
          className="mail-auth-dns-record"
          title={(
            <Space>
              <Tag color={recordColor(record)}>{record.type}</Tag>
              <span>{record.available ? '可发布' : '未生成'}</span>
            </Space>
          )}
        >
          <Descriptions.Item label="主机名">
            <Space.Compact className="full-width">
              <Input value={record.name} readOnly />
              <Button icon={<CopyOutlined />} onClick={() => onCopyText(record.name)} />
            </Space.Compact>
          </Descriptions.Item>
          <Descriptions.Item label="TXT">
            <Space direction="vertical" className="full-width">
              <Input.TextArea value={record.value} autoSize readOnly disabled={!record.available} />
              {record.available ? (
                <Button icon={<CopyOutlined />} onClick={() => onCopyText(record.value)}>
                  复制 TXT
                </Button>
              ) : null}
            </Space>
          </Descriptions.Item>
        </Descriptions>
      ))}
    </Space>
  );
};

export default MailAuthDnsRecords;
