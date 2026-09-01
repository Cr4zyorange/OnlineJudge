# OnlineJudge 开发容器镜像。
#
# 职责：把 Java/Maven/Node 等开发工具链固定到与 CI 对齐的版本，
# 供 agent 与 IDE 通过 .devcontainer 或 deploy/dev/docker-compose.yml 进入。
# 宿主机只负责公共工具（git 等）；本镜像内的工具版本不得随意漂移。
#
# 版本对齐基准：
#   JDK   21   —— pom.xml java.version 与 CI (temurin 21)
#   Maven 3.9.16 —— 与既有 Windows 开发环境一致
#   Node  22.23.2 —— frontend/package.json engines: >=22 <25, npm 10
#   gh    2.98.0、kubectl v1.37.0、kind v0.33.0（脚本/交付链路用）
FROM ubuntu:24.04

# 基础工具
RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates curl wget gnupg lsb-release \
        git ripgrep jq unzip xz-utils openssh-client \
        mysql-client \
    && rm -rf /var/lib/apt/lists/*

# Temurin JDK 21（与 CI 的 temurin 发行版一致）
RUN curl -fSL -o /tmp/jdk.tar.gz \
        "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" \
    && mkdir -p /opt/java \
    && tar -xzf /tmp/jdk.tar.gz -C /opt/java --strip-components=1 \
    && rm /tmp/jdk.tar.gz
ENV JAVA_HOME=/opt/java \
    PATH="/opt/java/bin:${PATH}"

# Maven 3.9.16
RUN curl -fSL -o /tmp/maven.tar.gz \
        "https://archive.apache.org/dist/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.tar.gz" \
    && mkdir -p /opt/maven \
    && tar -xzf /tmp/maven.tar.gz -C /opt/maven --strip-components=1 \
    && rm /tmp/maven.tar.gz
ENV MAVEN_HOME=/opt/maven \
    PATH="/opt/maven/bin:${PATH}"

# Node 22 + npm（npm 随发行包内置，版本满足 package.json 的 npm 10 约束）
RUN curl -fSL -o /tmp/node.tar.xz \
        "https://nodejs.org/dist/v22.23.2/node-v22.23.2-linux-x64.tar.xz" \
    && mkdir -p /opt/node \
    && tar -xJf /tmp/node.tar.xz -C /opt/node --strip-components=1 \
    && rm /tmp/node.tar.xz
ENV PATH="/opt/node/bin:${PATH}"

# Docker CLI + compose 插件（复用宿主机 docker daemon：挂载 /var/run/docker.sock）
RUN curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
        | gpg --dearmor -o /usr/share/keyrings/docker.gpg \
    && echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu noble stable" \
        > /etc/apt/sources.list.d/docker.list \
    && apt-get update && apt-get install -y --no-install-recommends \
        docker-ce-cli docker-compose-plugin \
    && rm -rf /var/lib/apt/lists/*

# GitHub CLI（PR/issue 协作）、kubectl 与 kind（k8s 交付验证）
RUN curl -fSL -o /tmp/gh.tar.gz \
        "https://github.com/cli/cli/releases/download/v2.98.0/gh_2.98.0_linux_amd64.tar.gz" \
    && tar -xzf /tmp/gh.tar.gz -C /tmp \
    && install -m 0755 /tmp/gh_2.98.0_linux_amd64/bin/gh /usr/local/bin/gh \
    && rm -rf /tmp/gh*

RUN curl -fSL -o /usr/local/bin/kubectl \
        "https://dl.k8s.io/release/v1.37.0/bin/linux/amd64/kubectl" \
    && chmod +x /usr/local/bin/kubectl

RUN curl -fSL -o /usr/local/bin/kind \
        "https://github.com/kubernetes-sigs/kind/releases/download/v0.33.0/kind-linux-amd64" \
    && chmod +x /usr/local/bin/kind

WORKDIR /workspace

# 版本自检：构建后可用 scripts/dev/container.sh version 确认
RUN java -version >/dev/null 2>&1 \
    && mvn -v >/dev/null 2>&1 \
    && node -v >/dev/null 2>&1 \
    && npm -v >/dev/null 2>&1 \
    && echo "onlinejudge dev image ready"
