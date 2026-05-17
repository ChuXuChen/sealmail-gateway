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
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#2f6f64',
          colorInfo: '#315f9f',
          colorSuccess: '#2f7d57',
          colorWarning: '#ad7b18',
          colorError: '#bd3f2a',
          colorBgBase: '#f5f6f2',
          colorTextBase: '#202827',
          colorBorder: '#d9e0d8',
          borderRadius: 8,
          borderRadiusLG: 8,
          fontFamily:
            '"Inter", "MiSans", "PingFang SC", "Microsoft YaHei UI", "Microsoft YaHei", "Noto Sans CJK SC", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
          fontFamilyCode:
            '"JetBrains Mono", "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace',
          fontSize: 14,
          fontSizeHeading1: 40,
          fontSizeHeading2: 30,
          fontSizeHeading3: 24,
          fontSizeHeading4: 20,
          fontSizeHeading5: 16,
          fontWeightStrong: 650,
          lineHeight: 1.58,
          lineHeightHeading1: 1.12,
          lineHeightHeading2: 1.18,
          lineHeightHeading3: 1.22,
          wireframe: false,
        },
        components: {
          Layout: {
            bodyBg: '#f5f6f2',
            headerBg: '#fbfcf8',
            siderBg: '#17211f',
          },
          Menu: {
            darkItemBg: '#17211f',
            darkSubMenuItemBg: '#111816',
            darkItemHoverBg: '#22312e',
            darkItemSelectedBg: '#2f6f64',
            darkItemColor: 'rgba(247, 250, 246, 0.78)',
            darkItemSelectedColor: '#ffffff',
          },
          Card: {
            headerBg: '#ffffff',
            headerFontSize: 15,
            headerFontSizeSM: 14,
            paddingLG: 20,
          },
          Table: {
            cellFontSize: 13,
            cellFontSizeSM: 13,
            cellPaddingBlock: 13,
            cellPaddingBlockSM: 10,
            headerBg: '#eef3ee',
            headerColor: '#3b4643',
            headerSplitColor: '#dce4dc',
            rowHoverBg: '#eef7f3',
          },
          Button: {
            borderRadius: 7,
            controlHeight: 34,
            fontWeight: 620,
          },
          Input: {
            controlHeight: 34,
            fontSize: 14,
          },
          Select: {
            controlHeight: 34,
            optionFontSize: 14,
          },
          Tabs: {
            inkBarColor: '#2f6f64',
            itemSelectedColor: '#2f6f64',
            itemHoverColor: '#2f6f64',
            titleFontSize: 14,
          },
          Tag: {
            fontSize: 12,
            lineHeight: 1.5,
          },
          Typography: {
            titleMarginBottom: 0,
            titleMarginTop: 0,
          },
        },
      }}
    >
      <AuthProvider>
        <App />
      </AuthProvider>
    </ConfigProvider>
  </React.StrictMode>
);
