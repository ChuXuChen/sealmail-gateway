import React from 'react';
import { Button, Space, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { DeleteOutlined, EditOutlined } from '@ant-design/icons';
import type { DlpPolicy, DlpRuleGroup, DlpSelection } from '../../types';
import {
  DataTable,
  EnabledTag,
  confirmDeleteAction,
} from '../../components/Page';
import { directionLabels, joinScope, modeLabels, scopeLabels } from './dlpSelectionUtils';

const { Text } = Typography;

interface GroupTableProps {
  data: DlpRuleGroup[];
  loading: boolean;
  onEdit: (record: DlpRuleGroup) => void;
  onRemove: (id: string) => void | Promise<void>;
}

interface PolicyTableProps {
  data: DlpPolicy[];
  groupById: Map<string, DlpRuleGroup>;
  loading: boolean;
  onEdit: (record: DlpPolicy) => void;
  onRemove: (id: string) => void | Promise<void>;
}

interface LegacySelectionTableProps {
  data: DlpSelection[];
  loading: boolean;
  onEdit: (record: DlpSelection) => void;
  onRemove: (id: string) => void | Promise<void>;
}

export const DlpRuleGroupTable: React.FC<GroupTableProps> = ({ data, loading, onEdit, onRemove }) => {
  const columns: TableColumnsType<DlpRuleGroup> = [
    {
      title: '规则组',
      dataIndex: 'name',
      key: 'name',
      width: 220,
      render: (_: string, record) => (
        <Space direction="vertical" size={2} className="min-width-zero">
          <Text strong ellipsis>{record.name}</Text>
          {record.description ? <Text type="secondary" className="text-micro" ellipsis>{record.description}</Text> : null}
        </Space>
      ),
    },
    { title: '规则数', dataIndex: 'ruleIds', key: 'ruleIds', width: 90, render: (ids: string[]) => ids.length },
    { title: '优先级', dataIndex: 'priority', key: 'priority', width: 90 },
    { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 90, render: (enabled: boolean) => <EnabledTag enabled={enabled} /> },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_: unknown, record: DlpRuleGroup) => (
        <Space size={4} wrap={false}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => onEdit(record)}>编辑</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => confirmDeleteAction('确认删除该规则组？', () => onRemove(record.id), record.name)}>删除</Button>
        </Space>
      ),
    },
  ];

  return (
    <DataTable<DlpRuleGroup>
      rowKey="id"
      loading={loading}
      dataSource={data}
      columns={columns}
      scroll={{ x: 760 }}
      pagination={{ pageSize: 20, total: data.length }}
    />
  );
};

export const DlpPolicyTable: React.FC<PolicyTableProps> = ({
  data,
  groupById,
  loading,
  onEdit,
  onRemove,
}) => {
  const columns: TableColumnsType<DlpPolicy> = [
    {
      title: '策略',
      dataIndex: 'name',
      key: 'name',
      width: 230,
      render: (_: string, record) => (
        <Space direction="vertical" size={2} className="min-width-zero">
          <Space size={6} wrap>
            <Text strong ellipsis>{record.name}</Text>
            <EnabledTag enabled={record.enabled} />
          </Space>
          {record.description ? <Text type="secondary" className="text-micro" ellipsis>{record.description}</Text> : null}
        </Space>
      ),
    },
    {
      title: '适用范围',
      key: 'scope',
      width: 260,
      render: (_: unknown, record) => (
        <Space direction="vertical" size={2} className="min-width-zero">
          <Text className="text-micro" ellipsis>发件域：{joinScope(record.senderDomains)}</Text>
          <Text className="text-micro" ellipsis>收件域：{joinScope(record.recipientDomains)}</Text>
          <Text className="text-micro" ellipsis>方向：{record.direction ? directionLabels[record.direction] || record.direction : '全部'}</Text>
        </Space>
      ),
    },
    {
      title: '执行方式',
      dataIndex: 'mode',
      key: 'mode',
      width: 110,
      render: (value: string) => <Tag color={value === 'ENFORCE' ? 'red' : 'blue'}>{modeLabels[value] || value}</Tag>,
    },
    {
      title: '规则组',
      dataIndex: 'ruleGroupIds',
      key: 'ruleGroupIds',
      width: 180,
      render: (ids: string[]) => (
        <Space size={[0, 4]} wrap>
          {ids.length === 0 ? '-' : ids.slice(0, 2).map((id) => <Tag key={id}>{groupById.get(id)?.name || id}</Tag>)}
          {ids.length > 2 ? <Tag>+{ids.length - 2}</Tag> : null}
        </Space>
      ),
    },
    { title: '优先级', dataIndex: 'priority', key: 'priority', width: 90 },
    { title: '附件', dataIndex: 'attachmentRequired', key: 'attachmentRequired', width: 80, render: (value: boolean) => value ? <Tag>需要</Tag> : '-' },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_: unknown, record: DlpPolicy) => (
        <Space size={4} wrap={false}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => onEdit(record)}>编辑</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => confirmDeleteAction('确认删除该策略？', () => onRemove(record.id), record.name)}>删除</Button>
        </Space>
      ),
    },
  ];

  return (
    <DataTable<DlpPolicy>
      rowKey="id"
      loading={loading}
      dataSource={data}
      columns={columns}
      scroll={{ x: 1080 }}
      pagination={{ pageSize: 20, total: data.length }}
    />
  );
};

export const DlpLegacySelectionTable: React.FC<LegacySelectionTableProps> = ({
  data,
  loading,
  onEdit,
  onRemove,
}) => {
  const columns: TableColumnsType<DlpSelection> = [
    { title: '范围', dataIndex: 'scopeType', key: 'scopeType', width: 120, render: (value: string) => scopeLabels[value] || value },
    { title: '值', dataIndex: 'scopeValue', key: 'scopeValue', width: 220, render: (value?: string) => value || '*' },
    { title: '规则', dataIndex: 'patternIds', key: 'patternIds', width: 120, render: (ids?: string[] | null) => ids == null ? <Tag color="blue">全部规则</Tag> : `${ids.length} 条` },
    { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 90, render: (enabled: boolean) => <EnabledTag enabled={enabled} /> },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      render: (_: unknown, record: DlpSelection) => (
        <Space size={4} wrap={false}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => onEdit(record)}>编辑</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => confirmDeleteAction('确认删除该范围？', () => onRemove(record.id), record.scopeValue || scopeLabels[record.scopeType])}>删除</Button>
        </Space>
      ),
    },
  ];

  return (
    <DataTable<DlpSelection>
      rowKey="id"
      loading={loading}
      dataSource={data}
      columns={columns}
      scroll={{ x: 720 }}
      pagination={{ pageSize: 20, total: data.length }}
    />
  );
};
