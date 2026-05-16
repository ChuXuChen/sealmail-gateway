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
