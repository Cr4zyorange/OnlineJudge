#!/usr/bin/env python3
"""Contract tests for the manifest-driven D7 delivery-plan generator (#318)."""

from __future__ import annotations

import json
import subprocess
import sys
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
PLANNER = REPOSITORY_ROOT / "scripts/platform/plan_delivery.py"
SCHEMA = REPOSITORY_ROOT / "deploy/platform/workload-manifest.schema.json"
MANIFEST = REPOSITORY_ROOT / "deploy/platform/workloads.json"
CONTRACT_GATE = REPOSITORY_ROOT / "scripts/ci/contract-verify.sh"
DELIVERY_CHECKPOINT = REPOSITORY_ROOT / "scripts/ci/delivery-checkpoint.sh"
GIT_SHA = "a" * 40


class DeliveryPlanTest(unittest.TestCase):
    """A release plan is a deterministic, secret-free manifest consumer."""

    def run_planner(self, *extra_args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                sys.executable,
                str(PLANNER),
                "--schema",
                str(SCHEMA),
                "--manifest",
                str(MANIFEST),
                "--git-sha",
                GIT_SHA,
                *extra_args,
            ],
            cwd=REPOSITORY_ROOT,
            check=False,
            capture_output=True,
            text=True,
        )

    def load_plan(self, *extra_args: str) -> dict:
        result = self.run_planner(*extra_args)
        self.assertEqual(result.returncode, 0, result.stderr)
        return json.loads(result.stdout)

    def test_full_release_plan_preserves_immutable_artifact_and_cutover_gates(self) -> None:
        plan = self.load_plan()

        self.assertEqual(plan["apiVersion"], "delivery.onlinejudge.io/v2")
        self.assertEqual(plan["kind"], "DeliveryPlan")
        self.assertEqual(plan["gitSha"], GIT_SHA)
        self.assertEqual(
            [build["workload"] for build in plan["builds"]],
            [
                "gateway",
                "identity-service",
                "course-service",
                "assessment-api",
                "assessment-worker",
                "grade-service",
                "frontend",
            ],
        )
        self.assertTrue(all(build["image"].endswith(f":{GIT_SHA}") for build in plan["builds"]))
        self.assertTrue(
            all(
                build["requiredArtifacts"] == ["image-digest", "sbom", "artifact-manifest"]
                for build in plan["builds"]
            )
        )
        self.assertEqual(
            [job["name"] for job in plan["releaseTemplate"]["migrationJobs"]],
            [
                "identity-migrations",
                "course-migrations",
                "assessment-migrations",
                "grade-migrations",
            ],
        )
        self.assertEqual(
            plan["releaseTemplate"]["rolloutPrerequisites"]["migrationJobs"],
            [
                "identity-migrations",
                "course-migrations",
                "assessment-migrations",
                "grade-migrations",
            ],
        )
        self.assertEqual(plan["releaseTemplate"]["trafficSwitch"]["workload"], "gateway")
        self.assertEqual(plan["releaseTemplate"]["rolloutStages"][-1], ["gateway"])
        self.assertNotIn("PASSWORD", json.dumps(plan))

    def test_shared_service_contract_change_builds_only_the_five_contract_participants(self) -> None:
        plan = self.load_plan(
            "--changed-path",
            "contracts/v2/asyncapi/events.asyncapi.json",
        )

        self.assertEqual(
            plan["selection"]["affectedWorkloads"],
            [
                "identity-service",
                "course-service",
                "assessment-api",
                "assessment-worker",
                "grade-service",
            ],
        )
        self.assertEqual(
            [build["workload"] for build in plan["builds"]],
            plan["selection"]["affectedWorkloads"],
        )

    def test_invalid_git_sha_is_rejected_before_a_delivery_plan_is_emitted(self) -> None:
        result = subprocess.run(
            [
                sys.executable,
                str(PLANNER),
                "--schema",
                str(SCHEMA),
                "--manifest",
                str(MANIFEST),
                "--git-sha",
                "latest",
            ],
            cwd=REPOSITORY_ROOT,
            check=False,
            capture_output=True,
            text=True,
        )

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("full 40-character Git SHA", result.stderr)

    def test_contract_gate_and_delivery_checkpoint_consume_the_same_planner(self) -> None:
        self.assertIn("scripts.platform.tests.test_plan_delivery", CONTRACT_GATE.read_text())
        checkpoint = DELIVERY_CHECKPOINT.read_text()
        self.assertIn("scripts/platform/plan_delivery.py", checkpoint)
        self.assertIn("plan.json", checkpoint)


if __name__ == "__main__":
    unittest.main(verbosity=2)
