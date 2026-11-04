# syntax=docker/dockerfile:1

# ---------- build ----------
FROM node:24-alpine AS build
WORKDIR /app
RUN npm install -g pnpm@10.31.0
COPY package.json pnpm-lock.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm/store pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

# ---------- runtime（非 root，监听 8080）----------
FROM nginxinc/nginx-unprivileged:stable-alpine
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/snippets/ /etc/nginx/snippets/
COPY --from=build /app/dist /usr/share/nginx/html
ENV BACKEND_URL=http://backend:8080
EXPOSE 8080
