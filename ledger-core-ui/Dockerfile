FROM node:22-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:alpine

ENV API_UPSTREAM=http://ledger-service:5555
ENV NGINX_ENVSUBST_FILTER=^API_UPSTREAM$

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/ledger-core-ui/browser /usr/share/nginx/html

EXPOSE 8080
