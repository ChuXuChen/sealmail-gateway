import React from 'react';
import { Space, Tag, Typography } from 'antd';
import {
  cryptoCapabilityColor,
  cryptoCapabilityIcon,
  type CryptoCapability,
} from './cryptoCapabilityPresentation';

const { Text } = Typography;

interface CryptoCapabilityTagsProps {
  capabilities?: CryptoCapability[];
  emptyText?: string;
  includeCategory?: boolean;
}

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
