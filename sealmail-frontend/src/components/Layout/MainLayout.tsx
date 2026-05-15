import React, { useState } from 'react';
import { Layout, Menu, Avatar, Dropdown, theme } from 'antd';
import {
  DashboardOutlined,
  SafetyOutlined,
  InboxOutlined,
  SettingOutlined,
  LogoutOutlined,
  UserOutlined,
  FileTextOutlined,
  GlobalOutlined,
  AuditOutlined,
  FileProtectOutlined,
  StopOutlined,
  WarningOutlined,
  PartitionOutlined,
} from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import {
  canManageCa,
  canManageCertificates,
  canManageDlp,
  canManageDomains,
  canViewAuditLogs,
  canViewCrl,
  canViewQuarantine,
} from '../../auth/permissions';
import SealMailLogo from '../Brand/SealMailLogo';

const { Header, Sider, Content } = Layout;

const MainLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken();

  const smimeChildren = [
    canManageCa(user) ? {
      key: '/smime/cas',
      icon: <AuditOutlined />,
      label: 'CA 证书',
    } : null,
    canManageCertificates(user) ? {
      key: '/smime/certificates',
      icon: <FileProtectOutlined />,
      label: '终端证书',
    } : null,
    canViewCrl(user) ? {
      key: '/smime/crl',
      icon: <StopOutlined />,
      label: 'CRL 吊销列表',
    } : null,
  ].filter(Boolean);

  const menuItems = [
    {
      key: '/dashboard',
      icon: <DashboardOutlined />,
      label: '仪表盘',
    },
    smimeChildren.length > 0 ? {
      key: 'smime',
      icon: <SafetyOutlined />,
      label: 'S/MIME',
      children: smimeChildren,
    } : null,
    canViewQuarantine(user) ? {
      key: '/exception-mails',
      icon: <WarningOutlined />,
      label: '异常邮件',
    } : null,
    (canManageDlp(user) || canViewQuarantine(user)) ? {
      key: 'dlp',
      icon: <InboxOutlined />,
      label: 'DLP',
      children: [
        canManageDlp(user) ? {
          key: '/dlp/patterns',
          icon: <FileTextOutlined />,
          label: '检测规则',
        } : null,
        canManageDlp(user) ? {
          key: '/dlp/selection',
          icon: <PartitionOutlined />,
          label: '生效范围',
        } : null,
        canViewQuarantine(user) ? {
          key: '/dlp/quarantine',
          icon: <InboxOutlined />,
          label: '隔离邮件',
        } : null,
      ].filter(Boolean),
    } : null,
    canManageDomains(user) ? {
      key: '/domains',
      icon: <GlobalOutlined />,
      label: '域名配置',
    } : null,
    canViewAuditLogs(user) ? {
      key: '/audit-logs',
      icon: <FileTextOutlined />,
      label: '审计日志',
    } : null,
    canManageCa(user) ? {
      key: '/settings',
      icon: <SettingOutlined />,
      label: '系统设置',
    } : null,
  ].filter(Boolean);

  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key);
  };

  const userMenuItems = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: logout,
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        style={{ background: '#001529' }}
      >
        <div className={`sealmail-sider-brand${collapsed ? ' sealmail-sider-brand--collapsed' : ''}`}>
          <SealMailLogo collapsed={collapsed} />
        </div>

        <Menu
          theme="dark"
          selectedKeys={[location.pathname]}
          defaultOpenKeys={['smime', 'dlp']}
          mode="inline"
          items={menuItems}
          onClick={handleMenuClick}
        />
      </Sider>

      <Layout>
        <Header
          style={{
            padding: '0 24px',
            background: colorBgContainer,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            borderBottom: '1px solid #f0f0f0',
          }}
        >
          <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
            <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center' }}>
              <Avatar icon={<UserOutlined />} style={{ marginRight: 8 }} />
              <span>{user?.username || 'User'}</span>
            </div>
          </Dropdown>
        </Header>

        <Content
          style={{
            margin: '24px',
            padding: 24,
            minHeight: 280,
            background: colorBgContainer,
            borderRadius: borderRadiusLG,
          }}
        >
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default MainLayout;
