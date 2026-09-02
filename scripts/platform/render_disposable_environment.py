#!/usr/bin/env python3
"""Render the #318 Compose and Kubernetes adapters from the workload manifest.

The manifest remains the source of truth for names, images, ports, probes,
resource bounds and migration ordering.  This adapter only supplies the small
amount of runtime wiring that is inherently platform-specific (service DNS
names, schema account names and container commands).  It never writes secret
values: Compose receives variable references and Kubernetes receives
``secretKeyRef`` entries.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

from plan_delivery import FULL_GIT_SHA, image_reference
from validate_workload_manifest import ManifestValidationError, load_json, validate


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
DATABASE_NAME = {
    "identity": "oj_identity",
    "course": "oj_course",
    "assessment": "oj_assessment",
    "grade": "oj_grade",
}
RUNTIME_ACCOUNT = {
    "identity": "oj_identity_rw",
    "course": "oj_course_rw",
    "assessment": "oj_assessment_rw",
    "grade": "oj_grade_rw",
}
RUNTIME_PASSWORD = {
    "identity": "IDENTITY_DATABASE_PASSWORD",
    "course": "COURSE_DATABASE_PASSWORD",
    "assessment": "ASSESSMENT_DATABASE_PASSWORD",
    "grade": "GRADE_DATABASE_PASSWORD",
}
MIGRATION_RUNNER_REPOSITORY = "onlinejudge/platform-migration-runner"
FRONTEND_PROXY_CONFIG_TEMPLATE = REPOSITORY_ROOT / "deploy/platform/frontend-disposable.conf.template"


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--schema", type=Path, required=True, help="workload-manifest JSON Schema")
    parser.add_argument("--manifest", type=Path, required=True, help="validated workload manifest")
    parser.add_argument("--git-sha", required=True, help="immutable full 40-character Git SHA")
    parser.add_argument("--compose-output", type=Path, required=True, help="generated Docker Compose YAML")
    parser.add_argument("--kubernetes-output", type=Path, required=True, help="generated Kubernetes YAML")
    parser.add_argument(
        "--kubernetes-stage-dir",
        type=Path,
        help="optional directory for executable, dependency-ordered Kubernetes stage manifests",
    )
    parser.add_argument(
        "--repository-root",
        type=Path,
        default=REPOSITORY_ROOT,
        help="checkout mounted into migration jobs (defaults to this checkout)",
    )
    parser.add_argument("--namespace", default="onlinejudge-platform", help="Kubernetes namespace")
    return parser.parse_args()


def yaml_scalar(value: Any) -> str:
    """Emit a deterministic quoted YAML scalar without adding a YAML dependency."""

    return json.dumps(str(value), ensure_ascii=False)


def indent(lines: list[str], amount: int) -> list[str]:
    prefix = " " * amount
    return [prefix + line if line else line for line in lines]


def compose_secret_expression(key: str) -> str:
    # A live JWKS URI is the primary local integration path.  A trust bundle is
    # optional here, but production injectors can still supply it through the
    # same key.  All other declared secrets fail closed when omitted.
    if key == "IDENTITY_JWKS_TRUST_BUNDLE":
        return "${IDENTITY_JWKS_TRUST_BUNDLE:-}"
    return "${" + key + ":?" + key + " is required}"


def workload_secret_environment(workload: dict[str, Any]) -> dict[str, str]:
    return {
        secret["key"]: compose_secret_expression(secret["key"])
        for secret in workload["secrets"]
    }


def common_environment(git_sha: str) -> dict[str, str]:
    return {
        "GIT_SHA": git_sha,
        "IDENTITY_JWT_ISSUER": "onlinejudge.identity.v2",
        "IDENTITY_JWT_AUDIENCE": "onlinejudge.api",
        # Controlled readiness drills are opt-in and default to healthy.  This
        # value is injected explicitly so the healthcheck's escaped shell
        # variable is resolved by the container, not Compose interpolation.
        "ISSUE318_FAIL_READINESS": "${ISSUE318_FAIL_READINESS:-0}",
    }


def workload_environment(workload: dict[str, Any], git_sha: str) -> dict[str, str]:
    """Return the checked runtime wiring for a declared workload."""

    name = workload["name"]
    environment = workload_secret_environment(workload)
    environment.update(common_environment(git_sha))
    if name == "gateway":
        environment.update(
            {
                "IDENTITY_UPSTREAM": "identity-service:8081",
                "COURSE_UPSTREAM": "course-service:8082",
                "ASSESSMENT_UPSTREAM": "assessment-api:8083",
                "GRADE_UPSTREAM": "grade-service:8084",
            }
        )
    elif name == "identity-service":
        environment.update(
            {
                "IDENTITY_DATABASE_HOST": "mysql",
                "IDENTITY_DATABASE_PORT": "3306",
                "IDENTITY_DATABASE_NAME": DATABASE_NAME["identity"],
                "IDENTITY_DATABASE_USERNAME": RUNTIME_ACCOUNT["identity"],
                "IDENTITY_JWT_KID": "issue318-disposable",
                "IDENTITY_SERVICE_REVISION": git_sha,
                "IDENTITY_SEED_DATA_ENABLED": "${IDENTITY_SEED_DATA_ENABLED:-false}",
            }
        )
    elif name == "course-service":
        environment.update(
            {
                "COURSE_DATABASE_HOST": "mysql",
                "COURSE_DATABASE_PORT": "3306",
                "COURSE_DATABASE_NAME": DATABASE_NAME["course"],
                "COURSE_DATABASE_USER": RUNTIME_ACCOUNT["course"],
                "IDENTITY_JWKS_URI": "http://identity-service:8081/.well-known/jwks.json",
                "RABBITMQ_HOST": "rabbitmq",
                "RABBITMQ_PORT": "5672",
                "RABBITMQ_USER": "onlinejudge",
                "COURSE_RABBIT_ENABLED": "true",
            }
        )
    elif name in {"assessment-api", "assessment-worker"}:
        environment.update(
            {
                "ASSESSMENT_DATABASE_HOST": "mysql",
                "ASSESSMENT_DATABASE_PORT": "3306",
                "ASSESSMENT_DATABASE_NAME": DATABASE_NAME["assessment"],
                "ASSESSMENT_DATABASE_USER": RUNTIME_ACCOUNT["assessment"],
                "IDENTITY_JWKS_URI": "http://identity-service:8081/.well-known/jwks.json",
                "ASSESSMENT_COURSE_AUTHORIZATION_URI": "http://course-service:8082/internal/v2/courses/{courseId}/authorizations/{userId}",
                "ASSESSMENT_COURSE_SERVICE_AUTHORIZATION": "${ASSESSMENT_SERVICE_IDENTITY:?ASSESSMENT_SERVICE_IDENTITY is required}",
                "ASSESSMENT_RABBIT_HOST": "rabbitmq",
                "ASSESSMENT_RABBIT_USERNAME": "onlinejudge",
                "ASSESSMENT_STORAGE_ROOT": "/var/lib/onlinejudge-assessment",
            }
        )
        if name == "assessment-api":
            environment.update({"ASSESSMENT_RABBIT_ENABLED": "false", "ASSESSMENT_RABBIT_RELAY_ENABLED": "false"})
        else:
            environment.update(
                {
                    "ASSESSMENT_WORKER_ENABLED": "true",
                    "ASSESSMENT_RABBIT_ENABLED": "true",
                    "ASSESSMENT_RABBIT_RELAY_ENABLED": "true",
                    # The worker is live and ready without a pending evaluation.  A
                    # sandbox endpoint is injected only by the dedicated LAB gate.
                    "ASSESSMENT_SANDBOX_DOCKER_API_URI": "${ASSESSMENT_SANDBOX_DOCKER_API_URI:-}",
                }
            )
    elif name == "grade-service":
        environment.update(
            {
                "GRADE_DATASOURCE_URL": "jdbc:mysql://mysql:3306/oj_grade?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false",
                "GRADE_DATABASE_USER": RUNTIME_ACCOUNT["grade"],
                "GRADE_DATABASE_DRIVER": "com.mysql.cj.jdbc.Driver",
                "GRADE_HTTP_PORT": "8084",
                "IDENTITY_JWKS_URI": "http://identity-service:8081/.well-known/jwks.json",
                "GRADE_COURSE_BASE_URL": "http://course-service:8082",
                "GRADE_COURSE_SERVICE_AUTHORIZATION": "${GRADE_COURSE_SERVICE_IDENTITY:?GRADE_COURSE_SERVICE_IDENTITY is required}",
                "GRADE_ASSESSMENT_BASE_URL": "http://assessment-api:8083",
                "GRADE_ASSESSMENT_SERVICE_AUTHORIZATION": "${GRADE_ASSESSMENT_SERVICE_IDENTITY:?GRADE_ASSESSMENT_SERVICE_IDENTITY is required}",
                "RABBITMQ_HOST": "rabbitmq",
                "RABBITMQ_USERNAME": "onlinejudge",
                "GRADE_RABBIT_ENABLED": "true",
                "GRADE_RABBIT_RELAY_ENABLED": "true",
            }
        )
    return environment


def compose_healthcheck(workload: dict[str, Any]) -> list[str]:
    name = workload["name"]
    if name == "assessment-worker":
        check = "test -f /tmp/assessment-worker-ready"
    elif name == "rabbitmq":
        check = "rabbitmq-diagnostics -q check_running"
    elif name == "mysql":
        check = "mysqladmin ping -h 127.0.0.1 -uroot -p$$MYSQL_ROOT_PASSWORD --silent"
    else:
        port = workload["ports"][0]["containerPort"]
        target = workload["health"]["readiness"]["target"]
        if name == "frontend":
            check = f"wget -qO- http://127.0.0.1:{port}{target} >/dev/null"
        else:
            check = f"wget -qO- http://127.0.0.1:{port}{target} | grep -q '\"status\":\"UP\"'"
    if name in {"gateway", "identity-service", "course-service", "assessment-api", "assessment-worker", "grade-service", "frontend"}:
        check = 'test "$${ISSUE318_FAIL_READINESS:-0}" != "1" && ' + check
    return [
        "healthcheck:",
        "  test: [\"CMD-SHELL\", " + yaml_scalar(check) + "]",
        "  interval: 5s",
        "  timeout: 3s",
        "  retries: 24",
        "  start_period: 10s",
    ]


def compose_resources(workload: dict[str, Any]) -> list[str]:
    resources = workload["resources"]

    def compose_cpu(quantity: str) -> str:
        if quantity.endswith("m"):
            return f"{int(quantity[:-1]) / 1000:g}"
        return quantity

    def compose_memory(quantity: str) -> str:
        if quantity.endswith("Mi"):
            return quantity[:-2] + "M"
        if quantity.endswith("Gi"):
            return quantity[:-2] + "G"
        raise ManifestValidationError(f"unsupported Compose memory quantity {quantity!r}")

    return [
        "deploy:",
        "  resources:",
        "    reservations:",
        "      cpus: " + yaml_scalar(compose_cpu(resources["requests"]["cpu"])),
        "      memory: " + yaml_scalar(compose_memory(resources["requests"]["memory"])),
        "    limits:",
        "      cpus: " + yaml_scalar(compose_cpu(resources["limits"]["cpu"])),
        "      memory: " + yaml_scalar(compose_memory(resources["limits"]["memory"])),
    ]


def compose_depends_on(workload: dict[str, Any]) -> dict[str, str]:
    dependencies = {dependency: "service_healthy" for dependency in workload["dependsOn"]}
    if workload["migrationJob"]:
        dependencies[workload["migrationJob"]] = "service_completed_successfully"
    if workload["name"] == "assessment-worker":
        dependencies["rabbitmq"] = "service_healthy"
    return dependencies


def compose_service(
    workload: dict[str, Any],
    git_sha: str,
    repository_root: Path,
    frontend_proxy_config_path: Path,
) -> list[str]:
    name = workload["name"]
    image = image_reference(workload, git_sha)
    lines = [f"  {name}:", f"    image: {yaml_scalar(image)}"]
    if workload["image"]["build"]:
        lines.extend(
            [
                "    build:",
                "      context: " + yaml_scalar(repository_root),
                "      dockerfile: " + yaml_scalar(workload["dockerfile"]),
                "      args:",
                "        GIT_SHA: " + yaml_scalar(git_sha),
            ]
        )
    if name == "mysql":
        lines.extend(
            [
                "    environment:",
                "      MYSQL_ROOT_PASSWORD: " + yaml_scalar("${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"),
                "    command: [\"--character-set-server=utf8mb4\", \"--collation-server=utf8mb4_unicode_ci\"]",
            ]
        )
    elif name == "rabbitmq":
        lines.extend(
            [
                "    environment:",
                "      RABBITMQ_DEFAULT_USER: \"onlinejudge\"",
                "      RABBITMQ_DEFAULT_PASS: " + yaml_scalar("${RABBITMQ_PASSWORD:?RABBITMQ_PASSWORD is required}"),
            ]
        )
    else:
        environment = workload_environment(workload, git_sha)
        lines.append("    environment:")
        for key in sorted(environment):
            lines.append("      " + key + ": " + yaml_scalar(environment[key]))
    dependencies = compose_depends_on(workload)
    if dependencies:
        lines.append("    depends_on:")
        for dependency, condition in dependencies.items():
            lines.extend([f"      {dependency}:", f"        condition: {condition}"])
    if name == "frontend":
        lines.extend(
            [
                "    volumes:",
                "      - "
                + yaml_scalar(
                    str(frontend_proxy_config_path) + ":/etc/nginx/conf.d/default.conf:ro"
                ),
            ]
        )
    if name == "assessment-api" or name == "assessment-worker":
        lines.extend(["    volumes:", "      - assessment-files:/var/lib/onlinejudge-assessment"])
    if name == "assessment-worker":
        lines.append("    command: [\"--spring.main.web-application-type=none\", \"--assessment.worker.enabled=true\"]")
    if workload["traffic"]["exposed"]:
        port = workload["ports"][0]["containerPort"]
        lines.extend(["    ports:", "      - " + yaml_scalar("127.0.0.1:${GATEWAY_HTTP_PORT:-18080}:" + str(port))])
    lines.extend(indent(compose_healthcheck(workload), 4))
    lines.extend(indent(compose_resources(workload), 4))
    lines.append("    restart: \"no\"")
    return lines


def init_runtime_accounts_command(*, compose_escaped: bool) -> str:
    """Render the account bootstrap shell program for Compose or Kubernetes.

    Compose consumes ``$$`` as a literal dollar while Kubernetes passes command
    arguments directly to ``sh``.  Keeping the two escaping modes explicit
    prevents a Kubernetes Job from treating ``$$`` as a process ID.
    """

    dollar = "$$" if compose_escaped else "$"
    commands = [f'export MYSQL_PWD="{dollar}MYSQL_ROOT_PASSWORD"']
    for schema in ("identity", "course", "assessment", "grade"):
        database = DATABASE_NAME[schema]
        account = RUNTIME_ACCOUNT[schema]
        password = RUNTIME_PASSWORD[schema]
        commands.extend(
            [
                f'''escaped_password={dollar}(printf '%s' "{dollar}{{{password}}}" | sed -e 's/\\\\/\\\\\\\\/g' -e "s/'/''/g")''',
                (
                    "mysql --protocol=tcp --host=mysql --port=3306 --user=root --execute "
                    f'"CREATE DATABASE IF NOT EXISTS {database} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; '
                    f"CREATE USER IF NOT EXISTS '{account}'@'%' IDENTIFIED BY '{dollar}escaped_password'; "
                    f"ALTER USER '{account}'@'%' IDENTIFIED BY '{dollar}escaped_password'; "
                    f"REVOKE ALL PRIVILEGES, GRANT OPTION FROM '{account}'@'%'; "
                    f"GRANT SELECT, INSERT, UPDATE, DELETE ON {database}.* TO '{account}'@'%';\""
                ),
            ]
        )
    commands.append('mysql --protocol=tcp --host=mysql --port=3306 --user=root --execute "FLUSH PRIVILEGES;"')
    return "\n".join(commands)


def compose_runtime_account_init() -> list[str]:
    return [
        "  mysql-runtime-account-init:",
        '    image: "mysql:8.4"',
        "    depends_on:",
        "      mysql:",
        "        condition: service_healthy",
        "    environment:",
        "      MYSQL_ROOT_PASSWORD: \"${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}\"",
        "      IDENTITY_DATABASE_PASSWORD: \"${IDENTITY_DATABASE_PASSWORD:?IDENTITY_DATABASE_PASSWORD is required}\"",
        "      COURSE_DATABASE_PASSWORD: \"${COURSE_DATABASE_PASSWORD:?COURSE_DATABASE_PASSWORD is required}\"",
        "      ASSESSMENT_DATABASE_PASSWORD: \"${ASSESSMENT_DATABASE_PASSWORD:?ASSESSMENT_DATABASE_PASSWORD is required}\"",
        "      GRADE_DATABASE_PASSWORD: \"${GRADE_DATABASE_PASSWORD:?GRADE_DATABASE_PASSWORD is required}\"",
        "    entrypoint: [\"sh\", \"-ec\", " + yaml_scalar(init_runtime_accounts_command(compose_escaped=True)) + "]",
        "    restart: \"no\"",
    ]


def compose_migration_job(job: dict[str, Any], repository_root: Path) -> list[str]:
    schema = job["schema"]
    depends = ["mysql-runtime-account-init", *job["dependsOnMigrationJobs"]]
    command = (
        'if [ "$${ISSUE318_FAIL_MIGRATION:-0}" = "1" ]; then '
        f'echo "controlled migration failure: {job["name"]}" >&2; exit 41; fi; '
        f"exec /workspace/migrate-service.sh --schema {schema}"
    )
    lines = [
        f"  {job['name']}:",
        "    image: " + yaml_scalar(f"{MIGRATION_RUNNER_REPOSITORY}:{job['_gitSha']}"),
        "    build:",
        "      context: " + yaml_scalar(repository_root),
        "      dockerfile: \"deploy/platform/migration-runner.Dockerfile\"",
        "      args:",
        "        GIT_SHA: " + yaml_scalar(job["_gitSha"]),
        "    depends_on:",
    ]
    for dependency in depends:
        lines.extend([f"      {dependency}:", "        condition: service_completed_successfully"])
    lines.extend(
        [
            "    environment:",
            "      MYSQL_HOST: \"mysql\"",
            "      MYSQL_PORT: \"3306\"",
            "      MIGRATION_DATABASE_NAME: " + yaml_scalar(DATABASE_NAME[schema]),
            "      MIGRATION_DATABASE_USER: \"root\"",
            "      MIGRATION_DATABASE_PASSWORD: \"${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}\"",
            "      MIGRATION_ROOT: \"/workspace/migrations\"",
            "      ISSUE318_FAIL_MIGRATION: \"${ISSUE318_FAIL_MIGRATION:-0}\"",
            "    entrypoint: [\"sh\", \"-ec\", " + yaml_scalar(command) + "]",
            "    restart: \"no\"",
        ]
    )
    return lines


def render_compose(
    manifest: dict[str, Any],
    git_sha: str,
    repository_root: Path,
    frontend_proxy_config_path: Path,
) -> str:
    lines = [
        "# Generated by scripts/platform/render_disposable_environment.py; do not edit.",
        "# Source of truth: deploy/platform/workloads.json",
        "name: ${COMPOSE_PROJECT_NAME:-onlinejudge-platform}",
        "services:",
    ]
    for workload in manifest["workloads"]:
        lines.extend(compose_service(workload, git_sha, repository_root, frontend_proxy_config_path))
    lines.extend(compose_runtime_account_init())
    for declared_job in manifest["migrationJobs"]:
        job = {**declared_job, "_gitSha": git_sha}
        lines.extend(compose_migration_job(job, repository_root))
    lines.extend(["volumes:", "  assessment-files:", "  mysql-data:"])
    return "\n".join(lines) + "\n"


def kube_env_lines(environment: dict[str, str], workload: dict[str, Any]) -> list[str]:
    lines: list[str] = []
    secret_keys = {secret["key"] for secret in workload["secrets"]}
    for key, value in sorted(environment.items()):
        lines.extend(["- name: " + key])
        optional_reference = re.fullmatch(r"\$\{([A-Z][A-Z0-9_]*):-([^}]*)\}", value)
        reference = re.fullmatch(r"\$\{([A-Z][A-Z0-9_]*)(?::[^}]*)?\}", value)
        if optional_reference and key not in secret_keys:
            # Optional Compose knobs such as the controlled failure toggle and
            # LAB's sandbox endpoint must remain optional in Kubernetes too.
            lines.append("  value: " + yaml_scalar(optional_reference.group(2)))
        elif optional_reference:
            # A declared optional secret remains injectable without making the
            # entire Pod fail when the optional key is absent from the runtime
            # Secret.
            lines.extend(
                [
                    "  valueFrom:",
                    "    secretKeyRef:",
                    "      name: onlinejudge-platform-runtime",
                    "      key: " + optional_reference.group(1),
                    "      optional: true",
                ]
            )
        elif key in secret_keys or key.endswith("PASSWORD") or key.endswith("IDENTITY") or reference:
            secret_key = reference.group(1) if reference else key
            if key == "MIGRATION_DATABASE_PASSWORD":
                secret_key = "MYSQL_ROOT_PASSWORD"
            lines.extend(["  valueFrom:", "    secretKeyRef:", "      name: onlinejudge-platform-runtime", "      key: " + secret_key])
        else:
            lines.append("  value: " + yaml_scalar(value))
    return lines


def kube_probe(probe: dict[str, Any], port: int, field: str, workload_name: str) -> list[str]:
    target = probe["target"]
    lines = [field + ":"]
    if probe["kind"] == "http":
        lines.extend(["  httpGet:", "    path: " + target, "    port: " + str(port)])
    else:
        command = "test -f /tmp/assessment-worker-ready" if workload_name == "assessment-worker" else target
        lines.extend(["  exec:", "    command: [\"sh\", \"-ec\", " + yaml_scalar(command) + "]"])
    lines.extend(["  periodSeconds: 5", "  timeoutSeconds: 3", "  failureThreshold: 24"])
    return lines


def frontend_proxy_config(resolver: str) -> str:
    """Render the target-specific resolver into the shared frontend template."""

    template = FRONTEND_PROXY_CONFIG_TEMPLATE.read_text(encoding="utf-8")
    return template.replace("__GATEWAY_RESOLVER__", resolver)


def kube_frontend_proxy_config(namespace: str) -> str:
    config = frontend_proxy_config("kube-dns.kube-system.svc.cluster.local")
    return "\n".join(
        [
            "apiVersion: v1",
            "kind: ConfigMap",
            "metadata:",
            "  name: frontend-proxy-config",
            "  namespace: " + namespace,
            "  labels:",
            "    app.kubernetes.io/part-of: onlinejudge-platform",
            "data:",
            "  frontend-disposable.conf: |",
            *indent(config.rstrip().splitlines(), 4),
        ]
    )


def kube_workload(workload: dict[str, Any], git_sha: str, namespace: str) -> str:
    name = workload["name"]
    image = image_reference(workload, git_sha)
    kind = "StatefulSet" if workload["type"] in {"database", "broker"} else "Deployment"
    declared_port = workload["ports"][0] if workload["ports"] else None
    port = declared_port["containerPort"] if declared_port else 0
    environment = workload_environment(workload, git_sha) if workload["type"] not in {"database", "broker"} else (
        {"MYSQL_ROOT_PASSWORD": ""} if name == "mysql" else {"RABBITMQ_DEFAULT_USER": "onlinejudge", "RABBITMQ_DEFAULT_PASS": ""}
    )
    if name == "mysql":
        environment["MYSQL_ROOT_PASSWORD"] = "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
    if name == "rabbitmq":
        environment["RABBITMQ_DEFAULT_PASS"] = "${RABBITMQ_PASSWORD:?RABBITMQ_PASSWORD is required}"
    resources = workload["resources"]
    lines = [
        "apiVersion: apps/v1",
        "kind: " + kind,
        "metadata:",
        "  name: " + name,
        "  namespace: " + namespace,
        "  labels:",
        "    app.kubernetes.io/name: " + name,
        "    app.kubernetes.io/part-of: onlinejudge-platform",
        "    app.kubernetes.io/version: " + git_sha,
        "spec:",
        "  serviceName: " + name if kind == "StatefulSet" else "  replicas: 1",
    ]
    if kind == "StatefulSet":
        lines.append("  replicas: 1")
    lines.extend(
        [
            "  selector:",
            "    matchLabels:",
            "      app.kubernetes.io/name: " + name,
            "  template:",
            "    metadata:",
            "      labels:",
            "        app.kubernetes.io/name: " + name,
            "    spec:",
            "      containers:",
            "        - name: " + name,
            "          image: " + image,
            "          imagePullPolicy: IfNotPresent",
            "          env:",
            *indent(kube_env_lines(environment, workload), 12),
            "          resources:",
            "            requests:",
            "              cpu: " + resources["requests"]["cpu"],
            "              memory: " + resources["requests"]["memory"],
            "            limits:",
            "              cpu: " + resources["limits"]["cpu"],
            "              memory: " + resources["limits"]["memory"],
        ]
    )
    if declared_port:
        port_lines = [
            "          ports:",
            "            - name: " + declared_port["name"],
            "              containerPort: " + str(port),
        ]
        env_index = lines.index("          env:")
        lines[env_index:env_index] = port_lines
    if name == "assessment-worker":
        lines.extend(["          command: [\"java\"]", "          args: [\"-jar\", \"/opt/onlinejudge-assessment/app.jar\", \"--spring.main.web-application-type=none\", \"--assessment.worker.enabled=true\"]"])
    if name == "frontend":
        lines.extend(
            [
                "          volumeMounts:",
                "            - name: frontend-proxy-config",
                "              mountPath: /etc/nginx/conf.d/default.conf",
                "              subPath: frontend-disposable.conf",
                "              readOnly: true",
            ]
        )
    for field, probe_name in (("startupProbe", "startup"), ("livenessProbe", "liveness"), ("readinessProbe", "readiness")):
        lines.extend(indent(kube_probe(workload["health"][probe_name], port, field, name), 10))
    if name == "frontend":
        lines.extend(
            [
                "      volumes:",
                "        - name: frontend-proxy-config",
                "          configMap:",
                "            name: frontend-proxy-config",
            ]
        )
    if declared_port is None:
        return "\n".join(lines)
    service = [
        "apiVersion: v1",
        "kind: Service",
        "metadata:",
        "  name: " + name,
        "  namespace: " + namespace,
        "spec:",
        "  selector:",
        "    app.kubernetes.io/name: " + name,
        "  ports:",
        "    - name: " + declared_port["name"],
        "      port: " + str(port),
        "      targetPort: " + str(port),
    ]
    return "\n---\n".join(["\n".join(lines), "\n".join(service)])


def kube_migration_job(job: dict[str, Any], namespace: str) -> str:
    schema = job["schema"]
    command = f"exec /workspace/migrate-service.sh --schema {schema}"
    return "\n".join(
        [
            "apiVersion: batch/v1",
            "kind: Job",
            "metadata:",
            "  name: " + job["name"],
            "  namespace: " + namespace,
            "  labels:",
            "    app.kubernetes.io/part-of: onlinejudge-platform",
            "    delivery.onlinejudge.io/blocks-traffic: \"true\"",
            "spec:",
            "  backoffLimit: 0",
            "  template:",
            "    spec:",
            "      restartPolicy: Never",
            "      containers:",
            "        - name: " + job["name"],
            "          image: " + MIGRATION_RUNNER_REPOSITORY + ":${GIT_SHA}",
            "          command: [\"sh\", \"-ec\", " + yaml_scalar(command) + "]",
            "          env:",
            "            - name: MYSQL_HOST",
            "              value: mysql",
            "            - name: MYSQL_PORT",
            "              value: \"3306\"",
            "            - name: MIGRATION_DATABASE_NAME",
            "              value: " + DATABASE_NAME[schema],
            "            - name: MIGRATION_DATABASE_USER",
            "              value: root",
            "            - name: MIGRATION_DATABASE_PASSWORD",
            "              valueFrom:",
            "                secretKeyRef:",
            "                  name: onlinejudge-platform-runtime",
            "                  key: MYSQL_ROOT_PASSWORD",
            "            - name: MIGRATION_ROOT",
            "              value: /workspace/migrations",
        ]
    )


def kube_runtime_account_init(namespace: str) -> str:
    return "\n".join(
        [
            "apiVersion: batch/v1",
            "kind: Job",
            "metadata:",
            "  name: mysql-runtime-account-init",
            "  namespace: " + namespace,
            "  annotations:",
            "    delivery.onlinejudge.io/prerequisite: mysql-ready",
            "spec:",
            "  backoffLimit: 0",
            "  template:",
            "    spec:",
            "      restartPolicy: Never",
            "      containers:",
            "        - name: mysql-runtime-account-init",
            "          image: mysql:8.4",
            "          command: [\"sh\", \"-ec\", " + yaml_scalar(init_runtime_accounts_command(compose_escaped=False)) + "]",
            "          env:",
            "            - name: MYSQL_ROOT_PASSWORD",
            "              valueFrom:",
            "                secretKeyRef:",
            "                  name: onlinejudge-platform-runtime",
            "                  key: MYSQL_ROOT_PASSWORD",
            *[
                item
                for key in RUNTIME_PASSWORD.values()
                for item in (
                    "            - name: " + key,
                    "              valueFrom:",
                    "                secretKeyRef:",
                    "                  name: onlinejudge-platform-runtime",
                    "                  key: " + key,
                )
            ],
        ]
    )


def kube_namespace(namespace: str) -> str:
    return "\n".join(
        [
            "apiVersion: v1",
            "kind: Namespace",
            "metadata:",
            "  name: " + namespace,
            "  labels:",
            "    app.kubernetes.io/part-of: onlinejudge-platform",
        ]
    )


def render_kubernetes(manifest: dict[str, Any], git_sha: str, namespace: str) -> str:
    """Render a complete inventory only; staged files are the deployable input.

    A Kubernetes multi-document stream has no execution dependencies.  The
    companion staged files and deployer therefore form the only supported
    application path; this inventory remains useful for inspection and schema
    tooling without pretending that document order gates migrations.
    """

    documents = [kube_namespace(namespace), kube_frontend_proxy_config(namespace)]
    documents.extend(kube_workload(workload, git_sha, namespace) for workload in manifest["workloads"])
    documents.append(kube_runtime_account_init(namespace))
    documents.extend(kube_migration_job(job, namespace).replace("${GIT_SHA}", git_sha) for job in manifest["migrationJobs"])
    return (
        "# Inventory only: deploy with scripts/platform/deploy_kubernetes_disposable_environment.sh.\n"
        "# Kubernetes does not treat YAML document order as a dependency graph.\n"
        + "\n---\n".join(documents)
        + "\n"
    )


def render_kubernetes_stages(manifest: dict[str, Any], git_sha: str, namespace: str) -> dict[str, str]:
    """Split Kubernetes resources into the only safe application sequence."""

    workloads = manifest["workloads"]
    infrastructure = [
        workload
        for workload in workloads
        if workload["type"] in {"database", "broker"}
    ]
    applications = [
        workload
        for workload in workloads
        if workload["type"] not in {"database", "broker"} and workload["name"] != "gateway"
    ]
    gateway = next(workload for workload in workloads if workload["name"] == "gateway")
    stages = {
        "00-namespace.yaml": kube_namespace(namespace),
        "10-infrastructure.yaml": "\n---\n".join(
            kube_workload(workload, git_sha, namespace) for workload in infrastructure
        ),
        "20-runtime-account-init.yaml": kube_runtime_account_init(namespace),
    }
    for index, job in enumerate(manifest["migrationJobs"], start=3):
        stages[f"{index * 10:02d}-{job['name']}.yaml"] = kube_migration_job(job, namespace).replace("${GIT_SHA}", git_sha)
    stages["70-applications.yaml"] = "\n---\n".join(
        [
            kube_frontend_proxy_config(namespace),
            *(kube_workload(workload, git_sha, namespace) for workload in applications),
        ]
    )
    stages["80-gateway.yaml"] = kube_workload(gateway, git_sha, namespace)
    return {name: content + "\n" for name, content in stages.items()}


def write_output(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def main() -> int:
    arguments = parse_arguments()
    if FULL_GIT_SHA.fullmatch(arguments.git_sha) is None:
        print("ERROR: --git-sha must be a full 40-character Git SHA", file=sys.stderr)
        return 2
    try:
        schema = load_json(arguments.schema, "schema")
        manifest = load_json(arguments.manifest, "manifest")
        validate(manifest, schema)
        if not re.fullmatch(r"[a-z0-9]([-a-z0-9]*[a-z0-9])?", arguments.namespace):
            raise ManifestValidationError("--namespace must be a DNS-1123 label")
        repository_root = arguments.repository_root.resolve()
        if not (repository_root / "database/mysql/migrate-service.sh").is_file():
            raise ManifestValidationError("--repository-root must contain database/mysql/migrate-service.sh")
        compose_frontend_proxy_config = arguments.compose_output.parent / "frontend-disposable.conf"
        write_output(compose_frontend_proxy_config, frontend_proxy_config("127.0.0.11"))
        # The runner creates runtime secrets under umask 077.  This generated
        # Nginx configuration contains no secrets and is mounted into an image
        # that deliberately runs as the unprivileged nginx user, so its mode
        # must be explicitly readable on Linux hosts as well.
        compose_frontend_proxy_config.chmod(0o644)
        write_output(
            arguments.compose_output,
            render_compose(manifest, arguments.git_sha, repository_root, compose_frontend_proxy_config),
        )
        write_output(arguments.kubernetes_output, render_kubernetes(manifest, arguments.git_sha, arguments.namespace))
        if arguments.kubernetes_stage_dir is not None:
            for name, content in render_kubernetes_stages(manifest, arguments.git_sha, arguments.namespace).items():
                write_output(arguments.kubernetes_stage_dir / name, content)
    except ManifestValidationError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
