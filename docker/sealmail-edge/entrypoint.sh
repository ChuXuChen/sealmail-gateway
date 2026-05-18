#!/usr/bin/env sh
set -eu

CONFIG_PATH="${EDGE_CONFIG_PATH:-/tmp/sealmail-edge.properties}"
GENERATE_CONFIG="${EDGE_GENERATE_CONFIG:-true}"

if [ "$GENERATE_CONFIG" = "true" ]; then
  cat > "$CONFIG_PATH" <<EOF
edge.inbound.enabled=${EDGE_INBOUND_ENABLED:-true}
edge.inbound.bind-address=${EDGE_INBOUND_BIND_ADDRESS:-0.0.0.0}
edge.inbound.starttls-port=${EDGE_INBOUND_STARTTLS_PORT:-2525}
edge.inbound.implicit-tls-port=${EDGE_INBOUND_IMPLICIT_TLS_PORT:-2465}
edge.inbound.backlog=${EDGE_INBOUND_BACKLOG:-128}
edge.inbound.max-connections=${EDGE_INBOUND_MAX_CONNECTIONS:-512}

edge.outbound.enabled=${EDGE_OUTBOUND_ENABLED:-true}
edge.outbound.bind-address=${EDGE_OUTBOUND_BIND_ADDRESS:-0.0.0.0}
edge.outbound.port=${EDGE_OUTBOUND_PORT:-2526}
edge.outbound.backlog=${EDGE_OUTBOUND_BACKLOG:-128}
edge.outbound.max-connections=${EDGE_OUTBOUND_MAX_CONNECTIONS:-256}

edge.postfix.host=${EDGE_POSTFIX_HOST:-postfix}
edge.postfix.port=${EDGE_POSTFIX_PORT:-2530}

edge.connect-timeout-ms=${EDGE_CONNECT_TIMEOUT_MS:-10000}
edge.read-timeout-ms=${EDGE_READ_TIMEOUT_MS:-60000}
edge.max-message-size-bytes=${EDGE_MAX_MESSAGE_SIZE_BYTES:-52428800}
edge.max-line-length-bytes=${EDGE_MAX_LINE_LENGTH_BYTES:-16384}
edge.max-recipients=${EDGE_MAX_RECIPIENTS:-100}

edge.admin.enabled=${EDGE_ADMIN_ENABLED:-true}
edge.admin.bind-address=${EDGE_ADMIN_BIND_ADDRESS:-0.0.0.0}
edge.admin.port=${EDGE_ADMIN_PORT:-2727}

edge.tls.protocols=${EDGE_TLS_PROTOCOLS:-TLCPv1.1,TLCP,TLSv1.3}
edge.tls.cipher-suites=${EDGE_TLS_CIPHER_SUITES:-TLS_SM4_GCM_SM3,TLS_SM4_CCM_SM3}
edge.tls.key-store=${EDGE_TLS_KEY_STORE:-}
edge.tls.key-store-password=${EDGE_TLS_KEY_STORE_PASSWORD:-}
edge.tls.key-store-type=${EDGE_TLS_KEY_STORE_TYPE:-PKCS12}
edge.tls.trust-store=${EDGE_TLS_TRUST_STORE:-}
edge.tls.trust-store-password=${EDGE_TLS_TRUST_STORE_PASSWORD:-}
edge.tls.trust-store-type=${EDGE_TLS_TRUST_STORE_TYPE:-PKCS12}
edge.tls.trust-all=${EDGE_TLS_TRUST_ALL:-false}

edge.outbound.routes=${EDGE_OUTBOUND_ROUTES:-}
EOF
fi

exec java -cp "/app/sealmail-edge.jar:/app/lib/*" \
  com.sealmail.edge.SealMailEdgeApplication "$CONFIG_PATH"
