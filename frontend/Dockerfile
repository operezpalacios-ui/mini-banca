# Lección 14: imagen del frontend.
#   1) build: Node compila Angular -> HTML, JS y CSS "normales" en dist/
#   2) final: nginx, un servidor web pequeñísimo que solo sirve esos ficheros. En producción NO hay Node.

# ---------- Etapa 1: compilar ----------
FROM node:24-alpine AS build
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
# 127.0.0.1 y NO "localhost": en esta imagen "localhost" apunta primero a la dirección IPv6 (::1),
# y nginx solo escucha en IPv4 -> el chequeo fallaba y Docker marcaba el contenedor como "unhealthy".
HEALTHCHECK --interval=10s --timeout=3s --retries=3 CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
