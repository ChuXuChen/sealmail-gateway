// API Response Types
export interface ApiResponse<T = any> {
  success: boolean;
  code: number;
  message: string;
  data: T;
  timestamp: string;
  requestId: string;
}

export interface PageResponse<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}

// Auth Types
export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserContext;
}

export interface UserContext {
  userId: string;
  username: string;
  email: string;
  role: string;
  roles?: string[];
  managedDomains?: string[];
}

// Certificate Types
export interface Certificate {
  id: string;
  thumbprint: string;
  ownerEmail: string;
  alias?: string;
  issuer: string;
  subject: string;
  serialNumber: string;
  validFrom: string;
  validTo: string;
  keyUsages: string[];
  trusted: boolean;
  revoked: boolean;
  revocationReason?: string;
  pemData?: string;
  algorithm?: string;
  hasPrivateKey?: boolean;
  ca?: boolean;
  selfSigned?: boolean;
  certificateType?: string;
  parentCaId?: string;
  crlDistributionPointUrl?: string;
}

// Quarantine Types
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
  status: 'QUARANTINED' | 'RELEASED' | 'REJECTED';
  quarantinedAt: string;
  resolvedAt?: string;
  resolvedBy?: string;
  resolutionComment?: string;
}

export interface QuarantineStats {
  total: number;
  pending: number;
  released: number;
  rejected: number;
  byReason: Record<string, number>;
}
