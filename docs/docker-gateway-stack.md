# Docker Gateway Stack

This stack is intended for one gateway node. Run the same compose file on both
servers and change `.env.gateway` for each side.

## Files

```text
docker-compose.gateway.yml
.env.gateway.example
docker/postfix/Dockerfile
docker/postfix/entrypoint.sh
docker/sealmail-edge/Dockerfile
docker/sealmail-edge/entrypoint.sh
docker/frontend/Dockerfile
sealmail-backend/Dockerfile
ops/gateway-stack-up.sh
```

The stack runs:

```text
PostgreSQL
SealMail Backend
SealMail Frontend
Postfix
SealMail Edge
Mailpit
```

## First Run

On each server:

```bash
cp .env.gateway.example .env.gateway
mkdir -p runtime
```

Edit `.env.gateway`.

For server A:

```text
SEALMAIL_SITE=alpha
LOCAL_DOMAIN=alpha.sealmail.top
REMOTE_DOMAIN=beta.sealmail.top
POSTFIX_HOSTNAME=mx-alpha.sealmail.top
REMOTE_STANDARD_HOST=<server-b-public-ip>
EDGE_OUTBOUND_ROUTES=beta.sealmail.top=<server-b-public-ip>:2525
```

For server B:

```text
SEALMAIL_SITE=beta
LOCAL_DOMAIN=beta.sealmail.top
REMOTE_DOMAIN=alpha.sealmail.top
POSTFIX_HOSTNAME=mx-beta.sealmail.top
REMOTE_STANDARD_HOST=<server-a-public-ip>
EDGE_OUTBOUND_ROUTES=alpha.sealmail.top=<server-a-public-ip>:2525
```

Then start:

```bash
ops/gateway-stack-up.sh
```

The script validates the required secrets and then runs:

```bash
docker compose --env-file .env.gateway -f docker-compose.gateway.yml up -d --build
```

## Public Ports

Open only the ports you need:

```text
587   Postfix STARTTLS test entry
2525  SealMail Edge STARTTLS entry
2465  SealMail Edge implicit TLS entry
22    SSH
```

For a public VPS, restrict `587`, `2525`, and `2465` in the cloud firewall to
your peer server IP and your own admin IP where possible. The Postfix container
is configured for a two-domain lab route, not as an authenticated public
submission service.

Keep these bound to localhost or behind SSH tunnel:

```text
5433  PostgreSQL debug mapping
8080  SealMail API
8088  SealMail frontend
10025 SealMail content-filter SMTP debug mapping
1025  Mailpit SMTP debug mapping
8025  Mailpit UI
2727  Edge admin
```

The frontend is served by Nginx and proxies `/api/**` to `sealmail-backend:8080`
inside the Compose network. By default it is bound to `127.0.0.1:8088`; use an
SSH tunnel for remote administration:

```bash
ssh -L 8088:127.0.0.1:8088 user@server
```

Then open `http://localhost:8088`.

## Route Modes

Standard SMTP STARTTLS path:

```text
REMOTE_ROUTE_MODE=standard
REMOTE_STANDARD_HOST=<peer-public-ip-or-hostname>
REMOTE_STANDARD_PORT=587
```

GM Edge path:

```text
REMOTE_ROUTE_MODE=gm
EDGE_OUTBOUND_ROUTES=<remote-domain>=<peer-public-ip-or-hostname>:2525
```

The Edge TLS keystore/truststore is not generated automatically. Put the files
under `runtime/<site>/edge-secrets/` and set:

```text
EDGE_TLS_KEY_STORE=/run/secrets/edge/sealmail-gm-edge.p12
EDGE_TLS_KEY_STORE_PASSWORD=...
EDGE_TLS_TRUST_STORE=/run/secrets/edge/sealmail-gm-trust.p12
EDGE_TLS_TRUST_STORE_PASSWORD=...
```

For a quick trust-only lab, `EDGE_TLS_TRUST_ALL=true` can be used, but do not use
that setting outside an isolated test.

To simplify local two-node setup, generate the Edge SM2 TLS material with:

```bash
EDGE_STORE_PASS='<strong-password>' \
  ops/gm-edge-keystore.sh init \
  --site alpha \
  --dns mx-alpha.sealmail.top \
  --dns alpha.sealmail.top \
  --ip <alpha-public-ip>

EDGE_STORE_PASS='<strong-password>' \
  ops/gm-edge-keystore.sh init \
  --site beta \
  --dns mx-beta.sealmail.top \
  --dns beta.sealmail.top \
  --ip <beta-public-ip>
```

Then exchange only the public `*-edge.crt` files. Do not copy
`sealmail-gm-edge.p12` or `*-edge-sm2.key` between nodes. Import the peer
certificate into each node's truststore:

```bash
EDGE_STORE_PASS='<strong-password>' \
  ops/gm-edge-keystore.sh trust \
  --site alpha \
  --peer beta \
  --cert runtime/alpha/edge-secrets/beta-edge.crt

EDGE_STORE_PASS='<strong-password>' \
  ops/gm-edge-keystore.sh trust \
  --site beta \
  --peer alpha \
  --cert runtime/beta/edge-secrets/alpha-edge.crt
```

Each node's keystore must be different. The truststore is how the nodes trust
each other.

PostgreSQL data, the backend S/MIME keystore, Postfix TLS files, and Mailpit
data use Docker named volumes. This avoids first-run host UID/GID write
failures. Back up these volumes before deleting the stack:

```bash
docker volume ls | grep sealmail-gateway
```

## Notes

Postfix auto-generates a self-signed RSA TLS certificate if
the `postfix-tls` volume does not already contain `tls.crt` and `tls.key`. That
is enough to test STARTTLS encryption with `POSTFIX_REMOTE_TLS_SECURITY_LEVEL=encrypt`,
but it does not prove public CA trust or hostname verification.

After startup, configure SealMail domain policies in the UI/API:

```text
local domain:  LOCAL_DOMAIN, localDomain=true
remote domain: REMOTE_DOMAIN, localDomain=false, no deliveryHost/deliveryPort
```

Leaving the remote domain delivery route empty lets SealMail return outbound
mail to Postfix on `10027`, and Postfix then chooses either the standard TLS
transport or the Edge transport according to `REMOTE_ROUTE_MODE`.
