import React, { useCallback, useEffect, useState } from 'react';
import { Button, Form, Input, InputNumber, Modal, Select, Space, Switch, message } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { dlpApi } from '../api/client';
import { getApiErrorMessage } from '../api/errors';
import { DlpPattern } from '../types';
import {
  DataTable,
  DlpActionTag,
  EnabledTag,
  PageHeader,
  PageShell,
  confirmDeleteAction,
} from '../components/Page';

const { TextArea } = Input;

const actionLabels: Record<string, string> = {
  WARN: '告警',
  MUST_ENCRYPT: '强制加密',
  QUARANTINE: '隔离',
  BLOCK: '阻断',
};

const actionOptions = Object.entries(actionLabels).map(([value, label]) => ({
  value,
  label,
}));

const DlpPatterns: React.FC = () => {
  const [data, setData] = useState<DlpPattern[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DlpPattern | null>(null);
  const [form] = Form.useForm();

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await dlpApi.listPatterns();
      setData(response.data.data);
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载 DLP 规则失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void Promise.resolve().then(loadData);
  }, [loadData]);

  const showCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      action: 'WARN',
      severity: 5,
      priority: 100,
      enabled: true,
    });
    setModalOpen(true);
  };

  const showEdit = (record: DlpPattern) => {
    setEditing(record);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const save = async (values: Omit<DlpPattern, 'id'>) => {
    try {
      if (editing) {
        await dlpApi.updatePattern(editing.id, values);
      } else {
        await dlpApi.createPattern(values);
      }
      message.success('DLP 规则已保存');
      setModalOpen(false);
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '保存 DLP 规则失败'));
    }
  };

  const remove = async (id: string) => {
    try {
      await dlpApi.deletePattern(id);
      message.success('DLP 规则已删除');
      void loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '删除 DLP 规则失败'));
    }
  };

  const columns: TableColumnsType<DlpPattern> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 160, ellipsis: true },
    { title: '说明', dataIndex: 'description', key: 'description', width: 220, ellipsis: true },
    { title: '正则', dataIndex: 'regex', key: 'regex', width: 260, ellipsis: true },
    {
      title: '动作',
      dataIndex: 'action',
      key: 'action',
      width: 120,
      render: (action: string) => <DlpActionTag action={action} />,
    },
    { title: '级别', dataIndex: 'severity', key: 'severity', width: 80 },
    { title: '优先级', dataIndex: 'priority', key: 'priority', width: 90 },
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
      render: (_: unknown, record: DlpPattern) => (
        <Space size={4} wrap={false}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => showEdit(record)}>编辑</Button>
          <Button
            type="link"
            size="small"
            danger
            icon={<DeleteOutlined />}
            onClick={() => confirmDeleteAction('确认删除该规则？', () => remove(record.id), record.name)}
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
        title="DLP 检测规则"
        description="维护用于识别敏感内容的检测规则和命中动作。"
        actions={(
          <Space wrap>
            <Button icon={<ReloadOutlined />} loading={loading} onClick={loadData}>刷新</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={showCreate}>添加规则</Button>
          </Space>
        )}
      />

      <DataTable<DlpPattern>
        rowKey="id"
        loading={loading}
        dataSource={data}
        columns={columns}
        scroll={{ x: 1150 }}
        pagination={{ pageSize: 20, total: data.length }}
      />

      <Modal
        title={editing ? '编辑 DLP 规则' : '添加 DLP 规则'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        footer={null}
        width={720}
      >
        <Form form={form} layout="vertical" onFinish={save}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input />
          </Form.Item>
          <Form.Item name="regex" label="正则表达式" rules={[{ required: true, message: '请输入正则表达式' }]}>
            <TextArea rows={4} />
          </Form.Item>
          <Space style={{ width: '100%' }} size="large">
            <Form.Item name="action" label="动作" rules={[{ required: true }]} style={{ minWidth: 180 }}>
              <Select options={actionOptions} />
            </Form.Item>
            <Form.Item name="severity" label="严重级别" rules={[{ required: true }]}>
              <InputNumber min={1} max={10} />
            </Form.Item>
            <Form.Item name="priority" label="优先级" rules={[{ required: true }]}>
              <InputNumber min={0} />
            </Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
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

export default DlpPatterns;
