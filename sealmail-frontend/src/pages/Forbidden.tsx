import React from 'react';
import { Button, Result } from 'antd';
import { useNavigate } from 'react-router-dom';
import { PageShell } from '../components/Page';

const Forbidden: React.FC = () => {
  const navigate = useNavigate();

  return (
    <PageShell>
      <Result
        status="403"
        title="无权限访问"
        subTitle="当前账号没有访问该功能所需的权限。"
        extra={(
          <Button type="primary" onClick={() => navigate('/dashboard')}>
            返回仪表盘
          </Button>
        )}
      />
    </PageShell>
  );
};

export default Forbidden;
