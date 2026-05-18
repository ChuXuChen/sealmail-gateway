# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY sealmail-backend/pom.xml sealmail-backend/pom.xml
COPY sealmail-backend/sealmail-app/pom.xml sealmail-backend/sealmail-app/pom.xml
COPY sealmail-backend/sealmail-boot/pom.xml sealmail-backend/sealmail-boot/pom.xml
COPY sealmail-backend/sealmail-domain/pom.xml sealmail-backend/sealmail-domain/pom.xml
COPY sealmail-backend/sealmail-edge/pom.xml sealmail-backend/sealmail-edge/pom.xml
COPY sealmail-backend/sealmail-infra/pom.xml sealmail-backend/sealmail-infra/pom.xml
COPY sealmail-backend/sealmail-web/pom.xml sealmail-backend/sealmail-web/pom.xml

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -f sealmail-backend/pom.xml -pl sealmail-edge -am -DskipTests dependency:go-offline

COPY sealmail-backend sealmail-backend

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -f sealmail-backend/pom.xml -pl sealmail-edge -am -DskipTests package \
    && ./mvnw -f sealmail-backend/sealmail-edge/pom.xml -DskipTests dependency:copy-dependencies \
        -DincludeScope=runtime \
        -DoutputDirectory=/workspace/sealmail-backend/sealmail-edge/target/dependency

FROM eclipse-temurin:21-jre

RUN useradd --system --uid 10002 --create-home --home-dir /home/sealmail-edge sealmail-edge \
    && mkdir -p /app /run/secrets/edge \
    && chown -R sealmail-edge:sealmail-edge /app /run/secrets/edge

WORKDIR /app
COPY --from=build /workspace/sealmail-backend/sealmail-edge/target/sealmail-edge-*.jar /app/sealmail-edge.jar
COPY --from=build /workspace/sealmail-backend/sealmail-edge/target/dependency /app/lib
COPY docker/sealmail-edge/entrypoint.sh /usr/local/bin/sealmail-edge-entrypoint

USER sealmail-edge
EXPOSE 2525 2465 2526 2727

ENTRYPOINT ["/usr/local/bin/sealmail-edge-entrypoint"]
