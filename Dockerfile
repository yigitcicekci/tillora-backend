FROM maven:3.9.16-eclipse-temurin-25-alpine AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

FROM eclipse-temurin:25.0.3_9-jre-alpine
RUN apk add --no-cache curl \
    && addgroup -S -g 10001 tillora \
    && adduser -S -D -H -u 10001 -G tillora tillora
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/tillora-backend.jar /app/app.jar
USER 10001:10001
EXPOSE 8080 8081
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 CMD ["curl", "--fail", "--silent", "--show-error", "http://127.0.0.1:8081/actuator/health/readiness"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
