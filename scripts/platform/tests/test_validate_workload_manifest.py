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
BACKEND_APPLICATION_WORKLOADS = [
    "identity-service",
    "course-service",
    "assessment-api",
    "assessment-worker",
    "grade-service",
    "learning-service",
]
CURRENT_MONOLITH_MODULE_PATHS = {
    "identity-service": ["backend/src/main/java/com/onlinejudge/auth/**"],
    "course-service": ["backend/src/main/java/com/onlinejudge/crs/**"],
    "assessment-api": [
        "backend/src/main/java/com/onlinejudge/lab/**",
        "backend/src/main/java/com/onlinejudge/hwk/**",
    ],
    "assessment-worker": [
        "backend/src/main/java/com/onlinejudge/lab/**",
        "backend/src/main/java/com/onlinejudge/hwk/**",
    ],
    "grade-service": ["backend/src/main/java/com/onlinejudge/grd/**"],
    "learning-service": ["backend/src/main/java/com/onlinejudge/lrn/**"],
}
SHARED_BACKEND_INPUTS = [
    "backend/src/main/java/com/onlinejudge/common/**",
    "backend/src/main/java/com/onlinejudge/integration/**",
    "backend/src/main/resources/**",
    "backend/pom.xml",
]
MICROSERVICE_V2_CONTRACT_INPUTS = [
    "contracts/v2/**",
    "docs/adr/ADR-006-五业务服务与可靠消息契约.md",
    "docs/开发/D4-CROSS-SERVICE-共享契约.md",
    "docs/开发/D6-D7-五服务共享契约-v2.md",
    "docs/开发/D6-D7-五服务架构冻结-305.md",
    "docs/diagrams/arch/issue305-*.mmd",
    "scripts/ci/contract-verify.sh",
    "scripts/ci/verify-final-architecture-305.mjs",
    "scripts/ci/verify-microservice-contract-v2.mjs",
    "scripts/ci/verify-workflow-gates.test.sh",
]


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

    def test_migration_job_requires_the_checked_in_executable_runner(self) -> None:
        def mutate(manifest: dict) -> None:
            assessment = next(
                job for job in manifest["migrationJobs"] if job["schema"] == "assessment"
            )
            assessment["command"] = "./database/mysql/removed-migrate-service.sh --schema assessment"

        temporary_directory = self.write_variant(mutate)
        self.addCleanup(temporary_directory.cleanup)

        result = self.run_validator(Path(temporary_directory.name) / "workloads.json")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("migration runner", result.stderr)

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

    def test_current_monolith_module_paths_select_their_workloads(self) -> None:
        expected_workloads_by_path = {
            "backend/src/main/java/com/onlinejudge/auth/domain/AuthUser.java": ["identity-service"],
            "backend/src/main/java/com/onlinejudge/crs/domain/Course.java": ["course-service"],
            "backend/src/main/java/com/onlinejudge/lab/domain/LabExperiment.java": [
                "assessment-api",
                "assessment-worker",
            ],
            "backend/src/main/java/com/onlinejudge/hwk/domain/HomeworkSubmissionAttachment.java": [
                "assessment-api",
                "assessment-worker",
            ],
            "backend/src/main/java/com/onlinejudge/grd/domain/GradeItem.java": ["grade-service"],
            "backend/src/main/java/com/onlinejudge/lrn/domain/LearningTask.java": ["learning-service"],
        }

        for changed_path, expected_workloads in expected_workloads_by_path.items():
            with self.subTest(changed_path=changed_path):
                result = self.run_validator(MANIFEST, "--changed-path", changed_path)

                self.assertEqual(result.returncode, 0, result.stderr)
                resolved = json.loads(result.stdout)
                self.assertEqual(resolved["affectedWorkloads"], expected_workloads)

    def test_shared_backend_inputs_select_all_backend_application_workloads(self) -> None:
        changed_paths = [
            "backend/src/main/java/com/onlinejudge/common/event/NotificationEvent.java",
            "backend/src/main/java/com/onlinejudge/integration/course/CoursePermissionClient.java",
            "backend/src/main/resources/application.yml",
            "backend/pom.xml",
        ]

        for changed_path in changed_paths:
            with self.subTest(changed_path=changed_path):
                result = self.run_validator(MANIFEST, "--changed-path", changed_path)

                self.assertEqual(result.returncode, 0, result.stderr)
                resolved = json.loads(result.stdout)
                self.assertEqual(resolved["affectedWorkloads"], BACKEND_APPLICATION_WORKLOADS)

    def test_canonical_microservice_v2_contract_inputs_select_backend_consumers_and_producers(self) -> None:
        changed_paths = [
            "contracts/v2/openapi/course.openapi.json",
            "contracts/v2/asyncapi/events.asyncapi.json",
            "contracts/v2/examples/event-envelope.valid.json",
            "docs/adr/ADR-006-五业务服务与可靠消息契约.md",
            "docs/开发/D4-CROSS-SERVICE-共享契约.md",
            "docs/开发/D6-D7-五服务共享契约-v2.md",
            "docs/开发/D6-D7-五服务架构冻结-305.md",
            "docs/diagrams/arch/issue305-five-service-context.mmd",
            "scripts/ci/contract-verify.sh",
            "scripts/ci/verify-final-architecture-305.mjs",
            "scripts/ci/verify-microservice-contract-v2.mjs",
            "scripts/ci/verify-workflow-gates.test.sh",
        ]

        for changed_path in changed_paths:
            with self.subTest(changed_path=changed_path):
                result = self.run_validator(MANIFEST, "--changed-path", changed_path)

                self.assertEqual(result.returncode, 0, result.stderr)
                resolved = json.loads(result.stdout)
                self.assertEqual(resolved["affectedWorkloads"], BACKEND_APPLICATION_WORKLOADS)

    def test_removing_a_microservice_v2_contract_input_binding_is_rejected(self) -> None:
        def apply_microservice_v2_contract_inputs(manifest: dict) -> None:
            manifest["sharedServiceContractPaths"] = list(MICROSERVICE_V2_CONTRACT_INPUTS)
            for workload in manifest["workloads"]:
                if workload["name"] not in BACKEND_APPLICATION_WORKLOADS:
                    continue
                for required_path in MICROSERVICE_V2_CONTRACT_INPUTS:
                    if required_path not in workload["sourcePaths"]:
                        workload["sourcePaths"].append(required_path)
                    if required_path not in workload["pathTriggers"]:
                        workload["pathTriggers"].append(required_path)

        for workload_name in BACKEND_APPLICATION_WORKLOADS:
            for required_path in MICROSERVICE_V2_CONTRACT_INPUTS:
                with self.subTest(workload=workload_name, missing=required_path):
                    def mutate(manifest: dict) -> None:
                        apply_microservice_v2_contract_inputs(manifest)
                        workload = next(
                            item for item in manifest["workloads"] if item["name"] == workload_name
                        )
                        workload["sourcePaths"].remove(required_path)
                        workload["pathTriggers"].remove(required_path)

                    temporary_directory = self.write_variant(mutate)
                    self.addCleanup(temporary_directory.cleanup)

                    result = self.run_validator(Path(temporary_directory.name) / "workloads.json")

                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn("shared service contract input", result.stderr)
                    self.assertIn(workload_name, result.stderr)

    def test_removing_a_required_current_backend_input_is_rejected(self) -> None:
        def apply_current_backend_inputs(manifest: dict) -> None:
            for workload in manifest["workloads"]:
                required_paths = CURRENT_MONOLITH_MODULE_PATHS.get(workload["name"], [])
                if workload["name"] in BACKEND_APPLICATION_WORKLOADS:
                    required_paths = [*required_paths, *SHARED_BACKEND_INPUTS]
                for required_path in required_paths:
                    if required_path not in workload["sourcePaths"]:
                        workload["sourcePaths"].append(required_path)
                    if required_path not in workload["pathTriggers"]:
                        workload["pathTriggers"].append(required_path)

        mutations = [
            ("identity-service", "backend/src/main/java/com/onlinejudge/auth/**"),
            ("course-service", "backend/src/main/java/com/onlinejudge/crs/**"),
            ("assessment-api", "backend/src/main/java/com/onlinejudge/hwk/**"),
            ("assessment-worker", "backend/src/main/java/com/onlinejudge/lab/**"),
            ("grade-service", "backend/src/main/java/com/onlinejudge/grd/**"),
            ("learning-service", "backend/src/main/java/com/onlinejudge/lrn/**"),
            ("identity-service", "backend/src/main/java/com/onlinejudge/common/**"),
            ("identity-service", "backend/src/main/java/com/onlinejudge/integration/**"),
            ("identity-service", "backend/src/main/resources/**"),
            ("identity-service", "backend/pom.xml"),
        ]
        for workload_name, required_path in mutations:
            with self.subTest(workload=workload_name, missing=required_path):
                def mutate(manifest: dict) -> None:
                    apply_current_backend_inputs(manifest)
                    workload = next(
                        item for item in manifest["workloads"] if item["name"] == workload_name
                    )
                    workload["sourcePaths"].remove(required_path)
                    workload["pathTriggers"].remove(required_path)

                temporary_directory = self.write_variant(mutate)
                self.addCleanup(temporary_directory.cleanup)

                result = self.run_validator(Path(temporary_directory.name) / "workloads.json")

                self.assertNotEqual(result.returncode, 0)
                self.assertIn("current repository source path", result.stderr)
                self.assertIn(workload_name, result.stderr)

    def test_sbom_is_required_for_every_workload_image(self) -> None:
        def mutate(manifest: dict) -> None:
            manifest["workloads"][0]["image"]["sbomRequired"] = False

        temporary_directory = self.write_variant(mutate)
        self.addCleanup(temporary_directory.cleanup)

        result = self.run_validator(Path(temporary_directory.name) / "workloads.json")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("sbomRequired", result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
