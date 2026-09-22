# Monolith image: one Spring Boot app serving all backend APIs on :8080.
# CI builds every image (see .github/workflows/ci.yml).
FROM gradle:8.9-jdk21 AS build
WORKDIR /src
COPY settings.gradle build.gradle gradle.properties ./
COPY services/finrecon-app ./services/finrecon-app/
RUN gradle :services:finrecon-app:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/services/finrecon-app/build/libs/*.jar app.jar
# Flyway loads filesystem:db/migrations relative to the working directory.
COPY db/migrations ./db/migrations/
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
