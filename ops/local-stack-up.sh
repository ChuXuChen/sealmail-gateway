#!/usr/bin/env sh
set -eu

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
ENV_FILE="${ENV_FILE:-.env}"
EXAMPLE_ENV_FILE="${EXAMPLE_ENV_FILE:-.env.example}"
BUILD_IMAGE=true
DRY_RUN=false

usage() {
  cat <<'EOF'
Usage: ops/local-stack-up.sh [options]

Create or update local .env, fill placeholder secrets, prepare runtime/keystore,
validate the local Compose config, and run docker compose up --build.

Options:
  --env-file PATH       Environment file to use (default: .env)
  --compose-file PATH   Compose file to use (default: docker-compose.yml)
  --no-build            Run compose up without --build
  --dry-run             Generate, validate, and print the compose command
  -h, --help            Show this help
EOF
}

die() {
  echo "ERROR: $*" >&2
  exit 1
}

log() {
  echo "==> $*" >&2
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
    --no-build)
      BUILD_IMAGE=false
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
    return 0
  fi
  if [ "$kind" = "raw-content" ]; then
    generated="$(random_raw_content_key)"
  else
    generated="$(random_hex 32)"
  fi
  set_env_value "$key" "$generated"
  log "Filled $key in $ENV_FILE"
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

  if [ "$generated_env" = "true" ] || is_placeholder "$(env_value SEALMAIL_UID)"; then
    set_env_value SEALMAIL_UID "$(id -u)"
  fi
  if [ "$generated_env" = "true" ] || is_placeholder "$(env_value SEALMAIL_GID)"; then
    set_env_value SEALMAIL_GID "$(id -g)"
  fi

  ensure_secret SEALMAIL_DB_PASSWORD normal
  ensure_secret SEALMAIL_JWT_SECRET normal
  ensure_secret SEALMAIL_KEYSTORE_PASSWORD normal
  ensure_secret SEALMAIL_RAW_CONTENT_ENCRYPTION_KEY raw-content

  chmod go-rwx "$ENV_FILE" 2>/dev/null || true
}

validate_env() {
  [ -f "$COMPOSE_FILE" ] || die "Missing $COMPOSE_FILE"
  for key in \
    SEALMAIL_DB_PASSWORD \
    SEALMAIL_JWT_SECRET \
    SEALMAIL_KEYSTORE_PASSWORD \
    SEALMAIL_RAW_CONTENT_ENCRYPTION_KEY
  do
    value="$(env_value "$key")"
    if is_placeholder "$value"; then
      die "Set a real value for $key in $ENV_FILE"
    fi
  done
}

check_docker() {
  command -v docker >/dev/null 2>&1 || die "Docker is not installed or not in PATH"
  docker compose version >/dev/null 2>&1 || die "Docker Compose plugin is not available"
  [ "$DRY_RUN" = "true" ] && return 0
  docker info >/dev/null 2>&1 || die "Docker daemon is not reachable by this user"
}

run_stack() {
  build_arg=""
  [ "$BUILD_IMAGE" = "true" ] && build_arg="--build"
  if [ "$DRY_RUN" = "true" ]; then
    echo docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up $build_arg
    return 0
  fi
  # shellcheck disable=SC2086
  exec docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up $build_arg
}

generate_or_update_env
validate_env
mkdir -p runtime/keystore
chmod 700 runtime runtime/keystore 2>/dev/null || true
check_docker
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config >/dev/null
run_stack
