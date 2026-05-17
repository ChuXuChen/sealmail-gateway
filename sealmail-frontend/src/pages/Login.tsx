import React, { useState } from 'react';
import { Alert, Form, Input, Button, Typography } from 'antd';
import { AuditOutlined, LockOutlined, MailOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useAuth } from '../contexts/useAuth';
import { useNavigate, Navigate, useLocation } from 'react-router-dom';
import { getApiErrorMessage } from '../api/errors';
import SealMailLogo from '../components/Brand/SealMailLogo';
import heroImage from '../assets/hero.png';
import './Login.css';

const { Text, Title } = Typography;

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
      <section className="login-hero" aria-hidden="true">
        <div className="login-hero__copy">
          <Text className="login-hero__eyebrow">SealMail Gateway</Text>
          <Title level={1}>邮件链路安全控制台</Title>
          <Text className="login-hero__text">
            S/MIME、DLP、邮件认证和审计能力在同一个运维界面中集中呈现。
          </Text>
        </div>
        <div className="login-hero__visual">
          <img src={heroImage} alt="" />
        </div>
        <div className="login-hero__metrics">
          <div>
            <SafetyCertificateOutlined />
            <span>证书链路</span>
          </div>
          <div>
            <AuditOutlined />
            <span>审计追踪</span>
          </div>
        </div>
      </section>

      <section className="login-panel">
        <div className="login-panel__brand">
          <SealMailLogo variant="login" subtitle="邮件安全网关管理系统" />
          <Text type="secondary">管理员访问入口</Text>
        </div>

        <Form className="login-form" name="login" onFinish={onFinish} autoComplete="off" size="large" layout="vertical">
          {loginError ? (
            <Alert
              className="login-form__alert"
              type="error"
              message={loginError}
              showIcon
            />
          ) : null}

          <Form.Item
            label="账号"
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input prefix={<MailOutlined />} placeholder="用户名" />
          </Form.Item>

          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>

          <Form.Item className="login-form__submit-row">
            <Button
              type="primary"
              htmlType="submit"
              className="login-form__submit"
              loading={loading}
              block
            >
              登录控制台
            </Button>
          </Form.Item>
        </Form>
      </section>
    </main>
  );
};

export default Login;
