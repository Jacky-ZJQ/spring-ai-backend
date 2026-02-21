FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre-jammy

ENV TZ=Asia/Shanghai
ENV JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=70 -XX:InitialRAMPercentage=20 -XX:MinRAMPercentage=20 -XX:+UseStringDeduplication -Dfile.encoding=UTF-8"

RUN useradd -r -u 10001 -m appuser \
    && mkdir -p /opt/app /data \
    && chown -R appuser:appuser /opt/app /data

COPY --from=build /workspace/target/*.jar /opt/app/app.jar

WORKDIR /data
USER appuser

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_TOOL_OPTIONS -jar /opt/app/app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}"]
