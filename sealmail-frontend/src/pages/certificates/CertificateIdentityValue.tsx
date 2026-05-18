import React, { useMemo, useState } from 'react';
import { Button, Typography } from 'antd';
import { DownOutlined, UpOutlined } from '@ant-design/icons';

const { Text } = Typography;

interface CertificateIdentityValueProps {
  emptyText?: string;
  label?: React.ReactNode;
  value?: string | null;
}

const compactValue = (value: string) => {
  const normalized = value.replace(/[:\s-]/g, '');
  if (normalized.length <= 28) {
    return value;
  }
  return `${normalized.slice(0, 12)} ${normalized.slice(12, 20)} ... ${normalized.slice(-10)}`;
};

const groupedValue = (value: string) => {
  const normalized = value.replace(/[:\s-]/g, '');
  if (normalized.length < 24) {
    return value;
  }

  return normalized.match(/.{1,4}/g)?.join(' ') || value;
};

const CertificateIdentityValue: React.FC<CertificateIdentityValueProps> = ({
  emptyText = '-',
  label,
  value,
}) => {
  const [expanded, setExpanded] = useState(false);
  const displayValue = useMemo(() => (value ? compactValue(value) : emptyText), [emptyText, value]);
  const fullValue = useMemo(() => (value ? groupedValue(value) : emptyText), [emptyText, value]);
  const canExpand = !!value && fullValue !== displayValue;

  return (
    <div className="certificate-identity-value">
      {label ? <Text className="certificate-identity-value__label">{label}</Text> : null}
      <div className="certificate-identity-value__line">
        <Text copyable={value ? { text: value } : false} className="certificate-identity-value__compact">
          {displayValue}
        </Text>
        {canExpand ? (
          <Button
            type="link"
            size="small"
            className="certificate-identity-value__toggle"
            icon={expanded ? <UpOutlined /> : <DownOutlined />}
            onClick={() => setExpanded((current) => !current)}
          >
            {expanded ? '收起' : '完整'}
          </Button>
        ) : null}
      </div>
      {expanded ? (
        <Text className="certificate-identity-value__full">
          {fullValue}
        </Text>
      ) : null}
    </div>
  );
};

export default CertificateIdentityValue;
