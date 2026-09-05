# ---------------------------------------------------------------------------
# Le contexte de build est ce répertoire :
#   docker build -f frontend/Dockerfile frontend
# ---------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

# Copier d'abord les manifestes, pour que la couche de dépendances ne soit reconstruite
# que lorsqu'ils changent, et non à chaque modification de source.
COPY package.json package-lock.json ./
# --legacy-peer-deps : le résolveur de peer dependencies de npm 10 plante sur le graphe
# de dépendances de vitest généré par Angular 21 (erreur arborist « edgesOut »).
RUN npm ci --legacy-peer-deps

COPY . .
RUN npm run build

# ---------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# 127.0.0.1, et non localhost : /etc/hosts fait pointer localhost vers 127.0.0.1 ET ::1,
# wget tente d'abord l'adresse IPv6, et nginx n'écoute qu'en IPv4 - si bien que `localhost`
# ici donne un refus de connexion face à un serveur qui sert pourtant parfaitement.
HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=10s \
  CMD wget -qO- http://127.0.0.1/ >/dev/null 2>&1 || exit 1
