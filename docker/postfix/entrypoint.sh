#!/usr/bin/env sh
set -eu

MAIN_CF="/etc/postfix/main.cf"
MASTER_CF="/etc/postfix/master.cf"
TRANSPORT_MAP="/etc/postfix/transport"
TLS_POLICY_MAP="/etc/postfix/tls_policy"

: "${LOCAL_DOMAIN:?Set LOCAL_DOMAIN}"

POSTFIX_HOSTNAME="${POSTFIX_HOSTNAME:-mail.${LOCAL_DOMAIN}}"
REMOTE_DOMAIN="${REMOTE_DOMAIN:-}"
REMOTE_ROUTE_MODE="${REMOTE_ROUTE_MODE:-standard}"
REMOTE_STANDARD_HOST="${REMOTE_STANDARD_HOST:-}"
REMOTE_STANDARD_PORT="${REMOTE_STANDARD_PORT:-2527}"
SEALMAIL_DEPLOYMENT_MODE="${SEALMAIL_DEPLOYMENT_MODE:-debug}"
SEALMAIL_HOST="${SEALMAIL_HOST:-sealmail-backend}"
SEALMAIL_PORT="${SEALMAIL_PORT:-10025}"
MAILPIT_HOST="${MAILPIT_HOST:-mailpit}"
MAILPIT_PORT="${MAILPIT_PORT:-1025}"
LOCAL_DELIVERY_MODE="${LOCAL_DELIVERY_MODE:-mailpit}"
LOCAL_DELIVERY_HOST="${LOCAL_DELIVERY_HOST:-$MAILPIT_HOST}"
LOCAL_DELIVERY_PORT="${LOCAL_DELIVERY_PORT:-$MAILPIT_PORT}"
LOCAL_DELIVERY_TRANSPORT="${LOCAL_DELIVERY_TRANSPORT:-smtp-clear}"
EDGE_OUTBOUND_HOST="${EDGE_OUTBOUND_HOST:-sealmail-edge}"
EDGE_OUTBOUND_PORT="${EDGE_OUTBOUND_PORT:-2526}"
POSTFIX_AFTER_FILTER_PORT="${POSTFIX_AFTER_FILTER_PORT:-10026}"
POSTFIX_OUTBOUND_PORT="${POSTFIX_OUTBOUND_PORT:-10027}"
EDGE_INBOUND_POSTFIX_PORT="${EDGE_INBOUND_POSTFIX_PORT:-2530}"
POSTFIX_SMTPD_TLS_SECURITY_LEVEL="${POSTFIX_SMTPD_TLS_SECURITY_LEVEL:-may}"
POSTFIX_REMOTE_TLS_SECURITY_LEVEL="${POSTFIX_REMOTE_TLS_SECURITY_LEVEL:-encrypt}"
POSTFIX_REMOTE_TLS_CA_FILE="${POSTFIX_REMOTE_TLS_CA_FILE:-}"
POSTFIX_REMOTE_TLS_FINGERPRINT_DIGEST="${POSTFIX_REMOTE_TLS_FINGERPRINT_DIGEST:-sha256}"
POSTFIX_REMOTE_TLS_POLICY_LEVEL="${POSTFIX_REMOTE_TLS_POLICY_LEVEL:-}"
POSTFIX_REMOTE_TLS_POLICY_MATCH="${POSTFIX_REMOTE_TLS_POLICY_MATCH:-}"
POSTFIX_REMOTE_TLS_POLICY_FINGERPRINT="${POSTFIX_REMOTE_TLS_POLICY_FINGERPRINT:-}"
POSTFIX_REMOTE_TLS_POLICY_PROTOCOLS="${POSTFIX_REMOTE_TLS_POLICY_PROTOCOLS:-}"
POSTFIX_REMOTE_TLS_POLICY_CIPHERS="${POSTFIX_REMOTE_TLS_POLICY_CIPHERS:-}"
POSTFIX_PUBLIC_RELAY_REMOTE_DOMAIN="${POSTFIX_PUBLIC_RELAY_REMOTE_DOMAIN:-}"
POSTFIX_TLS_CERT_FILE="${POSTFIX_TLS_CERT_FILE:-/etc/postfix/tls/tls.crt}"
POSTFIX_TLS_KEY_FILE="${POSTFIX_TLS_KEY_FILE:-/etc/postfix/tls/tls.key}"

bool_enabled() {
  case "$1" in
    true|TRUE|1|yes|YES|on|ON)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

append_domain() {
  domains="$1"
  domain="$2"
  if [ -z "$domains" ]; then
    printf '%s' "$domain"
  else
    printf '%s,%s' "$domains" "$domain"
  fi
}

if [ -z "$POSTFIX_PUBLIC_RELAY_REMOTE_DOMAIN" ]; then
  case "$SEALMAIL_DEPLOYMENT_MODE" in
    production|prod)
      POSTFIX_PUBLIC_RELAY_REMOTE_DOMAIN=false
      ;;
    *)
      POSTFIX_PUBLIC_RELAY_REMOTE_DOMAIN=true
      ;;
  esac
fi

mkdir -p /etc/postfix/tls
if [ ! -s "$POSTFIX_TLS_CERT_FILE" ] || [ ! -s "$POSTFIX_TLS_KEY_FILE" ]; then
  openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
    -subj "/CN=${POSTFIX_HOSTNAME}" \
    -keyout "$POSTFIX_TLS_KEY_FILE" \
    -out "$POSTFIX_TLS_CERT_FILE"
fi
chmod 600 "$POSTFIX_TLS_KEY_FILE"

ensure_master_block() {
  marker_regex="$1"
  block="$2"
  if grep -Eq "$marker_regex" "$MASTER_CF"; then
    return
  fi
  printf '\n%s\n' "$block" >> "$MASTER_CF"
}

postconf -e "compatibility_level=3.6"
postconf -e "maillog_file=/dev/stdout"
postconf -e "myhostname=${POSTFIX_HOSTNAME}"
postconf -e "myorigin=${LOCAL_DOMAIN}"
postconf -e "mydestination="
postconf -e "inet_interfaces=all"
postconf -e "inet_protocols=ipv4"
postconf -e "mynetworks=127.0.0.0/8 ${POSTFIX_EXTRA_MYNETWORKS:-}"
postconf -e "smtpd_relay_restrictions=permit_mynetworks,reject_unauth_destination"
postconf -e "smtpd_recipient_restrictions=permit_mynetworks,reject_unauth_destination"
postconf -e "smtpd_tls_security_level=${POSTFIX_SMTPD_TLS_SECURITY_LEVEL}"
postconf -e "smtpd_tls_cert_file=${POSTFIX_TLS_CERT_FILE}"
postconf -e "smtpd_tls_key_file=${POSTFIX_TLS_KEY_FILE}"
postconf -e "smtpd_tls_loglevel=1"
postconf -e "smtp_tls_security_level=may"
postconf -e "smtp_tls_loglevel=1"
if [ -n "$POSTFIX_REMOTE_TLS_CA_FILE" ]; then
  postconf -e "smtp_tls_CAfile=${POSTFIX_REMOTE_TLS_CA_FILE}"
fi
if [ -n "$POSTFIX_REMOTE_TLS_FINGERPRINT_DIGEST" ]; then
  postconf -e "smtp_tls_fingerprint_digest=${POSTFIX_REMOTE_TLS_FINGERPRINT_DIGEST}"
fi
postconf -e "content_filter=sealmail:[${SEALMAIL_HOST}]:${SEALMAIL_PORT}"
postconf -e "transport_maps=hash:${TRANSPORT_MAP}"

public_relay_domains="$LOCAL_DOMAIN"
outbound_relay_domains="$LOCAL_DOMAIN"
if [ -n "$REMOTE_DOMAIN" ] && [ "$REMOTE_ROUTE_MODE" != "none" ]; then
  outbound_relay_domains="$(append_domain "$outbound_relay_domains" "$REMOTE_DOMAIN")"
  if bool_enabled "$POSTFIX_PUBLIC_RELAY_REMOTE_DOMAIN"; then
    public_relay_domains="$(append_domain "$public_relay_domains" "$REMOTE_DOMAIN")"
  fi
fi
postconf -e "relay_domains=${public_relay_domains}"

ensure_master_block '^sealmail[[:space:]]+unix' "sealmail unix -       -       n       -       4       smtp
  -o smtp_send_xforward_command=yes
  -o disable_dns_lookups=yes"

ensure_master_block '^smtp-clear[[:space:]]+unix' "smtp-clear unix -       -       n       -       -       smtp
  -o smtp_tls_security_level=none"

ensure_master_block '^smtp-tls[[:space:]]+unix' "smtp-tls unix -       -       n       -       -       smtp
  -o smtp_tls_security_level=${POSTFIX_REMOTE_TLS_SECURITY_LEVEL}
  -o smtp_tls_loglevel=1"

ensure_master_block '^smtp-gm[[:space:]]+unix' "smtp-gm unix -       -       n       -       -       smtp
  -o syslog_name=postfix/smtp-gm
  -o smtp_tls_security_level=none
  -o disable_dns_lookups=yes"

ensure_master_block "^${POSTFIX_AFTER_FILTER_PORT}[[:space:]]+inet" "${POSTFIX_AFTER_FILTER_PORT} inet  n       -       n       -       10      smtpd
  -o syslog_name=postfix/${POSTFIX_AFTER_FILTER_PORT}
  -o content_filter=
  -o receive_override_options=no_unknown_recipient_checks,no_milters
  -o smtpd_relay_restrictions=reject_unauth_destination
  -o smtpd_recipient_restrictions=reject_unauth_destination
  -o smtpd_tls_security_level="

ensure_master_block "^${POSTFIX_OUTBOUND_PORT}[[:space:]]+inet" "${POSTFIX_OUTBOUND_PORT} inet  n       -       n       -       10      smtpd
  -o syslog_name=postfix/${POSTFIX_OUTBOUND_PORT}
  -o content_filter=
  -o receive_override_options=no_unknown_recipient_checks,no_milters
  -o relay_domains=${outbound_relay_domains}
  -o smtpd_relay_restrictions=reject_unauth_destination
  -o smtpd_recipient_restrictions=reject_unauth_destination
  -o smtpd_tls_security_level="

ensure_master_block "^${EDGE_INBOUND_POSTFIX_PORT}[[:space:]]+inet" "${EDGE_INBOUND_POSTFIX_PORT} inet  n       -       n       -       10      smtpd
  -o syslog_name=postfix/gm-edge-inbound
  -o content_filter=sealmail:[${SEALMAIL_HOST}]:${SEALMAIL_PORT}
  -o receive_override_options=no_unknown_recipient_checks,no_milters
  -o smtpd_relay_restrictions=reject_unauth_destination
  -o smtpd_recipient_restrictions=reject_unauth_destination
  -o smtpd_tls_security_level="

{
  case "$LOCAL_DELIVERY_MODE" in
    mailpit)
      printf '%s smtp-clear:[%s]:%s\n' "$LOCAL_DOMAIN" "$MAILPIT_HOST" "$MAILPIT_PORT"
      ;;
    smtp)
      : "${LOCAL_DELIVERY_HOST:?Set LOCAL_DELIVERY_HOST when LOCAL_DELIVERY_MODE=smtp}"
      : "${LOCAL_DELIVERY_PORT:?Set LOCAL_DELIVERY_PORT when LOCAL_DELIVERY_MODE=smtp}"
      case "$LOCAL_DELIVERY_TRANSPORT" in
        smtp-clear|smtp-tls)
          printf '%s %s:[%s]:%s\n' "$LOCAL_DOMAIN" "$LOCAL_DELIVERY_TRANSPORT" "$LOCAL_DELIVERY_HOST" "$LOCAL_DELIVERY_PORT"
          ;;
        *)
          echo "Unsupported LOCAL_DELIVERY_TRANSPORT: $LOCAL_DELIVERY_TRANSPORT" >&2
          exit 1
          ;;
      esac
      ;;
    none)
      printf '%s error:local-delivery-disabled\n' "$LOCAL_DOMAIN"
      ;;
    *)
      echo "Unsupported LOCAL_DELIVERY_MODE: $LOCAL_DELIVERY_MODE" >&2
      exit 1
      ;;
  esac
  if [ -n "$REMOTE_DOMAIN" ]; then
    case "$REMOTE_ROUTE_MODE" in
      standard)
        : "${REMOTE_STANDARD_HOST:?Set REMOTE_STANDARD_HOST when REMOTE_ROUTE_MODE=standard}"
        printf '%s smtp-tls:[%s]:%s\n' "$REMOTE_DOMAIN" "$REMOTE_STANDARD_HOST" "$REMOTE_STANDARD_PORT"
        ;;
      gm)
        printf '%s smtp-gm:[%s]:%s\n' "$REMOTE_DOMAIN" "$EDGE_OUTBOUND_HOST" "$EDGE_OUTBOUND_PORT"
        ;;
      none)
        ;;
      *)
        echo "Unsupported REMOTE_ROUTE_MODE: $REMOTE_ROUTE_MODE" >&2
        exit 1
        ;;
    esac
  fi
} > "$TRANSPORT_MAP"
postmap "$TRANSPORT_MAP"

if [ -n "$POSTFIX_REMOTE_TLS_POLICY_LEVEL" ] && [ -n "$REMOTE_DOMAIN" ] && [ "$REMOTE_ROUTE_MODE" = "standard" ]; then
  tls_policy="$POSTFIX_REMOTE_TLS_POLICY_LEVEL"
  if [ "$POSTFIX_REMOTE_TLS_POLICY_LEVEL" = "fingerprint" ] && [ -n "$POSTFIX_REMOTE_TLS_POLICY_FINGERPRINT" ]; then
    tls_policy="$tls_policy match=$POSTFIX_REMOTE_TLS_POLICY_FINGERPRINT"
  elif [ -n "$POSTFIX_REMOTE_TLS_POLICY_MATCH" ]; then
    tls_policy="$tls_policy match=$POSTFIX_REMOTE_TLS_POLICY_MATCH"
  fi
  if [ -n "$POSTFIX_REMOTE_TLS_POLICY_PROTOCOLS" ]; then
    tls_policy="$tls_policy protocols=$POSTFIX_REMOTE_TLS_POLICY_PROTOCOLS"
  fi
  if [ -n "$POSTFIX_REMOTE_TLS_POLICY_CIPHERS" ]; then
    tls_policy="$tls_policy ciphers=$POSTFIX_REMOTE_TLS_POLICY_CIPHERS"
  fi
  printf '[%s]:%s %s\n' "$REMOTE_STANDARD_HOST" "$REMOTE_STANDARD_PORT" "$tls_policy" > "$TLS_POLICY_MAP"
  postmap "$TLS_POLICY_MAP"
  postconf -e "smtp_tls_policy_maps=hash:${TLS_POLICY_MAP}"
fi

newaliases || true
postfix check
exec postfix start-fg
