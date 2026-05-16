import React, { useMemo, useState } from 'react';
import { Layout, Menu, Avatar, Dropdown, Grid, Button, Drawer, theme } from 'antd';
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
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';
import type { ItemType } from 'antd/es/menu/interface';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../contexts/useAuth';
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
const { useBreakpoint } = Grid;

const MainLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const {
    token: { colorBgContainer },
  } = theme.useToken();
  const screens = useBreakpoint();
  const isMobile = !screens.md;

  const smimeChildren = useMemo<ItemType[]>(() => [
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
  ].filter(Boolean) as ItemType[], [user]);

  const menuItems = useMemo<ItemType[]>(() => [
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
  ].filter(Boolean) as ItemType[], [smimeChildren, user]);

  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key);
    setMobileNavOpen(false);
  };

  const userMenuItems = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: logout,
    },
  ];

  const navigation = (
    <>
      <div className={`sealmail-sider-brand${collapsed && !isMobile ? ' sealmail-sider-brand--collapsed' : ''}`}>
        <SealMailLogo collapsed={collapsed && !isMobile} />
      </div>

      <Menu
        theme="dark"
        selectedKeys={[location.pathname]}
        defaultOpenKeys={['smime', 'dlp']}
        mode="inline"
        items={menuItems}
        onClick={handleMenuClick}
      />
    </>
  );

  return (
    <Layout className={`main-layout${collapsed ? ' main-layout--collapsed' : ''}${isMobile ? ' main-layout--mobile' : ''}`}>
      {!isMobile && (
        <Sider
          className="main-layout__sider"
          collapsible
          collapsed={collapsed}
          onCollapse={setCollapsed}
        >
          {navigation}
        </Sider>
      )}

      <Drawer
        className="main-layout__nav-drawer"
        placement="left"
        open={isMobile && mobileNavOpen}
        onClose={() => setMobileNavOpen(false)}
        width={240}
        styles={{
          body: { padding: 0, background: '#001529' },
          content: { background: '#001529' },
        }}
      >
        {navigation}
      </Drawer>

      <Layout className="main-layout__body">
        <Header
          className="main-layout__header"
          style={{ background: colorBgContainer }}
        >
          {isMobile ? (
            <Button
              type="text"
              icon={mobileNavOpen ? <MenuFoldOutlined /> : <MenuUnfoldOutlined />}
              onClick={() => setMobileNavOpen(true)}
              aria-label="打开导航"
            />
          ) : <span />}
          <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
            <div className="main-layout__user">
              <Avatar icon={<UserOutlined />} />
              <span>{user?.username || 'User'}</span>
            </div>
          </Dropdown>
        </Header>

        <Content className="main-layout__content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default MainLayout;
