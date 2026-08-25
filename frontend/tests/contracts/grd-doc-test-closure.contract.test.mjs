import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import test from 'node:test';

const repositoryRoot = resolve(import.meta.dirname, '../../..');

const sourceAssets = [
  'docs/diagrams/srs/fig_4_20_grade_item_config_ssd.mmd',
  'docs/diagrams/srs/fig_4_21_student_grade_query_ssd.mmd',
  'docs/diagrams/srs/fig_4_22_grade_review_ssd.mmd',
  'docs/diagrams/srs/fig_4_23_grade_analysis_ssd.mmd',
  'docs/diagrams/grd/fig_3_6_11_student_grade_query_sequence.mmd',
  'docs/diagrams/grd/fig_3_6_12_grade_review_sequence.mmd',
  'docs/diagrams/grd/fig_3_6_13_grade_analysis_sequence.mmd',
  'docs/diagrams/grd/fig_3_6_14_grade_analysis_activity.mmd',
  'docs/diagrams/grd/fig_3_6_15_grade_analysis_state.mmd'
];

const renderedAssets = sourceAssets.map((source) => {
  const name = source.slice(source.lastIndexOf('/') + 1).replace(/\.mmd$/, '.svg');
  return `docs/最终提交/assets/${name}`;
});

function readRepositoryFile(path) {
  return readFileSync(resolve(repositoryRoot, path), 'utf8');
}

test('GRD independent scenarios keep renderable sources and committed SVG assets', () => {
  for (const path of [...sourceAssets, ...renderedAssets]) {
    assert.equal(existsSync(resolve(repositoryRoot, path)), true, `missing ${path}`);
  }

  for (const path of sourceAssets) {
    const source = readRepositoryFile(path);
    assert.match(source, /^(?:---[\s\S]*?---\s*)?(?:sequenceDiagram|flowchart|stateDiagram-v2)/);
  }
});

test('GRD requirement and detailed design chapters reference the split scenario diagrams', () => {
  const srs = readRepositoryFile('docs/最终提交/软件需求规格说明书.md');
  const processOverview = readRepositoryFile('docs/过程/概要/成绩评价与教学分析模块概要设计提交稿（grd）.md');
  const processDetail = readRepositoryFile('docs/过程/详细设计/GRD-成绩评价与教学分析-详细设计提交稿.md');
  const finalDetail = readRepositoryFile('docs/最终提交/软件详细设计说明书.md');

  for (const figure of ['fig_4_20_grade_item_config_ssd.svg', 'fig_4_21_student_grade_query_ssd.svg',
    'fig_4_22_grade_review_ssd.svg', 'fig_4_23_grade_analysis_ssd.svg']) {
    assert.match(srs, new RegExp(figure.replaceAll('.', '\\.')));
  }

  assert.match(processOverview, /GRD 业务场景与三层图组索引/);
  for (const heading of ['学生成绩查询对象级顺序图', '成绩异议复核对象级顺序图',
    '教学分析对象级顺序图', '教学分析活动图', '教学分析状态图']) {
    assert.match(processDetail, new RegExp(heading));
    assert.match(finalDetail, new RegExp(heading));
  }
});

test('GRD uses the shared E2E runner for main, alternative, and exception paths', () => {
  const specPath = 'frontend/tests/e2e/grd/grade-lifecycle.spec.ts';
  assert.equal(existsSync(resolve(repositoryRoot, specPath)), true, `missing ${specPath}`);

  const spec = readRepositoryFile(specPath);
  assert.match(spec, /@grd-main/);
  assert.match(spec, /@grd-alternative/);
  assert.match(spec, /@grd-exception/);
  assert.match(spec, /\/api\/v1\/labs\/\$\{labId\}\/submissions/);
  assert.match(spec, /\/api\/v1\/homeworks\/\$\{homeworkId\}\/submissions/);
  assert.match(spec, /\/api\/v1\/courses\/\$\{courseId\}\/grades\/sync/);
  assert.match(spec, /\/api\/v1\/courses\/\$\{courseId\}\/grades\/publish/);
  assert.match(spec, /\/api\/v1\/courses\/\$\{courseId\}\/my-grades/);
  assert.match(spec, /\/api\/v1\/courses\/\$\{courseId\}\/grade-review-requests/);
  assert.match(spec, /\/api\/v1\/notifications/);
});
