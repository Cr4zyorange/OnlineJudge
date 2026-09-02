<template>
  <main class="lab-workspace" aria-labelledby="lab-workspace-title">
    <header class="lab-workspace__hero">
      <div>
        <p class="lab-workspace__eyebrow">{{ courseContextLabel }}</p>
        <h1 id="lab-workspace-title">{{ labContextLabel }} · 提交队列</h1>
        <p class="lab-workspace__intro">按学生姓名和处理状态筛选提交，再进入独立批阅页完成评测核对与评分。</p>
      </div>
      <nav class="lab-workspace__actions" aria-label="实验管理快捷入口">
        <RouterLink
          class="button button--quiet"
          :to="{ name: 'lab-manage-detail', params: { courseId, labId } }"
        >返回实验详情</RouterLink>
        <RouterLink
          class="button button--secondary"
          :to="{ name: 'lab-statistics', params: { courseId, labId } }"
        >查看统计</RouterLink>
        <button
          class="button button--secondary"
          type="button"
          :disabled="queueLoading"
          @click="loadSubmissions"
        >
          {{ queueLoading ? '正在刷新…' : '刷新队列' }}
        </button>
      </nav>
    </header>

    <section
      v-if="fatalError"
      class="state-panel state-panel--error workspace-fatal-error"
      data-testid="workspace-fatal-error"
      role="alert"
    >
      <strong>提交队列数据无法安全展示</strong>
      <p>{{ fatalError }}</p>
      <button
        class="button button--secondary"
        data-action="retry-workspace"
        type="button"
        @click="loadWorkspace"
      >重新加载</button>
    </section>

    <template v-else>
    <div
      v-if="contextLoading"
      class="context-state"
      data-testid="context-loading"
      role="status"
    >
      正在加载实验与学生姓名…
    </div>

    <div
      v-if="labContextError"
      class="context-state context-state--error"
      data-testid="context-error"
      role="alert"
    >
      <span>{{ labContextError }}</span>
      <button
        class="button button--quiet button--small"
        data-action="retry-context"
        type="button"
        @click="loadLabContext"
      >重新加载实验</button>
    </div>

    <div
      v-if="studentNameWarning"
      class="context-state context-state--warning"
      data-testid="student-name-warning"
      role="status"
    >
      <span>{{ studentNameWarning }}；队列仍可查看，学生姓名将暂时隐藏。</span>
      <button
        class="button button--quiet button--small"
        data-action="retry-student-names"
        type="button"
        :disabled="studentNamesLoading"
        @click="loadStudentNames"
      >{{ studentNamesLoading ? '正在重试…' : '重试姓名服务' }}</button>
    </div>

    <section class="summary-grid" aria-label="提交摘要">
      <article class="summary-card" data-testid="summary-total">
        <span>当前结果</span>
        <strong>{{ visibleSubmissions.length }}</strong>
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
        <button
          class="button button--quiet"
          data-action="reset-filters"
          type="button"
          @click="resetFilters"
        >清除筛选</button>
      </div>

      <form
        class="filter-form"
        data-action="filter-submissions"
        @submit.prevent="applyFilters"
      >
        <label class="field">
          <span>学生姓名</span>
          <input
            v-model="filters.keyword"
            name="keyword"
            type="search"
            autocomplete="off"
            placeholder="输入姓名关键词"
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
          <span class="count-chip">{{ visibleSubmissions.length }} 份</span>
        </div>
      </div>

      <div
        v-if="queueLoading"
        class="state-panel"
        data-testid="queue-loading"
        aria-live="polite"
      >
        <p>正在加载提交队列…</p>
      </div>

      <div
        v-else-if="queueError"
        class="state-panel state-panel--error"
        data-testid="queue-error"
        role="alert"
      >
        <strong>提交队列暂时无法加载</strong>
        <p>{{ queueError }}</p>
        <button
          class="button button--secondary"
          data-action="retry-submissions"
          type="button"
          @click="loadSubmissions"
        >重新加载</button>
      </div>

      <div
        v-else-if="visibleSubmissions.length === 0"
        class="state-panel state-panel--empty"
        data-testid="queue-empty"
      >
        <strong>暂无符合条件的提交</strong>
        <p>调整学生姓名或状态筛选后重新查询，或等待学生提交实验。</p>
      </div>

      <ul v-else class="submission-list" aria-label="实验提交列表">
        <li
          v-for="submission in visibleSubmissions"
          :key="submission.submissionId"
          :data-submission-id="submission.submissionId"
        >
          <RouterLink
            class="submission-card"
            :to="reviewRoute(submission.submissionId)"
            :aria-label="`批阅${studentDisplayName(submission.studentId)}的第 ${submission.version} 版提交`"
          >
            <span class="submission-card__topline">
              <strong>{{ studentDisplayName(submission.studentId) }}</strong>
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

            <span class="submission-card__footer">
              <span v-if="submissionFlags(submission).length" class="submission-card__flags">
                <span v-for="flag in submissionFlags(submission)" :key="flag">{{ flag }}</span>
              </span>
              <span class="submission-card__review-cue">进入批阅 <span aria-hidden="true">→</span></span>
            </span>
          </RouterLink>
        </li>
      </ul>
    </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { getLabDetail, listLabSubmissions } from '../../api/lab/labs';
import { getTeacherLearningProgress } from '../../api/lrn/learningProgress';
import type {
  LabSubmissionHistoryItem,
  LabSubmissionListFilters,
  LabSubmissionId,
  LabSubmissionSummary
} from '../../types/lab';

const props = defineProps<{
  courseId: number;
  labId: number;
}>();

type SubmitStatus = LabSubmissionSummary['submitStatus'];
type EvaluationStatus = LabSubmissionSummary['evaluationStatus'];

interface FilterForm {
  keyword: string;
  submitStatus: '' | SubmitStatus;
  evaluationStatus: '' | EvaluationStatus;
  overdue: boolean;
}

const submitStatuses: SubmitStatus[] = ['SUBMITTED', 'LATE', 'WITHDRAWN'];
const evaluationStatuses: EvaluationStatus[] = [
  'NONE',
  'PENDING',
  'RUNNING',
  'ACCEPTED',
  'WRONG_ANSWER',
  'COMPILE_ERROR',
  'RUNTIME_ERROR',
  'TIME_LIMIT_EXCEEDED',
  'SYSTEM_ERROR'
];

const route = useRoute();
const router = useRouter();
const filters = reactive<FilterForm>({
  keyword: queryText(route.query.keyword),
  submitStatus: submitStatusFromQuery(route.query.status),
  evaluationStatus: evaluationStatusFromQuery(route.query.evaluation),
  overdue: booleanFromQuery(route.query.overdue)
});
const submissions = ref<LabSubmissionHistoryItem[]>([]);
const labTitle = ref('');
const courseName = ref('');
const studentNames = ref<Record<number, string>>({});
const labContextLoading = ref(false);
const studentNamesLoading = ref(false);
const queueLoading = ref(false);
const labContextError = ref('');
const studentNameWarning = ref('');
const queueError = ref('');
const fatalError = ref('');
let labContextRequestId = 0;
let studentNamesRequestId = 0;
let queueRequestId = 0;

const contextLoading = computed(() => labContextLoading.value || studentNamesLoading.value);
const visibleSubmissions = computed(() => {
  const keyword = filters.keyword.trim().toLocaleLowerCase('zh-CN');
  if (!keyword) {
    return submissions.value;
  }
  return submissions.value.filter((submission) => studentDisplayName(submission.studentId)
    .toLocaleLowerCase('zh-CN')
    .includes(keyword));
});
const evaluationPendingCount = computed(() => visibleSubmissions.value.filter((submission) =>
  ['NONE', 'PENDING', 'RUNNING'].includes(submission.evaluationStatus)
).length);
const scoringPendingCount = computed(() => visibleSubmissions.value.filter((submission) =>
  submission.finalScore === null
).length);
const lateCount = computed(() => visibleSubmissions.value.filter((submission) =>
  submission.submitStatus === 'LATE'
).length);
const courseContextLabel = computed(() => fatalError.value ? '当前课程' : courseName.value || '当前课程');
const labContextLabel = computed(() => fatalError.value ? '当前实验' : labTitle.value || '当前实验');

watch(
  () => `${props.courseId}:${props.labId}`,
  () => {
    void loadWorkspace();
  },
  { immediate: true }
);

async function loadWorkspace() {
  fatalError.value = '';
  labContextError.value = '';
  studentNameWarning.value = '';
  queueError.value = '';
  labTitle.value = '';
  courseName.value = '';
  studentNames.value = {};
  submissions.value = [];
  await Promise.all([
    syncFilterQuery(),
    loadLabContext(),
    loadStudentNames(),
    loadSubmissions()
  ]);
}

async function loadLabContext() {
  const requestId = ++labContextRequestId;
  const targetLabId = props.labId;
  const targetCourseId = props.courseId;
  labContextLoading.value = true;
  labContextError.value = '';
  labTitle.value = '';
  try {
    const lab = await getLabDetail(targetLabId);
    if (requestId !== labContextRequestId) {
      return;
    }
    if (lab.id !== targetLabId || lab.courseId !== targetCourseId) {
      setFatalError('实验详情归属与当前页面不一致，请重新加载。');
      return;
    }
    if (fatalError.value) {
      return;
    }
    labTitle.value = lab.title.trim();
  } catch (error) {
    if (requestId !== labContextRequestId) {
      return;
    }
    labContextError.value = errorMessage(error, '实验信息加载失败');
  } finally {
    if (requestId === labContextRequestId) {
      labContextLoading.value = false;
    }
  }
}

async function loadStudentNames() {
  const requestId = ++studentNamesRequestId;
  const targetCourseId = props.courseId;
  studentNamesLoading.value = true;
  studentNameWarning.value = '';
  courseName.value = '';
  studentNames.value = {};
  try {
    const progress = await getTeacherLearningProgress(targetCourseId);
    if (requestId !== studentNamesRequestId) {
      return;
    }
    if (progress.courseId !== targetCourseId) {
      setFatalError('课程学生数据归属与当前页面不一致，请重新加载。');
      return;
    }
    if (fatalError.value) {
      return;
    }
    courseName.value = progress.courseName.trim();
    studentNames.value = Object.fromEntries(progress.students
      .map((student) => [student.studentId, student.studentName.trim()] as const)
      .filter(([, name]) => name.length > 0));
  } catch (error) {
    if (requestId !== studentNamesRequestId) {
      return;
    }
    studentNameWarning.value = errorMessage(error, '学生姓名加载失败');
  } finally {
    if (requestId === studentNamesRequestId) {
      studentNamesLoading.value = false;
    }
  }
}

async function loadSubmissions() {
  const requestId = ++queueRequestId;
  const targetLabId = props.labId;
  queueLoading.value = true;
  queueError.value = '';
  try {
    const result = await listLabSubmissions(targetLabId, buildApiFilters());
    if (requestId !== queueRequestId) {
      return;
    }
    if (fatalError.value) {
      submissions.value = [];
      return;
    }
    submissions.value = result;
  } catch (error) {
    if (requestId !== queueRequestId) {
      return;
    }
    submissions.value = [];
    queueError.value = errorMessage(error, '提交队列加载失败');
  } finally {
    if (requestId === queueRequestId) {
      queueLoading.value = false;
    }
  }
}

async function applyFilters() {
  filters.keyword = filters.keyword.trim();
  await syncFilterQuery();
  await loadSubmissions();
}

async function resetFilters() {
  filters.keyword = '';
  filters.submitStatus = '';
  filters.evaluationStatus = '';
  filters.overdue = false;
  await syncFilterQuery();
  await loadSubmissions();
}

function buildApiFilters(): LabSubmissionListFilters {
  const apiFilters: LabSubmissionListFilters = {};
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

async function syncFilterQuery() {
  await router.replace({ query: buildFilterQuery() });
}

function buildFilterQuery() {
  const query: Record<string, string> = {};
  const keyword = filters.keyword.trim();
  if (keyword) {
    query.keyword = keyword;
  }
  if (filters.submitStatus) {
    query.status = filters.submitStatus;
  }
  if (filters.evaluationStatus) {
    query.evaluation = filters.evaluationStatus;
  }
  if (filters.overdue) {
    query.overdue = 'true';
  }
  return query;
}

function reviewRoute(submissionId: LabSubmissionId) {
  const query = buildFilterQuery();
  return {
    name: 'lab-submission-review',
    params: {
      courseId: props.courseId,
      labId: props.labId,
      submissionId
    },
    ...(Object.keys(query).length > 0 ? { query } : {})
  };
}

function queryText(value: unknown) {
  const candidate = Array.isArray(value) ? value[0] : value;
  return typeof candidate === 'string' ? candidate.trim() : '';
}

function submitStatusFromQuery(value: unknown): '' | SubmitStatus {
  const candidate = queryText(value);
  return submitStatuses.includes(candidate as SubmitStatus) ? candidate as SubmitStatus : '';
}

function evaluationStatusFromQuery(value: unknown): '' | EvaluationStatus {
  const candidate = queryText(value);
  return evaluationStatuses.includes(candidate as EvaluationStatus) ? candidate as EvaluationStatus : '';
}

function booleanFromQuery(value: unknown) {
  const candidate = queryText(value).toLowerCase();
  return candidate === 'true' || candidate === '1';
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

function studentDisplayName(studentId: number) {
  return studentNames.value[studentId] || '学生姓名暂不可用';
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

function setFatalError(message: string) {
  fatalError.value = message;
  labTitle.value = '';
  courseName.value = '';
  studentNames.value = {};
  submissions.value = [];
  labContextError.value = '';
  studentNameWarning.value = '';
  queueError.value = '';
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
  max-width: none;
  margin: 0 auto;
  padding: clamp(18px, 3vw, 40px);
  color: var(--ink);
}

.lab-workspace,
.lab-workspace * {
  box-sizing: border-box;
}

.lab-workspace :where(a, button, input, select):focus-visible {
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
  max-width: 680px;
  margin: 10px 0 0;
  color: var(--muted);
  line-height: 1.65;
}

.lab-workspace__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 9px;
  flex-wrap: wrap;
}

.context-state {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: -8px 0 16px;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 10px 13px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--muted);
  font-size: 0.8rem;
  font-weight: 700;
}

.context-state--error {
  border-color: rgba(163, 58, 54, 0.28);
  background: rgba(249, 233, 231, 0.62);
  color: var(--danger);
}

.context-state--warning {
  border-color: rgba(146, 95, 11, 0.28);
  background: rgba(255, 243, 214, 0.68);
  color: #714b0e;
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
  grid-template-columns: minmax(180px, 1.25fr) repeat(2, minmax(160px, 1fr)) auto auto;
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

.field input,
.field select {
  width: 100%;
  min-width: 0;
  min-height: 42px;
  border: 1px solid rgba(35, 69, 68, 0.24);
  border-radius: 9px;
  padding: 0 11px;
  background: var(--surface-strong);
  color: var(--ink);
  font: inherit;
  font-weight: 500;
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
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  border: 1px solid transparent;
  border-radius: 9px;
  padding: 0 16px;
  font: inherit;
  font-size: 0.84rem;
  font-weight: 800;
  text-decoration: none;
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

.button--small {
  min-height: 34px;
  padding: 0 11px;
  font-size: 0.76rem;
}

.queue-panel {
  min-width: 0;
}

.count-chip {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 5px 9px;
  background: var(--brand-soft);
  color: var(--brand-deep);
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
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.submission-list li {
  display: flex;
  min-width: 0;
}

.submission-card {
  display: grid;
  width: 100%;
  min-width: 0;
  gap: 10px;
  border: 1px solid rgba(35, 69, 68, 0.15);
  border-radius: 10px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.88);
  color: var(--ink);
  text-align: left;
  text-decoration: none;
  transition: border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
}

.submission-card:hover {
  border-color: rgba(22, 66, 60, 0.58);
  box-shadow: 0 8px 18px rgba(18, 47, 48, 0.09);
  transform: translateY(-1px);
}

.submission-card__topline,
.submission-card__status-row,
.submission-card__meta,
.submission-card__scores,
.submission-card__footer,
.submission-card__flags {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 7px;
  flex-wrap: wrap;
}

.submission-card__topline,
.submission-card__meta,
.submission-card__footer {
  justify-content: space-between;
}

.submission-card__topline strong {
  overflow-wrap: anywhere;
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

.submission-card__footer {
  align-self: end;
  border-top: 1px solid rgba(35, 69, 68, 0.1);
  padding-top: 10px;
}

.submission-card__flags span {
  border: 1px solid rgba(22, 66, 60, 0.14);
  border-radius: 5px;
  padding: 3px 6px;
  color: #53686f;
  font-size: 0.66rem;
  font-weight: 700;
}

.submission-card__review-cue {
  margin-left: auto;
  color: var(--brand);
  font-size: 0.76rem;
  font-weight: 800;
  white-space: nowrap;
}

.state-panel {
  display: grid;
  justify-items: center;
  gap: 9px;
  min-height: 260px;
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
  max-width: 460px;
  margin: 0;
  line-height: 1.55;
}

.state-panel--error {
  border-color: rgba(163, 58, 54, 0.28);
  background: rgba(249, 233, 231, 0.45);
}

@media (max-width: 1040px) {
  .lab-workspace__hero {
    align-items: flex-start;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-form__submit {
    width: 100%;
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
    gap: 13px;
    margin-bottom: 14px;
  }

  .lab-workspace__actions {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    width: 100%;
  }

  .lab-workspace__actions .button:last-child {
    grid-column: 1 / -1;
  }

  .lab-workspace__intro {
    margin-top: 6px;
    line-height: 1.5;
  }

  .context-state {
    align-items: stretch;
    flex-direction: column;
  }

  .context-state .button {
    width: 100%;
  }

  .filter-form {
    grid-template-columns: minmax(0, 1fr);
  }

  .filter-form__submit {
    width: 100%;
  }

  #submission-filter-title {
    scroll-margin-top: 90px;
  }

  .queue-filter-link {
    display: inline-flex;
  }

  .submission-list {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 480px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .summary-card {
    padding: 12px;
  }

  .summary-card strong {
    font-size: 1.45rem;
  }

  .section-heading {
    align-items: flex-start;
  }

  .work-surface {
    padding: 16px;
  }

  .submission-card__meta,
  .submission-card__footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .submission-card__review-cue {
    margin-left: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .button,
  .submission-card {
    transition: none;
  }
}
</style>
