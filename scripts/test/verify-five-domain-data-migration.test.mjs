import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildCopyStatement,
  loadFiveDomainPlan,
  parseTabRows,
} from '../../database/mysql/migrate-five-domain-schemas.mjs';

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
