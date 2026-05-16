import React from 'react';
import { Button, Popover, Select, Space, Typography } from 'antd';
import { DownOutlined } from '@ant-design/icons';
import type { Certificate } from '../../types';
import type { CaFilters } from './caUtils';
import { getDisplayName } from './caUtils';

const { Text } = Typography;

interface CaFilterControlsProps {
  algorithmOptions: { value: string; label: string }[];
  filters: CaFilters;
  roots: Certificate[];
  onReset: () => void;
  onUpdateFilter: <K extends keyof CaFilters>(key: K, value: CaFilters[K]) => void;
}

export interface CaFilterHeaders {
  ca: React.ReactNode;
  algorithm: React.ReactNode;
  status: React.ReactNode;
}

const filterSection = (title: string, hint: string, control: React.ReactNode) => (
  <div className="ca-filter-section">
    <div className="ca-filter-section-head">
      <Text strong className="compact-heading">{title}</Text>
      <div className="ca-filter-hint">{hint}</div>
    </div>
    {control}
  </div>
);

const filterHeader = (label: string, active: boolean, content: React.ReactNode) => (
  <Popover
    trigger="click"
    placement="bottomLeft"
    overlayClassName="ca-filter-popover"
    content={<div className="ca-filter-panel">{content}</div>}
  >
    <Button type="text" size="small" className={`ca-filter-trigger${active ? ' is-active' : ''}`}>
      <Space size={4}>
        <span>{label}</span>
        <DownOutlined />
      </Space>
    </Button>
  </Popover>
);

export const buildCaFilterHeaders = ({
  algorithmOptions,
  filters,
  roots,
  onReset,
  onUpdateFilter,
}: CaFilterControlsProps): CaFilterHeaders => {
  const caHeaderFilter = (
    <div>
      <div className="ca-filter-panel-header">
        <Text strong>CA 过滤</Text>
        <div className="ca-filter-hint">按层级和归属筛选。</div>
      </div>
      {filterSection(
        '类型',
        'Root / Intermediate',
        <Select
          value={filters.type}
          onChange={(value) => onUpdateFilter('type', value)}
          className="full-width"
          options={[
            { value: 'ALL', label: '全部类型' },
            { value: 'ROOT', label: 'Root CA' },
            { value: 'INTERMEDIATE', label: 'Intermediate CA' },
          ]}
        />,
      )}
      {filterSection(
        '所属 Root',
        'Intermediate 归属',
        <Select
          value={filters.rootId}
          onChange={(value) => onUpdateFilter('rootId', value)}
          className="full-width"
          options={[
            { value: 'ALL', label: '全部 Root 归属' },
            ...roots.map((root) => ({
              value: root.id,
              label: getDisplayName(root),
            })),
          ]}
        />,
      )}
      <div className="ca-filter-panel-footer">
        <Button size="small" onClick={onReset}>重置</Button>
      </div>
    </div>
  );

  const algorithmHeaderFilter = (
    <div>
      <div className="ca-filter-panel-header">
        <Text strong>算法过滤</Text>
        <div className="ca-filter-hint">按证书算法筛选。</div>
      </div>
      {filterSection(
        '算法',
        'RSA / SM2',
        <Select
          value={filters.algorithm}
          onChange={(value) => onUpdateFilter('algorithm', value)}
          className="full-width"
          options={algorithmOptions}
        />,
      )}
      <div className="ca-filter-panel-footer">
        <Button size="small" onClick={onReset}>重置</Button>
      </div>
    </div>
  );

  const statusHeaderFilter = (
    <div>
      <div className="ca-filter-panel-header">
        <Text strong>状态过滤</Text>
        <div className="ca-filter-hint">信任、吊销与可签发状态。</div>
      </div>
      {filterSection(
        '证书状态',
        '有效 / 吊销 / 链路',
        <Select
          value={filters.status}
          onChange={(value) => onUpdateFilter('status', value)}
          className="full-width"
          options={[
            { value: 'ALL', label: '全部状态' },
            { value: 'VALID', label: '有效' },
            { value: 'REVOKED', label: '已吊销' },
            { value: 'CHAIN_BROKEN', label: '链路失效' },
          ]}
        />,
      )}
      {filterSection(
        '信任',
        '受信任状态',
        <Select
          value={filters.trust}
          onChange={(value) => onUpdateFilter('trust', value)}
          className="full-width"
          options={[
            { value: 'ALL', label: '全部信任' },
            { value: 'TRUSTED', label: '已信任' },
            { value: 'UNTRUSTED', label: '未信任' },
          ]}
        />,
      )}
      {filterSection(
        '私钥',
        '是否已关联私钥',
        <Select
          value={filters.key}
          onChange={(value) => onUpdateFilter('key', value)}
          className="full-width"
          options={[
            { value: 'ALL', label: '全部私钥' },
            { value: 'WITH_KEY', label: '有私钥' },
            { value: 'WITHOUT_KEY', label: '无私钥' },
          ]}
        />,
      )}
      {filterSection(
        '签发能力',
        '是否可用于 CSR',
        <Select
          value={filters.issuable}
          onChange={(value) => onUpdateFilter('issuable', value)}
          className="full-width"
          options={[
            { value: 'ALL', label: '全部' },
            { value: 'ISSUABLE', label: '可签发 CSR' },
          ]}
        />,
      )}
      <div className="ca-filter-panel-footer">
        <Button size="small" onClick={onReset}>重置</Button>
      </div>
    </div>
  );

  return {
    ca: filterHeader('CA', filters.type !== 'ALL' || filters.rootId !== 'ALL', caHeaderFilter),
    algorithm: filterHeader('算法', filters.algorithm !== 'ALL', algorithmHeaderFilter),
    status: filterHeader(
      '状态',
      filters.status !== 'ALL' || filters.trust !== 'ALL' || filters.key !== 'ALL' || filters.issuable !== 'ALL',
      statusHeaderFilter,
    ),
  };
};
