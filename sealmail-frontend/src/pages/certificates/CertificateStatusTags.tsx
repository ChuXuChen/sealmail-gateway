import React from 'react';
import { Space, Tag } from 'antd';
import type { Certificate } from '../../types';

export const CertificateAlgorithmTag: React.FC<{ algorithm?: string }> = ({ algorithm }) => {
  switch (algorithm) {
    case 'SM2':
      return <Tag color="error">SM2</Tag>;
    case 'RSA':
      return <Tag color="processing">RSA</Tag>;
    default:
      return <Tag>{algorithm || '未知'}</Tag>;
  }
};

const CertificateStatusTags: React.FC<{ record: Certificate }> = ({ record }) => (
  <Space>
    <Tag color={record.trusted ? 'success' : 'default'}>
      {record.trusted ? '信任' : '未信'}
    </Tag>
    <Tag color={record.revoked ? 'error' : 'processing'}>
      {record.revoked ? '吊销' : '有效'}
    </Tag>
    {!record.revoked && record.chainUsable === false ? (
      <Tag color="warning">链断</Tag>
    ) : null}
  </Space>
);

export default CertificateStatusTags;
