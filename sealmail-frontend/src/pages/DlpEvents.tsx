import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Descriptions, Drawer, Input, InputNumber, Select, Space, Table, Tabs, Tag, Typography, message } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import type { TableColumnsType } from 'antd';
import { dlpApi } from '../api/client';
import { getApiErrorMessage } from '../api/errors';
import type { DlpEvent, DlpEvidence, DlpUbaSenderRisk } from '../types';
import { DataTable, DlpActionTag, DlpRiskTag, FilterBar, PageHeader, PageShell, StatusSummary, formatDateTime } from '../components/Page';

const { Text } = Typography;

const DlpEvents: React.FC = () => {
  const [data, setData] = useState<DlpEvent[]>([]);
  const [evidence, setEvidence] = useState<DlpEvidence[]>([]);
  const [ubaRisks, setUbaRisks] = useState<DlpUbaSenderRisk[]>([]);
  const [loading, setLoading] = useState(false);
  const [riskLoading, setRiskLoading] = useState(false);
  const [pagination, setPagination] = useState({ page: 1, size: 20, total: 0 });
  const [action, setAction] = useState<string | undefined>();
  const [minSeverity, setMinSeverity] = useState<number | undefined>();
  const [rule, setRule] = useState<string | undefined>();
  const [domain, setDomain] = useState<string | undefined>();
  const [selected, setSelected] = useState<DlpEvent | null>(null);
  const [activeTab, setActiveTab] = useState('events');

  const riskSummary = useMemo(() => {
    const high = ubaRisks.filter((risk) => risk.riskLevel === 'HIGH').length;
    const medium = ubaRisks.filter((risk) => risk.riskLevel === 'MEDIUM').length;
    const dlpHits = ubaRisks.reduce((sum, risk) => sum + risk.dlpHitCount, 0);
    const externalDomains = ubaRisks.reduce((sum, risk) => sum + risk.externalDomainCount, 0);
    return { dlpHits, externalDomains, high, medium };
  }, [ubaRisks]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const page = await dlpApi.listEvents({
        page: pagination.page,
        size: pagination.size,
        action,
        minSeverity,
        rule,
        domain,
      });
      setData(page.items);
      setPagination((prev) => ({ ...prev, total: page.total }));
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载 DLP 命中事件失败'));
    } finally {
      setLoading(false);
    }
  }, [action, domain, minSeverity, pagination.page, pagination.size, rule]);

  const loadRisks = useCallback(async () => {
    setRiskLoading(true);
    try {
      const risks = await dlpApi.listUbaSenderRisks({ limit: 100 });
      setUbaRisks(risks);
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载发件人风险失败'));
    } finally {
      setRiskLoading(false);
    }
  }, []);

  const refreshAll = useCallback(() => {
    void Promise.all([loadData(), loadRisks()]);
  }, [loadData, loadRisks]);

  useEffect(() => {
    void Promise.resolve().then(loadData);
  }, [loadData]);

  useEffect(() => {
    void Promise.resolve().then(loadRisks);
  }, [loadRisks]);

  const openDetail = async (record: DlpEvent) => {
    setSelected(record);
    try {
      const eventEvidence = await dlpApi.eventEvidence(record.id);
      setEvidence(eventEvidence);
    } catch (error) {
      message.error(getApiErrorMessage(error, '加载证据失败'));
    }
  };

  const clearFilters = () => {
    setAction(undefined);
    setMinSeverity(undefined);
    setRule(undefined);
    setDomain(undefined);
    setPagination((prev) => ({ ...prev, page: 1 }));
  };

  const filterEventsBySenderDomain = useCallback((senderEmail: string) => {
    const atIndex = senderEmail.lastIndexOf('@');
    if (atIndex >= 0 && atIndex < senderEmail.length - 1) {
      setDomain(senderEmail.slice(atIndex + 1));
      setPagination((prev) => ({ ...prev, page: 1 }));
      setActiveTab('events');
    }
  }, []);

  const columns: TableColumnsType<DlpEvent> = [
    { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 170, render: (value: string) => formatDateTime(value) },
    { title: '动作', dataIndex: 'action', key: 'action', width: 110, render: (value: string) => <DlpActionTag action={value} /> },
    { title: '级别', dataIndex: 'maxSeverity', key: 'maxSeverity', width: 70 },
    { title: '命中', dataIndex: 'matchCount', key: 'matchCount', width: 70 },
    { title: '发件人', dataIndex: 'senderEmail', key: 'senderEmail', width: 190, ellipsis: true },
    { title: '主题', dataIndex: 'subject', key: 'subject', width: 240, ellipsis: true },
    { title: '模式', dataIndex: 'monitorMode', key: 'monitorMode', width: 90, render: (value: boolean) => value ? <Tag color="blue">监控</Tag> : <Tag color="red">执行</Tag> },
    { title: 'UBA', dataIndex: 'ubaRiskLevel', key: 'ubaRiskLevel', width: 80, render: (value?: string) => <DlpRiskTag risk={value} /> },
    { title: '误报', dataIndex: 'falsePositive', key: 'falsePositive', width: 80, render: (value: boolean) => value ? <Tag color="green">是</Tag> : '-' },
    { title: '操作', key: 'actions', width: 90, fixed: 'right', render: (_: unknown, record) => <Button type="link" size="small" onClick={() => openDetail(record)}>查看</Button> },
  ];

  const riskColumns: TableColumnsType<DlpUbaSenderRisk> = [
    {
      title: '发件人',
      dataIndex: 'senderEmail',
      key: 'senderEmail',
      width: 240,
      ellipsis: true,
      render: (value: string) => <Button type="link" size="small" onClick={() => filterEventsBySenderDomain(value)}>{value}</Button>,
    },
    { title: '风险', dataIndex: 'riskLevel', key: 'riskLevel', width: 90, render: (value: string) => <DlpRiskTag risk={value} /> },
    { title: '总量', dataIndex: 'totalMessages', key: 'totalMessages', width: 80 },
    { title: '外发', dataIndex: 'outboundMessages', key: 'outboundMessages', width: 90 },
    { title: '外部域', dataIndex: 'externalDomainCount', key: 'externalDomainCount', width: 90 },
    { title: 'DLP 命中', dataIndex: 'dlpHitCount', key: 'dlpHitCount', width: 100 },
    { title: '高风险', dataIndex: 'highRiskCount', key: 'highRiskCount', width: 90 },
    {
      title: '原因',
      dataIndex: 'lastReasons',
      key: 'lastReasons',
      ellipsis: true,
      render: (values: string[]) => values.length > 0 ? (
        <Space size={[0, 4]} wrap>
          {values.slice(0, 3).map((value) => <Tag key={value}>{value}</Tag>)}
          {values.length > 3 ? <Text type="secondary">+{values.length - 3}</Text> : null}
        </Space>
      ) : '-',
    },
    { title: '最近活动', dataIndex: 'lastSeenAt', key: 'lastSeenAt', width: 170, render: formatDateTime },
  ];

  return (
    <PageShell>
      <PageHeader
        title="DLP 命中事件"
        description="查看 DLP 命中记录、策略来源、抽取 warning 和脱敏证据。"
        actions={(
          <Button icon={<ReloadOutlined />} loading={loading || riskLoading} onClick={refreshAll}>
            刷新
          </Button>
        )}
      />

      <Tabs
        className="config-tabs"
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'events',
            label: '命中事件',
            children: (
              <div className="config-section-stack">
                <FilterBar onRefresh={loadData} onReset={clearFilters} refreshLoading={loading}>
                  <Select className="filter-control-sm" allowClear placeholder="动作" value={action} onChange={(value) => { setAction(value); setPagination((prev) => ({ ...prev, page: 1 })); }} options={[
                    { value: 'WARN', label: '告警' },
                    { value: 'MUST_ENCRYPT', label: '强制加密' },
                    { value: 'QUARANTINE', label: '隔离' },
                    { value: 'BLOCK', label: '阻断' },
                  ]} />
                  <InputNumber className="filter-control-sm" min={1} max={10} placeholder="最低级别" value={minSeverity} onChange={(value) => { setMinSeverity(value || undefined); setPagination((prev) => ({ ...prev, page: 1 })); }} />
                  <Input className="filter-control-md" allowClear placeholder="规则" value={rule} onChange={(event) => setRule(event.target.value || undefined)} />
                  <Input className="filter-control-md" allowClear placeholder="域名" value={domain} onChange={(event) => setDomain(event.target.value || undefined)} />
                  <Button icon={<ReloadOutlined />} onClick={loadData}>查询</Button>
                </FilterBar>

                <DataTable<DlpEvent>
                  rowKey="id"
                  loading={loading}
                  dataSource={data}
                  columns={columns}
                  scroll={{ x: 1220 }}
                  pagination={{
                    current: pagination.page,
                    pageSize: pagination.size,
                    total: pagination.total,
                    onChange: (page, size) => setPagination({ page, size, total: pagination.total }),
                  }}
                />
              </div>
            ),
          },
          {
            key: 'sender-risks',
            label: '发件人风险',
            children: (
              <div className="config-section-stack">
                <StatusSummary
                  items={[
                    { key: 'high', label: '高风险发件人', value: riskSummary.high, description: '需优先复核', tone: riskSummary.high > 0 ? 'danger' : 'success' },
                    { key: 'medium', label: '中风险发件人', value: riskSummary.medium, description: '持续观察', tone: riskSummary.medium > 0 ? 'warning' : 'success' },
                    { key: 'hits', label: 'DLP 命中', value: riskSummary.dlpHits, description: '发件人累计命中' },
                    { key: 'domains', label: '外部域覆盖', value: riskSummary.externalDomains, description: '发件人累计外部域' },
                  ]}
                />
                <DataTable<DlpUbaSenderRisk>
                  rowKey="senderEmail"
                  loading={riskLoading}
                  dataSource={ubaRisks}
                  columns={riskColumns}
                  scroll={{ x: 1180 }}
                  pagination={{ pageSize: 20, total: ubaRisks.length }}
                />
              </div>
            ),
          },
        ]}
      />

      <Drawer title="DLP 事件证据" width={760} open={!!selected} onClose={() => { setSelected(null); setEvidence([]); }}>
        {selected && (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Descriptions column={1} bordered size="small">
              <Descriptions.Item label="事件 ID">{selected.id}</Descriptions.Item>
              <Descriptions.Item label="动作"><DlpActionTag action={selected.action} /></Descriptions.Item>
              <Descriptions.Item label="策略">{selected.policyIds.join(', ') || '-'}</Descriptions.Item>
              <Descriptions.Item label="规则组">{selected.ruleGroupIds.join(', ') || '-'}</Descriptions.Item>
              <Descriptions.Item label="抽取 Warning">{selected.extractionWarnings.join('; ') || '-'}</Descriptions.Item>
              <Descriptions.Item label="UBA 风险"><DlpRiskTag risk={selected.ubaRiskLevel} /></Descriptions.Item>
              <Descriptions.Item label="UBA 升级">{selected.ubaActionUpgraded ? '是' : '否'}</Descriptions.Item>
              <Descriptions.Item label="UBA 原因">{selected.ubaRiskReasons?.join('; ') || '-'}</Descriptions.Item>
              <Descriptions.Item label="关联隔离">{selected.quarantineId || '-'}</Descriptions.Item>
            </Descriptions>
            <Table<DlpEvidence>
              rowKey="id"
              size="small"
              dataSource={evidence}
              pagination={false}
              columns={[
                { title: '规则', dataIndex: 'ruleName', key: 'ruleName', width: 150 },
                { title: '位置', dataIndex: 'partKind', key: 'partKind', width: 130, render: (value, record) => record.fileName ? `${value} / ${record.fileName}` : value },
                { title: '证据', dataIndex: 'maskedSnippet', key: 'maskedSnippet', ellipsis: true },
                { title: '哈希', dataIndex: 'matchHash', key: 'matchHash', width: 120, ellipsis: true },
              ]}
            />
          </Space>
        )}
      </Drawer>
    </PageShell>
  );
};

export default DlpEvents;
