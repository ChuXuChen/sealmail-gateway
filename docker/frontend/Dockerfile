# syntax=docker/dockerfile:1.7

FROM node:22-bookworm-slim AS build
WORKDIR /workspace/sealmail-frontend

COPY sealmail-frontend/package.json sealmail-frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY sealmail-frontend ./
RUN npm run build

FROM nginx:1.27-alpine

COPY docker/frontend/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /workspace/sealmail-frontend/dist /usr/share/nginx/html

EXPOSE 80
