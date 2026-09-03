#!/usr/bin/env python3
"""Contract tests for the #319 manifest-driven HPA experiment definition."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
VALIDATOR = REPOSITORY_ROOT / "scripts/platform/validate_observability_experiment.py"
WORKLOAD_SCHEMA = REPOSITORY_ROOT / "deploy/platform/workload-manifest.schema.json"
WORKLOAD_MANIFEST = REPOSITORY_ROOT / "deploy/platform/workloads.json"
EXPERIMENT = REPOSITORY_ROOT / "deploy/platform/observability-hpa-experiment.json"
CONTRACT_GATE = REPOSITORY_ROOT / "scripts/ci/contract-verify.sh"


class ObservabilityExperimentValidationTest(unittest.TestCase):
    """#319 must be an executable configuration contract, not a prose-only plan."""

    def run_validator(self, experiment: Path = EXPERIMENT) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(VALIDATOR),
                "--workload-schema",
                str(WORKLOAD_SCHEMA),
                "--workload-manifest",
                str(WORKLOAD_MANIFEST),
                "--experiment",
                str(experiment),
            ],
            cwd=REPOSITORY_ROOT,
            check=False,
            capture_output=True,
            text=True,
        )

    def write_variant(self, mutate) -> tempfile.TemporaryDirectory[str]:
        directory = tempfile.TemporaryDirectory()
        variant = Path(directory.name) / "experiment.json"
        document = json.loads(EXPERIMENT.read_text(encoding="utf-8"))
        mutate(document)
        variant.write_text(json.dumps(document), encoding="utf-8")
        return directory

    def test_assessment_hpa_experiment_has_all_required_metrics_diagnostics_and_evidence(self) -> None:
        result = self.run_validator()

        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("PASS: assessment-api HPA experiment", result.stdout)

    def test_hpa_without_cpu_resource_target_is_rejected(self) -> None:
        directory = self.write_variant(
            lambda document: document["hpa"]["metrics"].clear()
        )
        self.addCleanup(directory.cleanup)

        result = self.run_validator(Path(directory.name) / "experiment.json")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("CPU utilization metric", result.stderr)

    def test_missing_p95_or_error_rate_evidence_is_rejected(self) -> None:
        def mutate(document: dict) -> None:
            document["measurements"] = [
                measurement
                for measurement in document["measurements"]
                if measurement["name"] != "request_latency_p95"
            ]

        directory = self.write_variant(mutate)
        self.addCleanup(directory.cleanup)

        result = self.run_validator(Path(directory.name) / "experiment.json")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("request_latency_p95", result.stderr)

    def test_noncritical_rabbitmq_cannot_be_a_readiness_dependency(self) -> None:
        def mutate(document: dict) -> None:
            document["readinessPolicy"]["nonCriticalDependencies"] = []

        directory = self.write_variant(mutate)
        self.addCleanup(directory.cleanup)

        result = self.run_validator(Path(directory.name) / "experiment.json")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("rabbitmq", result.stderr)

    def test_diagnostic_contract_requires_request_correlation_and_recovery_watermarks(self) -> None:
        def mutate(document: dict) -> None:
            document["diagnostics"]["signals"] = document["diagnostics"]["signals"][:-1]

        directory = self.write_variant(mutate)
        self.addCleanup(directory.cleanup)

        result = self.run_validator(Path(directory.name) / "experiment.json")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("grade_projection_watermark", result.stderr)

    def test_contract_gate_runs_the_pre_environment_experiment_checks(self) -> None:
        gate = CONTRACT_GATE.read_text(encoding="utf-8")

        self.assertIn("validate_observability_experiment.py", gate)
        self.assertIn("scripts.platform.tests.test_validate_observability_experiment", gate)


if __name__ == "__main__":
    unittest.main(verbosity=2)
