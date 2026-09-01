#!/usr/bin/env python3
"""RED/GREEN contract tests for the #318 manifest-backed environment renderer."""

from __future__ import annotations

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

    def test_assessment_worker_waits_for_the_shared_assessment_schema_migration(self) -> None:
        compose, _ = self.render()
        worker = compose[compose.index("\n  assessment-worker:") : compose.index("\n  grade-service:")]

        self.assertIn("      assessment-migrations:\n        condition: service_completed_successfully", worker)

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
