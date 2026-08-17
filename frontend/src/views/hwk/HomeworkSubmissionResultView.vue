<template>
  <main class="homework-result">
    <PageHeader
      v-if="homework"
      eyebrow="学生作业台 · 评测结果"
      :title="homework.title"
      :subtitle="submission ? `正在查看版本 ${submission.version} 的提交回执与评测依据。` : '查看最新提交的评测结果。'"
    >
      <template #actions>
        <RouterLink class="homework-result__link" :to="detailHref">返回作业详情</RouterLink>
        <RouterLink class="homework-result__link homework-result__link--primary" :to="historyHref">
          查看提交历史
        </RouterLink>
      </template>
    </PageHeader>

    <PageState
      v-if="loading"
      state="loading"
      title="正在加载评测结果"
      message="正在同步作业、提交回执与评测状态。"
    />

    <PageState
      v-else-if="errorMessage"
      :state="errorState"
      :title="errorState === 'forbidden' ? '暂无权限查看该结果' : '评测结果加载失败'"
      :message="errorMessage"
      :retry-label="errorState === 'forbidden' ? undefined : '重试'"
      @retry="loadPage"
    >
      <template #actions>
        <RouterLink class="homework-result__link" :to="detailHref">返回作业详情</RouterLink>
      </template>
    </PageState>

    <PageState
      v-else-if="!submission"
      state="empty"
      title="还没有可查看的提交结果"
      message="完成一次作业提交后，这里会展示评测进度和最终结果。"
    >
      <template #actions>
        <RouterLink class="homework-result__link homework-result__link--primary" :to="detailHref">
          返回作业详情
        </RouterLink>
      </template>
    </PageState>

    <template v-else>
      <SummaryStrip :items="summaryItems" aria-label="提交结果摘要" />

      <section class="homework-result__workspace" aria-label="评测结果详情">
        <header class="homework-result__section-heading">
          <div>
            <p class="homework-result__eyebrow">提交 #{{ submission.submissionId }}</p>
            <h2>版本 {{ submission.version }} 的结果</h2>
          </div>
          <StatusBadge
            :label="resultStatusLabel"
            :tone="resultStatusTone"
            :title="resultStatusLabel"
          />
        </header>

        <section
          v-if="!canViewEvaluation"
          class="homework-result__notice"
          data-testid="result-hidden"
          role="status"
        >
          <strong>评测结果尚未发布</strong>
          <p>教师发布成绩后，你可以在此查看自动评测结果、最终得分与评语。</p>
        </section>

        <section
          v-else-if="submission.evaluationStatus === 'NONE'"
          class="homework-result__notice"
          data-testid="no-evaluation"
          role="status"
        >
          <strong>本次提交暂无自动评测结果</strong>
          <p>文本或附件类作业可能需要等待教师批阅。</p>
        </section>

        <section v-else-if="evaluation" class="homework-result__evaluation">
          <div v-if="isEvaluationPending" class="homework-result__pending" role="status" aria-live="polite">
            <span class="homework-result__spinner" aria-hidden="true" />
            <div>
              <strong>{{ formatEvaluationStatus(evaluation.evaluationStatus) }}</strong>
              <p>页面会自动更新，你也可以手动刷新当前状态。</p>
            </div>
          </div>

          <div v-else class="homework-result__score-card" data-testid="evaluation-score">
            <span>自动评测得分</span>
            <strong>{{ formatScore(evaluation.score) }}</strong>
            <small>/ {{ homework?.totalScore ?? 0 }} 分</small>
          </div>

          <dl class="homework-result__facts">
            <div>
              <dt>通过用例</dt>
              <dd>{{ evaluation.passedCases }} / {{ evaluation.totalCases }}</dd>
            </div>
            <div>
              <dt>运行耗时</dt>
              <dd>{{ formatDuration(evaluation.durationMs) }}</dd>
            </div>
            <div>
              <dt>开始时间</dt>
              <dd>{{ formatDateTime(evaluation.startedAt) }}</dd>
            </div>
            <div>
              <dt>完成时间</dt>
              <dd>{{ evaluation.finishedAt ? formatDateTime(evaluation.finishedAt) : '尚未完成' }}</dd>
            </div>
          </dl>

          <div v-if="evaluation.feedback" class="homework-result__message homework-result__message--feedback">
            <strong>评测反馈</strong>
            <p>{{ evaluation.feedback }}</p>
          </div>
          <div v-if="evaluation.errorMessage" class="homework-result__message homework-result__message--error" role="alert">
            <strong>错误信息</strong>
            <p>{{ evaluation.errorMessage }}</p>
          </div>
          <details v-if="evaluation.compileLog" class="homework-result__log">
            <summary>查看编译日志</summary>
            <pre>{{ evaluation.compileLog }}</pre>
          </details>
          <details v-if="evaluation.runLog" class="homework-result__log">
            <summary>查看运行日志</summary>
            <pre>{{ evaluation.runLog }}</pre>
          </details>
        </section>

        <p v-if="evaluationErrorMessage" class="homework-result__poll-error" role="alert">
          {{ evaluationErrorMessage }}
        </p>

        <div v-if="canRefreshEvaluation" class="homework-result__refresh-row">
          <button type="button" :disabled="refreshing" @click="manualRefreshEvaluation">
            {{ refreshing ? '正在刷新…' : '手动刷新评测状态' }}
          </button>
        </div>
      </section>

      <section
        v-if="showPublishedReview"
        class="homework-result__workspace homework-result__review"
        data-testid="published-review"
        aria-label="已发布的最终成绩"
      >
        <header class="homework-result__section-heading">
          <div>
            <p class="homework-result__eyebrow">教师批阅</p>
            <h2>已发布的最终成绩</h2>
          </div>
          <StatusBadge :label="formatReviewStatus(submission.reviewStatus)" tone="success" />
        </header>
        <p v-if="submission.finalScore !== null && submission.finalScore !== undefined" class="homework-result__final-score">
          最终得分 <strong>{{ formatScore(submission.finalScore) }}</strong>
        </p>
        <p v-if="submission.manualScore !== null && submission.manualScore !== undefined">
          教师评分：{{ formatScore(submission.manualScore) }}
        </p>
        <div v-if="submission.comment" class="homework-result__message homework-result__message--feedback">
          <strong>教师评语</strong>
          <p>{{ submission.comment }}</p>
        </div>
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import {
  getHomeworkDetail,
  getHomeworkSubmission,
  getHomeworkSubmissionEvaluation,
  listMyHomeworkSubmissions
} from '../../api/hwk/homeworks';
import PageHeader from '../../components/foundation/PageHeader.vue';
import PageState from '../../components/foundation/PageState.vue';
import StatusBadge, { type StatusBadgeTone } from '../../components/foundation/StatusBadge.vue';
import SummaryStrip, { type SummaryStripItem } from '../../components/foundation/SummaryStrip.vue';
import type {
  HomeworkDetail,
  HomeworkEvaluationResult,
  HomeworkEvaluationStatus,
  HomeworkSubmissionDetail,
  HomeworkSubmissionSummary
} from '../../types/hwk';
import {
  formatEvaluationStatus,
  formatReviewStatus,
  formatSubmitStatus
} from './hwkDisplay';

const props = defineProps<{
  courseId: number;
  homeworkId: number;
  submissionId?: number;
}>();

const homework = ref<HomeworkDetail | null>(null);
const submission = ref<HomeworkSubmissionDetail | null>(null);
const evaluation = ref<HomeworkEvaluationResult | null>(null);
const loading = ref(true);
const refreshing = ref(false);
const errorMessage = ref('');
const evaluationErrorMessage = ref('');

let pollTimer: ReturnType<typeof setTimeout> | undefined;
let pollStartedAt = 0;
let consecutivePollFailures = 0;
let loadGeneration = 0;
let evaluationRequestGeneration = 0;
let activePollRequest: number | null = null;
let disposed = false;

const detailHref = computed(() => `/courses/${props.courseId}/homeworks/${props.homeworkId}`);
const historyHref = computed(() => `/courses/${props.courseId}/homeworks/${props.homeworkId}/submissions`);
const canViewEvaluation = computed(() => Boolean(
  homework.value
  && (homework.value.showEvaluationBeforePublish || isPublishedResultStatus(homework.value.status))
));
const showPublishedReview = computed(() => Boolean(
  homework.value
  && isPublishedResultStatus(homework.value.status)
  && submission.value
  && (
    typeof submission.value.finalScore === 'number'
    || typeof submission.value.manualScore === 'number'
    || Boolean(submission.value.comment)
  )
));
const currentEvaluationStatus = computed<HomeworkEvaluationStatus>(() => (
  evaluation.value?.evaluationStatus ?? submission.value?.evaluationStatus ?? 'NONE'
));
const isEvaluationPending = computed(() => isPollableStatus(currentEvaluationStatus.value));
const resultStatusLabel = computed(() => (
  canViewEvaluation.value ? formatEvaluationStatus(currentEvaluationStatus.value) : '结果待发布'
));
const resultStatusTone = computed<StatusBadgeTone>(() => statusTone(currentEvaluationStatus.value, canViewEvaluation.value));
const canRefreshEvaluation = computed(() => Boolean(
  canViewEvaluation.value
  && submission.value
  && submission.value.evaluationStatus !== 'NONE'
));
const errorState = computed<'error' | 'forbidden'>(() => (
  /(?:403|无权限|权限不足|禁止访问|(?:access|permission)\s+denied|forbidden)/i.test(errorMessage.value)
    ? 'forbidden'
    : 'error'
));
const summaryItems = computed<SummaryStripItem[]>(() => {
  if (!submission.value) {
    return [];
  }
  const items: SummaryStripItem[] = [
    {
      key: 'version',
      label: '提交版本',
      value: `版本 ${submission.value.version}`,
      hint: `#${submission.value.submissionId}`,
      tone: 'brand'
    },
    {
      key: 'submit-status',
      label: '提交状态',
      value: formatSubmitStatus(submission.value.submitStatus),
      hint: submission.value.final ? '当前有效版本' : '历史版本',
      tone: submission.value.submitStatus === 'REJECTED' ? 'danger' : 'success'
    },
    {
      key: 'evaluation-status',
      label: '评测状态',
      value: resultStatusLabel.value,
      hint: canViewEvaluation.value ? '当前可见状态' : '等待教师发布',
      tone: summaryTone(currentEvaluationStatus.value, canViewEvaluation.value)
    },
    {
      key: 'submitted-at',
      label: '提交时间',
      value: formatDateTime(submission.value.submittedAt),
      hint: submission.value.language ? `语言：${submission.value.language}` : undefined
    }
  ];
  if (
    homework.value
    && isPublishedResultStatus(homework.value.status)
    && submission.value.finalScore !== null
    && submission.value.finalScore !== undefined
  ) {
    items.push({
      key: 'final-score',
      label: '最终成绩',
      value: formatScore(submission.value.finalScore),
      hint: '已由教师发布',
      tone: 'success'
    });
  }
  return items;
});

watch(
  () => [props.courseId, props.homeworkId, props.submissionId] as const,
  () => { void loadPage(); },
  { immediate: true }
);
onBeforeUnmount(() => {
  disposed = true;
  loadGeneration += 1;
  evaluationRequestGeneration += 1;
  activePollRequest = null;
  stopPolling();
});

async function loadPage() {
  const generation = ++loadGeneration;
  evaluationRequestGeneration += 1;
  activePollRequest = null;
  stopPolling();
  loading.value = true;
  errorMessage.value = '';
  evaluationErrorMessage.value = '';
  homework.value = null;
  submission.value = null;
  evaluation.value = null;
  try {
    const loadedHomework = await getHomeworkDetail(props.homeworkId);
    if (!isCurrentGeneration(generation)) {
      return;
    }
    homework.value = loadedHomework;

    const selectedSubmissionId = props.submissionId ?? await latestSubmissionId(generation);
    if (!isCurrentGeneration(generation) || selectedSubmissionId === null) {
      return;
    }

    const loadedSubmission = await getHomeworkSubmission(selectedSubmissionId);
    if (!isCurrentGeneration(generation)) {
      return;
    }
    if (loadedSubmission.homeworkId !== props.homeworkId) {
      throw new Error('该提交记录不属于当前作业');
    }
    submission.value = loadedSubmission;

    if (canViewEvaluation.value && loadedSubmission.evaluationStatus !== 'NONE') {
      const requestGeneration = ++evaluationRequestGeneration;
      const result = await getHomeworkSubmissionEvaluation(selectedSubmissionId);
      if (
        !isCurrentGeneration(generation)
        || !isCurrentEvaluationRequest(requestGeneration, selectedSubmissionId)
      ) {
        return;
      }
      evaluation.value = result;
      syncSubmissionEvaluationStatus();
    }
  } catch (error) {
    if (isCurrentGeneration(generation)) {
      errorMessage.value = errorText(error, '评测结果暂时无法加载，请稍后重试。');
    }
  } finally {
    if (isCurrentGeneration(generation)) {
      loading.value = false;
      beginPollingIfNeeded();
    }
  }
}

async function latestSubmissionId(generation: number) {
  const history = await listMyHomeworkSubmissions(props.homeworkId);
  if (!isCurrentGeneration(generation)) {
    return null;
  }
  return selectLatestSubmission(history)?.submissionId ?? null;
}

function beginPollingIfNeeded() {
  if (!evaluation.value || !isPollableStatus(evaluation.value.evaluationStatus)) {
    return;
  }
  pollStartedAt = Date.now();
  consecutivePollFailures = 0;
  scheduleNextPoll();
}

function scheduleNextPoll() {
  stopPolling();
  if (disposed || !evaluation.value || !isPollableStatus(evaluation.value.evaluationStatus)) {
    return;
  }
  const elapsed = Date.now() - pollStartedAt;
  if (elapsed >= 60_000) {
    evaluationErrorMessage.value = '评测仍在进行，自动刷新已暂停，请稍后手动刷新。';
    return;
  }
  const delay = elapsed < 10_000 ? 1_000 : elapsed < 30_000 ? 2_000 : 5_000;
  pollTimer = setTimeout(() => {
    void pollEvaluation();
  }, delay);
}

async function pollEvaluation() {
  pollTimer = undefined;
  const selected = submission.value;
  if (disposed || !selected || !canViewEvaluation.value || activePollRequest !== null) {
    return;
  }
  const requestGeneration = ++evaluationRequestGeneration;
  activePollRequest = requestGeneration;
  try {
    const result = await getHomeworkSubmissionEvaluation(selected.submissionId);
    if (!isCurrentEvaluationRequest(requestGeneration, selected.submissionId)) {
      return;
    }
    evaluation.value = result;
    consecutivePollFailures = 0;
    evaluationErrorMessage.value = '';
    syncSubmissionEvaluationStatus();
    if (isPollableStatus(result.evaluationStatus)) {
      scheduleNextPoll();
    }
  } catch (error) {
    if (!isCurrentEvaluationRequest(requestGeneration, selected.submissionId)) {
      return;
    }
    consecutivePollFailures += 1;
    if (consecutivePollFailures < 3) {
      scheduleNextPoll();
      return;
    }
    evaluationErrorMessage.value = `${errorText(error, '评测状态刷新失败。')} 自动重试已暂停，请手动刷新。`;
  } finally {
    if (activePollRequest === requestGeneration) {
      activePollRequest = null;
    }
  }
}

async function manualRefreshEvaluation() {
  if (!submission.value || refreshing.value) {
    return;
  }
  stopPolling();
  const selectedSubmissionId = submission.value.submissionId;
  const requestGeneration = ++evaluationRequestGeneration;
  activePollRequest = null;
  refreshing.value = true;
  evaluationErrorMessage.value = '';
  try {
    const result = await getHomeworkSubmissionEvaluation(selectedSubmissionId);
    if (!isCurrentEvaluationRequest(requestGeneration, selectedSubmissionId)) {
      return;
    }
    evaluation.value = result;
    syncSubmissionEvaluationStatus();
    if (evaluation.value && isPollableStatus(evaluation.value.evaluationStatus)) {
      pollStartedAt = Date.now();
      consecutivePollFailures = 0;
      scheduleNextPoll();
    }
  } catch (error) {
    if (isCurrentEvaluationRequest(requestGeneration, selectedSubmissionId)) {
      evaluationErrorMessage.value = errorText(error, '评测状态刷新失败，请稍后重试。');
    }
  } finally {
    refreshing.value = false;
  }
}

function syncSubmissionEvaluationStatus() {
  if (!submission.value || !evaluation.value) {
    return;
  }
  submission.value = {
    ...submission.value,
    evaluationStatus: evaluation.value.evaluationStatus,
    autoScore: evaluation.value.score
  };
}

function selectLatestSubmission(history: HomeworkSubmissionSummary[]) {
  return [...history].sort((left, right) => {
    const versionDifference = right.version - left.version;
    const timeDifference = timestamp(right.submittedAt) - timestamp(left.submittedAt);
    return versionDifference || timeDifference || right.submissionId - left.submissionId;
  })[0] ?? null;
}

function timestamp(value: string) {
  const parsed = new Date(value).getTime();
  return Number.isFinite(parsed) ? parsed : 0;
}

function isCurrentGeneration(generation: number) {
  return !disposed && generation === loadGeneration;
}

function isCurrentEvaluationRequest(generation: number, submissionId: number) {
  return !disposed
    && generation === evaluationRequestGeneration
    && submission.value?.submissionId === submissionId;
}

function isPollableStatus(status: HomeworkEvaluationStatus) {
  return status === 'PENDING' || status === 'RUNNING';
}

function isPublishedResultStatus(status: HomeworkDetail['status']) {
  return status === 'SCORE_PUBLISHED' || status === 'ARCHIVED';
}

function stopPolling() {
  if (pollTimer !== undefined) {
    clearTimeout(pollTimer);
    pollTimer = undefined;
  }
}

function statusTone(status: HomeworkEvaluationStatus, visible: boolean): StatusBadgeTone {
  if (!visible || status === 'NONE') {
    return 'neutral';
  }
  if (status === 'PENDING' || status === 'RUNNING') {
    return 'info';
  }
  if (status === 'ACCEPTED') {
    return 'success';
  }
  if (status === 'SYSTEM_ERROR') {
    return 'warning';
  }
  return 'danger';
}

function summaryTone(status: HomeworkEvaluationStatus, visible: boolean): SummaryStripItem['tone'] {
  const tone = statusTone(status, visible);
  if (tone === 'info') {
    return 'brand';
  }
  return tone === 'neutral' ? 'neutral' : tone;
}

function formatScore(value: number) {
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

function formatDuration(value: number | null | undefined) {
  return value === null || value === undefined ? '尚未生成' : `${value} ms`;
}

function formatDateTime(value: string) {
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(parsed);
}

function errorText(error: unknown, fallback: string) {
  return error instanceof Error && error.message.trim() ? error.message : fallback;
}
</script>

<style scoped>
.homework-result {
  display: grid;
  gap: 16px;
  min-height: 100vh;
  padding-bottom: 40px;
  color: var(--oj-ink);
}

.homework-result__link,
.homework-result button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 9px 14px;
  border: 1px solid var(--oj-line-strong);
  border-radius: var(--oj-radius-control);
  background: rgba(255, 255, 255, 0.72);
  color: var(--oj-brand);
  font: inherit;
  font-weight: 800;
  text-decoration: none;
  cursor: pointer;
}

.homework-result__link--primary,
.homework-result button {
  border-color: var(--oj-brand);
  background: var(--oj-brand);
  color: #fff;
}

.homework-result button:disabled {
  cursor: wait;
  opacity: 0.62;
}

.homework-result__workspace {
  display: grid;
  gap: 18px;
  padding: 22px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  box-shadow: var(--oj-shadow-soft);
  backdrop-filter: var(--oj-blur);
}

.homework-result__section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 15px;
  border-bottom: 1px solid var(--oj-line);
}

.homework-result__section-heading h2,
.homework-result__section-heading p,
.homework-result__notice p,
.homework-result__pending p,
.homework-result__message p,
.homework-result__review p,
.homework-result__poll-error {
  margin: 0;
}

.homework-result__section-heading h2 {
  margin-top: 4px;
  font-size: 1.2rem;
}

.homework-result__eyebrow {
  color: var(--oj-brand);
  font-size: 0.76rem;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.homework-result__notice,
.homework-result__pending,
.homework-result__message,
.homework-result__poll-error {
  padding: 16px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius-control);
  background: var(--oj-brand-soft);
}

.homework-result__notice,
.homework-result__message,
.homework-result__pending > div {
  display: grid;
  gap: 6px;
}

.homework-result__notice p,
.homework-result__pending p,
.homework-result__message p {
  color: var(--oj-ink-soft);
  line-height: 1.65;
}

.homework-result__evaluation {
  display: grid;
  gap: 16px;
}

.homework-result__pending {
  display: flex;
  align-items: center;
  gap: 13px;
}

.homework-result__spinner {
  flex: 0 0 auto;
  width: 20px;
  height: 20px;
  border: 3px solid rgba(22, 66, 60, 0.2);
  border-top-color: var(--oj-brand);
  border-radius: 50%;
  animation: homework-result-spin 0.8s linear infinite;
}

.homework-result__score-card {
  display: flex;
  align-items: baseline;
  gap: 9px;
  padding: 20px;
  border-radius: var(--oj-radius);
  background: var(--oj-brand-soft);
}

.homework-result__score-card span,
.homework-result__score-card small {
  color: var(--oj-ink-soft);
  font-weight: 700;
}

.homework-result__score-card strong {
  color: var(--oj-brand);
  font-size: clamp(2rem, 5vw, 3.5rem);
  line-height: 1;
}

.homework-result__facts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.homework-result__facts div {
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius-control);
  background: rgba(255, 255, 255, 0.58);
}

.homework-result__facts dt {
  color: var(--oj-muted);
  font-size: 0.76rem;
  font-weight: 800;
}

.homework-result__facts dd {
  margin: 6px 0 0;
  color: var(--oj-ink);
  font-weight: 800;
  overflow-wrap: anywhere;
}

.homework-result__message--error,
.homework-result__poll-error {
  border-color: rgba(143, 45, 36, 0.24);
  background: rgba(190, 49, 49, 0.09);
  color: #8f2d24;
}

.homework-result__log {
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius-control);
  overflow: hidden;
}

.homework-result__log summary {
  padding: 12px 14px;
  color: var(--oj-brand);
  font-weight: 800;
  cursor: pointer;
}

.homework-result__log pre {
  max-height: 300px;
  margin: 0;
  padding: 14px;
  overflow: auto;
  background: #17242c;
  color: #f5f8f7;
  font: 0.82rem/1.6 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  white-space: pre-wrap;
  word-break: break-word;
}

.homework-result__refresh-row {
  display: flex;
  justify-content: flex-end;
}

.homework-result__review {
  background: color-mix(in srgb, var(--oj-brand-soft) 55%, var(--oj-surface));
}

.homework-result__final-score {
  font-size: 1.05rem;
}

.homework-result__final-score strong {
  color: var(--oj-brand);
  font-size: 1.65rem;
}

@keyframes homework-result-spin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .homework-result__spinner { animation: none; }
}

@media (max-width: 760px) {
  .homework-result__facts { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 520px) {
  .homework-result { gap: 12px; }
  .homework-result__workspace { padding: 17px; }
  .homework-result__section-heading { align-items: stretch; flex-direction: column; }
  .homework-result__facts { grid-template-columns: minmax(0, 1fr); }
  .homework-result__refresh-row button { width: 100%; }
}
</style>
