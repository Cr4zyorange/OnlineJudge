import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { tmpdir } from 'node:os';
import test from 'node:test';

import {
  buildCopyStatement,
  loadFiveDomainPlan,
  parseTabRows,
  writeControlState,
} from '../../database/mysql/migrate-five-domain-schemas.mjs';

const migrationScript = resolve('database/mysql/migrate-five-domain-schemas.mjs');
const liveAcceptanceScript = resolve('database/tests/verify-five-domain-migration.sh');

test('five-domain plan retains all 46 canonical business tables exactly once', () => {
  const plan = loadFiveDomainPlan();

  assert.equal(plan.allTables.length, 59);
  assert.equal(plan.runtimeTables.length, 13);
  assert.equal(plan.tables.length, 46);
  assert.deepEqual(
    [...plan.schemaByOwner.entries()],
    [
      ['IDENTITY', 'oj_identity'],
      ['COURSE', 'oj_course'],
      ['ASSESSMENT', 'oj_assessment'],
      ['GRADE', 'oj_grade'],
      ['LEARNING', 'oj_learning'],
    ],
  );
  assert.equal(new Set(plan.tables.map((entry) => entry.table)).size, 46);
  assert.deepEqual(
    plan.runtimeTables.map((entry) => entry.table).sort(),
    [
      'assessment_event_inbox',
      'assessment_event_outbox',
      'course_event_outbox',
      'course_membership_reconciliation_checkpoint',
      'grade_event_inbox',
      'grade_event_outbox',
      'learning_course_member_projection',
      'learning_course_membership_watermark',
      'learning_deferred_event',
      'learning_event_dead_letter',
      'learning_event_delivery_attempt',
      'learning_event_inbox',
      'learning_event_reconciliation_request',
    ],
  );
});

test('copy SQL is idempotent and never needs a cross-schema foreign key', () => {
  const plan = loadFiveDomainPlan();
  const course = plan.tables.find((entry) => entry.table === 'crs_course');
  const sql = buildCopyStatement({
    sourceSchema: 'legacy_oj',
    targetSchema: plan.schemaByOwner.get(course.owner),
    table: course.table,
    columns: ['id', 'course_name', 'teacher_id'],
  });

  assert.match(sql, /ON DUPLICATE KEY UPDATE/);
  assert.match(sql, /`legacy_oj`\.`crs_course`/);
  assert.match(sql, /`oj_course`\.`crs_course`/);
  assert.doesNotMatch(sql, /FOREIGN KEY|REFERENCES/i);
});

test('raw mysql rows preserve tabular database errors for migration evidence', () => {
  assert.deepEqual(parseTabRows('a\tb\n1\t2\n'), [['a', 'b'], ['1', '2']]);
});

test('production migration CLI hard-rejects the permission bypass flag before any migration work', () => {
  const result = spawnSync(process.execPath, [migrationScript, '--skip-permissions'], { encoding: 'utf8' });

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /--skip-permissions is not supported/);
});

test('migration implementation creates nested evidence parents and validates all 45 account probes', () => {
  const source = readFileSync(migrationScript, 'utf8');

  assert.match(source, /mkdirSync\(dirname\(absolute\), \{ recursive: true \}\)/);
  assert.match(source, /export function writeControlState\(path, state\)/);
  assert.match(source, /renameSync\(temporary, absolute\)/);
  assert.equal((source.match(/writeControlState\(options\.cutoverState, state\)/g) ?? []).length, 2);
  assert.match(source, /CREATE TABLE IF NOT EXISTS .* LIKE .*entry\.table/);
  assert.match(source, /assessment_event_outbox/);
  assert.match(source, /course\.membership\.snapshot\.v2/);
  assert.doesNotMatch(source, /learning_course_projection/);
  assert.match(source, /migration_checkpoints \(migration_id, phase, source_schema, source_fingerprint\)/);
  assert.match(source, /permissions\.length !== requiredOwners\.length \* 9/);
  assert.match(source, /permission evidence must contain 45 complete allow\/deny probes/);
});

test('cutover control state creates a fresh nested parent before an atomic write', () => {
  const root = mkdtempSync(join(tmpdir(), 'oj341-control-state-'));
  const statePath = join(root, 'ci-artifacts', 'issue341', 'cutover-state.json');
  try {
    assert.equal(existsSync(statePath), false);
    writeControlState(statePath, { topology: 'FIVE_DOMAIN' });
    assert.deepEqual(JSON.parse(readFileSync(statePath, 'utf8')), { topology: 'FIVE_DOMAIN' });
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rollback cannot bypass the shared runtime-password and 45-probe PASS gate', () => {
  const source = readFileSync(migrationScript, 'utf8');
  const passwordGate = source.indexOf('const passwords = requiredRuntimePasswords(plan, options);');
  const rollbackPath = source.indexOf("if (options.action === 'rollback')");

  assert.ok(passwordGate >= 0 && passwordGate < rollbackPath);
  assert.match(source, /if \(!verification\.passed\) \{\n\s+writeEvidence\(options\.evidence, \{\n\s+\.\.\.base, result: 'FAIL', verification/);
  assert.match(source, /result: 'PASS', verification, rollback: state/);
});

test('migration replay serializes source-grade and member facts as valid v2 payloads', () => {
  const source = readFileSync(migrationScript, 'utf8');

  assert.match(source, /JSON_OBJECT\('courseId', CAST\(r\.course_id AS CHAR\)/);
  assert.match(source, /'score', CASE WHEN r\.grade_status IN \('SCORED', 'ADJUSTED'\) AND r\.raw_score IS NOT NULL THEN r\.raw_score ELSE NULL END/);
  assert.match(source, /'status', CASE WHEN r\.grade_status IN \('SCORED', 'ADJUSTED'\) AND r\.raw_score IS NOT NULL THEN 'SCORED' ELSE 'UNGRADED' END/);
  assert.match(source, /JSON_OBJECT\('courseId', CAST\(m\.course_id AS CHAR\), 'userId', CAST\(m\.user_id AS CHAR\)/);
  assert.match(source, /'membershipStatus', CASE WHEN m\.join_status = 'ACTIVE' THEN 'ACTIVE' ELSE 'REMOVED' END/);
});

test('migration verification rejects any replayed inbox payload that violates the v2 schema', () => {
  const source = readFileSync(migrationScript, 'utf8');

  assert.match(source, /invalidPayloads/);
  assert.match(source, /grade source replay payload contract mismatch/);
  assert.match(source, /learning member replay payload contract mismatch/);
});

test('DDL denial probes are repeat-safe and only accept a MySQL authorization denial', () => {
  const source = readFileSync(migrationScript, 'utf8');

  assert.match(source, /const ddlProbeTable = `__issue341_ddl_probe_\$\{owner\.toLowerCase\(\)\}_\$\{Date\.now\(\)\}`/);
  assert.match(source, /DROP TABLE IF EXISTS \$\{quoteIdentifier\(account\.schema\)\}\.\$\{quoteIdentifier\(ddlProbeTable\)\}/);
  assert.match(source, /ddl\.code !== 0 && /);
  assert.match(source, /ERROR 1142|command denied/);
});

test('disposable MySQL acceptance keeps bypass, missing-password, rollback, nested-evidence, and repeat-DDL negatives', () => {
  const source = readFileSync(liveAcceptanceScript, 'utf8');

  assert.match(source, /skip-permissions-bypass\.json/);
  assert.match(source, /no-runtime-passwords\.json/);
  assert.match(source, /rollback-no-runtime-passwords\.json/);
  assert.match(source, /verification\.permissions\.length !== 45/);
  assert.match(source, /oj_assessment\.assessment_event_outbox/);
  assert.match(source, /oj_course\.course_event_outbox/);
  assert.match(source, /nested\/evidence\/verify\.json/);
  assert.match(source, /ci-artifacts\/issue341\/cutover-state\.json/);
  assert.match(source, /fresh-rollback\/issue341\/rollback-state\.json/);
  assert.match(source, /fresh nested cutover state path was not created/);
  assert.match(source, /ddl-misconfigured-first\.json/);
  assert.match(source, /ddl-misconfigured-second\.json/);
  assert.match(source, /negative\/invalid-v2-replay\.json/);
  assert.match(source, /malformed v2 replay payload unexpectedly passed verification/);
});
