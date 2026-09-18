# P12: reporting-service image. Not built on machines without Docker;
# CI builds every image (see .github/workflows/ci.yml).
FROM gradle:8.9-jdk21 AS build
WORKDIR /src
COPY settings.gradle build.gradle gradle.properties ./
COPY services/reporting-service ./services/reporting-service/
RUN gradle :services:reporting-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/services/reporting-service/build/libs/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
