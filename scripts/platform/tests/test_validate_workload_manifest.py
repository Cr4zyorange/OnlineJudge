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
CONTRACT_GATE = REPOSITORY_ROOT / "scripts/ci/contract-verify.sh"


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
            frontend = next(
                workload for workload in manifest["workloads"] if workload["name"] == "frontend"
            )
            frontend["dependsOn"].append("gateway")

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

    def test_auth_source_change_selects_identity_service(self) -> None:
        result = self.run_validator(
            MANIFEST,
            "--changed-path",
            "backend/src/main/java/com/onlinejudge/auth/AuthApplication.java",
        )

        self.assertEqual(result.returncode, 0, result.stderr)
        resolved = json.loads(result.stdout)
        self.assertEqual(resolved["affectedWorkloads"], ["identity-service"])

    def test_source_path_without_trigger_is_rejected(self) -> None:
        def mutate(manifest: dict) -> None:
            identity = next(
                workload
                for workload in manifest["workloads"]
                if workload["name"] == "identity-service"
            )
            identity["pathTriggers"] = ["services/identity/**"]

        temporary_directory = self.write_variant(mutate)
        self.addCleanup(temporary_directory.cleanup)

        result = self.run_validator(Path(temporary_directory.name) / "workloads.json")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("source path", result.stderr)
        self.assertIn("identity-service", result.stderr)

    def test_migration_path_without_owning_workload_trigger_is_rejected(self) -> None:
        def mutate(manifest: dict) -> None:
            manifest["migrationJobs"][0]["sourcePaths"] = ["database/migrations/uncovered/**"]

        temporary_directory = self.write_variant(mutate)
        self.addCleanup(temporary_directory.cleanup)

        result = self.run_validator(Path(temporary_directory.name) / "workloads.json")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("migration source path", result.stderr)
        self.assertIn("identity-migrations", result.stderr)

    def test_gateway_models_the_browser_entry_for_frontend(self) -> None:
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        gateway = next(workload for workload in manifest["workloads"] if workload["name"] == "gateway")

        self.assertEqual(gateway["traffic"]["browserEntry"], {
            "path": "/",
            "targetWorkload": "frontend",
        })
        self.assertTrue(gateway["traffic"]["exposed"])
        self.assertIn("frontend", gateway["dependsOn"])

    def test_gateway_without_frontend_dependency_is_rejected(self) -> None:
        def mutate(manifest: dict) -> None:
            gateway = next(workload for workload in manifest["workloads"] if workload["name"] == "gateway")
            gateway["dependsOn"] = [
                dependency for dependency in gateway["dependsOn"] if dependency != "frontend"
            ]

        temporary_directory = self.write_variant(mutate)
        self.addCleanup(temporary_directory.cleanup)

        result = self.run_validator(Path(temporary_directory.name) / "workloads.json")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("browser entry", result.stderr)
        self.assertIn("frontend", result.stderr)

    def test_direct_frontend_exposure_is_rejected(self) -> None:
        def mutate(manifest: dict) -> None:
            frontend = next(
                workload for workload in manifest["workloads"] if workload["name"] == "frontend"
            )
            frontend["traffic"]["exposed"] = True

        temporary_directory = self.write_variant(mutate)
        self.addCleanup(temporary_directory.cleanup)

        result = self.run_validator(Path(temporary_directory.name) / "workloads.json")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("served through gateway", result.stderr)

    def test_contract_gate_runs_platform_validation_and_regression_suite(self) -> None:
        contract_gate = CONTRACT_GATE.read_text(encoding="utf-8")

        self.assertIn("scripts/platform/validate_workload_manifest.py", contract_gate)
        self.assertIn("scripts.platform.tests.test_validate_workload_manifest", contract_gate)


if __name__ == "__main__":
    unittest.main(verbosity=2)
