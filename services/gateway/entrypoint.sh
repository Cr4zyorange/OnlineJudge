#!/usr/bin/env bash

set -Eeuo pipefail

/usr/local/bin/render-gateway-config \
  --template /opt/onlinejudge/gateway.conf.template \
  --output /etc/nginx/conf.d/gateway.conf

nginx -t
exec "$@"
