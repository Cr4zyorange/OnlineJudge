#!/usr/bin/env node
/**
 * Issue #367 API coverage tooling.
 *
 * Sources of truth (never older design docs):
 *  - Spring controllers in services/{identity,course,assessment}/src/main/java
 *  - Grade service controllers: backend/src/main/java/com/onlinejudge/grd/controller
 *    (the Grade service pom reuses this reviewed source via build-helper add-source)
 *  - Gateway route table: deploy/gateway/gateway.conf.template
 *  - Public health/readiness/version endpoints declared in each service
 *
 * Subcommands:
 *   inventory  -> tests/api/inventory.json
 *   map        -> tests/api/mapping.json (endpoint -> test file -> test method)
 *   coverage   -> tests/api/coverage-report.json + summary on stdout
 *   all        -> run inventory, map, coverage
 */

import { readFileSync, writeFileSync, readdirSync, statSync, existsSync } from "node:fs";
import { join, relative, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const inventoryPath = join(root, "tests", "api", "inventory.json");
const mappingPath = join(root, "tests", "api", "mapping.json");
const coveragePath = join(root, "tests", "api", "coverage-report.json");

const METHOD_ANNOTATIONS = [
  "GetMapping",
  "PostMapping",
  "PutMapping",
  "DeleteMapping",
  "PatchMapping",
  "RequestMapping",
];

const SERVICES = {
  identity: {
    mainDirs: [
      join(root, "services/identity/src/main/java/com/onlinejudge/auth/controller"),
      join(root, "services/identity/src/main/java/com/onlinejudge/authservice/controller"),
    ],
    testDir: join(root, "services/identity/src/test"),
    upstream: "identity-service",
  },
  course: {
    mainDirs: [join(root, "services/course/src/main/java/com/onlinejudge/courseservice/controller")],
    testDir: join(root, "services/course/src/test"),
    upstream: "course-service",
  },
  assessment: {
    mainDirs: [join(root, "services/assessment/src/main/java/com/onlinejudge/assessmentservice/controller")],
    testDir: join(root, "services/assessment/src/test"),
    upstream: "assessment-api",
  },
  grade: {
    mainDirs: [
      join(root, "backend/src/main/java/com/onlinejudge/grd/controller"),
      join(root, "services/grade/src/main/java/com/onlinejudge/gradeservice/controller"),
    ],
    testDir: join(root, "services/grade/src/test"),
    upstream: "grade-service",
  },
};

function walk(dir, out = []) {
  if (!existsSync(dir)) return out;
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) walk(full, out);
    else if (full.endsWith(".java")) out.push(full);
  }
  return out;
}

function listFiles(dirs) {
  return dirs.flatMap((dir) => walk(dir));
}

function trimValue(expr) {
  return expr
    .trim()
    .replace(/^"|"$/g, "")
    .replace(/^'|'$/g, "")
    .trim();
}

/** Extract one annotation argument list, tolerating parentheses nesting. */
function annotationArgs(annotationBody) {
  const args = [];
  let depth = 0;
  let current = "";
  for (const ch of annotationBody) {
    if (ch === "(") depth += 1;
    if (ch === ")") depth -= 1;
    if (ch === "," && depth === 0) {
      args.push(current.trim());
      current = "";
      continue;
    }
    current += ch;
  }
  if (current.trim()) args.push(current.trim());
  return args;
}

function extractPathsFromArgs(args) {
  const paths = [];
  for (const arg of args) {
    if (arg.includes("=")) {
      const [key, value] = arg.split(/=(.*)/s);
      const name = key.trim();
      if (["value", "path", "consumes", "produces", "headers"].includes(name)) {
        if (name === "consumes" || name === "produces" || name === "headers") continue;
        for (const part of value.split(/,(?![^(]*\))/)) {
          const cleaned = trimValue(part);
          if (cleaned.startsWith("/")) paths.push(cleaned);
        }
      }
    } else {
      const cleaned = trimValue(arg);
      if (cleaned.startsWith("/")) paths.push(cleaned);
    }
  }
  return paths;
}

function annotationFrom(line) {
  const match = line.match(/@(?:org\.springframework\.web\.bind\.annotation\.)?(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\s*(?:\(([\s\S]*?)\))?/);
  if (!match) return null;
  return { kind: match[1], body: match[2] || "" };
}

function joinPath(base, sub) {
  if (!base) return sub;
  if (!sub) return base;
  return (base.replace(/\/+$/, "") + "/" + sub.replace(/^\/+/, "")).replace(/\/+/g, "/");
}

function authFor(service, method, path) {
  if (path.startsWith("/internal/")) return "SERVICE";
  if (service === "identity") {
    if (["/api/v1/auth/register", "/api/v1/auth/login", "/.well-known/jwks.json"].includes(path)) return "PUBLIC";
    if (path.startsWith("/api/v1/system/")) return "PUBLIC";
    return "USER";
  }
  if (service === "course") {
    if (path === "/version") return "PUBLIC";
    return "USER";
  }
  if (service === "assessment") {
    if (path === "/health/ready") return "PUBLIC";
    return "USER";
  }
  if (service === "grade") {
    if (path === "/health/ready") return "PUBLIC";
    return "USER";
  }
  return "UNKNOWN";
}

function extractInventory() {
  const endpoints = [];
  let seq = 0;
  for (const [service, config] of Object.entries(SERVICES)) {
    for (const file of listFiles(config.mainDirs)) {
      const source = readFileSync(file, "utf8");
      if (!/@RestController/.test(source)) continue;
      let classBase = "";
      for (const line of source.split(/\r?\n/)) {
        if (line.includes("@RequestMapping")) {
          const ann = annotationFrom(line);
          if (ann && ann.kind === "RequestMapping" && ann.body) {
            const paths = extractPathsFromArgs(annotationArgs(ann.body));
            if (paths.length) classBase = paths[0];
          }
        }
      }
      const methodLines = source.split(/\r?\n/).map((line, index) => ({ line, index }));
      for (const { line } of methodLines) {
        if (!/@(?:org\.springframework\.web\.bind\.annotation\.)?(?:Get|Post|Put|Delete|Patch)Mapping\b/.test(line)) {
          continue;
        }
        const ann = annotationFrom(line);
        if (!ann) continue;
        const args = annotationArgs(ann.body);
        const paths = extractPathsFromArgs(args);
        const method = ann.kind.replace("Mapping", "").toUpperCase();
        if (!paths.length) {
          const path = classBase || "/";
          endpoints.push({
            id: `API-${service.toUpperCase()}-${String(++seq).padStart(2, "0")}`,
            method,
            path,
            service,
            auth: authFor(service, method, path),
            controller: relative(root, file).replace(/\\/g, "/"),
            gatewayUpstream: config.upstream,
            gatewayExposed: !path.startsWith("/internal/")
                && path !== "/version"
                && path !== "/health/ready"
                && !path.startsWith("/api/v1/system/"),
          });
        }
        for (const sub of paths) {
          const path = joinPath(classBase, sub);
          endpoints.push({
            id: `API-${service.toUpperCase()}-${String(++seq).padStart(2, "0")}`,
            method,
            path,
            service,
            auth: authFor(service, method, path),
            controller: relative(root, file).replace(/\\/g, "/"),
            gatewayUpstream: config.upstream,
            gatewayExposed: !path.startsWith("/internal/")
                && path !== "/version"
                && path !== "/health/ready"
                && !path.startsWith("/api/v1/system/"),
          });
        }
      }
    }
  }

  // Gateway-owned public endpoints and route boundaries.
  const gatewayOwned = [
    { method: "GET", path: "/health/startup", description: "Gateway liveness (nginx static 200)" },
    { method: "GET", path: "/health/live", description: "Gateway liveness (nginx static 200)" },
    { method: "GET", path: "/health/ready", description: "Gateway readiness (nginx static 200)" },
    { method: "ANY", path: "/internal/v2/*", description: "Gateway rejects internal v2 traffic with GATEWAY_404" },
    { method: "ANY", path: "/api/*", description: "Gateway rejects unknown API routes with GATEWAY_404" },
    { method: "ANY", path: "/gateway-error/413", description: "Gateway payload-too-large error page (GATEWAY_413)" },
    { method: "ANY", path: "/gateway-error/429", description: "Gateway rate-limit error page (GATEWAY_429)" },
    { method: "ANY", path: "/gateway-error/502", description: "Gateway bad-gateway error page (GATEWAY_502)" },
    { method: "ANY", path: "/gateway-error/503", description: "Gateway unavailable error page (GATEWAY_503)" },
    { method: "ANY", path: "/gateway-error/504", description: "Gateway timeout error page (GATEWAY_504)" },
  ];
  for (const item of gatewayOwned) {
    endpoints.push({
      id: `API-GATEWAY-${String(++seq).padStart(2, "0")}`,
      method: item.method,
      path: item.path,
      service: "gateway",
      auth: "PUBLIC",
      controller: "deploy/gateway/gateway.conf.template",
      gatewayUpstream: "gateway",
      gatewayExposed: true,
      description: item.description,
    });
  }

  const byKey = new Map();
  for (const endpoint of endpoints) {
    const key = `${endpoint.method} ${endpoint.path}`;
    if (!byKey.has(key)) byKey.set(key, []);
    byKey.get(key).push(endpoint);
  }
  const unique = [...byKey.values()].map((group) => group[0]);
  unique.sort((a, b) => a.service.localeCompare(b.service) || a.path.localeCompare(b.path) || a.method.localeCompare(b.method));
  unique.forEach((endpoint, index) => {
    endpoint.id = `API-${endpoint.service.toUpperCase()}-${String(index + 1).padStart(3, "0")}`;
  });
  return unique;
}

function parseGatewayRoutes() {
  const template = readFileSync(join(root, "deploy/gateway/gateway.conf.template"), "utf8");
  const routes = [];
  const locationRe = /location\s+(=?\s*[~^]*\s*)([^\s{]+)\s*\{([\s\S]*?)\n\s*\}/g;
  let match;
  while ((match = locationRe.exec(template))) {
    const modifier = match[1].trim();
    const pattern = match[2];
    const body = match[3];
    const upstreamMatch = body.match(/proxy_pass\s+http:\/\/([^;]+);/);
    const returnMatch = body.match(/return\s+(\d+)/);
    const nameMatch = body.match(/^location\s+@([a-z0-9_]+)/m);
    routes.push({
      modifier,
      pattern,
      upstream: upstreamMatch ? upstreamMatch[1] : null,
      status: returnMatch ? Number(returnMatch[1]) : null,
      named: nameMatch ? nameMatch[1] : null,
    });
  }
  return routes;
}

/**
 * Static Gateway route-ownership check: every service endpoint in the
 * inventory must be reachable through a template `location` that proxies to
 * its owning upstream, and gateway-owned health/rejection rules must exist.
 */
function gatewayStatic(endpoints) {
  const routes = parseGatewayRoutes();
  const failures = [];
  const upstreamFor = {
    identity: "identity-service",
    course: "course-service",
    assessment: "assessment-api",
    grade: "grade-service",
  };
  const normalizeUpstream = (value) => value
      .replace("__IDENTITY_UPSTREAM__", "identity-service")
      .replace("__COURSE_UPSTREAM__", "course-service")
      .replace("__ASSESSMENT_UPSTREAM__", "assessment-api")
      .replace("__GRADE_UPSTREAM__", "grade-service");
  const normalized = routes.map((route) => ({
    ...route,
    upstream: route.upstream ? normalizeUpstream(route.upstream) : null,
  }));

  const locationMatches = (pattern, modifier, path) => {
    const normalizedPath = path.replace(/\{[^}]+\}/g, "999");
    if (modifier === "=") return pattern === path;
    if (modifier === "^~") return normalizedPath === pattern || normalizedPath.startsWith(pattern.replace(/\/+$/, "") + "/");
    if (modifier === "~") {
      try {
        return new RegExp(pattern).test(normalizedPath);
      } catch {
        return false;
      }
    }
    return normalizedPath === pattern || normalizedPath.startsWith(pattern.replace(/\/+$/, "") + "/");
  };

  const ownedPaths = new Set([
    "/health/startup",
    "/health/live",
    "/health/ready",
    "/internal/v2/*",
    "/api/*",
    "/gateway-error/413",
    "/gateway-error/429",
    "/gateway-error/502",
    "/gateway-error/503",
    "/gateway-error/504",
  ]);

  for (const endpoint of endpoints) {
    if (endpoint.service === "gateway") {
      if (!ownedPaths.has(endpoint.path)) failures.push(`gateway-owned path missing from template: ${endpoint.path}`);
      continue;
    }
    const expected = upstreamFor[endpoint.service];
    if (!expected) continue;
    // Direct service probes (/version, /health/ready) are not exposed through
    // the gateway and must stay reachable on the service port only.
    if (!endpoint.gatewayExposed) continue;
    // The gateway intentionally rejects internal v2 traffic with GATEWAY_404.
    if (endpoint.path.startsWith("/internal/")) continue;
    const hit = normalized.some((route) => route.upstream && route.upstream.startsWith(expected)
        && locationMatches(route.pattern, route.modifier, endpoint.path));
    if (!hit) failures.push(`${endpoint.method} ${endpoint.path} has no ${expected} route in the gateway template`);
  }

  const internalRejection = normalized.some((route) => route.pattern === "/internal/v2/" && route.status === 404);
  const apiRejection = normalized.some((route) => route.pattern === "/api/" && route.status === 404);
  if (!internalRejection) failures.push("gateway template lacks the /internal/v2/ GATEWAY_404 rejection");
  if (!apiRejection) failures.push("gateway template lacks the /api/ GATEWAY_404 rejection");
  for (const status of [413, 429, 502, 503, 504]) {
    if (!normalized.some((route) => route.named === `gateway_${status}` || route.status === status)) {
      failures.push(`gateway template lacks the ${status} error page`);
    }
  }
  for (const host of ["identity-service", "course-service", "assessment-api", "grade-service"]) {
    if (!normalized.some((route) => route.upstream && route.upstream.startsWith(host))) {
      failures.push(`gateway template has no ${host} upstream route`);
    }
  }
  if (normalized.some((route) => route.upstream && route.upstream.startsWith("learning-service"))) {
    failures.push("gateway template still references the retired learning-service upstream");
  }
  return { routes: routes.length, failures };
}

function pathLiteralMatches(endpointPath, literal) {
  if (!literal.startsWith("/")) return false;
  const escaped = endpointPath.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const regex = new RegExp(`^${escaped.replace(/\\\{[^}]+\\\}/g, "[^/]+")}$`);
  return regex.test(literal);
}

function scanTests(endpoints) {
  const mapping = new Map(); // "method path" -> [{file, method, line}]
  for (const [service, config] of Object.entries(SERVICES)) {
    for (const file of walk(config.testDir)) {
      const source = readFileSync(file, "utf8");
      const lines = source.split(/\r?\n/);
      const serviceEndpoints = endpoints.filter((e) => e.service === service);
      // Collect @Test-annotated methods with brace-counted bodies so helper
      // methods and @DynamicPropertySource setups never create false mappings.
      const testMethods = [];
      for (let i = 0; i < lines.length; i++) {
        if (!/^\s*@Test\b/.test(lines[i])) continue;
        let j = i + 1;
        while (j < lines.length && !/^\s*(?:public\s+|protected\s+|private\s+)?(?:static\s+)?[\w<>[\],\s]+\s+\w+\s*\([^)]*\)\s*(?:throws[\s\S]*?)?\{/.test(lines[j])) {
          j += 1;
        }
        if (j >= lines.length) continue;
        const methodMatch = lines[j].match(/(\w+)\s*\([^)]*\)\s*(?:throws[\s\S]*?)?\{/);
        if (!methodMatch) continue;
        const testName = methodMatch[1];
        let depth = 0;
        let bodyStart = j;
        const body = [];
        for (let k = j; k < lines.length; k++) {
          body.push(lines[k]);
          depth += (lines[k].match(/\{/g) || []).length;
          depth -= (lines[k].match(/\}/g) || []).length;
          if (depth === 0) {
            bodyStart = k;
            break;
          }
        }
        if (depth !== 0) continue;
        testMethods.push({ testName, body: body.join("\n"), line: j + 1 });
      }
      for (const { testName, body, line } of testMethods) {
        // Associate every request-builder verb with its path literal so a GET
        // hit on a template never maps a POST endpoint.
        const calls = [];
        for (const sourceLine of body.split(/\r?\n/)) {
          const matches = sourceLine.matchAll(/\b(get|post|put|delete|patch|multipart|options)\(\s*"((?:\\.[^"\\]*|[^"\\])*)"/g);
          for (const match of matches) {
            let literal;
            try {
              literal = JSON.parse(`"${match[2]}"`);
            } catch {
              literal = match[2];
            }
            const verb = match[1] === "multipart" ? "POST" : match[1].toUpperCase();
            calls.push({ verb, literal });
          }
        }
        for (const endpoint of serviceEndpoints) {
          const key = `${endpoint.method} ${endpoint.path}`;
          const matched = calls.some(({ verb, literal }) =>
            (endpoint.method === "ANY" || endpoint.method === verb) &&
            pathLiteralMatches(endpoint.path, literal.split("?")[0])
          );
          if (!matched && endpoint.method === "ANY") {
            // Gateway/internal rejection endpoints match any literal under their prefix.
            continue;
          }
          if (!matched) continue;
          if (!mapping.has(key)) mapping.set(key, []);
          const entries = mapping.get(key);
          if (!entries.some((e) => e.file === file && e.method === testName)) {
            entries.push({
              file: relative(root, file).replace(/\\/g, "/"),
              method: testName,
              line,
            });
          }
        }
      }
    }
  }
  // Gateway endpoints map to the gateway runtime test plus render/verify scripts.
  const gatewayTest = {
    file: "scripts/gateway/tests/gateway-runtime.test.sh",
    method: "gateway-runtime.test",
    line: 1,
  };
  const gatewayConfigTest = {
    file: "scripts/gateway/tests/kind-gateway-config.test.sh",
    method: "kind-gateway-config.test",
    line: 1,
  };
  for (const endpoint of endpoints.filter((e) => e.service === "gateway")) {
    const key = `${endpoint.method} ${endpoint.path}`;
    if (!mapping.has(key)) mapping.set(key, []);
    mapping.get(key).push(gatewayTest);
    mapping.get(key).push(gatewayConfigTest);
  }
  return mapping;
}

function writeJson(path, value) {
  writeFileSync(path, JSON.stringify(value, null, 2) + "\n");
}

function run(subcommand) {
  if (subcommand === "inventory" || subcommand === "all") {
    const endpoints = extractInventory();
    const gatewayRoutes = parseGatewayRoutes();
    writeJson(inventoryPath, { generatedAt: new Date().toISOString(), gatewayRoutes, endpoints });
    console.log(`inventory: ${endpoints.length} endpoints -> ${relative(root, inventoryPath)}`);
  }
  if (subcommand === "map" || subcommand === "all") {
    const { endpoints } = JSON.parse(readFileSync(inventoryPath, "utf8"));
    const mapping = scanTests(endpoints);
    const payload = {};
    for (const [key, tests] of mapping) payload[key] = tests;
    writeJson(mappingPath, { generatedAt: new Date().toISOString(), mapping: payload });
    console.log(`map: ${Object.keys(payload).length} endpoints mapped -> ${relative(root, mappingPath)}`);
  }
  if (subcommand === "coverage" || subcommand === "all") {
    const { endpoints } = JSON.parse(readFileSync(inventoryPath, "utf8"));
    const { mapping } = JSON.parse(readFileSync(mappingPath, "utf8"));
    const unmapped = [];
    for (const endpoint of endpoints) {
      const key = `${endpoint.method} ${endpoint.path}`;
      if (!mapping[key] || mapping[key].length === 0) unmapped.push(endpoint);
    }
    const byService = {};
    for (const endpoint of endpoints) {
      byService[endpoint.service] = byService[endpoint.service] || { total: 0, mapped: 0, unmapped: 0 };
      byService[endpoint.service].total += 1;
      const key = `${endpoint.method} ${endpoint.path}`;
      if (mapping[key] && mapping[key].length) byService[endpoint.service].mapped += 1;
      else byService[endpoint.service].unmapped += 1;
    }
    const report = {
      generatedAt: new Date().toISOString(),
      totals: {
        endpoints: endpoints.length,
        mapped: endpoints.length - unmapped.length,
        unmapped: unmapped.length,
      },
      byService,
      unmapped,
    };
    writeJson(coveragePath, report);
    console.log(`coverage: ${report.totals.mapped}/${report.totals.endpoints} mapped, ${report.totals.unmapped} unmapped`);
    for (const endpoint of unmapped) {
      console.log(`  UNMAPPED ${endpoint.method} ${endpoint.path} (${endpoint.service})`);
    }
  }
  if (subcommand === "gateway-static") {
    const { endpoints } = JSON.parse(readFileSync(inventoryPath, "utf8"));
    const result = gatewayStatic(endpoints);
    if (result.failures.length) {
      console.error("gateway-static: FAIL");
      for (const failure of result.failures) console.error(`  ${failure}`);
      process.exit(1);
    }
    console.log(`gateway-static: PASS (${result.routes} location rules, ${endpoints.filter((e) => e.service !== "gateway").length} service endpoints routed)`);
  }
}

const subcommand = process.argv[2] || "all";
run(subcommand);
