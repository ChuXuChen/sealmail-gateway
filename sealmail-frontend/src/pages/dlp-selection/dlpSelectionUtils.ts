import type { DlpPolicy, DlpRuleGroup, DlpSelection } from '../../types';

export interface DlpRuleGroupFormValues extends Partial<DlpRuleGroup> {
  name?: string;
}

export interface DlpPolicyFormValues extends Partial<DlpPolicy> {
  name?: string;
}

export interface DlpLegacySelectionFormValues extends Partial<DlpSelection> {
  patternMode?: 'ALL' | 'SELECTED';
}

export interface DlpPolicySimulationFormValues {
  policyId?: string;
  direction?: 'OUTBOUND' | 'INBOUND';
  sender?: string;
  recipients?: string;
  subject?: string;
  body?: string;
}

export const modeLabels: Record<string, string> = { MONITOR: '监控', ENFORCE: '执行' };
export const scopeLabels: Record<string, string> = { GLOBAL: '全局', SENDER_DOMAIN: '发件域', RECIPIENT_DOMAIN: '收件域' };
export const directionLabels: Record<string, string> = { OUTBOUND: '出站', INBOUND: '入站' };

export const joinScope = (values?: string[]) => {
  if (!values || values.length === 0) {
    return '全部';
  }
  return values.join('、');
};
