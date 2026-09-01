#!/usr/bin/env bash
# OnlineJudge 开发容器统一入口（固定入口之一）。
#
# 固定入口：
#   1. scripts/dev/container.sh    本脚本（宿主机/agent 统一 CLI）
#   2. .devcontainer/devcontainer.json  IDE（VS Code "Reopen in Container"）
#   3. deploy/dev/docker-compose.yml    机器级 compose 定义
#   4. docker exec -it onlinejudge-dev bash  直接进入（容器名固定）
#
# 用法（在宿主机执行）：
#   scripts/dev/container.sh build       手动构建/更新 dev 镜像（见下方 build 说明）
#   scripts/dev/container.sh up          启动 dev 容器（含 MySQL，完整链路）
#   scripts/dev/container.sh up dev      仅启动 dev 容器（H2 快速循环）
#   scripts/dev/container.sh exec <cmd>  在容器内运行单条命令（agent 用，无 TTY）
#   scripts/dev/container.sh shell       进入容器交互 shell
#   scripts/dev/container.sh version     容器内工具链版本自检
#   scripts/dev/container.sh status      容器状态
#   scripts/dev/container.sh down        停止并移除 dev/mysql 容器
#
# 在容器内部执行本脚本时，等价于直接在容器内运行后续命令。
set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
compose_file="$repo_root/deploy/dev/docker-compose.yml"
container_name="onlinejudge-dev"
cred_file="${HOME:-/home/skk4784}/.git-credentials"

in_container() {
  [ -f /.dockerenv ] || [ -f /run/.containerenv ]
}

usage() {
  sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

cmd="${1:-help}"
shift || true

if in_container; then
  case "$cmd" in
    up|down|status|version|shell|exec|help)
      # 已在容器内：无需再进入，直接执行后续命令
      if [ $# -gt 0 ]; then
        exec "$@"
      else
        exec bash
      fi
      ;;
  esac
fi

ensure_compose() {
  command -v docker >/dev/null 2>&1 || { echo "container.sh: docker not found on host" >&2; exit 2; }
  [ -f "$compose_file" ] || { echo "container.sh: $compose_file missing" >&2; exit 2; }
}

ensure_credentials() {
  if [ -f "$cred_file" ]; then
    chmod 600 "$cred_file"
  else
    # 容器内 git 推送需要凭据；缺少时给出明确指引，不静默跳过
    echo "container.sh: $cred_file missing — run once on the host:" >&2
    echo "  gh.exe auth token >/dev/null 2>&1 || true" >&2
    echo "  read -s -p 'GitHub token: ' t && printf 'https://x-access-token:%s@github.com\\n' \"\$t\" > \"$cred_file\" && chmod 600 \"$cred_file\"" >&2
    exit 2
  fi
}

case "$cmd" in
  build)
    # 手动构建镜像：docker compose build 会把宿主代理环境注入 FROM 解析导致
    # docker hub 证书校验失败，因此改用 host 网络 + 显式代理参数构建
    # （github.com 在本机 DNS 被污染，下载须经 Windows 本地代理 127.0.0.1:7897）。
    command -v docker >/dev/null 2>&1 || { echo "container.sh: docker not found on host" >&2; exit 2; }
    docker build --network=host \
      --build-arg HTTP_PROXY=http://127.0.0.1:7897 \
      --build-arg HTTPS_PROXY=http://127.0.0.1:7897 \
      --build-arg http_proxy=http://127.0.0.1:7897 \
      --build-arg https_proxy=http://127.0.0.1:7897 \
      -t onlinejudge/dev:latest \
      -f "$repo_root/.devcontainer/Dockerfile" "$repo_root"
    ;;
  up)
    ensure_compose
    target="${1:-mysql dev}"
    docker compose -f "$compose_file" up -d $target
    docker compose -f "$compose_file" ps
    ;;
  exec)
    ensure_compose
    ensure_credentials
    [ $# -gt 0 ] || { echo "container.sh exec: missing command" >&2; exit 2; }
    # 经 bash -lc 执行，支持 "cd ... && mvn test" 这类复合命令
    docker compose -f "$compose_file" exec -T dev bash -lc "$*"
    ;;
  shell)
    ensure_compose
    ensure_credentials
    docker compose -f "$compose_file" exec dev bash
    ;;
  version)
    ensure_compose
    docker compose -f "$compose_file" exec -T dev bash -lc \
      'echo "java: $(java -version 2>&1 | head -1)"; echo "maven: $(mvn -v 2>&1 | head -1)"; echo "node: $(node -v)"; echo "npm: $(npm -v)"; echo "git: $(git --version)"; echo "gh: $(gh --version | head -1)"; echo "docker: $(docker --version)"'
    ;;
  status)
    ensure_compose
    docker compose -f "$compose_file" ps
    docker ps --filter "name=$container_name" --format 'container: {{.Names}} ({{.Status}})'
    ;;
  down)
    ensure_compose
    docker compose -f "$compose_file" down
    ;;
  help|--help|-h)
    usage 0
    ;;
  *)
    usage 1
    ;;
esac
