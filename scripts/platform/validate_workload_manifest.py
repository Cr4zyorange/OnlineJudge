#!/usr/bin/env python3
"""Validate and query the declarative v2 delivery workload manifest.

The validator deliberately depends only on the Python standard library so that a
clean GitHub-hosted runner, a developer checkout, and a deployment job all
evaluate exactly the same contract.  The JSON Schema is the published machine
interface; the semantic checks below cover relationships JSON Schema cannot
express (unique ports, known dependencies, topology cycles, and migration
ordering).
"""

from __future__ import annotations

import argparse
import fnmatch
import json
import re
import sys
from collections.abc import Iterable
from pathlib import Path
from typing import Any


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]


CORE_WORKLOADS = {
    "gateway",
    "identity-service",
    "course-service",
    "assessment-api",
    "assessment-worker",
    "grade-service",
    "learning-service",
    "frontend",
    "rabbitmq",
    "mysql",
}
ORDERED_SCHEMAS = ["identity", "course", "assessment", "grade", "learning"]
BACKEND_APPLICATION_WORKLOADS = (
    "identity-service",
    "course-service",
    "assessment-api",
    "assessment-worker",
    "grade-service",
    "learning-service",
)
CURRENT_MONOLITH_MODULE_PATHS = {
    "identity-service": ("backend/src/main/java/com/onlinejudge/auth/**",),
    "course-service": ("backend/src/main/java/com/onlinejudge/crs/**",),
    "assessment-api": (
        "backend/src/main/java/com/onlinejudge/lab/**",
        "backend/src/main/java/com/onlinejudge/hwk/**",
    ),
    "assessment-worker": (
        "backend/src/main/java/com/onlinejudge/lab/**",
        "backend/src/main/java/com/onlinejudge/hwk/**",
    ),
    "grade-service": ("backend/src/main/java/com/onlinejudge/grd/**",),
    "learning-service": ("backend/src/main/java/com/onlinejudge/lrn/**",),
}
SHARED_BACKEND_INPUTS = (
    "backend/src/main/java/com/onlinejudge/common/**",
    "backend/src/main/java/com/onlinejudge/integration/**",
    "backend/src/main/resources/**",
    "backend/pom.xml",
)
MICROSERVICE_V2_SHARED_SERVICE_CONTRACT_INPUTS = (
    "contracts/v2/**",
    "docs/adr/ADR-006-五业务服务与可靠消息契约.md",
    "docs/开发/D6-D7-五服务共享契约-v2.md",
    "docs/开发/D6-D7-五服务架构冻结-305.md",
    "docs/diagrams/arch/issue305-*.mmd",
    "scripts/ci/contract-verify.sh",
    "scripts/ci/verify-final-architecture-305.mjs",
    "scripts/ci/verify-microservice-contract-v2.mjs",
    "scripts/ci/verify-workflow-gates.test.sh",
)


class ManifestValidationError(ValueError):
    """An actionable declaration error that must block consumption."""


def load_json(path: Path, role: str) -> dict[str, Any]:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise ManifestValidationError(f"{role} not found: {path}") from error
    except json.JSONDecodeError as error:
        raise ManifestValidationError(
            f"invalid {role} JSON at line {error.lineno}, column {error.colno}: {error.msg}"
        ) from error
    if not isinstance(document, dict):
        raise ManifestValidationError(f"{role} root must be an object")
    return document


def required_fields(value: Any, fields: Iterable[str], location: str) -> None:
    if not isinstance(value, dict):
        raise ManifestValidationError(f"{location} must be an object")
    for field in fields:
        if field not in value:
            raise ManifestValidationError(f"{location}: missing required field '{field}'")


def reject_unknown_fields(value: dict[str, Any], allowed: set[str], location: str) -> None:
    unknown = sorted(set(value).difference(allowed))
    if unknown:
        raise ManifestValidationError(f"{location}: unknown field(s): {', '.join(unknown)}")


def validate_probe(probe: Any, location: str) -> None:
    required_fields(probe, ("kind", "target", "requiredDependencies"), location)
    if probe["kind"] not in {"http", "command", "process"}:
        raise ManifestValidationError(f"{location}.kind must be http, command, or process")
    if not isinstance(probe["target"], str) or not probe["target"]:
        raise ManifestValidationError(f"{location}.target must be a non-empty string")
    if not isinstance(probe["requiredDependencies"], list):
        raise ManifestValidationError(f"{location}.requiredDependencies must be an array")


def validate_schema_shape(schema: dict[str, Any]) -> None:
    required_fields(schema, ("$schema", "$id", "properties", "$defs"), "schema")
    if schema.get("$schema") != "https://json-schema.org/draft/2020-12/schema":
        raise ManifestValidationError("schema must declare JSON Schema draft 2020-12")
    if "workload" not in schema["$defs"] or "migrationJob" not in schema["$defs"]:
        raise ManifestValidationError("schema must define workload and migrationJob definitions")


def resolve_schema_reference(schema: dict[str, Any], root_schema: dict[str, Any]) -> dict[str, Any]:
    reference = schema.get("$ref")
    if reference is None:
        return schema
    if not reference.startswith("#/"):
        raise ManifestValidationError(f"unsupported external schema reference '{reference}'")
    target: Any = root_schema
    for component in reference[2:].split("/"):
        if not isinstance(target, dict) or component not in target:
            raise ManifestValidationError(f"unresolved schema reference '{reference}'")
        target = target[component]
    if not isinstance(target, dict):
        raise ManifestValidationError(f"schema reference '{reference}' must resolve to an object")
    return target


def matches_json_type(value: Any, expected_type: str) -> bool:
    return {
        "object": isinstance(value, dict),
        "array": isinstance(value, list),
        "string": isinstance(value, str),
        "integer": isinstance(value, int) and not isinstance(value, bool),
        "boolean": isinstance(value, bool),
        "null": value is None,
    }.get(expected_type, False)


def validate_against_schema(
    value: Any, schema: dict[str, Any], root_schema: dict[str, Any], location: str
) -> None:
    """Evaluate the published JSON-Schema subset without a runner dependency."""
    schema = resolve_schema_reference(schema, root_schema)
    if "const" in schema and value != schema["const"]:
        raise ManifestValidationError(f"{location} must equal {schema['const']!r}")
    if "enum" in schema and value not in schema["enum"]:
        allowed = ", ".join(repr(item) for item in schema["enum"])
        raise ManifestValidationError(f"{location} must be one of {allowed}")
    expected_type = schema.get("type")
    if expected_type:
        types = expected_type if isinstance(expected_type, list) else [expected_type]
        if not any(matches_json_type(value, item) for item in types):
            rendered_types = " or ".join(types)
            raise ManifestValidationError(f"{location} must be {rendered_types}")
    if isinstance(value, dict):
        required = schema.get("required", [])
        for field in required:
            if field not in value:
                raise ManifestValidationError(f"{location}: missing required field '{field}'")
        properties = schema.get("properties", {})
        if schema.get("additionalProperties") is False:
            unknown = sorted(set(value).difference(properties))
            if unknown:
                raise ManifestValidationError(f"{location}: unknown field(s): {', '.join(unknown)}")
        for field, field_schema in properties.items():
            if field in value:
                validate_against_schema(value[field], field_schema, root_schema, f"{location}.{field}")
    if isinstance(value, list):
        minimum_items = schema.get("minItems")
        if minimum_items is not None and len(value) < minimum_items:
            raise ManifestValidationError(f"{location} must contain at least {minimum_items} item(s)")
        if "items" in schema:
            for index, item in enumerate(value):
                validate_against_schema(item, schema["items"], root_schema, f"{location}[{index}]")
    if isinstance(value, str):
        minimum_length = schema.get("minLength")
        if minimum_length is not None and len(value) < minimum_length:
            raise ManifestValidationError(f"{location} must be at least {minimum_length} character(s)")
        pattern = schema.get("pattern")
        if pattern is not None and re.match(pattern, value) is None:
            raise ManifestValidationError(f"{location} does not match required pattern {pattern!r}")
    if isinstance(value, int) and not isinstance(value, bool):
        minimum = schema.get("minimum")
        maximum = schema.get("maximum")
        if minimum is not None and value < minimum:
            raise ManifestValidationError(f"{location} must be at least {minimum}")
        if maximum is not None and value > maximum:
            raise ManifestValidationError(f"{location} must be at most {maximum}")


def validate_manifest_shape(manifest: dict[str, Any]) -> list[dict[str, Any]]:
    root_fields = {
        "apiVersion",
        "kind",
        "metadata",
        "sharedTriggerPaths",
        "sharedServiceContractPaths",
        "workloads",
        "migrationJobs",
        "promotion",
        "d3Retirement",
    }
    required_fields(manifest, root_fields, "manifest")
    reject_unknown_fields(manifest, root_fields, "manifest")
    if manifest["apiVersion"] != "delivery.onlinejudge.io/v2":
        raise ManifestValidationError("manifest.apiVersion must be delivery.onlinejudge.io/v2")
    if manifest["kind"] != "WorkloadManifest":
        raise ManifestValidationError("manifest.kind must be WorkloadManifest")
    required_fields(manifest["metadata"], ("name", "version", "ownerIssue"), "metadata")
    if manifest["metadata"] != {"name": "onlinejudge-platform", "version": "v2", "ownerIssue": "#336"}:
        raise ManifestValidationError("metadata must identify the #336 onlinejudge-platform v2 contract")
    if not isinstance(manifest["sharedTriggerPaths"], list) or not manifest["sharedTriggerPaths"]:
        raise ManifestValidationError("manifest.sharedTriggerPaths must be a non-empty array")
    if (
        not isinstance(manifest["sharedServiceContractPaths"], list)
        or not manifest["sharedServiceContractPaths"]
    ):
        raise ManifestValidationError(
            "manifest.sharedServiceContractPaths must be a non-empty array"
        )
    workloads = manifest["workloads"]
    if not isinstance(workloads, list) or not workloads:
        raise ManifestValidationError("manifest.workloads must be a non-empty array")
    if not isinstance(manifest["migrationJobs"], list) or not manifest["migrationJobs"]:
        raise ManifestValidationError("manifest.migrationJobs must be a non-empty array")
    return workloads


def validate_workloads(workloads: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    workload_fields = {
        "name",
        "type",
        "sourcePaths",
        "dockerfile",
        "image",
        "ports",
        "health",
        "dependsOn",
        "migrationJob",
        "configuration",
        "secrets",
        "resources",
        "pathTriggers",
        "traffic",
    }
    workload_by_name: dict[str, dict[str, Any]] = {}
    port_owner: dict[int, str] = {}
    for index, workload in enumerate(workloads):
        location = f"workloads[{index}]"
        required_fields(workload, workload_fields, location)
        reject_unknown_fields(workload, workload_fields, location)
        name = workload["name"]
        if not isinstance(name, str) or not name:
            raise ManifestValidationError(f"{location}.name must be a non-empty string")
        if name in workload_by_name:
            raise ManifestValidationError(f"duplicate workload name '{name}'")
        workload_by_name[name] = workload
        if workload["type"] not in {"gateway", "service", "worker", "frontend", "broker", "database"}:
            raise ManifestValidationError(f"{location}.type has an unsupported value '{workload['type']}'")
        for field in ("sourcePaths", "ports", "dependsOn", "configuration", "secrets", "pathTriggers"):
            if not isinstance(workload[field], list):
                raise ManifestValidationError(f"{location}.{field} must be an array")
        if not workload["sourcePaths"] or not workload["pathTriggers"]:
            raise ManifestValidationError(f"{location} must declare sourcePaths and pathTriggers")
        if workload["dockerfile"] is not None and not isinstance(workload["dockerfile"], str):
            raise ManifestValidationError(f"{location}.dockerfile must be a string or null")
        image = workload["image"]
        required_fields(image, ("repository", "tagTemplate", "build", "sbomRequired"), f"{location}.image")
        if not isinstance(image["build"], bool) or not isinstance(image["sbomRequired"], bool):
            raise ManifestValidationError(f"{location}.image build and sbomRequired must be booleans")
        if image["sbomRequired"] is not True:
            raise ManifestValidationError(
                f"{location}.image.sbomRequired must be true for every workload image"
            )
        if image["build"] and "${GIT_SHA}" not in image["tagTemplate"]:
            raise ManifestValidationError(f"{location}.image.tagTemplate must contain ${{GIT_SHA}} for a built workload")
        if image["build"] and workload["dockerfile"] is None:
            raise ManifestValidationError(f"{location} builds an image but has no Dockerfile")
        for port in workload["ports"]:
            required_fields(port, ("name", "containerPort", "protocol", "external"), f"{location}.ports")
            container_port = port["containerPort"]
            if not isinstance(container_port, int) or not 1 <= container_port <= 65535:
                raise ManifestValidationError(f"{location} has invalid container port {container_port!r}")
            if container_port in port_owner:
                raise ManifestValidationError(
                    f"duplicate container port {container_port}: {port_owner[container_port]} and {name}"
                )
            port_owner[container_port] = name
        health = workload["health"]
        required_fields(health, ("startup", "liveness", "readiness"), f"{location}.health")
        for probe_name in ("startup", "liveness", "readiness"):
            validate_probe(health[probe_name], f"{location}.health.{probe_name}")
        resources = workload["resources"]
        required_fields(resources, ("requests", "limits"), f"{location}.resources")
        for resource_type in ("requests", "limits"):
            required_fields(resources[resource_type], ("cpu", "memory"), f"{location}.resources.{resource_type}")
        for secret in workload["secrets"]:
            required_fields(secret, ("key", "injectedAs", "redact", "imageBuild"), f"{location}.secrets")
            reject_unknown_fields(secret, {"key", "injectedAs", "redact", "imageBuild"}, f"{location}.secrets")
            if secret["redact"] is not True or secret["imageBuild"] != "forbidden":
                raise ManifestValidationError(f"{location} secret {secret['key']} must be redacted and forbidden at image build")
    missing_core = CORE_WORKLOADS.difference(workload_by_name)
    if missing_core:
        raise ManifestValidationError(f"manifest missing required v2 workload(s): {', '.join(sorted(missing_core))}")
    return workload_by_name


def path_pattern_covers(source_path: str, trigger_pattern: str) -> bool:
    """Return whether every path selected by source_path is selected by trigger_pattern.

    The manifest intentionally uses repository-relative directory patterns.  A
    narrower trigger must never be accepted for a workload source path: doing
    so would make the build matrix silently omit source changes.  We support
    exact paths and the directory ``/**`` form used by this contract; arbitrary
    glob-to-glob containment is deliberately rejected unless the patterns are
    equal because it cannot be proved safely with the standard library matcher.
    """
    source = source_path.lstrip("./")
    trigger = trigger_pattern.lstrip("./")
    if source == trigger:
        return True
    if trigger.endswith("/**"):
        trigger_prefix = trigger[:-3]
        return source == trigger_prefix or source.startswith(trigger_prefix + "/")
    return False


def validate_workload_source_path_triggers(workload_by_name: dict[str, dict[str, Any]]) -> None:
    for name, workload in workload_by_name.items():
        for source_path in workload["sourcePaths"]:
            if not any(
                path_pattern_covers(source_path, trigger_pattern)
                for trigger_pattern in workload["pathTriggers"]
            ):
                raise ManifestValidationError(
                    f"workload '{name}' source path '{source_path}' is not covered by pathTriggers"
                )


def validate_current_repository_source_mappings(
    workload_by_name: dict[str, dict[str, Any]]
) -> None:
    """Keep v2 delivery selection bound to the source tree that exists today."""
    for name, module_paths in CURRENT_MONOLITH_MODULE_PATHS.items():
        required_paths = module_paths
        if name in BACKEND_APPLICATION_WORKLOADS:
            required_paths = (*required_paths, *SHARED_BACKEND_INPUTS)
        workload = workload_by_name[name]
        for required_path in required_paths:
            if required_path not in workload["sourcePaths"]:
                raise ManifestValidationError(
                    f"workload '{name}' must declare current repository source path "
                    f"'{required_path}' in sourcePaths"
                )
            if required_path not in workload["pathTriggers"]:
                raise ManifestValidationError(
                    f"workload '{name}' must trigger current repository source path "
                    f"'{required_path}' in pathTriggers"
                )


def validate_microservice_v2_contract_mappings(
    manifest: dict[str, Any], workload_by_name: dict[str, dict[str, Any]]
) -> None:
    """Bind each v2 cross-service contract input to its actual backend participants.

    These files describe five business services plus the Assessment API/Worker
    deployment pair.  The current gateway routes opaque public traffic and the
    SPA does not consume generated v2 clients, so neither is rebuilt merely for
    a v2 contract-document or verifier change.
    """
    declared_paths = manifest["sharedServiceContractPaths"]
    if len(declared_paths) != len(set(declared_paths)):
        raise ManifestValidationError("manifest.sharedServiceContractPaths must not contain duplicates")
    unexpected_paths = sorted(
        set(declared_paths).difference(MICROSERVICE_V2_SHARED_SERVICE_CONTRACT_INPUTS)
    )
    missing_paths = [
        path
        for path in MICROSERVICE_V2_SHARED_SERVICE_CONTRACT_INPUTS
        if path not in declared_paths
    ]
    if missing_paths or unexpected_paths:
        details = []
        if missing_paths:
            details.append(f"missing {', '.join(missing_paths)}")
        if unexpected_paths:
            details.append(f"unexpected {', '.join(unexpected_paths)}")
        raise ManifestValidationError(
            "manifest.sharedServiceContractPaths must declare the canonical v2 "
            f"shared service contract inputs ({'; '.join(details)})"
        )

    for name in BACKEND_APPLICATION_WORKLOADS:
        workload = workload_by_name[name]
        for required_path in MICROSERVICE_V2_SHARED_SERVICE_CONTRACT_INPUTS:
            if required_path not in workload["sourcePaths"]:
                raise ManifestValidationError(
                    f"workload '{name}' must declare shared service contract input "
                    f"'{required_path}' in sourcePaths"
                )
            if required_path not in workload["pathTriggers"]:
                raise ManifestValidationError(
                    f"workload '{name}' must trigger shared service contract input "
                    f"'{required_path}' in pathTriggers"
                )


def validate_dependency_graph(workload_by_name: dict[str, dict[str, Any]]) -> None:
    for name, workload in workload_by_name.items():
        for dependency in workload["dependsOn"]:
            if dependency not in workload_by_name:
                raise ManifestValidationError(f"workload '{name}' has unknown dependency '{dependency}'")
        for probe_name in ("startup", "liveness", "readiness"):
            for dependency in workload["health"][probe_name]["requiredDependencies"]:
                if dependency not in workload_by_name:
                    raise ManifestValidationError(
                        f"workload '{name}' {probe_name} has unknown required dependency '{dependency}'"
                    )

    active: list[str] = []
    visited: set[str] = set()

    def visit(name: str) -> None:
        if name in active:
            cycle = active[active.index(name):] + [name]
            raise ManifestValidationError(f"dependency cycle: {' -> '.join(cycle)}")
        if name in visited:
            return
        active.append(name)
        for dependency in workload_by_name[name]["dependsOn"]:
            visit(dependency)
        active.pop()
        visited.add(name)

    for workload_name in workload_by_name:
        visit(workload_name)


def validate_migration_jobs(manifest: dict[str, Any], workload_by_name: dict[str, dict[str, Any]]) -> None:
    expected_previous: str | None = None
    seen_schemas: set[str] = set()
    jobs_by_name: dict[str, dict[str, Any]] = {}
    for index, job in enumerate(manifest["migrationJobs"]):
        location = f"migrationJobs[{index}]"
        fields = {
            "name", "schema", "forWorkload", "sourcePaths", "command", "configuration", "secrets", "dependsOnWorkloads",
            "dependsOnMigrationJobs", "runOnce", "blocksTraffic", "failurePolicy", "retryPolicy", "rollback",
        }
        required_fields(job, fields, location)
        reject_unknown_fields(job, fields, location)
        if job["name"] in jobs_by_name:
            raise ManifestValidationError(f"duplicate migration job '{job['name']}'")
        jobs_by_name[job["name"]] = job
        if job["schema"] not in ORDERED_SCHEMAS:
            raise ManifestValidationError(f"{location}.schema has unsupported value '{job['schema']}'")
        if job["schema"] in seen_schemas:
            raise ManifestValidationError(f"duplicate migration schema '{job['schema']}'")
        seen_schemas.add(job["schema"])
        expected_command = f"./database/mysql/migrate-service.sh --schema {job['schema']}"
        if job["command"] != expected_command:
            raise ManifestValidationError(
                f"{location} must use the checked-in migration runner '{expected_command}'"
            )
        runner = REPOSITORY_ROOT / "database/mysql/migrate-service.sh"
        if not runner.is_file() or runner.stat().st_mode & 0o111 == 0:
            raise ManifestValidationError(
                f"{location} migration runner must exist and be executable: {runner.relative_to(REPOSITORY_ROOT)}"
            )
        if job["configuration"] != [
            "MYSQL_HOST",
            "MYSQL_PORT",
            "MIGRATION_DATABASE_NAME",
            "MIGRATION_DATABASE_USER",
        ]:
            raise ManifestValidationError(
                f"{location} must inject only the migration control-plane connection configuration"
            )
        expected_secret = {
            "key": "MIGRATION_DATABASE_PASSWORD",
            "injectedAs": "migration-secret",
            "redact": True,
            "imageBuild": "forbidden",
        }
        if job["secrets"] != [expected_secret]:
            raise ManifestValidationError(
                f"{location} must inject exactly one redacted migration-only credential"
            )
        if job["forWorkload"] not in workload_by_name:
            raise ManifestValidationError(f"{location} references unknown workload '{job['forWorkload']}'")
        if workload_by_name[job["forWorkload"]]["migrationJob"] != job["name"]:
            raise ManifestValidationError(f"{location} is not linked by workload '{job['forWorkload']}'")
        owning_workload = workload_by_name[job["forWorkload"]]
        for source_path in job["sourcePaths"]:
            if not any(
                path_pattern_covers(source_path, trigger_pattern)
                for trigger_pattern in owning_workload["pathTriggers"]
            ):
                raise ManifestValidationError(
                    f"migration source path '{source_path}' for job '{job['name']}' is not covered "
                    f"by owning workload '{job['forWorkload']}' pathTriggers"
                )
        if job["dependsOnWorkloads"] != ["mysql"]:
            raise ManifestValidationError(f"{location} must depend on the physical mysql workload")
        expected_dependencies = [] if expected_previous is None else [expected_previous]
        if job["dependsOnMigrationJobs"] != expected_dependencies:
            raise ManifestValidationError(
                f"{location} must depend on ordered migration job(s) {expected_dependencies}"
            )
        if not job["runOnce"] or not job["blocksTraffic"] or job["failurePolicy"] != "block":
            raise ManifestValidationError(f"{location} must be a one-time, traffic-blocking migration job")
        if job["retryPolicy"] != "safe-to-rerun" or job["rollback"] != "forward-fix-or-approved-restore":
            raise ManifestValidationError(f"{location} has an unsafe retry or rollback policy")
        expected_previous = job["name"]
    actual_schemas = [job["schema"] for job in manifest["migrationJobs"]]
    if actual_schemas != ORDERED_SCHEMAS:
        raise ManifestValidationError(
            f"migration schema order must be {' -> '.join(ORDERED_SCHEMAS)}, got {' -> '.join(actual_schemas)}"
        )
    for workload in workload_by_name.values():
        migration_job = workload["migrationJob"]
        if migration_job is not None and migration_job not in jobs_by_name:
            raise ManifestValidationError(
                f"workload '{workload['name']}' references unknown migration job '{migration_job}'"
            )


def validate_promotion_and_retirement(manifest: dict[str, Any]) -> None:
    promotion = manifest["promotion"]
    required_fields(promotion, ("environments", "requiredEvidence"), "promotion")
    if promotion["environments"] != ["DEV", "FAT", "UAT", "PRO"]:
        raise ManifestValidationError("promotion.environments must be DEV -> FAT -> UAT -> PRO")
    if not promotion["requiredEvidence"]:
        raise ManifestValidationError("promotion.requiredEvidence must not be empty")
    retirement = manifest["d3Retirement"]
    required_fields(retirement, ("legacyContract", "compatibilityWindow", "retirementGates"), "d3Retirement")
    if retirement["legacyContract"] != "docs/开发/D3-CICD-共享契约.md":
        raise ManifestValidationError("d3Retirement must identify the D3 shared contract being retired")
    if retirement["compatibilityWindow"] != "v2-parallel-until-gates-pass":
        raise ManifestValidationError("d3Retirement must retain the explicit compatibility-window policy")


def validate_browser_entry(workload_by_name: dict[str, dict[str, Any]]) -> None:
    """Require a browser-reachable SPA through the public gateway route."""
    gateway = workload_by_name["gateway"]
    frontend = workload_by_name["frontend"]
    gateway_traffic = gateway["traffic"]
    frontend_traffic = frontend["traffic"]
    browser_entry = gateway_traffic["browserEntry"]

    if gateway_traffic["exposed"] is not True:
        raise ManifestValidationError("gateway browser entry must be externally exposed")
    if browser_entry != {"path": "/", "targetWorkload": "frontend"}:
        raise ManifestValidationError(
            "gateway browser entry must route path '/' to workload 'frontend'"
        )
    if frontend["type"] != "frontend":
        raise ManifestValidationError("gateway browser entry target 'frontend' must be a frontend workload")
    if frontend_traffic["exposed"] is not False:
        raise ManifestValidationError("frontend browser entry must be served through gateway, not exposed directly")
    if "frontend" not in gateway["dependsOn"]:
        raise ManifestValidationError(
            "gateway browser entry must declare frontend as a deployment dependency"
        )
    for name, workload in workload_by_name.items():
        if name != "gateway" and workload["traffic"]["browserEntry"] is not None:
            raise ManifestValidationError(f"only gateway may declare a browser entry (found on '{name}')")


def validate(manifest: dict[str, Any], schema: dict[str, Any]) -> dict[str, dict[str, Any]]:
    validate_schema_shape(schema)
    validate_against_schema(manifest, schema, schema, "manifest")
    workloads = validate_manifest_shape(manifest)
    workload_by_name = validate_workloads(workloads)
    validate_workload_source_path_triggers(workload_by_name)
    validate_current_repository_source_mappings(workload_by_name)
    validate_microservice_v2_contract_mappings(manifest, workload_by_name)
    validate_dependency_graph(workload_by_name)
    validate_migration_jobs(manifest, workload_by_name)
    validate_promotion_and_retirement(manifest)
    validate_browser_entry(workload_by_name)
    return workload_by_name


def path_matches(path: str, pattern: str) -> bool:
    normalized_path = path.lstrip("./")
    normalized_pattern = pattern.lstrip("./")
    if normalized_pattern.endswith("/**"):
        prefix = normalized_pattern[:-3]
        return normalized_path == prefix or normalized_path.startswith(prefix + "/")
    return fnmatch.fnmatchcase(normalized_path, normalized_pattern)


def resolve_changed_paths(
    manifest: dict[str, Any], workload_by_name: dict[str, dict[str, Any]], changed_paths: list[str]
) -> dict[str, Any]:
    shared_changed = [
        changed_path
        for changed_path in changed_paths
        if any(path_matches(changed_path, pattern) for pattern in manifest["sharedTriggerPaths"])
    ]
    if shared_changed:
        affected = [name for name, workload in workload_by_name.items() if workload["image"]["build"]]
        reasons = {name: {"sharedContractPaths": shared_changed, "workloadPaths": []} for name in affected}
    else:
        affected = []
        reasons: dict[str, dict[str, list[str]]] = {}
        for name, workload in workload_by_name.items():
            matched = [
                changed_path
                for changed_path in changed_paths
                if any(path_matches(changed_path, pattern) for pattern in workload["pathTriggers"])
            ]
            if matched:
                affected.append(name)
                reasons[name] = {"sharedContractPaths": [], "workloadPaths": matched}
    return {"changedPaths": changed_paths, "affectedWorkloads": affected, "reasons": reasons}


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path, help="path to workloads.json")
    parser.add_argument("--schema", required=True, type=Path, help="path to workload-manifest.schema.json")
    parser.add_argument(
        "--changed-path",
        action="append",
        default=[],
        help="repository-relative changed path; may be passed more than once",
    )
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    try:
        schema = load_json(arguments.schema, "schema")
        manifest = load_json(arguments.manifest, "manifest")
        workload_by_name = validate(manifest, schema)
    except ManifestValidationError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2
    if arguments.changed_path:
        print(
            json.dumps(
                resolve_changed_paths(manifest, workload_by_name, arguments.changed_path),
                ensure_ascii=False,
                indent=2,
            )
        )
    else:
        print(
            f"PASS: {len(workload_by_name)} workloads; "
            f"{len(manifest['migrationJobs'])} ordered migration jobs; "
            "schema, ports, dependencies, migrations, promotion, and D3 retirement are valid"
        )
    return 0


if __name__ == "__main__":
    sys.exit(main())
