import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import test from 'node:test';

import {
  buildCopyStatement,
  loadFiveDomainPlan,
  parseTabRows,
} from '../../database/mysql/migrate-five-domain-schemas.mjs';

const migrationScript = resolve('database/mysql/migrate-five-domain-schemas.mjs');
const liveAcceptanceScript = resolve('database/tests/verify-five-domain-migration.sh');

test('five-domain plan retains all 46 canonical business tables exactly once', () => {
  const plan = loadFiveDomainPlan();

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
  assert.match(source, /permissions\.length !== requiredOwners\.length \* 9/);
  assert.match(source, /permission evidence must contain 45 complete allow\/deny probes/);
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

test('disposable MySQL acceptance keeps bypass, missing-password, nested-evidence, and repeat-DDL negatives', () => {
  const source = readFileSync(liveAcceptanceScript, 'utf8');

  assert.match(source, /skip-permissions-bypass\.json/);
  assert.match(source, /no-runtime-passwords\.json/);
  assert.match(source, /nested\/evidence\/verify\.json/);
  assert.match(source, /ddl-misconfigured-first\.json/);
  assert.match(source, /ddl-misconfigured-second\.json/);
  assert.match(source, /negative\/invalid-v2-replay\.json/);
  assert.match(source, /malformed v2 replay payload unexpectedly passed verification/);
});
