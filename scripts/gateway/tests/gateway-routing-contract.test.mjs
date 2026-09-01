import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

function read(relativePath) {
  return readFileSync(fileURLToPath(new URL(relativePath, import.meta.url)), "utf8");
}

const manifest = JSON.parse(read("../../../deploy/platform/workloads.json"));
const template = read("../../../deploy/gateway/gateway.conf.template");

const expected = [
  ["identity-service", 8081, "IDENTITY"],
  ["course-service", 8082, "COURSE"],
  ["assessment-api", 8083, "ASSESSMENT"],
  ["grade-service", 8084, "GRADE"],
];

assert.equal(manifest.workloads.length, 9, "#306 freezes exactly nine workloads");
assert.equal(
  manifest.workloads.some((entry) => entry.name === "learning-service"),
  false,
  "Learning must be hosted by Course",
);

for (const [name, port, token] of expected) {
  const workload = manifest.workloads.find((entry) => entry.name === name);
  assert.ok(workload, `${name} must exist in the workload manifest`);
  assert.equal(workload.ports[0].containerPort, port, `${name} port drifted`);
  assert.match(
    template,
    new RegExp(`proxy_pass http://__${token}_UPSTREAM__`),
    `${name} needs a public proxy route`,
  );
}

const requiredRoutes = [
  "location ^~ /api/v1/auth/",
  "location = /api/v1/users/me",
  "location ^~ /api/v1/admin/",
  "location = /api/v1/courses",
  "location ^~ /api/v1/chapters/",
  "location ^~ /api/v1/labs/",
  "location = /api/v1/homeworks",
  "location ^~ /api/v1/submissions/",
  "location ^~ /api/v1/evaluations/",
  "location ^~ /api/v1/grades/",
  "location ^~ /api/v1/grade-items/",
  "location ^~ /api/v1/grade-records/",
  "location ^~ /api/v1/learning/",
  "location = /api/v1/notifications",
  "location = /api/v1/reminder-rules",
];
for (const route of requiredRoutes) {
  assert.ok(template.includes(route), `missing public route: ${route}`);
}

for (const rejectedPrefix of ["location ^~ /internal/v2/ {", "location ^~ /api/ {"]) {
  const start = template.indexOf(rejectedPrefix);
  assert.ok(start >= 0, `missing rejected prefix: ${rejectedPrefix}`);
  const end = template.indexOf("\n    }", start);
  assert.ok(template.slice(start, end).includes("return 404"), `${rejectedPrefix} must return 404`);
}
assert.doesNotMatch(template, /backend:8080|LEARNING_GRADE|__AUTH_UPSTREAM__|__CRS_UPSTREAM__/);
assert.doesNotMatch(template, /LEARNING_UPSTREAM|__LEARNING_UPSTREAM__|learning-service/);
assert.match(template, /proxy_pass_request_headers off|proxy-request-headers\.conf/);
assert.match(template, /proxy_next_upstream off;/);
assert.match(template, /error_page 413 = @gateway_payload_too_large;/);
assert.match(template, /error_page 429 = @gateway_rate_limited;/);
assert.match(template, /error_page 502 = @gateway_bad_gateway;/);
assert.match(template, /error_page 503 = @gateway_unavailable;/);
assert.match(template, /error_page 504 = @gateway_timeout;/);
assert.match(template, /add_header X-Request-Id \$gateway_request_id always;/);

const assessmentCourseRoute = template.indexOf(
  "location ~ ^/api/v1/courses/[0-9]+/(labs|homeworks)(/|$)",
);
const gradeCourseRoute = template.indexOf(
  "location ~ ^/api/v1/courses/[0-9]+/(grades|grade-items|grade-rules|grade-publish-records|grade-change-logs|my-grades|grade-analysis|grade-review-requests|my-grade-review-requests)(/|$)",
);
const genericCourseRoute = template.indexOf("location /api/v1/courses/");
assert.ok(assessmentCourseRoute >= 0, "missing Assessment course subresource route");
assert.ok(gradeCourseRoute >= 0, "missing Grade course subresource route");
assert.ok(genericCourseRoute > assessmentCourseRoute, "Course route must follow Assessment route");
assert.ok(genericCourseRoute > gradeCourseRoute, "Course route must follow Grade route");

for (const route of [
  "location ^~ /api/v1/learning/",
  "location = /api/v1/notifications",
  "location ^~ /api/v1/notifications/",
  "location = /api/v1/reminder-rules",
  "location ^~ /api/v1/reminder-rules/",
]) {
  const start = template.indexOf(route);
  assert.ok(start >= 0, `missing Course-owned route: ${route}`);
  const end = template.indexOf("\n    }", start);
  assert.match(
    template.slice(start, end),
    /proxy_pass http:\/\/__COURSE_UPSTREAM__;/,
    `${route} must route to Course`,
  );
}

console.log("gateway-routing-contract.test: PASS (services=4)");
