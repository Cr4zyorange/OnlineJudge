# Gateway routing and controlled cutover design

## Scope

Issue #317 keeps the browser-facing API base unchanged while introducing a route
gateway for four independently delivered services: AUTH, CRS, Assessment, and
Learning & Grade. It covers routing, request-safety boundaries, controlled
cutover and rollback, and executable gateway verification. It does not extract
business code, change public request/response DTOs, or define the unfinished
inter-service identity protocol owned by #310.

## Existing constraints

- The stable deployment baseline is an Nginx frontend gateway with the public
  `/api/` base, SPA history fallback, and a 55 MB request body limit.
- #311 supplies an independent AUTH service at the existing AUTH paths. Its
  contract explicitly rejects browser-provided `X-User-*` identity headers and
  forbids new service-to-service identity headers before #310 is frozen.
- #312, #313, and #316 own the independent CRS, Assessment, and Learning &
  Grade service implementations. The gateway must consume their published
  health/readiness/version endpoints, not duplicate their implementation.
- A service may remain on the monolith during migration. A failed cutover must
  restore only that service without changing the browser API base or the other
  services' traffic.

## Considered approaches

1. **Nginx gateway with an explicit route table and generated upstream include
   files (recommended).** The existing frontend Nginx remains the sole public
   entry point. Versioned route fragments map every public path to one of four
   logical services. A small, validated cutover command renders selected
   upstream targets, validates Nginx configuration, reloads the gateway, runs
   probes and smoke checks, and restores the previous selection when a probe
   or smoke check fails. This preserves the deployment baseline and enables
   per-service rollback.
2. **Introduce a new application gateway.** This would provide dynamic routing
   but adds a new public component, runtime, image, telemetry surface, and
   operational ownership that none of the frozen deployment documents require.
3. **One-time replacement after all four services are available.** This avoids
   cutover machinery but cannot meet the Issue's staged traffic switch and
   repeatable rollback requirements.

The implementation uses option 1.

## Architecture and routing

The gateway keeps `/` for static assets and `try_files $uri /index.html` for
deep links. `/api/v1/` is handled by an ordered, version-controlled route
table. Specific course subresources are matched before the general CRS course
route so that the following logical ownership is unambiguous:

| Logical service | Public route families |
| --- | --- |
| AUTH | `/api/v1/auth/**`, `/api/v1/users/me/**`, `/api/v1/admin/**` |
| CRS | `/api/v1/courses/**` except assessment and grade subresources; `/api/v1/chapters/**` |
| Assessment | `/api/v1/labs/**`, `/api/v1/homeworks/**`, `/api/v1/submissions/**`, `/api/v1/evaluations/**`, and course lab/assessment subresources |
| Learning & Grade | `/api/v1/learning/**`, `/api/v1/notifications/**`, `/api/v1/reminder-rules/**`, and course grade/grade-item/grade-analysis/grade-review subresources |

Every logical service has two eligible upstreams: its independent service and
the compatible monolith endpoint. The selected target is an explicit deployment
input, never a browser-provided header, query parameter, or unreviewed default.
The deployment command records the selected targets before and after a change,
so a rollback can deterministically restore the immediately preceding state.

The gateway also owns gateway health/readiness checks. Health reports that the
gateway process can serve traffic. Readiness checks every selected downstream
readiness endpoint with bounded time and returns no dependency address,
credential, or stack trace to the browser.

## Authentication and request safety

Anonymous routes are limited to login, registration, and gateway/service
health, readiness, and version probes. Other public routes are passed through
with the original `Authorization: Bearer` value. The gateway removes every
browser-supplied identity, role, permission, course-authority, and internal
authentication header before proxying. It does not manufacture an identity
header or treat a header as an authenticated subject. When the authorization
service or a downstream verifier cannot validate a token, the downstream must
fail closed with its compatible 401/403 response.

The gateway forwards ordinary reverse-proxy metadata (`Host`, client address,
forwarded chain, protocol) and adds a request ID if the caller did not provide
one. It neither logs bearer values nor emits them in error bodies. Request
bodies remain limited to 55 MB. Idempotent reads may use the one bounded retry
defined in the route policy; mutation and upload requests do not retry at the
gateway. Connection, response-header, and response-body timeouts are explicit;
upload paths receive the larger bounded response window required for existing
multipart behavior.

## Errors and rollback

The gateway preserves compatible application 401, 403, and 404 responses. It
turns connection refusal or invalid upstream response into a stable 502, and a
gateway timeout into a stable 504. These gateway bodies are generic and omit
upstream hostnames, ports, credentials, filesystem paths, and stack traces.

For a selected service, cutover is: validate input -> render route target ->
validate Nginx -> reload -> service readiness -> authenticated smoke -> record
success. Any failed post-reload check triggers restoration of the captured
target set, configuration validation, reload, and the same readiness and smoke
checks. The command exits non-zero and emits a redacted diagnostic record if
either cutover or restoration cannot be verified.

## Verification

Tests are written before gateway implementation changes. They cover the route
table, removal of forged identity headers, propagation of Bearer authentication,
SPA deep links, multipart request limit and non-retry behavior, compatible
401/403/404 responses, redacted 502/504 responses, selected-service readiness,
and a cutover/rollback round trip. Static configuration tests run without
Docker; integration tests use disposable upstream stubs and never require
business-service credentials. Compose and Kind validation consume the existing
D3 commands and add only the gateway-specific checks required by #317.

## Non-goals and dependency rule

This Issue does not change the external API, database schema, service business
logic, source-grade DTO, notification event DTO, or the #310 inter-service
identity protocol. If #310 publishes a different trusted-subject mechanism,
the gateway consumes that versioned contract in a follow-up change rather than
silently substituting a header-based scheme.
