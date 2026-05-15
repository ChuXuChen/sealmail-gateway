import React, { useEffect, useState } from 'react';
import { Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Switch, Table, Tag, Typography, message } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { dlpApi } from '../api/client';
import { DlpPattern } from '../types';

const { Title } = Typography;
const { TextArea } = Input;

const actionColors: Record<string, string> = {
  WARN: 'blue',
  MUST_ENCRYPT: 'gold',
  QUARANTINE: 'orange',
  BLOCK: 'red',
};

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

const getErrorMessage = (error: unknown, fallback: string) => {
  if (
    typeof error === 'object' &&
    error !== null &&
    'response' in error &&
    typeof (error as { response?: { data?: { message?: unknown } } }).response?.data?.message === 'string'
  ) {
    return (error as { response: { data: { message: string } } }).response.data.message;
  }
  return fallback;
};

const DlpPatterns: React.FC = () => {
  const [data, setData] = useState<DlpPattern[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DlpPattern | null>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const response = await dlpApi.listPatterns();
      setData(response.data.data);
    } catch {
      message.error('加载 DLP 规则失败');
    } finally {
      setLoading(false);
    }
  };

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
      loadData();
    } catch (error) {
      message.error(getErrorMessage(error, '保存 DLP 规则失败'));
    }
  };

  const remove = async (id: string) => {
    try {
      await dlpApi.deletePattern(id);
      message.success('DLP 规则已删除');
      loadData();
    } catch (error) {
      message.error(getErrorMessage(error, '删除 DLP 规则失败'));
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
      render: (action: string) => (
        <Tag color={actionColors[action] || 'default'}>
          {actionLabels[action] || action}
        </Tag>
      ),
    },
    { title: '级别', dataIndex: 'severity', key: 'severity', width: 80 },
    { title: '优先级', dataIndex: 'priority', key: 'priority', width: 90 },
    {
      title: '状态',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 90,
      render: (enabled: boolean) => <Tag color={enabled ? 'green' : 'default'}>{enabled ? '启用' : '停用'}</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 130,
      fixed: 'right',
      render: (_: unknown, record: DlpPattern) => (
        <Space size={4} wrap={false}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => showEdit(record)}>编辑</Button>
          <Popconfirm title="确认删除该规则？" onConfirm={() => remove(record.id)} okText="确认" cancelText="取消">
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>DLP 检测规则</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={showCreate}>添加规则</Button>
      </div>

      <Table
        rowKey="id"
        loading={loading}
        dataSource={data}
        columns={columns}
        scroll={{ x: 1150 }}
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
    </div>
  );
};

export default DlpPatterns;
