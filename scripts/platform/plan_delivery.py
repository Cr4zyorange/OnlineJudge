#!/usr/bin/env python3
"""Generate a deterministic, secret-free delivery plan from the D7 manifest.

The plan is intentionally an input to CI/CD rather than a deployment executor.
It gives the delivery workflow one canonical build matrix, immutable artifact
requirements, migration sequence, rollout order, and traffic-switch gate while
the remaining service-specific Compose/Kubernetes adapters are delivered.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections.abc import Iterable
from pathlib import Path
from typing import Any

from validate_workload_manifest import (
    ManifestValidationError,
    load_json,
    resolve_changed_paths,
    validate,
)


FULL_GIT_SHA = re.compile(r"^[0-9a-f]{40}$")


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--schema", type=Path, required=True, help="workload-manifest JSON Schema")
    parser.add_argument("--manifest", type=Path, required=True, help="workload manifest")
    parser.add_argument("--git-sha", required=True, help="immutable full 40-character Git SHA")
    parser.add_argument(
        "--changed-path",
        action="append",
        default=[],
        help="repository-relative changed path; may be passed more than once",
    )
    return parser.parse_args()


def image_reference(workload: dict[str, Any], git_sha: str) -> str:
    image = workload["image"]
    return f"{image['repository']}:{image['tagTemplate'].replace('${GIT_SHA}', git_sha)}"


def ordered_rollout_stages(workloads: list[dict[str, Any]]) -> list[list[str]]:
    """Return stable readiness stages, keeping the gateway as the final cutover workload."""

    workload_by_name = {workload["name"]: workload for workload in workloads}
    infrastructure = {
        workload["name"]
        for workload in workloads
        if workload["type"] in {"database", "broker"}
    }
    application_names = [
        workload["name"]
        for workload in workloads
        if workload["name"] not in infrastructure and workload["name"] != "gateway"
    ]
    pending = set(application_names)
    completed = set(infrastructure)
    stages: list[list[str]] = []

    while pending:
        ready = [
            name
            for name in application_names
            if name in pending
            and set(workload_by_name[name]["dependsOn"]).issubset(completed)
        ]
        if not ready:
            unresolved = ", ".join(sorted(pending))
            raise ManifestValidationError(f"cannot resolve application rollout stage: {unresolved}")
        stages.append(ready)
        completed.update(ready)
        pending.difference_update(ready)

    gateway = workload_by_name["gateway"]
    if not set(gateway["dependsOn"]).issubset(completed):
        missing = sorted(set(gateway["dependsOn"]).difference(completed))
        raise ManifestValidationError(
            "gateway traffic switch cannot wait for unresolved dependency/dependencies: "
            + ", ".join(missing)
        )
    if any("gateway" in workload["dependsOn"] for workload in workloads if workload["name"] != "gateway"):
        raise ManifestValidationError("gateway must not be a prerequisite for another workload rollout")
    stages.append(["gateway"])
    return stages


def migration_plan(migration_jobs: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    """Keep only orchestration metadata; runtime Secret values never enter a plan."""

    return [
        {
            "name": job["name"],
            "schema": job["schema"],
            "forWorkload": job["forWorkload"],
            "command": job["command"],
            "dependsOnMigrationJobs": job["dependsOnMigrationJobs"],
            "blocksTraffic": job["blocksTraffic"],
            "failurePolicy": job["failurePolicy"],
            "retryPolicy": job["retryPolicy"],
            "rollback": job["rollback"],
        }
        for job in migration_jobs
    ]


def create_plan(
    manifest: dict[str, Any], workload_by_name: dict[str, dict[str, Any]], git_sha: str, changed_paths: list[str]
) -> dict[str, Any]:
    workloads = manifest["workloads"]
    if changed_paths:
        selection = resolve_changed_paths(manifest, workload_by_name, changed_paths)
    else:
        selection = {
            "changedPaths": [],
            "affectedWorkloads": [workload["name"] for workload in workloads],
            "reasons": {workload["name"]: {"fullRelease": True} for workload in workloads},
        }

    affected_names = set(selection["affectedWorkloads"])
    builds = [
        {
            "workload": workload["name"],
            "dockerfile": workload["dockerfile"],
            "image": image_reference(workload, git_sha),
            "requiredArtifacts": ["image-digest", "sbom", "artifact-manifest"],
        }
        for workload in workloads
        if workload["name"] in affected_names and workload["image"]["build"]
    ]
    infrastructure_workloads = [
        {
            "workload": workload["name"],
            "image": image_reference(workload, git_sha),
            "requiredArtifacts": ["sbom", "artifact-manifest"],
        }
        for workload in workloads
        if not workload["image"]["build"]
    ]
    rollout_stages = ordered_rollout_stages(workloads)
    migration_jobs = migration_plan(manifest["migrationJobs"])
    application_workloads = [
        workload["name"]
        for workload in workloads
        if workload["type"] not in {"database", "broker", "gateway"}
    ]

    return {
        "apiVersion": "delivery.onlinejudge.io/v2",
        "kind": "DeliveryPlan",
        "gitSha": git_sha,
        "selection": selection,
        "builds": builds,
        "releaseTemplate": {
            "infrastructureWorkloads": infrastructure_workloads,
            "migrationJobs": migration_jobs,
            "rolloutPrerequisites": {
                "infrastructureWorkloads": [
                    workload["workload"] for workload in infrastructure_workloads
                ],
                "migrationJobs": [job["name"] for job in migration_jobs],
            },
            "rolloutStages": rollout_stages,
            "trafficSwitch": {
                "workload": "gateway",
                "requiresMigrationJobs": [job["name"] for job in migration_jobs],
                "requiresReadyWorkloads": application_workloads,
                "requiredEvidence": [
                    "immutable image digest",
                    "SBOM and artifact manifest",
                    "migration stdout/stderr and exit codes",
                    "startup/liveness/readiness probe output",
                    "rollback drill evidence",
                ],
            },
        },
    }


def main() -> int:
    arguments = parse_arguments()
    if FULL_GIT_SHA.fullmatch(arguments.git_sha) is None:
        print("ERROR: --git-sha must be a full 40-character Git SHA", file=sys.stderr)
        return 2
    try:
        schema = load_json(arguments.schema, "schema")
        manifest = load_json(arguments.manifest, "manifest")
        workload_by_name = validate(manifest, schema)
        plan = create_plan(manifest, workload_by_name, arguments.git_sha, arguments.changed_path)
    except ManifestValidationError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2
    print(json.dumps(plan, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
