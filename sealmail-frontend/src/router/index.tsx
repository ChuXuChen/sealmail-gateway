import React from 'react';
import {
  createBrowserRouter,
  RouterProvider,
} from 'react-router-dom';
import { AppRoutes } from './appRoutes';

const router = createBrowserRouter(AppRoutes());

const AppRouter: React.FC = () => {
  return <RouterProvider router={router} />;
};

export default AppRouter;
