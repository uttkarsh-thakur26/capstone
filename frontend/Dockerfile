# syntax=docker/dockerfile:1

# Build: type check and bundle the SPA.
FROM node:24.21.0-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-fund --no-audit
COPY . .
RUN npm run build

# Runtime: static files behind nginx, which also proxies /api so both share one origin (DEP-1).
FROM nginxinc/nginx-unprivileged:1.30.5-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
