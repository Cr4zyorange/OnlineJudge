FROM mysql:8.4

ARG GIT_SHA
ARG IMAGE_SOURCE=https://github.com/Cr4zyorange/OnlineJudge
LABEL org.opencontainers.image.revision="$GIT_SHA" \
      org.opencontainers.image.version="$GIT_SHA" \
      org.opencontainers.image.source="$IMAGE_SOURCE"

COPY database/mysql/migrate-service.sh /workspace/migrate-service.sh
COPY database/migrations /workspace/migrations

RUN chmod 0555 /workspace/migrate-service.sh

ENTRYPOINT ["sh", "/workspace/migrate-service.sh"]
