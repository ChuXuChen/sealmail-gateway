import { createElement, type ReactNode } from 'react';
import {
  ApiOutlined,
  FileProtectOutlined,
  KeyOutlined,
  LockOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import type { SystemSettings } from '../../types';

export type CryptoCapability = SystemSettings['cryptoCapabilities'][number];

const suiteCategoryNames = new Set(['S/MIME STANDARD 套件', 'S/MIME GM 套件']);

export const cryptoCapabilityDescription = (category: string) => {
  const descriptions: Record<string, string> = {
    'S/MIME STANDARD 套件': '标准 S/MIME 的默认与可选能力，主要用于跨组织兼容。',
    'S/MIME GM 套件': '国密 S/MIME 的默认与可选能力，主要用于国密合规域。',
    哈希算法: '用于摘要、签名散列和内容完整性校验。',
    密钥交换: '用于收件人密钥封装和会话密钥保护。',
    签名算法: '用于出站签名和入站验签。',
  };

  return descriptions[category] || '当前运行配置返回的加密能力。';
};

export const cryptoCapabilityProfile = (capability: CryptoCapability) => {
  const value = `${capability.category} ${capability.algorithms.join(' ')}`.toUpperCase();
  if (value.includes('GM') || value.includes('SM2') || value.includes('SM3') || value.includes('SM4')) {
    return { label: '国密', tone: 'gm' as const };
  }
  if (value.includes('STANDARD') || value.includes('RSA') || value.includes('AES') || value.includes('SHA')) {
    return { label: '标准', tone: 'standard' as const };
  }
  return { label: '通用', tone: 'default' as const };
};

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

export const cryptoCapabilityIcon = (capability: CryptoCapability): ReactNode => {
  if (suiteCategoryNames.has(capability.category)) {
    return createElement(LockOutlined);
  }
  if (capability.category.includes('签名')) {
    return createElement(SafetyCertificateOutlined);
  }
  if (capability.category.includes('密钥')) {
    return createElement(KeyOutlined);
  }
  if (capability.category.includes('哈希')) {
    return createElement(ApiOutlined);
  }
  return createElement(FileProtectOutlined);
};
