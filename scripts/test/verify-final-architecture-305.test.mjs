import assert from 'node:assert/strict';
import { cpSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import { verifyFinalArchitecture305 } from '../ci/verify-final-architecture-305.mjs';

const repoRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)));

function fixtureRoot() {
  const root = mkdtempSync(join(tmpdir(), 'onlinejudge-final-architecture-305-'));
  for (const relativePath of [
    'contracts',
    'database/ownership',
    'database/mysql/compose-schema.sql',
    'database/migrations/identity',
    'deploy/platform',
    'docs/diagrams/arch/issue305-five-service-context.mmd',
    'docs/diagrams/arch/issue305-assessment-worker-fencing.mmd',
    'docs/diagrams/arch/issue305-five-service-deployment.mmd',
    'docs/adr/ADR-006-五业务服务与可靠消息契约.md',
    'docs/开发/D6-AUTH-独立身份服务交付.md',
    'docs/开发/D6-D7-五服务共享契约-v2.md',
    'docs/开发/D6-D7-五服务架构冻结-305.md',
    'docs/开发/D6-DATA-五域数据所有权契约.md',
    'docs/开发/D7-平台工作负载清单契约.md'
  ]) {
    const source = join(repoRoot, relativePath);
    const destination = join(root, relativePath);
    cpSync(source, destination, { recursive: true });
  }
  return root;
}

test('Issue #305 freezes the current five-service boundary against the merged contract inputs', () => {
  const summary = verifyFinalArchitecture305({ rootPath: repoRoot });

  assert.deepEqual(summary, {
    ownershipTableCount: 59,
    serviceLocalTableCount: 14,
    accountCount: 5,
    crossDomainReferenceCount: 59,
    openApiCount: 5,
    asyncEventCount: 10,
    workloadCount: 10,
    migrationJobCount: 5,
    identityRuntimeTables: {
      outbox: 't_identity_outbox_event',
      idempotency: 't_identity_service_token_idempotency'
    },
    mergeShas: {
      issue309: '50bdc8490b204c868aafe7b7f2602ce6826c84ad',
      issue338: 'dd89083a0a8c6a04512e48d6f9ccab0f62f8897f',
      issue336: '2a0ce94262596820eefe905bcd3c301c474880cf'
    },
    identityDeliverySha: 'd9f3d74abd2d64b81632956832107bec7b0983be'
  });
});

test('the freeze binds the delivered Identity runtime tables without inventing an event inbox', () => {
  const root = fixtureRoot();
  try {
    const serviceLocalPath = join(root, 'database/ownership/service-local-tables.csv');
    const serviceLocal = readFileSync(serviceLocalPath, 'utf8').replace(
      'IDENTITY,oj_identity,t_identity_service_token_idempotency,IDEMPOTENCY,implemented,#311',
      'IDENTITY,oj_identity,event_inbox,INBOX,planned,#337'
    );
    writeFileSync(serviceLocalPath, serviceLocal, 'utf8');

    assert.throws(
      () => verifyFinalArchitecture305({ rootPath: root }),
      /Identity must record its delivered idempotency table instead of a planned event inbox/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('the freeze rejects a worker design that omits a generation-fenced final write', () => {
  const root = fixtureRoot();
  try {
    const architecturePath = join(root, 'docs/开发/D6-D7-五服务架构冻结-305.md');
    const architecture = readFileSync(architecturePath, 'utf8').replaceAll(
      'taskId + generation + leaseOwner + leaseUntil',
      'taskId + leaseOwner + leaseUntil'
    );
    writeFileSync(architecturePath, architecture, 'utf8');

    assert.throws(
      () => verifyFinalArchitecture305({ rootPath: root }),
      /Assessment Worker fencing must condition final write on taskId \+ generation \+ leaseOwner \+ leaseUntil/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('the freeze rejects an ownership gate that drifts back to the stale 58-table claim', () => {
  const root = fixtureRoot();
  try {
    const ownershipPath = join(root, 'docs/开发/D6-DATA-五域数据所有权契约.md');
    const ownership = readFileSync(ownershipPath, 'utf8').replace(
      '#341 当前 59 张 legacy 数据迁移输入',
      '#341 当前 58 张 legacy 数据迁移输入'
    );
    writeFileSync(ownershipPath, ownership, 'utf8');

    assert.throws(
      () => verifyFinalArchitecture305({ rootPath: root }),
      /ownership document must state the 59-table executable gate/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('the freeze rejects D7 evidence that points to the superseded #309 or draft #338 input', () => {
  const root = fixtureRoot();
  try {
    const workloadPath = join(root, 'docs/开发/D7-平台工作负载清单契约.md');
    const workload = readFileSync(workloadPath, 'utf8').replace(
      'dd89083a0a8c6a04512e48d6f9ccab0f62f8897f',
      '76528efedafbdd356c8570ff044e3ffc75fe0645'
    );
    writeFileSync(workloadPath, workload, 'utf8');

    assert.throws(
      () => verifyFinalArchitecture305({ rootPath: root }),
      /D7 must identify #338 merge SHA dd89083a0a8c6a04512e48d6f9ccab0f62f8897f/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('the freeze rejects reintroducing a deleted architecture document', () => {
  const root = fixtureRoot();
  try {
    const v1Path = join(root, 'docs/开发/D4-CROSS-SERVICE-共享契约.md');
    writeFileSync(v1Path, '# rejected architecture\n', 'utf8');

    assert.throws(
      () => verifyFinalArchitecture305({ rootPath: root }),
      /rejected architecture document must be deleted: docs\/开发\/D4-CROSS-SERVICE-共享契约\.md/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
