import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { cpSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

import { verifyDataOwnershipContract } from '../ci/verify-data-ownership-contract.mjs';

const repoRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)));

function fixtureRoot() {
  const root = mkdtempSync(join(tmpdir(), 'onlinejudge-data-ownership-'));
  cpSync(join(repoRoot, 'database'), join(root, 'database'), { recursive: true });
  cpSync(join(repoRoot, 'contracts'), join(root, 'contracts'), { recursive: true });
  cpSync(join(repoRoot, 'docs', '开发'), join(root, 'docs', '开发'), { recursive: true });
  return root;
}

function createSymlinkOrSkip(t, target, linkPath) {
  try {
    symlinkSync(target, linkPath, 'dir');
    return true;
  } catch (error) {
    if (['EPERM', 'EACCES', 'ENOTSUP', 'EINVAL'].includes(error.code)) {
      t.skip(`symbolic links are unavailable on this platform (${error.code})`);
      return false;
    }
    throw error;
  }
}

test('Issue #309 contract assigns every current business table to exactly one of five schemas', () => {
  const summary = verifyDataOwnershipContract({ rootPath: repoRoot });

  assert.equal(summary.tableCount, 57);
  assert.deepEqual(summary.owners, ['ASSESSMENT', 'COURSE', 'GRADE', 'IDENTITY', 'LEARNING']);
  assert.deepEqual(summary.schemas, ['oj_assessment', 'oj_course', 'oj_grade', 'oj_identity', 'oj_learning']);
  assert.equal(summary.accountCount, 5);
  assert.equal(summary.crossDomainReferenceCount, 58);
  assert.equal(summary.expectedReferenceCount, 58);
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

  assert.match(output, /data ownership contract passed: 57 tables, 5 accounts/);
});

test('the ledger rejects a count-preserving replacement of a declared mapping', () => {
  const root = fixtureRoot();
  try {
    const ledgerPath = join(root, 'database', 'ownership', 'cross-domain-references.csv');
    const ledger = readFileSync(ledgerPath, 'utf8').replace(
      'COURSE,crs_course_member,user_id,IDENTITY.t_auth_user.user_id,identity-security-version-event,contracts/v2/asyncapi/events.asyncapi.json#/components/messages/identity.security-version.changed.v2,eventual',
      'COURSE,crs_course_member,approved_by,IDENTITY.t_auth_user.user_id,identity-security-version-event,contracts/v2/asyncapi/events.asyncapi.json#/components/messages/identity.security-version.changed.v2,eventual'
    );
    writeFileSync(ledgerPath, ledger, 'utf8');

    assert.throws(
      () => verifyDataOwnershipContract({ rootPath: root }),
      /missing ledger mapping: COURSE\.crs_course_member\.user_id/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('the ledger rejects removal of a real source-grade mapping', () => {
  const root = fixtureRoot();
  try {
    const ledgerPath = join(root, 'database', 'ownership', 'cross-domain-references.csv');
    const mapping = 'GRADE,t_grade_record,source_id,ASSESSMENT.contracts/v2/openapi/assessment.openapi.json#/paths/~1internal~1v2~1source-grades,assessment-source-grade-api,contracts/v2/openapi/assessment.openapi.json#/paths/~1internal~1v2~1source-grades,eventual\n';
    writeFileSync(ledgerPath, readFileSync(ledgerPath, 'utf8').replace(mapping, ''), 'utf8');

    assert.throws(
      () => verifyDataOwnershipContract({ rootPath: root }),
      /missing ledger mapping: GRADE\.t_grade_record\.source_id/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('the ledger rejects a redirect to a nonexistent v2 contract artifact', () => {
  const root = fixtureRoot();
  try {
    const ledgerPath = join(root, 'database', 'ownership', 'cross-domain-references.csv');
    const ledger = readFileSync(ledgerPath, 'utf8').replace(
      'contracts/v2/openapi/assessment.openapi.json#/paths/~1internal~1v2~1source-grades',
      'contracts/v2/openapi/not-a-real-contract.json#/paths/~1internal~1v2~1source-grades'
    );
    writeFileSync(ledgerPath, ledger, 'utf8');

    assert.throws(
      () => verifyDataOwnershipContract({ rootPath: root }),
      /contract artifact does not exist: contracts\/v2\/openapi\/not-a-real-contract\.json/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('the ledger rejects a column that does not exist in the source table', () => {
  const root = fixtureRoot();
  try {
    const ledgerPath = join(root, 'database', 'ownership', 'cross-domain-references.csv');
    const ledger = readFileSync(ledgerPath, 'utf8').replace(
      'COURSE,crs_course,teacher_id,IDENTITY.t_auth_user.user_id',
      'COURSE,crs_course,teacher_id_not_a_column,IDENTITY.t_auth_user.user_id'
    );
    writeFileSync(ledgerPath, ledger, 'utf8');

    assert.throws(
      () => verifyDataOwnershipContract({ rootPath: root }),
      /source column does not exist: crs_course\.teacher_id_not_a_column/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('the ledger rejects traversal that escapes canonical contracts/v2', () => {
  const root = fixtureRoot();
  try {
    const outsideContractPath = join(root, 'outside.json');
    writeFileSync(outsideContractPath, JSON.stringify({
      components: { messages: { 'identity.security-version.changed.v2': {} } }
    }), 'utf8');
    const ledgerPath = join(root, 'database', 'ownership', 'cross-domain-references.csv');
    const ledger = readFileSync(ledgerPath, 'utf8').replace(
      'contracts/v2/asyncapi/events.asyncapi.json#/components/messages/identity.security-version.changed.v2',
      'contracts/v2/../../outside.json#/components/messages/identity.security-version.changed.v2'
    );
    writeFileSync(ledgerPath, ledger, 'utf8');

    assert.throws(
      () => verifyDataOwnershipContract({ rootPath: root }),
      /contract artifact cannot contain traversal: contracts\/v2\/\.\.\/\.\.\/outside\.json/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('the ledger rejects an equivalent canonical path containing traversal', () => {
  const root = fixtureRoot();
  try {
    const ledgerPath = join(root, 'database', 'ownership', 'cross-domain-references.csv');
    const ledger = readFileSync(ledgerPath, 'utf8').replace(
      'contracts/v2/asyncapi/events.asyncapi.json#/components/messages/identity.security-version.changed.v2',
      'contracts/v2/asyncapi/../asyncapi/events.asyncapi.json#/components/messages/identity.security-version.changed.v2'
    );
    writeFileSync(ledgerPath, ledger, 'utf8');

    assert.throws(
      () => verifyDataOwnershipContract({ rootPath: root }),
      /contract artifact cannot contain traversal: contracts\/v2\/asyncapi\/\.\.\/asyncapi\/events\.asyncapi\.json/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('the ledger rejects an absolute contract artifact', () => {
  const root = fixtureRoot();
  try {
    const outsideContractPath = join(root, 'outside.json');
    writeFileSync(outsideContractPath, JSON.stringify({
      components: { messages: { 'identity.security-version.changed.v2': {} } }
    }), 'utf8');
    const ledgerPath = join(root, 'database', 'ownership', 'cross-domain-references.csv');
    const ledger = readFileSync(ledgerPath, 'utf8').replace(
      'contracts/v2/asyncapi/events.asyncapi.json#/components/messages/identity.security-version.changed.v2',
      `${outsideContractPath}#/components/messages/identity.security-version.changed.v2`
    );
    writeFileSync(ledgerPath, ledger, 'utf8');

    assert.throws(
      () => verifyDataOwnershipContract({ rootPath: root }),
      /contract artifact must be relative: /
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('the ledger rejects a canonical v2 symlink that resolves outside the boundary', (t) => {
  const root = fixtureRoot();
  try {
    const outsideContractPath = join(root, 'outside.json');
    writeFileSync(outsideContractPath, JSON.stringify({
      components: { messages: { 'identity.security-version.changed.v2': {} } }
    }), 'utf8');
    const escapedArtifact = join(root, 'contracts', 'v2', 'asyncapi', 'identity-escape.json');
    try {
      symlinkSync(outsideContractPath, escapedArtifact);
    } catch (error) {
      if (['EPERM', 'EACCES', 'ENOTSUP', 'EINVAL'].includes(error.code)) {
        t.skip(`symbolic links are unavailable on this platform (${error.code})`);
        return;
      }
      throw error;
    }
    const ledgerPath = join(root, 'database', 'ownership', 'cross-domain-references.csv');
    const ledger = readFileSync(ledgerPath, 'utf8').replace(
      'contracts/v2/asyncapi/events.asyncapi.json#/components/messages/identity.security-version.changed.v2',
      'contracts/v2/asyncapi/identity-escape.json#/components/messages/identity.security-version.changed.v2'
    );
    writeFileSync(ledgerPath, ledger, 'utf8');

    assert.throws(
      () => verifyDataOwnershipContract({ rootPath: root }),
      /contract artifact escapes canonical contracts\/v2: contracts\/v2\/asyncapi\/identity-escape\.json/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('the ledger rejects a contracts/v2 root directory symlink to external content', (t) => {
  const root = fixtureRoot();
  try {
    const canonicalV2 = join(root, 'contracts', 'v2');
    const externalV2 = join(root, 'external-v2');
    cpSync(canonicalV2, externalV2, { recursive: true });
    rmSync(canonicalV2, { recursive: true, force: true });
    if (!createSymlinkOrSkip(t, externalV2, canonicalV2)) return;

    assert.throws(
      () => verifyDataOwnershipContract({ rootPath: root }),
      /canonical contracts\/v2 root must not be a symlink: contracts\/v2/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('the ledger rejects a symlinked contracts ancestor of the canonical root', (t) => {
  const root = fixtureRoot();
  try {
    const canonicalContracts = join(root, 'contracts');
    const externalContracts = join(root, 'external-contracts');
    cpSync(canonicalContracts, externalContracts, { recursive: true });
    rmSync(canonicalContracts, { recursive: true, force: true });
    if (!createSymlinkOrSkip(t, externalContracts, canonicalContracts)) return;

    assert.throws(
      () => verifyDataOwnershipContract({ rootPath: root }),
      /canonical contracts\/v2 ancestor must not be a symlink: contracts/
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
