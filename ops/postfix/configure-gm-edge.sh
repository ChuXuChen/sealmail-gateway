#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo $0" >&2
  exit 1
fi

MAIN_CF="/etc/postfix/main.cf"
MASTER_CF="/etc/postfix/master.cf"
TRANSPORT_MAP="${TRANSPORT_MAP:-/etc/postfix/gm_transport}"
EDGE_OUTBOUND_HOST="${EDGE_OUTBOUND_HOST:-127.0.0.1}"
EDGE_OUTBOUND_PORT="${EDGE_OUTBOUND_PORT:-2526}"
EDGE_INBOUND_PORT="${EDGE_INBOUND_PORT:-2530}"
GM_DOMAINS="${GM_DOMAINS:-}"

backup_file() {
  local file="$1"
  local backup="${file}.gm-edge.$(date +%Y%m%d%H%M%S).bak"
  cp "$file" "$backup"
  echo "Backed up $file to $backup"
}

ensure_master_block() {
  local marker="$1"
  local block="$2"
  if grep -Eq "^${marker}[[:space:]]+" "$MASTER_CF"; then
    return
  fi
  printf '\n%s\n' "$block" >> "$MASTER_CF"
  echo "Appended ${marker} block to master.cf"
}

backup_file "$MAIN_CF"
backup_file "$MASTER_CF"

ensure_master_block "smtp-gm" "smtp-gm unix -       -       n       -       -       smtp
  -o syslog_name=postfix/smtp-gm
  -o smtp_tls_security_level=none
  -o disable_dns_lookups=yes"

ensure_master_block "127\\.0\\.0\\.1:${EDGE_INBOUND_PORT}" "127.0.0.1:${EDGE_INBOUND_PORT} inet  n       -       n       -       10      smtpd
  -o syslog_name=postfix/gm-edge-inbound
  -o content_filter=sealmail:[127.0.0.1]:10025
  -o receive_override_options=no_unknown_recipient_checks,no_milters
  -o smtpd_helo_restrictions=
  -o smtpd_client_restrictions=permit_mynetworks,reject
  -o smtpd_sender_restrictions=
  -o smtpd_recipient_restrictions=permit_mynetworks,reject
  -o smtpd_tls_security_level="

touch "$TRANSPORT_MAP"
if [[ -n "$GM_DOMAINS" ]]; then
  while IFS= read -r domain; do
    [[ -z "$domain" ]] && continue
    if ! grep -Eq "^${domain//./\\.}[[:space:]]+" "$TRANSPORT_MAP"; then
      printf '%s smtp-gm:[%s]:%s\n' "$domain" "$EDGE_OUTBOUND_HOST" "$EDGE_OUTBOUND_PORT" >> "$TRANSPORT_MAP"
    fi
  done < <(tr ',' '\n' <<< "$GM_DOMAINS" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
fi

postmap "$TRANSPORT_MAP"
postconf -e "transport_maps=hash:${TRANSPORT_MAP}"
postfix check
postfix reload

echo "Configured GM Edge transport map: hash:${TRANSPORT_MAP}"
echo "Add GM domains with: GM_DOMAINS=partner.example.cn sudo -E $0"
