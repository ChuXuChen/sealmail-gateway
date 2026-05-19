export interface SystemSettings {
  runtime: {
    applicationName: string;
    activeProfiles: string[];
    onlineEditingSupported: boolean;
    configSource: string;
    generatedAt: string;
  };
  smtpServer: {
    bindAddress: string;
    port: number;
    maxConnections: number;
    maxMessageSizeBytes: number;
  };
  delivery: {
    mode: 'POSTFIX' | 'DIRECT_RELAY' | string;
    postfix: {
      enabled: boolean;
      host: string;
      afterFilterPort: number;
      outboundPort: number;
      timeoutMs: number;
      envelopeFrom?: string;
    };
    directRelay: {
      host: string;
      port: number;
      timeoutMs: number;
      usernameConfigured: boolean;
      passwordConfigured: boolean;
    };
  };
  gmEdge: {
    enabled: boolean;
    inbound: {
      enabled: boolean;
      bindAddress: string;
      startTlsPort: number;
      implicitTlsPort: number;
      backlog: number;
      maxConnections: number;
    };
    outbound: {
      enabled: boolean;
      bindAddress: string;
      smartHostPort: number;
      backlog: number;
      maxConnections: number;
    };
    postfix: {
      host: string;
      port: number;
    };
    tls: {
      protocols: string[];
      cipherSuites: string[];
      keyStorePath?: string;
      keyStoreConfigured: boolean;
      keyStorePasswordConfigured: boolean;
      keyStorePasswordSecretRef?: string;
      keyStoreType: string;
      trustStorePath?: string;
      trustStoreConfigured: boolean;
      trustStorePasswordConfigured: boolean;
      trustStorePasswordSecretRef?: string;
      trustStoreType: string;
      trustAll: boolean;
    };
    limits: {
      connectTimeoutMs: number;
      readTimeoutMs: number;
      maxMessageSizeBytes: number;
      maxLineLengthBytes: number;
      maxRecipients: number;
    };
    routes: {
      domainPattern: string;
      targetHost: string;
      targetPort: number;
      security: string;
    }[];
  };
  smimeSuitePolicy: SmimeSuitePolicy;
  quarantinePolicy: {
    maxRetentionDays: number;
    notificationEnabled: boolean;
    releaseRequiresEncryption: boolean;
  };
  certificateValidation: {
    crlEnabled: boolean;
    ocspEnabled: boolean;
    ocspTimeoutMs: number;
  };
  internalCa: {
    crlBaseUrl: string;
    defaultRootValidityDays: number;
    defaultIntermediateValidityDays: number;
    defaultEndEntityValidityDays: number;
  };
  cryptoCapabilities: {
    category: string;
    algorithms: string[];
  }[];
}

export interface SmimeSuiteOption {
  id: string;
  displayName: string;
  profile: 'STANDARD' | 'GM' | string;
}

export interface SmimeSuitePolicy {
  defaultStandardSuite: string;
  defaultGmSuite: string;
  standardSuites: SmimeSuiteOption[];
  gmSuites: SmimeSuiteOption[];
  updatedAt?: string;
}

export type SmimeSuitePolicyRequest = Pick<SmimeSuitePolicy, 'defaultStandardSuite' | 'defaultGmSuite'>;

export interface RelayPolicy {
  enabled: boolean;
  host: string;
  port: number;
  username?: string;
  passwordConfigured: boolean;
  passwordSecretRef?: string;
  timeoutMs: number;
  envelopeFrom?: string;
  allowUnconfiguredExternalRecipientDomains: boolean;
  updatedAt?: string;
}

export interface QuarantinePolicy {
  maxRetentionDays: number;
  notificationEnabled: boolean;
  releaseRequiresEncryption: boolean;
  updatedAt?: string;
}

export type RelayPolicyRequest = Partial<RelayPolicy> & {
  clearPasswordSecretRef?: boolean;
};

export type QuarantinePolicyRequest = Partial<QuarantinePolicy>;

export interface GmEdgePolicy {
  enabled: boolean;
  inbound: {
    enabled: boolean;
    bindAddress: string;
    startTlsPort: number;
    implicitTlsPort: number;
    backlog: number;
    maxConnections: number;
  };
  outbound: {
    enabled: boolean;
    bindAddress: string;
    smartHostPort: number;
    backlog: number;
    maxConnections: number;
  };
  postfix: {
    host: string;
    port: number;
  };
  tls: {
    protocols: string[];
    cipherSuites: string[];
    keyStorePath?: string;
    keyStoreConfigured: boolean;
    keyStorePasswordConfigured: boolean;
    keyStorePasswordSecretRef?: string;
    keyStoreType: string;
    trustStorePath?: string;
    trustStoreConfigured: boolean;
    trustStorePasswordConfigured: boolean;
    trustStorePasswordSecretRef?: string;
    trustStoreType: string;
    trustAll: boolean;
  };
  limits: {
    connectTimeoutMs: number;
    readTimeoutMs: number;
    maxMessageSizeBytes: number;
    maxLineLengthBytes: number;
    maxRecipients: number;
  };
  routes: {
    domainPattern: string;
    targetHost: string;
    targetPort: number;
    security: 'STARTTLS' | 'IMPLICIT_TLS' | string;
  }[];
  updatedAt?: string;
}

export interface GmEdgePolicyRequest {
  enabled?: boolean;
  inbound?: Partial<GmEdgePolicy['inbound']>;
  outbound?: Partial<GmEdgePolicy['outbound']>;
  postfix?: Partial<GmEdgePolicy['postfix']>;
  tls?: Partial<GmEdgePolicy['tls']> & {
    clearKeyStorePasswordSecretRef?: boolean;
    clearTrustStorePasswordSecretRef?: boolean;
  };
  limits?: Partial<GmEdgePolicy['limits']>;
  routes?: Partial<GmEdgePolicy['routes'][number]>[];
}

export interface SendTestMailRequest {
  from: string;
  to: string[];
  subject: string;
  content: string;
}

export type SendProtectedMailRequest = SendTestMailRequest;
