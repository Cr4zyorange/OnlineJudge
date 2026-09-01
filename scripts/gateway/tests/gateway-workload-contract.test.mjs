import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

function read(relativePath) {
  return readFileSync(fileURLToPath(new URL(relativePath, import.meta.url)), "utf8");
}

const manifest = JSON.parse(read("../../../deploy/platform/workloads.json"));
const gateway = manifest.workloads.find((entry) => entry.name === "gateway");
const dockerfile = read("../../../services/gateway/Dockerfile");
const entrypoint = read("../../../services/gateway/entrypoint.sh");
const nginxConfig = read("../../../services/gateway/nginx.conf");
const compose = read("../../../deploy/docker/compose.gateway.yml");

assert.equal(gateway.dockerfile, "services/gateway/Dockerfile");
assert.equal(gateway.ports[0].containerPort, 8080);
assert.equal(gateway.traffic.exposed, true);
assert.deepEqual(gateway.traffic.routePrefixes, ["/api"]);

assert.match(dockerfile, /^FROM nginx:1\.27-alpine/m);
assert.doesNotMatch(dockerfile, /nginx-module-njs|openresty|lua/i);
assert.match(dockerfile, /COPY services\/gateway\/entrypoint\.sh/);
assert.match(dockerfile, /COPY deploy\/gateway\/gateway\.conf\.template/);
assert.match(dockerfile, /COPY deploy\/gateway\/proxy-request-headers\.conf/);
assert.match(dockerfile, /EXPOSE 8080/);
assert.match(dockerfile, /ENTRYPOINT \["\/usr\/local\/bin\/gateway-entrypoint"\]/);

assert.match(entrypoint, /render-gateway-config/);
assert.match(entrypoint, /nginx -t/);
assert.match(entrypoint, /exec "\$@"/);
assert.match(nginxConfig, /include \/etc\/nginx\/conf\.d\/\*\.conf;/);

assert.match(compose, /^\s{2}gateway:/m);
assert.match(compose, /target:\s*8080/);
assert.match(compose, /published:\s*"?\$\{GATEWAY_HTTP_PORT:-8088\}"?/);
assert.match(compose, /IDENTITY_UPSTREAM:\s*identity-service:8081/);
assert.match(compose, /COURSE_UPSTREAM:\s*course-service:8082/);
assert.match(compose, /ASSESSMENT_UPSTREAM:\s*assessment-api:8083/);
assert.match(compose, /GRADE_UPSTREAM:\s*grade-service:8084/);
assert.doesNotMatch(compose, /LEARNING_UPSTREAM|learning-service/);
assert.doesNotMatch(compose, /frontend:[\s\S]*gateway-runtime\/default\.conf/);

console.log("gateway-workload-contract.test: PASS");
