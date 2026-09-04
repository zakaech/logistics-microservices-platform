# ---------------------------------------------------------------------------
# Build context is this directory:
#   docker build -f frontend/Dockerfile frontend
# ---------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

# Copy the manifests first so the dependency layer is rebuilt only when they change,
# not on every source edit.
COPY package.json package-lock.json ./
# --legacy-peer-deps: npm 10's peer resolver crashes on the vitest dependency graph
# that Angular 21 scaffolds with (arborist "edgesOut" error).
RUN npm ci --legacy-peer-deps

COPY . .
RUN npm run build

# ---------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# 127.0.0.1, not localhost: /etc/hosts maps localhost to both 127.0.0.1 and ::1, wget tries
# the IPv6 address first, and nginx binds IPv4 only - so `localhost` here is a connection
# refused against a server that is serving perfectly well.
HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=10s \
  CMD wget -qO- http://127.0.0.1/ >/dev/null 2>&1 || exit 1
