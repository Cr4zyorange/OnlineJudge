<template>
  <main class="lab-submission-review" data-testid="lab-submission-review">
    <PageHeader
      eyebrow="教师实验台 · 独立批阅"
      :title="pageTitle"
      subtitle="聚焦一次提交的有效版本、评测证据、实验报告与教师评分。"
    >
      <template #actions>
        <RouterLink class="review-link" :to="workspaceRoute">返回提交队列</RouterLink>
      </template>
    </PageHeader>

    <PageState
      v-if="pageLoading"
      state="loading"
      title="正在加载批阅内容"
      message="正在同步实验、提交版本与评测结果。"
    />

    <PageState
      v-else-if="pageError"
      state="error"
      title="批阅内容加载失败"
      :message="pageError"
      retry-label="重试"
      @retry="loadPage"
    >
      <template #actions>
        <RouterLink class="review-link" :to="workspaceRoute">返回提交队列</RouterLink>
      </template>
    </PageState>

    <template v-else-if="lab && submission && evaluation">
      <section class="review-context" aria-labelledby="review-context-title">
        <div>
          <p class="section-eyebrow">批阅对象</p>
          <h2 id="review-context-title" data-testid="review-student-name">{{ studentName }}</h2>
          <p v-if="courseName" class="context-course">{{ courseName }}</p>
          <p
            v-if="studentNameWarning"
            class="inline-message inline-message--warning"
            data-testid="student-name-warning"
            role="status"
          >
            {{ studentNameWarning }}
          </p>
        </div>

        <div class="context-statuses">
          <StatusBadge
            :label="formatLabSubmitStatus(submission.submitStatus)"
            :tone="labSubmitStatusTone(submission.submitStatus)"
          />
          <StatusBadge
            :label="`\u8bc4\u6d4b${formatLabEvaluationStatus(submission.evaluationStatus)}`"
            :tone="labEvaluationStatusTone(submission.evaluationStatus)"
          />
        </div>

        <div class="version-context" data-testid="review-version-context">
          <strong>版本 {{ submission.version }}</strong>
          <span :class="{ 'version-pill--muted': !submission.isScoringBasis }">
            {{ submission.isScoringBasis ? '当前评分依据' : '非当前评分依据' }}
          </span>
          <span :class="{ 'version-pill--muted': !submission.isLatest }">
            {{ submission.isLatest ? '最新提交' : '历史提交' }}
          </span>
          <span :class="{ 'version-pill--muted': !submission.isFinal }">
            {{ submission.isFinal ? '最终版本' : '非最终版本' }}
          </span>
        </div>

        <dl class="context-facts">
          <div><dt>提交时间</dt><dd>{{ formatLabDateTime(submission.submittedAt) }}</dd></div>
          <div><dt>语言</dt><dd>{{ formatLabLanguage(submission.language) }}</dd></div>
          <div><dt>自动得分</dt><dd>{{ formatLabScore(submission.autoScore) }}</dd></div>
          <div><dt>当前最终得分</dt><dd>{{ formatLabScore(submission.finalScore) }}</dd></div>
        </dl>
      </section>

      <div class="review-grid">
        <div class="review-column">
          <section class="review-card" aria-labelledby="review-code-title">
            <header class="card-heading">
              <div>
                <p class="section-eyebrow">提交内容</p>
                <h2 id="review-code-title">源代码</h2>
              </div>
              <span>{{ formatLabLanguage(submission.language) }}</span>
            </header>

            <pre v-if="submission.code" class="code-panel" data-testid="submission-code"><code>{{ submission.code }}</code></pre>
            <p v-else class="empty-copy" data-testid="submission-code">该版本没有可展示的文本代码。</p>

            <div v-if="submission.hasFile && submission.sourceFile" class="source-file-panel" data-testid="source-file-panel">
              <div class="source-file-heading">
                <div>
                  <strong>本次提交包含源文件</strong>
                  <p>仅展示安全元数据，下载将通过教师受控接口完成。</p>
                </div>
              </div>

              <dl class="source-file-facts">
                <div>
                  <dt>文件名</dt>
                  <dd data-testid="source-file-name">{{ submission.sourceFile.originalFilename }}</dd>
                </div>
                <div>
                  <dt>内容类型</dt>
                  <dd data-testid="source-file-content-type">{{ submission.sourceFile.contentType }}</dd>
                </div>
                <div>
                  <dt>文件大小</dt>
                  <dd data-testid="source-file-size">{{ formatFileSize(submission.sourceFile.fileSize) }}</dd>
                </div>
              </dl>

              <div v-if="submission.sourceFile.downloadAvailable" class="action-row">
                <button
                  class="button button--secondary"
                  data-action="download-source-file"
                  type="button"
                  :disabled="sourceFileDownloading"
                  @click="downloadSourceFile"
                >
                  {{ sourceFileDownloading ? '正在下载…' : '下载源文件' }}
                </button>
              </div>
              <p v-else class="inline-message inline-message--warning" role="status">
                当前源文件暂不可下载。
              </p>
              <p
                v-if="sourceFileDownloadError"
                class="inline-message inline-message--error"
                data-testid="source-file-download-error"
                role="alert"
              >
                {{ sourceFileDownloadError }}
              </p>
              <p
                v-if="sourceFileDownloadFeedback"
                class="inline-message inline-message--success"
                data-testid="source-file-download-feedback"
                role="status"
              >
                {{ sourceFileDownloadFeedback }}
              </p>
            </div>
            <div
              v-else-if="submission.hasFile"
              class="source-file-blocker"
              data-testid="source-file-metadata-unavailable"
              role="status"
            >
              <strong>本次提交包含源文件</strong>
              <p>该提交的源文件元数据暂不可用，请核对其他评测证据。</p>
            </div>
          </section>

          <section class="review-card" aria-labelledby="evaluation-title">
            <header class="card-heading">
              <div>
                <p class="section-eyebrow">自动评测</p>
                <h2 id="evaluation-title">测试用例结果</h2>
              </div>
              <StatusBadge
                :label="formatLabEvaluationStatus(evaluation.evaluationStatus)"
                :tone="labEvaluationStatusTone(evaluation.evaluationStatus)"
              />
            </header>

            <dl class="evaluation-summary" data-testid="evaluation-summary">
              <div><dt>评测得分</dt><dd>{{ formatLabScore(evaluation.score) }}</dd></div>
              <div><dt>通过用例</dt><dd>{{ evaluation.passedCases }} / {{ evaluation.totalCases }}</dd></div>
              <div><dt>完成时间</dt><dd>{{ formatLabDateTime(evaluation.finishedAt) }}</dd></div>
            </dl>

            <p v-if="evaluation.message" class="evaluation-message">{{ evaluation.message }}</p>

            <div v-if="evaluation.caseResults.length" class="case-list">
              <article
                v-for="caseItem in evaluation.caseResults"
                :key="caseItem.testcaseId"
                class="case-card"
                :class="caseItem.passed ? 'case-card--passed' : 'case-card--failed'"
                :data-testid="`evaluation-case-${caseItem.orderNum}`"
              >
                <header>
                  <strong>用例 {{ caseItem.orderNum }}</strong>
                  <span>{{ caseItem.passed ? '通过' : '未通过' }} · {{ formatLabScore(caseItem.score) }}</span>
                </header>
                <p v-if="caseItem.message">{{ caseItem.message }}</p>
                <dl class="case-io">
                  <div><dt>输入</dt><dd><code>{{ caseItem.input || '（空输入）' }}</code></dd></div>
                  <div><dt>期望输出</dt><dd><code>{{ caseItem.expectedOutput || '（空输出）' }}</code></dd></div>
                  <div><dt>实际输出</dt><dd><code>{{ caseItem.actualOutput || '（空输出）' }}</code></dd></div>
                </dl>
              </article>
            </div>
            <p v-else class="empty-copy">当前评测尚无可展示的用例明细。</p>

            <div class="action-row">
              <button
                class="button button--secondary"
                data-action="reevaluate-submission"
                type="button"
                :disabled="reevaluationSaving"
                @click="reevaluateSubmission"
              >
                {{ reevaluationSaving ? '正在提交重新评测…' : '重新评测此版本' }}
              </button>
            </div>
            <p
              v-if="reevaluationError"
              class="inline-message inline-message--error"
              data-testid="reevaluation-error"
              role="alert"
            >
              {{ reevaluationError }}
            </p>
            <p
              v-if="reevaluationFeedback"
              class="inline-message inline-message--success"
              data-testid="reevaluation-feedback"
              role="status"
            >
              {{ reevaluationFeedback }}
            </p>
          </section>
        </div>

        <aside class="review-column review-column--grading" aria-label="教师评分区">
          <section class="review-card" aria-labelledby="report-title">
            <header class="card-heading">
              <div>
                <p class="section-eyebrow">实验报告</p>
                <h2 id="report-title">报告下载与评分</h2>
              </div>
              <span v-if="submission.latestReport">版本 {{ submission.latestReport.version }}</span>
            </header>

            <template v-if="submission.latestReport">
              <dl class="report-facts">
                <div><dt>文件名</dt><dd>{{ submission.latestReport.fileName }}</dd></div>
                <div>
                  <dt>文件类型</dt>
                  <dd data-testid="report-file-type">{{ formatReportFileType(submission.latestReport.fileType) }}</dd>
                </div>
                <div data-testid="report-score-current">
                  <dt>当前报告分</dt><dd>{{ formatLabScore(submission.latestReport.score) }}</dd>
                </div>
              </dl>

              <button
                class="button button--secondary"
                data-action="download-report"
                type="button"
                :disabled="reportDownloading"
                @click="downloadReport"
              >
                {{ reportDownloading ? '正在下载…' : '下载实验报告' }}
              </button>
              <p
                v-if="reportDownloadError"
                class="inline-message inline-message--error"
                data-testid="report-download-error"
                role="alert"
              >
                {{ reportDownloadError }}
              </p>
              <p
                v-if="reportDownloadFeedback"
                class="inline-message inline-message--success"
                data-testid="report-download-feedback"
                role="status"
              >
                {{ reportDownloadFeedback }}
              </p>

              <form class="score-form" data-action="score-report" @submit.prevent="saveReportScore">
                <label class="field">
                  <span>报告评分（0–{{ lab.maxScore }} 分）</span>
                  <input
                    v-model.trim="reportScoreForm.score"
                    name="reportScore"
                    type="number"
                    min="0"
                    :max="lab.maxScore"
                    step="1"
                  >
                </label>
                <label class="field">
                  <span>报告评语</span>
                  <textarea v-model="reportScoreForm.comment" name="reportComment" rows="3"></textarea>
                </label>
                <p
                  v-if="reportScoreError"
                  class="inline-message inline-message--error"
                  data-testid="report-score-error"
                  role="alert"
                >
                  {{ reportScoreError }}
                </p>
                <p
                  v-if="reportScoreFeedback"
                  class="inline-message inline-message--success"
                  data-testid="report-score-feedback"
                  role="status"
                >
                  {{ reportScoreFeedback }}
                </p>
                <button class="button button--primary" type="submit" :disabled="reportScoreSaving">
                  {{ reportScoreSaving ? '正在保存…' : '保存报告评分' }}
                </button>
              </form>
            </template>
            <p v-else class="empty-copy">该提交未关联实验报告。</p>
          </section>

          <section class="review-card" aria-labelledby="submission-score-title">
            <header class="card-heading">
              <div>
                <p class="section-eyebrow">教师批阅</p>
                <h2 id="submission-score-title">提交评分</h2>
              </div>
              <span v-if="submission.latestScore">已评分</span>
            </header>

            <form class="score-form" data-action="score-submission" @submit.prevent="saveSubmissionScore">
              <div class="score-pair">
                <label class="field">
                  <span>人工评分</span>
                  <input
                    v-model.trim="submissionScoreForm.manualScore"
                    name="manualScore"
                    type="number"
                    min="0"
                    :max="lab.maxScore"
                    step="1"
                  >
                </label>
                <label class="field">
                  <span>最终得分</span>
                  <input
                    v-model.trim="submissionScoreForm.finalScore"
                    name="finalScore"
                    type="number"
                    min="0"
                    :max="lab.maxScore"
                    step="1"
                  >
                </label>
              </div>

              <p class="score-reference">
                当前纳入提交评分的报告分：
                <strong>{{ formatLabScore(normalizedOptionalScore(submissionScoreForm.reportScore)) }}</strong>
              </p>

              <label class="field">
                <span>总评</span>
                <textarea v-model="submissionScoreForm.comment" name="scoreComment" rows="4"></textarea>
              </label>
              <label class="field">
                <span>
                  修改原因
                  <em v-if="submission.latestScore">修改已评分记录时必填</em>
                </span>
                <textarea v-model="submissionScoreForm.changeReason" name="changeReason" rows="3"></textarea>
              </label>

              <p
                v-if="submissionScoreError"
                class="inline-message inline-message--error"
                data-testid="submission-score-error"
                role="alert"
              >
                {{ submissionScoreError }}
              </p>
              <p
                v-if="submissionScoreFeedback"
                class="inline-message inline-message--success"
                data-testid="submission-score-feedback"
                role="status"
              >
                {{ submissionScoreFeedback }}
              </p>
              <button class="button button--primary" type="submit" :disabled="submissionScoreSaving">
                {{ submissionScoreSaving ? '正在保存…' : '保存提交评分' }}
              </button>
            </form>
          </section>
        </aside>
      </div>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import PageHeader from '../../components/foundation/PageHeader.vue';
import PageState from '../../components/foundation/PageState.vue';
import StatusBadge from '../../components/foundation/StatusBadge.vue';
import {
  downloadLabReport,
  downloadLabSubmissionSource,
  evaluateLabSubmission,
  getLabDetail,
  getLabSubmissionDetail,
  getLabSubmissionResult,
  scoreLabReport,
  scoreLabSubmission
} from '../../api/lab/labs';
import { getTeacherLearningProgress } from '../../api/lrn/learningProgress';
import type {
  LabExperimentDetail,
  LabReportSummary,
  LabReportScorePayload,
  LabScorePayload,
  LabSubmissionDetail,
  LabSubmissionResult
} from '../../types/lab';
import {
  formatLabDateTime,
  formatLabEvaluationStatus,
  formatLabLanguage,
  formatLabScore,
  formatLabSubmitStatus,
  labEvaluationStatusTone,
  labSubmitStatusTone,
  localizedLabError
} from './labDisplay';

const props = defineProps<{
  courseId: number;
  labId: number;
  submissionId: number;
}>();

const submitStatusValues = new Set(['SUBMITTED', 'LATE', 'WITHDRAWN']);
const evaluationStatusValues = new Set([
  'NONE',
  'PENDING',
  'RUNNING',
  'ACCEPTED',
  'WRONG_ANSWER',
  'COMPILE_ERROR',
  'RUNTIME_ERROR',
  'TIME_LIMIT_EXCEEDED',
  'SYSTEM_ERROR'
]);

interface NumericScoreForm {
  score: string | number;
  comment: string;
}

interface SubmissionScoreForm {
  manualScore: string | number;
  reportScore: string | number;
  finalScore: string | number;
  comment: string;
  changeReason: string;
}

const lab = ref<LabExperimentDetail | null>(null);
const submission = ref<LabSubmissionDetail | null>(null);
const evaluation = ref<LabSubmissionResult | null>(null);
const courseName = ref('');
const studentName = ref('学生姓名暂不可用');
const studentNameWarning = ref('');
const pageLoading = ref(false);
const pageError = ref('');
const sourceFileDownloading = ref(false);
const sourceFileDownloadError = ref('');
const sourceFileDownloadFeedback = ref('');
const reportDownloading = ref(false);
const reportDownloadError = ref('');
const reportDownloadFeedback = ref('');
const reportScoreSaving = ref(false);
const reportScoreError = ref('');
const reportScoreFeedback = ref('');
const submissionScoreSaving = ref(false);
const submissionScoreError = ref('');
const submissionScoreFeedback = ref('');
const reevaluationSaving = ref(false);
const reevaluationError = ref('');
const reevaluationFeedback = ref('');
let pageRequestId = 0;
let sourceFileDownloadRequestId = 0;

const reportScoreForm = reactive<NumericScoreForm>({ score: '', comment: '' });
const submissionScoreForm = reactive<SubmissionScoreForm>({
  manualScore: '',
  reportScore: '',
  finalScore: '',
  comment: '',
  changeReason: ''
});

const route = useRoute();
const pageTitle = computed(() => lab.value ? `批阅：${lab.value.title}` : '实验提交批阅');
const workspaceRoute = computed(() => ({
  name: 'lab-submission-workspace',
  params: { courseId: props.courseId, labId: props.labId },
  query: safeQueueQuery(route.query)
}));

watch(
  () => `${props.courseId}:${props.labId}:${props.submissionId}`,
  () => {
    void loadPage();
  },
  { immediate: true }
);

onBeforeUnmount(invalidateSourceFileDownload);

async function loadPage() {
  const requestId = ++pageRequestId;
  invalidateSourceFileDownload();
  pageLoading.value = true;
  pageError.value = '';
  studentNameWarning.value = '';
  clearActionMessages();

  const [labResult, submissionResult, evaluationResult, progressResult] = await Promise.allSettled([
    getLabDetail(props.labId),
    getLabSubmissionDetail(props.labId, props.submissionId),
    getLabSubmissionResult(props.labId, props.submissionId),
    getTeacherLearningProgress(props.courseId)
  ]);
  if (requestId !== pageRequestId) {
    return;
  }

  const requiredErrors: string[] = [];
  if (labResult.status === 'fulfilled') {
    if (labResult.value.id !== props.labId || labResult.value.courseId !== props.courseId) {
      requiredErrors.push('实验与当前课程不匹配，请返回提交队列重新进入');
    } else {
      lab.value = labResult.value;
    }
  } else {
    requiredErrors.push(localizedLabError(labResult.reason, '实验信息加载失败'));
  }
  if (submissionResult.status === 'fulfilled') {
    if (
      submissionResult.value.submissionId !== props.submissionId
      || submissionResult.value.labId !== props.labId
    ) {
      requiredErrors.push('提交版本与当前实验不匹配，请返回提交队列重新进入');
    } else {
      submission.value = submissionResult.value;
    }
  } else {
    requiredErrors.push(localizedLabError(submissionResult.reason, '提交详情加载失败'));
  }
  if (evaluationResult.status === 'fulfilled') {
    if (evaluationResult.value.submissionId !== props.submissionId) {
      requiredErrors.push('评测结果与当前提交不匹配，请重新加载');
    } else {
      evaluation.value = evaluationResult.value;
    }
  } else {
    requiredErrors.push(localizedLabError(evaluationResult.reason, '评测结果加载失败'));
  }

  if (progressResult.status === 'fulfilled') {
    courseName.value = progressResult.value.courseName.trim();
    const selectedStudentId = submissionResult.status === 'fulfilled'
      ? submissionResult.value.studentId
      : null;
    const matchedStudent = progressResult.value.students.find((item) => item.studentId === selectedStudentId);
    studentName.value = matchedStudent?.studentName.trim() || '学生姓名暂不可用';
    if (!matchedStudent?.studentName.trim() && selectedStudentId !== null) {
      studentNameWarning.value = '课程名单中未找到该提交对应的学生姓名';
    }
  } else {
    courseName.value = '';
    studentName.value = '学生姓名暂不可用';
    studentNameWarning.value = localizedLabError(progressResult.reason, '学生姓名同步失败');
  }

  if (requiredErrors.length > 0) {
    lab.value = null;
    submission.value = null;
    evaluation.value = null;
    pageError.value = requiredErrors.join('；');
  } else if (submission.value) {
    syncForms(submission.value);
  }
  pageLoading.value = false;
}

async function downloadSourceFile() {
  const currentSubmission = submission.value;
  const currentSourceFile = currentSubmission?.sourceFile;
  if (
    sourceFileDownloading.value
    || !currentSubmission
    || !currentSourceFile
    || !currentSourceFile.downloadAvailable
  ) {
    return;
  }

  const labId = props.labId;
  const submissionId = currentSubmission.submissionId;
  const requestId = ++sourceFileDownloadRequestId;
  sourceFileDownloading.value = true;
  sourceFileDownloadError.value = '';
  sourceFileDownloadFeedback.value = '';
  try {
    const { blob, filename } = await downloadLabSubmissionSource(labId, submissionId);
    if (!isCurrentSourceFileDownload(requestId, labId, submissionId)) {
      return;
    }
    const downloadFilename = filename || currentSourceFile.originalFilename;
    const objectUrl = window.URL.createObjectURL(blob);
    try {
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = downloadFilename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
    } finally {
      window.URL.revokeObjectURL(objectUrl);
    }
    sourceFileDownloadFeedback.value = `源文件“${downloadFilename}”已开始下载`;
  } catch (error) {
    if (isCurrentSourceFileDownload(requestId, labId, submissionId)) {
      sourceFileDownloadError.value = localizedLabError(error, '源文件下载失败，请重试');
    }
  } finally {
    if (isCurrentSourceFileDownload(requestId, labId, submissionId)) {
      sourceFileDownloading.value = false;
    }
  }
}

async function downloadReport() {
  const currentReport = submission.value?.latestReport;
  if (!currentReport) {
    return;
  }

  reportDownloading.value = true;
  reportDownloadError.value = '';
  reportDownloadFeedback.value = '';
  try {
    const { blob, filename } = await downloadLabReport(props.labId, currentReport.reportId);
    const objectUrl = window.URL.createObjectURL(blob);
    try {
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = filename || currentReport.fileName;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
    } finally {
      window.URL.revokeObjectURL(objectUrl);
    }
    reportDownloadFeedback.value = `实验报告“${filename || currentReport.fileName}”已开始下载`;
  } catch (error) {
    reportDownloadError.value = localizedLabError(error, '实验报告下载失败，请重试');
  } finally {
    reportDownloading.value = false;
  }
}

async function saveReportScore() {
  const currentLab = lab.value;
  const currentSubmission = submission.value;
  const currentReport = currentSubmission?.latestReport;
  if (!currentLab || !currentSubmission || !currentReport) {
    return;
  }

  reportScoreError.value = '';
  reportScoreFeedback.value = '';
  let payload: LabReportScorePayload;
  try {
    payload = {
      score: requiredBoundedInteger(reportScoreForm.score, '报告评分', currentLab.maxScore),
      comment: reportScoreForm.comment.trim()
    };
  } catch (error) {
    reportScoreError.value = errorText(error, '请检查报告评分');
    return;
  }

  reportScoreSaving.value = true;
  try {
    const updatedReport = await scoreLabReport(props.labId, currentReport.reportId, payload);
    if (submission.value?.submissionId !== currentSubmission.submissionId) {
      return;
    }
    submission.value = {
      ...currentSubmission,
      latestReport: updatedReport
    };
    reportScoreForm.score = numberInputValue(updatedReport.score);
    reportScoreForm.comment = updatedReport.comment ?? '';
    submissionScoreForm.reportScore = numberInputValue(updatedReport.score);
    reportScoreFeedback.value = '报告评分已保存';
  } catch (error) {
    reportScoreError.value = localizedLabError(error, '报告评分保存失败');
  } finally {
    reportScoreSaving.value = false;
  }
}

async function saveSubmissionScore() {
  const currentLab = lab.value;
  const currentSubmission = submission.value;
  if (!currentLab || !currentSubmission) {
    return;
  }

  submissionScoreError.value = '';
  submissionScoreFeedback.value = '';
  let payload: LabScorePayload;
  try {
    payload = {
      manualScore: requiredBoundedInteger(
        submissionScoreForm.manualScore,
        '人工评分',
        currentLab.maxScore
      ),
      reportScore: optionalBoundedInteger(
        submissionScoreForm.reportScore,
        '报告评分',
        currentLab.maxScore
      ),
      finalScore: requiredBoundedInteger(
        submissionScoreForm.finalScore,
        '最终得分',
        currentLab.maxScore
      ),
      comment: normalizedText(submissionScoreForm.comment),
      changeReason: normalizedText(submissionScoreForm.changeReason)
    };
  } catch (error) {
    submissionScoreError.value = errorText(error, '请检查提交评分');
    return;
  }

  if (currentSubmission.latestScore && scoreChanged(currentSubmission, payload) && !payload.changeReason) {
    submissionScoreError.value = '修改已评分记录时必须填写修改原因';
    return;
  }

  submissionScoreSaving.value = true;
  try {
    const updatedScore = await scoreLabSubmission(
      props.labId,
      currentSubmission.submissionId,
      payload
    );
    if (submission.value?.submissionId !== currentSubmission.submissionId) {
      return;
    }
    const updatedSubmission: LabSubmissionDetail = {
      ...currentSubmission,
      autoScore: updatedScore.autoScore,
      finalScore: updatedScore.finalScore,
      latestScore: updatedScore,
      latestReport: currentSubmission.latestReport
        ? { ...currentSubmission.latestReport, score: updatedScore.reportScore }
        : currentSubmission.latestReport
    };
    submission.value = updatedSubmission;
    syncSubmissionScoreForm(updatedSubmission);
    submissionScoreFeedback.value = '提交评分已保存';
  } catch (error) {
    submissionScoreError.value = localizedLabError(error, '提交评分保存失败');
  } finally {
    submissionScoreSaving.value = false;
  }
}

async function reevaluateSubmission() {
  const currentSubmission = submission.value;
  if (!currentSubmission) {
    return;
  }
  const confirmed = window.confirm(
    `确认重新评测“${studentName.value}”的版本 ${currentSubmission.version}？新任务提交后将刷新当前批阅证据。`
  );
  if (!confirmed) {
    return;
  }

  reevaluationSaving.value = true;
  reevaluationError.value = '';
  reevaluationFeedback.value = '';
  try {
    await evaluateLabSubmission(props.labId, currentSubmission.submissionId);
    const [detailResult, evaluationResult] = await Promise.all([
      getLabSubmissionDetail(props.labId, currentSubmission.submissionId),
      getLabSubmissionResult(props.labId, currentSubmission.submissionId)
    ]);
    if (submission.value?.submissionId !== currentSubmission.submissionId) {
      return;
    }
    submission.value = detailResult;
    evaluation.value = evaluationResult;
    syncForms(detailResult);
    reevaluationFeedback.value = '已提交重新评测，当前版本与评测结果已刷新';
  } catch (error) {
    reevaluationError.value = localizedLabError(error, '重新评测提交失败，请重试');
  } finally {
    reevaluationSaving.value = false;
  }
}

function syncForms(detail: LabSubmissionDetail) {
  const currentReport = detail.latestReport;
  reportScoreForm.score = numberInputValue(currentReport?.score);
  reportScoreForm.comment = currentReport?.comment ?? '';
  syncSubmissionScoreForm(detail);
}

function syncSubmissionScoreForm(detail: LabSubmissionDetail) {
  const currentScore = detail.latestScore;
  submissionScoreForm.manualScore = numberInputValue(currentScore?.manualScore ?? detail.autoScore);
  submissionScoreForm.reportScore = numberInputValue(currentScore?.reportScore ?? detail.latestReport?.score);
  submissionScoreForm.finalScore = numberInputValue(currentScore?.finalScore ?? detail.finalScore ?? detail.autoScore);
  submissionScoreForm.comment = currentScore?.comment ?? '';
  submissionScoreForm.changeReason = '';
}

function scoreChanged(detail: LabSubmissionDetail, payload: LabScorePayload) {
  const currentScore = detail.latestScore;
  if (!currentScore) {
    return true;
  }
  return currentScore.manualScore !== payload.manualScore
    || currentScore.reportScore !== payload.reportScore
    || currentScore.finalScore !== payload.finalScore
    || currentScore.comment !== payload.comment;
}

function requiredBoundedInteger(value: string | number, label: string, maximum: number) {
  if (String(value).trim() === '') {
    throw new Error(`${label}不能为空`);
  }
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error(`${label}必须是大于或等于 0 的数字`);
  }
  if (!Number.isInteger(parsed)) {
    throw new Error(`${label}必须是整数`);
  }
  if (parsed > maximum) {
    throw new Error(`${label}不得超过 ${maximum} 分`);
  }
  return parsed;
}

function optionalBoundedInteger(value: string | number, label: string, maximum: number) {
  if (String(value).trim() === '') {
    return null;
  }
  return requiredBoundedInteger(value, label, maximum);
}

function normalizedOptionalScore(value: string | number) {
  if (String(value).trim() === '') {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function numberInputValue(value: number | null | undefined) {
  return value === null || value === undefined ? '' : value;
}

function normalizedText(value: string) {
  const normalized = value.trim();
  return normalized || null;
}

function safeQueueQuery(querySource: Record<string, unknown>) {
  const query: Record<string, string> = {};
  const keyword = queryText(querySource.keyword);
  const status = queryText(querySource.status);
  const evaluationStatus = queryText(querySource.evaluation);
  const overdue = queryText(querySource.overdue).toLowerCase();

  if (keyword) {
    query.keyword = keyword;
  }
  if (submitStatusValues.has(status)) {
    query.status = status;
  }
  if (evaluationStatusValues.has(evaluationStatus)) {
    query.evaluation = evaluationStatus;
  }
  if (overdue === 'true' || overdue === '1') {
    query.overdue = 'true';
  }
  return query;
}

function queryText(value: unknown) {
  const candidate = Array.isArray(value) ? value[0] : value;
  return typeof candidate === 'string' ? candidate.trim() : '';
}

function formatReportFileType(fileType: LabReportSummary['fileType']) {
  return {
    PDF: '便携式文档',
    DOCX: 'Word 文档',
    ZIP: '压缩文件'
  }[fileType];
}

function formatFileSize(size: number) {
  if (!Number.isFinite(size) || size < 0) {
    return '大小未知';
  }
  if (size >= 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(1)} MB`;
  }
  if (size >= 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${size} B`;
}

function isCurrentSourceFileDownload(requestId: number, labId: number, submissionId: number) {
  return requestId === sourceFileDownloadRequestId
    && props.labId === labId
    && props.submissionId === submissionId
    && submission.value?.labId === labId
    && submission.value?.submissionId === submissionId;
}

function invalidateSourceFileDownload() {
  sourceFileDownloadRequestId += 1;
  sourceFileDownloading.value = false;
  sourceFileDownloadError.value = '';
  sourceFileDownloadFeedback.value = '';
}

function errorText(error: unknown, fallback: string) {
  return error instanceof Error && error.message.trim() ? error.message : fallback;
}

function clearActionMessages() {
  sourceFileDownloadError.value = '';
  sourceFileDownloadFeedback.value = '';
  reportDownloadError.value = '';
  reportDownloadFeedback.value = '';
  reportScoreError.value = '';
  reportScoreFeedback.value = '';
  submissionScoreError.value = '';
  submissionScoreFeedback.value = '';
  reevaluationError.value = '';
  reevaluationFeedback.value = '';
}
</script>

<style scoped>
.lab-submission-review {
  display: grid;
  gap: 20px;
  width: 100%;
  max-width: none;
  min-width: 0;
  margin: 0;
}

.review-link,
.button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 9px 16px;
  border: 1px solid var(--oj-brand);
  border-radius: var(--oj-radius);
  font: inherit;
  font-weight: 800;
  text-decoration: none;
}

.review-link,
.button--secondary {
  background: rgba(255, 255, 255, 0.62);
  color: var(--oj-brand);
}

.button--primary {
  background: var(--oj-brand);
  color: #fff;
}

.button {
  cursor: pointer;
}

.button:disabled {
  cursor: wait;
  opacity: 0.62;
}

.review-context,
.review-card {
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  box-shadow: var(--oj-shadow-soft);
  backdrop-filter: var(--oj-blur);
  -webkit-backdrop-filter: var(--oj-blur);
}

.review-context {
  display: grid;
  grid-template-columns: minmax(190px, 1fr) auto minmax(260px, 1.5fr);
  align-items: center;
  gap: 18px 24px;
  padding: 20px 24px;
}

.section-eyebrow,
.context-course,
.review-context h2,
.card-heading h2,
.card-heading p,
.source-file-heading p,
.source-file-blocker p,
.evaluation-message,
.empty-copy,
.case-card p,
.inline-message,
.score-reference {
  margin: 0;
}

.section-eyebrow {
  color: var(--oj-brand);
  font-size: 0.74rem;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.review-context h2,
.card-heading h2 {
  color: var(--oj-ink);
}

.review-context h2 {
  margin-top: 5px;
  font-size: 1.35rem;
}

.context-course,
.empty-copy,
.score-reference {
  margin-top: 5px;
  color: var(--oj-ink-soft);
}

.context-statuses,
.version-context,
.action-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.version-context strong,
.version-context span {
  padding: 6px 10px;
  border-radius: 999px;
  background: var(--oj-brand-soft);
  color: var(--oj-brand-strong);
  font-size: 0.76rem;
  font-weight: 800;
}

.version-context .version-pill--muted {
  background: rgba(93, 113, 119, 0.1);
  color: var(--oj-muted);
}

.context-facts {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.context-facts div,
.evaluation-summary div,
.source-file-facts div,
.report-facts div {
  padding: 10px 12px;
  border: 1px solid var(--oj-line);
  border-radius: calc(var(--oj-radius) - 4px);
  background: rgba(255, 255, 255, 0.45);
}

.context-facts dt,
.evaluation-summary dt,
.source-file-facts dt,
.report-facts dt,
.case-io dt {
  color: var(--oj-muted);
  font-size: 0.72rem;
  font-weight: 800;
}

.context-facts dd,
.evaluation-summary dd,
.source-file-facts dd,
.report-facts dd,
.case-io dd {
  margin: 4px 0 0;
  color: var(--oj-ink);
  overflow-wrap: anywhere;
}

.review-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(340px, 0.8fr);
  align-items: start;
  gap: 20px;
  min-width: 0;
}

.review-column {
  display: grid;
  gap: 20px;
  min-width: 0;
}

.review-column--grading {
  position: sticky;
  top: 16px;
}

.review-card {
  display: grid;
  gap: 18px;
  min-width: 0;
  padding: 22px;
}

.card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.card-heading h2 {
  margin-top: 5px;
  font-size: 1.18rem;
}

.card-heading > span {
  color: var(--oj-muted);
  font-size: 0.8rem;
  font-weight: 800;
}

.code-panel {
  max-height: 560px;
  margin: 0;
  padding: 18px;
  border-radius: calc(var(--oj-radius) - 2px);
  background: #10211f;
  color: #e7f5ee;
  overflow: auto;
  font-size: 0.86rem;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.source-file-panel,
.source-file-blocker,
.evaluation-message,
.inline-message {
  padding: 12px 14px;
  border-radius: calc(var(--oj-radius) - 4px);
}

.source-file-panel {
  display: grid;
  gap: 14px;
  border: 1px solid rgba(28, 115, 90, 0.2);
  background: rgba(28, 115, 90, 0.07);
}

.source-file-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  color: var(--oj-ink);
}

.source-file-heading p {
  margin-top: 5px;
  color: var(--oj-ink-soft);
  line-height: 1.6;
}

.source-file-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.source-file-blocker {
  border: 1px solid rgba(194, 123, 0, 0.22);
  background: rgba(194, 123, 0, 0.08);
  color: #6b4103;
}

.source-file-blocker p {
  margin-top: 5px;
  line-height: 1.6;
}

.evaluation-summary,
.report-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.report-facts {
  grid-template-columns: minmax(0, 1fr);
}

.evaluation-message {
  background: var(--oj-brand-soft);
  color: var(--oj-brand-strong);
}

.case-list,
.score-form {
  display: grid;
  gap: 14px;
}

.case-card {
  display: grid;
  gap: 12px;
  padding: 15px;
  border: 1px solid var(--oj-line);
  border-left-width: 4px;
  border-radius: calc(var(--oj-radius) - 3px);
  background: rgba(255, 255, 255, 0.42);
}

.case-card--passed {
  border-left-color: var(--oj-brand);
}

.case-card--failed {
  border-left-color: #a13d32;
}

.case-card header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.case-card header span {
  color: var(--oj-ink-soft);
  font-size: 0.78rem;
  font-weight: 800;
}

.case-io {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.case-io div {
  min-width: 0;
  padding: 10px;
  border-radius: 8px;
  background: rgba(22, 66, 60, 0.06);
}

.case-io code {
  white-space: pre-wrap;
  word-break: break-word;
}

.field {
  display: grid;
  gap: 7px;
  color: var(--oj-ink-soft);
  font-size: 0.82rem;
  font-weight: 800;
}

.field em {
  color: #8f2d24;
  font-size: 0.72rem;
  font-style: normal;
}

.field input,
.field textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  border: 1px solid var(--oj-line-strong);
  border-radius: calc(var(--oj-radius) - 4px);
  background: rgba(255, 255, 255, 0.72);
  color: var(--oj-ink);
  font: inherit;
  font-weight: 500;
}

.field textarea {
  resize: vertical;
}

.score-pair {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.inline-message--warning {
  margin-top: 8px;
  background: rgba(194, 123, 0, 0.1);
  color: #6b4103;
}

.inline-message--error {
  background: rgba(190, 49, 49, 0.1);
  color: #8f2d24;
}

.inline-message--success {
  background: var(--oj-brand-soft);
  color: var(--oj-brand-strong);
}

@media (max-width: 1000px) {
  .review-context {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .version-context {
    grid-column: 1 / -1;
  }

  .review-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .review-column--grading {
    position: static;
  }
}

@media (max-width: 760px) {
  .lab-submission-review {
    gap: 14px;
  }

  .review-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 14px;
  }

  .review-context {
    grid-template-columns: minmax(0, 1fr);
    padding: 18px;
  }

  .context-facts,
  .evaluation-summary,
  .source-file-facts,
  .case-io,
  .score-pair {
    grid-template-columns: minmax(0, 1fr);
  }

  .review-card {
    padding: 18px;
  }

  .card-heading,
  .case-card header {
    align-items: flex-start;
    flex-direction: column;
  }

  .button,
  .review-link {
    width: 100%;
    box-sizing: border-box;
  }
}
</style>
