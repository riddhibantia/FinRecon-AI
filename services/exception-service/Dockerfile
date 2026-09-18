# P12: exception-service image. Not built on machines without Docker;
# CI builds every image (see .github/workflows/ci.yml).
FROM gradle:8.9-jdk21 AS build
WORKDIR /src
COPY settings.gradle build.gradle gradle.properties ./
COPY services/exception-service ./services/exception-service/
RUN gradle :services:exception-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/services/exception-service/build/libs/*.jar app.jar
# Flyway loads filesystem:db/migrations relative to the working directory.
COPY db/migrations ./db/migrations/
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
