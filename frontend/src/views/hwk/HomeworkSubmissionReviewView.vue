<template>
  <main class="homework-submission-review" data-testid="homework-submission-review">
    <PageHeader
      eyebrow="教师作业台 · 独立批阅"
      :title="pageTitle"
      subtitle="核对一次提交的作业类型、有效版本、自动评测证据与人工评分记录。"
    >
      <template #actions>
        <RouterLink class="review-link" :to="workspaceRoute">返回提交队列</RouterLink>
        <RouterLink class="review-link" :to="manageDetailRoute">返回作业管理</RouterLink>
      </template>
    </PageHeader>

    <PageState
      v-if="pageLoading"
      state="loading"
      title="正在加载批阅内容"
      message="正在同步作业、提交版本、评测结果与批阅日志。"
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

    <template v-else-if="homework && submission">
      <section class="review-context" aria-labelledby="review-context-title">
        <div class="review-context__student">
          <p class="section-eyebrow">批阅对象</p>
          <h2 id="review-context-title" data-testid="review-student-name">{{ studentName }}</h2>
          <p>{{ courseName || '当前课程' }} · {{ homework.title }}</p>
          <p
            v-if="studentNameWarning"
            class="inline-message inline-message--warning"
            data-testid="student-name-warning"
            role="status"
          >{{ studentNameWarning }}</p>
        </div>

        <div class="review-context__statuses">
          <StatusBadge
            :label="formatSubmitStatus(submission.submitStatus)"
            :tone="submitTone(submission.submitStatus)"
          />
          <StatusBadge
            :label="formatEvaluationStatus(submission.evaluationStatus)"
            :tone="evaluationTone(submission.evaluationStatus)"
          />
          <StatusBadge
            :label="formatReviewStatus(submission.reviewStatus)"
            :tone="reviewTone(submission.reviewStatus)"
          />
        </div>

        <div class="version-context" data-testid="review-version-context">
          <strong>版本 {{ submission.version }}</strong>
          <span>{{ submission.final ? '当前有效提交' : '历史提交版本' }}</span>
          <span>{{ formatHomeworkType(effectiveType) }}</span>
          <span>提交于 {{ formatDateTime(submission.submittedAt) }}</span>
        </div>

        <dl class="score-strip" aria-label="分数来源">
          <div data-testid="score-auto"><dt>自动得分</dt><dd>{{ formatScore(submission.autoScore) }}</dd></div>
          <div data-testid="score-manual"><dt>人工得分</dt><dd>{{ formatScore(submission.manualScore) }}</dd></div>
          <div data-testid="score-final"><dt>最终得分</dt><dd>{{ formatScore(submission.finalScore) }}</dd></div>
        </dl>
        <p
          v-if="!reviewMutable"
          class="inline-message inline-message--warning"
          data-testid="review-readonly"
          role="status"
        >{{ readOnlyMessage }}</p>
      </section>

      <div class="review-grid">
        <div class="review-column">
          <section class="review-card" data-testid="submission-answer" aria-labelledby="submission-answer-title">
            <header class="card-heading">
              <div>
                <p class="section-eyebrow">提交内容</p>
                <h2 id="submission-answer-title">{{ answerTitle }}</h2>
              </div>
              <span v-if="effectiveType === 'CODE'">{{ formatLanguage(submission.language) }}</span>
            </header>

            <pre
              v-if="effectiveType === 'CODE' && submission.answerText"
              class="content-panel content-panel--code"
              data-testid="submission-code"
            ><code>{{ submission.answerText }}</code></pre>
            <div
              v-else-if="effectiveType === 'TEXT'"
              class="content-panel content-panel--prose"
            >{{ submission.answerText || '本次提交没有文本内容。' }}</div>
            <div
              v-else-if="effectiveType === 'OBJECTIVE'"
              class="content-panel content-panel--prose"
            >{{ formatObjectiveAnswer(submission.answerJson) }}</div>
            <div
              v-else-if="effectiveType === 'FILE' && submission.attachment"
              class="attachment-panel"
              data-testid="homework-attachment-panel"
            >
              <strong>{{ submission.attachment.originalFilename }}</strong>
              <span>{{ submission.attachment.contentType }} · {{ formatFileSize(submission.attachment.fileSize) }}</span>
              <button
                v-if="submission.attachment.downloadAvailable"
                class="button button--secondary"
                type="button"
                data-action="download-homework-attachment"
                :disabled="attachmentDownloading"
                @click="downloadAttachment"
              >{{ attachmentDownloading ? '正在下载…' : '下载附件' }}</button>
              <p v-else>当前附件暂不可下载。</p>
              <p v-if="attachmentDownloadError" class="inline-message inline-message--error" role="alert">
                {{ attachmentDownloadError }}
              </p>
              <p v-if="attachmentDownloadFeedback" class="inline-message inline-message--success" role="status">
                {{ attachmentDownloadFeedback }}
              </p>
            </div>
            <div v-else-if="effectiveType === 'FILE' && submission.hasAttachment" class="not-applicable" role="status">
              附件元数据暂不可用，当前不能下载；仍可继续完成人工批阅。
            </div>
            <p v-else class="empty-copy">本次提交没有可展示的内容。</p>
          </section>

          <section class="review-card" aria-labelledby="evaluation-title">
            <header class="card-heading">
              <div>
                <p class="section-eyebrow">自动评测</p>
                <h2 id="evaluation-title">评测结果与日志</h2>
              </div>
              <StatusBadge
                v-if="evaluation"
                :label="formatEvaluationStatus(evaluation.evaluationStatus)"
                :tone="evaluationTone(evaluation.evaluationStatus)"
              />
            </header>

            <PageState
              v-if="evaluationLoading"
              state="loading"
              title="正在加载评测结果"
              message="正在同步当前版本的自动评测证据。"
            />
            <PageState
              v-else-if="evaluationError"
              state="error"
              title="评测结果加载失败"
              :message="evaluationError"
              retry-label="重试评测结果"
              @retry="loadEvaluation"
            />
            <template v-else-if="evaluation">
              <dl class="evaluation-summary" data-testid="evaluation-summary">
                <div><dt>评测得分</dt><dd>{{ formatScore(evaluation.score) }}</dd></div>
                <div><dt>通过用例</dt><dd>{{ evaluation.passedCases }} / {{ evaluation.totalCases }}</dd></div>
                <div><dt>运行耗时</dt><dd>{{ formatDuration(evaluation.durationMs) }}</dd></div>
                <div><dt>开始时间</dt><dd>{{ formatDateTime(evaluation.startedAt) }}</dd></div>
                <div><dt>完成时间</dt><dd>{{ formatOptionalDateTime(evaluation.finishedAt) }}</dd></div>
                <div><dt>评测来源</dt><dd>{{ evaluation.reevaluation ? '教师重评' : '首次评测' }}</dd></div>
              </dl>
              <p v-if="evaluation.feedback" class="evaluation-copy">{{ evaluation.feedback }}</p>
              <p v-if="evaluation.errorMessage" class="inline-message inline-message--error">
                {{ evaluation.errorMessage }}
              </p>
              <details v-if="evaluation.compileLog" class="log-panel">
                <summary>编译日志</summary>
                <pre>{{ evaluation.compileLog }}</pre>
              </details>
              <details v-if="evaluation.runLog" class="log-panel">
                <summary>运行日志</summary>
                <pre>{{ evaluation.runLog }}</pre>
              </details>
              <p
                v-if="evaluationRefreshWarning"
                class="inline-message inline-message--warning"
                data-testid="evaluation-refresh-warning"
                role="status"
              >{{ evaluationRefreshWarning }}</p>
            </template>
            <div v-else class="not-applicable">
              当前作业类型不使用自动评测，人工分与最终分由教师批阅确定。
            </div>

            <form
              v-if="canReevaluate && reviewMutable"
              class="action-form"
              data-action="reevaluate-submission"
              @submit.prevent="reevaluateSubmission"
            >
              <label class="field">
                <span>重评理由</span>
                <textarea
                  v-model="reevaluationReason"
                  name="reevaluationReason"
                  rows="3"
                  placeholder="说明触发重评的原因"
                ></textarea>
              </label>
              <p
                v-if="reevaluationError"
                class="inline-message inline-message--error"
                data-testid="reevaluation-error"
                role="alert"
              >{{ reevaluationError }}</p>
              <p
                v-if="reevaluationFeedback"
                class="inline-message"
                :class="reevaluationRefreshRequired ? 'inline-message--warning' : 'inline-message--success'"
                data-testid="reevaluation-feedback"
                role="status"
              >{{ reevaluationFeedback }}</p>
              <button class="button button--secondary" type="submit" :disabled="mutationPending || reevaluationRefreshRequired">
                {{ reevaluationSaving ? '正在提交重评…' : reevaluationRefreshRequired ? '请先重新加载页面' : '确认并触发重评' }}
              </button>
              <button
                v-if="reevaluationRefreshRequired"
                class="button button--quiet"
                type="button"
                @click="loadPage"
              >重新加载批阅页面</button>
            </form>
            <div v-else-if="!reviewMutable" class="not-applicable" role="status">
              {{ readOnlyEvaluationMessage }}
            </div>
            <div
              v-else
              class="not-applicable"
              data-testid="reevaluation-unavailable"
              role="status"
            >{{ formatHomeworkType(effectiveType) }}不支持自动重评；可在右侧直接完成人工批阅。</div>
          </section>

          <section class="review-card" aria-labelledby="review-log-title">
            <header class="card-heading">
              <div>
                <p class="section-eyebrow">变更留痕</p>
                <h2 id="review-log-title">批阅与重评日志</h2>
              </div>
              <button
                class="button button--quiet"
                type="button"
                :disabled="reviewLogsLoading"
                @click="() => loadReviewLogs()"
              >刷新日志</button>
            </header>
            <div v-if="reviewLogsLoading" class="compact-state" role="status">正在加载批阅日志…</div>
            <div v-else-if="reviewLogsError" class="compact-state compact-state--error" role="alert">
              <span>{{ reviewLogsError }}</span>
              <button class="button button--quiet" type="button" @click="() => loadReviewLogs()">重新加载</button>
            </div>
            <p v-else-if="reviewLogs.length === 0" class="empty-copy">当前提交尚无批阅或重评记录。</p>
            <ol v-else class="audit-list" data-testid="review-logs">
              <li v-for="log in reviewLogs" :key="log.id">
                <div>
                  <strong>{{ formatReviewOperation(log.operationType) }}</strong>
                  <span>{{ formatDateTime(log.createdAt) }}</span>
                </div>
                <p>{{ reviewLogNote(log) }}</p>
                <small>{{ scoreChangeLabel(log.oldScore, log.newScore) }}</small>
              </li>
            </ol>
          </section>
        </div>

        <aside class="review-column review-column--grading" aria-label="教师评分区">
          <section class="review-card" aria-labelledby="grading-title">
            <header class="card-heading">
              <div>
                <p class="section-eyebrow">教师批阅</p>
                <h2 id="grading-title">人工评分与最终分</h2>
              </div>
              <span>满分 {{ homework.totalScore }}</span>
            </header>

            <form
              v-if="reviewMutable"
              class="score-form"
              data-action="save-review"
              @submit.prevent="saveReview"
            >
              <div class="score-pair">
                <label class="field">
                  <span>人工得分</span>
                  <input
                    v-model="reviewForm.manualScore"
                    name="manualScore"
                    type="number"
                    min="0"
                    :max="homework.totalScore"
                    step="0.01"
                  >
                </label>
                <label class="field">
                  <span>最终得分</span>
                  <input
                    v-model="reviewForm.finalScore"
                    name="finalScore"
                    type="number"
                    min="0"
                    :max="homework.totalScore"
                    step="0.01"
                  >
                </label>
              </div>
              <label class="field">
                <span>批阅说明 / 理由</span>
                <textarea
                  v-model="reviewForm.reason"
                  name="reviewReason"
                  rows="5"
                  placeholder="说明评分依据；该内容会进入批阅日志"
                ></textarea>
              </label>
              <p class="form-hint">保存前会再次确认学生、版本和分数；失败时当前输入不会丢失。</p>
              <p
                v-if="reviewError"
                class="inline-message inline-message--error"
                data-testid="review-error"
                role="alert"
              >{{ reviewError }}</p>
              <p
                v-if="reviewFeedback"
                class="inline-message inline-message--success"
                data-testid="review-feedback"
                role="status"
              >{{ reviewFeedback }}</p>
              <button class="button button--primary" type="submit" :disabled="mutationPending || reevaluationRefreshRequired">
                {{ reviewSaving ? '正在保存批阅…' : '确认并保存批阅' }}
              </button>
            </form>
            <div v-else class="not-applicable" role="status">
              {{ readOnlyGradingMessage }}
            </div>
          </section>
        </aside>
      </div>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import {
  downloadHomeworkSubmissionAttachment,
  getHomeworkDetail,
  getHomeworkSubmission,
  getHomeworkSubmissionEvaluation,
  getHomeworkSubmissionReviewLogs,
  reevaluateHomeworkSubmission,
  reviewHomeworkSubmission
} from '../../api/hwk/homeworks';
import { getTeacherLearningProgress } from '../../api/lrn/learningProgress';
import PageHeader from '../../components/foundation/PageHeader.vue';
import PageState from '../../components/foundation/PageState.vue';
import StatusBadge, { type StatusBadgeTone } from '../../components/foundation/StatusBadge.vue';
import type {
  HomeworkDetail,
  HomeworkEvaluationResult,
  HomeworkEvaluationStatus,
  HomeworkQuestion,
  HomeworkReviewLog,
  HomeworkReviewStatus,
  HomeworkSubmissionDetail,
  HomeworkSubmitStatus,
  HomeworkType
} from '../../types/hwk';
import {
  formatEvaluationStatus,
  formatHomeworkType,
  formatReviewOperation,
  formatReviewStatus,
  formatSubmitStatus
} from './hwkDisplay';

const props = defineProps<{
  courseId: number;
  homeworkId: number;
  submissionId: number;
}>();

interface ReviewForm {
  manualScore: string | number;
  finalScore: string | number;
  reason: string;
}

const route = useRoute();
const homework = ref<HomeworkDetail | null>(null);
const submission = ref<HomeworkSubmissionDetail | null>(null);
const evaluation = ref<HomeworkEvaluationResult | null>(null);
const reviewLogs = ref<HomeworkReviewLog[]>([]);
const courseName = ref('');
const studentName = ref('学生姓名暂不可用');
const studentNameWarning = ref('');
const pageLoading = ref(false);
const pageError = ref('');
const evaluationLoading = ref(false);
const evaluationError = ref('');
const evaluationRefreshWarning = ref('');
const reviewLogsLoading = ref(false);
const reviewLogsError = ref('');
const reviewSaving = ref(false);
const reviewError = ref('');
const reviewFeedback = ref('');
const reevaluationSaving = ref(false);
const reevaluationReason = ref('');
const reevaluationError = ref('');
const reevaluationFeedback = ref('');
const reevaluationRefreshRequired = ref(false);
const attachmentDownloading = ref(false);
const attachmentDownloadError = ref('');
const attachmentDownloadFeedback = ref('');
const reviewForm = reactive<ReviewForm>({ manualScore: '', finalScore: '', reason: '' });
let pageRequestId = 0;
let evaluationRequestId = 0;
let reviewLogsRequestId = 0;
let mutationRequestId = 0;
let attachmentDownloadRequestId = 0;

const pageTitle = computed(() => homework.value ? `批阅：${homework.value.title}` : '作业提交批阅');
const effectiveType = computed<HomeworkType>(() => submission.value?.submitType ?? homework.value?.type ?? 'TEXT');
const canReevaluate = computed(() => effectiveType.value === 'CODE' || effectiveType.value === 'OBJECTIVE');
const mutationPending = computed(() => reviewSaving.value || reevaluationSaving.value);
const historicalSubmission = computed(() => submission.value?.final === false);
const archivedHomework = computed(() => homework.value?.status === 'ARCHIVED');
const reviewMutable = computed(() => !archivedHomework.value && !historicalSubmission.value);
const readOnlyMessage = computed(() => historicalSubmission.value
  ? '历史提交版本仅供查看：不能修改评分或触发重评。'
  : '已归档作业仅供查看：不能修改评分或触发重评。');
const readOnlyEvaluationMessage = computed(() => historicalSubmission.value
  ? '历史提交版本的自动评测证据仅供查看，不能再次触发评测。'
  : '已归档作业的自动评测证据仅供查看，不能再次触发评测。');
const readOnlyGradingMessage = computed(() => historicalSubmission.value
  ? '历史提交版本的人工分、最终分与批阅说明仅供查看。'
  : '已归档作业的人工分、最终分与批阅说明已锁定。');
const answerTitle = computed(() => ({
  CODE: '源代码',
  TEXT: '文本答案',
  OBJECTIVE: '客观题作答',
  FILE: '附件提交'
}[effectiveType.value]));
const workspaceRoute = computed(() => ({
  name: 'homework-submission-workspace',
  params: { courseId: props.courseId, homeworkId: props.homeworkId },
  query: safeQueueQuery(route.query)
}));
const manageDetailRoute = computed(() => ({
  name: 'homework-manage-detail',
  params: { courseId: props.courseId, homeworkId: props.homeworkId }
}));

watch(
  () => `${props.courseId}:${props.homeworkId}:${props.submissionId}`,
  () => {
    pageRequestId += 1;
    mutationRequestId += 1;
    evaluationRequestId += 1;
    reviewLogsRequestId += 1;
    attachmentDownloadRequestId += 1;
  },
  { flush: 'sync' }
);

watch(
  () => `${props.courseId}:${props.homeworkId}:${props.submissionId}`,
  () => {
    void loadPage();
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  attachmentDownloadRequestId += 1;
});

async function loadPage() {
  const requestId = ++pageRequestId;
  const targetCourseId = props.courseId;
  const targetHomeworkId = props.homeworkId;
  const targetSubmissionId = props.submissionId;
  mutationRequestId += 1;
  evaluationRequestId += 1;
  reviewLogsRequestId += 1;
  attachmentDownloadRequestId += 1;
  reviewSaving.value = false;
  reevaluationSaving.value = false;
  evaluationLoading.value = false;
  reviewLogsLoading.value = false;
  attachmentDownloading.value = false;
  attachmentDownloadError.value = '';
  attachmentDownloadFeedback.value = '';
  reevaluationReason.value = '';
  reevaluationRefreshRequired.value = false;
  pageLoading.value = true;
  pageError.value = '';
  evaluationError.value = '';
  evaluationRefreshWarning.value = '';
  reviewLogsError.value = '';
  studentNameWarning.value = '';
  clearActionMessages();
  homework.value = null;
  submission.value = null;
  evaluation.value = null;
  reviewLogs.value = [];
  courseName.value = '';
  studentName.value = '学生姓名暂不可用';
  resetReviewForm();

  const [homeworkResult, submissionResult, progressResult] = await Promise.allSettled([
    getHomeworkDetail(targetHomeworkId),
    getHomeworkSubmission(targetSubmissionId),
    getTeacherLearningProgress(targetCourseId)
  ]);
  if (requestId !== pageRequestId) {
    return;
  }

  const requiredErrors: string[] = [];
  if (homeworkResult.status === 'fulfilled') {
    const detail = homeworkResult.value;
    if (detail.id !== targetHomeworkId || detail.courseId !== targetCourseId) {
      requiredErrors.push('作业与当前课程不匹配，请返回提交队列重新进入');
    } else {
      homework.value = detail;
    }
  } else {
    requiredErrors.push(errorMessage(homeworkResult.reason, '作业信息加载失败'));
  }

  if (submissionResult.status === 'fulfilled') {
    const detail = submissionResult.value;
    if (detail.submissionId !== targetSubmissionId || detail.homeworkId !== targetHomeworkId) {
      requiredErrors.push('提交版本与当前作业不匹配，请返回提交队列重新进入');
    } else {
      submission.value = detail;
    }
  } else {
    requiredErrors.push(errorMessage(submissionResult.reason, '提交详情加载失败'));
  }

  if (
    homework.value
    && submission.value?.submitType
    && submission.value.submitType !== homework.value.type
  ) {
    requiredErrors.push('提交类型与当前作业不匹配，请返回提交队列重新进入');
  }

  resolveStudentContext(progressResult);

  if (requiredErrors.length > 0) {
    homework.value = null;
    submission.value = null;
    pageError.value = requiredErrors.join('；');
    pageLoading.value = false;
    return;
  }

  if (submission.value) {
    syncReviewForm(submission.value);
  }
  await Promise.all([
    canReevaluate.value ? loadEvaluation(requestId) : Promise.resolve(),
    loadReviewLogs(requestId)
  ]);
  if (requestId === pageRequestId) {
    pageLoading.value = false;
  }
}

function resolveStudentContext(
  result: PromiseSettledResult<Awaited<ReturnType<typeof getTeacherLearningProgress>>>
) {
  if (result.status === 'rejected') {
    studentNameWarning.value = errorMessage(result.reason, '学生姓名同步失败');
    return;
  }
  if (result.value.courseId !== props.courseId) {
    studentNameWarning.value = '课程名单与当前课程不匹配，学生姓名暂时隐藏';
    return;
  }
  courseName.value = result.value.courseName.trim();
  const matched = result.value.students.find((item) => item.studentId === submission.value?.studentId);
  const resolvedName = safeStudentName(matched?.studentName, submission.value?.studentId);
  studentName.value = resolvedName || '学生姓名暂不可用';
  if (!resolvedName && submission.value) {
    studentNameWarning.value = '课程名单中未找到该提交对应的学生姓名';
  }
}

async function loadEvaluation(expectedPageRequestId = pageRequestId) {
  if (!canReevaluate.value || !submission.value) {
    evaluation.value = null;
    evaluationError.value = '';
    return;
  }
  const requestId = ++evaluationRequestId;
  const targetSubmissionId = submission.value.submissionId;
  const targetHomeworkId = submission.value.homeworkId;
  const targetStudentId = submission.value.studentId;
  const targetSubmitType = submission.value.submitType;
  evaluationLoading.value = true;
  evaluationError.value = '';
  evaluationRefreshWarning.value = '';
  try {
    const result = await getHomeworkSubmissionEvaluation(targetSubmissionId);
    if (requestId !== evaluationRequestId || expectedPageRequestId !== pageRequestId) {
      return;
    }
    if (result.submissionId !== targetSubmissionId) {
      evaluation.value = null;
      evaluationError.value = '评测结果与当前提交不匹配，请重新加载。';
      return;
    }
    evaluation.value = result;
    try {
      const refreshedSubmission = await getHomeworkSubmission(targetSubmissionId);
      if (requestId !== evaluationRequestId || expectedPageRequestId !== pageRequestId) {
        return;
      }
      if (
        refreshedSubmission.submissionId !== targetSubmissionId
        || refreshedSubmission.homeworkId !== targetHomeworkId
        || refreshedSubmission.studentId !== targetStudentId
        || (targetSubmitType && refreshedSubmission.submitType !== targetSubmitType)
      ) {
        throw new Error('评测后的提交状态与当前页面不匹配');
      }
      submission.value = refreshedSubmission;
      syncReviewForm(refreshedSubmission);
    } catch (error) {
      if (requestId === evaluationRequestId && expectedPageRequestId === pageRequestId) {
        evaluationRefreshWarning.value = `${errorMessage(error, '提交状态刷新失败')}；评测结果已加载，请重新加载页面以同步分数与批阅状态。`;
      }
    }
  } catch (error) {
    if (requestId === evaluationRequestId && expectedPageRequestId === pageRequestId) {
      evaluation.value = null;
      evaluationError.value = errorMessage(error, '评测结果加载失败，请重试。');
    }
  } finally {
    if (requestId === evaluationRequestId) {
      evaluationLoading.value = false;
    }
  }
}

async function loadReviewLogs(expectedPageRequestId = pageRequestId) {
  if (!submission.value) {
    return;
  }
  const requestId = ++reviewLogsRequestId;
  const targetSubmissionId = submission.value.submissionId;
  reviewLogsLoading.value = true;
  reviewLogsError.value = '';
  try {
    const result = await getHomeworkSubmissionReviewLogs(targetSubmissionId);
    if (requestId !== reviewLogsRequestId || expectedPageRequestId !== pageRequestId) {
      return;
    }
    if (result.some((item) => item.submissionId !== targetSubmissionId)) {
      reviewLogs.value = [];
      reviewLogsError.value = '批阅日志与当前提交不匹配，请重新加载。';
      return;
    }
    reviewLogs.value = result;
  } catch (error) {
    if (requestId === reviewLogsRequestId && expectedPageRequestId === pageRequestId) {
      reviewLogs.value = [];
      reviewLogsError.value = errorMessage(error, '批阅日志加载失败，请重试。');
    }
  } finally {
    if (requestId === reviewLogsRequestId) {
      reviewLogsLoading.value = false;
    }
  }
}

async function downloadAttachment() {
  const current = submission.value;
  if (!current?.attachment?.downloadAvailable || attachmentDownloading.value) {
    return;
  }
  const request = ++attachmentDownloadRequestId;
  const submissionId = current.submissionId;
  const homeworkId = props.homeworkId;
  attachmentDownloading.value = true;
  attachmentDownloadError.value = '';
  attachmentDownloadFeedback.value = '';
  try {
    const result = await downloadHomeworkSubmissionAttachment(homeworkId, submissionId);
    if (request !== attachmentDownloadRequestId || submission.value?.submissionId !== submissionId) {
      return;
    }
    const filename = result.filename || current.attachment.originalFilename;
    triggerBrowserDownload(result.blob, filename);
    attachmentDownloadFeedback.value = `已开始下载 ${filename}`;
  } catch (error) {
    if (request === attachmentDownloadRequestId && submission.value?.submissionId === submissionId) {
      attachmentDownloadError.value = errorMessage(error, '附件下载失败，请重试。');
    }
  } finally {
    if (request === attachmentDownloadRequestId) {
      attachmentDownloading.value = false;
    }
  }
}

async function saveReview() {
  const currentHomework = homework.value;
  const currentSubmission = submission.value;
  if (
    !currentHomework
    || !currentSubmission
    || !reviewMutable.value
    || mutationPending.value
    || reevaluationRefreshRequired.value
  ) {
    return;
  }
  reviewError.value = '';
  reviewFeedback.value = '';

  let manualScore: number;
  let finalScore: number;
  try {
    manualScore = requiredBoundedScore(reviewForm.manualScore, '人工得分', currentHomework.totalScore);
    finalScore = requiredBoundedScore(reviewForm.finalScore, '最终得分', currentHomework.totalScore);
  } catch (error) {
    reviewError.value = errorMessage(error, '请检查批阅分数');
    return;
  }
  const reason = reviewForm.reason.trim();
  if (!reason) {
    reviewError.value = '批阅说明不能为空';
    return;
  }
  const confirmed = window.confirm(
    `确认保存“${studentName.value}”版本 ${currentSubmission.version} 的批阅？人工得分 ${manualScore}，最终得分 ${finalScore}。`
  );
  if (!confirmed) {
    return;
  }

  const requestId = ++mutationRequestId;
  reviewSaving.value = true;
  try {
    const updated = await reviewHomeworkSubmission(currentSubmission.submissionId, {
      manualScore,
      finalScore,
      comment: reason
    });
    if (!isCurrentMutation(requestId, currentSubmission.submissionId)) {
      return;
    }
    if (updated.submissionId !== currentSubmission.submissionId || updated.homeworkId !== props.homeworkId) {
      throw new Error('批阅结果与当前提交不匹配，请重新加载。');
    }
    submission.value = updated;
    syncReviewForm(updated);
    reviewFeedback.value = '批阅已保存，分数与批阅日志已更新。';
    await loadReviewLogs();
  } catch (error) {
    if (isCurrentMutation(requestId, currentSubmission.submissionId)) {
      reviewError.value = errorMessage(error, '批阅保存失败，请重试。');
    }
  } finally {
    if (requestId === mutationRequestId) {
      reviewSaving.value = false;
    }
  }
}

async function reevaluateSubmission() {
  const currentSubmission = submission.value;
  if (
    !currentSubmission
    || !canReevaluate.value
    || !reviewMutable.value
    || mutationPending.value
    || reevaluationRefreshRequired.value
  ) {
    return;
  }
  reevaluationError.value = '';
  reevaluationFeedback.value = '';
  const reason = reevaluationReason.value.trim();
  if (!reason) {
    reevaluationError.value = '重评理由不能为空';
    return;
  }
  const confirmed = window.confirm(
    `确认重新评测“${studentName.value}”的版本 ${currentSubmission.version}？理由：${reason}`
  );
  if (!confirmed) {
    return;
  }

  const requestId = ++mutationRequestId;
  evaluationRequestId += 1;
  evaluationLoading.value = false;
  reevaluationSaving.value = true;
  let mutationCompleted = false;
  try {
    const updatedEvaluation = await reevaluateHomeworkSubmission(currentSubmission.submissionId, reason);
    mutationCompleted = true;
    if (!isCurrentMutation(requestId, currentSubmission.submissionId)) {
      return;
    }
    if (updatedEvaluation.submissionId !== currentSubmission.submissionId) {
      throw new Error('重评结果与当前提交不匹配，请重新加载。');
    }
    evaluation.value = updatedEvaluation;
    evaluationError.value = '';
    evaluationRefreshWarning.value = '';

    const refreshedSubmission = await getHomeworkSubmission(currentSubmission.submissionId);
    if (!isCurrentMutation(requestId, currentSubmission.submissionId)) {
      return;
    }
    if (refreshedSubmission.submissionId !== currentSubmission.submissionId || refreshedSubmission.homeworkId !== props.homeworkId) {
      throw new Error('重评后的提交版本与当前页面不匹配，请重新加载。');
    }
    submission.value = refreshedSubmission;
    syncReviewForm(refreshedSubmission);
    await loadReviewLogs();
    reevaluationReason.value = '';
    reevaluationFeedback.value = '重评已成功，当前版本、评测结果与操作日志已刷新。';
  } catch (error) {
    if (isCurrentMutation(requestId, currentSubmission.submissionId)) {
      if (mutationCompleted) {
        markReevaluationRefreshFailure(error);
      } else {
        reevaluationError.value = errorMessage(error, '重评提交失败，请重试。');
      }
    }
  } finally {
    if (requestId === mutationRequestId) {
      reevaluationSaving.value = false;
    }
  }
}

function markReevaluationRefreshFailure(error: unknown) {
  const detail = errorMessage(error, '提交状态刷新失败');
  reevaluationError.value = '';
  reevaluationRefreshRequired.value = true;
  reevaluationFeedback.value = `重评已成功，页面刷新失败。${detail}。为避免重复重评，请先重新加载页面。`;
}

function isCurrentMutation(requestId: number, submissionId: number) {
  return requestId === mutationRequestId && submission.value?.submissionId === submissionId;
}

function syncReviewForm(detail: HomeworkSubmissionDetail) {
  reviewForm.manualScore = inputScore(detail.manualScore ?? detail.autoScore);
  reviewForm.finalScore = inputScore(detail.finalScore ?? detail.autoScore);
  reviewForm.reason = detail.comment ?? '';
}

function resetReviewForm() {
  reviewForm.manualScore = '';
  reviewForm.finalScore = '';
  reviewForm.reason = '';
}

function safeStudentName(value: string | null | undefined, studentId: number | undefined) {
  const candidate = value?.trim() ?? '';
  if (!candidate || studentId === undefined) {
    return candidate;
  }
  const compactName = candidate.replace(/\s+/gu, '');
  return compactName === String(studentId) || compactName === `学生${studentId}` ? '' : candidate;
}

function requiredBoundedScore(value: string | number, label: string, maximum: number) {
  if (String(value).trim() === '') {
    throw new Error(`${label}不能为空`);
  }
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error(`${label}必须是大于或等于 0 的数字`);
  }
  if (parsed > maximum) {
    throw new Error(`${label}不得超过 ${maximum} 分`);
  }
  return parsed;
}

function inputScore(value: number | null | undefined) {
  return value === null || value === undefined ? '' : value;
}

function formatObjectiveAnswer(value: string | null | undefined) {
  if (!value) {
    return '本次提交未记录客观题答案。';
  }
  try {
    const parsed: unknown = JSON.parse(value);
    const questions = [...(homework.value?.questions ?? [])]
      .sort((left, right) => left.sortOrder - right.sortOrder);
    const entries: Array<readonly [string, unknown]> = questions.length > 0
      ? questions.map((question, index) => [
          objectiveQuestionLabel(question, index),
          objectiveAnswerForQuestion(parsed, question, index, questions.length)
        ] as const)
      : fallbackObjectiveEntries(parsed);
    if (entries.length === 0) {
      return '本次提交未记录客观题答案。';
    }
    return entries
      .map(([question, answer]) => `${question}：${formatObjectiveChoice(answer)}`)
      .join('\n');
  } catch {
    return '客观题答案暂时无法解析，请核对该提交的评测结果。';
  }
}

function objectiveAnswerForQuestion(
  parsed: unknown,
  question: HomeworkQuestion,
  index: number,
  questionCount: number
) {
  if (isPlainRecord(parsed)) {
    const keys = [String(question.id), String(question.sortOrder), `q${question.sortOrder}`];
    const matchedKey = keys.find((key) => Object.prototype.hasOwnProperty.call(parsed, key));
    return matchedKey === undefined ? undefined : parsed[matchedKey];
  }
  if (Array.isArray(parsed) && questionCount > 1) {
    return parsed[index];
  }
  return questionCount === 1 ? parsed : undefined;
}

function objectiveQuestionLabel(question: HomeworkQuestion, index: number) {
  const stem = question.stem.trim();
  return stem ? `第 ${index + 1} 题 · ${stem}` : `第 ${index + 1} 题`;
}

function fallbackObjectiveEntries(parsed: unknown): Array<readonly [string, unknown]> {
  if (Array.isArray(parsed)) {
    return parsed.map((answer, index) => [`第 ${index + 1} 题`, answer] as const);
  }
  if (!isPlainRecord(parsed)) {
    return [];
  }
  return Object.entries(parsed).map(([key, answer], index) => {
    const transportOrder = /^q(\d+)$/i.exec(key)?.[1];
    return [`第 ${transportOrder ?? index + 1} 题`, answer] as const;
  });
}

function formatObjectiveChoice(value: unknown) {
  const choices = collectObjectiveChoices(value);
  return choices.length > 0 ? `选项 ${choices.join('、')}` : '未作答';
}

function collectObjectiveChoices(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.flatMap(collectObjectiveChoices);
  }
  if (isPlainRecord(value)) {
    return Object.values(value).flatMap(collectObjectiveChoices);
  }
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    const text = String(value).trim();
    if (!text) {
      return [];
    }
    if (text.toLowerCase() === 'true') {
      return ['正确'];
    }
    if (text.toLowerCase() === 'false') {
      return ['错误'];
    }
    return [text];
  }
  return [];
}

function isPlainRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function safeQueueQuery(query: Record<string, unknown>) {
  const safe: Record<string, string> = {};
  const keyword = queryText(query.keyword);
  const studentRef = queryText(query.studentRef).toLowerCase();
  const submit = queryText(query.submit);
  const evaluationStatus = queryText(query.evaluation);
  const review = queryText(query.review);
  const attention = queryText(query.attention);
  const page = queryText(query.page);
  if (keyword) {
    safe.keyword = keyword;
  }
  if (/^[0-9a-f]{16}$/.test(studentRef)) {
    safe.studentRef = studentRef;
  }
  if (['SUBMITTED', 'LATE', 'REJECTED'].includes(submit)) {
    safe.submit = submit;
  }
  if ([
    'NONE',
    'PENDING',
    'RUNNING',
    'ACCEPTED',
    'WRONG_ANSWER',
    'COMPILE_ERROR',
    'RUNTIME_ERROR',
    'TIME_LIMIT_EXCEEDED',
    'SYSTEM_ERROR'
  ].includes(evaluationStatus)) {
    safe.evaluation = evaluationStatus;
  }
  if (['UNREVIEWED', 'NEED_REVIEW', 'REVIEWED'].includes(review)) {
    safe.review = review;
  }
  if (['EVALUATION_PENDING', 'REVIEW_PENDING'].includes(attention)) {
    safe.attention = attention;
  }
  if (Number.isInteger(Number(page)) && Number(page) > 1) {
    safe.page = String(Number(page));
  }
  return safe;
}

function queryText(value: unknown) {
  const candidate = Array.isArray(value) ? value[0] : value;
  return typeof candidate === 'string' ? candidate.trim() : '';
}

function submitTone(status: HomeworkSubmitStatus): StatusBadgeTone {
  return status === 'SUBMITTED' ? 'success' : status === 'LATE' ? 'warning' : 'danger';
}

function evaluationTone(status: HomeworkEvaluationStatus): StatusBadgeTone {
  if (status === 'ACCEPTED') {
    return 'success';
  }
  if (status === 'NONE' || status === 'PENDING' || status === 'RUNNING') {
    return 'info';
  }
  return 'danger';
}

function reviewTone(status: HomeworkReviewStatus): StatusBadgeTone {
  return status === 'REVIEWED' ? 'success' : status === 'NEED_REVIEW' ? 'warning' : 'neutral';
}

function formatLanguage(value: string | null | undefined) {
  if (!value) {
    return '未注明语言';
  }
  return {
    c: 'C',
    cpp: 'C++',
    java: 'Java',
    python: 'Python',
    javascript: 'JavaScript',
    typescript: 'TypeScript'
  }[value.toLowerCase()] ?? value;
}

function formatScore(value: number | null | undefined) {
  return value === null || value === undefined ? '待定' : `${value} 分`;
}

function formatDuration(value: number | null | undefined) {
  return value === null || value === undefined ? '未记录' : `${value} ms`;
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function triggerBrowserDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

function formatOptionalDateTime(value: string | null | undefined) {
  return value ? formatDateTime(value) : '尚未完成';
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

function scoreChangeLabel(oldScore: number | null | undefined, newScore: number | null | undefined) {
  if (oldScore === null || oldScore === undefined) {
    return newScore === null || newScore === undefined ? '未记录分数变化' : `设置为 ${newScore} 分`;
  }
  if (newScore === null || newScore === undefined) {
    return `原分数 ${oldScore} 分`;
  }
  return `${oldScore} 分 → ${newScore} 分`;
}

function reviewLogNote(log: HomeworkReviewLog) {
  if (log.operationType === 'REJUDGE') {
    return log.reason || log.comment || '本次重评未填写补充说明';
  }
  return log.comment || log.reason || '本次操作未填写补充说明';
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message.trim() ? error.message : fallback;
}

function clearActionMessages() {
  reviewError.value = '';
  reviewFeedback.value = '';
  reevaluationError.value = '';
  reevaluationFeedback.value = '';
}
</script>

<style scoped>
.homework-submission-review {
  display: grid;
  gap: 18px;
  width: 100%;
  max-width: none;
  min-width: 0;
  color: var(--oj-ink);
}

.homework-submission-review,
.homework-submission-review * {
  box-sizing: border-box;
}

.review-link,
.button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 42px;
  padding: 9px 15px;
  border: 1px solid var(--oj-brand);
  border-radius: var(--oj-radius);
  font: inherit;
  font-size: 0.84rem;
  font-weight: 800;
  text-decoration: none;
}

.review-link,
.button--secondary {
  background: rgba(255, 255, 255, 0.68);
  color: var(--oj-brand);
}

.button--primary {
  background: var(--oj-brand);
  color: #fff;
}

.button--quiet {
  min-height: 36px;
  padding: 6px 9px;
  border-color: transparent;
  background: transparent;
  color: var(--oj-brand);
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
  min-width: 0;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  box-shadow: var(--oj-shadow-soft);
  backdrop-filter: var(--oj-blur);
  -webkit-backdrop-filter: var(--oj-blur);
}

.review-context {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) auto minmax(280px, 1.4fr);
  align-items: center;
  gap: 18px 22px;
  padding: 20px 23px;
}

.review-context__student h2,
.review-context__student p,
.section-eyebrow,
.card-heading h2,
.card-heading p,
.attachment-panel p,
.audit-list p,
.form-hint {
  margin: 0;
}

.review-context__student h2 {
  margin: 4px 0;
  font-size: 1.35rem;
}

.review-context__student > p:last-of-type {
  color: var(--oj-muted);
  font-size: 0.84rem;
}

.section-eyebrow {
  color: var(--oj-brand);
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.review-context__statuses,
.version-context {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.version-context span {
  padding: 4px 8px;
  border: 1px solid var(--oj-line);
  border-radius: 999px;
  color: var(--oj-muted);
  font-size: 0.72rem;
  font-weight: 700;
}

.score-strip {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.score-strip div,
.evaluation-summary div {
  min-width: 0;
  padding: 11px 12px;
  border-radius: calc(var(--oj-radius) - 4px);
  background: rgba(22, 66, 60, 0.06);
}

.score-strip dt,
.evaluation-summary dt {
  color: var(--oj-muted);
  font-size: 0.74rem;
  font-weight: 700;
}

.score-strip dd,
.evaluation-summary dd {
  margin: 5px 0 0;
  color: var(--oj-ink);
  font-weight: 800;
}

.review-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(330px, 0.8fr);
  align-items: start;
  gap: 18px;
  min-width: 0;
}

.review-column {
  display: grid;
  gap: 18px;
  min-width: 0;
}

.review-column--grading {
  position: sticky;
  top: 16px;
}

.review-card {
  display: grid;
  gap: 17px;
  padding: 21px;
}

.card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.card-heading h2 {
  margin-top: 5px;
  font-size: 1.16rem;
}

.card-heading > span {
  color: var(--oj-muted);
  font-size: 0.78rem;
  font-weight: 800;
}

.content-panel {
  min-width: 0;
  margin: 0;
  padding: 17px;
  border-radius: calc(var(--oj-radius) - 3px);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.7;
}

.content-panel--code {
  max-height: 560px;
  overflow: auto;
  background: #10211f;
  color: #e7f5ee;
  font-size: 0.86rem;
}

.content-panel--prose {
  border: 1px solid var(--oj-line);
  background: rgba(255, 255, 255, 0.55);
}

.attachment-panel,
.not-applicable,
.evaluation-copy,
.inline-message,
.compact-state {
  padding: 12px 14px;
  border-radius: calc(var(--oj-radius) - 4px);
}

.not-applicable,
.inline-message--warning {
  border: 1px solid rgba(194, 123, 0, 0.22);
  background: rgba(194, 123, 0, 0.08);
  color: #6b4103;
}

.attachment-panel {
  display: grid;
  gap: 8px;
  min-width: 0;
  border: 1px solid color-mix(in srgb, var(--oj-brand) 24%, var(--oj-line));
  background: color-mix(in srgb, var(--oj-brand) 7%, rgba(255, 255, 255, 0.5));
}

.attachment-panel strong,
.attachment-panel span {
  overflow-wrap: anywhere;
}

.attachment-panel span {
  color: var(--oj-muted);
  font-size: 0.8rem;
}

.attachment-panel .button {
  justify-self: start;
}

.attachment-panel p {
  line-height: 1.6;
}

.evaluation-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 9px;
  margin: 0;
}

.evaluation-copy,
.inline-message--success {
  background: var(--oj-brand-soft);
  color: var(--oj-brand-strong);
}

.inline-message--error,
.compact-state--error {
  background: rgba(190, 49, 49, 0.1);
  color: #8f2d24;
}

.log-panel {
  border: 1px solid var(--oj-line);
  border-radius: calc(var(--oj-radius) - 3px);
  padding: 11px 13px;
  background: rgba(255, 255, 255, 0.5);
}

.log-panel summary {
  cursor: pointer;
  color: var(--oj-brand);
  font-weight: 800;
}

.log-panel pre {
  max-height: 280px;
  margin: 12px 0 0;
  overflow: auto;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.action-form,
.score-form {
  display: grid;
  gap: 13px;
  padding-top: 3px;
}

.field {
  display: grid;
  gap: 7px;
  color: var(--oj-ink-soft);
  font-size: 0.82rem;
  font-weight: 800;
}

.field input,
.field textarea {
  width: 100%;
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid var(--oj-line-strong);
  border-radius: calc(var(--oj-radius) - 4px);
  background: rgba(255, 255, 255, 0.74);
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
  gap: 11px;
}

.form-hint,
.empty-copy {
  color: var(--oj-muted);
  font-size: 0.8rem;
  line-height: 1.6;
}

.compact-state {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.audit-list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.audit-list li {
  display: grid;
  gap: 7px;
  padding: 13px 14px;
  border: 1px solid var(--oj-line);
  border-radius: calc(var(--oj-radius) - 3px);
  background: rgba(255, 255, 255, 0.52);
}

.audit-list li > div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.audit-list span,
.audit-list small {
  color: var(--oj-muted);
  font-size: 0.76rem;
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
  .homework-submission-review {
    gap: 14px;
  }

  .review-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 14px;
  }

  .review-context,
  .score-strip,
  .evaluation-summary,
  .score-pair {
    grid-template-columns: minmax(0, 1fr);
  }

  .review-context,
  .review-card {
    padding: 17px;
  }

  .card-heading,
  .audit-list li > div,
  .compact-state {
    align-items: flex-start;
    flex-direction: column;
  }

  .button,
  .review-link {
    width: 100%;
  }

  .attachment-panel .button {
    justify-self: stretch;
  }
}
</style>
