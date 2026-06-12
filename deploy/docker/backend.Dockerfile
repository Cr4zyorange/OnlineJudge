FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
RUN mvn -f backend/pom.xml -q -DskipTests dependency:go-offline

COPY backend backend
COPY database database
RUN mvn -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /opt/onlinejudge

RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/backend/target/onlinejudge-backend-0.1.0-SNAPSHOT.jar app.jar
COPY --from=build /workspace/database /opt/onlinejudge/database

RUN mkdir -p /opt/onlinejudge/data/uploads

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/opt/onlinejudge/app.jar", "--spring.config.additional-location=classpath:/application-compose.properties"]
