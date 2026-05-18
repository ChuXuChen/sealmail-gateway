import React from 'react';
import { Alert, Button, Descriptions, Popconfirm, Space, Tag } from 'antd';
import {
  CopyOutlined,
  DownloadOutlined,
  FileProtectOutlined,
  KeyOutlined,
  LockOutlined,
  SafetyOutlined,
  UnlockOutlined,
} from '@ant-design/icons';
import type { Certificate } from '../../types';
import { DetailDrawer } from '../../components/Page';
import { formatDate } from './certificateUtils';
import CertificateIdentityValue from './CertificateIdentityValue';
import CertificateStatusTags, { CertificateAlgorithmTag } from './CertificateStatusTags';

interface CertificateDetailDrawerProps {
  certificate: Certificate | null;
  open: boolean;
  onClose: () => void;
  onCopyPem: (record: Certificate) => unknown | Promise<unknown>;
  onDownloadPem: (record: Certificate) => unknown | Promise<unknown>;
  onTrust: (id: string) => unknown | Promise<unknown>;
  onUntrust: (id: string) => unknown | Promise<unknown>;
}

const CertificateDetailDrawer: React.FC<CertificateDetailDrawerProps> = ({
  certificate,
  open,
  onClose,
  onCopyPem,
  onDownloadPem,
  onTrust,
  onUntrust,
}) => (
  <DetailDrawer title="证书详情" width={600} open={open} onClose={onClose}>
    {certificate ? (
      <Space direction="vertical" size={12} className="full-width">
        <Alert
          type="info"
          showIcon
          message="仅可导出公开证书 PEM；托管私钥不可导出，对端网关只导入公开证书。"
        />
        <Space wrap>
          <Button icon={<CopyOutlined />} onClick={() => onCopyPem(certificate)}>
            复制 PEM
          </Button>
          <Button icon={<DownloadOutlined />} onClick={() => onDownloadPem(certificate)}>
            下载 PEM
          </Button>
        </Space>
        <Descriptions column={1} bordered>
          <Descriptions.Item label="别名">{certificate.alias || '-'}</Descriptions.Item>
          <Descriptions.Item label="所有者邮箱">{certificate.ownerEmail}</Descriptions.Item>
          <Descriptions.Item label="算法类型">
            <CertificateAlgorithmTag algorithm={certificate.algorithm} />
          </Descriptions.Item>
          <Descriptions.Item label="颁发者DN">{certificate.issuerDn || '-'}</Descriptions.Item>
          <Descriptions.Item label="主体DN">{certificate.subjectDn || '-'}</Descriptions.Item>
          <Descriptions.Item label="序列号">
            <CertificateIdentityValue value={certificate.serialNumber} />
          </Descriptions.Item>
          <Descriptions.Item label="指纹">
            <CertificateIdentityValue value={certificate.thumbprint} />
          </Descriptions.Item>
          <Descriptions.Item label="有效期">
            {formatDate(certificate.notBefore)} - {formatDate(certificate.notAfter)}
          </Descriptions.Item>
          <Descriptions.Item label="密钥用途">
            {certificate.keyUsages?.join(', ') || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="签名能力">
            {certificate.suitableForSigning ? (
              <Tag color="success" icon={<FileProtectOutlined />}>可签</Tag>
            ) : (
              <Tag>禁签</Tag>
            )}
          </Descriptions.Item>
          <Descriptions.Item label="加密能力">
            {certificate.suitableForEncryption ? (
              <Tag color="success" icon={<SafetyOutlined />}>可加密</Tag>
            ) : (
              <Tag>禁加密</Tag>
            )}
          </Descriptions.Item>
          <Descriptions.Item label="托管私钥">
            {certificate.hasPrivateKey ? (
              <Tag color="success" icon={<KeyOutlined />}>网关托管</Tag>
            ) : (
              <Tag>仅公开证书</Tag>
            )}
          </Descriptions.Item>
          <Descriptions.Item label="状态">
            <Space>
              <CertificateStatusTags record={certificate} />
              {certificate.trusted ? (
                <Popconfirm
                  title="撤销此证书的信任？"
                  onConfirm={async () => {
                    await onUntrust(certificate.id);
                    onClose();
                  }}
                >
                  <Button size="small" icon={<LockOutlined />}>撤销信任</Button>
                </Popconfirm>
              ) : (
                <Button
                  size="small"
                  icon={<UnlockOutlined />}
                  onClick={async () => {
                    await onTrust(certificate.id);
                    onClose();
                  }}
                >
                  标记信任
                </Button>
              )}
            </Space>
          </Descriptions.Item>
        </Descriptions>
      </Space>
    ) : null}
  </DetailDrawer>
);

export default CertificateDetailDrawer;
