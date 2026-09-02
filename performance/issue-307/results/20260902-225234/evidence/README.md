# Issue #307 EVIDENCE_READY — 20260902-225234

This is the only formal evidence window for implementation head
`3943b06045d4530fc73c42f4e5c5bae232457434`.  Earlier `20260902-*` and
`20260902T*` diagnostic directories are retained locally as failed, aborted,
or contaminated attempts and are neither archived nor aggregated here.

## Comparable conditions

| Item | Frozen evidence |
| --- | --- |
| Monolith baseline | `78715f21288782a2c7ef1d9c23f933c46569b108` |
| Three-service baseline | `c66686ff0e011f5ee63e3908683f01afd4f83ebc` |
| Machine / dataset | fingerprint `033a722a0f09f91f2525c397c31fa628faa841eed7c8a223751e09a6520a6616`; dataset `733338e1ba51a64b693b60678eeacaa78a0597f7e2034bba6dc2b09e067885c6` |
| Load | 10 virtual students; 30 s warmup; 120 s measurement; 1000 ms minimum per-student interval; 10 s timeout |
| Gateway policy | No benchmark-only override: frozen read 30 r/s and write 10 r/s limits remain in force. |
| Resources | Both architectures have Docker hard limits totaling exactly 4 CPU / 6144 MiB; see `monolith-resource-policy.yml`, `monolith-hard-limits.json`, `three-service-runtime/resource-policy.yml`, and `three-service-runtime/hard-limits.txt`. |
| Isolation | Three-service ran alone at 9/9 containers and was fully removed before monolith ran alone at 3/3; every raw resource sample rechecks exclusivity. `monolith-shutdown-guard.txt` and `monolith-shutdown-result.txt` prove final cleanup. |

The monolith was rebuilt from its unchanged frozen Dockerfile.  The exact
locally available Maven and JRE image identities are recorded in
`monolith-base-images.txt`; the build and runtime logs are retained without
changing the product Dockerfile.

## Dataset and successful preflight gates

Every architecture/scenario/round was independently reset, then ten fixture
students completed a preflight at 100% success before its formal window.  The
Course list gate parses every response and requires `data.total == 105`;
`combined-raw-count.json` proves all six Course rounds meet that condition.
The HWK writes use different fixture students and the Grades endpoint succeeds
under the real identity and course permissions.

| Architecture | API | Rounds | Result |
| --- | --- | ---: | --- |
| monolith | course-list | 3 | 3 x 1,199 `200` |
| monolith | homework-submission | 3 | 3 x 1,199 `201` |
| monolith | my-grades | 3 | 3 x 1,199 `200` |
| three-service | course-list | 3 | 3 x 1,199 `200` |
| three-service | homework-submission | 3 | 3 x 1,199 `201` |
| three-service | my-grades | 3 | 3 x 1,199 `200` |

`combined-raw-count.json` records 18 valid rounds, zero invalid rounds, and
21,582 formal requests. `../raw/raw-manifest.json` losslessly archives all 18
raw files; `checksums.sha256` records the archive and report hashes.

## Results and interpretation boundary

`../report/comparison.md`, `comparison.json`, and `rounds.csv` include every
round's P95, throughput, errors, CPU, and memory figures. No round is selected
or discarded. The approximately 9.915 successful requests/second on both sides
is bounded by the configured ten-student, one-request-per-second pacing, so it
is not a saturation-capacity result. The measured 9-process/JVM-plus-RabbitMQ
topology versus three monolith containers supports an observation of higher
three-service CPU and memory overhead. Extra gateway and service hops are a
bounded plausible explanation for the Course and Grades P95 observations; the
lower HWK P95 is not evidence of an architectural improvement. No per-hop,
connection-pool, serialization, or cache causality is claimed without separate
measurements.

The evidence directory passed a bearer-token/private-key/password-pattern scan.
