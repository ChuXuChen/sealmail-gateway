export interface AuditLog {
  id: string;
  type: string;
  typeDisplayName: string;
  userId?: string;
  username?: string;
  ipAddress?: string;
  resourceType?: string;
  resourceId?: string;
  action?: string;
  detail?: string;
  success: boolean;
  errorMessage?: string;
  occurredAt: string;
}
