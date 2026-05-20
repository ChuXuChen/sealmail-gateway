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
    ./mvnw -f sealmail-backend/pom.xml -pl sealmail-boot -am -DskipTests dependency:go-offline

COPY sealmail-backend sealmail-backend

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -f sealmail-backend/pom.xml -pl sealmail-boot -am -DskipTests package

FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home --home-dir /home/sealmail sealmail \
    && mkdir -p /var/lib/sealmail/keystore \
    && chown -R sealmail:sealmail /var/lib/sealmail

WORKDIR /app
COPY --from=build /workspace/sealmail-backend/sealmail-boot/target/sealmail-boot-*.jar /app/sealmail-boot.jar

USER sealmail
EXPOSE 8080 10025

ENTRYPOINT ["java", "-jar", "/app/sealmail-boot.jar"]
