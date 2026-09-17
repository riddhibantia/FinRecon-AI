# P12: reconciliation-service image. Not built on machines without
# Docker; CI builds every image (see .github/workflows/ci.yml).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY settings.gradle build.gradle gradle.properties ./
COPY services/reconciliation-service ./services/reconciliation-service/
RUN gradle :services:reconciliation-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/services/reconciliation-service/build/libs/*.jar app.jar
# Flyway loads filesystem:db/migrations relative to the working directory.
COPY db/migrations ./db/migrations/
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
