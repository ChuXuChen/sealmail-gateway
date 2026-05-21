# Docker Gateway Stack

Use `ops/gateway-stack-up.sh` as the deployment entry point. The script creates
`.env.gateway` on first run, fills random secrets, prepares `runtime/<site>/...`,
checks the host, and runs `docker compose up -d --build` against
`docker-compose.gateway.yml`.

The same compose file runs on both gateway nodes. Each server keeps its own
`.env.gateway`.

## Quick Start

Server A:

```bash
ops/gateway-stack-up.sh \
  --site alpha \
  --local-domain alpha.sealmail.top \
  --remote-domain beta.sealmail.top \
  --remote-host <server-b-public-ip-or-hostname>
```

Server B:

```bash
ops/gateway-stack-up.sh \
  --site beta \
  --local-domain beta.sealmail.top \
  --remote-domain alpha.sealmail.top \
  --remote-host <server-a-public-ip-or-hostname>
```

If stdin is interactive, running `ops/gateway-stack-up.sh` without arguments will
prompt for the missing first-run values. In automation, pass the values as flags
or pre-create `.env.gateway`.

Default mode is `standard`: Postfix sends peer-domain mail over SMTP STARTTLS to
`REMOTE_STANDARD_HOST:REMOTE_STANDARD_PORT`. GM Edge routing stays available but
is not the default.

Production mode also activates Spring's `prod` profile by setting
`SPRING_PROFILES_ACTIVE=postgres,prod`. That keeps the name aligned with backend
runtime behavior instead of only changing Compose profiles.

## What Runs

Core services:

```text
PostgreSQL
SealMail Backend
SealMail Frontend
Postfix
```

Debug profile services:

```text
Mailpit
PostgreSQL localhost proxy
Backend HTTP localhost proxy
Backend SMTP localhost proxy
```

Optional profile services:

```text
gm     SealMail Edge plus public GM STARTTLS/implicit TLS proxies
gm-debug  Edge admin localhost proxy, enabled by the script with debug+gm
https  Caddy HTTPS reverse proxy for the frontend
```

The script enables the debug profile by default. Use production mode to skip
Mailpit and debug proxies:

```bash
ops/gateway-stack-up.sh --production
```

Production mode requires `LOCAL_DELIVERY_MODE=none` or `LOCAL_DELIVERY_MODE=smtp`
because Mailpit is not started.

`LOCAL_DELIVERY_MODE=none` keeps the stack bootable without Mailpit but maps the
local domain to a Postfix error transport. Use `LOCAL_DELIVERY_MODE=smtp` when
the node should deliver accepted local-domain mail to a downstream mailbox
service.

## Ports

Published in both modes:

```text
2527  Postfix SMTP ingress, mapped to container port 25
8088  Frontend, bound by SEALMAIL_FRONTEND_BIND by default
```

`POSTFIX_PUBLIC_SMTP_PORT` is a lab or gateway ingress port. It is not an RFC
submission service: the stack does not enable SMTP AUTH/SASL just because a
host port is published.

Published only when the GM profile is enabled by `REMOTE_ROUTE_MODE=gm` or
`SEALMAIL_GM_EDGE_PUBLIC_ENABLED=true`:

```text
2525  SealMail Edge STARTTLS entry
2465  SealMail Edge implicit TLS entry
```

Published only when `SEALMAIL_HTTPS_ENABLED=true`:

```text
80    Caddy HTTP challenge/redirect entry
443   Caddy HTTPS entry
```

Published only in debug mode, all bound to `127.0.0.1`:

```text
5433   PostgreSQL
8080   Backend API
10025  Backend SMTP content-filter entry
1025   Mailpit SMTP
8025   Mailpit UI, server-side default
```

Published only when debug and GM are both enabled, bound to `127.0.0.1`:

```text
2727   Edge admin
```

For remote administration, keep the frontend bound to localhost and tunnel it:

```bash
ssh -L 8088:127.0.0.1:8088 user@alpha-server
ssh -L 8089:127.0.0.1:8088 user@beta-server
```

Then open `http://localhost:8088` for alpha and `http://localhost:8089` for
beta. The default CORS allowlist includes both local tunnel origins.

For the debug Mailpit UI, keep the server-side port at 8025 and use different
local tunnel ports from your workstation:

```bash
ssh -L 8025:127.0.0.1:8025 user@alpha-server
ssh -L 8026:127.0.0.1:8025 user@beta-server
```

## HTTPS Front Door

The built-in frontend remains an HTTP service and defaults to
`127.0.0.1:8088`, which is appropriate for SSH tunnel administration. For a
public HTTPS endpoint, enable the bundled Caddy profile:

```bash
ops/gateway-stack-up.sh \
  --production \
  --https-host admin.alpha.sealmail.top
```

This sets `SEALMAIL_HTTPS_ENABLED=true`, starts the `gateway-https` service, and
uses Caddy's ACME flow for `SEALMAIL_PUBLIC_HOST`. Make sure DNS for the host
points to the server and ports `80` and `443` are reachable from the public
internet. If a corporate or cloud reverse proxy terminates TLS elsewhere, keep
`SEALMAIL_HTTPS_ENABLED=false`, keep the frontend bound to localhost or a
private interface, and point the external proxy at `frontend:80` or
`127.0.0.1:8088`.

For the bundled Caddy path, use a hostname such as
`admin.alpha.sealmail.top`, not a URL with a path or a non-standard port. Caddy
listens on container ports `80` and `443`; `SEALMAIL_HTTPS_HTTP_PORT` and
`SEALMAIL_HTTPS_HTTPS_PORT` only change the host-side published ports.

When HTTPS is enabled through the script, localhost CORS and CRL defaults are
rewritten to the public origin if they were still untouched. If you use an
external proxy instead, set `SEALMAIL_CORS_ALLOWED_ORIGINS` and
`SEALMAIL_CA_CRL_BASE_URL` yourself.

For certificate operations:

```text
DNS        SEALMAIL_PUBLIC_HOST must resolve to this server.
Firewall   Public ports 80 and 443 must reach the host for ACME HTTP-01.
Email      Uncomment the global email block in docker/caddy/Caddyfile if you
           want ACME expiry/rate-limit contact mail.
Staging    Uncomment the acme_ca staging line in docker/caddy/Caddyfile before
           first public testing, then switch back to production ACME.
Backup     Back up caddy-data with the other named volumes; it contains account
           and certificate state.
Renewal    Caddy renews automatically. Check gateway-https logs after DNS,
           firewall, or certificate changes.
```

## Route Modes

Standard SMTP STARTTLS path:

```text
REMOTE_ROUTE_MODE=standard
REMOTE_STANDARD_HOST=<peer-public-ip-or-hostname>
REMOTE_STANDARD_PORT=2527
```

The default Postfix standard path uses STARTTLS encryption
(`POSTFIX_REMOTE_TLS_SECURITY_LEVEL=encrypt`). That protects against passive
inspection but does not prove the peer identity, so it is not a complete
anti-MITM configuration by itself.

In debug mode, the public Postfix ingress accepts both `LOCAL_DOMAIN` and
`REMOTE_DOMAIN` so two-node relay tests can be driven directly through the
gateway port. In production mode, the default public ingress accepts only
`LOCAL_DOMAIN`; backend outbound reinjection on the internal Postfix port still
uses `REMOTE_DOMAIN` for the standard or GM peer route. Set
`POSTFIX_PUBLIC_RELAY_REMOTE_DOMAIN=true` only when the public SMTP port is
firewalled to trusted peers.

For production, choose one of these stricter Postfix policies:

```text
# Private/public CA trust plus hostname verification.
# Place peer-ca.pem under runtime/<site>/standard-tls/.
POSTFIX_REMOTE_TLS_CA_FILE=/run/secrets/standard-tls/peer-ca.pem
POSTFIX_REMOTE_TLS_POLICY_LEVEL=secure
POSTFIX_REMOTE_TLS_POLICY_MATCH=mx-beta.sealmail.top
```

```text
# Certificate pinning.
POSTFIX_REMOTE_TLS_POLICY_LEVEL=fingerprint
POSTFIX_REMOTE_TLS_FINGERPRINT_DIGEST=sha256
POSTFIX_REMOTE_TLS_POLICY_FINGERPRINT=<sha256-peer-cert-fingerprint>
```

For fingerprint mode, the script writes the Postfix policy as
`fingerprint match=<sha256-peer-cert-fingerprint>`.

The deployment script validates mounted CA paths and warns in production when a
standard route does not use `secure` or `fingerprint`.

No peer route:

```text
REMOTE_ROUTE_MODE=none
REMOTE_DOMAIN=
```

If `REMOTE_DOMAIN` is left populated in `none` mode, the script warns and the
Postfix entrypoint does not add that domain to public or internal relay domains.

GM Edge path:

```text
REMOTE_ROUTE_MODE=gm
SEALMAIL_GM_EDGE_PUBLIC_ENABLED=true
EDGE_OUTBOUND_ROUTES=<remote-domain>=<peer-public-ip-or-hostname>:2525
EDGE_TLS_KEY_STORE=/run/secrets/edge/sealmail-gm-edge.p12
EDGE_TLS_KEY_STORE_PASSWORD=<local-edge-store-password>
EDGE_TLS_TRUST_STORE=/run/secrets/edge/sealmail-gm-trust.p12
EDGE_TLS_TRUST_STORE_PASSWORD=<local-edge-store-password>
```

For an isolated lab only, `EDGE_TLS_TRUST_ALL=true` can be used to skip the GM
truststore requirement. Do not use that setting for production or exposed test
environments.

Generate local GM material on each node:

```bash
EDGE_STORE_PASS='<strong-password>' \
  ops/gm-edge-keystore.sh init \
  --site alpha \
  --dns mx-alpha.sealmail.top \
  --dns alpha.sealmail.top \
  --ip <alpha-public-ip>
```

Exchange only the public `*-edge.crt` files. Import the peer certificate:

```bash
EDGE_STORE_PASS='<strong-password>' \
  ops/gm-edge-keystore.sh trust \
  --site alpha \
  --peer beta \
  --cert runtime/alpha/edge-secrets/beta-edge.crt
```

Do not copy `sealmail-gm-edge.p12` or private keys between nodes.

## Restart And Upgrade

Reconcile configuration and restart changed containers:

```bash
ops/gateway-stack-up.sh
```

Upgrade after pulling new code:

```bash
git pull
ops/gateway-stack-up.sh
```

Check status:

```bash
ops/gateway-stack-up.sh --dry-run
docker compose --env-file .env.gateway -f docker-compose.gateway.yml --profile debug ps
docker compose --env-file .env.gateway -f docker-compose.gateway.yml --profile debug logs -f
```

The dry-run output shows which profiles the script will use. Omit `--profile
debug` for production-mode stacks; add `--profile gm` or `--profile https` only
when those features are enabled.

Stop containers without deleting volumes:

```bash
docker compose --env-file .env.gateway -f docker-compose.gateway.yml --profile debug down
```

## Backup And Restore

Named volumes use the compose project name `sealmail-gateway-<site>`.

Back up the persistent volumes:

```bash
site="$(awk -F= '$1=="SEALMAIL_SITE"{print $2}' .env.gateway)"
mkdir -p "runtime/$site/backups"

docker run --rm \
  -v "sealmail-gateway-${site}_postgres-data:/volume:ro" \
  -v "$PWD/runtime/$site/backups:/backup" \
  alpine tar czf /backup/postgres-data.tgz -C /volume .

docker run --rm \
  -v "sealmail-gateway-${site}_backend-keystore:/volume:ro" \
  -v "$PWD/runtime/$site/backups:/backup" \
  alpine tar czf /backup/backend-keystore.tgz -C /volume .

docker run --rm \
  -v "sealmail-gateway-${site}_postfix-tls:/volume:ro" \
  -v "$PWD/runtime/$site/backups:/backup" \
  alpine tar czf /backup/postfix-tls.tgz -C /volume .

docker run --rm \
  -v "sealmail-gateway-${site}_caddy-data:/volume:ro" \
  -v "$PWD/runtime/$site/backups:/backup" \
  alpine tar czf /backup/caddy-data.tgz -C /volume .
```

If HTTPS is disabled, the `caddy-data` volume may not exist. If debug Mailpit
data matters, back up `mailpit-data` the same way.

Restore a volume after stopping the stack:

```bash
site="$(awk -F= '$1=="SEALMAIL_SITE"{print $2}' .env.gateway)"
docker compose --env-file .env.gateway -f docker-compose.gateway.yml --profile debug down

docker volume create "sealmail-gateway-${site}_backend-keystore"
docker run --rm \
  -v "sealmail-gateway-${site}_backend-keystore:/volume" \
  -v "$PWD/runtime/$site/backups:/backup:ro" \
  alpine sh -c 'cd /volume && tar xzf /backup/backend-keystore.tgz'

ops/gateway-stack-up.sh
```

Restore PostgreSQL before starting services that depend on it.

## Post-Start Checks

```bash
docker compose --env-file .env.gateway -f docker-compose.gateway.yml --profile debug ps
curl -fsS http://127.0.0.1:8088/ >/dev/null
curl -fsS http://127.0.0.1:8080/actuator/health
```

The direct backend health URL is published only in debug mode. In production,
use `docker compose ps` health status or check through the frontend/reverse
proxy. Compose healthchecks are defined for PostgreSQL, backend, frontend,
Postfix, and SealMail Edge when GM is enabled. `depends_on` waits for backend
and Postfix healthchecks where readiness matters.

After both nodes are up, configure SealMail domain policies in the UI/API:

```text
local domain:  LOCAL_DOMAIN, localDomain=true
remote domain: REMOTE_DOMAIN, localDomain=false, no deliveryHost/deliveryPort
```

Leaving the remote domain delivery route empty lets SealMail return outbound
mail to Postfix on `10027`; Postfix then chooses `standard`, `gm`, or `none`
according to `REMOTE_ROUTE_MODE`.
