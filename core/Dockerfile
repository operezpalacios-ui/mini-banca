# Lección 14: la "receta" para construir la imagen Docker del core.
# Dos etapas ("multi-stage"):
#   1) build: una imagen GRANDE con Maven y el JDK, que compila y genera el .jar
#   2) final: una imagen PEQUEÑA solo con el JRE y el .jar  (sin Maven, sin código fuente)
# Así la imagen que se despliega pesa mucho menos y tiene menos cosas que puedan tener fallos de seguridad.

# ---------- Etapa 1: compilar ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Primero SOLO el pom.xml y descargamos las librerías. Docker guarda cada paso en "capas":
# si mañana cambias código pero no el pom, este paso NO se repite (se reutiliza la capa) -> mucho más rápido.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# -DskipTests: los tests ya se ejecutan en GitHub Actions ANTES de construir la imagen.
RUN mvn -B -q -DskipTests package

# ---------- Etapa 2: imagen final ----------
FROM eclipse-temurin:21-jre-alpine

# Nunca como root: si alguien lograra colarse en la aplicación, no sería "administrador" del contenedor.
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER app

EXPOSE 8080

# Docker pregunta cada 10 s "¿estás vivo?" a /actuator/health/liveness (lección 13)
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

# MaxRAMPercentage: que Java use como mucho el 75 % de la memoria que le dé el contenedor
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
