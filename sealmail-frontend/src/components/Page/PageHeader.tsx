import React from 'react';
import { Space, Typography } from 'antd';

const { Text, Title } = Typography;

interface PageHeaderProps {
  actions?: React.ReactNode;
  children?: React.ReactNode;
  description?: React.ReactNode;
  extra?: React.ReactNode;
  title: React.ReactNode;
}

const PageHeader: React.FC<PageHeaderProps> = ({
  actions,
  children,
  description,
  extra,
  title,
}) => (
  <div className="page-header">
    <div className="page-header__main">
      <Space size={8} align="center" wrap>
        <Title level={3} className="page-header__title">
          {title}
        </Title>
        {extra}
      </Space>
      {description ? (
        <Text type="secondary" className="page-header__description">
          {description}
        </Text>
      ) : null}
      {children}
    </div>
    {actions ? <div className="page-header__actions">{actions}</div> : null}
  </div>
);

export default PageHeader;
