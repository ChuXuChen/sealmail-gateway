import React, { Suspense } from 'react';
import {
  createBrowserRouter,
  Navigate,
  RouterProvider,
  useLocation,
} from 'react-router-dom';
import { useAuth } from '../contexts/useAuth';
import {
  canManageCa,
  canManageCertificates,
  canManageDlp,
  canManageDomains,
  canManageMailAuth,
  canViewAuditLogs,
  canViewCrl,
  canViewQuarantine,
} from '../auth/permissions';
import { PageShell } from '../components/Page';
import { ROUTES } from './routes';

const MainLayout = React.lazy(() => import('../components/Layout/MainLayout'));
const Login = React.lazy(() => import('../pages/Login'));
const Dashboard = React.lazy(() => import('../pages/Dashboard'));
const Certificates = React.lazy(() => import('../pages/Certificates'));
const CertificateAuthorities = React.lazy(() => import('../pages/CertificateAuthorities'));
const Crl = React.lazy(() => import('../pages/Crl'));
const ExceptionMails = React.lazy(() => import('../pages/ExceptionMails'));
const DlpQuarantine = React.lazy(() => import('../pages/DlpQuarantine'));
const DlpPatterns = React.lazy(() => import('../pages/DlpPatterns'));
const DlpSelection = React.lazy(() => import('../pages/DlpSelection'));
const DlpEvents = React.lazy(() => import('../pages/DlpEvents'));
const AuditLogs = React.lazy(() => import('../pages/AuditLogs'));
const DomainConfigs = React.lazy(() => import('../pages/DomainConfigs'));
const MailAuth = React.lazy(() => import('../pages/MailAuth'));
const GmEdgePolicyPage = React.lazy(() => import('../pages/policies/GmEdgePolicyPage'));
const SmimeSuitePolicyPage = React.lazy(() => import('../pages/policies/SmimeSuitePolicyPage'));
const RelayPolicyPage = React.lazy(() => import('../pages/policies/RelayPolicyPage'));
const QuarantinePolicyPage = React.lazy(() => import('../pages/policies/QuarantinePolicyPage'));
const SmtpProbePage = React.lazy(() => import('../pages/ops/SmtpProbePage'));
const ProtectedTestMailPage = React.lazy(() => import('../pages/ops/ProtectedTestMailPage'));
const Forbidden = React.lazy(() => import('../pages/Forbidden'));

interface ProtectedRouteProps {
  children: React.ReactNode;
}

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children }) => {
  const { isAuthenticated, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <div>Loading...</div>;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <>{children}</>;
};

interface RoleRouteProps {
  check: (user: ReturnType<typeof useAuth>['user']) => boolean;
  children: React.ReactNode;
}

const RoleRoute: React.FC<RoleRouteProps> = ({ check, children }) => {
  const { user, loading } = useAuth();

  if (loading) {
    return <div>Loading...</div>;
  }
  if (!check(user)) {
    return withSuspense(<Forbidden />);
  }
  return <>{children}</>;
};

const PageLoader = (
  <PageShell>
    Loading...
  </PageShell>
);

const withSuspense = (element: React.ReactNode) => (
  <Suspense fallback={PageLoader}>
    {element}
  </Suspense>
);

const roleElement = (check: RoleRouteProps['check'], element: React.ReactNode) => withSuspense(
  <RoleRoute check={check}>
    {element}
  </RoleRoute>,
);

const router = createBrowserRouter([
  {
    path: '/login',
    element: withSuspense(<Login />),
  },
  {
    path: '/',
    element: withSuspense(
      <ProtectedRoute>
        <MainLayout />
      </ProtectedRoute>,
    ),
    children: [
      {
        index: true,
        element: <Navigate to={ROUTES.dashboard} replace />,
      },
      {
        path: 'dashboard',
        element: withSuspense(<Dashboard />),
      },
      {
        path: 'disposition',
        children: [
          {
            index: true,
            element: <Navigate to={ROUTES.dispositionExceptionMails} replace />,
          },
          {
            path: 'exception-mails',
            element: roleElement(canViewQuarantine, <ExceptionMails />),
          },
          {
            path: 'dlp-quarantine',
            element: roleElement(canViewQuarantine, <DlpQuarantine />),
          },
          {
            path: 'dlp-events',
            element: roleElement(canViewQuarantine, <DlpEvents />),
          },
        ],
      },
      {
        path: 'policies',
        children: [
          {
            index: true,
            element: <Navigate to={ROUTES.policiesDomains} replace />,
          },
          {
            path: 'domains',
            element: roleElement(canManageDomains, <DomainConfigs />),
          },
          {
            path: 'mail-auth',
            element: roleElement(canManageMailAuth, <MailAuth />),
          },
          {
            path: 'dlp-rules',
            element: roleElement(canManageDlp, <DlpPatterns />),
          },
          {
            path: 'dlp-policies',
            element: roleElement(canManageDlp, <DlpSelection />),
          },
          {
            path: 'gm-edge',
            element: roleElement(canManageCa, <GmEdgePolicyPage />),
          },
          {
            path: 'smime-suite',
            element: roleElement(canManageCa, <SmimeSuitePolicyPage />),
          },
          {
            path: 'relay',
            element: roleElement(canManageCa, <RelayPolicyPage />),
          },
          {
            path: 'quarantine',
            element: roleElement(canManageCa, <QuarantinePolicyPage />),
          },
          {
            path: 'smtp-entry',
            element: <Navigate to={ROUTES.dashboard} replace />,
          },
          {
            path: 'delivery',
            element: <Navigate to={ROUTES.dashboard} replace />,
          },
        ],
      },
      {
        path: 'trust',
        children: [
          {
            index: true,
            element: <Navigate to={ROUTES.trustCas} replace />,
          },
          {
            path: 'cas',
            element: roleElement(canManageCa, <CertificateAuthorities />),
          },
          {
            path: 'certificates',
            element: roleElement(canManageCertificates, <Certificates />),
          },
          {
            path: 'crl',
            element: roleElement(canViewCrl, <Crl />),
          },
          {
            path: 'certificate-validation',
            element: <Navigate to={ROUTES.dashboard} replace />,
          },
          {
            path: 'crypto-capabilities',
            element: <Navigate to={ROUTES.dashboard} replace />,
          },
          {
            path: 'gm-tls-edge',
            element: <Navigate to={ROUTES.dashboard} replace />,
          },
        ],
      },
      {
        path: 'ops',
        children: [
          {
            index: true,
            element: <Navigate to={ROUTES.dashboard} replace />,
          },
          {
            path: 'runtime',
            element: <Navigate to={ROUTES.dashboard} replace />,
          },
          {
            path: 'config-overview',
            element: <Navigate to={ROUTES.dashboard} replace />,
          },
          {
            path: 'tools',
            element: <Navigate to={ROUTES.opsSmtpProbe} replace />,
          },
          {
            path: 'smtp-entry',
            element: <Navigate to={ROUTES.dashboard} replace />,
          },
          {
            path: 'delivery',
            element: <Navigate to={ROUTES.dashboard} replace />,
          },
          {
            path: 'certificate-validation',
            element: <Navigate to={ROUTES.dashboard} replace />,
          },
          {
            path: 'crypto-capabilities',
            element: <Navigate to={ROUTES.dashboard} replace />,
          },
          {
            path: 'gm-tls-edge',
            element: <Navigate to={ROUTES.dashboard} replace />,
          },
          {
            path: 'smtp-probe',
            element: roleElement(canManageCa, <SmtpProbePage />),
          },
          {
            path: 'protected-test-mail',
            element: roleElement(canManageCa, <ProtectedTestMailPage />),
          },
          {
            path: 'audit-logs',
            element: roleElement(canViewAuditLogs, <AuditLogs />),
          },
        ],
      },
      {
        path: '403',
        element: withSuspense(<Forbidden />),
      },
      {
        path: 'settings',
        element: <Navigate to={ROUTES.dashboard} replace />,
      },
      {
        path: 'exception-mails',
        element: <Navigate to={ROUTES.dispositionExceptionMails} replace />,
      },
      {
        path: 'quarantine',
        element: <Navigate to={ROUTES.dispositionExceptionMails} replace />,
      },
      {
        path: 'domains',
        element: <Navigate to={ROUTES.policiesDomains} replace />,
      },
      {
        path: 'mail-auth',
        element: <Navigate to={ROUTES.policiesMailAuth} replace />,
      },
      {
        path: 'audit-logs',
        element: <Navigate to={ROUTES.opsAuditLogs} replace />,
      },
      {
        path: 'dlp',
        children: [
          {
            index: true,
            element: <Navigate to={ROUTES.policiesDlpRules} replace />,
          },
          {
            path: 'patterns',
            element: <Navigate to={ROUTES.policiesDlpRules} replace />,
          },
          {
            path: 'selection',
            element: <Navigate to={ROUTES.policiesDlpPolicies} replace />,
          },
          {
            path: 'quarantine',
            element: <Navigate to={ROUTES.dispositionDlpQuarantine} replace />,
          },
          {
            path: 'events',
            element: <Navigate to={ROUTES.dispositionDlpEvents} replace />,
          },
        ],
      },
      {
        path: 'smime',
        children: [
          {
            index: true,
            element: <Navigate to={ROUTES.trustCas} replace />,
          },
          {
            path: 'cas',
            element: <Navigate to={ROUTES.trustCas} replace />,
          },
          {
            path: 'certificates',
            element: <Navigate to={ROUTES.trustCertificates} replace />,
          },
          {
            path: 'crl',
            element: <Navigate to={ROUTES.trustCrl} replace />,
          },
        ],
      },
      {
        path: 'cas',
        element: <Navigate to={ROUTES.trustCas} replace />,
      },
      {
        path: 'certificates',
        element: <Navigate to={ROUTES.trustCertificates} replace />,
      },
      {
        path: 'crl',
        element: <Navigate to={ROUTES.trustCrl} replace />,
      },
    ],
  },
]);

const AppRouter: React.FC = () => {
  return <RouterProvider router={router} />;
};

export default AppRouter;
