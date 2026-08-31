#!/usr/bin/env node

import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const defaultRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)));
const requiredWorkloads = ['gateway', 'identity-service', 'course-service', 'assessment-api', 'assessment-worker', 'grade-service', 'frontend', 'rabbitmq', 'mysql'];
const requiredSchemas = ['identity', 'course', 'assessment', 'grade'];

function read(rootPath, relativePath, problems) {
  const absolute = resolve(rootPath, relativePath);
  if (!existsSync(absolute)) {
    problems.push(`missing ${relativePath}`);
    return '';
  }
  return readFileSync(absolute, 'utf8');
}

export function verifyThreeServiceBaseline306({ rootPath = defaultRoot } = {}) {
  const problems = [];
  const architecture = read(rootPath, 'docs/开发/D6-三服务架构冻结-306.md', problems);
  const shared = read(rootPath, 'docs/开发/D6-三服务共享契约-306.md', problems);
  const ownership = read(rootPath, 'docs/开发/D6-DATA-四域数据所有权契约.md', problems);
  const adr = read(rootPath, 'docs/adr/ADR-006-三业务服务与可靠消息契约.md', problems);
  for (const marker of ['`Course`（CRS+LRN）', '`Assessment`（LAB+HWK，API+Worker）', '`Grade`（GRD）', '`Identity` 只提供身份支撑', 'taskId + generation + leaseOwner + leaseUntil', '/api/v1/learning/**', '/api/v1/notifications/**']) {
    if (!architecture.includes(marker)) problems.push(`architecture missing ${marker}`);
  }
  if (!shared.includes('Assessment 向 Grade 发布') || !shared.includes('Grade 向 Course 发布')) problems.push('shared contract does not bind Grade and Course notification facts');
  if (!ownership.includes('Course 吸收所有 LRN 表与投影')) problems.push('ownership must place LRN in Course');
  if (!adr.includes('三业务服务')) problems.push('ADR must be the three-service decision');
  for (const stale of ['docs/开发/D6-D7-五服务架构冻结-305.md', 'docs/开发/D6-D7-五服务共享契约-v2.md', 'contracts/v2/openapi/learning.openapi.json']) {
    if (existsSync(resolve(rootPath, stale))) problems.push(`stale canonical artifact must be absent: ${stale}`);
  }

  const accounts = read(rootPath, 'database/ownership/schema-account-matrix.csv', problems).trim().split(/\r?\n/).slice(1);
  if (accounts.length !== 4) problems.push(`schema account matrix must contain 4 accounts, found ${accounts.length}`);
  for (const [schema, account] of [['oj_identity', 'oj_identity_rw'], ['oj_course', 'oj_course_rw'], ['oj_assessment', 'oj_assessment_rw'], ['oj_grade', 'oj_grade_rw']]) {
    if (!accounts.some((line) => line.includes(`,${schema},${account},`))) problems.push(`missing schema account ${schema}/${account}`);
  }
  if (accounts.some((line) => line.includes('oj_learning') || line.includes('oj_learning_rw'))) problems.push('schema account matrix must not retain a standalone Learning service');
  const ownershipLedger = read(rootPath, 'database/ownership/table-ownership.csv', problems);
  for (const row of ownershipLedger.trim().split(/\r?\n/).slice(1)) {
    const [table, owner, schema] = row.split(',');
    if ((table.startsWith('lrn_') || table.startsWith('learning_')) && (owner !== 'COURSE' || schema !== 'oj_course')) {
      problems.push(`Course must own LRN table ${table} in oj_course`);
    }
  }

  let manifest;
  try { manifest = JSON.parse(read(rootPath, 'deploy/platform/workloads.json', problems)); } catch (error) { problems.push(`invalid workload manifest: ${error.message}`); }
  const workloadNames = manifest?.workloads?.map((workload) => workload.name) ?? [];
  const migrationSchemas = manifest?.migrationJobs?.map((job) => job.schema) ?? [];
  if (JSON.stringify(workloadNames) !== JSON.stringify(requiredWorkloads)) problems.push(`workloads must be ${requiredWorkloads.join(', ')}`);
  if (JSON.stringify(migrationSchemas) !== JSON.stringify(requiredSchemas)) problems.push(`migration schemas must be ${requiredSchemas.join(' -> ')}`);
  const course = manifest?.workloads?.find((workload) => workload.name === 'course-service');
  if (!course?.sourcePaths?.includes('backend/src/main/java/com/onlinejudge/lrn/**')) problems.push('Course workload must own current LRN source input');

  const openApiDirectory = resolve(rootPath, 'contracts/v2/openapi');
  const openApiContracts = existsSync(openApiDirectory) ? readdirSync(openApiDirectory).filter((name) => name.endsWith('.openapi.json')).length : 0;
  if (openApiContracts !== 4) problems.push(`v2 OpenAPI set must contain 4 contracts, found ${openApiContracts}`);
  if (existsSync(openApiDirectory) && readdirSync(openApiDirectory).some((name) => {
    const contract = readFileSync(resolve(openApiDirectory, name), 'utf8');
    return contract.includes('Learning Service') || contract.includes('"learning"') || contract.includes('five-service architecture');
  })) {
    problems.push('OpenAPI must not retain a standalone Learning service or five-service audience');
  }
  let asyncApiMessages = 0;
  try {
    const asyncApi = JSON.parse(read(rootPath, 'contracts/v2/asyncapi/events.asyncapi.json', problems));
    asyncApiMessages = Object.keys(asyncApi.components.messages).length;
    if (asyncApiMessages !== 10) problems.push(`v2 AsyncAPI must retain 10 typed facts, found ${asyncApiMessages}`);
    if (JSON.stringify(asyncApi.info.title).includes('five-service')) problems.push('AsyncAPI must not retain five-service title');
    if (JSON.stringify(asyncApi.components.messages).includes('"learning"')) problems.push('AsyncAPI must not retain a standalone Learning service');
    for (const [eventName, message] of Object.entries(asyncApi.components.messages)) {
      const consumers = message['x-onlinejudge-consumers'];
      if (!Array.isArray(consumers) || consumers.some((consumer) => !['course', 'assessment', 'grade'].includes(consumer)) || new Set(consumers).size !== consumers.length) {
        problems.push(`AsyncAPI ${eventName} must name each three-service consumer at most once`);
      }
    }
  } catch (error) { problems.push(`invalid AsyncAPI: ${error.message}`); }

  for (const diagram of ['issue306-three-service-context.mmd', 'issue306-assessment-worker-fencing.mmd', 'issue306-three-service-deployment.mmd']) {
    if (!existsSync(resolve(rootPath, 'docs/diagrams/arch', diagram))) problems.push(`missing Mermaid source ${diagram}`);
  }
  if (problems.length) throw new Error(problems.join('\n'));
  return { businessServices: ['course', 'assessment', 'grade'], supportServices: ['identity'], schemaAccounts: 4, openApiContracts, asyncApiMessages, workloads: workloadNames.length, migrationJobs: migrationSchemas.length };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const summary = verifyThreeServiceBaseline306();
  console.log(`three-service baseline #306 passed: ${summary.workloads} workloads, ${summary.migrationJobs} migration jobs, ${summary.schemaAccounts} accounts`);
}
