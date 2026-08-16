# --- Build stage ---
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Run stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S jobfit && adduser -S jobfit -G jobfit
COPY --from=build /app/target/jobfit-backend-*.jar app.jar
USER jobfit
EXPOSE 8080
# ECS/Fargate and `docker compose` both read this - matches the
# /actuator/health/liveness probe wired up in application.yml.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1
# Container-aware JVM flags: respect the cgroup memory limit ECS sets on the
# task (-XX:MaxRAMPercentage) instead of guessing the host's total RAM.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
