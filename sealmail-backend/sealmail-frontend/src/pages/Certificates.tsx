import React, { useState, useEffect } from 'react';
import {
  Table,
  Button,
  Typography,
  Space,
  Tag,
  Modal,
  Form,
  Input,
  message,
  Popconfirm,
  Switch,
  Descriptions,
  Drawer,
} from 'antd';
import {
  PlusOutlined,
  EyeOutlined,
  LockOutlined,
  UnlockOutlined,
} from '@ant-design/icons';
import type { Certificate } from '../types';
import { certificateApi } from '../api/client';

const { Title } = Typography;
const { TextArea } = Input;

const Certificates: React.FC = () => {
  const [data, setData] = useState<Certificate[]>([]);
  const [loading, setLoading] = useState(false);
  const [importModalVisible, setImportModalVisible] = useState(false);
  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedCert, setSelectedCert] = useState<Certificate | null>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const response = await certificateApi.list({ page: 1, size: 50 });
      setData(response.data.data.items);
    } catch {
      message.error('加载证书列表失败');
    } finally {
      setLoading(false);
    }
  };

  const handleImport = async (values: any) => {
    try {
      await certificateApi.import({
        pemData: values.pemData,
        ownerEmail: values.ownerEmail,
        alias: values.alias,
        trusted: values.trusted,
      });
      message.success('证书导入成功');
      setImportModalVisible(false);
      form.resetFields();
      loadData();
    } catch {
      message.error('证书导入失败');
    }
  };

  const handleTrust = async (id: string) => {
    try {
      await certificateApi.trust(id);
      message.success('证书已标记为信任');
      loadData();
    } catch {
      message.error('操作失败');
    }
  };

  const handleRevoke = async (id: string) => {
    try {
      await certificateApi.revoke(id, '管理员手动吊销');
      message.success('证书已吊销');
      loadData();
    } catch {
      message.error('操作失败');
    }
  };

  const columns = [
    {
      title: '别名',
      dataIndex: 'alias',
      key: 'alias',
      render: (text: string, record: Certificate) =>
        text || record.subjectDn?.substring(0, 30) + '...',
    },
    {
      title: '所有者邮箱',
      dataIndex: 'ownerEmail',
      key: 'ownerEmail',
    },
    {
      title: '状态',
      key: 'status',
      render: (_: any, record: Certificate) => (
        <Space>
          {record.trusted ? (
            <Tag color="green">已信任</Tag>
          ) : (
            <Tag color="default">未信任</Tag>
          )}
          {record.revoked ? (
            <Tag color="red">已吊销</Tag>
          ) : (
            <Tag color="blue">有效</Tag>
          )}
        </Space>
      ),
    },
    {
      title: '有效期至',
      dataIndex: 'notAfter',
      key: 'notAfter',
      render: (date: string) => new Date(date).toLocaleDateString(),
    },
    {
      title: '导入时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => new Date(date).toLocaleDateString(),
    },
    {
      title: '操作',
      key: 'actions',
      render: (_: any, record: Certificate) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => {
              setSelectedCert(record);
              setDetailVisible(true);
            }}
          >
            查看
          </Button>
          {!record.trusted && (
            <Button
              type="link"
              size="small"
              icon={<UnlockOutlined />}
              onClick={() => handleTrust(record.id)}
            >
              信任
            </Button>
          )}
          {!record.revoked && (
            <Popconfirm
              title="确定要吊销此证书吗？"
              onConfirm={() => handleRevoke(record.id)}
              okText="确定"
              cancelText="取消"
            >
              <Button
                type="link"
                size="small"
                danger
                icon={<LockOutlined />}
              >
                吊销
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 24,
        }}
      >
        <Title level={3} style={{ margin: 0 }}>
          证书管理
        </Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setImportModalVisible(true)}>
          导入证书
        </Button>
      </div>

      <Table
        columns={columns}
        dataSource={data}
        loading={loading}
        rowKey="id"
      />

      <Modal
        title="导入证书"
        open={importModalVisible}
        onCancel={() => setImportModalVisible(false)}
        onOk={() => form.submit()}
        okText="导入"
        cancelText="取消"
        width={600}
      >
        <Form form={form} onFinish={handleImport} layout="vertical">
          <Form.Item
            name="ownerEmail"
            label="所有者邮箱"
            rules={[{ required: true, message: '请输入所有者邮箱' }]}
          >
            <Input placeholder="email@example.com" />
          </Form.Item>

          <Form.Item
            name="alias"
            label="证书别名"
          >
            <Input placeholder="可选，便于识别" />
          </Form.Item>

          <Form.Item
            name="pemData"
            label="PEM 格式证书内容"
            rules={[{ required: true, message: '请输入 PEM 格式证书' }]}
          >
            <TextArea
              rows={10}
              placeholder="-----BEGIN CERTIFICATE-----&#10;...&#10;-----END CERTIFICATE-----"
            />
          </Form.Item>

          <Form.Item
            name="trusted"
            label="标记为信任"
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title="证书详情"
        width={600}
        open={detailVisible}
        onClose={() => setDetailVisible(false)}
      >
        {selectedCert && (
          <Descriptions column={1} bordered>
            <Descriptions.Item label="别名">
              {selectedCert.alias || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="所有者邮箱">
              {selectedCert.ownerEmail}
            </Descriptions.Item>
            <Descriptions.Item label="颁发者DN">
              {selectedCert.issuerDn || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="主体DN">
              {selectedCert.subjectDn || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="序列号">
              {selectedCert.serialNumber || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="指纹">
              {selectedCert.thumbprint}
            </Descriptions.Item>
            <Descriptions.Item label="有效期">
              {new Date(selectedCert.notBefore).toLocaleDateString()} - {new Date(selectedCert.notAfter).toLocaleDateString()}
            </Descriptions.Item>
            <Descriptions.Item label="密钥用途">
              {selectedCert.keyUsages?.join(', ') || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              {selectedCert.trusted ? '已信任' : '未信任'} / {selectedCert.revoked ? '已吊销' : '有效'}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </div>
  );
};

export default Certificates;
