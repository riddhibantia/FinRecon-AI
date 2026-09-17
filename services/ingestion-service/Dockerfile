# P12: ingestion-service image. Not built on machines without Docker;
# CI builds every image (see .github/workflows/ci.yml).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY settings.gradle build.gradle gradle.properties ./
COPY services/ingestion-service ./services/ingestion-service/
RUN gradle :services:ingestion-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/services/ingestion-service/build/libs/*.jar app.jar
# Flyway loads filesystem:db/migrations relative to the working directory.
COPY db/migrations ./db/migrations/
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
