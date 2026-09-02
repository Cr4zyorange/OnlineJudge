#!/usr/bin/env python3
"""Contract checks for the executable #318 disposable delivery commands."""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[3]
BUILD = REPOSITORY_ROOT / "scripts/platform/build_workload_images.sh"
RUN = REPOSITORY_ROOT / "scripts/platform/run_disposable_environment.sh"
ROLLBACK = REPOSITORY_ROOT / "scripts/platform/rollback_disposable_environment.sh"
KUBERNETES_DEPLOY = REPOSITORY_ROOT / "scripts/platform/deploy_kubernetes_disposable_environment.sh"
CI_DELIVERY = REPOSITORY_ROOT / "scripts/ci/disposable-delivery.sh"
HPA_EXPERIMENT = REPOSITORY_ROOT / "scripts/platform/run_hpa_observability_experiment.sh"
CI_WORKFLOW = REPOSITORY_ROOT / ".github/workflows/ci.yml"


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
        self.assertIn("PYTHONDONTWRITEBYTECODE=1 python3 \"$planner\"", source)

    def test_build_command_rejects_a_sha_that_does_not_identify_the_checkout(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fake_docker = Path(directory) / "docker"
            fake_docker.write_text("#!/usr/bin/env sh\nexit 0\n", encoding="utf-8")
            fake_docker.chmod(0o755)
            result = subprocess.run(
                [
                    "bash",
                    str(BUILD),
                    "--git-sha",
                    "0" * 40,
                    "--skip-tests",
                    "--output-dir",
                    str(Path(directory) / "artifacts"),
                ],
                cwd=REPOSITORY_ROOT,
                env={"PATH": f"{directory}:{Path('/usr/bin')}:{Path('/bin')}"},
                capture_output=True,
                text=True,
                check=False,
            )

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("does not match checked-out HEAD", result.stderr)

    def test_run_command_has_success_and_controlled_failure_modes(self) -> None:
        source = self.assert_help(RUN)
        self.assertIn("--inject-failure", source)
        self.assertIn("migration", source)
        self.assertIn("readiness", source)
        self.assertIn("ENVIRONMENT_READY", source)
        self.assertIn("collect_diagnostics startup-failure", source)
        self.assertIn("json.loads(line)", source)

    def test_hpa_experiment_captures_scale_timeline_and_diagnostics_on_success_or_failure(self) -> None:
        source = self.assert_help(HPA_EXPERIMENT)
        self.assertIn("--gateway-url", source)
        self.assertIn("top pod", source)
        self.assertIn("get hpa", source)
        self.assertIn("request_latency_p95", source)
        self.assertIn("rabbitmq_queue_backlog", source)
        self.assertIn("assessment_outbox_pending_and_lease", source)
        self.assertIn("grade_projection_watermark", source)
        # AC-319-03: the outage phase must prove RabbitMQ really went away
        # (statefulset readyReplicas at zero AND service endpoints empty) and
        # must record assessment-api availability samples from inside that
        # verified outage window; a rollout status on an already-ready
        # deployment returns immediately and is not evidence.
        self.assertIn("rabbitmq_outage_window_seconds", source)
        self.assertIn("status.readyReplicas", source)
        self.assertIn("endpoints rabbitmq", source)
        self.assertIn("endpoints assessment-api", source)
        self.assertIn("availableReplicas", source)
        self.assertIn("rabbitmq confirmed unavailable", source)
        self.assertIn("rabbitmq restored", source)
        # AC-319-04: the two diagnostics signals must carry raw database
        # values, not just application logs; the logs stay as context files.
        self.assertIn("lease_owner, lease_until, heartbeat_at", source)
        self.assertIn("grade_source_projection_watermark", source)
        self.assertIn("assessment-outbox-lease-timeline", source)
        self.assertIn("assessment-api-applog", source)
        self.assertIn("grade-service-applog", source)
        self.assertIn('"finishedAtUtc"', source)
        self.assertIn("request_id", source)
        self.assertIn("rabbitmq_outage=1", source)
        self.assertIn("kubectl -n \"$namespace\" scale statefulset/rabbitmq", source)
        self.assertIn("wait_for_replicas", source)
        self.assertIn("scaled up", source)
        self.assertIn("scaled down", source)
        self.assertIn("--authorization-file", source)
        self.assertIn("--request-method", source)
        self.assertIn("--request-body-file", source)
        self.assertIn("may be repeated", source)
        self.assertIn("request_urls+=(", source)
        self.assertIn("--noproxy", source)
        # wait_for_replicas must branch on the comparison explicitly; a bare
        # `A && B || C && D` chain parses left-associative and the -le branch
        # would veto every successful scale-up, so each branch is guarded by
        # its own comparison instead.
        self.assertIn('[[ "$comparison" == "-gt" ]] && (( current > baseline ))', source)
        self.assertIn('[[ "$comparison" == "-le" ]] && (( current <= baseline ))', source)
        # The committed runner SHA and the deployed image SHA must be recorded
        # separately: a run is only reproducible when the evidence states which
        # commit executed the experiment and which GIT_SHA was under test.
        self.assertIn('GIT_SHA")].value', source)
        self.assertIn('"deploymentVersion": sys.argv[11]', source)
        self.assertNotIn('"deploymentVersion": sys.argv[7]', source)
        self.assertIn("EXPERIMENT_FAILURE", source)
        self.assertIn("EXPERIMENT_READY", source)

    def test_rollback_command_requires_an_immutable_artifact_manifest(self) -> None:
        source = self.assert_help(ROLLBACK)
        self.assertIn("artifact-manifest.json", source)
        self.assertIn("--from-sha", source)
        self.assertIn("docker image inspect", source)
        self.assertIn("expected_images", source)
        self.assertIn('artifact.get("image") != expected_images[workload]', source)
        self.assertIn('show "$from_sha:deploy/platform/workloads.json"', source)

    def test_rollback_rejects_a_manifest_with_missing_artifact_records_before_docker_runs(self) -> None:
        git_sha = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=REPOSITORY_ROOT, capture_output=True, text=True, check=True
        ).stdout.strip()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact_manifest = root / "artifact-manifest.json"
            artifact_manifest.write_text(
                '{"apiVersion":"delivery.onlinejudge.io/v2","kind":"ArtifactManifest","gitSha":"'
                + git_sha
                + '","artifacts":[]}\n',
                encoding="utf-8",
            )
            env_file = root / "runtime.env"
            env_file.write_text("MYSQL_ROOT_PASSWORD=not-a-secret-value\n", encoding="utf-8")
            result = subprocess.run(
                [
                    "bash",
                    str(ROLLBACK),
                    "--from-sha",
                    git_sha,
                    "--artifact-manifest",
                    str(artifact_manifest),
                    "--env-file",
                    str(env_file),
                    "--project-name",
                    "issue318-test",
                    "--output-dir",
                    str(root / "out"),
                ],
                cwd=REPOSITORY_ROOT,
                capture_output=True,
                text=True,
                check=False,
            )

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("exactly the expected workload and migration-runner records", result.stderr)

    def test_rollback_rejects_an_attacker_image_reference_before_compose_up(self) -> None:
        git_sha = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=REPOSITORY_ROOT, capture_output=True, text=True, check=True
        ).stdout.strip()
        workload_manifest = json.loads(
            (REPOSITORY_ROOT / "deploy/platform/workloads.json").read_text(encoding="utf-8")
        )
        artifacts = [
            {
                "workload": workload["name"],
                "image": f"attacker.invalid/{workload['name']}:{git_sha}"
                if workload["image"]["build"]
                else f"attacker.invalid/{workload['name']}:fixed",
                "digest": "sha256:" + "a" * 64,
                "source": "source" if workload["image"]["build"] else "infrastructure",
            }
            for workload in workload_manifest["workloads"]
        ]
        artifacts.append(
            {
                "workload": "platform-migration-runner",
                "image": f"attacker.invalid/platform-migration-runner:{git_sha}",
                "digest": "sha256:" + "a" * 64,
                "source": "source",
            }
        )

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            artifact_manifest = root / "artifact-manifest.json"
            artifact_manifest.write_text(
                json.dumps(
                    {
                        "apiVersion": "delivery.onlinejudge.io/v2",
                        "kind": "ArtifactManifest",
                        "gitSha": git_sha,
                        "artifacts": artifacts,
                    }
                ),
                encoding="utf-8",
            )
            env_file = root / "runtime.env"
            env_file.write_text("MYSQL_ROOT_PASSWORD=not-a-secret-value\n", encoding="utf-8")
            docker_calls = root / "docker-calls.log"
            fake_docker = root / "docker"
            fake_docker.write_text(
                "#!/usr/bin/env sh\n"
                f"printf '%s\\n' \"$*\" >> {docker_calls}\n"
                "if [ \"$1\" = image ]; then printf '%s\\n' sha256:" + "a" * 64 + "; fi\n"
                "exit 0\n",
                encoding="utf-8",
            )
            fake_docker.chmod(0o755)
            result = subprocess.run(
                [
                    "bash",
                    str(ROLLBACK),
                    "--from-sha",
                    git_sha,
                    "--artifact-manifest",
                    str(artifact_manifest),
                    "--env-file",
                    str(env_file),
                    "--project-name",
                    "issue318-test",
                    "--output-dir",
                    str(root / "out"),
                ],
                cwd=REPOSITORY_ROOT,
                env={**os.environ, "PATH": f"{root}:{os.environ['PATH']}"},
                capture_output=True,
                text=True,
                check=False,
            )
            calls = docker_calls.read_text(encoding="utf-8") if docker_calls.exists() else ""

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("does not match the workload's expected image reference", result.stderr)
        self.assertNotIn("compose", calls)

    def test_kubernetes_deployer_stops_before_later_stages_when_a_migration_job_fails(self) -> None:
        git_sha = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=REPOSITORY_ROOT, capture_output=True, text=True, check=True
        ).stdout.strip()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            commands = root / "kubectl.log"
            fake_kubectl = root / "kubectl"
            fake_kubectl.write_text(
                "#!/usr/bin/env sh\n"
                f"printf '%s\\n' \"$*\" >> {commands}\n"
                "case \"$*\" in\n"
                "  *\"wait --for=condition=complete job/identity-migrations\"*) exit 1 ;;\n"
                "esac\n"
                "exit 0\n",
                encoding="utf-8",
            )
            fake_kubectl.chmod(0o755)
            result = subprocess.run(
                [
                    "bash",
                    str(KUBERNETES_DEPLOY),
                    "--git-sha",
                    git_sha,
                    "--namespace",
                    "issue318-test",
                    "--output-dir",
                    str(root / "out"),
                ],
                cwd=REPOSITORY_ROOT,
                env={
                    "KUBECTL_BIN": str(fake_kubectl),
                    "PATH": f"{Path(sys.executable).parent}:/usr/bin:/bin",
                },
                capture_output=True,
                text=True,
                check=False,
            )
            calls = commands.read_text(encoding="utf-8")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("10-infrastructure.yaml", calls)
        self.assertIn("20-runtime-account-init.yaml", calls)
        self.assertIn("30-identity-migrations.yaml", calls)
        self.assertNotIn("40-course-migrations.yaml", calls)
        self.assertNotIn("70-applications.yaml", calls)
        self.assertNotIn("80-gateway.yaml", calls)

    def test_ci_delivery_executor_passes_changed_paths_into_the_delivery_plan(self) -> None:
        git_sha = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=REPOSITORY_ROOT, capture_output=True, text=True, check=True
        ).stdout.strip()
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "delivery"
            result = subprocess.run(
                [
                    "bash",
                    str(CI_DELIVERY),
                    "--checkout",
                    str(REPOSITORY_ROOT),
                    "--git-sha",
                    git_sha,
                    "--changed-path",
                    "services/course/pom.xml",
                    "--output-dir",
                    str(output),
                    "--dry-run",
                ],
                cwd=REPOSITORY_ROOT,
                capture_output=True,
                text=True,
                check=False,
            )

            self.assertEqual(result.returncode, 0, result.stderr)
            plan = __import__("json").loads((output / "selected-plan.json").read_text(encoding="utf-8"))

        self.assertEqual(plan["selection"]["affectedWorkloads"], ["course-service"])
        self.assertEqual([build["workload"] for build in plan["builds"]], ["course-service"])

    def test_ci_delivery_executor_treats_an_empty_change_set_as_a_full_release(self) -> None:
        git_sha = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=REPOSITORY_ROOT, capture_output=True, text=True, check=True
        ).stdout.strip()
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "delivery"
            result = subprocess.run(
                [
                    "bash",
                    str(CI_DELIVERY),
                    "--checkout",
                    str(REPOSITORY_ROOT),
                    "--git-sha",
                    git_sha,
                    "--output-dir",
                    str(output),
                    "--dry-run",
                ],
                cwd=REPOSITORY_ROOT,
                capture_output=True,
                text=True,
                check=False,
            )

            self.assertEqual(result.returncode, 0, result.stderr)
            plan = __import__("json").loads((output / "selected-plan.json").read_text(encoding="utf-8"))

        self.assertEqual(plan["selection"]["changedPaths"], [])
        self.assertEqual(len(plan["selection"]["affectedWorkloads"]), 9)

    def test_ci_delivery_installs_a_checksum_verified_docker_scout_before_sbom_generation(self) -> None:
        workflow = CI_WORKFLOW.read_text(encoding="utf-8")
        scout_archive = "docker-scout_1.24.0_linux_amd64.tar.gz"
        scout_digest = "f4e2814bd61040365153d5b964b144cb2dc6ee536a68b5bac4cadf00fc0ec34b"

        self.assertIn(scout_archive, workflow)
        self.assertIn(scout_digest, workflow)
        self.assertIn("sha256sum --check", workflow)
        self.assertIn("cli-plugins/docker-scout", workflow)
        self.assertIn("docker scout version", workflow)
        self.assertLess(
            workflow.index("docker scout version"),
            workflow.index("scripts/ci/disposable-delivery.sh"),
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
