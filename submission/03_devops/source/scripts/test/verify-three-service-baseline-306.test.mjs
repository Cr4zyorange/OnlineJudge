import assert from 'node:assert/strict';
import { cpSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import { verifyThreeServiceBaseline306 } from '../ci/verify-three-service-baseline-306.mjs';

const repoRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)));

function fixtureRoot() {
  const root = mkdtempSync(join(tmpdir(), 'onlinejudge-three-service-baseline-306-'));
  for (const relativePath of [
    'contracts/v2',
    'database/migrations',
    'database/mysql',
    'database/ownership',
    'deploy/platform',
    'docs/adr/ADR-006-三业务服务与可靠消息契约.md',
    'docs/diagrams/arch/issue306-three-service-context.mmd',
    'docs/diagrams/arch/issue306-assessment-worker-fencing.mmd',
    'docs/diagrams/arch/issue306-three-service-deployment.mmd',
    'docs/开发/D6-三服务架构冻结-306.md',
    'docs/开发/D6-三服务共享契约-306.md',
    'docs/开发/D6-DATA-四域数据所有权契约.md',
    'docs/开发/D7-平台工作负载清单契约.md'
  ]) {
    cpSync(join(repoRoot, relativePath), join(root, relativePath), { recursive: true });
  }
  return root;
}

test('Issue #306 freezes three business services, Identity support, four schema accounts and 9/4 delivery inputs', () => {
  assert.deepEqual(verifyThreeServiceBaseline306({ rootPath: repoRoot }), {
    businessServices: ['course', 'assessment', 'grade'],
    supportServices: ['identity'],
    schemaAccounts: 4,
    openApiContracts: 4,
    asyncApiMessages: 10,
    workloads: 9,
    migrationJobs: 4
  });
});

test('Issue #306 rejects a stale Learning service contract from the canonical v2 set', () => {
  const root = fixtureRoot();
  try {
    const contractPath = join(root, 'contracts/v2/openapi/course.openapi.json');
    const contract = readFileSync(contractPath, 'utf8').replace(
      '"title": "OnlineJudge Course Service internal contract"',
      '"title": "OnlineJudge Learning Service internal contract"'
    );
    writeFileSync(contractPath, contract, 'utf8');

    assert.throws(
      () => verifyThreeServiceBaseline306({ rootPath: root }),
      /must not retain a standalone Learning service or five-service audience/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('Issue #306 rejects Identity minting a token for a retired Learning audience', () => {
  const root = fixtureRoot();
  try {
    const contractPath = join(root, 'contracts/v2/openapi/identity.openapi.json');
    const contract = readFileSync(contractPath, 'utf8').replace(
      '"enum": ["course", "assessment", "grade"]',
      '"enum": ["course", "assessment", "grade", "learning"]'
    );
    writeFileSync(contractPath, contract, 'utf8');

    assert.throws(
      () => verifyThreeServiceBaseline306({ rootPath: root }),
      /must not retain a standalone Learning service or five-service audience/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('Issue #306 rejects assigning an LRN table to a standalone owner', () => {
  const root = fixtureRoot();
  try {
    const ledgerPath = join(root, 'database/ownership/table-ownership.csv');
    const ledger = readFileSync(ledgerPath, 'utf8').replace(
      'lrn_learning_task,COURSE,oj_course',
      'lrn_learning_task,LEARNING,oj_learning'
    );
    writeFileSync(ledgerPath, ledger, 'utf8');

    assert.throws(
      () => verifyThreeServiceBaseline306({ rootPath: root }),
      /Course must own LRN table lrn_learning_task in oj_course/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('Issue #306 rejects duplicate or retired AsyncAPI consumers', () => {
  const root = fixtureRoot();
  try {
    const contractPath = join(root, 'contracts/v2/asyncapi/events.asyncapi.json');
    const contract = readFileSync(contractPath, 'utf8').replace(
      '"x-onlinejudge-consumers": ["course", "assessment", "grade"]',
      '"x-onlinejudge-consumers": ["course", "assessment", "grade", "course"]'
    );
    writeFileSync(contractPath, contract, 'utf8');

    assert.throws(
      () => verifyThreeServiceBaseline306({ rootPath: root }),
      /AsyncAPI identity\.security-version\.changed\.v2 must name each three-service consumer at most once/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('Issue #306 rejects a Course migration that omits an owned LRN runtime table', () => {
  const root = fixtureRoot();
  try {
    const migrationPath = join(root, 'database/migrations/course/V20260901_07__course_lrn_owned_tables.sql');
    const migration = readFileSync(migrationPath, 'utf8').replace(
      'CREATE TABLE IF NOT EXISTS learning_event_dead_letter',
      '-- missing learning_event_dead_letter'
    );
    writeFileSync(migrationPath, migration, 'utf8');

    assert.throws(
      () => verifyThreeServiceBaseline306({ rootPath: root }),
      /Course migration must create LRN runtime table learning_event_dead_letter/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('Issue #306 rejects a migration runner that accepts the retired Learning schema', () => {
  const root = fixtureRoot();
  try {
    const runnerPath = join(root, 'database/mysql/migrate-service.sh');
    const runner = readFileSync(runnerPath, 'utf8').replace(
      'identity|course|assessment|grade)',
      'identity|course|assessment|grade|learning)'
    );
    writeFileSync(runnerPath, runner, 'utf8');

    assert.throws(
      () => verifyThreeServiceBaseline306({ rootPath: root }),
      /migration runner must reject the retired Learning schema/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
