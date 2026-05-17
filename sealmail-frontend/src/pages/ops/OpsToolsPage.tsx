import React from 'react';
import { Button, Card, Col, Row, Space, Typography } from 'antd';
import { SendOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { PageHeader, PageShell } from '../../components/Page';
import { ROUTES } from '../../router/routes';

const { Text } = Typography;

const OpsToolsPage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <PageShell>
      <PageHeader
        title="运维工具"
        description="集中进入 SMTP 链路探测和受保护测试邮件。"
      />
      <Row gutter={[16, 16]}>
        <Col xs={24} md={12}>
          <Card title="SMTP 探测">
            <Space direction="vertical" size={12} className="full-width">
              <Text>测试当前投递链路是否能建立 SMTP 连接。</Text>
              <Button icon={<ThunderboltOutlined />} onClick={() => navigate(ROUTES.opsSmtpProbe)}>
                进入探测
              </Button>
            </Space>
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title="受保护测试邮件">
            <Space direction="vertical" size={12} className="full-width">
              <Text>提交一封测试邮件，验证当前域名策略、证书绑定和投递链路。</Text>
              <Button type="primary" icon={<SendOutlined />} onClick={() => navigate(ROUTES.opsProtectedTestMail)}>
                编写测试邮件
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>
    </PageShell>
  );
};

export default OpsToolsPage;
