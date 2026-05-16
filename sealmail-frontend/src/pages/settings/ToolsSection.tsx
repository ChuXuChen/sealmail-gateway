import React from 'react';
import { Button, Card, Col, Row, Space, Typography } from 'antd';
import { SendOutlined, ThunderboltOutlined } from '@ant-design/icons';

const { Text } = Typography;

interface ToolsSectionProps {
  onOpenProbe: () => void;
  onOpenTest: () => void;
}

const ToolsSection: React.FC<ToolsSectionProps> = ({ onOpenProbe, onOpenTest }) => (
  <Card title="运维工具">
    <Row gutter={[16, 16]}>
      <Col xs={24} md={12}>
        <Card type="inner" title="SMTP 探测">
          <Space direction="vertical" size={12} className="full-width">
            <Text>测试当前投递链路是否能建立 SMTP 连接。</Text>
            <Button icon={<ThunderboltOutlined />} onClick={onOpenProbe}>
              打开探测
            </Button>
          </Space>
        </Card>
      </Col>
      <Col xs={24} md={12}>
        <Card type="inner" title="受保护测试邮件">
          <Space direction="vertical" size={12} className="full-width">
            <Text>提交一封测试邮件，验证当前域名策略、证书绑定和投递链路。</Text>
            <Button type="primary" icon={<SendOutlined />} onClick={onOpenTest}>
              发送测试
            </Button>
          </Space>
        </Card>
      </Col>
    </Row>
  </Card>
);

export default ToolsSection;
