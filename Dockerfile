# ---------- Etapa 1: build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
# Capa de dependencias cacheable: solo se invalida si cambia el pom
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Las pruebas se ejecutan en el pipeline (etapas dedicadas con quality gates);
# aquí solo se empaqueta para mantener la imagen reproducible y rápida de construir.
RUN mvn -B -q package -DskipTests

# ---------- Etapa 2: runtime ----------
FROM eclipse-temurin:17-jre-alpine
LABEL org.opencontainers.image.title="orders-api" \
      org.opencontainers.image.description="API de órdenes de compra - Reto QA"
WORKDIR /app
# Usuario sin privilegios
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/orders-api-*.jar /app/app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
