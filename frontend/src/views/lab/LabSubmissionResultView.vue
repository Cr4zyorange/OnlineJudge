<template>
  <main class="lab-result">
    <PageHeader
      v-if="lab"
      eyebrow="学生实验台 · 评测结果"
      :title="lab.title"
      :subtitle="submission ? `正在查看版本 ${submission.version} 的提交回执与评测结果。` : '查看实验提交的评测进度与已发布成绩。'"
    >
      <template #actions>
        <RouterLink class="lab-result__link" :to="detailHref">返回实验详情</RouterLink>
        <RouterLink class="lab-result__link lab-result__link--primary" :to="historyHref">
          查看提交历史
        </RouterLink>
      </template>
    </PageHeader>

    <PageState
      v-if="loading"
      state="loading"
      title="正在加载实验结果"
      message="正在同步实验、提交回执和评测状态。"
    />

    <PageState
      v-else-if="errorMessage"
      :state="pageErrorState"
      :title="pageErrorState === 'forbidden' ? '暂无权限查看该结果' : '实验结果加载失败'"
      :message="errorMessage"
      :retry-label="pageErrorState === 'forbidden' ? undefined : '重试'"
      @retry="loadPage"
    >
      <template #actions>
        <RouterLink class="lab-result__link" :to="detailHref">返回实验详情</RouterLink>
      </template>
    </PageState>

    <PageState
      v-else-if="!submission"
      state="empty"
      title="还没有可查看的实验结果"
      message="完成一次实验提交后，这里会展示评测进度和发布后的成绩。"
    >
      <template #actions>
        <RouterLink class="lab-result__link lab-result__link--primary" :to="detailHref">
          返回实验详情
        </RouterLink>
      </template>
    </PageState>

    <template v-else>
      <SummaryStrip :items="summaryItems" aria-label="实验提交结果摘要" />

      <section class="lab-result__workspace" aria-label="实验评测结果">
        <header class="lab-result__section-heading">
          <div>
            <p class="lab-result__eyebrow">提交回执</p>
            <h2>版本 {{ submission.version }} 的结果</h2>
          </div>
          <StatusBadge
            :label="formatLabEvaluationStatus(currentEvaluationStatus)"
            :tone="labEvaluationStatusTone(currentEvaluationStatus)"
            :title="formatLabEvaluationStatus(currentEvaluationStatus)"
          />
        </header>

        <section
          v-if="!canViewPublishedScores"
          class="lab-result__notice"
          data-testid="result-hidden"
          role="status"
        >
          <strong>成绩尚未发布</strong>
          <p>自动评测进度可正常查看；最终得分、报告评分和教师评语将在成绩发布后展示。</p>
        </section>

        <section
          v-if="currentEvaluationStatus === 'NONE'"
          class="lab-result__notice"
          data-testid="no-evaluation"
          role="status"
        >
          <strong>本次提交暂无自动评测结果</strong>
          <p>如果实验需要教师批阅，请等待评分状态更新。</p>
        </section>

        <section v-else-if="evaluation" class="lab-result__evaluation">
          <div
            v-if="isEvaluationPending"
            class="lab-result__pending"
            role="status"
            aria-live="polite"
          >
            <span class="lab-result__spinner" aria-hidden="true" />
            <div>
              <strong>{{ formatLabEvaluationStatus(evaluation.evaluationStatus) }}</strong>
              <p>页面会在有限时间内自动更新，也可以手动刷新当前状态。</p>
            </div>
          </div>

          <template v-else>
            <div class="lab-result__score-card" data-testid="evaluation-score">
              <span>自动评测得分</span>
              <strong>{{ formatLabScore(evaluation.score) }}</strong>
              <small v-if="lab">/ {{ formatLabScore(lab.maxScore) }}</small>
            </div>

            <dl class="lab-result__facts">
              <div>
                <dt>通过用例</dt>
                <dd>{{ evaluation.passedCases }} / {{ evaluation.totalCases }}</dd>
              </div>
              <div>
                <dt>提交时间</dt>
                <dd>{{ formatLabDateTime(evaluation.submittedAt || submission.submittedAt) }}</dd>
              </div>
              <div>
                <dt>完成时间</dt>
                <dd>{{ evaluation.finishedAt ? formatLabDateTime(evaluation.finishedAt) : '尚未完成' }}</dd>
              </div>
            </dl>

            <div v-if="evaluation.message" class="lab-result__message lab-result__message--feedback">
              <strong>评测反馈</strong>
              <p>{{ evaluation.message }}</p>
            </div>

            <section
              v-if="failedCaseResults.length"
              class="lab-result__case-group lab-result__case-group--failed"
              data-testid="case-group-failed"
              aria-label="未通过的公开用例"
            >
              <header>
                <h3>需要检查的公开用例</h3>
                <span>{{ failedCaseResults.length }} 项</span>
              </header>
              <article
                v-for="(caseItem, index) in failedCaseResults"
                :key="`failed-${caseItem.orderNum}-${index}`"
                class="lab-result__case"
              >
                <div class="lab-result__case-heading">
                  <strong>用例 {{ caseItem.orderNum }}</strong>
                  <span>{{ formatLabScore(caseItem.score) }}</span>
                </div>
                <p v-if="caseItem.message">{{ caseItem.message }}</p>
                <dl class="lab-result__case-io">
                  <div>
                    <dt>输入</dt>
                    <dd><code>{{ caseItem.input || '（空输入）' }}</code></dd>
                  </div>
                  <div>
                    <dt>期望输出</dt>
                    <dd><code>{{ caseItem.expectedOutput || '（空输出）' }}</code></dd>
                  </div>
                  <div>
                    <dt>实际输出</dt>
                    <dd><code>{{ caseItem.actualOutput || '（空输出）' }}</code></dd>
                  </div>
                </dl>
              </article>
            </section>

            <section
              v-if="passedCaseResults.length"
              class="lab-result__case-group lab-result__case-group--passed"
              data-testid="case-group-passed"
              aria-label="已通过的公开用例"
            >
              <header>
                <h3>已通过的公开用例</h3>
                <span>{{ passedCaseResults.length }} 项</span>
              </header>
              <article
                v-for="(caseItem, index) in passedCaseResults"
                :key="`passed-${caseItem.orderNum}-${index}`"
                class="lab-result__case"
              >
                <div class="lab-result__case-heading">
                  <strong>用例 {{ caseItem.orderNum }}</strong>
                  <span>{{ formatLabScore(caseItem.score) }}</span>
                </div>
                <p v-if="caseItem.message">{{ caseItem.message }}</p>
                <details>
                  <summary>查看公开输入与输出</summary>
                  <dl class="lab-result__case-io">
                    <div>
                      <dt>输入</dt>
                      <dd><code>{{ caseItem.input || '（空输入）' }}</code></dd>
                    </div>
                    <div>
                      <dt>期望输出</dt>
                      <dd><code>{{ caseItem.expectedOutput || '（空输出）' }}</code></dd>
                    </div>
                    <div>
                      <dt>实际输出</dt>
                      <dd><code>{{ caseItem.actualOutput || '（空输出）' }}</code></dd>
                    </div>
                  </dl>
                </details>
              </article>
            </section>
          </template>
        </section>

        <p v-if="evaluationErrorMessage" class="lab-result__poll-error" role="alert">
          {{ evaluationErrorMessage }}
        </p>

        <div v-if="canRefreshEvaluation" class="lab-result__refresh-row">
          <button type="button" :disabled="refreshing" @click="manualRefreshEvaluation">
            {{ refreshing ? '正在刷新…' : '手动刷新评测状态' }}
          </button>
        </div>
      </section>

      <section
        v-if="showPublishedReview"
        class="lab-result__workspace lab-result__review"
        data-testid="published-review"
        aria-label="已发布的实验成绩"
      >
        <header class="lab-result__section-heading">
          <div>
            <p class="lab-result__eyebrow">教师批阅</p>
            <h2>已发布的最终成绩</h2>
          </div>
          <StatusBadge label="成绩已发布" tone="success" />
        </header>

        <div
          v-if="!hasPublishedReview"
          class="lab-result__notice"
          data-testid="published-review-empty"
          role="status"
        >
          <strong>成绩已发布，当前提交暂无评分</strong>
          <p>若对评分状态有疑问，请联系任课教师核对。</p>
        </div>

        <div v-else class="lab-result__review-grid">
          <p v-if="publishedFinalScore !== null" class="lab-result__final-score">
            最终得分 <strong>{{ formatLabScore(publishedFinalScore) }}</strong>
          </p>
          <p v-if="publishedReportScore !== null">
            报告评分 <strong>{{ formatLabScore(publishedReportScore) }}</strong>
          </p>
          <p v-if="publishedManualScore !== null">
            教师评分 <strong>{{ formatLabScore(publishedManualScore) }}</strong>
          </p>
        </div>

        <div v-if="publishedComment" class="lab-result__message lab-result__message--feedback">
          <strong>教师评语</strong>
          <p>{{ publishedComment }}</p>
        </div>
        <div
          v-if="publishedReportComment && publishedReportComment !== publishedComment"
          class="lab-result__message lab-result__message--feedback"
        >
          <strong>实验报告评语</strong>
          <p>{{ publishedReportComment }}</p>
        </div>
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import {
  getLabDetail,
  getLabResult,
  getLabSubmissionDetail,
  getLabSubmissionResult,
  listLabSubmissions
} from '../../api/lab/labs';
import { currentUser } from '../../app/runtimeContext';
import PageHeader from '../../components/foundation/PageHeader.vue';
import PageState from '../../components/foundation/PageState.vue';
import StatusBadge, { type StatusBadgeTone } from '../../components/foundation/StatusBadge.vue';
import SummaryStrip, { type SummaryStripItem } from '../../components/foundation/SummaryStrip.vue';
import type {
  LabEvaluationCaseResult,
  LabExperimentDetail,
  LabExperimentStatus,
  LabReportSummary,
  LabResult,
  LabScoreSummary,
  LabSubmissionDetail,
  LabSubmissionHistoryItem,
  LabSubmissionId,
  LabSubmissionResult,
  LabSubmissionSummary
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
  submissionId?: LabSubmissionId;
}>();

type LabEvaluationStatus = LabSubmissionSummary['evaluationStatus'];
type PageErrorState = 'error' | 'forbidden';

const lab = ref<LabExperimentDetail | null>(null);
const submission = ref<LabSubmissionDetail | null>(null);
const evaluation = ref<LabSubmissionResult | null>(null);
const score = ref<LabScoreSummary | null>(null);
const report = ref<LabReportSummary | null>(null);
const resultVisibilityStatus = ref<LabExperimentStatus | null>(null);
const loading = ref(true);
const refreshing = ref(false);
const errorMessage = ref('');
const pageErrorState = ref<PageErrorState>('error');
const evaluationErrorMessage = ref('');

let pollTimer: ReturnType<typeof setTimeout> | undefined;
let pollRequestWatchdogTimer: ReturnType<typeof setTimeout> | undefined;
let pollStartedAt = 0;
let consecutivePollFailures = 0;
let loadGeneration = 0;
let evaluationRequestGeneration = 0;
let activePollRequest: number | null = null;
let disposed = false;

const detailHref = computed(() => `/courses/${props.courseId}/labs/${props.labId}`);
const historyHref = computed(() => `/courses/${props.courseId}/labs/${props.labId}/submissions`);
const currentEvaluationStatus = computed<LabEvaluationStatus>(() => (
  evaluation.value?.evaluationStatus ?? submission.value?.evaluationStatus ?? 'NONE'
));
const isEvaluationPending = computed(() => isPollableStatus(currentEvaluationStatus.value));
const canViewPublishedScores = computed(() => Boolean(
  lab.value
  && resultVisibilityStatus.value
  && isScorePublished(lab.value.status)
  && isScorePublished(resultVisibilityStatus.value)
));
const canRefreshEvaluation = computed(() => Boolean(
  submission.value && currentEvaluationStatus.value !== 'NONE'
));
const passedCaseResults = computed(() => publicCaseResults(true));
const failedCaseResults = computed(() => publicCaseResults(false));
const publishedFinalScore = computed<number | null>(() => {
  if (!canViewPublishedScores.value) {
    return null;
  }
  return score.value?.finalScore ?? submission.value?.finalScore ?? null;
});
const publishedReportScore = computed<number | null>(() => {
  if (!canViewPublishedScores.value) {
    return null;
  }
  return score.value?.reportScore ?? report.value?.score ?? null;
});
const publishedManualScore = computed<number | null>(() => (
  canViewPublishedScores.value ? score.value?.manualScore ?? null : null
));
const publishedComment = computed(() => (
  canViewPublishedScores.value ? score.value?.comment?.trim() || '' : ''
));
const publishedReportComment = computed(() => (
  canViewPublishedScores.value ? report.value?.comment?.trim() || '' : ''
));
const hasPublishedReview = computed(() => Boolean(
  publishedFinalScore.value !== null
  || publishedReportScore.value !== null
  || publishedManualScore.value !== null
  || publishedComment.value
  || publishedReportComment.value
));
const showPublishedReview = computed(() => canViewPublishedScores.value);
const summaryItems = computed<SummaryStripItem[]>(() => {
  const selected = submission.value;
  if (!selected) {
    return [];
  }

  const items: SummaryStripItem[] = [
    {
      key: 'version',
      label: '提交版本',
      value: `版本 ${selected.version}`,
      hint: selected.isLatest ? '最新版本' : '历史版本',
      tone: 'brand'
    },
    {
      key: 'submit-status',
      label: '提交状态',
      value: formatLabSubmitStatus(selected.submitStatus),
      hint: selected.isScoringBasis ? '当前评分依据' : undefined,
      tone: summaryTone(labSubmitStatusTone(selected.submitStatus))
    },
    {
      key: 'evaluation-status',
      label: '评测状态',
      value: formatLabEvaluationStatus(currentEvaluationStatus.value),
      hint: isEvaluationPending.value ? '正在自动更新' : '当前可见状态',
      tone: summaryTone(labEvaluationStatusTone(currentEvaluationStatus.value))
    },
    {
      key: 'submitted-at',
      label: '提交时间',
      value: formatLabDateTime(selected.submittedAt),
      hint: `语言：${formatLabLanguage(selected.language)}`
    }
  ];

  if (publishedFinalScore.value !== null) {
    items.push({
      key: 'final-score',
      label: '最终成绩',
      value: formatLabScore(publishedFinalScore.value),
      hint: '已由教师发布',
      tone: 'success'
    });
  }
  return items;
});

watch(
  () => [props.courseId, props.labId, props.submissionId] as const,
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
  refreshing.value = false;
  errorMessage.value = '';
  evaluationErrorMessage.value = '';
  pageErrorState.value = 'error';
  lab.value = null;
  submission.value = null;
  evaluation.value = null;
  score.value = null;
  report.value = null;
  resultVisibilityStatus.value = null;

  try {
    const loadedLab = await getLabDetail(props.labId);
    if (!isCurrentGeneration(generation)) {
      return;
    }
    validateLab(loadedLab);
    lab.value = loadedLab;

    if (props.submissionId !== undefined) {
      await loadHistoricResult(generation, props.submissionId, loadedLab.status);
    } else {
      await loadLatestResult(generation);
    }
  } catch (error) {
    if (isCurrentGeneration(generation)) {
      pageErrorState.value = isForbiddenError(error) ? 'forbidden' : 'error';
      errorMessage.value = localizedLabError(
        error,
        pageErrorState.value === 'forbidden'
          ? '当前账号无权查看该实验结果。'
          : '实验结果暂时无法加载，请稍后重试。'
      );
    }
  } finally {
    if (isCurrentGeneration(generation)) {
      loading.value = false;
      beginPollingIfNeeded();
    }
  }
}

async function loadLatestResult(generation: number) {
  const studentId = currentUser.value?.id;
  if (studentId === undefined || studentId === null) {
    throw new Error('无法确认当前学生身份，请重新登录后重试。');
  }
  const history = await listLabSubmissions(props.labId);
  if (!isCurrentGeneration(generation)) {
    return;
  }
  validateLatestHistory(history, studentId);
  const selected = selectLatestSubmission(history);
  if (!selected) {
    return;
  }

  const aggregate = await getLabResult(props.labId, studentId);
  if (!isCurrentGeneration(generation)) {
    return;
  }
  validateAggregateResult(aggregate, selected);
  submission.value = aggregate.submission;
  evaluation.value = aggregate.evaluationResult;
  score.value = aggregate.latestScore ?? aggregate.submission.latestScore ?? null;
  report.value = aggregate.latestReport ?? aggregate.submission.latestReport;
  resultVisibilityStatus.value = aggregate.status;
}

async function loadHistoricResult(
  generation: number,
  selectedSubmissionId: LabSubmissionId,
  labStatus: LabExperimentStatus
) {
  const [loadedSubmission, loadedEvaluation] = await Promise.all([
    getLabSubmissionDetail(props.labId, selectedSubmissionId),
    getLabSubmissionResult(props.labId, selectedSubmissionId)
  ]);
  if (!isCurrentGeneration(generation)) {
    return;
  }
  validateHistoricResult(loadedSubmission, loadedEvaluation, selectedSubmissionId);
  submission.value = loadedSubmission;
  evaluation.value = loadedEvaluation;
  score.value = loadedSubmission.latestScore ?? null;
  report.value = loadedSubmission.latestReport;
  resultVisibilityStatus.value = labStatus;
}

function beginPollingIfNeeded() {
  if (!submission.value || !evaluation.value || !isPollableStatus(evaluation.value.evaluationStatus)) {
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
  const phaseDelay = elapsed < 10_000 ? 1_000 : elapsed < 30_000 ? 2_000 : 5_000;
  const delay = Math.min(phaseDelay, 60_000 - elapsed);
  pollTimer = setTimeout(() => {
    pollTimer = undefined;
    if (Date.now() - pollStartedAt >= 60_000) {
      evaluationErrorMessage.value = '评测仍在进行，自动刷新已暂停，请稍后手动刷新。';
      return;
    }
    void pollEvaluation();
  }, delay);
}

async function pollEvaluation() {
  pollTimer = undefined;
  const selected = submission.value;
  if (disposed || !selected || activePollRequest !== null) {
    return;
  }
  const selectedSubmissionId = selected.submissionId;
  const requestGeneration = ++evaluationRequestGeneration;
  activePollRequest = requestGeneration;

  try {
    const remaining = Math.max(0, 60_000 - (Date.now() - pollStartedAt));
    const loadedEvaluation = await requestEvaluationWithWatchdog(
      selectedSubmissionId,
      requestGeneration,
      remaining,
      '评测仍在进行，自动刷新已暂停，请稍后手动刷新。'
    );
    if (!loadedEvaluation) {
      return;
    }
    if (!isCurrentEvaluationRequest(requestGeneration, selectedSubmissionId)) {
      return;
    }
    validateEvaluationResult(loadedEvaluation, selectedSubmissionId);
    applyEvaluationResult(loadedEvaluation);
    consecutivePollFailures = 0;
    evaluationErrorMessage.value = '';
    if (isPollableStatus(loadedEvaluation.evaluationStatus)) {
      scheduleNextPoll();
    }
  } catch (error) {
    if (!isCurrentEvaluationRequest(requestGeneration, selectedSubmissionId)) {
      return;
    }
    consecutivePollFailures += 1;
    if (consecutivePollFailures < 3) {
      scheduleNextPoll();
      return;
    }
    evaluationErrorMessage.value = `${localizedLabError(error, '评测状态刷新失败。')} 自动重试已暂停，请手动刷新。`;
  } finally {
    if (activePollRequest === requestGeneration) {
      activePollRequest = null;
    }
  }
}

async function manualRefreshEvaluation() {
  const selected = submission.value;
  if (!selected || refreshing.value) {
    return;
  }
  stopPolling();
  const selectedSubmissionId = selected.submissionId;
  const requestGeneration = ++evaluationRequestGeneration;
  activePollRequest = null;
  refreshing.value = true;
  evaluationErrorMessage.value = '';

  try {
    const loadedEvaluation = await requestEvaluationWithWatchdog(
      selectedSubmissionId,
      requestGeneration,
      60_000,
      '评测状态刷新超过 60 秒，请稍后手动刷新。'
    );
    if (!loadedEvaluation) {
      return;
    }
    if (!isCurrentEvaluationRequest(requestGeneration, selectedSubmissionId)) {
      return;
    }
    validateEvaluationResult(loadedEvaluation, selectedSubmissionId);
    applyEvaluationResult(loadedEvaluation);
    if (isPollableStatus(loadedEvaluation.evaluationStatus)) {
      pollStartedAt = Date.now();
      consecutivePollFailures = 0;
      scheduleNextPoll();
    }
  } catch (error) {
    if (isCurrentEvaluationRequest(requestGeneration, selectedSubmissionId)) {
      evaluationErrorMessage.value = localizedLabError(
        error,
        '评测状态刷新失败，请稍后重试。'
      );
    }
  } finally {
    refreshing.value = false;
  }
}

function applyEvaluationResult(loadedEvaluation: LabSubmissionResult) {
  evaluation.value = loadedEvaluation;
  if (submission.value) {
    submission.value = {
      ...submission.value,
      evaluationStatus: loadedEvaluation.evaluationStatus,
      autoScore: loadedEvaluation.score
    };
  }
}

async function requestEvaluationWithWatchdog(
  selectedSubmissionId: LabSubmissionId,
  requestGeneration: number,
  timeoutMs: number,
  timeoutMessage: string
) {
  const requestTimedOut = Symbol('evaluation-request-timeout');
  let watchdogTimer: ReturnType<typeof setTimeout> | undefined;
  const timeoutResult = new Promise<typeof requestTimedOut>((resolve) => {
    watchdogTimer = setTimeout(() => resolve(requestTimedOut), timeoutMs);
    pollRequestWatchdogTimer = watchdogTimer;
  });

  try {
    const result = await Promise.race([
      getLabSubmissionResult(props.labId, selectedSubmissionId),
      timeoutResult
    ]);
    if (result === requestTimedOut) {
      if (isCurrentEvaluationRequest(requestGeneration, selectedSubmissionId)) {
        evaluationRequestGeneration += 1;
        activePollRequest = null;
        evaluationErrorMessage.value = timeoutMessage;
      }
      return null;
    }
    return result;
  } finally {
    if (watchdogTimer !== undefined) {
      clearTimeout(watchdogTimer);
      if (pollRequestWatchdogTimer === watchdogTimer) {
        pollRequestWatchdogTimer = undefined;
      }
    }
  }
}

function publicCaseResults(passed: boolean): LabEvaluationCaseResult[] {
  return evaluation.value?.caseResults.filter((caseItem) => caseItem.passed === passed) ?? [];
}

function selectLatestSubmission(history: LabSubmissionHistoryItem[]) {
  return [...history].sort((left, right) => {
    const latestDifference = Number(right.isLatest) - Number(left.isLatest);
    const versionDifference = right.version - left.version;
    const timeDifference = timestamp(right.submittedAt) - timestamp(left.submittedAt);
    return latestDifference || versionDifference || timeDifference
      || String(right.submissionId).localeCompare(String(left.submissionId));
  })[0] ?? null;
}

function validateLatestHistory(history: LabSubmissionHistoryItem[], studentId: number) {
  if (currentUser.value?.id !== studentId
    || history.some((item) => item.labId !== props.labId || item.studentId !== studentId)) {
    throw new Error('提交历史与当前实验或学生不匹配，请重新加载。');
  }
}

function validateLab(loadedLab: LabExperimentDetail) {
  if (loadedLab.id !== props.labId || loadedLab.courseId !== props.courseId) {
    throw new Error('实验信息与当前课程不匹配。');
  }
}

function validateAggregateResult(aggregate: LabResult, selected: LabSubmissionHistoryItem) {
  if (
    aggregate.labId !== props.labId
    || aggregate.studentId !== selected.studentId
    || aggregate.submission.labId !== props.labId
    || aggregate.submission.studentId !== selected.studentId
    || aggregate.submission.submissionId !== selected.submissionId
    || aggregate.evaluationResult.submissionId !== selected.submissionId
  ) {
    throw new Error('返回的实验结果与当前提交不匹配。');
  }
}

function validateHistoricResult(
  loadedSubmission: LabSubmissionDetail,
  loadedEvaluation: LabSubmissionResult,
  selectedSubmissionId: LabSubmissionId
) {
  if (
    loadedSubmission.labId !== props.labId
    || loadedSubmission.submissionId !== selectedSubmissionId
    || loadedSubmission.studentId !== currentUser.value?.id
    || loadedEvaluation.submissionId !== selectedSubmissionId
  ) {
    throw new Error('返回的实验结果与当前提交不匹配。');
  }
}

function validateEvaluationResult(loadedEvaluation: LabSubmissionResult, selectedSubmissionId: LabSubmissionId) {
  if (loadedEvaluation.submissionId !== selectedSubmissionId) {
    throw new Error('评测结果与当前提交不匹配。');
  }
}

function timestamp(value: string) {
  const parsed = new Date(value).getTime();
  return Number.isFinite(parsed) ? parsed : 0;
}

function isCurrentGeneration(generation: number) {
  return !disposed && generation === loadGeneration;
}

function isCurrentEvaluationRequest(generation: number, selectedSubmissionId: LabSubmissionId) {
  return !disposed
    && generation === evaluationRequestGeneration
    && submission.value?.submissionId === selectedSubmissionId;
}

function isPollableStatus(status: LabEvaluationStatus) {
  return status === 'PENDING' || status === 'RUNNING';
}

function isScorePublished(status: LabExperimentStatus) {
  return status === 'SCORE_PUBLISHED' || status === 'ARCHIVED';
}

function isForbiddenError(error: unknown) {
  const message = error instanceof Error ? error.message : '';
  return /(?:403|无权限|权限不足|禁止访问|(?:access|permission)\s+denied|forbidden)/i.test(message);
}

function summaryTone(tone: StatusBadgeTone): SummaryStripItem['tone'] {
  if (tone === 'info') {
    return 'brand';
  }
  return tone === 'neutral' ? 'neutral' : tone;
}

function stopPolling() {
  if (pollTimer !== undefined) {
    clearTimeout(pollTimer);
    pollTimer = undefined;
  }
  if (pollRequestWatchdogTimer !== undefined) {
    clearTimeout(pollRequestWatchdogTimer);
    pollRequestWatchdogTimer = undefined;
  }
}
</script>

<style scoped>
.lab-result {
  display: grid;
  gap: 16px;
  min-height: 100vh;
  padding-bottom: 40px;
  color: var(--oj-ink);
}

.lab-result__link,
.lab-result button {
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

.lab-result__link--primary,
.lab-result button {
  border-color: var(--oj-brand);
  background: var(--oj-brand);
  color: #fff;
}

.lab-result button:disabled {
  cursor: wait;
  opacity: 0.62;
}

.lab-result__workspace {
  display: grid;
  gap: 18px;
  padding: 22px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  box-shadow: var(--oj-shadow-soft);
  backdrop-filter: var(--oj-blur);
  -webkit-backdrop-filter: var(--oj-blur);
}

.lab-result__section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 15px;
  border-bottom: 1px solid var(--oj-line);
}

.lab-result__section-heading h2,
.lab-result__section-heading p,
.lab-result__notice p,
.lab-result__pending p,
.lab-result__message p,
.lab-result__case p,
.lab-result__review p,
.lab-result__poll-error {
  margin: 0;
}

.lab-result__section-heading h2 {
  margin-top: 4px;
  font-size: 1.2rem;
}

.lab-result__eyebrow {
  color: var(--oj-brand);
  font-size: 0.76rem;
  font-weight: 800;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.lab-result__notice,
.lab-result__pending,
.lab-result__message,
.lab-result__poll-error {
  padding: 16px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius-control);
  background: var(--oj-brand-soft);
}

.lab-result__notice,
.lab-result__message,
.lab-result__pending > div {
  display: grid;
  gap: 6px;
}

.lab-result__notice p,
.lab-result__pending p,
.lab-result__message p,
.lab-result__case p {
  color: var(--oj-ink-soft);
  line-height: 1.65;
}

.lab-result__evaluation {
  display: grid;
  gap: 16px;
}

.lab-result__pending {
  display: flex;
  align-items: center;
  gap: 13px;
}

.lab-result__spinner {
  flex: 0 0 auto;
  width: 20px;
  height: 20px;
  border: 3px solid rgba(22, 66, 60, 0.2);
  border-top-color: var(--oj-brand);
  border-radius: 50%;
  animation: lab-result-spin 0.8s linear infinite;
}

.lab-result__score-card {
  display: flex;
  align-items: baseline;
  gap: 9px;
  padding: 20px;
  border-radius: var(--oj-radius);
  background: var(--oj-brand-soft);
}

.lab-result__score-card span,
.lab-result__score-card small {
  color: var(--oj-ink-soft);
  font-weight: 700;
}

.lab-result__score-card strong {
  color: var(--oj-brand);
  font-size: clamp(2rem, 5vw, 3.5rem);
  line-height: 1;
}

.lab-result__facts,
.lab-result__case-io {
  display: grid;
  gap: 12px;
  margin: 0;
}

.lab-result__facts {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.lab-result__facts div,
.lab-result__case-io div {
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius-control);
  background: rgba(255, 255, 255, 0.58);
}

.lab-result__facts dt,
.lab-result__case-io dt {
  color: var(--oj-muted);
  font-size: 0.76rem;
  font-weight: 800;
}

.lab-result__facts dd,
.lab-result__case-io dd {
  margin: 6px 0 0;
  color: var(--oj-ink);
  font-weight: 800;
  overflow-wrap: anywhere;
}

.lab-result__case-io code {
  font: 0.82rem/1.6 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  white-space: pre-wrap;
}

.lab-result__case-group {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: rgba(255, 255, 255, 0.48);
}

.lab-result__case-group > header,
.lab-result__case-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.lab-result__case-group h3 {
  margin: 0;
  font-size: 1rem;
}

.lab-result__case-group > header span,
.lab-result__case-heading span {
  color: var(--oj-muted);
  font-size: 0.8rem;
  font-weight: 800;
}

.lab-result__case-group--failed {
  border-color: rgba(143, 45, 36, 0.24);
  background: rgba(190, 49, 49, 0.06);
}

.lab-result__case-group--passed {
  border-color: rgba(22, 66, 60, 0.2);
  background: rgba(22, 66, 60, 0.05);
}

.lab-result__case {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius-control);
  background: var(--oj-surface);
}

.lab-result__case details {
  color: var(--oj-brand);
}

.lab-result__case summary {
  font-weight: 800;
  cursor: pointer;
}

.lab-result__case details .lab-result__case-io {
  margin-top: 12px;
}

.lab-result__message--error,
.lab-result__poll-error {
  border-color: rgba(143, 45, 36, 0.24);
  background: rgba(190, 49, 49, 0.09);
  color: #8f2d24;
}

.lab-result__refresh-row {
  display: flex;
  justify-content: flex-end;
}

.lab-result__review {
  background: color-mix(in srgb, var(--oj-brand-soft) 55%, var(--oj-surface));
}

.lab-result__review-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 12px;
}

.lab-result__review-grid p {
  padding: 15px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius-control);
  background: rgba(255, 255, 255, 0.52);
}

.lab-result__review-grid strong {
  color: var(--oj-brand);
}

.lab-result__final-score strong {
  font-size: 1.65rem;
}

@keyframes lab-result-spin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .lab-result__spinner { animation: none; }
}

@media (max-width: 760px) {
  .lab-result__facts { grid-template-columns: minmax(0, 1fr); }
}

@media (max-width: 520px) {
  .lab-result { gap: 12px; }
  .lab-result__workspace { padding: 17px; }
  .lab-result__section-heading { align-items: stretch; flex-direction: column; }
  .lab-result__refresh-row button { width: 100%; }
}
</style>
