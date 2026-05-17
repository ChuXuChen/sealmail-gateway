import React, { useCallback, useState } from 'react';
import { Button, Card, Typography, message } from 'antd';
import { ThunderboltOutlined } from '@ant-design/icons';
import { mailTestApi } from '../../api/client';
import { getApiErrorMessage } from '../../api/errors';
import { PageHeader, PageShell } from '../../components/Page';

const { Text } = Typography;

const SmtpProbePage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState('');

  const handleProbe = useCallback(async () => {
    setLoading(true);
    try {
      const response = await mailTestApi.testSmtpConfig();
      if (!response.data.success) {
        throw new Error(response.data.message);
      }
      const probeResult = response.data.data || '';
      setResult(probeResult);
      if (probeResult.includes('FAILED')) {
        message.warning('SMTP 探测完成，存在失败链路');
      } else {
        message.success('SMTP 探测通过');
      }
    } catch (error) {
      message.error(getApiErrorMessage(error, 'SMTP 探测失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  return (
    <PageShell>
      <PageHeader
        title="SMTP 探测"
        description="测试当前投递链路是否能建立 SMTP 连接。"
        actions={(
          <Button type="primary" icon={<ThunderboltOutlined />} loading={loading} onClick={handleProbe}>
            开始探测
          </Button>
        )}
      />
      <Card title="探测结果">
        {result ? (
          <pre className="settings-probe-result">{result}</pre>
        ) : (
          <div className="settings-empty-result">
            <Text>尚未执行探测。</Text>
          </div>
        )}
      </Card>
    </PageShell>
  );
};

export default SmtpProbePage;
