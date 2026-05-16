import React, { useState } from 'react';
import { Alert, Form, Input, Button, Card, Typography } from 'antd';
import { LockOutlined, MailOutlined } from '@ant-design/icons';
import { useAuth } from '../contexts/useAuth';
import { useNavigate, Navigate, useLocation } from 'react-router-dom';
import { getApiErrorMessage } from '../api/errors';
import SealMailLogo from '../components/Brand/SealMailLogo';
import './Login.css';

const { Text } = Typography;

const Login: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [loginError, setLoginError] = useState('');
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname || '/dashboard';

  if (isAuthenticated) {
    return <Navigate to={from} replace />;
  }

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true);
    setLoginError('');
    try {
      await login(values.username, values.password);
      navigate(from, { replace: true });
    } catch (error) {
      setLoginError(getApiErrorMessage(error, '登录失败，请检查账号和密码'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="login-page">
      <Card className="login-card">
        <div className="login-card__brand">
          <SealMailLogo variant="login" subtitle="邮件安全网关管理系统" />
          <Text type="secondary">请使用管理员账号登录</Text>
        </div>

        <Form className="login-form" name="login" onFinish={onFinish} autoComplete="off" size="large">
          {loginError ? (
            <Alert
              className="login-form__alert"
              type="error"
              message={loginError}
              showIcon
            />
          ) : null}

          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input prefix={<MailOutlined />} placeholder="用户名" />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              htmlType="submit"
              className="login-form__submit"
              loading={loading}
              block
            >
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </main>
  );
};

export default Login;
