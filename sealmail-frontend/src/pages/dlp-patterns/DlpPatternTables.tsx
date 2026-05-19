import React from 'react';
import { Button, Space, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { DeleteOutlined, EditOutlined } from '@ant-design/icons';
import type { DlpEdmDataset, DlpEvaluation, DlpFingerprintLibrary, DlpRule } from '../../types';
import {
  DataTable,
  DlpActionTag,
  EnabledTag,
  confirmDeleteAction,
} from '../../components/Page';
import { ruleTypeMeta } from './dlpPatternUtils';

const { Text } = Typography;

interface DlpRuleTableProps {
  data: DlpRule[];
  loading: boolean;
  onEdit: (record: DlpRule) => void;
  onRemove: (id: string) => void | Promise<void>;
}

interface DlpDatasetTableProps<T> {
  data: T[];
  onRemove: (id: string) => void | Promise<void>;
}

interface DlpEvidenceTableProps {
  data: DlpEvaluation['evidence'];
}

export const DlpRuleTable: React.FC<DlpRuleTableProps> = ({
  data,
  loading,
  onEdit,
  onRemove,
}) => {
  const columns: TableColumnsType<DlpRule> = [
    {
      title: '规则',
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
    { title: '类型', dataIndex: 'type', key: 'type', width: 110, render: (value: string) => <Tag>{ruleTypeMeta[value]?.title || value}</Tag> },
    {
      title: '匹配资产',
      dataIndex: 'pattern',
      key: 'pattern',
      width: 260,
      ellipsis: true,
      render: (_, record) => record.builtinCode || record.pattern || '-',
    },
    {
      title: '动作',
      dataIndex: 'action',
      key: 'action',
      width: 110,
      render: (action: string) => <DlpActionTag action={action} />,
    },
    { title: '级别', dataIndex: 'severity', key: 'severity', width: 70 },
    { title: '证据', dataIndex: 'maxEvidenceCount', key: 'maxEvidenceCount', width: 70 },
    { title: '优先级', dataIndex: 'priority', key: 'priority', width: 80 },
    { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 80, render: (enabled: boolean) => <EnabledTag enabled={enabled} /> },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_: unknown, record: DlpRule) => (
        <Space size={4} wrap={false}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => onEdit(record)}>编辑</Button>
          <Button type="link" size="small" danger icon={<DeleteOutlined />} onClick={() => confirmDeleteAction('确认删除该规则？', () => onRemove(record.id), record.name)}>删除</Button>
        </Space>
      ),
    },
  ];

  return (
    <DataTable<DlpRule>
      rowKey="id"
      loading={loading}
      dataSource={data}
      columns={columns}
      scroll={{ x: 1160 }}
      pagination={{ pageSize: 20, total: data.length }}
    />
  );
};

export const DlpEdmDatasetTable: React.FC<DlpDatasetTableProps<DlpEdmDataset>> = ({ data, onRemove }) => {
  const columns: TableColumnsType<DlpEdmDataset> = [
    {
      title: '数据集',
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
    { title: '哈希值', dataIndex: 'valueCount', key: 'valueCount', width: 100 },
    { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 90, render: (enabled: boolean) => <EnabledTag enabled={enabled} /> },
    {
      title: '操作',
      key: 'actions',
      width: 110,
      render: (_: unknown, record: DlpEdmDataset) => (
        <Button
          type="link"
          size="small"
          danger
          icon={<DeleteOutlined />}
          onClick={() => confirmDeleteAction('确认删除该 EDM 数据集？', () => onRemove(record.id), record.name)}
        >
          删除
        </Button>
      ),
    },
  ];

  return (
    <DataTable<DlpEdmDataset>
      rowKey="id"
      dataSource={data}
      columns={columns}
      pagination={{ pageSize: 10, total: data.length }}
    />
  );
};

export const DlpFingerprintLibraryTable: React.FC<DlpDatasetTableProps<DlpFingerprintLibrary>> = ({ data, onRemove }) => {
  const columns: TableColumnsType<DlpFingerprintLibrary> = [
    {
      title: '指纹库',
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
    { title: '文档', dataIndex: 'documentCount', key: 'documentCount', width: 90 },
    { title: '片段', dataIndex: 'chunkCount', key: 'chunkCount', width: 90 },
    { title: '状态', dataIndex: 'enabled', key: 'enabled', width: 90, render: (enabled: boolean) => <EnabledTag enabled={enabled} /> },
    {
      title: '操作',
      key: 'actions',
      width: 110,
      render: (_: unknown, record: DlpFingerprintLibrary) => (
        <Button
          type="link"
          size="small"
          danger
          icon={<DeleteOutlined />}
          onClick={() => confirmDeleteAction('确认删除该文档指纹库？', () => onRemove(record.id), record.name)}
        >
          删除
        </Button>
      ),
    },
  ];

  return (
    <DataTable<DlpFingerprintLibrary>
      rowKey="id"
      dataSource={data}
      columns={columns}
      pagination={{ pageSize: 10, total: data.length }}
    />
  );
};

export const DlpEvidenceTable: React.FC<DlpEvidenceTableProps> = ({ data }) => {
  const columns: TableColumnsType<DlpEvaluation['evidence'][number]> = [
    { title: '规则', dataIndex: 'ruleName', key: 'ruleName', width: 180, ellipsis: true },
    { title: '位置', dataIndex: 'partKind', key: 'partKind', width: 120 },
    { title: '级别', dataIndex: 'severity', key: 'severity', width: 70 },
    { title: '动作', dataIndex: 'action', key: 'action', width: 110, render: (action: string) => <DlpActionTag action={action} /> },
    { title: '证据', dataIndex: 'maskedSnippet', key: 'maskedSnippet', ellipsis: true, render: (value?: string) => value || '-' },
  ];

  return (
    <DataTable<DlpEvaluation['evidence'][number]>
      rowKey={(record) => record.id || `${record.ruleId}-${record.startOffset}-${record.endOffset}`}
      dataSource={data}
      columns={columns}
      pagination={false}
      scroll={{ x: 680 }}
    />
  );
};
