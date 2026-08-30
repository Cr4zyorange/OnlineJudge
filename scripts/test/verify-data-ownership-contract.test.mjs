import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { cpSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import { verifyDataOwnershipContract } from '../ci/verify-data-ownership-contract.mjs';

const repoRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)));

function fixtureRoot() {
  const root = mkdtempSync(join(tmpdir(), 'onlinejudge-data-ownership-'));
  cpSync(join(repoRoot, 'database'), join(root, 'database'), { recursive: true });
  cpSync(join(repoRoot, 'docs', '开发'), join(root, 'docs', '开发'), { recursive: true });
  return root;
}

test('Issue #309 contract assigns every current business table to exactly one of five schemas', () => {
  const summary = verifyDataOwnershipContract({ rootPath: repoRoot });

  assert.equal(summary.tableCount, 46);
  assert.deepEqual(summary.owners, ['ASSESSMENT', 'COURSE', 'GRADE', 'IDENTITY', 'LEARNING']);
  assert.deepEqual(summary.schemas, ['oj_assessment', 'oj_course', 'oj_grade', 'oj_identity', 'oj_learning']);
  assert.equal(summary.accountCount, 5);
  assert.equal(summary.crossDomainReferenceCount, 11);
  assert.equal(summary.serviceLocalTableCount, 12);
});

test('the executable matrix rejects an account that can access another domain schema', () => {
  const root = fixtureRoot();
  try {
    const matrixPath = join(root, 'database', 'ownership', 'schema-account-matrix.csv');
    const matrix = readFileSync(matrixPath, 'utf8').replace(
      'GRADE,oj_grade,oj_grade_rw,SELECT|INSERT|UPDATE|DELETE,oj_assessment|oj_course|oj_identity|oj_learning,DENY',
      'GRADE,oj_grade,oj_grade_rw,SELECT|INSERT|UPDATE|DELETE,oj_assessment|oj_course|oj_identity,DENY'
    );
    writeFileSync(matrixPath, matrix, 'utf8');

    assert.throws(
      () => verifyDataOwnershipContract({ rootPath: root }),
      /account oj_grade_rw must deny every foreign schema/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('the executable matrix rejects a duplicate owner assignment', () => {
  const root = fixtureRoot();
  try {
    const ownershipPath = join(root, 'database', 'ownership', 'table-ownership.csv');
    const ownership = readFileSync(ownershipPath, 'utf8').replace(
      't_grade_item,GRADE,oj_grade,id',
      't_grade_item,LEARNING,oj_grade,id'
    );
    writeFileSync(ownershipPath, ownership, 'utf8');

    assert.throws(
      () => verifyDataOwnershipContract({ rootPath: root }),
      /schema mismatch for t_grade_item: owner LEARNING must use oj_learning/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('the verifier still executes when CI invokes the /tmp worktree alias', () => {
  const cliPath = join(resolve(repoRoot).replace(/^\/private\/tmp\//, '/tmp/'), 'scripts', 'ci', 'verify-data-ownership-contract.mjs');
  const output = execFileSync(process.execPath, [cliPath], {
    encoding: 'utf8'
  });

  assert.match(output, /data ownership contract passed: 46 tables, 5 accounts/);
});
