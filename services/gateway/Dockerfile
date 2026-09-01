FROM nginx:1.27-alpine

RUN apk add --no-cache bash

COPY services/gateway/nginx.conf /etc/nginx/nginx.conf
COPY services/gateway/entrypoint.sh /usr/local/bin/gateway-entrypoint
COPY scripts/gateway/render-gateway-config.sh /usr/local/bin/render-gateway-config
COPY deploy/gateway/gateway.conf.template /opt/onlinejudge/gateway.conf.template
COPY deploy/gateway/proxy-request-headers.conf /etc/nginx/includes/proxy-request-headers.conf

RUN chmod 0555 /usr/local/bin/gateway-entrypoint /usr/local/bin/render-gateway-config

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/health/live || exit 1

ENTRYPOINT ["/usr/local/bin/gateway-entrypoint"]
CMD ["nginx", "-g", "daemon off;"]
