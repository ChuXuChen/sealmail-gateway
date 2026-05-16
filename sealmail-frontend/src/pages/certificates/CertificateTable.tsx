import React from 'react';
import { Button, Popconfirm, Space, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  DeleteOutlined,
  EyeOutlined,
  KeyOutlined,
  LinkOutlined,
  LockOutlined,
  UnlockOutlined,
} from '@ant-design/icons';
import type { Certificate, CertificateBinding, CertificateBindingPurpose } from '../../types';
import { DataTable } from '../../components/Page';
import type { CertificatePagination } from './certificateUtils';
import { findBindingFor, formatDate } from './certificateUtils';
import CertificateStatusTags, { CertificateAlgorithmTag } from './CertificateStatusTags';

interface CertificateTableProps {
  bindings: CertificateBinding[];
  data: Certificate[];
  loading: boolean;
  pagination: CertificatePagination;
  onBind: (record: Certificate, purpose: CertificateBindingPurpose) => unknown | Promise<unknown>;
  onDelete: (id: string) => unknown | Promise<unknown>;
  onDeleteBinding: (id: string) => unknown | Promise<unknown>;
  onPaginationChange: (page: number, size: number) => void;
  onRevoke: (id: string) => unknown | Promise<unknown>;
  onTrust: (id: string) => unknown | Promise<unknown>;
  onUntrust: (id: string) => unknown | Promise<unknown>;
  onView: (record: Certificate) => void;
}

const CertificateTable: React.FC<CertificateTableProps> = ({
  bindings,
  data,
  loading,
  pagination,
  onBind,
  onDelete,
  onDeleteBinding,
  onPaginationChange,
  onRevoke,
  onTrust,
  onUntrust,
  onView,
}) => {
  const columns: ColumnsType<Certificate> = [
    {
      title: '别名',
      dataIndex: 'alias',
      key: 'alias',
      render: (text: string, record) =>
        text || (record.subjectDn ? `${record.subjectDn.substring(0, 30)}...` : '-'),
    },
    {
      title: '所有者邮箱',
      dataIndex: 'ownerEmail',
      key: 'ownerEmail',
    },
    {
      title: '算法类型',
      dataIndex: 'algorithm',
      key: 'algorithm',
      render: (algorithm: string) => <CertificateAlgorithmTag algorithm={algorithm} />,
    },
    {
      title: '私钥',
      key: 'hasPrivateKey',
      render: (_, record) =>
        record.hasPrivateKey ? (
          <Tag color="success" icon={<KeyOutlined />}>私钥</Tag>
        ) : (
          <Tag>无钥</Tag>
        ),
    },
    {
      title: '状态',
      key: 'status',
      render: (_, record) => <CertificateStatusTags record={record} />,
    },
    {
      title: '绑定',
      key: 'binding',
      render: (_, record) => {
        const encryptionBinding = findBindingFor(bindings, record, 'ENCRYPTION');
        const signingBinding = findBindingFor(bindings, record, 'SIGNING');

        return (
          <Space wrap>
            {encryptionBinding?.certificateId === record.id && encryptionBinding.enabled ? (
              <Popconfirm
                title="删除此加密绑定？"
                onConfirm={() => onDeleteBinding(encryptionBinding.id)}
                okText="删除"
                cancelText="取消"
              >
                <Tag color="success" icon={<LinkOutlined />}>加密</Tag>
              </Popconfirm>
            ) : (
              <Button
                size="small"
                icon={<LinkOutlined />}
                disabled={!record.suitableForEncryption}
                onClick={() => onBind(record, 'ENCRYPTION')}
              >
                绑加密
              </Button>
            )}
            {signingBinding?.certificateId === record.id && signingBinding.enabled ? (
              <Popconfirm
                title="删除此签名绑定？"
                onConfirm={() => onDeleteBinding(signingBinding.id)}
                okText="删除"
                cancelText="取消"
              >
                <Tag color="processing" icon={<LinkOutlined />}>签名</Tag>
              </Popconfirm>
            ) : (
              <Button
                size="small"
                icon={<LinkOutlined />}
                disabled={!record.suitableForSigning || !record.hasPrivateKey}
                onClick={() => onBind(record, 'SIGNING')}
              >
                绑签名
              </Button>
            )}
          </Space>
        );
      },
    },
    {
      title: '有效期至',
      dataIndex: 'notAfter',
      key: 'notAfter',
      render: (date: string) => formatDate(date),
    },
    {
      title: '导入时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date: string) => formatDate(date),
    },
    {
      title: '操作',
      key: 'actions',
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => onView(record)}>
            查看
          </Button>
          {record.trusted ? (
            <Popconfirm
              title="撤销此证书的信任？"
              onConfirm={() => onUntrust(record.id)}
              okText="确认"
              cancelText="取消"
            >
              <Button type="link" size="small" icon={<LockOutlined />}>
                撤销信任
              </Button>
            </Popconfirm>
          ) : (
            <Button type="link" size="small" icon={<UnlockOutlined />} onClick={() => onTrust(record.id)}>
              信任
            </Button>
          )}
          {!record.revoked ? (
            <Popconfirm
              title="确定要吊销此证书吗？"
              onConfirm={() => onRevoke(record.id)}
              okText="确定"
              cancelText="取消"
            >
              <Button type="link" size="small" danger icon={<LockOutlined />}>
                吊销
              </Button>
            </Popconfirm>
          ) : null}
          <Popconfirm
            title="确定要删除此证书吗？关联的密钥将自动解除关联。"
            onConfirm={() => onDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <DataTable
      columns={columns}
      dataSource={data}
      loading={loading}
      rowKey="id"
      pagination={{
        current: pagination.page,
        pageSize: pagination.size,
        total: pagination.total,
        onChange: onPaginationChange,
      }}
    />
  );
};

export default CertificateTable;
