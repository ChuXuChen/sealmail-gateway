import React from 'react';
import { Descriptions, Tag } from 'antd';
import type { DomainConfig } from '../../types';
import { DetailDrawer } from '../../components/Page';

interface DomainConfigDetailDrawerProps {
  domain: DomainConfig | null;
  open: boolean;
  onClose: () => void;
}

const DomainConfigDetailDrawer: React.FC<DomainConfigDetailDrawerProps> = ({
  domain,
  open,
  onClose,
}) => (
  <DetailDrawer title="域名配置详情" width={600} open={open} onClose={onClose}>
    {domain ? (
      <Descriptions column={1} bordered>
        <Descriptions.Item label="域名">{domain.domain}</Descriptions.Item>
        <Descriptions.Item label="域名类型">
          {domain.localDomain ? (
            <Tag color="blue">本地域名</Tag>
          ) : (
            <Tag color="default">远程域名</Tag>
          )}
        </Descriptions.Item>
        <Descriptions.Item label="加密策略">
          <Tag color={domain.encryptionPolicy === 'MANDATORY' ? 'red' : domain.encryptionPolicy === 'ALLOW' ? 'green' : 'default'}>
            {domain.encryptionPolicyDisplayName}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="算法偏好">
          <Tag color={domain.preferredAlgorithm === 'GM_ONLY' ? 'error' : domain.preferredAlgorithm === 'STANDARD_ONLY' ? 'processing' : 'blue'}>
            {domain.preferredAlgorithmDisplayName || '自动选择'}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="邮件签名">
          {domain.signingEnabled ? <Tag color="success">已启用</Tag> : <Tag color="default">未启用</Tag>}
        </Descriptions.Item>
        <Descriptions.Item label="DKIM签名">
          {domain.dkimEnabled ? <Tag color="success">已启用</Tag> : <Tag color="default">未启用</Tag>}
        </Descriptions.Item>
        <Descriptions.Item label="外部发送地址">
          {domain.localDomain ? (
            <Tag color="default">本地域名不使用</Tag>
          ) : domain.deliveryHost && domain.deliveryPort ? (
            <Tag color="processing">{domain.deliveryHost}:{domain.deliveryPort}</Tag>
          ) : (
            <Tag color="default">默认出站投递</Tag>
          )}
        </Descriptions.Item>
        <Descriptions.Item label="配置状态">
          {domain.active ? <Tag color="green">启用</Tag> : <Tag color="red">禁用</Tag>}
        </Descriptions.Item>
      </Descriptions>
    ) : null}
  </DetailDrawer>
);

export default DomainConfigDetailDrawer;
