import React from 'react';
import { Drawer } from 'antd';
import type { DrawerProps } from 'antd';

const DetailDrawer: React.FC<DrawerProps> = ({
  width = 640,
  className,
  styles,
  ...props
}) => (
  <Drawer
    width={width}
    className={['detail-drawer', className].filter(Boolean).join(' ')}
    styles={{
      ...styles,
      body: {
        overflowX: 'auto',
        ...styles?.body,
      },
    }}
    {...props}
  />
);

export default DetailDrawer;
