import type { DlpRule, DlpRuleType } from '../../types';

export interface DlpRuleFormValues extends Partial<DlpRule> {
  name?: string;
  type?: DlpRuleType;
}

export interface DlpDatasetFormValues {
  name?: string;
  description?: string;
  enabled?: boolean;
}

export interface EdmImportValues {
  datasetId: string;
  text?: string;
}

export interface FingerprintImportValues {
  libraryId: string;
  documentName?: string;
  text?: string;
}

export interface DlpTestValues {
  subject?: string;
  body?: string;
  sender?: string;
  recipients?: string;
}

export const actionOptions = [
  { value: 'WARN', label: '告警' },
  { value: 'MUST_ENCRYPT', label: '强制加密' },
  { value: 'QUARANTINE', label: '隔离' },
  { value: 'BLOCK', label: '阻断' },
];

export const typeOptions = [
  { value: 'PATTERN', label: '正则模式' },
  { value: 'KEYWORD', label: '关键词' },
  { value: 'BUILTIN', label: '内置模板' },
  { value: 'EDM', label: 'EDM 精确匹配' },
  { value: 'FINGERPRINT', label: '文档指纹' },
];

export const builtinOptions = [
  { value: 'CN_ID_CARD', label: '中国身份证' },
  { value: 'BANK_CARD', label: '银行卡号' },
  { value: 'API_KEY', label: 'API Key / Token' },
  { value: 'PRIVATE_KEY', label: '私钥块' },
  { value: 'PHONE_CN', label: '中国手机号' },
];

export const contentKindOptions = [
  { value: 'SUBJECT', label: '主题' },
  { value: 'HEADERS', label: '头部' },
  { value: 'BODY_TEXT', label: '纯文本正文' },
  { value: 'BODY_HTML', label: 'HTML 正文' },
  { value: 'ATTACHMENT_TEXT', label: '文本附件' },
  { value: 'ATTACHMENT_PDF', label: 'PDF' },
  { value: 'ATTACHMENT_ZIP_ENTRY', label: 'ZIP 文本' },
  { value: 'ATTACHMENT_METADATA', label: '附件元数据' },
];

export const maskingOptions = [
  { value: 'DEFAULT', label: '默认' },
  { value: 'PARTIAL', label: '部分遮盖' },
  { value: 'FULL', label: '完全遮盖' },
  { value: 'HASH_ONLY', label: '仅哈希' },
  { value: 'EMAIL', label: '邮箱' },
  { value: 'SECRET', label: '密钥' },
];

export const ruleTypeMeta: Record<string, { example: string; help: string; title: string }> = {
  PATTERN: {
    example: String.raw`\b\d{16,19}\b`,
    help: '适合格式稳定的敏感内容，保存前后端会校验正则表达式。',
    title: '正则模式',
  },
  KEYWORD: {
    example: '项目代号A\n报价底稿',
    help: '每行或逗号分隔一个关键词，用于简单文本命中。',
    title: '关键词',
  },
  BUILTIN: {
    example: '中国身份证 / 银行卡号 / API Key',
    help: '选择内置模板后无需手写模式，适合通用敏感类型快速启用。',
    title: '内置模板',
  },
  EDM: {
    example: '选择已导入的客户号、证件号、账号等哈希数据集',
    help: 'EDM 只保存规范化哈希，规则通过数据集 ID 进行精确匹配。',
    title: 'EDM 精确匹配',
  },
  FINGERPRINT: {
    example: '选择已生成片段指纹的合同、标书或设计文档库',
    help: '文档指纹适合发现邮件正文或附件中的敏感文档片段。',
    title: '文档指纹',
  },
};

export const splitImportValues = (text?: string) => (
  text
    ? text
      .split(/[\n,，]/)
      .map((item) => item.trim())
    : []
);
