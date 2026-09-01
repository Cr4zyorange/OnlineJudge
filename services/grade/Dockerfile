FROM maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e AS build
WORKDIR /workspace
COPY services/grade/pom.xml services/grade/pom.xml
RUN mvn -f services/grade/pom.xml -q -Dmaven.test.skip=true dependency:go-offline
COPY services/grade services/grade
COPY backend/src/main/java backend/src/main/java
COPY database/migrations/20260525_01_create_grd_grade_item.sql database/migrations/20260525_01_create_grd_grade_item.sql
RUN mvn -f services/grade/pom.xml -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre@sha256:7a65df4b22d2de92d4e04056e884f3b9122d70b21e2847fd66084278bd0ce037
WORKDIR /opt/onlinejudge-grade
ARG GIT_SHA
ARG IMAGE_SOURCE=https://github.com/Cr4zyorange/OnlineJudge
LABEL org.opencontainers.image.revision="$GIT_SHA" org.opencontainers.image.version="$GIT_SHA" org.opencontainers.image.source="$IMAGE_SOURCE"
RUN command -v wget >/dev/null && groupadd --system --gid 10004 onlinejudge && useradd --system --uid 10004 --gid 10004 --home-dir /opt/onlinejudge-grade --shell /usr/sbin/nologin onlinejudge \
    && install -d -o 10004 -g 10004 /var/lib/onlinejudge-grade
COPY --from=build --chown=10004:10004 /workspace/services/grade/target/onlinejudge-grade-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8084
USER 10004:10004
ENTRYPOINT ["java", "-jar", "/opt/onlinejudge-grade/app.jar"]
