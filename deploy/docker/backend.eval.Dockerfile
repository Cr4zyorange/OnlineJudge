# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f backend/pom.xml -q -Dmaven.test.skip=true dependency:go-offline

COPY backend backend
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f backend/pom.xml -Dmaven.test.skip=true package

FROM docker:27.5.1-cli
WORKDIR /opt/onlinejudge

RUN apk add --no-cache openjdk21-jre-headless wget

COPY --from=build /workspace/backend/target/onlinejudge-backend-0.1.0-SNAPSHOT.jar app.jar

RUN mkdir -p /opt/onlinejudge/data/uploads /tmp/onlinejudge-sandbox

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/opt/onlinejudge/app.jar", "--spring.config.additional-location=classpath:/application-compose.properties"]
