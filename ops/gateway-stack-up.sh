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
if [ "$remote_mode" = "standard" ]; then
  remote_host="$(awk -F= '$1 == "REMOTE_STANDARD_HOST" {print substr($0, index($0, "=") + 1)}' "$ENV_FILE" | tail -n 1)"
  if [ -z "$remote_host" ] || printf '%s' "$remote_host" | grep -Eq '^(<|replace-with)'; then
    echo "Set REMOTE_STANDARD_HOST in $ENV_FILE, or set REMOTE_ROUTE_MODE=none/gm." >&2
    exit 1
  fi
fi

site="$(awk -F= '$1 == "SEALMAIL_SITE" {print substr($0, index($0, "=") + 1)}' "$ENV_FILE" | tail -n 1)"
site="${site:-node}"
mkdir -p "runtime/$site/edge-secrets"

exec docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build
