#!/usr/bin/env sh
set -eu

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.gateway.yml}"
ENV_FILE="${ENV_FILE:-.env.gateway}"
EXAMPLE_ENV_FILE="${EXAMPLE_ENV_FILE:-.env.gateway.example}"
BUILD_IMAGE=true
DRY_RUN=false
SKIP_PORT_CHECK=false

OPT_SITE=""
OPT_LOCAL_DOMAIN=""
OPT_REMOTE_DOMAIN=""
OPT_REMOTE_HOST=""
OPT_ROUTE_MODE=""
OPT_DEPLOYMENT_MODE=""
OPT_HTTPS_ENABLED=""
OPT_PUBLIC_HOST=""

usage() {
  cat <<'EOF'
Usage: ops/gateway-stack-up.sh [options]

Create or update .env.gateway, prepare runtime directories, validate the host,
and run docker compose up -d --build for the gateway stack.

Options:
  --env-file PATH          Environment file to use (default: .env.gateway)
  --compose-file PATH      Compose file to use (default: docker-compose.gateway.yml)
  --site NAME              Node name, for example alpha or beta
  --local-domain DOMAIN    Domain handled by this node
  --remote-domain DOMAIN   Peer node domain
  --remote-host HOST       Peer SMTP host for REMOTE_ROUTE_MODE=standard
  --route-mode MODE        standard, gm, or none
  --debug                  Enable debug profile and Mailpit (default)
  --production             Disable debug profile and Mailpit
  --https-host HOST        Enable Caddy HTTPS reverse proxy for HOST
  --no-https               Disable the bundled HTTPS reverse proxy
  --no-build               Run compose up without --build
  --skip-port-check        Skip host port preflight
  --dry-run                Generate, validate, and print the compose command
  -h, --help               Show this help
EOF
}

die() {
  echo "ERROR: $*" >&2
  exit 1
}

log() {
  echo "==> $*" >&2
}

warn() {
  echo "WARN: $*" >&2
}

need_arg() {
  [ "$#" -ge 2 ] || die "Missing value for $1"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --env-file)
      need_arg "$@"
      ENV_FILE="$2"
      shift 2
      ;;
    --compose-file)
      need_arg "$@"
      COMPOSE_FILE="$2"
      shift 2
      ;;
    --site)
      need_arg "$@"
      OPT_SITE="$2"
      shift 2
      ;;
    --local-domain)
      need_arg "$@"
      OPT_LOCAL_DOMAIN="$2"
      shift 2
      ;;
    --remote-domain)
      need_arg "$@"
      OPT_REMOTE_DOMAIN="$2"
      shift 2
      ;;
    --remote-host)
      need_arg "$@"
      OPT_REMOTE_HOST="$2"
      shift 2
      ;;
    --route-mode)
      need_arg "$@"
      OPT_ROUTE_MODE="$2"
      shift 2
      ;;
    --debug)
      OPT_DEPLOYMENT_MODE="debug"
      shift
      ;;
    --production|--prod)
      OPT_DEPLOYMENT_MODE="production"
      shift
      ;;
    --https-host)
      need_arg "$@"
      OPT_HTTPS_ENABLED="true"
      OPT_PUBLIC_HOST="$2"
      shift 2
      ;;
    --no-https)
      OPT_HTTPS_ENABLED="false"
      shift
      ;;
    --no-build)
      BUILD_IMAGE=false
      shift
      ;;
    --skip-port-check)
      SKIP_PORT_CHECK=true
      shift
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "Unknown option: $1"
      ;;
  esac
done

env_value() {
  key="$1"
  default="${2-}"
  if [ ! -f "$ENV_FILE" ]; then
    printf '%s' "$default"
    return
  fi
  value="$(awk -v key="$key" '
    /^[[:space:]]*#/ || /^[[:space:]]*$/ || index($0, "=") == 0 { next }
    {
      line = $0
      sub(/^[[:space:]]*/, "", line)
      split(line, parts, "=")
      name = parts[1]
      sub(/[[:space:]]*$/, "", name)
      if (name == key) {
        value = substr(line, index(line, "=") + 1)
        sub(/\r$/, "", value)
        print value
      }
    }
  ' "$ENV_FILE" | tail -n 1)"
  if [ -n "$value" ]; then
    printf '%s' "$value"
  else
    printf '%s' "$default"
  fi
}

set_env_value() {
  key="$1"
  value="$2"
  tmp="${ENV_FILE}.tmp.$$"
  awk -v key="$key" -v value="$value" '
    BEGIN { replaced = 0 }
    /^[[:space:]]*#/ || index($0, "=") == 0 {
      print
      next
    }
    {
      line = $0
      sub(/^[[:space:]]*/, "", line)
      split(line, parts, "=")
      name = parts[1]
      sub(/[[:space:]]*$/, "", name)
      if (name == key) {
        print key "=" value
        replaced = 1
        next
      }
      print
    }
    END {
      if (replaced == 0) {
        print key "=" value
      }
    }
  ' "$ENV_FILE" > "$tmp" || {
    rm -f "$tmp"
    die "Failed to update $ENV_FILE"
  }
  mv "$tmp" "$ENV_FILE"
}

is_placeholder() {
  value="$1"
  case "$value" in
    ""|replace-with*|change-me*|CHANGE_ME*|REPLACE*|"<"*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

random_hex() {
  bytes="$1"
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex "$bytes"
  elif [ -r /dev/urandom ] && command -v od >/dev/null 2>&1; then
    od -An -N "$bytes" -tx1 /dev/urandom | tr -d ' \n'
  else
    die "openssl or /dev/urandom is required to generate secrets"
  fi
}

random_raw_content_key() {
  if command -v openssl >/dev/null 2>&1; then
    printf 'base64:%s' "$(openssl rand -base64 32 | tr -d '\n')"
  else
    random_hex 32
  fi
}

ensure_secret() {
  key="$1"
  kind="$2"
  current="$(env_value "$key")"
  if ! is_placeholder "$current"; then
    return
  fi
  case "$kind" in
    raw-content)
      generated="$(random_raw_content_key)"
      ;;
    admin)
      generated="$(random_hex 18)"
      ;;
    *)
      generated="$(random_hex 32)"
      ;;
  esac
  set_env_value "$key" "$generated"
  log "Filled $key in $ENV_FILE"
}

interactive_enabled() {
  [ -t 0 ] && [ "${SEALMAIL_NONINTERACTIVE:-false}" != "true" ]
}

prompt_value() {
  key="$1"
  label="$2"
  default="$3"
  required="$4"
  current="$(env_value "$key")"
  if ! is_placeholder "$current"; then
    return
  fi
  if interactive_enabled; then
    while :; do
      if [ -n "$default" ]; then
        printf '%s [%s]: ' "$label" "$default" >&2
      else
        printf '%s: ' "$label" >&2
      fi
      IFS= read -r answer || answer=""
      answer="${answer:-$default}"
      if [ -n "$answer" ] || [ "$required" != "true" ]; then
        set_env_value "$key" "$answer"
        return
      fi
      echo "A value is required." >&2
    done
  elif [ -n "$default" ]; then
    set_env_value "$key" "$default"
  fi
}

derive_postfix_hostname() {
  site="$1"
  local_domain="$2"
  case "$local_domain" in
    "$site".*)
      base_domain="${local_domain#"$site".}"
      printf 'mx-%s.%s' "$site" "$base_domain"
      ;;
    *)
      printf 'mx.%s' "$local_domain"
      ;;
  esac
}

normalize_deployment_mode() {
  mode="$1"
  case "$mode" in
    ""|debug|dev)
      printf 'debug'
      ;;
    production|prod)
      printf 'production'
      ;;
    *)
      die "Unsupported SEALMAIL_DEPLOYMENT_MODE: $mode"
      ;;
  esac
}

bool_enabled() {
  value="$1"
  case "$value" in
    true|TRUE|1|yes|YES|on|ON)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

profile_contains() {
  profiles="$1"
  profile="$2"
  case ",$profiles," in
    *",$profile,"*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

append_profile() {
  profiles="$1"
  profile="$2"
  if [ -z "$profiles" ]; then
    printf '%s' "$profile"
  elif profile_contains "$profiles" "$profile"; then
    printf '%s' "$profiles"
  else
    printf '%s,%s' "$profiles" "$profile"
  fi
}

origin_from_host() {
  host="$1"
  case "$host" in
    http://*|https://*)
      printf '%s' "${host%/}"
      ;;
    *)
      printf 'https://%s' "$host"
      ;;
  esac
}

local_tunnel_cors_origins() {
  frontend_port="$1"
  origins="http://localhost:$frontend_port,http://127.0.0.1:$frontend_port"
  if [ "$frontend_port" != "8088" ]; then
    origins="$origins,http://localhost:8088,http://127.0.0.1:8088"
  fi
  if [ "$frontend_port" != "8089" ]; then
    origins="$origins,http://localhost:8089,http://127.0.0.1:8089"
  fi
  printf '%s' "$origins"
}

crl_base_url_from_host() {
  printf '%s/api/v1/crl/' "$(origin_from_host "$1")"
}

validate_public_host() {
  host="$1"
  without_scheme="$host"
  case "$without_scheme" in
    http://*)
      die "SEALMAIL_PUBLIC_HOST must not use http:// when SEALMAIL_HTTPS_ENABLED=true"
      ;;
  esac
  case "$without_scheme" in
    https://*)
      without_scheme="${without_scheme#https://}"
      ;;
  esac
  without_scheme="${without_scheme%/}"
  case "$without_scheme" in
    ""|*/*|*"://"*|*" "*|*"	"|*:*)
      die "SEALMAIL_PUBLIC_HOST must be a hostname or https://hostname origin without a URL path or port"
      ;;
  esac
}

sync_spring_profiles() {
  deployment_mode="$1"
  profiles="$(env_value SPRING_PROFILES_ACTIVE postgres | tr -d '[:space:]')"
  [ -n "$profiles" ] || profiles="postgres"
  profiles="$(append_profile "$profiles" postgres)"
  if [ "$deployment_mode" = "production" ]; then
    profiles="$(append_profile "$profiles" prod)"
  elif [ "$profiles" = "postgres,prod" ] && [ "$OPT_DEPLOYMENT_MODE" = "debug" ]; then
    profiles="postgres"
  fi
  current="$(env_value SPRING_PROFILES_ACTIVE)"
  if [ "$current" != "$profiles" ]; then
    set_env_value SPRING_PROFILES_ACTIVE "$profiles"
  fi
}

generate_or_update_env() {
  generated_env=false
  if [ ! -f "$ENV_FILE" ]; then
    [ -f "$EXAMPLE_ENV_FILE" ] || die "Missing $EXAMPLE_ENV_FILE"
    log "Creating $ENV_FILE from $EXAMPLE_ENV_FILE"
    old_umask="$(umask)"
    umask 077
    cp "$EXAMPLE_ENV_FILE" "$ENV_FILE"
    umask "$old_umask"
    generated_env=true
  fi

  [ -w "$ENV_FILE" ] || die "$ENV_FILE is not writable"

  [ -n "$OPT_SITE" ] && set_env_value SEALMAIL_SITE "$OPT_SITE"
  [ -n "$OPT_LOCAL_DOMAIN" ] && set_env_value LOCAL_DOMAIN "$OPT_LOCAL_DOMAIN"
  [ -n "$OPT_REMOTE_DOMAIN" ] && set_env_value REMOTE_DOMAIN "$OPT_REMOTE_DOMAIN"
  [ -n "$OPT_REMOTE_HOST" ] && set_env_value REMOTE_STANDARD_HOST "$OPT_REMOTE_HOST"
  [ -n "$OPT_ROUTE_MODE" ] && set_env_value REMOTE_ROUTE_MODE "$OPT_ROUTE_MODE"
  [ -n "$OPT_DEPLOYMENT_MODE" ] && set_env_value SEALMAIL_DEPLOYMENT_MODE "$OPT_DEPLOYMENT_MODE"
  [ -n "$OPT_HTTPS_ENABLED" ] && set_env_value SEALMAIL_HTTPS_ENABLED "$OPT_HTTPS_ENABLED"
  [ -n "$OPT_PUBLIC_HOST" ] && set_env_value SEALMAIL_PUBLIC_HOST "$OPT_PUBLIC_HOST"

  prompt_value SEALMAIL_SITE "Site name" "$(env_value SEALMAIL_SITE alpha)" true
  site="$(env_value SEALMAIL_SITE alpha)"
  prompt_value LOCAL_DOMAIN "Local domain" "$(env_value LOCAL_DOMAIN "$site.example.invalid")" true
  local_domain="$(env_value LOCAL_DOMAIN)"
  prompt_value REMOTE_DOMAIN "Remote peer domain" "$(env_value REMOTE_DOMAIN)" false
  prompt_value REMOTE_ROUTE_MODE "Remote route mode (standard/gm/none)" "$(env_value REMOTE_ROUTE_MODE standard)" true

  if [ "$generated_env" = "true" ]; then
    set_env_value POSTFIX_HOSTNAME "$(derive_postfix_hostname "$site" "$local_domain")"
    set_env_value SEALMAIL_ADMIN_USER_ID "admin-$site"
    set_env_value SEALMAIL_ADMIN_EMAIL "admin@$local_domain"
  fi
  frontend_port="$(env_value SEALMAIL_FRONTEND_HTTP_PORT 8088)"
  cors="$(env_value SEALMAIL_CORS_ALLOWED_ORIGINS)"
  if is_placeholder "$cors" || [ "$cors" = "http://localhost:8088,http://127.0.0.1:8088" ]; then
    set_env_value SEALMAIL_CORS_ALLOWED_ORIGINS "$(local_tunnel_cors_origins "$frontend_port")"
  fi

  route_mode="$(env_value REMOTE_ROUTE_MODE standard)"
  if [ "$route_mode" = "standard" ] && [ -n "$(env_value REMOTE_DOMAIN)" ]; then
    prompt_value REMOTE_STANDARD_HOST "Peer SMTP host/IP for standard mode" "$(env_value REMOTE_STANDARD_HOST)" true
  fi

  prompt_value SEALMAIL_DEPLOYMENT_MODE "Deployment mode (debug/production)" "$(env_value SEALMAIL_DEPLOYMENT_MODE debug)" true
  deployment_mode="$(normalize_deployment_mode "$(env_value SEALMAIL_DEPLOYMENT_MODE debug)")"
  set_env_value SEALMAIL_DEPLOYMENT_MODE "$deployment_mode"
  sync_spring_profiles "$deployment_mode"
  if [ "$deployment_mode" = "production" ] && [ "$(env_value LOCAL_DELIVERY_MODE mailpit)" = "mailpit" ]; then
    set_env_value LOCAL_DELIVERY_MODE none
  fi
  if bool_enabled "$(env_value SEALMAIL_HTTPS_ENABLED false)"; then
    public_host="$(env_value SEALMAIL_PUBLIC_HOST)"
    if ! is_placeholder "$public_host"; then
      validate_public_host "$public_host"
      normalized_public_host="${public_host%/}"
      if [ "$normalized_public_host" != "$public_host" ]; then
        set_env_value SEALMAIL_PUBLIC_HOST "$normalized_public_host"
        public_host="$normalized_public_host"
      fi
      cors="$(env_value SEALMAIL_CORS_ALLOWED_ORIGINS)"
      if is_placeholder "$cors" \
        || [ "$cors" = "http://localhost:8088,http://127.0.0.1:8088" ] \
        || [ "$cors" = "http://localhost:8088,http://127.0.0.1:8088,http://localhost:8089,http://127.0.0.1:8089" ]; then
        set_env_value SEALMAIL_CORS_ALLOWED_ORIGINS "$(origin_from_host "$public_host")"
      fi
      crl_base_url="$(env_value SEALMAIL_CA_CRL_BASE_URL)"
      if is_placeholder "$crl_base_url" || [ "$crl_base_url" = "http://localhost:8080/api/v1/crl/" ]; then
        set_env_value SEALMAIL_CA_CRL_BASE_URL "$(crl_base_url_from_host "$public_host")"
      fi
    fi
  fi

  ensure_secret SEALMAIL_DB_PASSWORD normal
  ensure_secret SEALMAIL_JWT_SECRET normal
  ensure_secret SEALMAIL_KEYSTORE_PASSWORD normal
  ensure_secret SEALMAIL_RAW_CONTENT_ENCRYPTION_KEY raw-content
  ensure_secret SEALMAIL_ADMIN_PASSWORD admin

  chmod go-rwx "$ENV_FILE" 2>/dev/null || warn "Could not restrict permissions on $ENV_FILE"
}

require_real_value() {
  key="$1"
  value="$(env_value "$key")"
  if is_placeholder "$value"; then
    die "Set a real value for $key in $ENV_FILE"
  fi
}

validate_site_name() {
  site="$1"
  case "$site" in
    ""|.*|*/*|*[!A-Za-z0-9_.-]*)
      die "SEALMAIL_SITE may contain only letters, numbers, dot, underscore, and dash, and must not start with dot"
      ;;
  esac
}

host_secret_path() {
  site="$1"
  container_path="$2"
  case "$container_path" in
    /run/secrets/edge/*)
      relative_path="${container_path#/run/secrets/edge/}"
      case "$relative_path" in
        ""|..|../*|*/..|*/../*)
          printf ''
          ;;
        *)
          printf 'runtime/%s/edge-secrets/%s' "$site" "$relative_path"
          ;;
      esac
      ;;
    /run/secrets/standard-tls/*)
      relative_path="${container_path#/run/secrets/standard-tls/}"
      case "$relative_path" in
        ""|..|../*|*/..|*/../*)
          printf ''
          ;;
        *)
          printf 'runtime/%s/standard-tls/%s' "$site" "$relative_path"
          ;;
      esac
      ;;
    *)
      printf ''
      ;;
  esac
}

require_mounted_secret_file() {
  label="$1"
  container_path="$2"
  site="$3"
  host_path="$(host_secret_path "$site" "$container_path")"
  if [ -z "$host_path" ]; then
    die "$label must point under /run/secrets/edge or /run/secrets/standard-tls"
  fi
  [ -f "$host_path" ] || die "$label points to $container_path, but $host_path does not exist"
}

validate_env() {
  [ -f "$COMPOSE_FILE" ] || die "Missing $COMPOSE_FILE"
  [ -f "$ENV_FILE" ] || die "Missing $ENV_FILE"

  require_real_value LOCAL_DOMAIN
  require_real_value SEALMAIL_DB_PASSWORD
  require_real_value SEALMAIL_JWT_SECRET
  require_real_value SEALMAIL_KEYSTORE_PASSWORD
  require_real_value SEALMAIL_RAW_CONTENT_ENCRYPTION_KEY
  require_real_value SEALMAIL_ADMIN_PASSWORD

  site="$(env_value SEALMAIL_SITE node)"
  validate_site_name "$site"

  route_mode="$(env_value REMOTE_ROUTE_MODE standard)"
  case "$route_mode" in
    standard|gm|none)
      ;;
    *)
      die "REMOTE_ROUTE_MODE must be standard, gm, or none"
      ;;
  esac

  remote_domain="$(env_value REMOTE_DOMAIN)"
  if [ -n "$remote_domain" ] && is_placeholder "$remote_domain"; then
    die "Set REMOTE_DOMAIN to a real peer domain, or leave it empty and set REMOTE_ROUTE_MODE=none"
  fi
  if [ "$route_mode" = "none" ] && [ -n "$remote_domain" ]; then
    warn "REMOTE_ROUTE_MODE=none ignores REMOTE_DOMAIN=$remote_domain; clear REMOTE_DOMAIN to avoid operator confusion"
  fi

  if [ "$route_mode" = "standard" ] && [ -n "$remote_domain" ]; then
    remote_host="$(env_value REMOTE_STANDARD_HOST)"
    if is_placeholder "$remote_host"; then
      die "Set REMOTE_STANDARD_HOST in $ENV_FILE, or set REMOTE_ROUTE_MODE=none/gm"
    fi
  elif [ "$route_mode" = "gm" ]; then
    edge_key_store="$(env_value EDGE_TLS_KEY_STORE)"
    edge_key_store_password="$(env_value EDGE_TLS_KEY_STORE_PASSWORD)"
    edge_trust_store="$(env_value EDGE_TLS_TRUST_STORE)"
    edge_trust_store_password="$(env_value EDGE_TLS_TRUST_STORE_PASSWORD)"
    edge_trust_all="$(env_value EDGE_TLS_TRUST_ALL false)"
    edge_routes="$(env_value EDGE_OUTBOUND_ROUTES)"
    if is_placeholder "$edge_key_store" || is_placeholder "$edge_key_store_password" || is_placeholder "$edge_routes"; then
      die "GM mode requires EDGE_TLS_KEY_STORE, EDGE_TLS_KEY_STORE_PASSWORD, and EDGE_OUTBOUND_ROUTES. Generate material with: EDGE_STORE_PASS='<strong-password>' ops/gm-edge-keystore.sh init --site $site --dns <mx-host> --ip <public-ip>"
    fi
    if ! bool_enabled "$edge_trust_all" && { is_placeholder "$edge_trust_store" || is_placeholder "$edge_trust_store_password"; }; then
      die "GM mode requires EDGE_TLS_TRUST_STORE and EDGE_TLS_TRUST_STORE_PASSWORD unless EDGE_TLS_TRUST_ALL=true"
    fi
    require_mounted_secret_file EDGE_TLS_KEY_STORE "$edge_key_store" "$site"
    if ! bool_enabled "$edge_trust_all"; then
      require_mounted_secret_file EDGE_TLS_TRUST_STORE "$edge_trust_store" "$site"
    fi
  fi

  local_delivery_mode="$(env_value LOCAL_DELIVERY_MODE mailpit)"
  case "$local_delivery_mode" in
    mailpit|smtp|none)
      ;;
    *)
      die "LOCAL_DELIVERY_MODE must be mailpit, smtp, or none"
      ;;
  esac
  if [ "$local_delivery_mode" = "smtp" ]; then
    require_real_value LOCAL_DELIVERY_HOST
    require_real_value LOCAL_DELIVERY_PORT
    local_transport="$(env_value LOCAL_DELIVERY_TRANSPORT smtp-clear)"
    case "$local_transport" in
      smtp-clear|smtp-tls)
        ;;
      *)
        die "LOCAL_DELIVERY_TRANSPORT must be smtp-clear or smtp-tls"
        ;;
    esac
  fi

  deployment_mode="$(normalize_deployment_mode "$(env_value SEALMAIL_DEPLOYMENT_MODE debug)")"
  spring_profiles="$(env_value SPRING_PROFILES_ACTIVE postgres | tr -d '[:space:]')"
  if ! profile_contains "$spring_profiles" postgres; then
    die "SPRING_PROFILES_ACTIVE must include postgres for the gateway compose stack"
  fi
  if [ "$deployment_mode" = "production" ] && ! profile_contains "$spring_profiles" prod; then
    die "SEALMAIL_DEPLOYMENT_MODE=production requires SPRING_PROFILES_ACTIVE to include prod"
  fi
  if [ "$deployment_mode" = "production" ] && [ "$local_delivery_mode" = "mailpit" ]; then
    die "Production mode does not start Mailpit. Set LOCAL_DELIVERY_MODE=none/smtp or use --debug"
  fi

  postfix_remote_tls_ca_file="$(env_value POSTFIX_REMOTE_TLS_CA_FILE)"
  if [ -n "$postfix_remote_tls_ca_file" ]; then
    require_mounted_secret_file POSTFIX_REMOTE_TLS_CA_FILE "$postfix_remote_tls_ca_file" "$site"
  fi

  postfix_tls_policy_level="$(env_value POSTFIX_REMOTE_TLS_POLICY_LEVEL)"
  if [ -n "$postfix_tls_policy_level" ]; then
    case "$postfix_tls_policy_level" in
      encrypt|verify|secure|fingerprint|may|none)
        ;;
      *)
        die "POSTFIX_REMOTE_TLS_POLICY_LEVEL must be encrypt, verify, secure, fingerprint, may, or none"
        ;;
    esac
    if [ "$postfix_tls_policy_level" = "fingerprint" ] && is_placeholder "$(env_value POSTFIX_REMOTE_TLS_POLICY_FINGERPRINT)"; then
      die "POSTFIX_REMOTE_TLS_POLICY_LEVEL=fingerprint requires POSTFIX_REMOTE_TLS_POLICY_FINGERPRINT"
    fi
  fi

  if [ "$deployment_mode" = "production" ] && [ "$route_mode" = "standard" ] && [ -n "$remote_domain" ]; then
    case "$postfix_tls_policy_level" in
      secure|fingerprint)
        ;;
      *)
        warn "Standard SMTP TLS is not peer-identity verified by default. For production, set POSTFIX_REMOTE_TLS_POLICY_LEVEL=secure with a trusted CA, or POSTFIX_REMOTE_TLS_POLICY_LEVEL=fingerprint with a pinned fingerprint."
        ;;
    esac
  fi

  if bool_enabled "$(env_value SEALMAIL_HTTPS_ENABLED false)"; then
    public_host="$(env_value SEALMAIL_PUBLIC_HOST)"
    if is_placeholder "$public_host"; then
      die "SEALMAIL_HTTPS_ENABLED=true requires SEALMAIL_PUBLIC_HOST or --https-host"
    fi
    validate_public_host "$public_host"
  fi

  public_remote_relay="$(env_value POSTFIX_PUBLIC_RELAY_REMOTE_DOMAIN)"
  if [ -n "$public_remote_relay" ]; then
    case "$public_remote_relay" in
      true|TRUE|1|yes|YES|on|ON|false|FALSE|0|no|NO|off|OFF)
        ;;
      *)
        die "POSTFIX_PUBLIC_RELAY_REMOTE_DOMAIN must be true or false when set"
        ;;
    esac
  fi
  if [ "$deployment_mode" = "production" ] && bool_enabled "$public_remote_relay"; then
    warn "POSTFIX_PUBLIC_RELAY_REMOTE_DOMAIN=true allows unauthenticated public ingress for REMOTE_DOMAIN. Keep it false in production unless a firewall restricts the port to trusted peers."
  fi

  if bool_enabled "$(env_value SEALMAIL_GM_EDGE_PUBLIC_ENABLED false)"; then
    edge_key_store="$(env_value EDGE_TLS_KEY_STORE)"
    edge_key_store_password="$(env_value EDGE_TLS_KEY_STORE_PASSWORD)"
    if is_placeholder "$edge_key_store" || is_placeholder "$edge_key_store_password"; then
      die "Public GM Edge ports require EDGE_TLS_KEY_STORE and EDGE_TLS_KEY_STORE_PASSWORD"
    fi
    require_mounted_secret_file EDGE_TLS_KEY_STORE "$edge_key_store" "$site"
  fi

  if [ "$(env_value EDGE_ADMIN_ENABLED true)" != "true" ]; then
    die "EDGE_ADMIN_ENABLED must stay true; the admin listener is internal by default and is used for the Edge healthcheck"
  fi

  standard_trust_store="$(env_value SEALMAIL_STANDARD_TLS_TRUST_STORE_PATH)"
  if [ -n "$standard_trust_store" ]; then
    require_mounted_secret_file SEALMAIL_STANDARD_TLS_TRUST_STORE_PATH "$standard_trust_store" "$site"
  fi
}

init_runtime_dirs() {
  site="$(env_value SEALMAIL_SITE node)"
  validate_site_name "$site"
  mkdir -p "runtime/$site/edge-secrets" "runtime/$site/standard-tls" "runtime/$site/backups"
  chmod 700 runtime "runtime/$site" "runtime/$site/edge-secrets" "runtime/$site/standard-tls" "runtime/$site/backups" 2>/dev/null \
    || warn "Could not restrict permissions under runtime/$site"
}

check_docker() {
  command -v docker >/dev/null 2>&1 || die "Docker is not installed or not in PATH"
  docker compose version >/dev/null 2>&1 || die "Docker Compose plugin is not available"
  [ "$DRY_RUN" = "true" ] && return 0
  docker info >/dev/null 2>&1 || die "Docker daemon is not reachable by this user"
}

debug_profile_enabled() {
  deployment_mode="$(normalize_deployment_mode "$(env_value SEALMAIL_DEPLOYMENT_MODE debug)")"
  [ "$deployment_mode" = "debug" ]
}

gm_profile_enabled() {
  [ "$(env_value REMOTE_ROUTE_MODE standard)" = "gm" ] || bool_enabled "$(env_value SEALMAIL_GM_EDGE_PUBLIC_ENABLED false)"
}

https_profile_enabled() {
  bool_enabled "$(env_value SEALMAIL_HTTPS_ENABLED false)"
}

compose_profile_args() {
  output=""
  if debug_profile_enabled; then
    output="--profile debug"
  fi
  if gm_profile_enabled; then
    output="${output:+$output }--profile gm"
  fi
  if debug_profile_enabled && gm_profile_enabled; then
    output="${output:+$output }--profile gm-debug"
  fi
  if https_profile_enabled; then
    output="${output:+$output }--profile https"
  fi
  printf '%s' "$output"
  return 0
}

compose_has_containers() {
  # shellcheck disable=SC2046
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" $(compose_profile_args) ps -q 2>/dev/null | grep -q .
}

port_in_use() {
  port="$1"
  case "$port" in
    ""|*[!0-9]*)
      return 1
      ;;
  esac
  if [ -r /proc/net/tcp ]; then
    hex_port="$(awk -v port="$port" 'BEGIN { printf "%04X", port }')"
    if awk -v port="$hex_port" '
      NR > 1 {
        split($2, local_address, ":")
        if (toupper(local_address[2]) == port && $4 == "0A") {
          found = 1
        }
      }
      END { exit found ? 0 : 1 }
    ' /proc/net/tcp /proc/net/tcp6 2>/dev/null; then
      return 0
    fi
  fi
  if command -v ss >/dev/null 2>&1; then
    ss -H -ltn "sport = :$port" 2>/dev/null | grep -q .
  elif command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
  elif command -v netstat >/dev/null 2>&1; then
    netstat -ltn 2>/dev/null | awk '{print $4}' | grep -Eq "(^|[.:])$port$"
  else
    warn "No ss/lsof/netstat found; skipping port checks"
    return 1
  fi
}

check_named_port() {
  label="$1"
  port="$2"
  [ -n "$port" ] || return 0
  case "$port" in
    *[!0-9]*)
      die "$label port must be numeric, got: $port"
      ;;
  esac
  case " ${CHECKED_PORTS:-} " in
    *" $port "*)
      die "Host port $port is assigned more than once; check $label and the other port settings"
      ;;
  esac
  CHECKED_PORTS="${CHECKED_PORTS:-} $port"
  if port_in_use "$port"; then
    die "$label port $port is already listening on this host"
  fi
}

check_ports() {
  [ "$DRY_RUN" = "false" ] || return 0
  [ "$SKIP_PORT_CHECK" = "false" ] || return 0
  if compose_has_containers; then
    log "Existing compose containers detected; skipping host port preflight"
    return
  fi

  CHECKED_PORTS=""
  check_named_port "Frontend HTTP" "$(env_value SEALMAIL_FRONTEND_HTTP_PORT 8088)"
  check_named_port "Postfix public SMTP ingress" "$(env_value POSTFIX_PUBLIC_SMTP_PORT 2527)"

  if gm_profile_enabled; then
    check_named_port "Edge STARTTLS" "$(env_value EDGE_STARTTLS_PUBLIC_PORT 2525)"
    check_named_port "Edge implicit TLS" "$(env_value EDGE_IMPLICIT_TLS_PUBLIC_PORT 2465)"
  fi

  if https_profile_enabled; then
    check_named_port "HTTPS reverse proxy HTTP" "$(env_value SEALMAIL_HTTPS_HTTP_PORT 80)"
    check_named_port "HTTPS reverse proxy HTTPS" "$(env_value SEALMAIL_HTTPS_HTTPS_PORT 443)"
  fi

  if debug_profile_enabled; then
    check_named_port "PostgreSQL debug" "$(env_value SEALMAIL_DB_HOST_PORT 5433)"
    check_named_port "Backend HTTP debug" "$(env_value SEALMAIL_BACKEND_HTTP_PORT 8080)"
    check_named_port "Backend SMTP debug" "$(env_value SEALMAIL_SMTP_DEBUG_PORT 10025)"
    check_named_port "Mailpit SMTP debug" "$(env_value MAILPIT_SMTP_DEBUG_PORT 1025)"
    check_named_port "Mailpit UI debug" "$(env_value MAILPIT_UI_HOST_PORT 8025)"
  fi

  if debug_profile_enabled && gm_profile_enabled; then
    check_named_port "Edge admin debug" "$(env_value EDGE_ADMIN_HOST_PORT 2727)"
  fi
}

validate_compose_config() {
  # shellcheck disable=SC2046
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" $(compose_profile_args) config >/dev/null
}

run_stack() {
  build_arg=""
  [ "$BUILD_IMAGE" = "true" ] && build_arg="--build"
  # shellcheck disable=SC2046,SC2086
  if [ "$DRY_RUN" = "true" ]; then
    echo docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" $(compose_profile_args) up -d $build_arg
    return
  fi
  # shellcheck disable=SC2046,SC2086
  exec docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" $(compose_profile_args) up -d $build_arg
}

generate_or_update_env
validate_env
init_runtime_dirs
check_docker
check_ports
validate_compose_config
run_stack
