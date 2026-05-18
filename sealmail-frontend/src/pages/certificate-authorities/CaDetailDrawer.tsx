import React from 'react';
import { Button, Descriptions, Space, Typography } from 'antd';
import { LockOutlined, UnlockOutlined } from '@ant-design/icons';
import type { Certificate } from '../../types';
import { DetailDrawer } from '../../components/Page';
import CertificateIdentityValue from '../certificates/CertificateIdentityValue';
import type { ConfirmActionType } from './caUtils';
import { formatDate, getDisplayName } from './caUtils';
import CaStatusTags, { CaAlgorithmTag, CaRoleTag } from './CaStatusTags';

const { Text } = Typography;

interface CaDetailDrawerProps {
  detail: Certificate | null;
  rootById: Map<string, Certificate>;
  onClose: () => void;
  onConfirmAction: (type: ConfirmActionType, record: Certificate, closeDetail?: boolean) => void;
  onTrust: (id: string) => unknown | Promise<unknown>;
}

const CaDetailDrawer: React.FC<CaDetailDrawerProps> = ({
  detail,
  rootById,
  onClose,
  onConfirmAction,
  onTrust,
}) => (
  <DetailDrawer title="CA 详情" width={640} open={!!detail} onClose={onClose}>
    {detail ? (
      <Space direction="vertical" size={14} className="full-width">
        <div className="ca-detail-summary">
          <div className="ca-detail-summary__main">
            <Text strong className="ca-detail-summary__name" ellipsis={{ tooltip: getDisplayName(detail) }}>
              {getDisplayName(detail)}
            </Text>
            <Text type="secondary" className="ca-detail-summary__dn" ellipsis={{ tooltip: detail.subjectDn }}>
              {detail.subjectDn || '-'}
            </Text>
          </div>
          <div className="ca-detail-summary__meta">
            <CaRoleTag cert={detail} />
            <CaAlgorithmTag algorithm={detail.algorithm} />
            <CaStatusTags cert={detail} />
          </div>
          <div className="ca-detail-summary__actions">
            {detail.trusted ? (
              <Button
                size="small"
                icon={<LockOutlined />}
                onClick={() => onConfirmAction('untrust', detail, true)}
              >
                撤销信任
              </Button>
            ) : (
              <Button
                size="small"
                icon={<UnlockOutlined />}
                onClick={async () => {
                  await onTrust(detail.id);
                  onClose();
                }}
              >
                标记信任
              </Button>
            )}
          </div>
        </div>

        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="别名">{detail.alias || '-'}</Descriptions.Item>
          <Descriptions.Item label="Subject DN">{detail.subjectDn || '-'}</Descriptions.Item>
          <Descriptions.Item label="Issuer DN">{detail.issuerDn || '-'}</Descriptions.Item>
          <Descriptions.Item label="所属 Root">
            {detail.pathLenConstraint === 1
              ? '自身'
              : detail.issuerCertId
                ? getDisplayName(rootById.get(detail.issuerCertId) || detail)
                : '-'}
          </Descriptions.Item>
          <Descriptions.Item label="证书指纹">
            <CertificateIdentityValue value={detail.thumbprint} />
          </Descriptions.Item>
          <Descriptions.Item label="序列号">
            <CertificateIdentityValue value={detail.serialNumber} />
          </Descriptions.Item>
          <Descriptions.Item label="SKI">
            <CertificateIdentityValue value={detail.subjectKeyIdentifier} />
          </Descriptions.Item>
          <Descriptions.Item label="父 CA ID">
            <CertificateIdentityValue
              value={detail.pathLenConstraint === 1 ? null : detail.issuerCertId}
              emptyText={detail.pathLenConstraint === 1 ? 'self-signed' : '-'}
            />
          </Descriptions.Item>
          <Descriptions.Item label="有效期">{formatDate(detail.notBefore)} - {formatDate(detail.notAfter)}</Descriptions.Item>
          <Descriptions.Item label="pathLen">{detail.pathLenConstraint ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="KeyUsage">{detail.keyUsages?.join(', ') || '-'}</Descriptions.Item>
          <Descriptions.Item label="EKU">{detail.extendedKeyUsages?.join(', ') || '-'}</Descriptions.Item>
        </Descriptions>
      </Space>
    ) : null}
  </DetailDrawer>
);

export default CaDetailDrawer;
