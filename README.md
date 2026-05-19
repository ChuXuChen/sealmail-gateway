# sealmail-gateway

## Backend build

Use the Maven Wrapper from the repository root so every developer and CI job uses
the same Maven baseline:

```bash
./mvnw -f sealmail-backend/pom.xml test
./mvnw -f sealmail-backend/pom.xml -DskipTests package
```

In restricted or disposable environments, keep Maven's user home and local
repository under `/tmp`:

```bash
MAVEN_USER_HOME=/tmp/sealmail-m2 \
./mvnw -Dmaven.repo.local=/tmp/sealmail-m2/repository -f sealmail-backend/pom.xml test
```

The backend build enforces Java 21, Maven 3.8.7 or newer, and dependency
convergence during `validate`.

## Local acceptance

Run these checks before handing off a refactoring phase:

```bash
MAVEN_USER_HOME=/tmp/sealmail-m2 \
./mvnw -Dmaven.repo.local=/tmp/sealmail-m2/repository -f sealmail-backend/pom.xml test

cd sealmail-frontend
npm run lint
npm run build

cd ..
./ops/scan-sensitive-material.sh
git status --short
```

Keep `paper/` outside product repository gates, do not edit historical Flyway
migrations, and do not commit real secrets, private keys, certificates,
keystores, or production credentials.

Each larger follow-up phase should land as an independently reviewable commit:
lock existing behavior with characterization tests first, keep backend flow work,
frontend page splits, and database changes in separate commits, and record any
verification commands that could not run. Do not regress the security defaults:
CORS origins stay allowlisted instead of `*`, production health details stay
hidden, production SQL logging stays off, and the old custom mail pipeline
abstractions must not return.

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
