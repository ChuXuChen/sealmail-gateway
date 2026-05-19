export interface QuarantineItem {
  id: string;
  messageId: string;
  subject: string;
  sender: string;
  recipients: string[];
  direction?: 'INBOUND' | 'OUTBOUND';
  remoteAddress?: string;
  reason: string;
  detail?: string;
  status: 'QUARANTINED' | 'RELEASING' | 'RELEASED' | 'REJECTED';
  hasRawContent?: boolean;
  canRelease?: boolean;
  releaseUnavailableReason?: string;
  quarantinedAt: string;
  resolvedAt?: string;
  resolvedBy?: string;
  resolutionComment?: string;
  dlpEventId?: string;
  falsePositive?: boolean;
  falsePositiveAt?: string;
  falsePositiveBy?: string;
  falsePositiveComment?: string;
}

export interface ExceptionMailItem {
  id: string;
  messageId: string;
  subject: string;
  sender: string;
  recipients: string[];
  direction?: 'INBOUND' | 'OUTBOUND';
  remoteAddress?: string;
  reason: string;
  detail?: string;
  blockedBy: string;
  blockComment?: string;
  createdAt: string;
}

export interface QuarantineStats {
  total: number;
  pending: number;
  releasing: number;
  released: number;
  rejected: number;
  byReason: Record<string, number>;
}

export interface ExceptionMailStats {
  total: number;
  byReason: Record<string, number>;
}
