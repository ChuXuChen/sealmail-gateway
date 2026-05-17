import React from 'react';
import { Space, Tag, Typography } from 'antd';
import { FileProtectOutlined, LockOutlined } from '@ant-design/icons';
import type { SystemSettings } from '../../types';

const { Text } = Typography;

type CryptoCapability = SystemSettings['cryptoCapabilities'][number];

interface CryptoCapabilityTagsProps {
  capabilities?: CryptoCapability[];
  emptyText?: string;
  includeCategory?: boolean;
}

const suiteCategoryNames = new Set(['S/MIME STANDARD 套件', 'S/MIME GM 套件']);

export const cryptoCapabilityColor = (capability: CryptoCapability, algorithm: string) => {
  const value = `${capability.category} ${algorithm}`.toUpperCase();
  if (value.includes('GM') || value.includes('SM2') || value.includes('SM3') || value.includes('SM4')) {
    return 'error';
  }
  if (value.includes('STANDARD') || value.includes('AES') || value.includes('RSA') || value.includes('SHA-256')) {
    return 'processing';
  }
  return 'default';
};

export const cryptoCapabilityIcon = (capability: CryptoCapability) =>
  suiteCategoryNames.has(capability.category) ? <LockOutlined /> : <FileProtectOutlined />;

const CryptoCapabilityTags: React.FC<CryptoCapabilityTagsProps> = ({
  capabilities,
  emptyText = '暂无算法能力数据',
  includeCategory = true,
}) => {
  const items = capabilities?.flatMap((capability) =>
    capability.algorithms.map((algorithm) => (
      <Tag
        key={`${capability.category}-${algorithm}`}
        color={cryptoCapabilityColor(capability, algorithm)}
        icon={cryptoCapabilityIcon(capability)}
        className="settings-tag"
      >
        {includeCategory ? `${capability.category}: ${algorithm}` : algorithm}
      </Tag>
    )),
  );

  return (
    <Space size={8} wrap>
      {items?.length ? items : <Text type="secondary">{emptyText}</Text>}
    </Space>
  );
};

export default CryptoCapabilityTags;
