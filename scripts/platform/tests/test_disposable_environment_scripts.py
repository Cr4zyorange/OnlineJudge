#!/usr/bin/env python3
"""Contract checks for the executable #318 disposable delivery commands."""

from __future__ import annotations

import subprocess
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
BUILD = REPOSITORY_ROOT / "scripts/platform/build_workload_images.sh"
RUN = REPOSITORY_ROOT / "scripts/platform/run_disposable_environment.sh"
ROLLBACK = REPOSITORY_ROOT / "scripts/platform/rollback_disposable_environment.sh"


class DisposableEnvironmentScriptsTest(unittest.TestCase):
    def assert_help(self, script: Path) -> str:
        result = subprocess.run(["bash", str(script), "--help"], capture_output=True, text=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Usage:", result.stdout)
        return script.read_text()

    def test_build_command_produces_digest_sbom_and_artifact_manifest(self) -> None:
        source = self.assert_help(BUILD)
        self.assertIn("docker scout sbom", source)
        self.assertIn("artifact-manifest.json", source)
        self.assertIn("migration-runner.Dockerfile", source)
        self.assertIn("OJ318_JAVA_HOME", source)
        self.assertIn("attest_prebuilt", source)
        self.assertIn("infrastructureWorkloads", source)
        self.assertIn("retry 3 docker build", source)

    def test_run_command_has_success_and_controlled_failure_modes(self) -> None:
        source = self.assert_help(RUN)
        self.assertIn("--inject-failure", source)
        self.assertIn("migration", source)
        self.assertIn("readiness", source)
        self.assertIn("ENVIRONMENT_READY", source)
        self.assertIn("collect_diagnostics startup-failure", source)
        self.assertIn("json.loads(line)", source)

    def test_rollback_command_requires_an_immutable_artifact_manifest(self) -> None:
        source = self.assert_help(ROLLBACK)
        self.assertIn("artifact-manifest.json", source)
        self.assertIn("--from-sha", source)
        self.assertIn("docker image inspect", source)
        self.assertIn('artifact.get("source") != "infrastructure"', source)
        self.assertIn('show "$from_sha:deploy/platform/workloads.json"', source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
