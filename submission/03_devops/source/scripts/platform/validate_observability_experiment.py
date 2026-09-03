#!/usr/bin/env python3
"""Validate the #319 HPA and observability experiment before an environment exists."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any

from validate_workload_manifest import ManifestValidationError, load_json, validate


REQUIRED_MEASUREMENTS = {
    "pod_count", "cpu_usage", "memory_usage", "throughput", "request_latency_avg",
    "request_latency_p95", "error_rate", "timeline",
}
REQUIRED_DIAGNOSTICS = {
    "gateway_request_correlation", "rabbitmq_queue_backlog",
    "assessment_outbox_pending_and_lease", "grade_projection_watermark",
}
REQUIRED_EVIDENCE_METADATA = {
    "baseSha", "headSha", "deploymentVersion", "environment", "startedAtUtc", "finishedAtUtc",
}
REQUIRED_RAW_OUTPUTS = {"hpa", "pods", "resourceUsage", "loadSummary", "diagnostics"}


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workload-schema", type=Path, required=True)
    parser.add_argument("--workload-manifest", type=Path, required=True)
    parser.add_argument("--experiment", type=Path, required=True)
    return parser.parse_args()


def require_fields(value: Any, fields: set[str], location: str) -> None:
    if not isinstance(value, dict):
        raise ManifestValidationError(f"{location} must be an object")
    missing = sorted(fields.difference(value))
    if missing:
        raise ManifestValidationError(f"{location} missing required field(s): {', '.join(missing)}")


def names(items: Any, location: str) -> set[str]:
    if not isinstance(items, list) or not all(isinstance(item, dict) and isinstance(item.get("name"), str) for item in items):
        raise ManifestValidationError(f"{location} must be an array of named objects")
    return {item["name"] for item in items}


def validate_experiment(experiment: dict[str, Any], workloads: dict[str, dict[str, Any]]) -> None:
    require_fields(experiment, {"apiVersion", "kind", "metadata", "hpa", "readinessPolicy", "measurements", "diagnostics", "evidence"}, "experiment")
    if experiment["apiVersion"] != "operations.onlinejudge.io/v1" or experiment["kind"] != "ScalabilityExperiment":
        raise ManifestValidationError("experiment must be an operations.onlinejudge.io/v1 ScalabilityExperiment")
    metadata = experiment["metadata"]
    require_fields(metadata, {"ownerIssue", "environmentGate", "targetWorkload"}, "experiment.metadata")
    if metadata != {"ownerIssue": "#319", "environmentGate": "ENVIRONMENT_READY", "targetWorkload": "assessment-api"}:
        raise ManifestValidationError("experiment metadata must bind #319 to ENVIRONMENT_READY assessment-api")
    assessment = workloads.get(metadata["targetWorkload"])
    if assessment is None:
        raise ManifestValidationError("assessment-api is absent from the workload manifest")
    resources = assessment["resources"]
    if not resources.get("requests") or not resources.get("limits"):
        raise ManifestValidationError("assessment-api must declare CPU/memory requests and limits before HPA")
    health = assessment["health"]
    if set(health) != {"startup", "liveness", "readiness"}:
        raise ManifestValidationError("assessment-api must retain startup, liveness, and readiness probes")

    hpa = experiment["hpa"]
    require_fields(hpa, {"minReplicas", "maxReplicas", "metrics", "scaleDownStabilizationSeconds"}, "experiment.hpa")
    if not isinstance(hpa["minReplicas"], int) or not isinstance(hpa["maxReplicas"], int) or hpa["minReplicas"] < 1 or hpa["maxReplicas"] <= hpa["minReplicas"]:
        raise ManifestValidationError("HPA maxReplicas must be greater than a positive minReplicas")
    if not isinstance(hpa["scaleDownStabilizationSeconds"], int) or hpa["scaleDownStabilizationSeconds"] < 1:
        raise ManifestValidationError("HPA must declare positive scaleDownStabilizationSeconds")
    cpu_metrics = [metric for metric in hpa["metrics"] if isinstance(metric, dict) and metric.get("type") == "Resource" and metric.get("resource") == "cpu"]
    if len(cpu_metrics) != 1 or not isinstance(cpu_metrics[0].get("targetAverageUtilization"), int) or not 1 <= cpu_metrics[0]["targetAverageUtilization"] <= 100:
        raise ManifestValidationError("HPA must declare one CPU utilization metric from 1 to 100")

    policy = experiment["readinessPolicy"]
    require_fields(policy, {"criticalDependencies", "nonCriticalDependencies", "assertion"}, "experiment.readinessPolicy")
    if policy["criticalDependencies"] != health["readiness"]["requiredDependencies"]:
        raise ManifestValidationError("readiness critical dependencies must match assessment-api readiness probe")
    if "rabbitmq" not in policy["nonCriticalDependencies"] or "rabbitmq" in policy["criticalDependencies"]:
        raise ManifestValidationError("rabbitmq must be a noncritical readiness dependency")

    missing_measurements = sorted(REQUIRED_MEASUREMENTS.difference(names(experiment["measurements"], "experiment.measurements")))
    if missing_measurements:
        raise ManifestValidationError("missing required measurement(s): " + ", ".join(missing_measurements))
    diagnostics = experiment["diagnostics"]
    require_fields(diagnostics, {"requiredLogFields", "signals", "secretHandling"}, "experiment.diagnostics")
    if not {"requestId", "correlationId", "workload"}.issubset(diagnostics["requiredLogFields"]):
        raise ManifestValidationError("diagnostics must retain requestId, correlationId, and workload")
    missing_signals = sorted(REQUIRED_DIAGNOSTICS.difference(set(diagnostics["signals"])))
    if missing_signals:
        raise ManifestValidationError("missing required diagnostic signal(s): " + ", ".join(missing_signals))
    evidence = experiment["evidence"]
    require_fields(evidence, {"requiredMetadata", "requiredRawOutputs", "failureRetention"}, "experiment.evidence")
    missing_metadata = sorted(REQUIRED_EVIDENCE_METADATA.difference(set(evidence["requiredMetadata"])))
    missing_raw_outputs = sorted(REQUIRED_RAW_OUTPUTS.difference(set(evidence["requiredRawOutputs"])))
    if missing_metadata or missing_raw_outputs or evidence["failureRetention"] != "retain raw output and failure reason":
        raise ManifestValidationError("evidence must retain SHA/environment metadata, all raw outputs, and failure reason")


def main() -> int:
    options = arguments()
    try:
        workloads = validate(load_json(options.workload_manifest, "workload manifest"), load_json(options.workload_schema, "workload schema"))
        validate_experiment(load_json(options.experiment, "observability experiment"), workloads)
    except ManifestValidationError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2
    print("PASS: assessment-api HPA experiment has configuration, metrics, diagnostics, and evidence gates")
    return 0


if __name__ == "__main__":
    sys.exit(main())
