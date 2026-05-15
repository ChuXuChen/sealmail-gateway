import React from 'react';
import { createRoot } from 'react-dom/client';
import { ConfigProvider, unstableSetRender } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { AuthProvider } from './contexts/AuthContext';
import App from './App';
import './index.css';

const antdRoots = new WeakMap<Element | DocumentFragment, ReturnType<typeof createRoot>>();

unstableSetRender((node, container) => {
  const root = antdRoots.get(container) ?? createRoot(container);
  antdRoots.set(container, root);
  root.render(node);
  return async () => {
    root.unmount();
    antdRoots.delete(container);
  };
});

const root = createRoot(document.getElementById('root')!);

root.render(
  <React.StrictMode>
    <ConfigProvider locale={zhCN}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </ConfigProvider>
  </React.StrictMode>
);
