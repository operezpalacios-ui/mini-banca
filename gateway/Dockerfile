# La "receta" de la imagen Docker del gateway. La misma idea que la del orquestador (lección 14):
#   1) build: imagen GRANDE con Maven y el JDK que compila el .jar
#   2) final: imagen PEQUEÑA solo con el JRE y el .jar

# ---------- Etapa 1: compilar ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# -DskipTests: los tests ya se ejecutan en el CI ANTES de construir la imagen.
RUN mvn -B -q -DskipTests package

# ---------- Etapa 2: imagen final ----------
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER app

EXPOSE 8082

HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8082/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
