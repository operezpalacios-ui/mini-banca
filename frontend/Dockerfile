# Lección 14: imagen del frontend.
#   1) build: Node compila Angular -> HTML, JS y CSS "normales" en dist/
#   2) final: nginx, un servidor web pequeñísimo que solo sirve esos ficheros. En producción NO hay Node.

# ---------- Etapa 1: compilar ----------
FROM node:26-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
# npm ci: instala EXACTAMENTE las versiones del package-lock.json (reproducible). npm install podría cambiarlas.
RUN npm ci
COPY . .
RUN npm run build

# ---------- Etapa 2: servir con nginx ----------
FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=3 CMD wget -qO- http://localhost/ >/dev/null || exit 1
