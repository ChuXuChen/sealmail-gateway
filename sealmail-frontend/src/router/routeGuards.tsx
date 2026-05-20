import React, { Suspense } from 'react';
import {
  Navigate,
  useLocation,
} from 'react-router-dom';
import { useAuth } from '../contexts/useAuth';
import { PageShell } from '../components/Page';

const Forbidden = React.lazy(() => import('../pages/Forbidden'));

interface ProtectedRouteProps {
  children: React.ReactNode;
}

interface RoleRouteProps {
  check: (user: ReturnType<typeof useAuth>['user']) => boolean;
  children: React.ReactNode;
}

const PageLoader = (
  <PageShell>
    Loading...
  </PageShell>
);

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children }) => {
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

export const RoleRoute: React.FC<RoleRouteProps> = ({ check, children }) => {
  const { user, loading } = useAuth();

  if (loading) {
    return <div>Loading...</div>;
  }
  if (!check(user)) {
    return (
      <Suspense fallback={PageLoader}>
        <Forbidden />
      </Suspense>
    );
  }
  return <>{children}</>;
};
