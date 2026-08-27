# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e AS build
WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f backend/pom.xml -q -Dmaven.test.skip=true dependency:go-offline

COPY backend backend
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f backend/pom.xml -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre@sha256:7a65df4b22d2de92d4e04056e884f3b9122d70b21e2847fd66084278bd0ce037
WORKDIR /opt/onlinejudge

ARG GIT_SHA
ARG IMAGE_SOURCE=https://github.com/Cr4zyorange/OnlineJudge
LABEL org.opencontainers.image.revision="$GIT_SHA" \
      org.opencontainers.image.version="$GIT_SHA" \
      org.opencontainers.image.source="$IMAGE_SOURCE"

RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && groupadd --system --gid 10001 onlinejudge \
    && useradd --system --uid 10001 --gid 10001 --home-dir /opt/onlinejudge --shell /usr/sbin/nologin onlinejudge \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build --chown=10001:10001 /workspace/backend/target/onlinejudge-backend-0.1.0-SNAPSHOT.jar app.jar

RUN install -d -o 10001 -g 10001 /opt/onlinejudge/data /opt/onlinejudge/data/uploads

EXPOSE 8080

USER 10001:10001

ENTRYPOINT ["java", "-jar", "/opt/onlinejudge/app.jar", "--spring.config.additional-location=classpath:/application-compose.properties"]
