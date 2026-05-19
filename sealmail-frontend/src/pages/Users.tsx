import React from 'react';
import { Button, Space, Tabs } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { UserStatusFilter } from '../types';
import { useAuth } from '../contexts/useAuth';
import { PageHeader, PageShell } from '../components/Page';
import { ResetPasswordModal, UserFormModal } from './users/UserForms';
import UserTable from './users/UserTable';
import { statusTabs } from './users/userUtils';
import { useUsers } from './users/useUsers';

const Users: React.FC = () => {
  const { user } = useAuth();
  const users = useUsers(user?.userId);

  return (
    <PageShell>
      <PageHeader
        title="用户管理"
        description="按正常用户和停用用户维护账号资料、角色、管理域、锁定状态和密码。"
        actions={(
          <Space>
            <Button icon={<ReloadOutlined />} loading={users.loading} onClick={users.loadData}>
              刷新
            </Button>
            {users.status === 'active' ? (
              <Button type="primary" icon={<PlusOutlined />} onClick={users.openCreate}>
                创建用户
              </Button>
            ) : null}
          </Space>
        )}
      />

      <Tabs
        activeKey={users.status}
        items={statusTabs}
        onChange={(key) => users.setStatus(key as UserStatusFilter)}
      />

      <UserTable
        currentUserId={user?.userId}
        data={users.data}
        loading={users.loading}
        onDisable={users.handleDisable}
        onEdit={users.openEdit}
        onEnable={users.handleEnable}
        onResetPassword={users.openReset}
        onUnlock={users.handleUnlock}
      />

      <UserFormModal
        form={users.createForm}
        mode="create"
        open={users.createOpen}
        saving={users.saving}
        onCancel={users.closeCreate}
        onFinish={users.handleCreate}
      />
      <UserFormModal
        form={users.editForm}
        mode="edit"
        open={users.editOpen}
        saving={users.saving}
        onCancel={users.closeEdit}
        onFinish={users.handleEdit}
      />
      <ResetPasswordModal
        form={users.resetForm}
        open={users.resetOpen}
        saving={users.saving}
        user={users.selectedUser}
        onCancel={users.closeReset}
        onFinish={users.handleResetPassword}
      />
    </PageShell>
  );
};

export default Users;
