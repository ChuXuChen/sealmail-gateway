import { useCallback, useEffect, useState } from 'react';
import { Form, message } from 'antd';
import type { UserAccount, UserStatusFilter } from '../../types';
import { userApi } from '../../api/client';
import { getApiErrorMessage } from '../../api/errors';
import { confirmAction } from '../../components/Page';
import type { ResetPasswordValues, UserFormValues } from './userTypes';
import { hasRole, normalizeList, normalizeText } from './userUtils';

export const useUsers = (currentUserId?: string) => {
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

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      setData(await userApi.list(status));
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

  const closeCreate = useCallback(() => {
    setCreateOpen(false);
  }, []);

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

  const closeEdit = useCallback(() => {
    setEditOpen(false);
    setSelectedUser(null);
  }, []);

  const openReset = useCallback((account: UserAccount) => {
    setSelectedUser(account);
    resetForm.resetFields();
    setResetOpen(true);
  }, [resetForm]);

  const closeReset = useCallback(() => {
    setResetOpen(false);
    setSelectedUser(null);
  }, []);

  const handleCreate = useCallback(async (values: UserFormValues) => {
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
  }, [loadData]);

  const handleEdit = useCallback(async (values: UserFormValues) => {
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
  }, [currentUserId, loadData, selectedUser]);

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

  const handleResetPassword = useCallback(async (values: ResetPasswordValues) => {
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
  }, [loadData, selectedUser]);

  return {
    closeCreate,
    closeEdit,
    closeReset,
    createForm,
    createOpen,
    data,
    editForm,
    editOpen,
    handleCreate,
    handleDisable,
    handleEdit,
    handleEnable,
    handleResetPassword,
    handleUnlock,
    loadData,
    loading,
    openCreate,
    openEdit,
    openReset,
    resetForm,
    resetOpen,
    saving,
    selectedUser,
    setStatus,
    status,
  };
};
