<template>
  <main class="lab-workspace" aria-labelledby="lab-workspace-title">
    <header class="lab-workspace__hero">
      <div>
        <p class="lab-workspace__eyebrow">课程 #{{ courseId }} · 实验 #{{ labId }}</p>
        <h1 id="lab-workspace-title">实验提交工作台</h1>
        <p class="lab-workspace__intro">筛选提交、核对评测结果，并在同一处完成最终评分。</p>
      </div>
      <button
        class="button button--secondary lab-workspace__refresh"
        type="button"
        :disabled="queueLoading"
        @click="loadSubmissions"
      >
        {{ queueLoading ? '正在刷新…' : '刷新队列' }}
      </button>
    </header>

    <section class="summary-grid" aria-label="提交摘要">
      <article class="summary-card" data-testid="summary-total">
        <span>当前结果</span>
        <strong>{{ submissions.length }}</strong>
        <small>份提交</small>
      </article>
      <article class="summary-card" data-testid="summary-evaluation-pending">
        <span>等待评测</span>
        <strong>{{ evaluationPendingCount }}</strong>
        <small>份待出结果</small>
      </article>
      <article class="summary-card" data-testid="summary-scoring-pending">
        <span>等待评分</span>
        <strong>{{ scoringPendingCount }}</strong>
        <small>份未定分</small>
      </article>
      <article class="summary-card" data-testid="summary-late">
        <span>逾期提交</span>
        <strong>{{ lateCount }}</strong>
        <small>份需关注</small>
      </article>
    </section>

    <section class="work-surface filter-surface" aria-labelledby="submission-filter-title">
      <div class="section-heading">
        <div>
          <p class="section-heading__kicker">QUEUE FILTER</p>
          <h2 id="submission-filter-title">筛选提交</h2>
        </div>
        <button class="button button--quiet" type="button" @click="resetFilters">清除筛选</button>
      </div>

      <form
        class="filter-form"
        data-action="filter-submissions"
        @submit.prevent="loadSubmissions"
      >
        <label class="field">
          <span>学生编号</span>
          <input
            v-model.trim="filters.studentId"
            name="studentId"
            type="number"
            min="1"
            inputmode="numeric"
            placeholder="例如 602"
          >
        </label>

        <label class="field">
          <span>提交状态</span>
          <select v-model="filters.submitStatus" name="submitStatus">
            <option value="">全部提交状态</option>
            <option value="SUBMITTED">已提交</option>
            <option value="LATE">逾期提交</option>
            <option value="WITHDRAWN">已撤回</option>
          </select>
        </label>

        <label class="field">
          <span>评测状态</span>
          <select v-model="filters.evaluationStatus" name="evaluationStatus">
            <option value="">全部评测状态</option>
            <option value="NONE">未评测</option>
            <option value="PENDING">排队中</option>
            <option value="RUNNING">评测中</option>
            <option value="ACCEPTED">评测通过</option>
            <option value="WRONG_ANSWER">答案错误</option>
            <option value="COMPILE_ERROR">编译错误</option>
            <option value="RUNTIME_ERROR">运行错误</option>
            <option value="TIME_LIMIT_EXCEEDED">运行超时</option>
            <option value="SYSTEM_ERROR">系统错误</option>
          </select>
        </label>

        <label class="checkbox-field">
          <input v-model="filters.overdue" name="overdue" type="checkbox">
          <span>仅看逾期</span>
        </label>

        <button class="button button--primary filter-form__submit" type="submit" :disabled="queueLoading">
          {{ queueLoading ? '查询中…' : '查询提交' }}
        </button>
      </form>
    </section>

    <div class="lab-workspace__columns">
      <section class="work-surface queue-panel" aria-labelledby="submission-queue-title">
        <div class="section-heading section-heading--compact">
          <div>
            <p class="section-heading__kicker">SUBMISSION QUEUE</p>
            <h2 id="submission-queue-title">提交队列</h2>
          </div>
          <div class="queue-panel__tools">
            <a
              class="queue-filter-link"
              data-action="jump-to-submission-filters"
              href="#submission-filter-title"
            >筛选</a>
            <span class="count-chip">{{ submissions.length }} 份</span>
          </div>
        </div>

        <div v-if="queueLoading" class="state-panel" aria-live="polite">
          <p>正在加载提交队列…</p>
        </div>

        <div v-else-if="queueError" class="state-panel state-panel--error" role="alert">
          <strong>提交队列暂时无法加载</strong>
          <p>{{ queueError }}</p>
          <button
            class="button button--secondary"
            data-action="retry-submissions"
            type="button"
            @click="loadSubmissions"
          >
            重新加载
          </button>
        </div>

        <div v-else-if="submissions.length === 0" class="state-panel state-panel--empty">
          <strong>暂无符合条件的提交</strong>
          <p>调整筛选条件后重新查询，或等待学生提交实验。</p>
        </div>

        <ul v-else class="submission-list" aria-label="实验提交列表">
          <li
            v-for="submission in submissions"
            :key="submission.submissionId"
            :data-submission-id="submission.submissionId"
          >
            <button
              class="submission-card"
              :class="{ 'submission-card--selected': selectedSubmissionId === submission.submissionId }"
              type="button"
              :aria-pressed="selectedSubmissionId === submission.submissionId"
              @click="openSubmission(submission.submissionId)"
            >
              <span class="submission-card__topline">
                <strong>学生 #{{ submission.studentId }}</strong>
                <span class="submission-card__version">版本 {{ submission.version }}</span>
              </span>

              <span class="submission-card__status-row">
                <span class="status-pill" :class="submitStatusClass(submission.submitStatus)">
                  {{ submitStatusLabel(submission.submitStatus) }}
                </span>
                <span class="status-pill" :class="evaluationStatusClass(submission.evaluationStatus)">
                  {{ evaluationStatusLabel(submission.evaluationStatus) }}
                </span>
              </span>

              <span class="submission-card__meta">
                <span>{{ languageLabel(submission.language) }}</span>
                <span>{{ formatDateTime(submission.submittedAt) }}</span>
              </span>

              <span class="submission-card__scores">
                <span>自动分 <b>{{ scoreLabel(submission.autoScore) }}</b></span>
                <span>最终分 <b>{{ scoreLabel(submission.finalScore) }}</b></span>
              </span>

              <span v-if="submissionFlags(submission).length" class="submission-card__flags">
                <span v-for="flag in submissionFlags(submission)" :key="flag">{{ flag }}</span>
              </span>
            </button>
          </li>
        </ul>
      </section>

      <section class="work-surface detail-panel" aria-labelledby="submission-detail-title">
        <div class="section-heading section-heading--compact">
          <div>
            <p class="section-heading__kicker">REVIEW &amp; SCORE</p>
            <h2 id="submission-detail-title">提交详情与评分</h2>
          </div>
          <span v-if="submissionDetail" class="count-chip">#{{ submissionDetail.submissionId }}</span>
        </div>

        <div v-if="detailLoading" class="state-panel" aria-live="polite">
          <p>正在加载提交详情…</p>
        </div>

        <div v-else-if="detailError" class="state-panel state-panel--error" role="alert">
          <strong>提交详情暂时无法加载</strong>
          <p>{{ detailError }}</p>
          <button
            v-if="selectedSubmissionId !== null"
            class="button button--secondary"
            type="button"
            @click="openSubmission(selectedSubmissionId)"
          >
            重试详情
          </button>
        </div>

        <div v-else-if="!submissionDetail" class="state-panel state-panel--empty">
          <strong>选择一份提交开始核对</strong>
          <p>队列中的评测结果、源代码和评分记录会显示在这里。</p>
        </div>

        <div v-else class="detail-content">
          <div class="detail-summary">
            <div>
              <span>学生</span>
              <strong>#{{ submissionDetail.studentId }}</strong>
            </div>
            <div>
              <span>提交状态</span>
              <strong>{{ submitStatusLabel(submissionDetail.submitStatus) }}</strong>
            </div>
            <div>
              <span>评测状态</span>
              <strong>{{ evaluationStatusLabel(submissionDetail.evaluationStatus) }}</strong>
            </div>
            <div data-testid="selected-final-score">
              <span>最终得分</span>
              <strong>{{ scoreLabel(submissionDetail.finalScore) }}</strong>
            </div>
          </div>

          <section class="detail-block" aria-labelledby="submission-code-title">
            <div class="detail-block__heading">
              <h3 id="submission-code-title">源代码</h3>
              <span>{{ languageLabel(submissionDetail.language) }}</span>
            </div>
            <pre v-if="submissionDetail.code" class="code-preview"><code>{{ submissionDetail.code }}</code></pre>
            <p v-else class="inline-empty">
              本次提交没有文本代码{{ submissionDetail.hasFile ? '，请核对随附文件。' : '。' }}
            </p>
          </section>

          <section class="detail-block" aria-labelledby="submission-report-title">
            <div class="detail-block__heading">
              <h3 id="submission-report-title">实验报告</h3>
              <span v-if="submissionDetail.latestReport">版本 {{ submissionDetail.latestReport.version }}</span>
            </div>
            <dl v-if="submissionDetail.latestReport" class="report-meta">
              <div>
                <dt>文件</dt>
                <dd>{{ submissionDetail.latestReport.fileName }}</dd>
              </div>
              <div>
                <dt>类型</dt>
                <dd>{{ submissionDetail.latestReport.fileType }}</dd>
              </div>
              <div>
                <dt>报告分</dt>
                <dd>{{ scoreLabel(submissionDetail.latestReport.score) }}</dd>
              </div>
            </dl>
            <p v-else class="inline-empty">该提交未关联实验报告。</p>
          </section>

          <form
            class="score-form"
            data-action="score-submission"
            aria-labelledby="submission-score-title"
            @submit.prevent="saveScore"
          >
            <div class="detail-block__heading score-form__heading">
              <div>
                <h3 id="submission-score-title">教师评分</h3>
                <p>保存后更新当前提交的最终成绩。</p>
              </div>
              <span v-if="submissionDetail.latestScore" class="saved-badge">已有评分</span>
            </div>

            <div class="score-form__numbers">
              <label class="field">
                <span>人工评分</span>
                <input v-model.trim="scoreForm.manualScore" name="manualScore" type="number" min="0" step="0.01">
              </label>
              <label class="field">
                <span>报告评分</span>
                <input
                  v-model.trim="scoreForm.reportScore"
                  name="reportScore"
                  type="number"
                  min="0"
                  step="0.01"
                  placeholder="选填"
                >
              </label>
              <label class="field">
                <span>最终得分</span>
                <input v-model.trim="scoreForm.finalScore" name="finalScore" type="number" min="0" step="0.01">
              </label>
            </div>

            <label class="field">
              <span>评分评语</span>
              <textarea
                v-model="scoreForm.comment"
                name="comment"
                rows="3"
                placeholder="记录完成情况、主要问题与改进建议"
              ></textarea>
            </label>

            <label class="field">
              <span>修改原因 <em v-if="submissionDetail.latestScore">修改已有评分时必填</em></span>
              <textarea
                v-model="scoreForm.changeReason"
                name="changeReason"
                rows="2"
                placeholder="首次评分可留空"
              ></textarea>
            </label>

            <p v-if="scoreError" class="form-message form-message--error" data-testid="score-error" role="alert">
              {{ scoreError }}
            </p>
            <p v-if="scoreFeedback" class="form-message form-message--success" role="status">
              {{ scoreFeedback }}
            </p>

            <button class="button button--primary score-form__submit" type="submit" :disabled="scoreSaving">
              {{ scoreSaving ? '正在保存…' : '保存评分' }}
            </button>
          </form>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import {
  getLabSubmissionDetail,
  listLabSubmissions,
  scoreLabSubmission
} from '../../api/lab/labs';
import type {
  LabScorePayload,
  LabSubmissionDetail,
  LabSubmissionHistoryItem,
  LabSubmissionListFilters,
  LabSubmissionSummary
} from '../../types/lab';

const props = defineProps<{
  courseId: number;
  labId: number;
}>();

type SubmitStatus = LabSubmissionSummary['submitStatus'];
type EvaluationStatus = LabSubmissionSummary['evaluationStatus'];

interface FilterForm {
  studentId: string;
  submitStatus: '' | SubmitStatus;
  evaluationStatus: '' | EvaluationStatus;
  overdue: boolean;
}

interface ScoreForm {
  manualScore: string | number;
  reportScore: string | number;
  finalScore: string | number;
  comment: string;
  changeReason: string;
}

const filters = reactive<FilterForm>({
  studentId: '',
  submitStatus: '',
  evaluationStatus: '',
  overdue: false
});
const scoreForm = reactive<ScoreForm>({
  manualScore: '',
  reportScore: '',
  finalScore: '',
  comment: '',
  changeReason: ''
});

const submissions = ref<LabSubmissionHistoryItem[]>([]);
const selectedSubmissionId = ref<number | null>(null);
const submissionDetail = ref<LabSubmissionDetail | null>(null);
const queueLoading = ref(false);
const detailLoading = ref(false);
const scoreSaving = ref(false);
const queueError = ref('');
const detailError = ref('');
const scoreError = ref('');
const scoreFeedback = ref('');
let queueRequestId = 0;
let detailRequestId = 0;

const evaluationPendingCount = computed(() => submissions.value.filter((submission) =>
  ['NONE', 'PENDING', 'RUNNING'].includes(submission.evaluationStatus)
).length);
const scoringPendingCount = computed(() => submissions.value.filter((submission) =>
  submission.finalScore === null
).length);
const lateCount = computed(() => submissions.value.filter((submission) =>
  submission.submitStatus === 'LATE'
).length);

watch(
  () => props.labId,
  () => {
    clearSelection();
    void loadSubmissions();
  },
  { immediate: true }
);

function buildFilters(): LabSubmissionListFilters {
  const apiFilters: LabSubmissionListFilters = {};
  if (filters.studentId !== '') {
    const studentId = Number(filters.studentId);
    if (Number.isFinite(studentId) && studentId > 0) {
      apiFilters.studentId = studentId;
    }
  }
  if (filters.submitStatus) {
    apiFilters.submitStatus = filters.submitStatus;
  }
  if (filters.evaluationStatus) {
    apiFilters.evaluationStatus = filters.evaluationStatus;
  }
  if (filters.overdue) {
    apiFilters.overdue = true;
  }
  return apiFilters;
}

async function loadSubmissions() {
  const requestId = ++queueRequestId;
  queueLoading.value = true;
  queueError.value = '';
  scoreError.value = '';
  scoreFeedback.value = '';
  try {
    const result = await listLabSubmissions(props.labId, buildFilters());
    if (requestId !== queueRequestId) {
      return;
    }
    submissions.value = result;
    clearSelection();
    if (result.length > 0) {
      await openSubmission(result[0].submissionId);
    }
  } catch (error) {
    if (requestId !== queueRequestId) {
      return;
    }
    submissions.value = [];
    clearSelection();
    queueError.value = errorMessage(error, '提交队列加载失败');
  } finally {
    if (requestId === queueRequestId) {
      queueLoading.value = false;
    }
  }
}

async function openSubmission(submissionId: number) {
  const requestId = ++detailRequestId;
  selectedSubmissionId.value = submissionId;
  submissionDetail.value = null;
  detailLoading.value = true;
  detailError.value = '';
  scoreError.value = '';
  scoreFeedback.value = '';
  try {
    const result = await getLabSubmissionDetail(props.labId, submissionId);
    if (requestId !== detailRequestId) {
      return;
    }
    submissionDetail.value = result;
    syncScoreForm(result);
  } catch (error) {
    if (requestId !== detailRequestId) {
      return;
    }
    detailError.value = errorMessage(error, '提交详情加载失败');
  } finally {
    if (requestId === detailRequestId) {
      detailLoading.value = false;
    }
  }
}

async function saveScore() {
  const currentDetail = submissionDetail.value;
  if (!currentDetail) {
    return;
  }

  scoreError.value = '';
  scoreFeedback.value = '';
  let payload: LabScorePayload;
  try {
    payload = {
      manualScore: requiredScore(scoreForm.manualScore, '人工评分'),
      reportScore: optionalScore(scoreForm.reportScore, '报告评分'),
      finalScore: requiredScore(scoreForm.finalScore, '最终得分'),
      comment: normalizedText(scoreForm.comment),
      changeReason: normalizedText(scoreForm.changeReason)
    };
  } catch (error) {
    scoreError.value = errorMessage(error, '请检查评分输入');
    return;
  }

  if (scoreWasChanged(currentDetail, payload) && currentDetail.latestScore && !payload.changeReason) {
    scoreError.value = '修改已评分记录时必须填写修改原因';
    return;
  }

  scoreSaving.value = true;
  try {
    const result = await scoreLabSubmission(props.labId, currentDetail.submissionId, payload);
    submissionDetail.value = {
      ...currentDetail,
      autoScore: result.autoScore,
      finalScore: result.finalScore,
      latestReport: currentDetail.latestReport
        ? { ...currentDetail.latestReport, score: result.reportScore }
        : currentDetail.latestReport,
      latestScore: result
    };
    submissions.value = submissions.value.map((submission) => submission.submissionId === currentDetail.submissionId
      ? { ...submission, autoScore: result.autoScore, finalScore: result.finalScore }
      : submission);
    syncScoreForm(submissionDetail.value);
    scoreFeedback.value = '评分已保存';
  } catch (error) {
    scoreError.value = errorMessage(error, '评分保存失败');
  } finally {
    scoreSaving.value = false;
  }
}

function scoreWasChanged(detail: LabSubmissionDetail, payload: LabScorePayload) {
  const existing = detail.latestScore;
  if (!existing) {
    return true;
  }
  return existing.manualScore !== payload.manualScore
    || existing.reportScore !== payload.reportScore
    || existing.finalScore !== payload.finalScore
    || existing.comment !== payload.comment;
}

function syncScoreForm(detail: LabSubmissionDetail) {
  const score = detail.latestScore;
  scoreForm.manualScore = numberInputValue(score?.manualScore ?? detail.autoScore);
  scoreForm.reportScore = numberInputValue(score?.reportScore ?? detail.latestReport?.score ?? null);
  scoreForm.finalScore = numberInputValue(score?.finalScore ?? detail.finalScore ?? detail.autoScore);
  scoreForm.comment = score?.comment ?? '';
  scoreForm.changeReason = '';
}

function clearSelection() {
  detailRequestId += 1;
  selectedSubmissionId.value = null;
  submissionDetail.value = null;
  detailLoading.value = false;
  detailError.value = '';
  scoreError.value = '';
  scoreFeedback.value = '';
  syncBlankScoreForm();
}

function syncBlankScoreForm() {
  scoreForm.manualScore = '';
  scoreForm.reportScore = '';
  scoreForm.finalScore = '';
  scoreForm.comment = '';
  scoreForm.changeReason = '';
}

function resetFilters() {
  filters.studentId = '';
  filters.submitStatus = '';
  filters.evaluationStatus = '';
  filters.overdue = false;
  void loadSubmissions();
}

function requiredScore(value: string | number, label: string) {
  if (String(value).trim() === '') {
    throw new Error(`${label}不能为空`);
  }
  const score = Number(value);
  if (!Number.isFinite(score) || score < 0) {
    throw new Error(`${label}必须是大于或等于 0 的数字`);
  }
  return score;
}

function optionalScore(value: string | number, label: string) {
  if (String(value).trim() === '') {
    return null;
  }
  return requiredScore(value, label);
}

function normalizedText(value: string) {
  const normalized = value.trim();
  return normalized || null;
}

function numberInputValue(value: number | null | undefined) {
  return value === null || value === undefined ? '' : String(value);
}

function submitStatusLabel(status: SubmitStatus) {
  return {
    SUBMITTED: '已提交',
    LATE: '逾期提交',
    WITHDRAWN: '已撤回'
  }[status];
}

function evaluationStatusLabel(status: EvaluationStatus) {
  return {
    NONE: '未评测',
    PENDING: '排队中',
    RUNNING: '评测中',
    ACCEPTED: '评测通过',
    WRONG_ANSWER: '答案错误',
    COMPILE_ERROR: '编译错误',
    RUNTIME_ERROR: '运行错误',
    TIME_LIMIT_EXCEEDED: '运行超时',
    SYSTEM_ERROR: '系统错误'
  }[status];
}

function submitStatusClass(status: SubmitStatus) {
  return status === 'SUBMITTED' ? 'status-pill--success'
    : status === 'LATE' ? 'status-pill--warning'
      : 'status-pill--muted';
}

function evaluationStatusClass(status: EvaluationStatus) {
  if (status === 'ACCEPTED') {
    return 'status-pill--success';
  }
  if (status === 'NONE' || status === 'PENDING' || status === 'RUNNING') {
    return 'status-pill--info';
  }
  return 'status-pill--danger';
}

function languageLabel(language: string) {
  const labels: Record<string, string> = {
    cpp: 'C++',
    c: 'C',
    java: 'Java',
    python: 'Python',
    javascript: 'JavaScript',
    typescript: 'TypeScript'
  };
  return labels[language.toLowerCase()] ?? language;
}

function submissionFlags(submission: LabSubmissionHistoryItem) {
  const flags: string[] = [];
  if (submission.isLatest) {
    flags.push('最新版本');
  }
  if (submission.isFinal) {
    flags.push('当前有效');
  }
  if (submission.isScoringBasis) {
    flags.push('评分依据');
  }
  if (submission.hasFile) {
    flags.push('包含文件');
  }
  return flags;
}

function scoreLabel(score: number | null | undefined) {
  return score === null || score === undefined ? '待定' : `${score} 分`;
}

function formatDateTime(value: string) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(parsed);
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}
</script>

<style scoped>
.lab-workspace {
  --brand: #16423c;
  --brand-deep: #0e302c;
  --brand-soft: #dcebe6;
  --ink: #172b35;
  --muted: #66757d;
  --line: rgba(22, 66, 60, 0.16);
  --surface: rgba(250, 252, 252, 0.95);
  --surface-strong: #ffffff;
  --danger: #a33a36;
  --danger-soft: #f9e9e7;
  --warning: #925f0b;
  --warning-soft: #fff3d6;
  width: 100%;
  min-width: 0;
  max-width: 1440px;
  margin: 0 auto;
  padding: clamp(18px, 3vw, 40px);
  color: var(--ink);
}

.lab-workspace,
.lab-workspace * {
  box-sizing: border-box;
}

.lab-workspace :where(a, button, input, select, textarea):focus-visible {
  outline: 3px solid #2b7a70;
  outline-offset: 2px;
}

.lab-workspace__hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 22px;
}

.lab-workspace__eyebrow,
.section-heading__kicker {
  margin: 0 0 7px;
  color: var(--brand);
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.lab-workspace__hero h1 {
  margin: 0;
  color: var(--ink);
  font-size: clamp(1.75rem, 3vw, 2.65rem);
  line-height: 1.08;
  letter-spacing: -0.035em;
}

.lab-workspace__intro {
  max-width: 620px;
  margin: 10px 0 0;
  color: var(--muted);
  line-height: 1.65;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.summary-card,
.work-surface {
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: 0 12px 34px rgba(18, 47, 48, 0.08);
  backdrop-filter: blur(14px);
}

.summary-card {
  min-width: 0;
  padding: 18px 20px;
  overflow: hidden;
}

.summary-card span,
.summary-card small {
  display: block;
  color: var(--muted);
}

.summary-card span {
  font-size: 0.82rem;
  font-weight: 700;
}

.summary-card strong {
  display: inline-block;
  margin: 5px 5px 0 0;
  color: var(--brand-deep);
  font-size: 1.8rem;
  line-height: 1;
}

.summary-card small {
  display: inline;
  font-size: 0.75rem;
}

.work-surface {
  min-width: 0;
  padding: clamp(18px, 2.2vw, 26px);
}

.filter-surface {
  margin-bottom: 16px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 20px;
}

.section-heading--compact {
  align-items: flex-start;
  margin-bottom: 16px;
}

.section-heading h2 {
  margin: 0;
  font-size: 1.18rem;
  letter-spacing: -0.01em;
}

.filter-form {
  display: grid;
  grid-template-columns: minmax(120px, 0.85fr) repeat(2, minmax(150px, 1fr)) auto auto;
  align-items: end;
  gap: 12px;
}

.field {
  display: grid;
  min-width: 0;
  gap: 7px;
  color: #40545d;
  font-size: 0.82rem;
  font-weight: 700;
}

.field em {
  margin-left: 5px;
  color: var(--danger);
  font-size: 0.72rem;
  font-style: normal;
  font-weight: 600;
}

.field input,
.field select,
.field textarea {
  width: 100%;
  min-width: 0;
  border: 1px solid rgba(35, 69, 68, 0.24);
  border-radius: 9px;
  background: var(--surface-strong);
  color: var(--ink);
  font: inherit;
  font-weight: 500;
}

.field input,
.field select {
  min-height: 42px;
  padding: 0 11px;
}

.field textarea {
  padding: 10px 11px;
  resize: vertical;
  line-height: 1.55;
}

.checkbox-field {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 4px;
  color: #40545d;
  font-size: 0.84rem;
  font-weight: 700;
  white-space: nowrap;
}

.checkbox-field input {
  width: 18px;
  height: 18px;
  accent-color: var(--brand);
}

.button {
  min-height: 42px;
  border: 1px solid transparent;
  border-radius: 9px;
  padding: 0 16px;
  font: inherit;
  font-size: 0.84rem;
  font-weight: 800;
  cursor: pointer;
  transition: transform 140ms ease, background-color 140ms ease, border-color 140ms ease;
}

.button:hover:not(:disabled) {
  transform: translateY(-1px);
}

.button:disabled {
  cursor: wait;
  opacity: 0.62;
}

.button--primary {
  background: var(--brand);
  color: #fff;
}

.button--primary:hover:not(:disabled) {
  background: var(--brand-deep);
}

.button--secondary {
  border-color: rgba(22, 66, 60, 0.3);
  background: rgba(255, 255, 255, 0.82);
  color: var(--brand);
}

.button--quiet {
  min-height: 36px;
  padding: 0 10px;
  background: transparent;
  color: var(--brand);
}

.lab-workspace__columns {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(390px, 1.08fr);
  align-items: start;
  gap: 16px;
  min-width: 0;
}

.queue-panel,
.detail-panel {
  min-width: 0;
}

.count-chip,
.saved-badge {
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--brand-deep);
  padding: 5px 9px;
  font-size: 0.72rem;
  font-weight: 800;
}

.queue-panel__tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.queue-filter-link {
  display: none;
  border-radius: 999px;
  padding: 5px 9px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--brand);
  font-size: 0.72rem;
  font-weight: 800;
  text-decoration: none;
}

.submission-list {
  display: grid;
  gap: 10px;
  max-height: 780px;
  margin: 0;
  padding: 0 3px 0 0;
  overflow-y: auto;
  list-style: none;
}

.submission-card {
  display: grid;
  width: 100%;
  min-width: 0;
  gap: 10px;
  border: 1px solid rgba(35, 69, 68, 0.15);
  border-radius: 10px;
  padding: 15px;
  background: rgba(255, 255, 255, 0.88);
  color: var(--ink);
  text-align: left;
  cursor: pointer;
  transition: border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
}

.submission-card:hover,
.submission-card--selected {
  border-color: rgba(22, 66, 60, 0.58);
  box-shadow: 0 8px 18px rgba(18, 47, 48, 0.09);
  transform: translateY(-1px);
}

.submission-card--selected {
  background: rgba(220, 235, 230, 0.72);
}

.submission-card__topline,
.submission-card__status-row,
.submission-card__meta,
.submission-card__scores,
.submission-card__flags {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 7px;
  flex-wrap: wrap;
}

.submission-card__topline {
  justify-content: space-between;
}

.submission-card__version {
  color: var(--muted);
  font-size: 0.75rem;
  font-weight: 700;
}

.status-pill {
  border-radius: 999px;
  padding: 4px 8px;
  font-size: 0.7rem;
  font-weight: 800;
}

.status-pill--success {
  background: #dceee4;
  color: #17613d;
}

.status-pill--warning {
  background: var(--warning-soft);
  color: var(--warning);
}

.status-pill--muted {
  background: #e8ecee;
  color: #5d6c73;
}

.status-pill--info {
  background: #e4edf8;
  color: #315c88;
}

.status-pill--danger {
  background: var(--danger-soft);
  color: var(--danger);
}

.submission-card__meta {
  justify-content: space-between;
  color: var(--muted);
  font-size: 0.75rem;
}

.submission-card__scores {
  gap: 16px;
  color: var(--muted);
  font-size: 0.78rem;
}

.submission-card__scores b {
  color: var(--ink);
}

.submission-card__flags span {
  border: 1px solid rgba(22, 66, 60, 0.14);
  border-radius: 5px;
  padding: 3px 6px;
  color: #53686f;
  font-size: 0.66rem;
  font-weight: 700;
}

.state-panel {
  display: grid;
  justify-items: center;
  gap: 9px;
  min-height: 220px;
  align-content: center;
  border: 1px dashed rgba(35, 69, 68, 0.24);
  border-radius: 10px;
  padding: 28px 20px;
  color: var(--muted);
  text-align: center;
}

.state-panel strong {
  color: var(--ink);
}

.state-panel p {
  max-width: 380px;
  margin: 0;
  line-height: 1.55;
}

.state-panel--error {
  border-color: rgba(163, 58, 54, 0.28);
  background: rgba(249, 233, 231, 0.45);
}

.detail-content {
  display: grid;
  min-width: 0;
  gap: 14px;
}

.detail-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.detail-summary > div {
  min-width: 0;
  border-radius: 8px;
  padding: 10px;
  background: #eef4f2;
}

.detail-summary span,
.detail-summary strong {
  display: block;
  overflow-wrap: anywhere;
}

.detail-summary span {
  margin-bottom: 4px;
  color: var(--muted);
  font-size: 0.68rem;
}

.detail-summary strong {
  font-size: 0.82rem;
}

.detail-block,
.score-form {
  min-width: 0;
  border: 1px solid rgba(35, 69, 68, 0.14);
  border-radius: 10px;
  padding: 15px;
  background: rgba(255, 255, 255, 0.75);
}

.detail-block__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 11px;
}

.detail-block__heading h3,
.detail-block__heading p {
  margin: 0;
}

.detail-block__heading h3 {
  font-size: 0.95rem;
}

.detail-block__heading p,
.detail-block__heading > span {
  color: var(--muted);
  font-size: 0.72rem;
  line-height: 1.5;
}

.code-preview {
  max-width: 100%;
  max-height: 300px;
  margin: 0;
  overflow: auto;
  border-radius: 8px;
  padding: 14px;
  background: #142428;
  color: #d9eee8;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.78rem;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.inline-empty {
  margin: 0;
  border-radius: 8px;
  padding: 12px;
  background: #f0f4f4;
  color: var(--muted);
  font-size: 0.8rem;
}

.report-meta {
  display: grid;
  grid-template-columns: 1.5fr 0.7fr 0.7fr;
  gap: 8px;
  margin: 0;
}

.report-meta div {
  min-width: 0;
}

.report-meta dt {
  color: var(--muted);
  font-size: 0.68rem;
}

.report-meta dd {
  margin: 3px 0 0;
  font-size: 0.8rem;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.score-form {
  display: grid;
  gap: 13px;
}

.score-form__heading {
  margin-bottom: 0;
}

.score-form__numbers {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.score-form__submit {
  justify-self: end;
  min-width: 130px;
}

.form-message {
  margin: 0;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 0.8rem;
  font-weight: 700;
}

.form-message--error {
  background: var(--danger-soft);
  color: var(--danger);
}

.form-message--success {
  background: #dceee4;
  color: #17613d;
}

@media (max-width: 1040px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-form__submit {
    width: 100%;
  }

  .lab-workspace__columns {
    grid-template-columns: minmax(280px, 0.82fr) minmax(0, 1.18fr);
  }

  .detail-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .lab-workspace {
    display: flex;
    flex-direction: column;
    padding: 14px;
  }

  .lab-workspace__hero {
    display: grid;
    align-items: start;
    gap: 12px;
    margin-bottom: 14px;
  }

  .lab-workspace__intro { margin-top: 6px; line-height: 1.45; }

  .summary-grid { order: 2; }
  .lab-workspace__hero { order: 1; }
  .lab-workspace__columns { order: 3; }
  .filter-surface { order: 4; margin-top: 16px; }

  #submission-filter-title {
    scroll-margin-top: 90px;
  }

  .queue-filter-link {
    display: inline-flex;
  }

  .lab-workspace__refresh,
  .filter-form__submit {
    width: 100%;
  }

  .filter-form,
  .lab-workspace__columns {
    grid-template-columns: minmax(0, 1fr);
  }

  .submission-list {
    max-height: none;
    overflow: visible;
  }

  .score-form__numbers {
    grid-template-columns: minmax(0, 1fr);
  }

  .score-form__submit {
    justify-self: stretch;
    width: 100%;
  }
}

@media (max-width: 480px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .summary-card { padding: 12px; }
  .summary-card strong { font-size: 1.45rem; }

  .detail-summary,
  .report-meta {
    grid-template-columns: minmax(0, 1fr);
  }

  .section-heading {
    align-items: flex-start;
  }

  .work-surface {
    padding: 16px;
  }

  .submission-card__meta {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {
  .button,
  .submission-card {
    transition: none;
  }
}
</style>
