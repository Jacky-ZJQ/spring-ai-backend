# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /build

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && addgroup --system spring \
    && adduser --system --ingroup spring spring

COPY --from=builder /build/target/spring-ai-backend-*.jar /app/app.jar

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=30 -Dfile.encoding=UTF-8"

EXPOSE 8080
USER spring

HEALTHCHECK --interval=30s --timeout=5s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '\"status\":\"UP\"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
