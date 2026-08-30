#!/usr/bin/env node

import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)));
const errors = [];
let openApiCount = 0;
let asyncMessageCount = 0;
let validFixtureCount = 0;
let rejectedFixtureCount = 0;

const serviceContracts = {
  identity: ['/.well-known/jwks.json', '/internal/v2/service-tokens'],
  course: [
    '/internal/v2/courses/{courseId}/authorizations/{userId}',
    '/internal/v2/courses/{courseId}/members'
  ],
  assessment: ['/internal/v2/source-grades'],
  grade: ['/internal/v2/courses/{courseId}/grade-publications'],
  learning: ['/internal/v2/notifications/reconciliation-requests']
};

const expectedEventTypes = [
  'identity.security-version.changed.v2',
  'course.member.changed.v2',
  'course.announcement.published.v2',
  'assessment.source-grade.changed.v2',
  'assessment.evaluation.completed.v2',
  'assessment.homework.published.v2',
  'grade.published.v2',
  'grade.review.processed.v2'
];

function relative(absolutePath) {
  return absolutePath.slice(repoRoot.length + 1);
}

function problem(message) {
  errors.push(message);
}

function readJson(relativePath) {
  const absolutePath = resolve(repoRoot, relativePath);
  if (!existsSync(absolutePath)) {
    problem(`missing ${relativePath}`);
    return undefined;
  }
  try {
    return JSON.parse(readFileSync(absolutePath, 'utf8'));
  } catch (error) {
    problem(`invalid JSON ${relativePath}: ${error.message}`);
    return undefined;
  }
}

function assert(condition, message) {
  if (!condition) {
    problem(message);
  }
}

function hasRequiredProperties(schema, names) {
  const required = new Set(schema?.required ?? []);
  return names.every((name) => required.has(name));
}

function operationHasRequestId(operation) {
  return (operation?.parameters ?? []).some(
    (parameter) => parameter?.in === 'header' && parameter?.name === 'X-Request-Id' && parameter?.required === true
  );
}

function operationHasAudienceBoundServiceIdentity(operation) {
  const requirements = operation?.security ?? [];
  return requirements.some(
    (requirement) => Object.hasOwn(requirement, 'serviceJwt') || Object.hasOwn(requirement, 'mTLS')
  );
}

function validateOpenApi(service, expectedPaths) {
  const relativePath = `contracts/v2/openapi/${service}.openapi.json`;
  const document = readJson(relativePath);
  if (!document) return;

  openApiCount += 1;
  assert(String(document.openapi ?? '').startsWith('3.1.'), `${relativePath}: openapi must be 3.1.x`);
  assert(document.info?.version === '2.0.0', `${relativePath}: info.version must be 2.0.0`);
  assert(document['x-onlinejudge-service'] === service, `${relativePath}: service marker must be ${service}`);
  assert(document.components?.securitySchemes?.userJwt?.type === 'http', `${relativePath}: missing userJwt scheme`);
  assert(
    document.components?.securitySchemes?.serviceJwt?.type === 'apiKey'
      && document.components?.securitySchemes?.serviceJwt?.in === 'header'
      && document.components?.securitySchemes?.serviceJwt?.name === 'X-OnlineJudge-Service-Authorization',
    `${relativePath}: serviceJwt must be the audience-bound internal header scheme`
  );
  assert(document.components?.securitySchemes?.mTLS?.type === 'mutualTLS', `${relativePath}: missing mTLS scheme`);
  assert(
    hasRequiredProperties(document.components?.schemas?.Error, ['code', 'message', 'requestId', 'retryable']),
    `${relativePath}: Error schema must require code, message, requestId, retryable`
  );
  assert(
    hasRequiredProperties(document.components?.schemas?.Page, ['items', 'page', 'size', 'total']),
    `${relativePath}: Page schema must require items, page, size, total`
  );

  for (const expectedPath of expectedPaths) {
    const pathItem = document.paths?.[expectedPath];
    assert(pathItem, `${relativePath}: missing path ${expectedPath}`);
    if (!pathItem) continue;
    const operations = Object.entries(pathItem).filter(([method]) => ['get', 'post', 'put', 'delete', 'patch'].includes(method));
    assert(operations.length > 0, `${relativePath}: ${expectedPath} has no operation`);
    for (const [method, operation] of operations) {
      assert(operationHasRequestId(operation), `${relativePath}: ${method.toUpperCase()} ${expectedPath} must require X-Request-Id`);
      if (expectedPath !== '/.well-known/jwks.json') {
        assert(
          operationHasAudienceBoundServiceIdentity(operation),
          `${relativePath}: ${method.toUpperCase()} ${expectedPath} must accept audience-bound service JWT or mTLS`
        );
      }
      assert(
        Number.isInteger(operation['x-onlinejudge-timeout-ms']) && operation['x-onlinejudge-timeout-ms'] > 0,
        `${relativePath}: ${method.toUpperCase()} ${expectedPath} must declare a positive timeout`
      );
    }
  }
}

function validateEnvelope(envelope) {
  const failures = [];
  const required = [
    'eventId',
    'eventType',
    'payloadVersion',
    'aggregateType',
    'aggregateId',
    'aggregateVersion',
    'occurredAt',
    'correlationId',
    'payload'
  ];
  for (const field of required) {
    if (!(field in (envelope ?? {}))) failures.push(`missing ${field}`);
  }
  if (!expectedEventTypes.includes(envelope?.eventType)) failures.push(`unknown eventType ${envelope?.eventType}`);
  if (envelope?.payloadVersion !== 2) failures.push(`unsupported payloadVersion ${envelope?.payloadVersion}`);
  if (!Number.isInteger(envelope?.aggregateVersion) || envelope.aggregateVersion < 1) {
    failures.push('aggregateVersion must be a positive integer');
  }
  if (typeof envelope?.payload !== 'object' || envelope.payload === null || Array.isArray(envelope.payload)) {
    failures.push('payload must be an object');
  }
  return failures;
}

function validateAsyncApi() {
  const relativePath = 'contracts/v2/asyncapi/events.asyncapi.json';
  const document = readJson(relativePath);
  if (!document) return;

  assert(String(document.asyncapi ?? '').startsWith('3.'), `${relativePath}: asyncapi must be 3.x`);
  assert(document.info?.version === '2.0.0', `${relativePath}: info.version must be 2.0.0`);
  const envelope = document.components?.schemas?.EventEnvelope;
  assert(
    hasRequiredProperties(envelope, [
      'eventId',
      'eventType',
      'payloadVersion',
      'aggregateType',
      'aggregateId',
      'aggregateVersion',
      'occurredAt',
      'correlationId',
      'payload'
    ]),
    `${relativePath}: EventEnvelope required fields are incomplete`
  );

  const messages = document.components?.messages ?? {};
  for (const eventType of expectedEventTypes) {
    const message = messages[eventType];
    assert(message, `${relativePath}: missing message ${eventType}`);
    if (!message) continue;
    asyncMessageCount += 1;
    assert(message.payload?.$ref === '#/components/schemas/EventEnvelope', `${relativePath}: ${eventType} must use EventEnvelope`);
    assert(typeof message['x-onlinejudge-producer'] === 'string', `${relativePath}: ${eventType} needs a producer`);
    assert(Array.isArray(message['x-onlinejudge-consumers']) && message['x-onlinejudge-consumers'].length > 0,
      `${relativePath}: ${eventType} needs at least one consumer`);
    assert(typeof message['x-onlinejudge-idempotency-key'] === 'string', `${relativePath}: ${eventType} needs idempotency semantics`);
  }
}

function validateDocumentation() {
  const currentContract = 'docs/开发/D6-D7-五服务共享契约-v2.md';
  const adr = 'docs/adr/ADR-006-五业务服务与可靠消息契约.md';
  const v1Contract = 'docs/开发/D4-CROSS-SERVICE-共享契约.md';
  for (const relativePath of [currentContract, adr, v1Contract]) {
    const absolutePath = resolve(repoRoot, relativePath);
    assert(existsSync(absolutePath), `missing ${relativePath}`);
  }
  if (existsSync(resolve(repoRoot, currentContract))) {
    const text = readFileSync(resolve(repoRoot, currentContract), 'utf8');
    for (const expectedText of ['Identity', 'Course', 'Assessment', 'Grade', 'Learning', 'at-least-once', 'DLQ', 'X-Internal-Token']) {
      assert(text.includes(expectedText), `${currentContract}: missing required policy text ${expectedText}`);
    }
  }
  if (existsSync(resolve(repoRoot, v1Contract))) {
    const text = readFileSync(resolve(repoRoot, v1Contract), 'utf8');
    assert(text.includes('历史基线') && text.includes('D6-D7-五服务共享契约-v2.md'),
      `${v1Contract}: must identify v1 as historical and link v2`);
  }
}

function validateFixtures() {
  const examplesDirectory = resolve(repoRoot, 'contracts/v2/examples');
  if (!existsSync(examplesDirectory)) {
    problem('missing contracts/v2/examples');
    return;
  }
  const fixtureNames = readdirSync(examplesDirectory).filter((name) => name.endsWith('.json')).sort();
  const valid = fixtureNames.filter((name) => name.includes('.valid.'));
  const invalid = fixtureNames.filter((name) => name.includes('.invalid-'));
  assert(valid.length >= 1, 'contracts/v2/examples: need at least one valid fixture');
  assert(invalid.length >= 2, 'contracts/v2/examples: need at least two incompatible fixtures');
  for (const name of valid) {
    const envelope = readJson(`contracts/v2/examples/${name}`);
    if (!envelope) continue;
    const failures = validateEnvelope(envelope);
    assert(failures.length === 0, `contracts/v2/examples/${name}: valid fixture rejected: ${failures.join(', ')}`);
    if (failures.length === 0) validFixtureCount += 1;
  }
  for (const name of invalid) {
    const envelope = readJson(`contracts/v2/examples/${name}`);
    if (!envelope) continue;
    const failures = validateEnvelope(envelope);
    assert(failures.length > 0, `contracts/v2/examples/${name}: incompatible fixture was accepted`);
    if (failures.length > 0) rejectedFixtureCount += 1;
  }
}

for (const [service, expectedPaths] of Object.entries(serviceContracts)) {
  validateOpenApi(service, expectedPaths);
}
validateAsyncApi();
validateDocumentation();
validateFixtures();

if (errors.length > 0) {
  console.error(`microservice-contract-v2: FAIL (${errors.length} problem(s))`);
  for (const error of errors) console.error(`- ${error}`);
  process.exitCode = 1;
} else {
  console.log(
    `microservice-contract-v2: PASS (${openApiCount} OpenAPI, ${asyncMessageCount} AsyncAPI messages, ${validFixtureCount} valid fixture(s), ${rejectedFixtureCount} incompatible fixture(s) rejected)`
  );
}
