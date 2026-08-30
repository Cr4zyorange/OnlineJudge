FROM maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e AS build
WORKDIR /workspace

COPY services/course/pom.xml services/course/pom.xml
RUN mvn -f services/course/pom.xml -q -Dmaven.test.skip=true dependency:go-offline

COPY services/course services/course
RUN mvn -f services/course/pom.xml -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre@sha256:7a65df4b22d2de92d4e04056e884f3b9122d70b21e2847fd66084278bd0ce037
WORKDIR /opt/onlinejudge-course

ARG GIT_SHA
ARG IMAGE_SOURCE=https://github.com/Cr4zyorange/OnlineJudge
LABEL org.opencontainers.image.revision="$GIT_SHA" \
      org.opencontainers.image.version="$GIT_SHA" \
      org.opencontainers.image.source="$IMAGE_SOURCE"

RUN command -v wget >/dev/null \
    && groupadd --system --gid 10002 onlinejudge \
    && useradd --system --uid 10002 --gid 10002 --home-dir /opt/onlinejudge-course --shell /usr/sbin/nologin onlinejudge

COPY --from=build --chown=10002:10002 /workspace/services/course/target/onlinejudge-course-service-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8082
USER 10002:10002
ENTRYPOINT ["java", "-jar", "/opt/onlinejudge-course/app.jar", "--spring.config.additional-location=classpath:/application-compose.properties"]
