FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre

WORKDIR /app
RUN useradd --system --create-home --uid 10001 appuser
COPY --from=build --chown=appuser:appuser /workspace/target/postapi-*.jar /app/app.jar
USER appuser

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
