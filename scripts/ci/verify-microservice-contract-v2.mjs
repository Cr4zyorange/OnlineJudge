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
let rejectedMutationCount = 0;

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

const eventContracts = {
  'identity.security-version.changed.v2': {
    aggregateType: 'identity-user',
    aggregateIdTemplate: '{userId}',
    envelopeSchema: 'IdentitySecurityVersionChangedEvent',
    payloadSchema: 'IdentitySecurityVersionChangedPayload',
    requiredPayload: ['userId', 'securityVersion', 'changeReason']
  },
  'course.member.changed.v2': {
    aggregateType: 'course-member',
    aggregateIdTemplate: '{courseId}:{userId}',
    envelopeSchema: 'CourseMemberChangedEvent',
    payloadSchema: 'CourseMemberChangedPayload',
    requiredPayload: ['courseId', 'userId', 'membershipStatus', 'memberVersion']
  },
  'course.announcement.published.v2': {
    aggregateType: 'course-announcement',
    aggregateIdTemplate: '{announcementId}',
    envelopeSchema: 'CourseAnnouncementPublishedEvent',
    payloadSchema: 'CourseAnnouncementPublishedPayload',
    requiredPayload: ['courseId', 'announcementId', 'publishedAt']
  },
  'assessment.source-grade.changed.v2': {
    aggregateType: 'assessment-source-grade',
    aggregateIdTemplate: '{sourceType}:{sourceId}:{studentId}',
    envelopeSchema: 'AssessmentSourceGradeChangedEvent',
    payloadSchema: 'AssessmentSourceGradeChangedPayload',
    requiredPayload: ['courseId', 'sourceType', 'sourceId', 'studentId', 'score', 'fullScore', 'sourceVersion']
  },
  'assessment.evaluation.completed.v2': {
    aggregateType: 'assessment-submission',
    aggregateIdTemplate: '{submissionId}',
    envelopeSchema: 'AssessmentEvaluationCompletedEvent',
    payloadSchema: 'AssessmentEvaluationCompletedPayload',
    requiredPayload: ['courseId', 'submissionId', 'evaluationStatus', 'evaluationVersion', 'completedAt']
  },
  'assessment.homework.published.v2': {
    aggregateType: 'assessment-homework',
    aggregateIdTemplate: '{homeworkId}',
    envelopeSchema: 'AssessmentHomeworkPublishedEvent',
    payloadSchema: 'AssessmentHomeworkPublishedPayload',
    requiredPayload: ['courseId', 'homeworkId', 'publishedAt']
  },
  'grade.published.v2': {
    aggregateType: 'grade-publication',
    aggregateIdTemplate: '{publicationId}',
    envelopeSchema: 'GradePublishedEvent',
    payloadSchema: 'GradePublishedPayload',
    requiredPayload: ['courseId', 'publicationId', 'publishedAt', 'publicationVersion']
  },
  'grade.review.processed.v2': {
    aggregateType: 'grade-review',
    aggregateIdTemplate: '{reviewRequestId}',
    envelopeSchema: 'GradeReviewProcessedEvent',
    payloadSchema: 'GradeReviewProcessedPayload',
    requiredPayload: ['courseId', 'reviewRequestId', 'studentId', 'reviewStatus', 'resultVersion', 'processedAt']
  }
};

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

function responseHasErrorSchema(response) {
  return response?.content?.['application/json']?.schema?.$ref === '#/components/schemas/Error';
}

function responseHasErrorCode(response, code) {
  return Array.isArray(response?.['x-onlinejudge-error-codes']) && response['x-onlinejudge-error-codes'].includes(code);
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
        const unauthenticated = operation.responses?.['401'];
        const forbidden = operation.responses?.['403'];
        assert(
          responseHasErrorSchema(unauthenticated) && responseHasErrorCode(unauthenticated, 'SERVICE_IDENTITY_INVALID'),
          `${relativePath}: ${method.toUpperCase()} ${expectedPath} must declare 401 SERVICE_IDENTITY_INVALID for missing, invalid, expired, or wrong-audience service identity`
        );
        assert(
          responseHasErrorSchema(forbidden) && responseHasErrorCode(forbidden, 'SERVICE_IDENTITY_FORBIDDEN'),
          `${relativePath}: ${method.toUpperCase()} ${expectedPath} must reserve 403 SERVICE_IDENTITY_FORBIDDEN for an authenticated principal without required scope`
        );
        const acceptsServiceJwt = (operation.security ?? []).some((requirement) => Object.hasOwn(requirement, 'serviceJwt'));
        if (acceptsServiceJwt) {
          assert(
            /invalid/i.test(unauthenticated?.description ?? '')
              && /expired/i.test(unauthenticated?.description ?? '')
              && /audience/i.test(unauthenticated?.description ?? ''),
            `${relativePath}: ${method.toUpperCase()} ${expectedPath} 401 must explicitly cover invalid, expired, and wrong-audience service JWTs`
          );
        }
        assert(
          /authenticated/i.test(forbidden?.description ?? '') && /scope/i.test(forbidden?.description ?? ''),
          `${relativePath}: ${method.toUpperCase()} ${expectedPath} 403 must explicitly cover authenticated service identity without scope`
        );
      }
      assert(
        Number.isInteger(operation['x-onlinejudge-timeout-ms']) && operation['x-onlinejudge-timeout-ms'] > 0,
        `${relativePath}: ${method.toUpperCase()} ${expectedPath} must declare a positive timeout`
      );
    }
  }
}

function isObject(value) {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isUuid(value) {
  return typeof value === 'string'
    && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}

function isDateTime(value) {
  return typeof value === 'string'
    && /^\d{4}-\d{2}-\d{2}T/.test(value)
    && !Number.isNaN(Date.parse(value));
}

function validateValue(value, schema, path) {
  const failures = [];
  if (!schema) return [`${path}: missing schema`];
  if (Object.hasOwn(schema, 'const') && value !== schema.const) failures.push(`${path}: must equal ${JSON.stringify(schema.const)}`);
  if (Array.isArray(schema.enum) && !schema.enum.includes(value)) failures.push(`${path}: must be one of ${schema.enum.join(', ')}`);
  if (schema.type === 'object') {
    if (!isObject(value)) return [`${path}: must be an object`];
    const required = schema.required ?? [];
    for (const property of required) if (!Object.hasOwn(value, property)) failures.push(`${path}: missing ${property}`);
    if (schema.additionalProperties === false) {
      const permitted = new Set(Object.keys(schema.properties ?? {}));
      for (const property of Object.keys(value)) if (!permitted.has(property)) failures.push(`${path}: unexpected ${property}`);
    }
    for (const [property, propertySchema] of Object.entries(schema.properties ?? {})) {
      if (Object.hasOwn(value, property)) failures.push(...validateValue(value[property], propertySchema, `${path}.${property}`));
    }
    return failures;
  }
  if (schema.type === 'string') {
    if (typeof value !== 'string') return [`${path}: must be a string`];
    if (Number.isInteger(schema.minLength) && value.length < schema.minLength) failures.push(`${path}: too short`);
    if (schema.format === 'uuid' && !isUuid(value)) failures.push(`${path}: must be a UUID`);
    if (schema.format === 'date-time' && !isDateTime(value)) failures.push(`${path}: must be an RFC3339 date-time`);
    return failures;
  }
  if (schema.type === 'integer') {
    if (!Number.isInteger(value)) return [`${path}: must be an integer`];
    if (typeof schema.minimum === 'number' && value < schema.minimum) failures.push(`${path}: below minimum`);
    return failures;
  }
  if (schema.type === 'number') {
    if (typeof value !== 'number' || !Number.isFinite(value)) return [`${path}: must be a finite number`];
    if (typeof schema.minimum === 'number' && value < schema.minimum) failures.push(`${path}: below minimum`);
    return failures;
  }
  return failures;
}

function eventExtensionSchema(asyncApi, contract) {
  const schema = asyncApi?.components?.schemas?.[contract.envelopeSchema];
  return (schema?.allOf ?? []).find((part) => isObject(part) && isObject(part.properties));
}

function validateEnvelope(envelope, asyncApi) {
  const failures = [];
  const baseSchema = asyncApi?.components?.schemas?.EventEnvelope;
  failures.push(...validateValue(envelope, baseSchema, 'envelope'));
  const contract = eventContracts[envelope?.eventType];
  if (!contract) return failures;
  const extension = eventExtensionSchema(asyncApi, contract);
  if (!extension) return [...failures, `envelope: missing ${contract.envelopeSchema}`];
  const payloadReference = extension.properties?.payload?.$ref;
  const expectedReference = `#/components/schemas/${contract.payloadSchema}`;
  if (payloadReference !== expectedReference) failures.push(`envelope: ${contract.envelopeSchema} must reference ${contract.payloadSchema}`);
  failures.push(...validateValue(envelope.eventType, extension.properties?.eventType, 'envelope.eventType'));
  failures.push(...validateValue(envelope.aggregateType, extension.properties?.aggregateType, 'envelope.aggregateType'));
  const expectedAggregateId = contract.aggregateIdTemplate.replace(/\{([^}]+)\}/g, (_, field) => String(envelope?.payload?.[field] ?? ''));
  if (envelope?.aggregateId !== expectedAggregateId) failures.push(`envelope.aggregateId: must equal ${expectedAggregateId}`);
  const payloadSchema = asyncApi?.components?.schemas?.[contract.payloadSchema];
  failures.push(...validateValue(envelope.payload, payloadSchema, 'envelope.payload'));
  return failures;
}

function validateAsyncApiDocument(document) {
  const failures = [];
  const check = (condition, message) => {
    if (!condition) failures.push(message);
  };
  check(String(document?.asyncapi ?? '').startsWith('3.'), 'asyncapi must be 3.x');
  check(document?.info?.version === '2.0.0', 'info.version must be 2.0.0');
  const envelope = document.components?.schemas?.EventEnvelope;
  check(
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
    'EventEnvelope required fields are incomplete'
  );

  const messages = document.components?.messages ?? {};
  for (const eventType of expectedEventTypes) {
    const contract = eventContracts[eventType];
    const message = messages[eventType];
    check(message, `missing message ${eventType}`);
    if (!message) continue;
    check(message.payload?.$ref === `#/components/schemas/${contract.envelopeSchema}`, `${eventType} must use ${contract.envelopeSchema}`);
    check(typeof message['x-onlinejudge-producer'] === 'string', `${eventType} needs a producer`);
    check(Array.isArray(message['x-onlinejudge-consumers']) && message['x-onlinejudge-consumers'].length > 0,
      `${eventType} needs at least one consumer`);
    check(typeof message['x-onlinejudge-idempotency-key'] === 'string', `${eventType} needs idempotency semantics`);
    check(message['x-onlinejudge-ordering'] === `${contract.aggregateType} aggregateVersion`,
      `${eventType} ordering must be ${contract.aggregateType} aggregateVersion`);

    const eventSchema = document.components?.schemas?.[contract.envelopeSchema];
    const extension = eventExtensionSchema(document, contract);
    check(Array.isArray(eventSchema?.allOf) && eventSchema.allOf.some((part) => part?.$ref === '#/components/schemas/EventEnvelope'),
      `${eventType} must compose EventEnvelope`);
    check(extension?.properties?.eventType?.const === eventType, `${eventType} must bind eventType with const`);
    check(extension?.properties?.aggregateType?.const === contract.aggregateType,
      `${eventType} must bind aggregateType ${contract.aggregateType}`);
    check(extension?.['x-onlinejudge-aggregate-id-template'] === contract.aggregateIdTemplate,
      `${eventType} aggregateId template must be ${contract.aggregateIdTemplate}`);
    check(extension?.properties?.payload?.$ref === `#/components/schemas/${contract.payloadSchema}`,
      `${eventType} must bind payload schema ${contract.payloadSchema}`);
    const payloadSchema = document.components?.schemas?.[contract.payloadSchema];
    check(payloadSchema?.type === 'object' && payloadSchema?.additionalProperties === false,
      `${eventType} payload schema must be a closed object`);
    check(hasRequiredProperties(payloadSchema, contract.requiredPayload),
      `${eventType} payload schema must require ${contract.requiredPayload.join(', ')}`);
  }
  return failures;
}

function validateAsyncApi() {
  const relativePath = 'contracts/v2/asyncapi/events.asyncapi.json';
  const document = readJson(relativePath);
  if (!document) return undefined;
  const failures = validateAsyncApiDocument(document);
  for (const failure of failures) problem(`${relativePath}: ${failure}`);
  asyncMessageCount += expectedEventTypes.filter((eventType) => document.components?.messages?.[eventType]).length;
  return document;
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

function validateFixtures(asyncApi) {
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
    const failures = validateEnvelope(envelope, asyncApi);
    assert(failures.length === 0, `contracts/v2/examples/${name}: valid fixture rejected: ${failures.join(', ')}`);
    if (failures.length === 0) validFixtureCount += 1;
  }
  for (const name of invalid) {
    const envelope = readJson(`contracts/v2/examples/${name}`);
    if (!envelope) continue;
    const failures = validateEnvelope(envelope, asyncApi);
    assert(failures.length > 0, `contracts/v2/examples/${name}: incompatible fixture was accepted`);
    if (failures.length > 0) rejectedFixtureCount += 1;
  }
}

function validateRejectingMutations(asyncApi) {
  if (!asyncApi) return;
  const orderingMutation = JSON.parse(JSON.stringify(asyncApi));
  delete orderingMutation.components.messages['grade.published.v2']['x-onlinejudge-ordering'];
  const orderingFailures = validateAsyncApiDocument(orderingMutation);
  assert(orderingFailures.length > 0, 'mutation: deleting x-onlinejudge-ordering was accepted');
  if (orderingFailures.length > 0) rejectedMutationCount += 1;

  const validFixture = readJson('contracts/v2/examples/event-envelope.valid.json');
  if (!validFixture) return;
  const emptyPayloadMutation = JSON.parse(JSON.stringify(validFixture));
  emptyPayloadMutation.payload = {};
  const emptyPayloadFailures = validateEnvelope(emptyPayloadMutation, asyncApi);
  assert(emptyPayloadFailures.length > 0, 'mutation: empty event payload was accepted');
  if (emptyPayloadFailures.length > 0) rejectedMutationCount += 1;

  const aggregateMutation = JSON.parse(JSON.stringify(validFixture));
  aggregateMutation.aggregateType = 'unrelated-aggregate';
  const aggregateFailures = validateEnvelope(aggregateMutation, asyncApi);
  assert(aggregateFailures.length > 0, 'mutation: wrong event aggregate was accepted');
  if (aggregateFailures.length > 0) rejectedMutationCount += 1;
}

for (const [service, expectedPaths] of Object.entries(serviceContracts)) {
  validateOpenApi(service, expectedPaths);
}
const asyncApi = validateAsyncApi();
validateDocumentation();
validateFixtures(asyncApi);
validateRejectingMutations(asyncApi);

if (errors.length > 0) {
  console.error(`microservice-contract-v2: FAIL (${errors.length} problem(s))`);
  for (const error of errors) console.error(`- ${error}`);
  process.exitCode = 1;
} else {
  console.log(
    `microservice-contract-v2: PASS (${openApiCount} OpenAPI, ${asyncMessageCount} AsyncAPI messages, ${validFixtureCount} valid fixture(s), ${rejectedFixtureCount} incompatible fixture(s), ${rejectedMutationCount} review mutation(s) rejected)`
  );
}
