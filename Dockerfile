# syntax=docker/dockerfile:1

# --- Build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :lims-core-service-app:bootJar --no-daemon

# --- Runtime stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
# Run as a non-root user
RUN useradd -r -u 1001 lims
COPY --from=build /workspace/lims-core-service-app/build/libs/lims-core-service.jar /app/app.jar
USER lims
EXPOSE 11000
# Secrets come from the environment (see SECURITY.md) — never baked into the image.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
