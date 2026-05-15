#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo $0" >&2
  exit 1
fi

MAIN_CF="/etc/postfix/main.cf"
MASTER_CF="/etc/postfix/master.cf"
TRANSPORT="sealmail"
AFTER_FILTER_QUEUE_MINFREE="sealmail_after_filter_queue_minfree"
BEFORE_FILTER_MESSAGE_SIZE_LIMIT="sealmail_before_filter_message_size_limit"
DEFAULT_SEALMAIL_HOSTNAME="${DEFAULT_SEALMAIL_HOSTNAME:-postfix.sealmail.local}"

backup_file() {
  local file="$1"
  local backup="${file}.sealmail.$(date +%Y%m%d%H%M%S).bak"
  cp "$file" "$backup"
  echo "Backed up $file to $backup"
}

block_has_option() {
  local block_start_regex="$1"
  local option_regex="$2"
  awk -v start="$block_start_regex" -v option="$option_regex" '
    $0 ~ start { inside = 1 }
    inside && $0 ~ option { found = 1 }
    inside && /^$/ { inside = 0 }
    END { exit(found ? 0 : 1) }
  ' "$MASTER_CF"
}

ensure_block_option() {
  local block_start_regex="$1"
  local anchor_regex="$2"
  local option_regex="$3"
  local option_line="$4"

  if ! grep -Eq "$block_start_regex" "$MASTER_CF"; then
    return
  fi
  if block_has_option "$block_start_regex" "$option_regex"; then
    return
  fi

  local tmp
  tmp="$(mktemp)"
  awk -v start="$block_start_regex" -v anchor="$anchor_regex" -v option="$option_line" '
    $0 ~ start { inside = 1; inserted = 0 }
    inside && !inserted && $0 ~ anchor {
      print
      print option
      inserted = 1
      next
    }
    inside && /^$/ { inside = 0; inserted = 0 }
    { print }
  ' "$MASTER_CF" > "$tmp"
  cat "$tmp" > "$MASTER_CF"
  rm -f "$tmp"
}

ensure_transport() {
  if grep -Eq '^sealmail[[:space:]]+unix' "$MASTER_CF"; then
    return
  fi

  if grep -Eq '^ciphermail[[:space:]]+unix' "$MASTER_CF"; then
    sed -i -E 's/^ciphermail([[:space:]]+unix[[:space:]].*)$/sealmail\1/' "$MASTER_CF"
    echo "Renamed ciphermail transport to sealmail in master.cf"
    return
  fi

  cat <<'EOF' >> "$MASTER_CF"

sealmail unix -       -       n       -       4       smtp
  -o smtp_send_xforward_command=yes
  -o disable_dns_lookups=yes
EOF
  echo "Appended sealmail transport to master.cf"
}

rename_legacy_parameters() {
  sed -i \
    -e 's/ciphermail_/sealmail_/g' \
    -e 's/CipherMail/SealMail/g' \
    -e "s/postfix\\.ciphermail\\.net/${DEFAULT_SEALMAIL_HOSTNAME}/g" \
    "$MAIN_CF" "$MASTER_CF"
}

ensure_main_parameter() {
  local name="$1"
  local default_value="$2"
  local current_value

  current_value="$(postconf -h "$name" 2>/dev/null || true)"
  if [[ -z "$current_value" ]]; then
    postconf -e "${name}=${default_value}"
  fi
}

append_after_filter_block() {
  if grep -Eq '^127\.0\.0\.1:10026[[:space:]]+inet' "$MASTER_CF"; then
    echo "master.cf already contains 127.0.0.1:10026"
    ensure_block_option \
      '^127\.0\.0\.1:10026[[:space:]]+inet' \
      'cleanup_service_name=port_10026_cleanup' \
      '^[[:space:]]+-o[[:space:]]+content_filter=' \
      '  -o content_filter='
    return
  fi

  cat <<'EOF' >> "$MASTER_CF"

port_10026_cleanup unix  n       -       n       -       0       cleanup
  -o hopcount_limit=100
  -o header_checks=
  -o mime_header_checks=
  -o nested_header_checks=
  -o body_checks=
  -o milter_header_checks=
  -o smtp_header_checks=
  -o smtp_mime_header_checks=
  -o smtp_nested_header_checks=
  -o smtp_body_checks=

127.0.0.1:10026 inet  n       -       n       -       10      smtpd
  -o syslog_name=postfix/10026
  -o cleanup_service_name=port_10026_cleanup
  -o content_filter=
  -o receive_override_options=no_unknown_recipient_checks,no_milters
  -o smtpd_helo_restrictions=
  -o smtpd_client_restrictions=
  -o smtpd_sender_restrictions=
  -o smtpd_recipient_restrictions=permit_mynetworks,reject
  -o smtpd_tls_security_level=
  -o smtpd_authorized_xforward_hosts=127.0.0.0/8
  -o smtpd_authorized_xclient_hosts=127.0.0.0/8
  -o queue_minfree=${sealmail_after_filter_queue_minfree}
EOF
  echo "Appended 10026 reinject block to master.cf"
}

append_outbound_block() {
  if grep -Eq '^127\.0\.0\.1:10027[[:space:]]+inet' "$MASTER_CF"; then
    echo "master.cf already contains 127.0.0.1:10027"
    ensure_block_option \
      '^127\.0\.0\.1:10027[[:space:]]+inet' \
      'cleanup_service_name=port_10027_cleanup' \
      '^[[:space:]]+-o[[:space:]]+content_filter=' \
      '  -o content_filter='
    return
  fi

  cat <<'EOF' >> "$MASTER_CF"

port_10027_cleanup unix  n       -       n       -       0       cleanup
  -o header_checks=
  -o mime_header_checks=
  -o nested_header_checks=
  -o body_checks=
  -o milter_header_checks=
  -o smtp_header_checks=
  -o smtp_mime_header_checks=
  -o smtp_nested_header_checks=
  -o smtp_body_checks=

127.0.0.1:10027 inet  n       -       n       -       10      smtpd
  -o syslog_name=postfix/10027
  -o cleanup_service_name=port_10027_cleanup
  -o content_filter=
  -o smtpd_helo_restrictions=
  -o smtpd_client_restrictions=
  -o smtpd_sender_restrictions=
  -o smtpd_recipient_restrictions=permit_mynetworks,reject
  -o smtpd_tls_security_level=
  -o message_size_limit=${sealmail_before_filter_message_size_limit}
EOF
  echo "Appended 10027 reinject block to master.cf"
}

backup_file "$MAIN_CF"
backup_file "$MASTER_CF"

rename_legacy_parameters
ensure_transport
append_after_filter_block
append_outbound_block
postconf -e "content_filter=${TRANSPORT}:[127.0.0.1]:10025"
postconf -e "mail_name=SealMail"
ensure_main_parameter "${AFTER_FILTER_QUEUE_MINFREE}" "0"
ensure_main_parameter "${BEFORE_FILTER_MESSAGE_SIZE_LIMIT}" "0"

postfix check
postfix reload

echo "Configured Postfix content_filter -> ${TRANSPORT}:[127.0.0.1]:10025"
echo "Reinject ports expected: 127.0.0.1:10026 and 127.0.0.1:10027"
