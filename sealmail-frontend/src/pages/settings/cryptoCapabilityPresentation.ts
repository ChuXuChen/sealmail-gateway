import { createElement, type ReactNode } from 'react';
import { FileProtectOutlined, LockOutlined } from '@ant-design/icons';
import type { SystemSettings } from '../../types';

export type CryptoCapability = SystemSettings['cryptoCapabilities'][number];

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

export const cryptoCapabilityIcon = (capability: CryptoCapability): ReactNode =>
  suiteCategoryNames.has(capability.category)
    ? createElement(LockOutlined)
    : createElement(FileProtectOutlined);
