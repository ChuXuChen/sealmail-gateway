#!/usr/bin/env sh
set -eu

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.gateway.yml}"
ENV_FILE="${ENV_FILE:-.env.gateway}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing $ENV_FILE. Copy .env.gateway.example to $ENV_FILE and edit it first." >&2
  exit 1
fi

required_keys="
LOCAL_DOMAIN
SEALMAIL_DB_PASSWORD
SEALMAIL_JWT_SECRET
SEALMAIL_KEYSTORE_PASSWORD
SEALMAIL_ADMIN_PASSWORD
"

for key in $required_keys; do
  value="$(awk -F= -v key="$key" '$1 == key {print substr($0, index($0, "=") + 1)}' "$ENV_FILE" | tail -n 1)"
  if [ -z "$value" ] || printf '%s' "$value" | grep -Eq '^(replace-with|change-me|<)'; then
    echo "Set a real value for $key in $ENV_FILE." >&2
    exit 1
  fi
done

remote_mode="$(awk -F= '$1 == "REMOTE_ROUTE_MODE" {print substr($0, index($0, "=") + 1)}' "$ENV_FILE" | tail -n 1)"
remote_mode="${remote_mode:-standard}"
site="$(awk -F= '$1 == "SEALMAIL_SITE" {print substr($0, index($0, "=") + 1)}' "$ENV_FILE" | tail -n 1)"
site="${site:-node}"
if [ "$remote_mode" = "standard" ]; then
  remote_host="$(awk -F= '$1 == "REMOTE_STANDARD_HOST" {print substr($0, index($0, "=") + 1)}' "$ENV_FILE" | tail -n 1)"
  if [ -z "$remote_host" ] || printf '%s' "$remote_host" | grep -Eq '^(<|replace-with)'; then
    echo "Set REMOTE_STANDARD_HOST in $ENV_FILE, or set REMOTE_ROUTE_MODE=none/gm." >&2
    exit 1
  fi
elif [ "$remote_mode" = "gm" ]; then
  edge_key_store="$(awk -F= '$1 == "EDGE_TLS_KEY_STORE" {print substr($0, index($0, "=") + 1)}' "$ENV_FILE" | tail -n 1)"
  edge_key_store_password="$(awk -F= '$1 == "EDGE_TLS_KEY_STORE_PASSWORD" {print substr($0, index($0, "=") + 1)}' "$ENV_FILE" | tail -n 1)"
  edge_trust_store="$(awk -F= '$1 == "EDGE_TLS_TRUST_STORE" {print substr($0, index($0, "=") + 1)}' "$ENV_FILE" | tail -n 1)"
  edge_trust_store_password="$(awk -F= '$1 == "EDGE_TLS_TRUST_STORE_PASSWORD" {print substr($0, index($0, "=") + 1)}' "$ENV_FILE" | tail -n 1)"
  edge_trust_all="$(awk -F= '$1 == "EDGE_TLS_TRUST_ALL" {print substr($0, index($0, "=") + 1)}' "$ENV_FILE" | tail -n 1)"
  edge_routes="$(awk -F= '$1 == "EDGE_OUTBOUND_ROUTES" {print substr($0, index($0, "=") + 1)}' "$ENV_FILE" | tail -n 1)"
  if [ -z "$edge_key_store" ] || [ -z "$edge_key_store_password" ] || [ -z "$edge_routes" ]; then
    echo "GM Edge mode requires EDGE_TLS_KEY_STORE, EDGE_TLS_KEY_STORE_PASSWORD and EDGE_OUTBOUND_ROUTES." >&2
    echo "Generate local material with: ops/gm-edge-keystore.sh init --site $site --dns <mx-host> --ip <public-ip>" >&2
    exit 1
  fi
  if [ "$edge_trust_all" != "true" ] && { [ -z "$edge_trust_store" ] || [ -z "$edge_trust_store_password" ]; }; then
    echo "GM Edge mode requires EDGE_TLS_TRUST_STORE and EDGE_TLS_TRUST_STORE_PASSWORD, unless EDGE_TLS_TRUST_ALL=true is used for a lab." >&2
    echo "Import the peer certificate with: ops/gm-edge-keystore.sh trust --site $site --peer <peer-site> --cert <peer-edge.crt>" >&2
    exit 1
  fi
fi

mkdir -p "runtime/$site/edge-secrets"
mkdir -p "runtime/$site/standard-tls"

exec docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build
