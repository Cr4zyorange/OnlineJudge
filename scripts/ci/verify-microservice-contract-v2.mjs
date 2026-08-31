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
  grade: ['/internal/v2/courses/{courseId}/grade-publications']
};

const expectedEventTypes = [
  'identity.security-version.changed.v2',
  'course.member.changed.v2',
  'course.membership.snapshot.v2',
  'course.announcement.published.v2',
  'assessment.source-grade.changed.v2',
  'assessment.evaluation.completed.v2',
  'assessment.lab.published.v2',
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
  'course.membership.snapshot.v2': {
    aggregateType: 'course-membership-roster',
    aggregateIdTemplate: '{courseId}',
    envelopeSchema: 'CourseMembershipSnapshotEvent',
    payloadSchema: 'CourseMembershipSnapshotPayload',
    requiredPayload: ['courseId', 'rosterVersion', 'members']
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
    requiredPayload: ['courseId', 'sourceType', 'sourceId', 'studentId', 'score', 'fullScore', 'status', 'sourceVersion']
  },
  'assessment.evaluation.completed.v2': {
    aggregateType: 'assessment-submission',
    aggregateIdTemplate: '{submissionId}',
    envelopeSchema: 'AssessmentEvaluationCompletedEvent',
    payloadSchema: 'AssessmentEvaluationCompletedPayload',
    requiredPayload: ['courseId', 'submissionId', 'evaluationStatus', 'evaluationVersion', 'completedAt']
  },
  'assessment.lab.published.v2': {
    aggregateType: 'assessment-lab',
    aggregateIdTemplate: '{labId}',
    envelopeSchema: 'AssessmentLabPublishedEvent',
    payloadSchema: 'AssessmentLabPublishedPayload',
    requiredPayload: ['courseId', 'labId', 'title', 'deadline', 'receiverScope', 'publishedAt']
  },
  'assessment.homework.published.v2': {
    aggregateType: 'assessment-homework',
    aggregateIdTemplate: '{homeworkId}',
    envelopeSchema: 'AssessmentHomeworkPublishedEvent',
    payloadSchema: 'AssessmentHomeworkPublishedPayload',
    requiredPayload: ['courseId', 'homeworkId', 'title', 'deadline', 'receiverScope', 'publishedAt']
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

function schemaHasTypes(schema, expectedTypes) {
  const actual = new Set(Array.isArray(schema?.type) ? schema.type : [schema?.type]);
  return actual.size === expectedTypes.length && expectedTypes.every((type) => actual.has(type));
}

function sourceGradeConditionalProblems(schema, label) {
  const problems = [];
  const check = (condition, message) => {
    if (!condition) problems.push(`${label}: ${message}`);
  };
  const score = schema?.properties?.score;
  const fullScore = schema?.properties?.fullScore;
  const status = schema?.properties?.status;
  const branches = schema?.oneOf;
  const isBranch = (branch, expectedStatus, expectedScoreType) =>
    branch?.type === 'object'
      && hasRequiredProperties(branch, ['status', 'score'])
      && branch?.properties?.status?.const === expectedStatus
      && schemaHasTypes(branch?.properties?.score, [expectedScoreType])
      && (expectedScoreType !== 'number' || branch?.properties?.score?.minimum === 0);

  check(schema?.type === 'object' && schema?.additionalProperties === false, 'must be a closed object');
  check(hasRequiredProperties(schema, ['courseId', 'sourceType', 'sourceId', 'studentId', 'score', 'fullScore', 'status']),
    'must require courseId, sourceType, sourceId, studentId, score, fullScore, status');
  check(status?.type === 'string' && Array.isArray(status?.enum)
      && status.enum.length === 2 && status.enum.includes('SCORED') && status.enum.includes('UNGRADED'),
  'status must be the SCORED/UNGRADED discriminator');
  check(schemaHasTypes(score, ['number', 'null']) && score?.minimum === 0,
    'score must be nullable only at the shared field and retain minimum 0 for numeric values');
  check(fullScore?.type === 'number' && fullScore?.exclusiveMinimum === 0,
    'fullScore must be a positive number');
  check(Array.isArray(branches) && branches.length === 2
      && branches.some((branch) => isBranch(branch, 'SCORED', 'number'))
      && branches.some((branch) => isBranch(branch, 'UNGRADED', 'null')),
  'must use mutually exclusive SCORED:number and UNGRADED:null score branches');
  return problems;
}

function courseMembershipSnapshotProblems(schema, label) {
  const problems = [];
  const check = (condition, message) => {
    if (!condition) problems.push(`${label}: ${message}`);
  };
  const rosterVersion = schema?.properties?.rosterVersion;
  const members = schema?.properties?.members;
  const member = schema?.components?.schemas?.CourseMembershipSnapshotMember;
  check(schema?.type === 'object' && schema?.additionalProperties === false,
    'must be a closed object');
  check(hasRequiredProperties(schema, ['courseId', 'rosterVersion', 'members']),
    'must require courseId, rosterVersion, members');
  check(rosterVersion?.type === 'integer' && rosterVersion?.minimum === 1,
    'rosterVersion must be a positive integer watermark');
  check(members?.type === 'array'
      && members?.items?.$ref === '#/components/schemas/CourseMembershipSnapshotMember',
  'members must be an atomic array of CourseMembershipSnapshotMember');
  check(member?.type === 'object' && member?.additionalProperties === false
      && hasRequiredProperties(member, ['userId', 'membershipStatus', 'memberVersion'])
      && member?.properties?.userId?.type === 'string'
      && member?.properties?.userId?.minLength === 1
      && member?.properties?.membershipStatus?.type === 'string'
      && Array.isArray(member?.properties?.membershipStatus?.enum)
      && member.properties.membershipStatus.enum.length === 2
      && member.properties.membershipStatus.enum.includes('ACTIVE')
      && member.properties.membershipStatus.enum.includes('REMOVED')
      && member?.properties?.memberVersion?.type === 'integer'
      && member.properties.memberVersion.minimum === 1,
  'members must be closed userId/status/memberVersion facts');
  return problems;
}

function serviceTokenResponseProblems(document, label) {
  const problems = [];
  const check = (condition, message) => {
    if (!condition) problems.push(`${label}: ${message}`);
  };
  const operation = document?.paths?.['/internal/v2/service-tokens']?.post;
  const requestReference = operation?.requestBody?.content?.['application/json']?.schema?.$ref;
  const responseReference = operation?.responses?.['201']?.content?.['application/json']?.schema?.$ref;
  const request = document?.components?.schemas?.ServiceTokenRequest;
  const response = document?.components?.schemas?.ServiceTokenResponse;
  const accessToken = response?.properties?.accessToken;

  check(requestReference === '#/components/schemas/ServiceTokenRequest', 'mint request must use ServiceTokenRequest');
  check(responseReference === '#/components/schemas/ServiceTokenResponse', '201 mint response must use ServiceTokenResponse');
  check(request?.type === 'object' && request?.additionalProperties === false,
    'ServiceTokenRequest must be a closed input object');
  check(!Object.hasOwn(request?.properties ?? {}, 'accessToken'),
    'ServiceTokenRequest must never accept an accessToken supplied by the caller');
  check(response?.type === 'object' && response?.additionalProperties === false,
    'ServiceTokenResponse must be a closed output object');
  check(hasRequiredProperties(response, ['accessToken', 'expiresAt', 'audience']),
    'ServiceTokenResponse must require accessToken, expiresAt, audience');
  check(accessToken?.type === 'string' && accessToken?.readOnly === true && accessToken?.writeOnly !== true,
    '201 accessToken must be a readOnly consumable response field, never writeOnly');
  return problems;
}

function homeworkPublicApiProblems(document, label) {
  const problems = [];
  const check = (condition, message) => {
    if (!condition) problems.push(`${label}: ${message}`);
  };
  const publishScores = document?.paths?.['/api/v1/homeworks/{homeworkId}/scores/publish']?.put;
  const reevaluate = document?.paths?.['/api/v1/submissions/{submissionId}/reevaluate']?.post;
  const homeworkSummaryEnvelope = document?.components?.schemas?.HomeworkSummaryEnvelope;
  const reasonRequest = document?.components?.schemas?.HomeworkReevaluationRequest;

  check(publishScores?.operationId === 'publishHomeworkScores' && publishScores?.['x-onlinejudge-api-id'] === 'API-HWK-14',
    'API-HWK-14 score publication path and operation id are required');
  check((publishScores?.security ?? []).some((requirement) => Object.hasOwn(requirement, 'userJwt')),
    'API-HWK-14 must require a user JWT');
  check(operationHasRequestId(publishScores), 'API-HWK-14 must require X-Request-Id');
  check(publishScores?.responses?.['200']?.content?.['application/json']?.schema?.$ref === '#/components/schemas/HomeworkSummaryEnvelope',
    'API-HWK-14 success response must use HomeworkSummaryEnvelope');
  check(homeworkSummaryEnvelope?.type === 'object' && homeworkSummaryEnvelope?.additionalProperties === false
      && hasRequiredProperties(homeworkSummaryEnvelope, ['code', 'message', 'data'])
      && homeworkSummaryEnvelope?.properties?.code?.const === 0
      && homeworkSummaryEnvelope?.properties?.message?.type === 'string'
      && homeworkSummaryEnvelope?.properties?.data?.$ref === '#/components/schemas/HomeworkSummary',
  'HomeworkSummaryEnvelope must be the standard browser response wrapper');
  for (const status of ['403', '404', '409']) {
    check(responseHasErrorSchema(publishScores?.responses?.[status]), `API-HWK-14 must declare ${status} Error response`);
  }

  check(reevaluate?.['x-onlinejudge-api-id'] === 'API-HWK-12', 'API-HWK-12 identifier is required');
  check(reevaluate?.requestBody?.required === true
      && reevaluate?.requestBody?.content?.['application/json']?.schema?.$ref === '#/components/schemas/HomeworkReevaluationRequest',
  'API-HWK-12 must require HomeworkReevaluationRequest');
  check(reasonRequest?.type === 'object' && reasonRequest?.additionalProperties === false
      && hasRequiredProperties(reasonRequest, ['reason'])
      && reasonRequest?.properties?.reason?.type === 'string'
      && reasonRequest.properties.reason.minLength === 1
      && reasonRequest.properties.reason.maxLength === 500,
  'HomeworkReevaluationRequest must require a 1-500 character reason');
  check(responseHasErrorSchema(reevaluate?.responses?.['400']), 'API-HWK-12 must declare a validation Error response');
  check(responseHasErrorSchema(reevaluate?.responses?.['409']), 'API-HWK-12 must declare a conflict Error response');
  return problems;
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

  if (service === 'identity') {
    for (const failure of serviceTokenResponseProblems(document, relativePath)) problem(failure);
  }
  if (service === 'assessment') {
    for (const failure of sourceGradeConditionalProblems(document.components?.schemas?.SourceGrade, `${relativePath}: SourceGrade`)) {
      problem(failure);
    }
    for (const failure of homeworkPublicApiProblems(document, `${relativePath}: HWK public API`)) {
      problem(failure);
    }
  }

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

function valueMatchesType(value, type) {
  switch (type) {
    case 'object': return isObject(value);
    case 'string': return typeof value === 'string';
    case 'integer': return Number.isInteger(value);
    case 'number': return typeof value === 'number' && Number.isFinite(value);
    case 'null': return value === null;
    default: return true;
  }
}

function validateValue(value, schema, path) {
  const failures = [];
  if (!schema) return [`${path}: missing schema`];
  if (Array.isArray(schema.allOf)) {
    for (const branch of schema.allOf) failures.push(...validateValue(value, branch, path));
  }
  if (Array.isArray(schema.oneOf)) {
    const matchingBranches = schema.oneOf.filter((branch) => validateValue(value, branch, path).length === 0);
    if (matchingBranches.length !== 1) failures.push(`${path}: must match exactly one conditional branch`);
  }
  if (Object.hasOwn(schema, 'const') && value !== schema.const) failures.push(`${path}: must equal ${JSON.stringify(schema.const)}`);
  if (Array.isArray(schema.enum) && !schema.enum.includes(value)) failures.push(`${path}: must be one of ${schema.enum.join(', ')}`);
  const declaredTypes = Array.isArray(schema.type) ? schema.type : [schema.type];
  if (declaredTypes[0] !== undefined && !declaredTypes.some((type) => valueMatchesType(value, type))) {
    return [`${path}: must be ${declaredTypes.join(' or ')}`];
  }
  if (schema.type === 'object' || Object.hasOwn(schema, 'properties') || Object.hasOwn(schema, 'required')) {
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
  if (declaredTypes.includes('string') && typeof value === 'string') {
    if (Number.isInteger(schema.minLength) && value.length < schema.minLength) failures.push(`${path}: too short`);
    if (schema.format === 'uuid' && !isUuid(value)) failures.push(`${path}: must be a UUID`);
    if (schema.format === 'date-time' && !isDateTime(value)) failures.push(`${path}: must be an RFC3339 date-time`);
    return failures;
  }
  if (declaredTypes.includes('integer') && Number.isInteger(value)) {
    if (typeof schema.minimum === 'number' && value < schema.minimum) failures.push(`${path}: below minimum`);
    if (typeof schema.exclusiveMinimum === 'number' && value <= schema.exclusiveMinimum) failures.push(`${path}: below or equal to exclusive minimum`);
    return failures;
  }
  if (declaredTypes.includes('number') && typeof value === 'number' && Number.isFinite(value)) {
    if (typeof schema.minimum === 'number' && value < schema.minimum) failures.push(`${path}: below minimum`);
    if (typeof schema.exclusiveMinimum === 'number' && value <= schema.exclusiveMinimum) failures.push(`${path}: below or equal to exclusive minimum`);
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
  if (envelope?.eventType === 'course.membership.snapshot.v2') {
    const members = envelope?.payload?.members;
    if (!Array.isArray(members)) {
      failures.push('envelope.payload.members: must be an array');
    } else {
      const seenUserIds = new Set();
      for (const [index, member] of members.entries()) {
        const memberPath = `envelope.payload.members[${index}]`;
        if (!isObject(member)) {
          failures.push(`${memberPath}: must be an object`);
          continue;
        }
        const allowed = new Set(['userId', 'membershipStatus', 'memberVersion']);
        for (const property of Object.keys(member)) {
          if (!allowed.has(property)) failures.push(`${memberPath}: unexpected ${property}`);
        }
        if (typeof member.userId !== 'string' || member.userId.length === 0) failures.push(`${memberPath}.userId: must be a non-empty string`);
        if (seenUserIds.has(member.userId)) failures.push(`${memberPath}.userId: must be unique within a complete roster`);
        seenUserIds.add(member.userId);
        if (!['ACTIVE', 'REMOVED'].includes(member.membershipStatus)) failures.push(`${memberPath}.membershipStatus: must be ACTIVE or REMOVED`);
        if (!Number.isInteger(member.memberVersion) || member.memberVersion < 1) failures.push(`${memberPath}.memberVersion: must be a positive integer`);
      }
    }
    if (envelope?.payload?.rosterVersion !== envelope?.aggregateVersion) {
      failures.push('envelope.payload.rosterVersion: must equal envelope.aggregateVersion');
    }
  }
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

    if (eventType === 'assessment.source-grade.changed.v2') {
      for (const failure of sourceGradeConditionalProblems(payloadSchema, `${eventType} payload`)) failures.push(failure);
    }

    if (eventType === 'course.membership.snapshot.v2') {
      for (const failure of courseMembershipSnapshotProblems(
        { ...payloadSchema, components: document.components },
        `${eventType} payload`
      )) failures.push(failure);
      check(
        message['x-onlinejudge-completeness']?.includes('atomic complete roster')
          && message['x-onlinejudge-completeness']?.includes('course.member.changed.v2'),
        `${eventType} must state that it is the atomic bootstrap watermark and member events are incremental`
      );
    }

    if (eventType === 'assessment.homework.published.v2' || eventType === 'assessment.lab.published.v2') {
      const title = payloadSchema?.properties?.title;
      const deadline = payloadSchema?.properties?.deadline;
      const receiverScope = payloadSchema?.properties?.receiverScope;
      check(title?.type === 'string' && title?.minLength === 1 && title?.maxLength === 100,
        `${eventType} title must be a non-empty string bounded to 100 characters`);
      check(deadline?.type === 'string' && deadline?.format === 'date-time',
        `${eventType} deadline must be an RFC3339 date-time`);
      check(receiverScope?.type === 'string'
          && Array.isArray(receiverScope?.enum)
          && receiverScope.enum.length === 1
          && receiverScope.enum[0] === 'COURSE_ACTIVE_STUDENTS',
        `${eventType} receiverScope must be the bounded COURSE_ACTIVE_STUDENTS selector, never a roster`);
    }
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

function sourceGradeDocumentationProblems(relativePath, text) {
  const problems = [];
  const check = (condition, message) => {
    if (!condition) problems.push(`${relativePath}: ${message}`);
  };
  check(/SCORED[\s\S]{0,160}(?:`?number`?|数值)/.test(text),
    'must state SCORED as a numeric source-grade fact');
  check(/UNGRADED[\s\S]{0,160}`?null`?/.test(text),
    'must state UNGRADED as score=null, never a fabricated score');
  return problems;
}

function serviceTokenDocumentationProblems(relativePath, text) {
  const problems = [];
  const check = (condition, message) => {
    if (!condition) problems.push(`${relativePath}: ${message}`);
  };
  check(text.includes('accessToken') && text.includes('readOnly') && text.includes('response-only'),
    'must state that minted accessToken is a readOnly response-only field');
  check(/accessToken[\s\S]{0,180}(?:请求体|request)/.test(text),
    'must forbid accepting accessToken in the mint request body');
  return problems;
}

function validateDocumentation() {
  const currentContract = 'docs/开发/D6-三服务共享契约-306.md';
  const adr = 'docs/adr/ADR-006-三业务服务与可靠消息契约.md';
  const finalOverview = 'docs/最终提交/软件概要设计说明书.md';
  const finalDetail = 'docs/最终提交/软件详细设计说明书.md';
  for (const relativePath of [currentContract, adr, finalOverview, finalDetail]) {
    const absolutePath = resolve(repoRoot, relativePath);
    assert(existsSync(absolutePath), `missing ${relativePath}`);
  }
  const sourceGradeDocuments = [currentContract, adr, finalOverview, finalDetail];
  for (const relativePath of sourceGradeDocuments) {
    const absolutePath = resolve(repoRoot, relativePath);
    if (!existsSync(absolutePath)) continue;
    const text = readFileSync(absolutePath, 'utf8');
    for (const failure of sourceGradeDocumentationProblems(relativePath, text)) problem(failure);
  }
  if (existsSync(resolve(repoRoot, currentContract))) {
    const text = readFileSync(resolve(repoRoot, currentContract), 'utf8');
    for (const expectedText of ['Identity', 'Course', 'Assessment', 'Grade', 'at-least-once', 'DLQ', 'X-Internal-Token']) {
      assert(text.includes(expectedText), `${currentContract}: missing required policy text ${expectedText}`);
    }
    for (const failure of serviceTokenDocumentationProblems(currentContract, text)) problem(failure);

    const ungradedMutationFailures = sourceGradeDocumentationProblems(
      'mutation:D6 source-grade',
      text.replaceAll('UNGRADED', 'UNSCORED')
    );
    assert(ungradedMutationFailures.length > 0, 'mutation: D6 accepted an omitted UNGRADED=null source-grade rule');
    if (ungradedMutationFailures.length > 0) rejectedMutationCount += 1;

    const tokenDirectionMutationFailures = serviceTokenDocumentationProblems(
      'mutation:D6 service-token',
      text.replaceAll('readOnly', 'writeOnly')
    );
    assert(tokenDirectionMutationFailures.length > 0, 'mutation: D6 accepted a writeOnly minted accessToken');
    if (tokenDirectionMutationFailures.length > 0) rejectedMutationCount += 1;
  }
  if (existsSync(resolve(repoRoot, adr))) {
    const text = readFileSync(resolve(repoRoot, adr), 'utf8');
    for (const failure of serviceTokenDocumentationProblems(adr, text)) problem(failure);
  }
  for (const rejectedDocument of [
    'docs/开发/D4-CROSS-SERVICE-共享契约.md',
    'docs/过程/概要/评测服务拆分设计与迁移边界.md'
  ]) {
    assert(!existsSync(resolve(repoRoot, rejectedDocument)),
      `${rejectedDocument}: rejected architecture document must remain deleted`);
  }
}

function validateHomeworkPublicationMigrationDocs() {
  const currentV2Documents = [
    'docs/最终提交/软件需求规格说明书.md',
    'docs/最终提交/软件概要设计说明书.md',
    'docs/最终提交/软件详细设计说明书.md',
    'docs/过程/概要/作业与自动评测模块概要设计提交稿（hwk）.md',
    'docs/过程/详细设计/HWK-作业与自动评测模块-详细设计提交稿.md',
    'docs/过程/概要/学习过程与通知提醒 - 概要设计.md',
    'docs/过程/需求/学习过程与通知提醒模块（前端总设计师负责）.md',
    'docs/过程/详细设计/LRN-学习过程与通知提醒-详细设计提交稿.md',
    'docs/过程/测试/D2-HWK业务场景与测试闭环.md',
    'docs/过程/测试/TST-DOC-06 HWK 作业与自动评测测试文档.md',
    'docs/最终提交/测试文档.md',
    'docs/开发/HWK-作业与自动评测模块开发流程.md',
    'docs/开发/LRN-学习过程与通知提醒模块开发流程.md',
    'docs/adr/ADR-006-三业务服务与可靠消息契约.md',
    'docs/开发/D6-三服务共享契约-306.md',
    'docs/diagrams/srs/fig_4_14b_hwk_publish_ssd.mmd',
    'docs/diagrams/arch/fig_5_2_hwk_02_publish_component.mmd',
    'docs/diagrams/dsd/fig_3_5_3a_hwk_publish_object.mmd'
  ];

  const currentV2RequiredTerms = ['HWK_5003', 'outbox', 'receiverScope'];
  const unqualifiedLegacyRules = [
    '必达通知同步加入来源事务，失败必须回滚关键业务状态',
    '必达通知与来源事务原子提交，通知失败必须回滚关键业务状态',
    'HOMEWORK_PUBLISHED 使用该契约以保证通知失败时作业发布整体回滚',
    '通过 `publishRequired` 同步必达，失败向上抛出并回滚发布事务'
  ];

  function migrationProblems(relativePath, text) {
    const problems = [];
    for (const requiredText of currentV2RequiredTerms) {
      if (!text.includes(requiredText)) {
        problems.push(`${relativePath}: must state the v2 HOMEWORK_PUBLISHED ${requiredText} migration rule`);
      }
    }
    for (const legacyRule of unqualifiedLegacyRules) {
      if (text.includes(legacyRule)) {
        problems.push(`${relativePath}: retains the unqualified retired v1 rule: ${legacyRule}`);
      }
    }
    if (text.includes('publishRequired')) {
      problems.push(`${relativePath}: current five-service documents must not retain publishRequired`);
    }
    return problems;
  }

  let finalSrsText;
  for (const relativePath of currentV2Documents) {
    const absolutePath = resolve(repoRoot, relativePath);
    assert(existsSync(absolutePath), `missing ${relativePath}`);
    if (!existsSync(absolutePath)) continue;
    const text = readFileSync(absolutePath, 'utf8');
    for (const migrationProblem of migrationProblems(relativePath, text)) problem(migrationProblem);
    if (relativePath === 'docs/最终提交/软件需求规格说明书.md') finalSrsText = text;
  }

  if (finalSrsText) {
    const reintroducedLegacyRule = `${finalSrsText}\n${unqualifiedLegacyRules[0]}`;
    const mutationFailures = migrationProblems('mutation:SRS', reintroducedLegacyRule);
    assert(mutationFailures.length > 0, 'mutation: reintroducing the unqualified v1 required-notification rollback rule was accepted');
    if (mutationFailures.length > 0) rejectedMutationCount += 1;
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

  const identity = readJson('contracts/v2/openapi/identity.openapi.json');
  if (identity) {
    const writeOnlyTokenMutation = JSON.parse(JSON.stringify(identity));
    const accessToken = writeOnlyTokenMutation.components.schemas.ServiceTokenResponse.properties.accessToken;
    delete accessToken.readOnly;
    accessToken.writeOnly = true;
    const writeOnlyFailures = serviceTokenResponseProblems(writeOnlyTokenMutation, 'mutation: identity service token');
    assert(writeOnlyFailures.length > 0, 'mutation: writeOnly 201 accessToken was accepted');
    if (writeOnlyFailures.length > 0) rejectedMutationCount += 1;

    const inputLeakMutation = JSON.parse(JSON.stringify(identity));
    inputLeakMutation.components.schemas.ServiceTokenRequest.properties.accessToken = { type: 'string' };
    const inputLeakFailures = serviceTokenResponseProblems(inputLeakMutation, 'mutation: identity service token');
    assert(inputLeakFailures.length > 0, 'mutation: ServiceTokenRequest accepting accessToken was accepted');
    if (inputLeakFailures.length > 0) rejectedMutationCount += 1;
  }

  const assessment = readJson('contracts/v2/openapi/assessment.openapi.json');
  if (assessment) {
    const numberOnlySourceMutation = JSON.parse(JSON.stringify(assessment));
    numberOnlySourceMutation.components.schemas.SourceGrade.properties.score.type = 'number';
    const numberOnlyFailures = sourceGradeConditionalProblems(
      numberOnlySourceMutation.components.schemas.SourceGrade,
      'mutation: OpenAPI SourceGrade'
    );
    assert(numberOnlyFailures.length > 0, 'mutation: OpenAPI UNGRADED source grade without nullable score was accepted');
    if (numberOnlyFailures.length > 0) rejectedMutationCount += 1;

    const missingPublishScoresMutation = JSON.parse(JSON.stringify(assessment));
    delete missingPublishScoresMutation.paths['/api/v1/homeworks/{homeworkId}/scores/publish'];
    const missingPublishScoresFailures = homeworkPublicApiProblems(
      missingPublishScoresMutation,
      'mutation: OpenAPI HWK public API'
    );
    assert(missingPublishScoresFailures.length > 0, 'mutation: missing API-HWK-14 score publication path was accepted');
    if (missingPublishScoresFailures.length > 0) rejectedMutationCount += 1;

    const missingRejudgeReasonMutation = JSON.parse(JSON.stringify(assessment));
    delete missingRejudgeReasonMutation.components.schemas.HomeworkReevaluationRequest.properties.reason;
    missingRejudgeReasonMutation.components.schemas.HomeworkReevaluationRequest.required = [];
    const missingRejudgeReasonFailures = homeworkPublicApiProblems(
      missingRejudgeReasonMutation,
      'mutation: OpenAPI HWK public API'
    );
    assert(missingRejudgeReasonFailures.length > 0, 'mutation: API-HWK-12 without a required reason was accepted');
    if (missingRejudgeReasonFailures.length > 0) rejectedMutationCount += 1;
  }

  const missingStatusMutation = JSON.parse(JSON.stringify(asyncApi));
  delete missingStatusMutation.components.schemas.AssessmentSourceGradeChangedPayload.properties.status;
  missingStatusMutation.components.schemas.AssessmentSourceGradeChangedPayload.required =
    missingStatusMutation.components.schemas.AssessmentSourceGradeChangedPayload.required.filter((field) => field !== 'status');
  const missingStatusFailures = validateAsyncApiDocument(missingStatusMutation);
  assert(missingStatusFailures.length > 0, 'mutation: source-grade event without status was accepted');
  if (missingStatusFailures.length > 0) rejectedMutationCount += 1;

  const missingRosterWatermarkMutation = JSON.parse(JSON.stringify(asyncApi));
  delete missingRosterWatermarkMutation.components.schemas.CourseMembershipSnapshotPayload.properties.rosterVersion;
  missingRosterWatermarkMutation.components.schemas.CourseMembershipSnapshotPayload.required =
    missingRosterWatermarkMutation.components.schemas.CourseMembershipSnapshotPayload.required
      .filter((field) => field !== 'rosterVersion');
  const missingRosterWatermarkFailures = validateAsyncApiDocument(missingRosterWatermarkMutation);
  assert(missingRosterWatermarkFailures.length > 0, 'mutation: course snapshot without rosterVersion was accepted');
  if (missingRosterWatermarkFailures.length > 0) rejectedMutationCount += 1;

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

  const ungradedFixture = readJson('contracts/v2/examples/event-envelope.source-grade-ungraded.valid.json');
  if (ungradedFixture) {
    ungradedFixture.payload.score = 10;
    const ungradedWithScoreFailures = validateEnvelope(ungradedFixture, asyncApi);
    assert(ungradedWithScoreFailures.length > 0, 'mutation: UNGRADED source grade with numeric score was accepted');
    if (ungradedWithScoreFailures.length > 0) rejectedMutationCount += 1;
  }

  const homeworkFixture = readJson('contracts/v2/examples/event-envelope.homework-published.valid.json');
  if (!homeworkFixture) return;
  for (const field of ['title', 'deadline', 'receiverScope']) {
    const missingTaskFactMutation = JSON.parse(JSON.stringify(homeworkFixture));
    delete missingTaskFactMutation.payload[field];
    const missingTaskFactFailures = validateEnvelope(missingTaskFactMutation, asyncApi);
    assert(missingTaskFactFailures.length > 0, `mutation: homework publication without ${field} was accepted`);
    if (missingTaskFactFailures.length > 0) rejectedMutationCount += 1;
  }
}

function assessmentRabbitTopologyProblems(documents) {
  const required = [
    'services/assessment/src/main/resources/application.yml',
    'services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/RabbitOutboxRelay.java',
    'services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/CourseMembershipRabbitConsumer.java',
    'services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/CourseMembershipDeadLetterReplayCommand.java',
    'services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/IdentitySecurityVersionRabbitConsumer.java',
    'services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/IdentitySecurityVersionDeadLetterReplayCommand.java',
    'deploy/docker/compose.assessment.yml'
  ];
  return required.filter((path) => !documents.get(path)?.includes('onlinejudge.events.v2'))
    .map((path) => `${path} must use canonical durable exchange onlinejudge.events.v2`);
}

function validateAssessmentRabbitTopology() {
  const paths = [
    'services/assessment/src/main/resources/application.yml',
    'services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/RabbitOutboxRelay.java',
    'services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/CourseMembershipRabbitConsumer.java',
    'services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/CourseMembershipDeadLetterReplayCommand.java',
    'services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/IdentitySecurityVersionRabbitConsumer.java',
    'services/assessment/src/main/java/com/onlinejudge/assessmentservice/messaging/IdentitySecurityVersionDeadLetterReplayCommand.java',
    'deploy/docker/compose.assessment.yml'
  ];
  const documents = new Map();
  for (const path of paths) {
    const absolutePath = resolve(repoRoot, path);
    if (!existsSync(absolutePath)) {
      problem(`missing ${path}`);
      continue;
    }
    documents.set(path, readFileSync(absolutePath, 'utf8'));
  }
  for (const failure of assessmentRabbitTopologyProblems(documents)) problem(failure);

  const legacyDefaultMutation = new Map(documents);
  const application = legacyDefaultMutation.get('services/assessment/src/main/resources/application.yml');
  if (application) {
    legacyDefaultMutation.set('services/assessment/src/main/resources/application.yml', application.replace('onlinejudge.events.v2', 'onlinejudge.events'));
    const failures = assessmentRabbitTopologyProblems(legacyDefaultMutation);
    assert(failures.length > 0, 'mutation: Assessment legacy onlinejudge.events default was accepted');
    if (failures.length > 0) rejectedMutationCount += 1;
  }
}

for (const [service, expectedPaths] of Object.entries(serviceContracts)) {
  validateOpenApi(service, expectedPaths);
}
const asyncApi = validateAsyncApi();
validateDocumentation();
validateHomeworkPublicationMigrationDocs();
validateFixtures(asyncApi);
validateRejectingMutations(asyncApi);
validateAssessmentRabbitTopology();

if (errors.length > 0) {
  console.error(`microservice-contract-v2: FAIL (${errors.length} problem(s))`);
  for (const error of errors) console.error(`- ${error}`);
  process.exitCode = 1;
} else {
  console.log(
    `microservice-contract-v2: PASS (${openApiCount} OpenAPI, ${asyncMessageCount} AsyncAPI messages, ${validFixtureCount} valid fixture(s), ${rejectedFixtureCount} incompatible fixture(s), ${rejectedMutationCount} rejecting mutation(s) rejected)`
  );
}
