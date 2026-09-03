# Issue #307 EVIDENCE_READY — 20260902T200359Z

This is the only successful formal window for the current PR head.  Earlier
untracked `20260902T1357Z` through `20260902T200035Z` directories are retained
as failed or aborted diagnostics and are neither archived here nor aggregated.

## Frozen, comparable conditions

| Item | Evidence |
| --- | --- |
| Monolith baseline | `78715f21288782a2c7ef1d9c23f933c46569b108` (`monolith-start`) |
| Three-service baseline | `c66686ff0e011f5ee63e3908683f01afd4f83ebc` (`origin/dev`) |
| Machine / dataset | fingerprint `033a722a0f09f91f2525c397c31fa628faa841eed7c8a223751e09a6520a6616`; dataset SHA-256 `733338e1ba51a64b693b60678eeacaa78a0597f7e2034bba6dc2b09e067885c6` |
| Load | same `plan.json`: 10 virtual students, 30 s warmup, 120 s measurement, 1000 ms minimum per-student interval, 10 s timeout |
| Rate-limit policy | No benchmark-only gateway override.  The frozen gateway remains at read 30 r/s and write 10 r/s; the common client pacing stays within the write limit. |
| Resource budget | Both architectures have explicit total hard limits of 4 CPU / 6144 MiB; see `monolith-resource-policy.yml`, `monolith-hard-limits.json`, `three-service-runtime/resource-policy.yml`, and `three-service-runtime/hard-limits.txt`. |
| Isolation | The three-service project `oj307-three-200359` ran alone at 9/9 containers and was stopped before monolith `oj307-monolith-200359` ran alone at 3/3.  Shutdown guards/results record the checks, and every resource sample rechecks exclusivity. |

The monolith was rebuilt from the unchanged frozen Dockerfile.  Local exact
base-image identities and the upstream source manifest digests used after the
Docker Hub metadata timeout are recorded in `monolith-base-images.txt` and the
successful rebuild is in `monolith-build.log`.

## Formal validity

Each scenario/architecture/round has an independent `formal/*.json`, reset log,
and ten-user `preflight/*/summary.json` with response files.  All 18 preflights
are 10/10 (100%).  The formal windows explicitly declare Docker ready,
no HPA, no E2E, no fault injection, and no other load.

| Architecture | API | Rounds | Formal HTTP result | Raw validation |
| --- | --- | ---: | --- | --- |
| monolith | course-list | 3 | 3 × 1199 `200` | `monolith-raw-validation.json` |
| monolith | homework-submission | 3 | 3 × 1199 `201` | `monolith-raw-validation.json` |
| monolith | my-grades | 3 | 3 × 1199 `200` | `monolith-raw-validation.json` |
| three-service | course-list | 3 | 3 × 1199 `200` | `three-service-raw-validation.json` |
| three-service | homework-submission | 3 | 3 × 1199 `201` | `three-service-raw-validation.json` |
| three-service | my-grades | 3 | 3 × 1199 `200` | `three-service-raw-validation.json` |

`combined-raw-count.json` proves 9 + 9 = 18 valid samples.  The prior
microservice 429/401 and monolith 409 results are not used: all raw evidence in
this window has zero errors.

## Artifacts and interpretation boundary

- `../raw/raw-manifest.json` lists all 18 byte-for-byte gzip archives with both
  uncompressed and compressed SHA-256 values; `raw-archive.log` and
  `post-archive-count.json` prove the lossless archive.
- `../report/comparison.md`, `comparison.json`, and `rounds.csv` contain every
  round's P95, successful throughput, error rate, CPU, and memory result.  No
  round was selected or discarded.
- The report only records observed deltas.  It does not infer a cause from
  process count, network hops, serialization, connection pools, or caching
  without separate evidence.

All #307 containers were stopped after aggregation; the final shutdown evidence
is `monolith-shutdown-result.txt` (`docker ps` = 0).
