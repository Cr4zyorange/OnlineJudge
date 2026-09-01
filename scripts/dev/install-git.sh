#!/usr/bin/env bash
# 在 WSL 宿主机安装最新版 git（公共工具），无需宿主机 sudo。
#
# 原理：git 需要 libcurl 头文件才能编译出 HTTPS 支持，而宿主机缺少
# libcurl4-openssl-dev 且无法 sudo 安装；因此在一次性容器内编译官方源码，
# 再把产物安装到 ~/.local（宿主已有 libcurl.so.4 运行库）。
#
# 用法：
#   scripts/dev/install-git.sh [版本]     # 默认取 GitHub 最新稳定 tag
#   scripts/dev/install-git.sh status     # 仅打印当前版本
set -Eeuo pipefail

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
prefix="${HOME:-/home/skk4784}/.local"
staging="${prefix}/git-staging"
container_name="oj-git-builder"

current_version() {
  "${prefix}/bin/git" --version 2>/dev/null | awk '{print $3}' || true
}

latest_version() {
  # 宿主机可直接访问 GitHub API（走 Windows 本地代理 127.0.0.1:7897）
  curl -fsS --max-time 20 https://api.github.com/repos/git/git/releases/latest \
    | grep -m1 '"tag_name"' | sed 's/.*"v\([0-9.]*\)".*/\1/'
}

if [ "${1:-}" = "status" ]; then
  if [ -x "${prefix}/bin/git" ]; then
    echo "installed: $(current_version) at ${prefix}/bin/git"
  else
    echo "not installed (system git: $(git --version 2>/dev/null || echo none))"
  fi
  exit 0
fi

version="${1:-$(latest_version)}"
[ -n "$version" ] || { echo "install-git.sh: cannot determine latest version" >&2; exit 1; }

installed="$(current_version || true)"
if [ -n "$installed" ] && [ "$installed" = "$version" ]; then
  echo "install-git.sh: git $version already installed (${prefix}/bin/git)"
  exit 0
fi

command -v docker >/dev/null 2>&1 || { echo "install-git.sh: docker required" >&2; exit 2; }

rm -rf "$staging" && mkdir -p "$staging"
docker rm -f "$container_name" >/dev/null 2>&1 || true
echo "install-git.sh: building git $version in container (host network + local proxy)..."
# host 网络 + 代理参数：本机 github.com DNS 被污染，须经 Windows 本地代理 127.0.0.1:7897
docker run --network=host --name "$container_name" \
  -v "$staging:/out" \
  -e HTTP_PROXY=http://127.0.0.1:7897 -e HTTPS_PROXY=http://127.0.0.1:7897 \
  ubuntu:24.04 bash -c '
    set -e
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq >/dev/null 2>&1
    apt-get install -y -qq ca-certificates build-essential libcurl4-openssl-dev \
        libssl-dev libexpat1-dev zlib1g-dev gettext autoconf \
        rustc cargo >/dev/null 2>&1
    cd /tmp
    curl -fsSL -o git.tar.gz "https://github.com/git/git/archive/refs/tags/v'"$version"'.tar.gz"
    tar xzf git.tar.gz && cd "git-'"$version"'"
    make prefix=/usr/local -j"$(nproc)" all >/tmp/make.log 2>&1
    make prefix=/usr/local install DESTDIR=/out >>/tmp/make.log 2>&1
    echo BUILD_OK
  ' 2>&1 | tail -3
docker rm -f "$container_name" >/dev/null 2>&1 || true

if [ ! -x "$staging/usr/local/bin/git" ]; then
  echo "install-git.sh: build failed (no binary staged)" >&2
  exit 1
fi
cp -rn "$staging/usr/local/"* "$prefix/"
# 容器内以 root 写入的产物无法直接删除，用一次性容器清理
docker run --rm -v "$(dirname "$staging"):/local" ubuntu:24.04 \
  rm -rf "/local/$(basename "$staging")" >/dev/null 2>&1 || rm -rf "$staging"
hash -r
"${prefix}/bin/git" --version
# 编译期 prefix=/usr/local 被写死，helper（git-remote-https 等）在 ~/.local/libexec，
# 必须显式设置 GIT_EXEC_PATH；同时写入 ~/.bashrc 保证后续会话可用。
export GIT_EXEC_PATH="${prefix}/libexec/git-core"
if ! grep -q 'GIT_EXEC_PATH' "${HOME}/.bashrc" 2>/dev/null; then
  {
    echo ''
    echo '# OnlineJudge WSL dev: latest git installed to ~/.local (built in container)'
    echo 'export PATH="$HOME/.local/bin:$PATH"'
    echo 'export GIT_EXEC_PATH="$HOME/.local/libexec/git-core"'
  } >> "${HOME}/.bashrc"
  echo "install-git.sh: GIT_EXEC_PATH added to ~/.bashrc"
fi
echo "install-git.sh: git $version installed to $prefix/bin (PATH already includes $prefix/bin)"
