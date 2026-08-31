#!/usr/bin/env node

import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { verifyDataOwnershipContract } from './verify-data-ownership-contract.mjs';

const defaultRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)));

const mergeShas = {
  issue309: '50bdc8490b204c868aafe7b7f2602ce6826c84ad',
  issue338: 'dd89083a0a8c6a04512e48d6f9ccab0f62f8897f',
  issue336: '2a0ce94262596820eefe905bcd3c301c474880cf'
};
const identityDeliverySha = 'd9f3d74abd2d64b81632956832107bec7b0983be';

const requiredArchitectureMarkers = [
  '五服务架构冻结正本（#305）',
  'Identity、Course、Assessment、Grade、Learning',
  'Assessment API 与 Worker 不是两个业务服务',
  'taskId + generation + leaseOwner + leaseUntil',
  'course.membership.snapshot.v2',
  'assessment.homework.published.v2',
  'at-least-once',
  '不共享 Repository、不跨 Schema SQL、不使用全局内部 Token',
  'Grade 正常计算只使用本地来源成绩投影',
  'Course 不可用时失败关闭',
  'onlinejudge_identity'
];

const rejectedArchitectureDocuments = [
  'docs/开发/D4-CROSS-SERVICE-共享契约.md',
  'docs/过程/概要/评测服务拆分设计与迁移边界.md',
  'docs/diagrams/fig_2_6_assessment_service_boundary.mmd',
  'docs/最终提交/assets/fig_2_6_assessment_service_boundary.svg'
];

function read(rootPath, relativePath, problems) {
  const absolutePath = resolve(rootPath, relativePath);
  if (!existsSync(absolutePath)) {
    problems.push(`missing ${relativePath}`);
    return '';
  }
  return readFileSync(absolutePath, 'utf8');
}

function countCsvRecords(contents) {
  return contents.trim().split(/\r?\n/).filter(Boolean).length - 1;
}

function requireText(contents, text, message, problems) {
  if (!contents.includes(text)) problems.push(message);
}

function requireLocalMarkdownLink(rootPath, contents, documentPath, target, message, problems) {
  if (!contents.includes(`](${target})`)) {
    problems.push(`${message} at ${target}`);
    return;
  }
  if (!existsSync(resolve(rootPath, dirname(documentPath), target))) {
    problems.push(`${message} target does not exist: ${target}`);
  }
}

export function verifyFinalArchitecture305({ rootPath = defaultRoot } = {}) {
  const problems = [];
  let ownershipSummary;
  try {
    ownershipSummary = verifyDataOwnershipContract({ rootPath });
  } catch (error) {
    problems.push(`five-domain ownership contract must remain valid: ${error.message}`);
  }
  const architecture = read(rootPath, 'docs/开发/D6-D7-五服务架构冻结-305.md', problems);
  const ownershipDocument = read(rootPath, 'docs/开发/D6-DATA-五域数据所有权契约.md', problems);
  const workloadDocument = read(rootPath, 'docs/开发/D7-平台工作负载清单契约.md', problems);
  const identityDeliveryDocument = read(rootPath, 'docs/开发/D6-AUTH-独立身份服务交付.md', problems);
  for (const rejectedDocument of rejectedArchitectureDocuments) {
    if (existsSync(resolve(rootPath, rejectedDocument))) {
      problems.push(`rejected architecture document must be deleted: ${rejectedDocument}`);
    }
  }

  for (const marker of requiredArchitectureMarkers) {
    const error = marker === 'taskId + generation + leaseOwner + leaseUntil'
      ? 'Assessment Worker fencing must condition final write on taskId + generation + leaseOwner + leaseUntil'
      : `final architecture document missing required decision: ${marker}`;
    requireText(architecture, marker, error, problems);
  }
  for (const rejectedText of ['D4-CROSS-SERVICE', '#279', '学习与成绩服务', 'source-grade event/API']) {
    if (architecture.includes(rejectedText)) {
      problems.push(`final architecture document retains rejected text: ${rejectedText}`);
    }
  }
  requireText(identityDeliveryDocument, 'services/identity', '#311 Identity delivery document must identify the standalone service root', problems);
  requireText(identityDeliveryDocument, 'IDENTITY_JWKS_TRUST_BUNDLE', '#311 Identity delivery document must require JWKS bootstrap for offline verification', problems);
  requireText(identityDeliveryDocument, 'POST /internal/v2/service-tokens', '#311 Identity delivery document must define the v2 service-token operation', problems);

  requireText(
    ownershipDocument,
    '#341 当前 59 张 legacy 数据迁移输入',
    'ownership document must state the 59-table executable gate',
    problems
  );
  requireText(
    ownershipDocument,
    '46 张 legacy 业务表 + 13 张可靠消息运行时表',
    'ownership document must explain 46 legacy tables plus 13 runtime tables',
    problems
  );
  requireText(workloadDocument, mergeShas.issue309, `D7 must identify #309 merge SHA ${mergeShas.issue309}`, problems);
  requireText(workloadDocument, mergeShas.issue338, `D7 must identify #338 merge SHA ${mergeShas.issue338}`, problems);
  requireText(workloadDocument, mergeShas.issue336, `D7 must identify #336 merge SHA ${mergeShas.issue336}`, problems);
  requireText(workloadDocument, identityDeliverySha, `D7 must identify #311 merge SHA ${identityDeliverySha}`, problems);
  requireLocalMarkdownLink(
    rootPath, workloadDocument, 'docs/开发/D7-平台工作负载清单契约.md', 'D6-D7-五服务架构冻结-305.md',
    'D7 workload document must link to the #305 freeze', problems
  );
  requireLocalMarkdownLink(
    rootPath, ownershipDocument, 'docs/开发/D6-DATA-五域数据所有权契约.md', 'D6-D7-五服务架构冻结-305.md',
    'D6 ownership document must link to the #305 freeze', problems
  );
  for (const diagram of [
    'docs/diagrams/arch/issue305-five-service-context.mmd',
    'docs/diagrams/arch/issue305-assessment-worker-fencing.mmd',
    'docs/diagrams/arch/issue305-five-service-deployment.mmd'
  ]) {
    if (!existsSync(resolve(rootPath, diagram))) problems.push(`final architecture diagram source does not exist: ${diagram}`);
  }

  const ownershipTableCount = countCsvRecords(read(rootPath, 'database/ownership/table-ownership.csv', problems));
  const serviceLocalTableCount = countCsvRecords(read(rootPath, 'database/ownership/service-local-tables.csv', problems));
  const crossDomainReferenceCount = countCsvRecords(read(rootPath, 'database/ownership/cross-domain-references.csv', problems));
  const accountCount = countCsvRecords(read(rootPath, 'database/ownership/schema-account-matrix.csv', problems));
  if (ownershipTableCount !== 59) problems.push(`table ownership must contain 59 records, found ${ownershipTableCount}`);
  if (serviceLocalTableCount !== 14) problems.push(`service-local table contract must contain 14 records, found ${serviceLocalTableCount}`);
  if (crossDomainReferenceCount !== 59) problems.push(`cross-domain reference ledger must contain 59 records, found ${crossDomainReferenceCount}`);
  if (accountCount !== 5) problems.push(`schema-account matrix must contain 5 records, found ${accountCount}`);
  const identityRuntimeTables = ownershipSummary?.identityRuntimeTables;
  if (identityRuntimeTables?.outbox !== 't_identity_outbox_event'
    || identityRuntimeTables?.idempotency !== 't_identity_service_token_idempotency') {
    problems.push('final architecture requires the #311 Identity outbox and idempotency mappings');
  }

  const openApiDirectory = resolve(rootPath, 'contracts/v2/openapi');
  const openApiCount = existsSync(openApiDirectory)
    ? readdirSync(openApiDirectory).filter((file) => file.endsWith('.openapi.json')).length
    : 0;
  if (openApiCount !== 5) problems.push(`v2 OpenAPI contract set must contain 5 documents, found ${openApiCount}`);
  const asyncApiText = read(rootPath, 'contracts/v2/asyncapi/events.asyncapi.json', problems);
  let asyncEventCount = 0;
  try {
    asyncEventCount = Object.keys(JSON.parse(asyncApiText).components.messages).length;
    if (asyncEventCount !== 9) problems.push(`v2 AsyncAPI must contain 9 messages, found ${asyncEventCount}`);
  } catch (error) {
    problems.push(`cannot read v2 AsyncAPI messages: ${error.message}`);
  }

  let workloadCount = 0;
  let migrationJobCount = 0;
  try {
    const manifest = JSON.parse(read(rootPath, 'deploy/platform/workloads.json', problems));
    workloadCount = manifest.workloads?.length ?? 0;
    migrationJobCount = manifest.migrationJobs?.length ?? 0;
    if (workloadCount !== 10) problems.push(`workload manifest must contain 10 workloads, found ${workloadCount}`);
    if (migrationJobCount !== 5) problems.push(`workload manifest must contain 5 migration jobs, found ${migrationJobCount}`);
  } catch (error) {
    problems.push(`cannot read workload manifest: ${error.message}`);
  }

  if (problems.length > 0) throw new Error(problems.join('\n'));
  return {
    ownershipTableCount,
    serviceLocalTableCount,
    accountCount,
    crossDomainReferenceCount,
    openApiCount,
    asyncEventCount,
    workloadCount,
    migrationJobCount,
    identityRuntimeTables,
    mergeShas,
    identityDeliverySha
  };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const summary = verifyFinalArchitecture305();
  console.log(
    `final architecture #305 passed: ${summary.ownershipTableCount} tables, ${summary.accountCount} accounts, `
      + `${summary.crossDomainReferenceCount} references, ${summary.openApiCount} OpenAPI, ${summary.asyncEventCount} AsyncAPI, `
      + `${summary.workloadCount} workloads, ${summary.migrationJobCount} migration jobs`
  );
}
