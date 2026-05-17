import React, { useEffect, useMemo, useState } from 'react';
import { Button, Card, Col, Form, Row, Select, Space, Statistic, Tag } from 'antd';
import { ReloadOutlined, RetweetOutlined } from '@ant-design/icons';
import { PageHeader, PageShell } from '../components/Page';
import MailAuthDnsRecords from './mail-auth/MailAuthDnsRecords';
import MailAuthDomainPolicyForm from './mail-auth/MailAuthDomainPolicyForm';
import MailAuthGlobalPolicyForm from './mail-auth/MailAuthGlobalPolicyForm';
import MailAuthProbeTable from './mail-auth/MailAuthProbeTable';
import MailAuthRotateDkimModal from './mail-auth/MailAuthRotateDkimModal';
import type {
  MailAuthDomainFormValues,
  MailAuthGlobalFormValues,
  RotateDkimFormValues,
} from './mail-auth/mailAuthUtils';
import {
  domainFormValues,
  emptyDomainPolicy,
  globalFormValues,
} from './mail-auth/mailAuthUtils';
import { useMailAuth } from './mail-auth/useMailAuth';

const MailAuth: React.FC = () => {
  const [rotateOpen, setRotateOpen] = useState(false);
  const [globalForm] = Form.useForm<MailAuthGlobalFormValues>();
  const [domainForm] = Form.useForm<MailAuthDomainFormValues>();
  const [rotateForm] = Form.useForm<RotateDkimFormValues>();
  const {
    copyText,
    dnsRecords,
    domainPolicy,
    domains,
    globalPolicy,
    loadInitial,
    loading,
    localDomains,
    probeDns,
    probeResults,
    probing,
    rotateDkimSelector,
    rotating,
    saveDomainPolicy,
    saveGlobalPolicy,
    savingDomain,
    savingGlobal,
    selectDomain,
    selectedDomain,
    status,
  } = useMailAuth();

  useEffect(() => {
    void loadInitial();
  }, [loadInitial]);

  useEffect(() => {
    globalForm.setFieldsValue(globalFormValues(globalPolicy));
  }, [globalForm, globalPolicy]);

  useEffect(() => {
    domainForm.setFieldsValue(domainFormValues(domainPolicy));
  }, [domainForm, domainPolicy]);

  const domainOptions = useMemo(() => {
    const source = localDomains.length > 0 ? localDomains : domains;
    return source.map((item) => ({
      value: item.domain,
      label: item.localDomain ? `${item.domain}（本地域）` : item.domain,
    }));
  }, [domains, localDomains]);

  const openRotate = () => {
    rotateForm.setFieldsValue({
      selector: domainPolicy?.dkimSelector || 'sealmail',
      keySecretRef: '',
      keyPath: '',
      signedHeaders: domainPolicy?.dkimSignedHeaders || emptyDomainPolicy.dkimSignedHeaders,
    });
    setRotateOpen(true);
  };

  const handleRotate = async (values: RotateDkimFormValues) => {
    const ok = await rotateDkimSelector(values);
    if (ok) {
      setRotateOpen(false);
      rotateForm.resetFields();
    }
  };

  return (
    <PageShell className="mail-auth-page">
      <PageHeader
        title="邮件认证"
        description="管理 DKIM、SPF、DMARC 策略，生成 DNS TXT 记录并检查发布状态。"
        extra={status?.enabled ? <Tag color="green">已启用</Tag> : <Tag>未启用</Tag>}
        actions={(
          <Space wrap>
            <Button icon={<ReloadOutlined />} loading={loading} onClick={() => loadInitial()}>
              刷新
            </Button>
            <Button
              icon={<RetweetOutlined />}
              disabled={!selectedDomain}
              onClick={openRotate}
            >
              轮换 DKIM
            </Button>
          </Space>
        )}
      />

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card className="mail-auth-stat-card">
            <Statistic title="认证服务" value={status?.authservId || '-'} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card className="mail-auth-stat-card">
            <Statistic title="域名策略" value={status?.domainPolicyCount ?? 0} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card className="mail-auth-stat-card">
            <Statistic title="DKIM 启用域名" value={status?.dkimEnabledDomainCount ?? 0} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card className="mail-auth-stat-card">
            <Statistic title="失败动作" value={status?.failureDefaultAction || '-'} />
          </Card>
        </Col>
      </Row>

      <MailAuthGlobalPolicyForm
        form={globalForm}
        loading={savingGlobal}
        onSubmit={saveGlobalPolicy}
      />

      <Card className="mail-auth-section" title="域名选择">
        <Select
          showSearch
          className="mail-auth-domain-select"
          placeholder="选择域名"
          value={selectedDomain || undefined}
          options={domainOptions}
          loading={loading}
          onChange={(value) => selectDomain(value)}
        />
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={14}>
          <MailAuthDomainPolicyForm
            form={domainForm}
            loading={savingDomain}
            selectedDomain={selectedDomain}
            onSubmit={saveDomainPolicy}
          />
        </Col>
        <Col xs={24} xl={10}>
          <Card title="DNS TXT" className="mail-auth-section">
            <MailAuthDnsRecords records={dnsRecords} onCopyText={copyText} />
          </Card>
        </Col>
      </Row>

      <MailAuthProbeTable
        loading={probing}
        probes={probeResults.length > 0 ? probeResults : status?.recentDnsProbes || []}
        onProbe={probeDns}
      />

      <MailAuthRotateDkimModal
        form={rotateForm}
        loading={rotating}
        open={rotateOpen}
        onCancel={() => setRotateOpen(false)}
        onSubmit={handleRotate}
      />
    </PageShell>
  );
};

export default MailAuth;
