# P12: gateway-service image. Gradle builds the bootJar, then only the
# JRE + jar ship. Not built on machines without Docker; CI builds every
# image (see .github/workflows/ci.yml).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY settings.gradle build.gradle gradle.properties ./
COPY services/gateway-service ./services/gateway-service/
RUN gradle :services:gateway-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/services/gateway-service/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
