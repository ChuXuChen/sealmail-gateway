import React from 'react';
import { Button, Space, Tag } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';

interface ActiveFilter {
  key: string;
  label: React.ReactNode;
  onClose?: () => void;
  value: React.ReactNode;
}

interface FilterBarProps {
  activeFilters?: ActiveFilter[];
  actions?: React.ReactNode;
  children?: React.ReactNode;
  className?: string;
  onRefresh?: () => void;
  onReset?: () => void;
  refreshLoading?: boolean;
  resetDisabled?: boolean;
}

const FilterBar: React.FC<FilterBarProps> = ({
  activeFilters,
  actions,
  children,
  className,
  onRefresh,
  onReset,
  refreshLoading = false,
  resetDisabled = false,
}) => {
  const classes = ['filter-bar', className].filter(Boolean).join(' ');
  const hasDefaultActions = Boolean(onRefresh || onReset);

  return (
    <div className={classes}>
      <div className="filter-bar__main">
        {children ? <div className="filter-bar__controls">{children}</div> : null}
        {activeFilters?.length ? (
          <div className="filter-bar__active" aria-label="当前筛选">
            <span className="filter-bar__active-label">当前筛选</span>
            {activeFilters.map((filter) => (
              <Tag
                key={filter.key}
                closable={Boolean(filter.onClose)}
                onClose={filter.onClose}
              >
                {filter.label}: {filter.value}
              </Tag>
            ))}
          </div>
        ) : null}
      </div>
      {actions || hasDefaultActions ? (
        <div className="filter-bar__actions">
          <Space wrap>
            {actions}
            {onReset ? (
              <Button disabled={resetDisabled} onClick={onReset}>
                重置
              </Button>
            ) : null}
            {onRefresh ? (
              <Button icon={<ReloadOutlined />} loading={refreshLoading} onClick={onRefresh}>
                刷新
              </Button>
            ) : null}
          </Space>
        </div>
      ) : null}
    </div>
  );
};

export default FilterBar;
