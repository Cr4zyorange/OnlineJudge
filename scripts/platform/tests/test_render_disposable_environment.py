#!/usr/bin/env python3
"""RED/GREEN contract tests for the #318 manifest-backed environment renderer."""

from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
RENDERER = REPOSITORY_ROOT / "scripts/platform/render_disposable_environment.py"
SCHEMA = REPOSITORY_ROOT / "deploy/platform/workload-manifest.schema.json"
MANIFEST = REPOSITORY_ROOT / "deploy/platform/workloads.json"
GIT_SHA = "b" * 40


class DisposableEnvironmentRendererTest(unittest.TestCase):
    """The disposable Compose and Kubernetes shapes must have one manifest source."""

    def render(self) -> tuple[str, str]:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            compose = output / "compose.yml"
            kubernetes = output / "platform.yaml"
            result = subprocess.run(
                [
                    sys.executable,
                    str(RENDERER),
                    "--schema",
                    str(SCHEMA),
                    "--manifest",
                    str(MANIFEST),
                    "--git-sha",
                    GIT_SHA,
                    "--compose-output",
                    str(compose),
                    "--kubernetes-output",
                    str(kubernetes),
                ],
                cwd=REPOSITORY_ROOT,
                check=False,
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            return compose.read_text(), kubernetes.read_text()

    def render_stages(self) -> dict[str, str]:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            compose = output / "compose.yml"
            kubernetes = output / "platform.yaml"
            stages = output / "stages"
            result = subprocess.run(
                [
                    sys.executable,
                    str(RENDERER),
                    "--schema",
                    str(SCHEMA),
                    "--manifest",
                    str(MANIFEST),
                    "--git-sha",
                    GIT_SHA,
                    "--compose-output",
                    str(compose),
                    "--kubernetes-output",
                    str(kubernetes),
                    "--kubernetes-stage-dir",
                    str(stages),
                ],
                cwd=REPOSITORY_ROOT,
                check=False,
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            return {path.name: path.read_text() for path in stages.glob("*.yaml")}

    def test_rendered_targets_preserve_all_manifest_workloads_and_migration_order(self) -> None:
        compose, kubernetes = self.render()

        for workload in (
            "gateway",
            "identity-service",
            "course-service",
            "assessment-api",
            "assessment-worker",
            "grade-service",
            "frontend",
            "rabbitmq",
            "mysql",
        ):
            self.assertIn(f"  {workload}:", compose)
            self.assertIn(f"name: {workload}", kubernetes)

        expected_jobs = (
            "identity-migrations",
            "course-migrations",
            "assessment-migrations",
            "grade-migrations",
        )
        positions = []
        for job in expected_jobs:
            self.assertIn(f"  {job}:", compose)
            self.assertIn(f"name: {job}", kubernetes)
            positions.append(compose.index(f"  {job}:"))
        self.assertEqual(positions, sorted(positions))

    def test_rendered_artifacts_keep_the_immutable_sha_and_do_not_embed_secret_values(self) -> None:
        compose, kubernetes = self.render()

        self.assertIn(f"onlinejudge/gateway:{GIT_SHA}", compose)
        self.assertIn(f"onlinejudge/grade-service:{GIT_SHA}", kubernetes)
        self.assertIn(f"onlinejudge/platform-migration-runner:{GIT_SHA}", kubernetes)
        self.assertIn("path: /api/v1/system/health", kubernetes)
        self.assertIn("/health/ready", compose)
        self.assertIn("GIT_SHA", compose)
        self.assertIn("MIGRATION_DATABASE_PASSWORD", compose)
        self.assertIn('cpus: "0.1"', compose)
        self.assertIn('memory: "128M"', compose)
        self.assertNotIn("root-password", compose)
        self.assertNotIn("assessment-password", compose)
        self.assertNotIn("root-password", kubernetes)

    def test_gateway_is_loopback_only_and_e2e_seed_is_opt_in(self) -> None:
        compose, _ = self.render()
        self.assertIn('127.0.0.1:${GATEWAY_HTTP_PORT:-18080}:8080', compose)
        self.assertIn('IDENTITY_SEED_DATA_ENABLED: "${IDENTITY_SEED_DATA_ENABLED:-false}"', compose)

    def test_compose_shell_jobs_pass_the_script_as_the_entrypoint_argument(self) -> None:
        compose, _ = self.render()

        self.assertIn('entrypoint: ["sh", "-ec",', compose)
        self.assertNotIn('    command: "if [', compose)
        self.assertNotIn('    command: "export MYSQL_PWD', compose)
        self.assertIn('ISSUE318_FAIL_MIGRATION: "${ISSUE318_FAIL_MIGRATION:-0}"', compose)
        self.assertIn('ISSUE318_FAIL_READINESS: "${ISSUE318_FAIL_READINESS:-0}"', compose)
        self.assertIn("escaped_password=$$(printf", compose)
        self.assertNotIn("<<'SQL'", compose)
        self.assertIn("CREATE DATABASE IF NOT EXISTS oj_identity", compose)
        self.assertNotIn("`oj_identity`", compose)

    def test_kubernetes_account_initialization_uses_normal_shell_expansion(self) -> None:
        _, kubernetes = self.render()
        account_job = kubernetes[
            kubernetes.index("kind: Job\nmetadata:\n  name: mysql-runtime-account-init") : kubernetes.index(
                "kind: Job\nmetadata:\n  name: identity-migrations"
            )
        ]

        self.assertIn("$MYSQL_ROOT_PASSWORD", account_job)
        self.assertIn("$(printf", account_job)
        self.assertIn("${IDENTITY_DATABASE_PASSWORD}", account_job)
        self.assertNotIn("$$", account_job)

    def test_kubernetes_keeps_optional_runtime_toggles_as_literal_defaults(self) -> None:
        _, kubernetes = self.render()
        worker_start = kubernetes.index("kind: Deployment\nmetadata:\n  name: assessment-worker")
        worker_end = kubernetes.index("\n---\napiVersion: apps/v1", worker_start)
        worker = kubernetes[worker_start:worker_end]

        self.assertIn('name: ISSUE318_FAIL_READINESS\n              value: "0"', worker)
        self.assertIn('name: ASSESSMENT_SANDBOX_DOCKER_API_URI\n              value: ""', worker)
        self.assertNotIn(
            'name: ISSUE318_FAIL_READINESS\n              valueFrom:',
            worker,
        )
        self.assertNotIn(
            'name: ASSESSMENT_SANDBOX_DOCKER_API_URI\n              valueFrom:',
            worker,
        )

    def test_kubernetes_stage_files_keep_migrations_before_workloads_and_gateway(self) -> None:
        stages = self.render_stages()

        self.assertEqual(
            sorted(stages),
            [
                "00-namespace.yaml",
                "10-infrastructure.yaml",
                "20-runtime-account-init.yaml",
                "30-identity-migrations.yaml",
                "40-course-migrations.yaml",
                "50-assessment-migrations.yaml",
                "60-grade-migrations.yaml",
                "70-applications.yaml",
                "80-gateway.yaml",
            ],
        )
        self.assertIn("name: mysql", stages["10-infrastructure.yaml"])
        self.assertIn("name: rabbitmq", stages["10-infrastructure.yaml"])
        self.assertNotIn("name: gateway", stages["10-infrastructure.yaml"])
        self.assertIn("name: identity-migrations", stages["30-identity-migrations.yaml"])
        self.assertNotIn("name: gateway", stages["70-applications.yaml"])
        self.assertIn("name: gateway", stages["80-gateway.yaml"])

    def test_assessment_worker_waits_for_the_shared_assessment_schema_migration(self) -> None:
        compose, _ = self.render()
        worker = compose[compose.index("\n  assessment-worker:") : compose.index("\n  grade-service:")]

        self.assertIn("      assessment-migrations:\n        condition: service_completed_successfully", worker)

    def test_frontend_legacy_backend_upstream_resolves_to_the_gateway_in_both_targets(self) -> None:
        compose, kubernetes = self.render()
        frontend = compose[compose.index("\n  frontend:") : compose.index("\n  rabbitmq:")]

        self.assertIn("frontend-disposable.conf:/etc/nginx/conf.d/default.conf:ro", frontend)
        self.assertIn(
            "kind: ConfigMap\nmetadata:\n  name: frontend-proxy-config\n  namespace: onlinejudge-platform",
            kubernetes,
        )
        self.assertIn("mountPath: /etc/nginx/conf.d/default.conf", kubernetes)
        self.assertIn("subPath: frontend-disposable.conf", kubernetes)
        self.assertIn("resolver kube-dns.kube-system.svc.cluster.local ipv6=off valid=10s", kubernetes)
        self.assertNotIn("aliases:\n          - backend", compose)

    def test_compose_frontend_proxy_file_remains_readable_when_runtime_secrets_use_a_private_umask(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            compose = output / "compose.yml"
            kubernetes = output / "platform.yaml"
            previous_umask = os.umask(0o077)
            try:
                result = subprocess.run(
                    [
                        sys.executable,
                        str(RENDERER),
                        "--schema",
                        str(SCHEMA),
                        "--manifest",
                        str(MANIFEST),
                        "--git-sha",
                        GIT_SHA,
                        "--compose-output",
                        str(compose),
                        "--kubernetes-output",
                        str(kubernetes),
                    ],
                    cwd=REPOSITORY_ROOT,
                    check=False,
                    capture_output=True,
                    text=True,
                )
            finally:
                os.umask(previous_umask)

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual((output / "frontend-disposable.conf").stat().st_mode & 0o777, 0o644)

    def test_invalid_sha_does_not_emit_an_environment_definition(self) -> None:
        result = subprocess.run(
            [
                sys.executable,
                str(RENDERER),
                "--schema",
                str(SCHEMA),
                "--manifest",
                str(MANIFEST),
                "--git-sha",
                "latest",
                "--compose-output",
                "/tmp/issue318-compose.yml",
                "--kubernetes-output",
                "/tmp/issue318-kubernetes.yml",
            ],
            cwd=REPOSITORY_ROOT,
            check=False,
            capture_output=True,
            text=True,
        )

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("full 40-character Git SHA", result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
