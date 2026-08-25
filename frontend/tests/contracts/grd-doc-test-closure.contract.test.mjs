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

test('GRD SSD branches and responses match the implemented API contracts', () => {
  const gradeItemSsd = readRepositoryFile('docs/diagrams/srs/fig_4_20_grade_item_config_ssd.mmd');
  const gradeReviewSsd = readRepositoryFile('docs/diagrams/srs/fig_4_22_grade_review_ssd.mmd');
  const gradeFlowSsd = readRepositoryFile('docs/diagrams/srs/fig_4_15_grade_flow_ssd.mmd');
  const studentGradeSequence = readRepositoryFile('docs/diagrams/grd/fig_3_6_11_student_grade_query_sequence.mmd');
  const gradeReviewSequence = readRepositoryFile('docs/diagrams/grd/fig_3_6_12_grade_review_sequence.mmd');
  const gradeAnalysisSequence = readRepositoryFile('docs/diagrams/grd/fig_3_6_13_grade_analysis_sequence.mmd');
  const srs = readRepositoryFile('docs/最终提交/软件需求规格说明书.md');
  const detailedDesign = readRepositoryFile('docs/最终提交/软件详细设计说明书.md');
  const processDetailedDesign = readRepositoryFile('docs/过程/详细设计/GRD-成绩评价与教学分析-详细设计提交稿.md');
  const processOverview = readRepositoryFile('docs/过程/概要/成绩评价与教学分析模块概要设计提交稿（grd）.md');
  const gradeTestReport = readRepositoryFile('docs/过程/测试/TST-DOC-07 GRD 成绩评价与教学分析测试文档.md');
  const gradeItemUseCase = srs.slice(
    srs.indexOf('## 4.11 UC-GR-02'),
    srs.indexOf('## 4.12 UC-GR-03')
  );

  assert.doesNotMatch(gradeItemSsd, /来源任务存在|来源不存在/);
  assert.match(gradeItemSsd, /LAB\/HWK.*来源编号.*正整数/);
  assert.doesNotMatch(gradeItemUseCase, /来源存在性|来源不存在/);
  assert.match(gradeItemUseCase, /LAB\/HWK.*来源编号.*正整数/);
  assert.match(gradeItemUseCase, /OTHER_COURSE_ITEM.*来源编号.*可为空/);
  assert.doesNotMatch(srs, /\| OP-GR-01 \|[^\n]*来源不存在/);
  assert.doesNotMatch(detailedDesign, /校验来源类型和来源任务/);
  assert.doesNotMatch(processDetailedDesign, /校验来源类型和来源任务/);
  assert.match(detailedDesign, /校验来源类型和编号格式/);
  assert.match(processDetailedDesign, /校验来源类型和编号格式/);
  for (const document of [gradeItemUseCase, processOverview, gradeTestReport]) {
    assert.doesNotMatch(document, /来源(?:任务)?可用性.{0,12}(?:同步阶段|同步时|同步测试)/);
  }
  assert.match(gradeItemUseCase, /不存在或跨课程.{0,30}未发布或暂无成绩.{0,30}MISSING/);
  assert.match(gradeTestReport, /不存在或跨课程.{0,30}未发布或暂无成绩.{0,30}MISSING/);

  assert.doesNotMatch(gradeReviewSsd, /教师通知状态/);
  assert.match(gradeReviewSsd, /requestId.*PENDING.*submittedAt/);

  const syncOperation = gradeFlowSsd.indexOf('OP-GR-02');
  const sourceFailure = gradeFlowSsd.indexOf('来源同步失败');
  const publishOperation = gradeFlowSsd.indexOf('OP-GR-03');
  assert.ok(syncOperation >= 0 && sourceFailure > syncOperation && publishOperation > sourceFailure);

  assert.doesNotMatch(gradeReviewSequence, /GradeRecordService/);
  assert.match(gradeReviewSequence, /GradeRecord(?:<br\/>)?Repository/);
  assert.match(gradeReviewSequence, /CourseGradeSummary(?:<br\/>)?Repository/);
  assert.match(gradeReviewSequence, /GradeChangeLog(?:<br\/>)?Repository/);
  assert.match(gradeReviewSequence, /applyApprovedAdjustment/);
  assert.doesNotMatch(gradeReviewSequence, /校验课程成员与本人已发布成绩/);
  assert.match(gradeReviewSequence, /isCourseMember/);
  assert.match(gradeReviewSequence, /originalPublishedScore/);
  const processRequestLookup = gradeReviewSequence.indexOf('findById(requestId)');
  const processPermissionCheck = gradeReviewSequence.indexOf('canManageCourseGrade');
  const processPermissionException = gradeReviewSequence.indexOf('GradeReviewPermissionException', processPermissionCheck);
  const processApproval = gradeReviewSequence.indexOf('alt APPROVE');
  assert.ok(processRequestLookup >= 0 && processPermissionCheck > processRequestLookup);
  assert.ok(processPermissionException > processPermissionCheck && processApproval > processPermissionException);
  assert.match(gradeReviewSequence, /GradeItemNotFoundException/);

  assert.doesNotMatch(gradeAnalysisSequence, /CourseMemberRepository|GradeAnalysisSourceVersionStore/);
  assert.match(gradeAnalysisSequence, /listCourseStudentIds/);
  assert.match(gradeAnalysisSequence, /GradeRecord(?:<br\/>)?Repository/);
  assert.match(gradeAnalysisSequence, /CourseGradeSummary(?:<br\/>)?Repository/);
  assert.match(gradeAnalysisSequence, /findAnalysisSourceVersion/);

  const membershipCheck = studentGradeSequence.indexOf('isCourseMember');
  const accessException = studentGradeSequence.indexOf('StudentGradeAccessException');
  const summaryLookup = studentGradeSequence.indexOf('Service->>SummaryRepo');
  const publishException = studentGradeSequence.indexOf('GradePublishException');
  const recordLookup = studentGradeSequence.indexOf('Service->>RecordRepo');
  assert.ok(membershipCheck >= 0 && accessException > membershipCheck && summaryLookup > accessException);
  assert.ok(publishException > summaryLookup && recordLookup > publishException);
  assert.match(studentGradeSequence, /StudentGradeAccessException.*ERR-GRD-02.*403/);
  assert.match(studentGradeSequence, /GradePublishException.*ERR-GRD-04.*400/);
  assert.match(detailedDesign, /\| ERR-GRD-04 \|[^\n]*未发布/);
  assert.match(processDetailedDesign, /\| ERR-GRD-04 \|[^\n]*未发布/);
});

test('GRD mutating lifecycle only runs through the disposable database wrapper', () => {
  const runnerPath = 'scripts/test/run-grd-e2e-disposable.sh';
  assert.equal(existsSync(resolve(repositoryRoot, runnerPath)), true, `missing ${runnerPath}`);

  const runner = readRepositoryFile(runnerPath);
  const spec = readRepositoryFile('frontend/tests/e2e/grd/grade-lifecycle.spec.ts');
  const e2eReadme = readRepositoryFile('frontend/tests/e2e/README.md');
  const packageJson = JSON.parse(readRepositoryFile('frontend/package.json'));

  assert.match(runner, /mktemp -d/);
  assert.match(runner, /SPRING_DATASOURCE_URL/);
  assert.match(runner, /trap cleanup/);
  assert.match(runner, /openssl rand -hex/);
  assert.match(runner, /E2E_GRD_DISPOSABLE_PROOF_FILE/);
  assert.match(runner, /E2E_GRD_DISPOSABLE_TOKEN/);
  assert.match(runner, /tests\/e2e\/grd\/grade-lifecycle\.spec\.ts/);
  assert.doesNotMatch(spec, /process\.env\.E2E_GRD_DISPOSABLE_RUN/);
  assert.match(spec, /timingSafeEqual/);
  assert.match(spec, /realpathSync/);
  assert.match(spec, /process\.kill/);
  assert.match(spec, /test\.skip/);
  assert.equal(packageJson.scripts['test:e2e:grd:disposable'], 'bash ../scripts/test/run-grd-e2e-disposable.sh');
  assert.match(e2eReadme, /npm run test:e2e:grd:disposable/);
});
