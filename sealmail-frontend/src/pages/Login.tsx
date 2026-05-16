import React, { useState } from 'react';
import { Form, Input, Button, Card } from 'antd';
import { LockOutlined, MailOutlined } from '@ant-design/icons';
import { useAuth } from '../contexts/AuthContext';
import { useNavigate, Navigate } from 'react-router-dom';
import SealMailLogo from '../components/Brand/SealMailLogo';
import './Login.css';

const Login: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      await login(values.username, values.password);
      navigate('/dashboard');
    } catch {
      // Error handled in context
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="login-page">
      <div className="login-scene" aria-hidden="true">
        <span className="login-scene__gateway" />
        <span className="login-scene__gateway login-scene__gateway--inner" />
        <span className="login-scene__stream login-scene__stream--one" />
        <span className="login-scene__stream login-scene__stream--two" />
        <span className="login-scene__stream login-scene__stream--three" />
        <span className="login-scene__node login-scene__node--one" />
        <span className="login-scene__node login-scene__node--two" />
        <span className="login-scene__node login-scene__node--three" />
      </div>

      <Card className="login-card" bordered={false}>
        <div className="login-card__brand">
          <SealMailLogo variant="login" subtitle="邮件安全网关管理系统" />
        </div>

        <Form className="login-form" name="login" onFinish={onFinish} autoComplete="off" size="large">
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
