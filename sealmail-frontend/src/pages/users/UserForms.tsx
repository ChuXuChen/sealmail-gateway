import React from 'react';
import { Button, Form, Input, Modal, Select, Space } from 'antd';
import type { FormInstance } from 'antd';
import type { UserAccount } from '../../types';
import type { ResetPasswordValues, UserFormValues } from './userTypes';
import { roleOptions } from './userUtils';

interface UserFormModalProps {
  form: FormInstance<UserFormValues>;
  mode: 'create' | 'edit';
  open: boolean;
  saving: boolean;
  onCancel: () => void;
  onFinish: (values: UserFormValues) => Promise<void>;
}

interface ResetPasswordModalProps {
  form: FormInstance<ResetPasswordValues>;
  open: boolean;
  saving: boolean;
  user?: UserAccount | null;
  onCancel: () => void;
  onFinish: (values: ResetPasswordValues) => Promise<void>;
}

export const UserFormModal: React.FC<UserFormModalProps> = ({
  form,
  mode,
  onCancel,
  onFinish,
  open,
  saving,
}) => {
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

export const ResetPasswordModal: React.FC<ResetPasswordModalProps> = ({
  form,
  onCancel,
  onFinish,
  open,
  saving,
  user,
}) => (
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
