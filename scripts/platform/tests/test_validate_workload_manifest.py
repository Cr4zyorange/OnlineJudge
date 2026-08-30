#!/usr/bin/env python3
"""Contract tests for the D7 workload-manifest validator (#336)."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
VALIDATOR = REPOSITORY_ROOT / "scripts/platform/validate_workload_manifest.py"
SCHEMA = REPOSITORY_ROOT / "deploy/platform/workload-manifest.schema.json"
MANIFEST = REPOSITORY_ROOT / "deploy/platform/workloads.json"


class WorkloadManifestValidationTest(unittest.TestCase):
    """The accepted delivery input rejects unsafe topology declarations."""

    def run_validator(self, manifest: Path, *extra_args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(VALIDATOR),
                "--schema",
                str(SCHEMA),
                "--manifest",
                str(manifest),
                *extra_args,
            ],
            cwd=REPOSITORY_ROOT,
            check=False,
            capture_output=True,
            text=True,
        )

    def write_variant(self, mutate) -> tempfile.TemporaryDirectory[str]:
        temporary_directory = tempfile.TemporaryDirectory()
        variant_path = Path(temporary_directory.name) / "workloads.json"
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        mutate(manifest)
        variant_path.write_text(json.dumps(manifest), encoding="utf-8")
        return temporary_directory

    def test_production_manifest_conforms_to_schema_and_semantics(self) -> None:
        result = self.run_validator(MANIFEST)

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("PASS: 10 workloads", result.stdout)
        self.assertIn("5 ordered migration jobs", result.stdout)

    def test_missing_required_field_is_rejected(self) -> None:
        temporary_directory = self.write_variant(
            lambda manifest: manifest["workloads"][0].pop("health")
        )
        self.addCleanup(temporary_directory.cleanup)

        result = self.run_validator(Path(temporary_directory.name) / "workloads.json")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("missing required field 'health'", result.stderr)

    def test_duplicate_container_port_is_rejected(self) -> None:
        def mutate(manifest: dict) -> None:
            manifest["workloads"][1]["ports"][0]["containerPort"] = 8080

        temporary_directory = self.write_variant(mutate)
        self.addCleanup(temporary_directory.cleanup)

        result = self.run_validator(Path(temporary_directory.name) / "workloads.json")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("duplicate container port 8080", result.stderr)

    def test_unknown_dependency_is_rejected(self) -> None:
        def mutate(manifest: dict) -> None:
            manifest["workloads"][0]["dependsOn"].append("invented-service")

        temporary_directory = self.write_variant(mutate)
        self.addCleanup(temporary_directory.cleanup)

        result = self.run_validator(Path(temporary_directory.name) / "workloads.json")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("unknown dependency 'invented-service'", result.stderr)

    def test_cyclic_dependency_is_rejected(self) -> None:
        def mutate(manifest: dict) -> None:
            manifest["workloads"][0]["dependsOn"].append("frontend")

        temporary_directory = self.write_variant(mutate)
        self.addCleanup(temporary_directory.cleanup)

        result = self.run_validator(Path(temporary_directory.name) / "workloads.json")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("dependency cycle", result.stderr)

    def test_path_resolution_includes_shared_contract_consumers(self) -> None:
        result = self.run_validator(
            MANIFEST,
            "--changed-path",
            "docs/开发/D7-平台工作负载清单契约.md",
        )

        self.assertEqual(result.returncode, 0, result.stderr)
        resolved = json.loads(result.stdout)
        self.assertEqual(resolved["affectedWorkloads"], [
            "gateway",
            "identity-service",
            "course-service",
            "assessment-api",
            "assessment-worker",
            "grade-service",
            "learning-service",
            "frontend",
        ])


if __name__ == "__main__":
    unittest.main(verbosity=2)
