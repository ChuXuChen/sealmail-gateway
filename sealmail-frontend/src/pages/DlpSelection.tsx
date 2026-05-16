import React, { useCallback, useEffect, useState } from 'react';
import { Button, Form, Input, Modal, Select, Space, Switch, Tag, message } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { dlpApi } from '../api/client';
import { getApiErrorMessage } from '../api/errors';
import { DlpPattern, DlpSelection } from '../types';
import {
  DataTable,
  EnabledTag,
  PageHeader,
  PageShell,
  confirmDeleteAction,
} from '../components/Page';

const scopeLabels: Record<string, string> = {
  GLOBAL: '全局',
  SENDER_DOMAIN: '发件域',
  RECIPIENT_DOMAIN: '收件域',
};

const DlpSelection: React.FC = () => {
  const [data, setData] = useState<DlpSelection[]>([]);
  const [patterns, setPatterns] = useState<DlpPattern[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DlpSelection | null>(null);
  const [scopeType, setScopeType] = useState<string>('GLOBAL');
  const [patternMode, setPatternMode] = useState<'ALL' | 'SELECTED'>('ALL');
  const [form] = Form.useForm();

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await dlpApi.listSelections();
      setData(response.data.data);
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载 DLP 生效范围失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  const loadPatterns = useCallback(async () => {
    try {
      const response = await dlpApi.listPatterns();
      setPatterns(response.data.data);
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载 DLP 规则失败'));
    }
  }, []);

  useEffect(() => {
    void loadData();
    void loadPatterns();
  }, [loadData, loadPatterns]);

  const showCreate = () => {
    setEditing(null);
    setScopeType('GLOBAL');
    setPatternMode('ALL');
    form.resetFields();
    form.setFieldsValue({ scopeType: 'GLOBAL', scopeValue: '', patternMode: 'ALL', patternIds: [], enabled: true });
    setModalOpen(true);
  };

  const showEdit = (record: DlpSelection) => {
    setEditing(record);
    setScopeType(record.scopeType);
    const nextPatternMode = record.patternIds === null || record.patternIds === undefined ? 'ALL' : 'SELECTED';
    setPatternMode(nextPatternMode);
    form.resetFields();
    form.setFieldsValue({
      ...record,
      patternMode: nextPatternMode,
      patternIds: record.patternIds || [],
    });
    setModalOpen(true);
  };

  const save = async (values: Omit<DlpSelection, 'id'>) => {
    try {
      const selectedPatternIds = values.patternMode === 'ALL'
        ? undefined
        : (values.patternIds || []);
      const payload = {
        ...values,
        patternIds: selectedPatternIds,
        scopeValue: values.scopeType === 'GLOBAL' ? undefined : values.scopeValue,
      };
      if (editing) {
        await dlpApi.updateSelection(editing.id, payload);
      } else {
        await dlpApi.createSelection(payload);
      }
      message.success('DLP 生效范围已保存');
      setModalOpen(false);
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '保存 DLP 生效范围失败'));
    }
  };

  const remove = async (id: string) => {
    try {
      await dlpApi.deleteSelection(id);
      message.success('DLP 生效范围已删除');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除 DLP 生效范围失败'));
    }
  };

  const columns: TableColumnsType<DlpSelection> = [
    {
      title: '范围',
      dataIndex: 'scopeType',
      key: 'scopeType',
      width: 120,
      render: (value: string) => scopeLabels[value] || value,
    },
    {
      title: '值',
      dataIndex: 'scopeValue',
      key: 'scopeValue',
      width: 260,
      ellipsis: true,
      render: (value?: string) => value || '*',
    },
    {
      title: '启用规则',
      dataIndex: 'patternIds',
      key: 'patternIds',
      width: 140,
      render: (patternIds?: string[] | null) => {
        if (patternIds === null || patternIds === undefined) {
          return <Tag color="blue">全部规则</Tag>;
        }
        if (patternIds.length === 0) {
          return <Tag color="default">未选择规则</Tag>;
        }
        return `${patternIds.length} 条规则`;
      },
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 90,
      render: (enabled: boolean) => <EnabledTag enabled={enabled} />,
    },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_: unknown, record: DlpSelection) => (
        <Space size={4} wrap={false}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => showEdit(record)}>编辑</Button>
          <Button
            type="link"
            size="small"
            danger
            icon={<DeleteOutlined />}
            onClick={() => confirmDeleteAction('确认删除该范围？', () => remove(record.id), record.scopeValue || scopeLabels[record.scopeType])}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <PageShell>
      <PageHeader
        title="DLP 生效范围"
        description="配置 DLP 规则在全局、发件域或收件域中的启用范围。"
        actions={(
          <Space wrap>
            <Button icon={<ReloadOutlined />} loading={loading} onClick={loadData}>刷新</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={showCreate}>添加范围</Button>
          </Space>
        )}
      />

      <DataTable<DlpSelection>
        rowKey="id"
        loading={loading}
        dataSource={data}
        columns={columns}
        scroll={{ x: 740 }}
        pagination={{ pageSize: 20, total: data.length }}
      />

      <Modal
        title={editing ? '编辑 DLP 生效范围' : '添加 DLP 生效范围'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        footer={null}
      >
        <Form form={form} layout="vertical" onFinish={save}>
          <Form.Item name="scopeType" label="范围" rules={[{ required: true }]}>
            <Select
              onChange={setScopeType}
              options={[
                { value: 'GLOBAL', label: '全局' },
                { value: 'SENDER_DOMAIN', label: '发件域' },
                { value: 'RECIPIENT_DOMAIN', label: '收件域' },
              ]}
            />
          </Form.Item>
          {scopeType !== 'GLOBAL' && (
            <Form.Item name="scopeValue" label="域名" rules={[{ required: true, message: '请输入域名' }]}>
              <Input placeholder="example.com" />
            </Form.Item>
          )}
          <Form.Item name="patternMode" label="规则模式" rules={[{ required: true }]}>
            <Select
              onChange={(value: 'ALL' | 'SELECTED') => setPatternMode(value)}
              options={[
                { value: 'ALL', label: '全部规则' },
                { value: 'SELECTED', label: '仅选择的规则' },
              ]}
            />
          </Form.Item>
          {patternMode === 'SELECTED' && (
            <Form.Item
              name="patternIds"
              label="启用规则"
              rules={[{ required: true, message: '请选择至少一条 DLP 规则' }]}
            >
              <Select
                mode="multiple"
                allowClear
                placeholder="选择要启用的 DLP 规则"
                options={patterns.map((pattern) => ({
                  value: pattern.id,
                  label: pattern.name,
                }))}
              />
            </Form.Item>
          )}
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item style={{ textAlign: 'right', marginBottom: 0 }}>
            <Space>
              <Button onClick={() => setModalOpen(false)}>取消</Button>
              <Button type="primary" htmlType="submit">保存</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </PageShell>
  );
};

export default DlpSelection;
