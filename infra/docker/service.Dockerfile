# syntax=docker/dockerfile:1
#
# Copyright (c) 2026 Jyotiprakash Parida
# SPDX-License-Identifier: MIT
#
# One multi-stage Dockerfile for every Spring Boot service in the monorepo.
#   docker build -f infra/docker/service.Dockerfile --build-arg MODULE=account-service .
#
ARG MODULE

# ---------- Build stage -------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
ARG MODULE
WORKDIR /workspace

# Copy POMs first so the dependency layer is cached until a POM changes.
COPY pom.xml ./
COPY common-security/pom.xml common-security/
COPY api-gateway/pom.xml api-gateway/
COPY account-service/pom.xml account-service/
COPY transfer-service/pom.xml transfer-service/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl "${MODULE}" -am dependency:go-offline -DskipTests

COPY common-security/src common-security/src
COPY ${MODULE}/src ${MODULE}/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl "${MODULE}" -am package -DskipTests \
 && cp "${MODULE}"/target/"${MODULE}"-*.jar /workspace/app.jar \
 && java -Djarmode=tools -jar /workspace/app.jar extract --layers --launcher --destination /workspace/extracted

# ---------- Runtime stage -----------------------------------------------------
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Non-root, no shell login, no home directory writes.
RUN addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app -s /sbin/nologin app

COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

USER 10001:10001
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom"

HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/liveness >/dev/null 2>&1 || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
