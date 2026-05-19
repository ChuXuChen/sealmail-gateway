import React, { useMemo } from 'react';
import { Button, Space, Tag, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  EditOutlined,
  LockOutlined,
  ReloadOutlined,
  StopOutlined,
  UndoOutlined,
} from '@ant-design/icons';
import type { UserAccount } from '../../types';
import { DataTable, formatDateTime } from '../../components/Page';

interface UserTableProps {
  currentUserId?: string;
  data: UserAccount[];
  loading: boolean;
  onDisable: (account: UserAccount) => void;
  onEdit: (account: UserAccount) => void;
  onEnable: (account: UserAccount) => void;
  onResetPassword: (account: UserAccount) => void;
  onUnlock: (account: UserAccount) => void;
}

const RoleTags: React.FC<{ roles: string[] }> = ({ roles }) => (
  <Space size={[0, 4]} wrap>
    {roles.map((role) => (
      <Tag key={role} color={role === 'SUPER_ADMIN' ? 'red' : 'blue'}>
        {role}
      </Tag>
    ))}
  </Space>
);

const DomainTags: React.FC<{ domains: string[] }> = ({ domains }) => {
  if (!domains.length) {
    return <>-</>;
  }
  return (
    <Space size={[0, 4]} wrap>
      {domains.map((domain) => (
        <Tag key={domain}>{domain}</Tag>
      ))}
    </Space>
  );
};

const UserTable: React.FC<UserTableProps> = ({
  currentUserId,
  data,
  loading,
  onDisable,
  onEdit,
  onEnable,
  onResetPassword,
  onUnlock,
}) => {
  const columns = useMemo<ColumnsType<UserAccount>>(() => [
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      render: (value: string, record) => (
        <Space>
          <strong>{value}</strong>
          {record.userId === currentUserId ? <Tag color="processing">当前用户</Tag> : null}
        </Space>
      ),
    },
    {
      title: '邮箱',
      dataIndex: 'email',
      key: 'email',
    },
    {
      title: '角色',
      dataIndex: 'roles',
      key: 'roles',
      render: (roles: string[]) => <RoleTags roles={roles} />,
    },
    {
      title: '管理域',
      dataIndex: 'managedDomains',
      key: 'managedDomains',
      render: (domains: string[]) => <DomainTags domains={domains} />,
    },
    {
      title: '启用',
      dataIndex: 'active',
      key: 'active',
      render: (active: boolean) => (
        <Tag color={active ? 'success' : 'default'}>{active ? '启用' : '停用'}</Tag>
      ),
    },
    {
      title: '锁定',
      dataIndex: 'locked',
      key: 'locked',
      render: (locked: boolean) => (
        <Tag color={locked ? 'error' : 'success'}>{locked ? '锁定' : '正常'}</Tag>
      ),
    },
    {
      title: '失败次数',
      dataIndex: 'failedLoginAttempts',
      key: 'failedLoginAttempts',
      width: 96,
    },
    {
      title: '最后登录',
      dataIndex: 'lastLoginAt',
      key: 'lastLoginAt',
      render: formatDateTime,
    },
    {
      title: '最后改密',
      dataIndex: 'lastPasswordChangedAt',
      key: 'lastPasswordChangedAt',
      render: formatDateTime,
    },
    {
      title: '操作',
      key: 'actions',
      fixed: 'right',
      render: (_, record) => {
        const isSelf = record.userId === currentUserId;
        return (
          <Space size="small">
            <Button type="text" size="small" icon={<EditOutlined />} onClick={() => onEdit(record)}>
              编辑
            </Button>
            {record.active ? (
              <Tooltip title={isSelf ? '不能停用当前登录用户' : undefined}>
                <Button
                  type="text"
                  danger
                  size="small"
                  icon={<StopOutlined />}
                  disabled={isSelf}
                  onClick={() => onDisable(record)}
                >
                  停用
                </Button>
              </Tooltip>
            ) : (
              <Button type="text" size="small" icon={<UndoOutlined />} onClick={() => onEnable(record)}>
                启用
              </Button>
            )}
            {record.active ? (
              <Button
                type="text"
                size="small"
                icon={<LockOutlined />}
                disabled={!record.locked}
                onClick={() => onUnlock(record)}
              >
                解锁
              </Button>
            ) : null}
            <Button type="text" size="small" icon={<ReloadOutlined />} onClick={() => onResetPassword(record)}>
              重置密码
            </Button>
          </Space>
        );
      },
    },
  ], [currentUserId, onDisable, onEdit, onEnable, onResetPassword, onUnlock]);

  return (
    <DataTable
      columns={columns}
      dataSource={data}
      rowKey="userId"
      loading={loading}
      pagination={{ pageSize: 10 }}
      scroll={{ x: 1180 }}
    />
  );
};

export default UserTable;
