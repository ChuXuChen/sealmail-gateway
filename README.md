# sealmail-gateway

## Backend build

Use the Maven Wrapper from the repository root so every developer and CI job uses
the same Maven baseline:

```bash
./mvnw -f sealmail-backend/pom.xml test
./mvnw -f sealmail-backend/pom.xml -DskipTests package
```

The backend build enforces Java 21, Maven 3.8.7 or newer, and dependency
convergence during `validate`.

## Local Docker runtime

For local Docker runs, copy `.env.example` to `.env`, replace all passwords and
secrets, then create the host keystore directory:

```bash
mkdir -p runtime/keystore
chmod 700 runtime/keystore
# Set SEALMAIL_UID=$(id -u) and SEALMAIL_GID=$(id -g) in .env.
docker compose up --build
```

The backend stores certificate private keys in
`runtime/keystore/sealmail-cert-keys.p12` through a container bind mount. Back up
that file together with PostgreSQL. See `docs/docker-local-keystore.md`.

## Flyway migrations

Flyway Maven goals are opt-in for the infrastructure module. Provide the target
database explicitly through environment variables or equivalent `-D` properties:

```bash
SEALMAIL_FLYWAY_URL=jdbc:postgresql://localhost:5432/sealmail \
SEALMAIL_FLYWAY_USER=sealmail \
SEALMAIL_FLYWAY_PASSWORD=change-me \
./mvnw -f sealmail-backend/pom.xml -pl sealmail-infra -Pflyway-migrate flyway:validate
```

Use `flyway:migrate` only against the intended environment. The default Maven
build does not connect to PostgreSQL.
