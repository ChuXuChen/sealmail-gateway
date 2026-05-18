import React, { useEffect, useState } from 'react';
import { Button, Form, Input, Modal, Space, Tabs, message } from 'antd';
import type { FormInstance } from 'antd';
import { AUTH_SESSION_EXPIRED_EVENT, authApi } from '../../api/client';
import { getApiErrorMessage } from '../../api/errors';
import { useAuth } from '../../contexts/useAuth';

interface ProfileModalProps {
  open: boolean;
  onCancel: () => void;
}

interface ProfileFormValues {
  username: string;
  email: string;
}

interface PasswordFormValues {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

const submitButton = (label: string, loading: boolean) => (
  <Form.Item className="form-actions">
    <Space>
      <Button type="primary" htmlType="submit" loading={loading}>
        {label}
      </Button>
    </Space>
  </Form.Item>
);

const ProfileForm: React.FC<{
  form: FormInstance<ProfileFormValues>;
  saving: boolean;
  onFinish: (values: ProfileFormValues) => Promise<void>;
}> = ({ form, onFinish, saving }) => (
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
    {submitButton('保存资料', saving)}
  </Form>
);

const PasswordForm: React.FC<{
  form: FormInstance<PasswordFormValues>;
  saving: boolean;
  onFinish: (values: PasswordFormValues) => Promise<void>;
}> = ({ form, onFinish, saving }) => (
  <Form form={form} layout="vertical" onFinish={onFinish}>
    <Form.Item
      name="currentPassword"
      label="当前密码"
      rules={[{ required: true, message: '请输入当前密码' }]}
    >
      <Input.Password autoComplete="current-password" />
    </Form.Item>
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
    {submitButton('修改密码', saving)}
  </Form>
);

const ProfileModal: React.FC<ProfileModalProps> = ({ onCancel, open }) => {
  const { refreshUser, user } = useAuth();
  const [profileForm] = Form.useForm<ProfileFormValues>();
  const [passwordForm] = Form.useForm<PasswordFormValues>();
  const [profileSaving, setProfileSaving] = useState(false);
  const [passwordSaving, setPasswordSaving] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    profileForm.setFieldsValue({
      username: user?.username || '',
      email: user?.email || '',
    });
    passwordForm.resetFields();
  }, [open, passwordForm, profileForm, user]);

  const handleProfile = async (values: ProfileFormValues) => {
    setProfileSaving(true);
    try {
      await authApi.updateProfile(values);
      message.success('资料已更新');
      try {
        await refreshUser();
        onCancel();
      } catch {
        message.info('资料已更新，请重新登录');
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, '资料更新失败'));
    } finally {
      setProfileSaving(false);
    }
  };

  const handlePassword = async (values: PasswordFormValues) => {
    setPasswordSaving(true);
    try {
      await authApi.changePassword({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      });
      message.success('密码已修改，请重新登录');
      window.dispatchEvent(new Event(AUTH_SESSION_EXPIRED_EVENT));
      onCancel();
    } catch (error) {
      message.error(getApiErrorMessage(error, '密码修改失败'));
    } finally {
      setPasswordSaving(false);
    }
  };

  return (
    <Modal
      title="个人中心"
      open={open}
      onCancel={onCancel}
      footer={null}
      width={520}
    >
      <Tabs
        items={[
          {
            key: 'profile',
            label: '资料',
            children: (
              <ProfileForm
                form={profileForm}
                saving={profileSaving}
                onFinish={handleProfile}
              />
            ),
          },
          {
            key: 'password',
            label: '密码',
            children: (
              <PasswordForm
                form={passwordForm}
                saving={passwordSaving}
                onFinish={handlePassword}
              />
            ),
          },
        ]}
      />
    </Modal>
  );
};

export default ProfileModal;
