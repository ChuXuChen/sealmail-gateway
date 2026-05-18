import React from 'react';
import { Empty, Space, Tag, Typography } from 'antd';
import {
  cryptoCapabilityColor,
  cryptoCapabilityDescription,
  cryptoCapabilityIcon,
  cryptoCapabilityProfile,
  type CryptoCapability,
} from './cryptoCapabilityPresentation';

const { Text } = Typography;

interface CryptoCapabilityTagsProps {
  capabilities?: CryptoCapability[];
  compact?: boolean;
  emptyText?: string;
  includeCategory?: boolean;
  layout?: 'matrix' | 'tags';
}

const CryptoCapabilityTags: React.FC<CryptoCapabilityTagsProps> = ({
  capabilities,
  compact = false,
  emptyText = '暂无算法能力数据',
  includeCategory = true,
  layout = 'matrix',
}) => {
  if (!capabilities?.length) {
    return layout === 'tags'
      ? <Text type="secondary">{emptyText}</Text>
      : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />;
  }

  if (layout === 'matrix') {
    return (
      <div className={['crypto-capability-matrix', compact ? 'crypto-capability-matrix--compact' : ''].filter(Boolean).join(' ')}>
        {capabilities.map((capability) => {
          const profile = cryptoCapabilityProfile(capability);

          return (
            <div className="crypto-capability-row" key={capability.category}>
              <div className="crypto-capability-row__head">
                <span className={`crypto-capability-row__icon crypto-capability-row__icon--${profile.tone}`}>
                  {cryptoCapabilityIcon(capability)}
                </span>
                <div className="crypto-capability-row__title-wrap">
                  <Space size={6} wrap>
                    <Text className="crypto-capability-row__title">{capability.category}</Text>
                    <Tag className="settings-tag" color={profile.tone === 'gm' ? 'error' : profile.tone === 'standard' ? 'processing' : 'default'}>
                      {profile.label}
                    </Tag>
                  </Space>
                  <Text className="crypto-capability-row__description">
                    {cryptoCapabilityDescription(capability.category)}
                  </Text>
                </div>
              </div>
              <div className="crypto-capability-row__algorithms">
                {capability.algorithms.map((algorithm) => (
                  <Tag
                    className="crypto-algorithm-token"
                    color={cryptoCapabilityColor(capability, algorithm)}
                    key={`${capability.category}-${algorithm}`}
                  >
                    {algorithm}
                  </Tag>
                ))}
              </div>
              <div className="crypto-capability-row__count">
                <span>{capability.algorithms.length}</span>
                <Text type="secondary">项</Text>
              </div>
            </div>
          );
        })}
      </div>
    );
  }

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
      {items}
    </Space>
  );
};

export default CryptoCapabilityTags;
