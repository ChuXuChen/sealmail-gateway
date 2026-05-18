import React, { useMemo, useState } from 'react';
import { Layout, Menu, Avatar, Dropdown, Grid, Button, Drawer, Space, Tag, Typography, theme } from 'antd';
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
  MailOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ToolOutlined,
  ThunderboltOutlined,
  SendOutlined,
} from '@ant-design/icons';
import type { ItemType } from 'antd/es/menu/interface';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../contexts/useAuth';
import {
  canManageCa,
  canManageCertificates,
  canManageDlp,
  canManageDomains,
  canManageMailAuth,
  canManageUsers,
  canViewAuditLogs,
  canViewCrl,
  canViewQuarantine,
  getPermissionSummary,
  getPrimaryRole,
} from '../../auth/permissions';
import SealMailLogo from '../Brand/SealMailLogo';
import { ROUTES } from '../../router/routes';
import ProfileModal from '../../pages/account/ProfileModal';

const { Header, Sider, Content } = Layout;
const { useBreakpoint } = Grid;
const { Text } = Typography;

const siderWidth = 212;
const collapsedWidth = 92;

const MainLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const {
    token: { colorBgContainer },
  } = theme.useToken();
  const screens = useBreakpoint();
  const isMobile = !screens.md;
  const role = getPrimaryRole(user);
  const permissionSummary = getPermissionSummary(user);

  const dispositionChildren = useMemo<ItemType[]>(() => [
    canViewQuarantine(user) ? {
      key: ROUTES.dispositionExceptionMails,
      icon: <WarningOutlined />,
      label: '异常邮件',
    } : null,
    canViewQuarantine(user) ? {
      key: ROUTES.dispositionDlpQuarantine,
      icon: <InboxOutlined />,
      label: 'DLP 隔离复核',
    } : null,
    canViewQuarantine(user) ? {
      key: ROUTES.dispositionDlpEvents,
      icon: <AuditOutlined />,
      label: 'DLP 命中事件',
    } : null,
  ].filter(Boolean) as ItemType[], [user]);

  const policyChildren = useMemo<ItemType[]>(() => [
    canManageDomains(user) ? {
      key: ROUTES.policiesDomains,
      icon: <GlobalOutlined />,
      label: '域名配置',
    } : null,
    canManageMailAuth(user) ? {
      key: ROUTES.policiesMailAuth,
      icon: <MailOutlined />,
      label: '邮件认证',
    } : null,
    canManageDlp(user) ? {
      key: ROUTES.policiesDlpRules,
      icon: <FileTextOutlined />,
      label: 'DLP 规则库',
    } : null,
    canManageDlp(user) ? {
      key: ROUTES.policiesDlpPolicies,
      icon: <PartitionOutlined />,
      label: 'DLP 策略集',
    } : null,
    canManageCa(user) ? {
      key: ROUTES.policiesGmEdge,
      icon: <SafetyOutlined />,
      label: '国密 Edge',
    } : null,
    canManageCa(user) ? {
      key: ROUTES.policiesSmimeSuite,
      icon: <FileProtectOutlined />,
      label: 'S/MIME 套件',
    } : null,
    canManageCa(user) ? {
      key: ROUTES.policiesRelay,
      icon: <MailOutlined />,
      label: 'Relay 策略',
    } : null,
    canManageCa(user) ? {
      key: ROUTES.policiesQuarantine,
      icon: <InboxOutlined />,
      label: '隔离策略',
    } : null,
  ].filter(Boolean) as ItemType[], [user]);

  const trustChildren = useMemo<ItemType[]>(() => [
    canManageCa(user) ? {
      key: ROUTES.trustCas,
      icon: <AuditOutlined />,
      label: 'CA 证书',
    } : null,
    canManageCertificates(user) ? {
      key: ROUTES.trustCertificates,
      icon: <FileProtectOutlined />,
      label: '终端证书',
    } : null,
    canViewCrl(user) ? {
      key: ROUTES.trustCrl,
      icon: <StopOutlined />,
      label: 'CRL 吊销列表',
    } : null,
  ].filter(Boolean) as ItemType[], [user]);

  const opsChildren = useMemo<ItemType[]>(() => [
    canManageUsers(user) ? {
      key: ROUTES.opsUsers,
      icon: <UserOutlined />,
      label: '用户管理',
    } : null,
    canManageCa(user) ? {
      key: ROUTES.opsSmtpProbe,
      icon: <ThunderboltOutlined />,
      label: 'SMTP 探测',
    } : null,
    canManageCa(user) ? {
      key: ROUTES.opsProtectedTestMail,
      icon: <SendOutlined />,
      label: '受保护测试邮件',
    } : null,
    canViewAuditLogs(user) ? {
      key: ROUTES.opsAuditLogs,
      icon: <FileTextOutlined />,
      label: '审计日志',
    } : null,
  ].filter(Boolean) as ItemType[], [user]);

  const menuItems = useMemo<ItemType[]>(() => [
    {
      key: ROUTES.dashboard,
      icon: <DashboardOutlined />,
      label: 'Dashboard',
    },
    dispositionChildren.length > 0 ? {
      key: 'disposition',
      icon: <InboxOutlined />,
      label: '处置中心',
      children: dispositionChildren,
    } : null,
    policyChildren.length > 0 ? {
      key: 'policies',
      icon: <SettingOutlined />,
      label: '策略中心',
      children: policyChildren,
    } : null,
    trustChildren.length > 0 ? {
      key: 'trust',
      icon: <SafetyOutlined />,
      label: '证书与信任',
      children: trustChildren,
    } : null,
    opsChildren.length > 0 ? {
      key: 'ops',
      icon: <ToolOutlined />,
      label: '系统运维',
      children: opsChildren,
    } : null,
  ].filter(Boolean) as ItemType[], [dispositionChildren, opsChildren, policyChildren, trustChildren]);

  const aliasMenuKeys: Record<string, string> = {
    [ROUTES.opsRuntime]: ROUTES.dashboard,
    [ROUTES.opsConfigOverview]: ROUTES.dashboard,
    [ROUTES.opsTools]: ROUTES.opsSmtpProbe,
    [ROUTES.opsSmtpEntry]: ROUTES.dashboard,
    [ROUTES.opsDelivery]: ROUTES.dashboard,
    [ROUTES.opsCertificateValidation]: ROUTES.dashboard,
    [ROUTES.opsCryptoCapabilities]: ROUTES.dashboard,
    [ROUTES.opsGmTlsEdge]: ROUTES.dashboard,
    [ROUTES.trustCertificateValidation]: ROUTES.dashboard,
    [ROUTES.trustCryptoCapabilities]: ROUTES.dashboard,
    [ROUTES.trustGmTlsEdge]: ROUTES.dashboard,
    [ROUTES.policiesSmtpEntry]: ROUTES.dashboard,
    [ROUTES.policiesDelivery]: ROUTES.dashboard,
  };
  const selectedMenuKey = aliasMenuKeys[location.pathname] || location.pathname;

  const handleMenuClick = ({ key }: { key: string }) => {
    if (!key.startsWith('/')) return;
    navigate(key);
    setMobileNavOpen(false);
  };

  const userMenuItems = [
    {
      key: 'user-info',
      disabled: true,
      label: (
        <div className="main-layout__user-menu">
          <Text strong>{user?.username || 'User'}</Text>
          <Text type="secondary" className="main-layout__user-email">
            {user?.email || '-'}
          </Text>
          <Space size={[4, 4]} wrap>
            <Tag>{role}</Tag>
            {permissionSummary.slice(0, 3).map((item) => (
              <Tag key={item} color="blue">{item}</Tag>
            ))}
          </Space>
        </div>
      ),
    },
    {
      type: 'divider' as const,
    },
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '个人中心',
      onClick: () => setProfileOpen(true),
    },
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
        className="main-layout__menu"
        theme="dark"
        selectedKeys={[selectedMenuKey]}
        defaultOpenKeys={['disposition', 'policies', 'trust', 'ops']}
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
          collapsedWidth={collapsedWidth}
          onCollapse={setCollapsed}
          width={siderWidth}
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
          body: { padding: 0, background: '#17211f' },
          content: { background: '#17211f' },
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
          ) : <span className="main-layout__header-spacer" />}
          <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
            <div className="main-layout__user">
              <Avatar icon={<UserOutlined />} />
              <span className="main-layout__user-name">{user?.username || 'User'}</span>
              <Tag>{role}</Tag>
            </div>
          </Dropdown>
        </Header>

        <Content className="main-layout__content">
          <Outlet />
        </Content>
      </Layout>
      <ProfileModal open={profileOpen} onCancel={() => setProfileOpen(false)} />
    </Layout>
  );
};

export default MainLayout;
