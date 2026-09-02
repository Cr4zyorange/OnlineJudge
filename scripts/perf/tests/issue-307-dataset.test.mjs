import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { fileURLToPath } from "node:url";
import test from "node:test";
import { promisify } from "node:util";

import {
  benchmarkFacts,
  renderDatasetSql,
  verificationSummary,
} from "../issue-307-dataset.mjs";

const execFileAsync = promisify(execFile);

test("frozen dataset contains 100 students, 105 courses, and three published grade items", () => {
  const facts = benchmarkFacts();

  assert.equal(facts.users.students.length, 100);
  assert.equal(facts.courses.length, 105);
  assert.equal(facts.members.length, 101);
  assert.equal(facts.gradeItems.length, 3);
  assert.equal(facts.gradeRecords.length, 300);
  assert.equal(facts.gradeSummaries.length, 100);
  assert.equal(facts.homework.id, 3_072_001);
});

for (const architecture of ["monolith", "three-service"]) {
  test(`${architecture} SQL is deterministic, scoped, and resettable`, () => {
    const load = renderDatasetSql({ architecture, phase: "load" });
    const reset = renderDatasetSql({ architecture, phase: "reset" });
    const verify = renderDatasetSql({ architecture, phase: "verify" });

    assert.match(load, /issue-307-v1/);
    assert.match(load, /3072001/);
    assert.match(reset, /3072001/);
    assert.match(verify, /users=101 courses=105 members=101 homeworks=1 summaries=100 grade-records=300 submissions=0/);
    assert.doesNotMatch(`${load}\n${reset}`, /DROP\s+(DATABASE|SCHEMA)/i);
    assert.equal(load, renderDatasetSql({ architecture, phase: "load" }));
  });
}

test("dataset load, reset, and verification isolate every API-visible course", () => {
  for (const architecture of ["monolith", "three-service"]) {
    const load = renderDatasetSql({ architecture, phase: "load" });
    const reset = renderDatasetSql({ architecture, phase: "reset" });
    const verify = renderDatasetSql({ architecture, phase: "verify" });

    assert.match(load, /DELETE FROM crs_course;/);
    assert.match(load, /DELETE FROM crs_course_member;/);
    assert.match(reset, /DELETE FROM crs_course;/);
    assert.match(reset, /DELETE FROM crs_course_member;/);
    assert.match(verify, /COUNT\(\*\) FROM (?:oj_course\.)?crs_course WHERE is_deleted=FALSE/);
    assert.doesNotMatch(verify, /description='issue-307-v1'/);
  }
});

test("verification summary is stable and machine-parseable", () => {
  assert.equal(
    verificationSummary(),
    "users=101 courses=105 members=101 homeworks=1 summaries=100 grade-records=300 submissions=0",
  );
});

test("monolith identities retain the seed password salt so the benchmark student can log in", () => {
  const load = renderDatasetSql({ architecture: "monolith", phase: "load" });
  assert.match(load, /password_salt/);
  assert.match(load, /password_salt=VALUES\(password_salt\)/);
});

test("published grade records use the executable SCORED lifecycle value", () => {
  const load = renderDatasetSql({ architecture: "monolith", phase: "load" });
  const gradeRecordSql = load.slice(
    load.indexOf("INSERT INTO t_grade_record"),
    load.indexOf("INSERT INTO t_course_grade_summary"),
  );
  assert.match(gradeRecordSql, /'SCORED', 'PUBLISHED'/);
  assert.doesNotMatch(gradeRecordSql, /'CALCULATED', 'PUBLISHED'/);
  assert.match(gradeRecordSql, /source_type=VALUES\(source_type\)/);
});

test("grade records only use source types accepted by the grade service", () => {
  const facts = benchmarkFacts();
  assert.deepEqual(
    facts.gradeItems.map((item) => item.sourceType),
    ["LAB", "HWK", "OTHER_COURSE_ITEM"],
  );
});

test("dataset CLI emits SQL when invoked through a relative path", async () => {
  const { stdout } = await execFileAsync(process.execPath, [
    "scripts/perf/issue-307-dataset.mjs",
    "--architecture",
    "monolith",
    "--phase",
    "verify",
  ]);
  assert.match(stdout, /users=101 courses=105/);
});

test("dataset CLI emits SQL when invoked through the macOS /tmp alias", async (t) => {
  const script = fileURLToPath(new URL("../issue-307-dataset.mjs", import.meta.url));
  const tmpAlias = script.replace(/^\/private\/tmp\//, "/tmp/");
  if (tmpAlias === script) {
    t.skip("this checkout is not under the macOS /private/tmp alias");
    return;
  }
  const { stdout } = await execFileAsync(process.execPath, [
    tmpAlias,
    "--architecture",
    "monolith",
    "--phase",
    "verify",
  ]);
  assert.match(stdout, /users=101 courses=105/);
});
