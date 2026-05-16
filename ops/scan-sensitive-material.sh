#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

scan_config_secrets() {
  local pattern='(^|[[:space:]-])(password|secret|token)[[:space:]]*[:=][[:space:]]*[^${[:space:]#][^[:space:]#]*'
  local matches

  matches="$(git -C "$ROOT" grep -n -I -E -e "$pattern" -- \
      '*.yml' '*.yaml' '*.properties' \
      ':(exclude)**/target/**' || true)"

  if [[ -n "$matches" ]]; then
    echo "$matches"
    echo "Sensitive material scan failed: literal secret assignment in config" >&2
    return 1
  fi
}

scan_sensitive_filenames() {
  local matches

  matches="$(git -C "$ROOT" ls-files --cached --others --exclude-standard | \
      grep -E '\.(pem|key|crt|cer|p12|pfx|jks|keystore)$' || true)"

  if [[ -n "$matches" ]]; then
    echo "$matches"
    echo "Sensitive material scan failed: key, certificate or keystore file committed" >&2
    return 1
  fi
}

scan_pem_blocks() {
  local failed=0

  while IFS= read -r -d '' file; do
    if [[ ! -f "$ROOT/$file" ]]; then
      continue
    fi

    case "$file" in
      ops/scan-sensitive-material.sh|*.png|*.jpg|*.jpeg|*.gif|*.ico|*.woff|*.woff2)
        continue
        ;;
    esac

    if perl -0ne 'exit 0 if m{-----BEGIN (?:RSA |EC |ENCRYPTED |OPENSSH |DSA |PRIVATE )?PRIVATE KEY-----\s+[A-Za-z0-9+/=\r\n]{80,}\s+-----END (?:RSA |EC |ENCRYPTED |OPENSSH |DSA |PRIVATE )?PRIVATE KEY-----}s; exit 1' "$ROOT/$file"; then
      echo "$file: private key PEM block"
      failed=1
    fi
    if perl -0ne 'exit 0 if m{-----BEGIN CERTIFICATE-----\s+[A-Za-z0-9+/=\r\n]{200,}\s+-----END CERTIFICATE-----}s; exit 1' "$ROOT/$file"; then
      echo "$file: certificate PEM block"
      failed=1
    fi
  done < <(git -C "$ROOT" ls-files -z --cached --others --exclude-standard)

  if [[ "$failed" -ne 0 ]]; then
    echo "Sensitive material scan failed: PEM material block" >&2
    return 1
  fi
}

scan_sensitive_filenames
scan_pem_blocks
scan_config_secrets

echo "Sensitive material scan passed."
