# Docker Local Keystore

SealMail stores certificate metadata in PostgreSQL and stores certificate private
keys in a PKCS12 keystore. The database only keeps a reference such as:

```text
keystore:certificate:<certificate-thumbprint>
```

For local Docker runs, the keystore must be mounted from the host so it survives
container recreation.

## Layout

```text
sealmail-gateway/
  docker-compose.yml
  .env
  runtime/
    keystore/
      sealmail-cert-keys.p12
```

`runtime/` is ignored by Git and must not be committed.

## First Run

Create the host directory:

```bash
mkdir -p runtime/keystore
chmod 700 runtime/keystore
```

Create a local `.env` from `.env.example` and replace all password/secret values.
Docker Compose `.env` files use `KEY=value`; do not use `export`.

Set `SEALMAIL_UID` and `SEALMAIL_GID` to your host user so the backend container
can write to the bind-mounted keystore directory:

```bash
id -u
id -g
```

Start the stack:

```bash
docker compose up --build
```

The backend sees this path inside the container:

```text
/var/lib/sealmail/keystore/sealmail-cert-keys.p12
```

The file is persisted on the host under:

```text
runtime/keystore/sealmail-cert-keys.p12
```

## Backup Rule

Back up PostgreSQL and `runtime/keystore/sealmail-cert-keys.p12` together.
Restoring only the database leaves certificate rows with private-key references
that cannot be resolved.

## Existing In-Memory CA Material

CA records created before `SEALMAIL_KEYSTORE_PATH` was configured may have
database references but no recoverable private key. Recreate those CAs after the
Docker keystore mount is active.
