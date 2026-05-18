# syntax=docker/dockerfile:1.7

FROM debian:bookworm-slim

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        openssl \
        postfix \
        libsasl2-modules \
    && rm -rf /var/lib/apt/lists/*

COPY docker/postfix/entrypoint.sh /usr/local/bin/sealmail-postfix-entrypoint

EXPOSE 25 10026 10027 2530

ENTRYPOINT ["/usr/local/bin/sealmail-postfix-entrypoint"]
