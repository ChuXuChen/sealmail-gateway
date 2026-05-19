#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<'EOF'
Usage:
  ops/gm-edge-keystore.sh init --site alpha --dns mx-alpha.example.com --ip 203.0.113.10
  ops/gm-edge-keystore.sh trust --site alpha --peer beta --cert /path/to/beta-edge.crt

Commands:
  init    Generate this node's SM2 Edge TLS key, certificate, and PKCS12 keystore.
  trust   Import a peer Edge public certificate into this node's PKCS12 truststore.

Options:
  --site <name>      Runtime site directory under runtime/<site>/edge-secrets.
  --dns <name>       DNS SAN for the generated certificate. Repeatable.
  --ip <address>     IP SAN for the generated certificate. Repeatable.
  --cn <name>        Certificate CN. Defaults to the first --dns, then the first --ip.
  --peer <name>      Peer alias for truststore import.
  --cert <path>      Peer certificate file for truststore import.

Environment:
  EDGE_STORE_PASS    PKCS12 password. If unset, the script prompts for it.
  EDGE_DAYS          Certificate validity days for init. Default: 365.

Output:
  runtime/<site>/edge-secrets/sealmail-gm-edge.p12
  runtime/<site>/edge-secrets/sealmail-gm-trust.p12
  runtime/<site>/edge-secrets/<site>-edge.crt
EOF
}

die() {
  echo "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Missing command: $1"
}

kona_provider_path() {
  local maven_user_home="${MAVEN_USER_HOME:-/tmp/maven}"
  local m2="${MAVEN_REPO:-$maven_user_home/repository}"
  local version="${KONA_VERSION:-1.0.20}"
  local files=(
    "$m2/com/tencent/kona/kona-provider/$version/kona-provider-$version.jar"
    "$m2/com/tencent/kona/kona-crypto/$version/kona-crypto-$version.jar"
    "$m2/com/tencent/kona/kona-pkix/$version/kona-pkix-$version.jar"
    "$m2/com/tencent/kona/kona-ssl/$version/kona-ssl-$version.jar"
  )
  if [[ ! -f "${files[0]}" && -x "$ROOT/mvnw" ]]; then
    MAVEN_USER_HOME="$maven_user_home" "$ROOT/mvnw" \
      -Dmaven.repo.local="$m2" \
      -f "$ROOT/sealmail-backend/pom.xml" \
      -pl sealmail-edge \
      -DskipTests \
      dependency:go-offline >/dev/null
  fi
  local file
  for file in "${files[@]}"; do
    [[ -f "$file" ]] || die "Missing Kona dependency: $file
Build or test sealmail-edge first, then rerun this command."
  done
  local IFS=:
  printf '%s' "${files[*]}"
}

store_password() {
  if [[ -n "${EDGE_STORE_PASS:-}" ]]; then
    printf '%s' "$EDGE_STORE_PASS"
    return
  fi
  local first second
  read -r -s -p "PKCS12 password: " first
  echo >&2
  read -r -s -p "Repeat PKCS12 password: " second
  echo >&2
  [[ "$first" == "$second" ]] || die "Passwords do not match"
  [[ -n "$first" ]] || die "PKCS12 password cannot be empty"
  printf '%s' "$first"
}

edge_dir() {
  local site="$1"
  [[ -n "$site" ]] || die "--site is required"
  printf '%s/runtime/%s/edge-secrets' "$ROOT" "$site"
}

write_config() {
  local file="$1"
  local cn="$2"
  shift 2
  local -a dns_values=()
  local -a ip_values=()

  while [[ "$#" -gt 0 ]]; do
    case "$1" in
      dns)
        dns_values+=("$2")
        shift 2
        ;;
      ip)
        ip_values+=("$2")
        shift 2
        ;;
      *)
        die "Internal error: unknown SAN kind $1"
        ;;
    esac
  done

  {
    cat <<EOF
[ req ]
distinguished_name = dn
prompt = no

[ dn ]
CN = $cn
O = SealMail Edge

[ v3_req ]
basicConstraints = critical,CA:false
keyUsage = critical,digitalSignature,keyAgreement
extendedKeyUsage = serverAuth,clientAuth
subjectAltName = @alt_names

[ alt_names ]
EOF
    local index=1
    for value in "${dns_values[@]}"; do
      printf 'DNS.%d = %s\n' "$index" "$value"
      index=$((index + 1))
    done
    index=1
    for value in "${ip_values[@]}"; do
      printf 'IP.%d = %s\n' "$index" "$value"
      index=$((index + 1))
    done
  } > "$file"
}

cmd_init() {
  local site="" cn="" days="${EDGE_DAYS:-365}"
  local -a dns_values=()
  local -a ip_values=()

  while [[ "$#" -gt 0 ]]; do
    case "$1" in
      --site)
        site="${2:-}"
        shift 2
        ;;
      --dns)
        dns_values+=("${2:-}")
        shift 2
        ;;
      --ip)
        ip_values+=("${2:-}")
        shift 2
        ;;
      --cn)
        cn="${2:-}"
        shift 2
        ;;
      --help|-h)
        usage
        exit 0
        ;;
      *)
        die "Unknown init option: $1"
        ;;
    esac
  done

  require_command openssl
  [[ -n "$site" ]] || die "--site is required"
  [[ "${#dns_values[@]}" -gt 0 || "${#ip_values[@]}" -gt 0 ]] || die "At least one --dns or --ip is required"
  [[ "$days" =~ ^[0-9]+$ && "$days" -gt 0 ]] || die "EDGE_DAYS must be a positive integer"

  if [[ -z "$cn" ]]; then
    if [[ "${#dns_values[@]}" -gt 0 ]]; then
      cn="${dns_values[0]}"
    else
      cn="${ip_values[0]}"
    fi
  fi

  local dir
  dir="$(edge_dir "$site")"
  mkdir -p "$dir"
  chmod 700 "$dir"

  local password key_file cnf_file csr_file cert_file p12_file
  password="$(store_password)"
  key_file="$dir/$site-edge-sm2.key"
  cnf_file="$dir/$site-edge.cnf"
  csr_file="$dir/$site-edge.csr"
  cert_file="$dir/$site-edge.crt"
  p12_file="$dir/sealmail-gm-edge.p12"

  local -a san_args=()
  local value
  for value in "${dns_values[@]}"; do
    [[ -n "$value" ]] || die "--dns cannot be empty"
    san_args+=(dns "$value")
  done
  for value in "${ip_values[@]}"; do
    [[ -n "$value" ]] || die "--ip cannot be empty"
    san_args+=(ip "$value")
  done

  write_config "$cnf_file" "$cn" "${san_args[@]}"
  openssl ecparam -genkey -name SM2 -out "$key_file"
  openssl req -new -key "$key_file" -sm3 -config "$cnf_file" -out "$csr_file"
  openssl x509 -req -in "$csr_file" -signkey "$key_file" -sm3 \
    -days "$days" -extfile "$cnf_file" -extensions v3_req -out "$cert_file"
  openssl pkcs12 -export -inkey "$key_file" -in "$cert_file" \
    -name sealmail-gm-edge -out "$p12_file" -passout "pass:$password"

  chmod 600 "$key_file" "$p12_file"
  chmod 644 "$cert_file"

  cat <<EOF
Generated Edge TLS material for site '$site':
  Keystore:    $p12_file
  Public cert: $cert_file

Set these in .env.gateway:
  EDGE_TLS_KEY_STORE=/run/secrets/edge/sealmail-gm-edge.p12
  EDGE_TLS_KEY_STORE_PASSWORD=<the password you entered>
EOF
}

cmd_trust() {
  local site="" peer="" cert=""

  while [[ "$#" -gt 0 ]]; do
    case "$1" in
      --site)
        site="${2:-}"
        shift 2
        ;;
      --peer)
        peer="${2:-}"
        shift 2
        ;;
      --cert)
        cert="${2:-}"
        shift 2
        ;;
      --help|-h)
        usage
        exit 0
        ;;
      *)
        die "Unknown trust option: $1"
        ;;
    esac
  done

  require_command openssl
  require_command keytool
  [[ -n "$site" ]] || die "--site is required"
  [[ -n "$peer" ]] || die "--peer is required"
  [[ -n "$cert" ]] || die "--cert is required"
  [[ -f "$cert" ]] || die "Peer certificate not found: $cert"

  local dir password trust_file provider_path
  dir="$(edge_dir "$site")"
  mkdir -p "$dir"
  chmod 700 "$dir"
  password="$(store_password)"
  trust_file="$dir/sealmail-gm-trust.p12"
  provider_path="$(kona_provider_path)"

  if [[ -f "$trust_file" ]] && keytool -list \
    -alias "$peer-edge" \
    -keystore "$trust_file" \
    -storetype PKCS12 \
    -storepass "$password" \
    -providerpath "$provider_path" \
    -providerclass com.tencent.kona.KonaProvider \
    -providerclass com.tencent.kona.crypto.KonaCryptoProvider \
    -providerclass com.tencent.kona.pkix.KonaPKIXProvider \
    -providerclass com.tencent.kona.ssl.KonaSSLProvider \
    -providername Kona >/dev/null 2>&1; then
    keytool -delete \
      -alias "$peer-edge" \
      -keystore "$trust_file" \
      -storetype PKCS12 \
      -storepass "$password" \
      -providerpath "$provider_path" \
      -providerclass com.tencent.kona.KonaProvider \
      -providerclass com.tencent.kona.crypto.KonaCryptoProvider \
      -providerclass com.tencent.kona.pkix.KonaPKIXProvider \
      -providerclass com.tencent.kona.ssl.KonaSSLProvider \
      -providername Kona
  fi

  keytool -importcert -noprompt \
    -alias "$peer-edge" \
    -file "$cert" \
    -keystore "$trust_file" \
    -storetype PKCS12 \
    -storepass "$password" \
    -providerpath "$provider_path" \
    -providerclass com.tencent.kona.KonaProvider \
    -providerclass com.tencent.kona.crypto.KonaCryptoProvider \
    -providerclass com.tencent.kona.pkix.KonaPKIXProvider \
    -providerclass com.tencent.kona.ssl.KonaSSLProvider \
    -providername Kona
  chmod 600 "$trust_file"

  cat <<EOF
Updated Edge truststore for site '$site':
  Truststore:  $trust_file

Set these in .env.gateway:
  EDGE_TLS_TRUST_STORE=/run/secrets/edge/sealmail-gm-trust.p12
  EDGE_TLS_TRUST_STORE_PASSWORD=<the password you entered>
EOF
}

if [[ "$#" -lt 1 ]]; then
  usage
  exit 1
fi

case "$1" in
  init)
    shift
    cmd_init "$@"
    ;;
  trust)
    shift
    cmd_trust "$@"
    ;;
  --help|-h|help)
    usage
    ;;
  *)
    die "Unknown command: $1"
    ;;
esac
