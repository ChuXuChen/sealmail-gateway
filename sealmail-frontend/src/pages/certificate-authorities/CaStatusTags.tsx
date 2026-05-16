import React from 'react';
import { Space, Tag } from 'antd';
import type { Certificate } from '../../types';
import { getUnavailableSigningReason, roleOf } from './caUtils';

export const CaAlgorithmTag: React.FC<{ algorithm?: string }> = ({ algorithm }) => {
  if (algorithm === 'SM2') return <Tag color="magenta">SM2</Tag>;
  if (algorithm === 'RSA') return <Tag color="blue">RSA</Tag>;
  return <Tag>{algorithm || '未知'}</Tag>;
};

export const CaRoleTag: React.FC<{ cert: Certificate }> = ({ cert }) => (
  <Tag color={cert.pathLenConstraint === 1 ? 'purple' : 'cyan'}>{roleOf(cert)}</Tag>
);

const TrustTag: React.FC<{ cert: Certificate }> = ({ cert }) => (
  <Tag color={cert.trusted ? 'green' : 'default'}>{cert.trusted ? '信任' : '未信'}</Tag>
);

const RevocationTag: React.FC<{ cert: Certificate }> = ({ cert }) => (
  <Tag color={cert.revoked ? 'red' : 'processing'}>{cert.revoked ? '吊销' : '有效'}</Tag>
);

const PrivateKeyTag: React.FC<{ cert: Certificate }> = ({ cert }) => (
  <Tag color={cert.hasPrivateKey ? 'green' : 'default'}>{cert.hasPrivateKey ? '私钥' : '无钥'}</Tag>
);

const ChainTag: React.FC<{ cert: Certificate }> = ({ cert }) => (
  <Tag color={cert.chainUsable === false ? 'orange' : 'green'}>
    {cert.chainUsable === false ? '链断' : '链通'}
  </Tag>
);

const IssuableTag: React.FC<{ cert: Certificate }> = ({ cert }) => {
  if (cert.pathLenConstraint === 1) {
    return <Tag color="purple">可签</Tag>;
  }

  const reason = getUnavailableSigningReason(cert);
  return reason ? <Tag color="default">禁签</Tag> : <Tag color="success">可签</Tag>;
};

const CaStatusTags: React.FC<{ cert: Certificate }> = ({ cert }) => (
  <Space size={[0, 4]} wrap>
    <TrustTag cert={cert} />
    <RevocationTag cert={cert} />
    <PrivateKeyTag cert={cert} />
    <ChainTag cert={cert} />
    <IssuableTag cert={cert} />
  </Space>
);

export default CaStatusTags;
