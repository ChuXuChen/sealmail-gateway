import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Tabs,
  Tag,
  Tooltip,
  message,
} from 'antd';
import type { FormInstance } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  EditOutlined,
  LockOutlined,
  PlusOutlined,
  ReloadOutlined,
  StopOutlined,
  UndoOutlined,
} from '@ant-design/icons';
import type { UserAccount, UserStatusFilter } from '../types';
import { userApi } from '../api/client';
import { getApiErrorMessage } from '../api/errors';
import { useAuth } from '../contexts/useAuth';
import {
  DataTable,
  PageHeader,
  PageShell,
  confirmAction,
  formatDateTime,
} from '../components/Page';

interface UserFormValues {
  username: string;
  email: string;
  password?: string;
  confirmPassword?: string;
  roles: string[];
  managedDomains: string[];
}

interface ResetPasswordValues {
  newPassword: string;
  confirmPassword: string;
}

const roleOptions = [
  { value: 'USER', label: 'USER' },
  { value: 'AUDITOR', label: 'AUDITOR' },
  { value: 'DOMAIN_MANAGER', label: 'DOMAIN_MANAGER' },
  { value: 'DOMAIN_ADMIN', label: 'DOMAIN_ADMIN' },
  { value: 'ADMIN', label: 'ADMIN' },
  { value: 'PKI_ADMIN', label: 'PKI_ADMIN' },
  { value: 'SUPER_ADMIN', label: 'SUPER_ADMIN' },
];

const statusTabs: Array<{ key: UserStatusFilter; label: string }> = [
  { key: 'active', label: '正常用户' },
  { key: 'disabled', label: '停用用户' },
];

const normalizeText = (value?: string) => value?.trim() || '';
const normalizeList = (values?: string[]) => (values || [])
  .map((value) => value.trim())
  .filter(Boolean);

const hasRole = (roles: string[] | undefined, role: string) =>
  !!roles?.some((item) => item.toUpperCase() === role.toUpperCase());

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

const UserFormModal: React.FC<{
  form: FormInstance<UserFormValues>;
  mode: 'create' | 'edit';
  open: boolean;
  saving: boolean;
  onCancel: () => void;
  onFinish: (values: UserFormValues) => Promise<void>;
}> = ({ form, mode, onCancel, onFinish, open, saving }) => {
  const isCreate = mode === 'create';
  return (
    <Modal
      title={isCreate ? '创建用户' : '编辑用户'}
      open={open}
      onCancel={onCancel}
      footer={null}
      width={640}
    >
      <Form form={form} layout="vertical" onFinish={onFinish}>
        <Form.Item
          name="username"
          label="用户名"
          rules={[
            { required: true, message: '请输入用户名' },
            { max: 64, message: '用户名长度不能超过 64 个字符' },
          ]}
        >
          <Input autoComplete="username" />
        </Form.Item>
        <Form.Item
          name="email"
          label="邮箱"
          rules={[
            { required: true, message: '请输入邮箱' },
            { type: 'email', message: '请输入有效的邮箱地址' },
          ]}
        >
          <Input autoComplete="email" />
        </Form.Item>
        {isCreate ? (
          <>
            <Form.Item
              name="password"
              label="初始密码"
              rules={[{ required: true, message: '请输入初始密码' }]}
            >
              <Input.Password autoComplete="new-password" />
            </Form.Item>
            <Form.Item
              name="confirmPassword"
              label="确认初始密码"
              dependencies={['password']}
              rules={[
                { required: true, message: '请再次输入初始密码' },
                ({ getFieldValue }) => ({
                  validator: (_, value) => {
                    if (!value || getFieldValue('password') === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error('两次输入的密码不一致'));
                  },
                }),
              ]}
            >
              <Input.Password autoComplete="new-password" />
            </Form.Item>
          </>
        ) : null}
        <Form.Item
          name="roles"
          label="角色"
          rules={[{ required: true, message: '请选择角色' }]}
        >
          <Select mode="multiple" options={roleOptions} />
        </Form.Item>
        <Form.Item name="managedDomains" label="管理域">
          <Select mode="tags" tokenSeparators={[',', ' ']} />
        </Form.Item>
        <Form.Item className="form-actions">
          <Space>
            <Button onClick={onCancel}>取消</Button>
            <Button type="primary" htmlType="submit" loading={saving}>
              {isCreate ? '创建' : '保存'}
            </Button>
          </Space>
        </Form.Item>
      </Form>
    </Modal>
  );
};

const ResetPasswordModal: React.FC<{
  form: FormInstance<ResetPasswordValues>;
  open: boolean;
  saving: boolean;
  user?: UserAccount | null;
  onCancel: () => void;
  onFinish: (values: ResetPasswordValues) => Promise<void>;
}> = ({ form, onCancel, onFinish, open, saving, user }) => (
  <Modal
    title={`重置密码${user ? `: ${user.username}` : ''}`}
    open={open}
    onCancel={onCancel}
    footer={null}
    width={520}
  >
    <Form form={form} layout="vertical" onFinish={onFinish}>
      <Form.Item
        name="newPassword"
        label="新密码"
        rules={[{ required: true, message: '请输入新密码' }]}
      >
        <Input.Password autoComplete="new-password" />
      </Form.Item>
      <Form.Item
        name="confirmPassword"
        label="确认新密码"
        dependencies={['newPassword']}
        rules={[
          { required: true, message: '请再次输入新密码' },
          ({ getFieldValue }) => ({
            validator: (_, value) => {
              if (!value || getFieldValue('newPassword') === value) {
                return Promise.resolve();
              }
              return Promise.reject(new Error('两次输入的新密码不一致'));
            },
          }),
        ]}
      >
        <Input.Password autoComplete="new-password" />
      </Form.Item>
      <Form.Item className="form-actions">
        <Space>
          <Button onClick={onCancel}>取消</Button>
          <Button type="primary" htmlType="submit" loading={saving}>
            重置密码
          </Button>
        </Space>
      </Form.Item>
    </Form>
  </Modal>
);

const Users: React.FC = () => {
  const { user } = useAuth();
  const [status, setStatus] = useState<UserStatusFilter>('active');
  const [data, setData] = useState<UserAccount[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [resetOpen, setResetOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<UserAccount | null>(null);
  const [createForm] = Form.useForm<UserFormValues>();
  const [editForm] = Form.useForm<UserFormValues>();
  const [resetForm] = Form.useForm<ResetPasswordValues>();

  const currentUserId = user?.userId;

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const response = await userApi.list(status);
      setData(response.data.data);
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载用户列表失败'));
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => {
    const timeout = window.setTimeout(() => {
      void loadData();
    }, 0);
    return () => window.clearTimeout(timeout);
  }, [loadData]);

  const openCreate = useCallback(() => {
    createForm.resetFields();
    createForm.setFieldsValue({
      roles: ['USER'],
      managedDomains: [],
    });
    setCreateOpen(true);
  }, [createForm]);

  const openEdit = useCallback((account: UserAccount) => {
    setSelectedUser(account);
    editForm.setFieldsValue({
      username: account.username,
      email: account.email,
      roles: account.roles,
      managedDomains: account.managedDomains,
    });
    setEditOpen(true);
  }, [editForm]);

  const openReset = useCallback((account: UserAccount) => {
    setSelectedUser(account);
    resetForm.resetFields();
    setResetOpen(true);
  }, [resetForm]);

  const handleCreate = async (values: UserFormValues) => {
    setSaving(true);
    try {
      await userApi.create({
        username: normalizeText(values.username),
        email: normalizeText(values.email),
        password: values.password || '',
        roles: normalizeList(values.roles),
        managedDomains: normalizeList(values.managedDomains),
      });
      message.success('用户已创建');
      setCreateOpen(false);
      await loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '创建用户失败'));
    } finally {
      setSaving(false);
    }
  };

  const handleEdit = async (values: UserFormValues) => {
    if (!selectedUser) return;
    const roles = normalizeList(values.roles);
    if (
      selectedUser.userId === currentUserId
      && hasRole(selectedUser.roles, 'SUPER_ADMIN')
      && !hasRole(roles, 'SUPER_ADMIN')
    ) {
      message.error('不能移除自己的 SUPER_ADMIN 角色');
      return;
    }

    setSaving(true);
    try {
      await userApi.update(selectedUser.userId, {
        username: normalizeText(values.username),
        email: normalizeText(values.email),
        roles,
        managedDomains: normalizeList(values.managedDomains),
      });
      message.success('用户已更新');
      setEditOpen(false);
      setSelectedUser(null);
      await loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '更新用户失败'));
    } finally {
      setSaving(false);
    }
  };

  const handleDisable = useCallback((account: UserAccount) => {
    if (account.userId === currentUserId) {
      message.error('不能停用当前登录用户');
      return;
    }
    confirmAction({
      title: '停用用户',
      content: `停用后 ${account.username} 将无法登录，已有 Token 会立即失效。`,
      danger: true,
      okText: '停用',
      onOk: async () => {
        try {
          await userApi.disable(account.userId);
          message.success('用户已停用');
          await loadData();
        } catch (error) {
          message.error(getApiErrorMessage(error, '停用用户失败'));
        }
      },
    });
  }, [currentUserId, loadData]);

  const handleEnable = useCallback((account: UserAccount) => {
    confirmAction({
      title: '启用用户',
      content: `启用 ${account.username} 后该用户可以重新登录。`,
      okText: '启用',
      onOk: async () => {
        try {
          await userApi.enable(account.userId);
          message.success('用户已启用');
          await loadData();
        } catch (error) {
          message.error(getApiErrorMessage(error, '启用用户失败'));
        }
      },
    });
  }, [loadData]);

  const handleUnlock = useCallback((account: UserAccount) => {
    confirmAction({
      title: '解锁用户',
      content: `清除 ${account.username} 的锁定状态和失败次数。`,
      okText: '解锁',
      onOk: async () => {
        try {
          await userApi.unlock(account.userId);
          message.success('用户已解锁');
          await loadData();
        } catch (error) {
          message.error(getApiErrorMessage(error, '解锁用户失败'));
        }
      },
    });
  }, [loadData]);

  const handleResetPassword = async (values: ResetPasswordValues) => {
    if (!selectedUser) return;
    setSaving(true);
    try {
      await userApi.resetPassword(selectedUser.userId, { newPassword: values.newPassword });
      message.success('密码已重置');
      setResetOpen(false);
      setSelectedUser(null);
      await loadData();
    } catch (error) {
      message.error(getApiErrorMessage(error, '重置密码失败'));
    } finally {
      setSaving(false);
    }
  };

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
            <Button type="text" size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>
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
                  onClick={() => handleDisable(record)}
                >
                  停用
                </Button>
              </Tooltip>
            ) : (
              <Button type="text" size="small" icon={<UndoOutlined />} onClick={() => handleEnable(record)}>
                启用
              </Button>
            )}
            {record.active ? (
              <Button
                type="text"
                size="small"
                icon={<LockOutlined />}
                disabled={!record.locked}
                onClick={() => handleUnlock(record)}
              >
                解锁
              </Button>
            ) : null}
            <Button type="text" size="small" icon={<ReloadOutlined />} onClick={() => openReset(record)}>
              重置密码
            </Button>
          </Space>
        );
      },
    },
  ], [currentUserId, handleDisable, handleEnable, handleUnlock, openEdit, openReset]);

  return (
    <PageShell>
      <PageHeader
        title="用户管理"
        description="按正常用户和停用用户维护账号资料、角色、管理域、锁定状态和密码。"
        actions={(
          <Space>
            <Button icon={<ReloadOutlined />} loading={loading} onClick={loadData}>
              刷新
            </Button>
            {status === 'active' ? (
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                创建用户
              </Button>
            ) : null}
          </Space>
        )}
      />

      <Tabs
        activeKey={status}
        items={statusTabs}
        onChange={(key) => setStatus(key as UserStatusFilter)}
      />

      <DataTable
        columns={columns}
        dataSource={data}
        rowKey="userId"
        loading={loading}
        pagination={{ pageSize: 10 }}
        scroll={{ x: 1180 }}
      />

      <UserFormModal
        form={createForm}
        mode="create"
        open={createOpen}
        saving={saving}
        onCancel={() => setCreateOpen(false)}
        onFinish={handleCreate}
      />
      <UserFormModal
        form={editForm}
        mode="edit"
        open={editOpen}
        saving={saving}
        onCancel={() => {
          setEditOpen(false);
          setSelectedUser(null);
        }}
        onFinish={handleEdit}
      />
      <ResetPasswordModal
        form={resetForm}
        open={resetOpen}
        saving={saving}
        user={selectedUser}
        onCancel={() => {
          setResetOpen(false);
          setSelectedUser(null);
        }}
        onFinish={handleResetPassword}
      />
    </PageShell>
  );
};

export default Users;
