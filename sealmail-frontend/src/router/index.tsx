import React, { Suspense } from 'react';
import {
  createBrowserRouter,
  Navigate,
  RouterProvider,
} from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import {
  canManageCa,
  canManageCertificates,
  canManageDlp,
  canManageDomains,
  canViewAuditLogs,
  canViewCrl,
  canViewQuarantine,
} from '../auth/permissions';

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
const Settings = React.lazy(() => import('../pages/Settings'));
const AuditLogs = React.lazy(() => import('../pages/AuditLogs'));
const DomainConfigs = React.lazy(() => import('../pages/DomainConfigs'));

interface ProtectedRouteProps {
  children: React.ReactNode;
}

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children }) => {
  const { isAuthenticated, loading } = useAuth();

  if (loading) {
    return <div>Loading...</div>;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
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
    return <Navigate to="/dashboard" replace />;
  }
  return <>{children}</>;
};

const PageLoader = (
  <div style={{ padding: 24 }}>
    Loading...
  </div>
);

const withSuspense = (element: React.ReactNode) => (
  <Suspense fallback={PageLoader}>
    {element}
  </Suspense>
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
        element: <Navigate to="/dashboard" replace />,
      },
      {
        path: 'dashboard',
        element: withSuspense(<Dashboard />),
      },
      {
        path: 'smime',
        children: [
          {
            index: true,
            element: <Navigate to="/smime/cas" replace />,
          },
          {
            path: 'cas',
            element: withSuspense(
              <RoleRoute check={canManageCa}>
                <CertificateAuthorities />
              </RoleRoute>,
            ),
          },
          {
            path: 'certificates',
            element: withSuspense(
              <RoleRoute check={canManageCertificates}>
                <Certificates />
              </RoleRoute>,
            ),
          },
          {
            path: 'crl',
            element: withSuspense(
              <RoleRoute check={canViewCrl}>
                <Crl />
              </RoleRoute>,
            ),
          },
        ],
      },
      {
        path: 'exception-mails',
        element: withSuspense(
          <RoleRoute check={canViewQuarantine}>
            <ExceptionMails />
          </RoleRoute>,
        ),
      },
      {
        path: 'quarantine',
        element: <Navigate to="/exception-mails" replace />,
      },
      {
        path: 'dlp/patterns',
        element: withSuspense(
          <RoleRoute check={canManageDlp}>
            <DlpPatterns />
          </RoleRoute>,
        ),
      },
      {
        path: 'dlp/selection',
        element: withSuspense(
          <RoleRoute check={canManageDlp}>
            <DlpSelection />
          </RoleRoute>,
        ),
      },
      {
        path: 'dlp/quarantine',
        element: withSuspense(
          <RoleRoute check={canViewQuarantine}>
            <DlpQuarantine />
          </RoleRoute>,
        ),
      },
      {
        path: 'settings',
        element: withSuspense(
          <RoleRoute check={canManageCa}>
            <Settings />
          </RoleRoute>,
        ),
      },
      {
        path: 'audit-logs',
        element: withSuspense(
          <RoleRoute check={canViewAuditLogs}>
            <AuditLogs />
          </RoleRoute>,
        ),
      },
      {
        path: 'domains',
        element: withSuspense(
          <RoleRoute check={canManageDomains}>
            <DomainConfigs />
          </RoleRoute>,
        ),
      },
      {
        path: 'cas',
        element: <Navigate to="/smime/cas" replace />,
      },
      {
        path: 'certificates',
        element: <Navigate to="/smime/certificates" replace />,
      },
      {
        path: 'crl',
        element: <Navigate to="/smime/crl" replace />,
      },
    ],
  },
]);

const AppRouter: React.FC = () => {
  return <RouterProvider router={router} />;
};

export default AppRouter;
