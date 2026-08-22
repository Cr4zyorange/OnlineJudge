<template>
  <main
    class="hwk-history"
    :class="{ 'hwk-history--teacher': isTeacher }"
    data-testid="homework-submission-history"
  >
    <template v-if="!isTeacher">
      <PageHeader
        title="提交历史"
        :eyebrow="`UI-HWK-06 · 作业 #${homeworkId}`"
        subtitle="按版本查看本人的真实提交、评测与批阅进度。"
      >
        <template #actions>
          <RouterLink class="hwk-history__primary-link" :to="backHref">返回作业详情</RouterLink>
        </template>
      </PageHeader>

      <SummaryStrip :items="studentSummaryItems" aria-label="提交历史摘要" />

      <section class="hwk-history__student-workspace" aria-label="我的提交版本">
        <PageState
          v-if="loading"
          state="loading"
          title="正在加载提交历史"
          message="正在同步提交、评测和批阅状态。"
        />
        <PageState
          v-else-if="errorMessage"
          state="error"
          title="提交历史加载失败"
          :message="errorMessage"
          retry-label="重新加载"
          @retry="loadSubmissions"
        />
        <PageState
          v-else-if="studentRows.length === 0"
          state="empty"
          title="暂无提交记录"
          message="完成第一次提交后，每个版本都会在这里保留。"
        >
          <template #actions>
            <RouterLink class="hwk-history__primary-link" :to="`${backHref}/submit`">去提交作业</RouterLink>
          </template>
        </PageState>
        <template v-else>
          <DataTable
            :columns="studentColumns"
            :rows="studentRows"
            caption="我的作业提交历史"
            row-key="submissionId"
            :row-label="studentRowLabel"
          >
            <template #cell-version="{ row }">
              <div :data-testid="`history-version-${row.submissionId}`" class="hwk-history__version-cell">
                <strong>版本 {{ row.version }}</strong>
                <div class="hwk-history__markers">
                  <StatusBadge v-if="row.isLatest" label="最新版本" tone="brand" />
                  <StatusBadge v-if="row.isEffective" label="当前有效" tone="success" />
                </div>
              </div>
            </template>
            <template #cell-submitStatus="{ row }">
              <StatusBadge :label="String(row.submitLabel)" :tone="toneValue(row.submitTone)" />
            </template>
            <template #cell-evaluationStatus="{ row }">
              <StatusBadge :label="String(row.evaluationLabel)" :tone="toneValue(row.evaluationTone)" />
            </template>
            <template #cell-reviewStatus="{ row }">
              <StatusBadge :label="String(row.reviewLabel)" :tone="toneValue(row.reviewTone)" />
            </template>
            <template #cell-action="{ row }">
              <div :data-submission-id="row.submissionId" class="hwk-history__row-actions">
                <RouterLink
                  :data-testid="`history-result-${row.submissionId}`"
                  :to="{
                    name: 'homework-submission-result',
                    params: {
                      courseId,
                      homeworkId,
                      submissionId: Number(row.submissionId)
                    }
                  }"
                >
                  查看结果
                </RouterLink>
                <button type="button" @click="openDetail(Number(row.submissionId))">查看内容</button>
              </div>
            </template>
          </DataTable>

          <section class="hwk-history__student-detail" aria-label="提交内容">
            <PageState
              v-if="detailLoading"
              state="loading"
              title="正在加载提交内容"
              message="正在获取该版本保存的答案。"
            />
            <PageState
              v-else-if="detailErrorMessage"
              state="error"
              title="提交内容加载失败"
              :message="detailErrorMessage"
              retry-label="重试"
              @retry="selectedSubmissionId && openDetail(selectedSubmissionId)"
            />
            <div v-else-if="detail" class="hwk-history__detail-card">
              <header>
                <div>
                  <p>提交内容</p>
                  <h2>版本 {{ detail.version }}</h2>
                </div>
                <div class="hwk-history__markers">
                  <StatusBadge :label="formatSubmitStatus(detail.submitStatus)" :tone="submitTone(detail.submitStatus)" />
                  <StatusBadge :label="formatEvaluationStatus(detail.evaluationStatus)" :tone="evaluationTone(detail.evaluationStatus)" />
                  <StatusBadge :label="formatReviewStatus(detail.reviewStatus)" :tone="reviewTone(detail.reviewStatus)" />
                </div>
              </header>
              <dl>
                <div><dt>作业类型</dt><dd>{{ formatHomeworkType(detail.submitType) }}</dd></div>
                <div><dt>提交时间</dt><dd>{{ formatDateTime(detail.submittedAt) }}</dd></div>
                <div><dt>程序语言</dt><dd>{{ detail.language ?? '—' }}</dd></div>
                <div><dt>得分</dt><dd>{{ formatScore(detail.finalScore ?? detail.autoScore) }}</dd></div>
              </dl>
              <section
                v-if="detail.submitType === 'FILE' && detail.attachment"
                class="hwk-history__attachment"
                data-testid="homework-attachment-panel"
              >
                <strong>{{ detail.attachment.originalFilename }}</strong>
                <span>{{ detail.attachment.contentType }} · {{ formatFileSize(detail.attachment.fileSize) }}</span>
                <button
                  v-if="detail.attachment.downloadAvailable"
                  type="button"
                  data-action="download-homework-attachment"
                  :disabled="attachmentDownloading"
                  @click="downloadSelectedAttachment"
                >{{ attachmentDownloading ? '正在下载…' : '下载附件' }}</button>
                <p v-else>当前附件暂不可下载。</p>
                <p v-if="attachmentDownloadError" class="hwk-history__error" role="alert">{{ attachmentDownloadError }}</p>
                <p v-if="attachmentDownloadFeedback" class="hwk-history__feedback" role="status">{{ attachmentDownloadFeedback }}</p>
              </section>
              <p
                v-else-if="detail.submitType === 'FILE' && detail.hasAttachment"
                class="hwk-history__detail-hint"
              >附件元数据暂不可用，当前不能下载。</p>
              <pre v-else class="hwk-history__answer">{{ formatStudentAnswer(detail) }}</pre>
              <p v-if="detail.comment" class="hwk-history__comment">评语：{{ detail.comment }}</p>
            </div>
            <p v-else class="hwk-history__detail-hint">选择“查看内容”对照某一次保存的答案。</p>
          </section>
        </template>
      </section>
    </template>

    <section v-else class="hwk-history__panel" aria-label="提交历史">
      <header class="hwk-history__header">
        <div>
          <h1>提交历史</h1>
          <p v-if="isTeacher">共 {{ total }} 条</p>
          <p v-else>查看当前作业的所有提交版本</p>
        </div>
        <a class="hwk-history__back" :href="backHref">返回作业详情</a>
      </header>

      <form v-if="isTeacher" class="hwk-history__filters" @submit.prevent="applyFilters">
        <label>
          学生
          <input v-model="studentKeyword" data-testid="history-student-keyword" type="search" placeholder="学号" />
        </label>
        <label>
          提交状态
          <select v-model="submitStatusFilter" data-testid="history-submit-status">
            <option value="">全部</option>
            <option v-for="status in submitStatusOptions" :key="status" :value="status">{{ formatSubmitStatus(status) }}</option>
          </select>
        </label>
        <label>
          评测状态
          <select v-model="evaluationStatusFilter" data-testid="history-evaluation-status">
            <option value="">全部</option>
            <option v-for="status in evaluationStatusOptions" :key="status" :value="status">{{ formatEvaluationStatus(status) }}</option>
          </select>
        </label>
        <label>
          批阅状态
          <select v-model="reviewStatusFilter" data-testid="history-review-status">
            <option value="">全部</option>
            <option v-for="status in reviewStatusOptions" :key="status" :value="status">{{ formatReviewStatus(status) }}</option>
          </select>
        </label>
        <button type="submit" data-testid="history-apply-filters">筛选</button>
      </form>

      <p v-if="loading">加载中</p>
      <p v-else-if="errorMessage" class="hwk-history__error">{{ errorMessage }}</p>
      <p v-else-if="submissions.length === 0" class="hwk-history__empty">暂无提交记录</p>
      <div v-else class="hwk-history__content">
        <ul class="hwk-history__list">
          <li
            v-for="item in submissions"
            :key="item.submissionId"
            :data-submission-id="item.submissionId"
            class="hwk-history__item"
          >
            <div class="hwk-history__item-head">
              <strong>版本 {{ item.version }}</strong>
              <button type="button" @click="openDetail(item.submissionId)">查看详情</button>
            </div>
            <p v-if="isTeacher">学生 {{ item.studentId }}</p>
            <p>提交状态：{{ formatSubmitStatus(item.submitStatus) }}</p>
            <p>评测状态：{{ formatEvaluationStatus(item.evaluationStatus) }}</p>
            <p>复核状态：{{ formatReviewStatus(item.reviewStatus) }}</p>
            <p>最终得分：{{ formatScore(item.finalScore) }}</p>
            <p>提交时间：{{ formatDateTime(item.submittedAt) }}</p>
            <div class="hwk-history__tags">
              <span v-if="item.final">当前有效</span>
              <span v-else>历史版本</span>
            </div>
          </li>
        </ul>

        <nav v-if="isTeacher && total > size" class="hwk-history__pager" aria-label="提交分页">
          <button type="button" :disabled="page <= 1 || loading" data-testid="history-prev" @click="goToPage(page - 1)">
            上一页
          </button>
          <span>第 {{ page }} 页</span>
          <button
            type="button"
            :disabled="page >= totalPages || loading"
            data-testid="history-next"
            @click="goToPage(page + 1)"
          >
            下一页
          </button>
        </nav>

        <aside class="hwk-history__detail" aria-label="提交详情">
          <p v-if="detailLoading">详情加载中</p>
          <p v-else-if="detailErrorMessage" class="hwk-history__error">{{ detailErrorMessage }}</p>
          <p v-else-if="detail === null">请选择一个版本查看详情</p>
          <template v-else>
            <h2>版本 {{ detail.version }}</h2>
            <p>学生 {{ detail.studentId }}</p>
            <p>作业类型：{{ formatHomeworkType(detail.submitType) }}</p>
            <p>提交状态：{{ formatSubmitStatus(detail.submitStatus) }}</p>
            <p>评测状态：{{ formatEvaluationStatus(detail.evaluationStatus) }}</p>
            <p>复核状态：{{ formatReviewStatus(detail.reviewStatus) }}</p>
            <section
              v-if="detail.submitType === 'FILE' && detail.attachment"
              class="hwk-history__attachment"
              data-testid="homework-attachment-panel"
            >
              <strong>{{ detail.attachment.originalFilename }}</strong>
              <span>{{ detail.attachment.contentType }} · {{ formatFileSize(detail.attachment.fileSize) }}</span>
              <button
                v-if="detail.attachment.downloadAvailable"
                type="button"
                data-action="download-homework-attachment"
                :disabled="attachmentDownloading"
                @click="downloadSelectedAttachment"
              >{{ attachmentDownloading ? '正在下载…' : '下载附件' }}</button>
              <p v-else>当前附件暂不可下载。</p>
              <p v-if="attachmentDownloadError" class="hwk-history__error" role="alert">{{ attachmentDownloadError }}</p>
              <p v-if="attachmentDownloadFeedback" class="hwk-history__feedback" role="status">{{ attachmentDownloadFeedback }}</p>
            </section>
            <p v-else-if="detail.submitType === 'FILE' && detail.hasAttachment">附件元数据暂不可用，当前不能下载。</p>
            <p>语言：{{ detail.language ?? '无' }}</p>
            <pre class="hwk-history__answer">{{ detail.answerText || detail.answerJson || '本次提交没有文本内容' }}</pre>
            <p v-if="detail.comment">评语：{{ detail.comment }}</p>
            <section v-if="isTeacher" class="hwk-history__review" aria-label="teacher review">
              <form data-testid="history-review-form" @submit.prevent="saveReview">
                <label>
                  人工分
                  <input v-model.trim="reviewManualScore" data-testid="history-review-manual-score" type="number" min="0" step="0.01" />
                </label>
                <label>
                  最终分
                  <input v-model.trim="reviewFinalScore" data-testid="history-review-final-score" type="number" min="0" step="0.01" />
                </label>
                <label>
                  评语
                  <textarea v-model.trim="reviewComment" data-testid="history-review-comment" rows="3" />
                </label>
                <button type="submit" :disabled="reviewSaving" data-testid="history-save-review">保存批阅</button>
              </form>
              <form data-testid="history-reevaluate-form" @submit.prevent="triggerReevaluation">
                <label>
                  重评原因
                  <textarea v-model.trim="reevaluationReason" data-testid="history-reevaluate-reason" rows="2" />
                </label>
                <button type="submit" :disabled="reevaluationSubmitting" data-testid="history-trigger-reevaluate">
                  触发重评
                </button>
              </form>
              <p v-if="reviewFeedback" class="hwk-history__feedback">{{ reviewFeedback }}</p>
              <p v-if="reviewErrorMessage" class="hwk-history__error">{{ reviewErrorMessage }}</p>

              <div class="hwk-history__logs" data-testid="history-review-logs">
                <h3>批阅日志</h3>
                <p v-if="reviewLogsLoading">日志加载中</p>
                <p v-else-if="reviewLogs.length === 0">暂无批阅日志</p>
                <ul v-else>
                  <li v-for="log in reviewLogs" :key="log.id">
                    <strong>{{ formatReviewOperation(log.operationType) }}</strong>
                    <span>{{ formatScore(log.oldScore) }} -> {{ formatScore(log.newScore) }}</span>
                    <span>{{ log.comment || log.reason || '-' }}</span>
                    <span>{{ formatDateTime(log.createdAt) }}</span>
                  </li>
                </ul>
              </div>
            </section>
          </template>
        </aside>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import {
  downloadHomeworkSubmissionAttachment,
  getHomeworkSubmission,
  getHomeworkSubmissionReviewLogs,
  listHomeworkSubmissions,
  listMyHomeworkSubmissions,
  reevaluateHomeworkSubmission,
  reviewHomeworkSubmission
} from '../../api/hwk/homeworks';
import type { HomeworkSubmissionListQuery } from '../../api/hwk/homeworks';
import type {
  HomeworkEvaluationStatus,
  HomeworkReviewLog,
  HomeworkReviewStatus,
  HomeworkSubmissionDetail,
  HomeworkSubmissionSummary,
  HomeworkSubmitStatus
} from '../../types/hwk';
import DataTable, {
  type DataTableColumn,
  type DataTableRow
} from '../../components/foundation/DataTable.vue';
import PageHeader from '../../components/foundation/PageHeader.vue';
import PageState from '../../components/foundation/PageState.vue';
import StatusBadge, { type StatusBadgeTone } from '../../components/foundation/StatusBadge.vue';
import SummaryStrip, { type SummaryStripItem } from '../../components/foundation/SummaryStrip.vue';
import {
  formatEvaluationStatus,
  formatHomeworkType,
  formatReviewOperation,
  formatReviewStatus,
  formatSubmitStatus
} from './hwkDisplay';

const props = withDefaults(defineProps<{
  courseId: number;
  homeworkId: number;
  role?: 'student' | 'teacher';
}>(), {
  role: 'student'
});

const page = ref(1);
const size = ref(20);
const total = ref(0);
const loading = ref(false);
const detailLoading = ref(false);
const selectedSubmissionId = ref<number | null>(null);
const submissions = ref<HomeworkSubmissionSummary[]>([]);
const detail = ref<HomeworkSubmissionDetail | null>(null);
const errorMessage = ref('');
const detailErrorMessage = ref('');
const reviewFeedback = ref('');
const reviewErrorMessage = ref('');
const reviewSaving = ref(false);
const reviewLogsLoading = ref(false);
const reviewManualScore = ref('');
const reviewFinalScore = ref('');
const reviewComment = ref('');
const reevaluationReason = ref('');
const reevaluationSubmitting = ref(false);
const reviewLogs = ref<HomeworkReviewLog[]>([]);
const attachmentDownloading = ref(false);
const attachmentDownloadError = ref('');
const attachmentDownloadFeedback = ref('');
const studentKeyword = ref('');
const submitStatusFilter = ref<'' | HomeworkSubmitStatus>('');
const evaluationStatusFilter = ref<'' | HomeworkEvaluationStatus>('');
const reviewStatusFilter = ref<'' | HomeworkReviewStatus>('');

const submitStatusOptions: HomeworkSubmitStatus[] = ['SUBMITTED', 'LATE', 'REJECTED'];
const evaluationStatusOptions: HomeworkEvaluationStatus[] = [
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
const reviewStatusOptions: HomeworkReviewStatus[] = ['UNREVIEWED', 'REVIEWED', 'NEED_REVIEW'];

let contextVersion = 0;
let submissionsRequestVersion = 0;
let detailRequestVersion = 0;
let reviewLogsRequestVersion = 0;
let mutationRequestVersion = 0;
let attachmentDownloadRequestVersion = 0;

const isTeacher = computed(() => props.role === 'teacher');
const backHref = computed(() => isTeacher.value
  ? `/courses/${props.courseId}/homeworks/manage`
  : `/courses/${props.courseId}/homeworks/${props.homeworkId}`);
const latestSubmission = computed(() => submissions.value[0] ?? null);
const effectiveSubmission = computed(() => submissions.value.find((item) => item.final) ?? null);
const studentColumns: DataTableColumn[] = [
  { key: 'version', label: '版本', width: '180px' },
  { key: 'submitStatus', label: '提交状态' },
  { key: 'evaluationStatus', label: '评测状态' },
  { key: 'reviewStatus', label: '批阅状态' },
  { key: 'score', label: '得分', align: 'end' },
  { key: 'submittedAt', label: '提交时间' },
  { key: 'action', label: '操作', align: 'end' }
];
const studentRows = computed<DataTableRow[]>(() => submissions.value.map((item) => ({
  submissionId: item.submissionId,
  version: item.version,
  isLatest: item.submissionId === latestSubmission.value?.submissionId,
  isEffective: item.final,
  submitStatus: item.submitStatus,
  submitLabel: formatSubmitStatus(item.submitStatus),
  submitTone: submitTone(item.submitStatus),
  evaluationStatus: item.evaluationStatus,
  evaluationLabel: formatEvaluationStatus(item.evaluationStatus),
  evaluationTone: evaluationTone(item.evaluationStatus),
  reviewStatus: item.reviewStatus,
  reviewLabel: formatReviewStatus(item.reviewStatus),
  reviewTone: reviewTone(item.reviewStatus),
  score: formatScore(item.finalScore ?? item.autoScore),
  submittedAt: formatDateTime(item.submittedAt),
  action: item.submissionId
})));
const studentSummaryItems = computed<SummaryStripItem[]>(() => {
  const evaluatingCount = submissions.value.filter((item) => ['PENDING', 'RUNNING'].includes(item.evaluationStatus)).length;
  return [
    { key: 'total', label: '已提交版本', value: total.value, hint: '仅展示我的提交', tone: 'brand' },
    {
      key: 'latest',
      label: '最新版本',
      value: latestSubmission.value ? `版本 ${latestSubmission.value.version}` : '—',
      hint: '按版本号与提交时间确定'
    },
    {
      key: 'effective',
      label: '当前有效',
      value: effectiveSubmission.value ? `版本 ${effectiveSubmission.value.version}` : '—',
      hint: '作为当前有效答案',
      tone: effectiveSubmission.value ? 'success' : 'neutral'
    },
    {
      key: 'evaluating',
      label: '评测处理中',
      value: evaluatingCount,
      hint: evaluatingCount ? '结果页会继续跟踪' : '当前没有等待任务',
      tone: evaluatingCount ? 'warning' : 'neutral'
    }
  ];
});

onMounted(resetContextAndReload);
watch(
  () => [props.courseId, props.homeworkId, props.role] as const,
  resetContextAndReload
);
onBeforeUnmount(invalidateContext);

function resetContextAndReload() {
  invalidateContext();
  page.value = 1;
  size.value = 20;
  total.value = 0;
  submissions.value = [];
  detail.value = null;
  selectedSubmissionId.value = null;
  loading.value = false;
  detailLoading.value = false;
  errorMessage.value = '';
  detailErrorMessage.value = '';
  reviewFeedback.value = '';
  reviewErrorMessage.value = '';
  reviewSaving.value = false;
  reviewLogsLoading.value = false;
  reevaluationSubmitting.value = false;
  reviewManualScore.value = '';
  reviewFinalScore.value = '';
  reviewComment.value = '';
  reevaluationReason.value = '';
  reviewLogs.value = [];
  attachmentDownloading.value = false;
  attachmentDownloadError.value = '';
  attachmentDownloadFeedback.value = '';
  studentKeyword.value = '';
  submitStatusFilter.value = '';
  evaluationStatusFilter.value = '';
  reviewStatusFilter.value = '';
  void loadSubmissions();
}

function invalidateContext() {
  contextVersion += 1;
  submissionsRequestVersion += 1;
  detailRequestVersion += 1;
  reviewLogsRequestVersion += 1;
  mutationRequestVersion += 1;
  attachmentDownloadRequestVersion += 1;
}

async function loadSubmissions() {
  const context = contextVersion;
  const request = ++submissionsRequestVersion;
  const homeworkId = props.homeworkId;
  const teacherContext = isTeacher.value;
  loading.value = true;
  errorMessage.value = '';
  try {
    if (teacherContext) {
      const query: HomeworkSubmissionListQuery = {
        page: page.value,
        size: size.value
      };
      if (studentKeyword.value.trim()) {
        query.studentKeyword = studentKeyword.value.trim();
      }
      if (submitStatusFilter.value) {
        query.submitStatus = submitStatusFilter.value;
      }
      if (evaluationStatusFilter.value) {
        query.evaluationStatus = evaluationStatusFilter.value;
      }
      if (reviewStatusFilter.value) {
        query.reviewStatus = reviewStatusFilter.value;
      }
      const result = await listHomeworkSubmissions(homeworkId, query);
      if (!isCurrentRequest(context, request, submissionsRequestVersion)) {
        return;
      }
      submissions.value = result.list;
      total.value = result.total;
      page.value = result.page;
      size.value = result.size;
    } else {
      const result = await listMyHomeworkSubmissions(homeworkId);
      if (!isCurrentRequest(context, request, submissionsRequestVersion)) {
        return;
      }
      submissions.value = result
        .slice()
        .sort(compareStudentSubmissions);
      total.value = submissions.value.length;
    }
  } catch (error) {
    if (isCurrentRequest(context, request, submissionsRequestVersion)) {
      errorMessage.value = error instanceof Error ? error.message : '提交历史加载失败';
    }
  } finally {
    if (isCurrentRequest(context, request, submissionsRequestVersion)) {
      loading.value = false;
    }
  }
}

async function openDetail(submissionId: number) {
  const context = contextVersion;
  const request = ++detailRequestVersion;
  const teacherContext = isTeacher.value;
  mutationRequestVersion += 1;
  attachmentDownloadRequestVersion += 1;
  selectedSubmissionId.value = submissionId;
  detailLoading.value = true;
  detail.value = null;
  detailErrorMessage.value = '';
  reviewFeedback.value = '';
  reviewErrorMessage.value = '';
  reviewSaving.value = false;
  reevaluationSubmitting.value = false;
  reevaluationReason.value = '';
  reviewLogs.value = [];
  attachmentDownloading.value = false;
  attachmentDownloadError.value = '';
  attachmentDownloadFeedback.value = '';
  try {
    const result = await getHomeworkSubmission(submissionId);
    if (!isCurrentRequest(context, request, detailRequestVersion)) {
      return;
    }
    detail.value = result;
    resetReviewForm(result);
    if (teacherContext) {
      await loadReviewLogs(submissionId, context);
    }
  } catch (error) {
    if (isCurrentRequest(context, request, detailRequestVersion)) {
      detailErrorMessage.value = error instanceof Error ? error.message : '提交详情加载失败';
    }
  } finally {
    if (isCurrentRequest(context, request, detailRequestVersion)) {
      detailLoading.value = false;
    }
  }
}

async function goToPage(nextPage: number) {
  if (!isTeacher.value || nextPage < 1 || nextPage > totalPages.value) {
    return;
  }
  page.value = nextPage;
  await loadSubmissions();
}

async function applyFilters() {
  page.value = 1;
  detailRequestVersion += 1;
  reviewLogsRequestVersion += 1;
  mutationRequestVersion += 1;
  attachmentDownloadRequestVersion += 1;
  selectedSubmissionId.value = null;
  detail.value = null;
  detailLoading.value = false;
  detailErrorMessage.value = '';
  reviewLogs.value = [];
  await loadSubmissions();
}

async function saveReview() {
  if (!detail.value || !isTeacher.value) {
    return;
  }
  const context = contextVersion;
  const request = ++mutationRequestVersion;
  const submissionId = detail.value.submissionId;
  reviewSaving.value = true;
  reviewFeedback.value = '';
  reviewErrorMessage.value = '';
  try {
    const manualScore = parseReviewScore(reviewManualScore.value, 'manualScore');
    const finalScore = parseReviewScore(reviewFinalScore.value, 'finalScore');
    const updated = await reviewHomeworkSubmission(submissionId, {
      manualScore,
      finalScore,
      comment: reviewComment.value.trim() || null
    });
    if (!isCurrentMutation(context, request, submissionId)) {
      return;
    }
    detail.value = updated;
    resetReviewForm(updated);
    submissions.value = submissions.value.map((item) => item.submissionId === updated.submissionId ? updated : item);
    reviewFeedback.value = '批阅已保存';
    await loadReviewLogs(updated.submissionId, context);
  } catch (error) {
    if (isCurrentMutation(context, request, submissionId)) {
      reviewErrorMessage.value = error instanceof Error ? error.message : '批阅失败';
    }
  } finally {
    if (isCurrentMutation(context, request, submissionId)) {
      reviewSaving.value = false;
    }
  }
}

async function triggerReevaluation() {
  if (!detail.value || !isTeacher.value) {
    return;
  }
  const reason = reevaluationReason.value.trim();
  if (!reason) {
    reviewErrorMessage.value = '请填写重评原因';
    return;
  }
  const context = contextVersion;
  const request = ++mutationRequestVersion;
  const submissionId = detail.value.submissionId;
  reevaluationSubmitting.value = true;
  reviewFeedback.value = '';
  reviewErrorMessage.value = '';
  try {
    await reevaluateHomeworkSubmission(submissionId, reason);
    if (!isCurrentMutation(context, request, submissionId)) {
      return;
    }
    const refreshed = await getHomeworkSubmission(submissionId);
    if (!isCurrentMutation(context, request, submissionId)) {
      return;
    }
    detail.value = refreshed;
    resetReviewForm(refreshed);
    submissions.value = submissions.value.map((item) => item.submissionId === refreshed.submissionId ? refreshed : item);
    reevaluationReason.value = '';
    reviewFeedback.value = '重评完成';
    await loadReviewLogs(refreshed.submissionId, context);
  } catch (error) {
    if (isCurrentMutation(context, request, submissionId)) {
      reviewErrorMessage.value = error instanceof Error ? error.message : '重评失败';
    }
  } finally {
    if (isCurrentMutation(context, request, submissionId)) {
      reevaluationSubmitting.value = false;
    }
  }
}

async function downloadSelectedAttachment() {
  const current = detail.value;
  if (!current?.attachment?.downloadAvailable || attachmentDownloading.value) {
    return;
  }
  const context = contextVersion;
  const request = ++attachmentDownloadRequestVersion;
  const submissionId = current.submissionId;
  const homeworkId = props.homeworkId;
  attachmentDownloading.value = true;
  attachmentDownloadError.value = '';
  attachmentDownloadFeedback.value = '';
  try {
    const result = await downloadHomeworkSubmissionAttachment(homeworkId, submissionId);
    if (
      !isCurrentRequest(context, request, attachmentDownloadRequestVersion)
      || detail.value?.submissionId !== submissionId
    ) {
      return;
    }
    triggerBrowserDownload(result.blob, result.filename || current.attachment.originalFilename);
    attachmentDownloadFeedback.value = `已开始下载 ${result.filename || current.attachment.originalFilename}`;
  } catch (error) {
    if (isCurrentRequest(context, request, attachmentDownloadRequestVersion)) {
      attachmentDownloadError.value = error instanceof Error ? error.message : '附件下载失败，请重试';
    }
  } finally {
    if (request === attachmentDownloadRequestVersion) {
      attachmentDownloading.value = false;
    }
  }
}

async function loadReviewLogs(submissionId: number, expectedContext = contextVersion) {
  const request = ++reviewLogsRequestVersion;
  reviewLogsLoading.value = true;
  reviewErrorMessage.value = '';
  try {
    const result = await getHomeworkSubmissionReviewLogs(submissionId);
    if (!isCurrentRequest(expectedContext, request, reviewLogsRequestVersion)) {
      return;
    }
    reviewLogs.value = result;
  } catch (error) {
    if (isCurrentRequest(expectedContext, request, reviewLogsRequestVersion)) {
      reviewLogs.value = [];
      reviewErrorMessage.value = error instanceof Error ? error.message : '批阅日志加载失败';
    }
  } finally {
    if (isCurrentRequest(expectedContext, request, reviewLogsRequestVersion)) {
      reviewLogsLoading.value = false;
    }
  }
}

function resetReviewForm(submission: HomeworkSubmissionDetail) {
  reviewManualScore.value = submission.manualScore == null ? '' : String(submission.manualScore);
  reviewFinalScore.value = submission.finalScore == null ? '' : String(submission.finalScore);
  reviewComment.value = submission.comment ?? '';
}

function parseReviewScore(value: string, field: string) {
  const text = String(value).trim();
  if (!text) {
    throw new Error(`${field === 'manualScore' ? '人工分' : '最终分'}不能为空`);
  }
  const score = Number(text);
  if (!Number.isFinite(score)) {
    throw new Error(`${field === 'manualScore' ? '人工分' : '最终分'}格式不正确`);
  }
  return score;
}

function formatDateTime(value: string) {
  return value.replace('T', ' ').slice(0, 16);
}

function formatScore(value: number | null | undefined) {
  return value ?? '未生成';
}

function formatStudentAnswer(submission: HomeworkSubmissionDetail) {
  if (submission.submitType === 'OBJECTIVE') {
    return formatObjectiveAnswer(submission.answerJson);
  }
  return submission.answerText
    || submission.answerJson
    || '本次提交没有可展示的文本内容';
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

function formatObjectiveAnswer(value: string | null | undefined) {
  if (!value) {
    return '本次提交未记录客观题答案';
  }
  try {
    const parsed: unknown = JSON.parse(value);
    const entries = Array.isArray(parsed)
      ? parsed.map((answer, index) => [String(index + 1), answer] as const)
      : isPlainRecord(parsed)
        ? Object.entries(parsed)
        : [];
    if (entries.length === 0) {
      return '本次提交未记录客观题答案';
    }
    return entries
      .map(([question, answer]) => `题目 ${question}：${formatObjectiveChoice(answer)}`)
      .join('\n');
  } catch {
    return '客观题答案暂时无法解析，请在结果页确认本次记录。';
  }
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
    const label = String(value).trim();
    if (!label) {
      return [];
    }
    if (label.toLowerCase() === 'true') {
      return ['正确'];
    }
    if (label.toLowerCase() === 'false') {
      return ['错误'];
    }
    return [label];
  }
  return [];
}

function isPlainRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isCurrentRequest(expectedContext: number, request: number, latestRequest: number) {
  return expectedContext === contextVersion && request === latestRequest;
}

function isCurrentMutation(expectedContext: number, request: number, submissionId: number) {
  return isCurrentRequest(expectedContext, request, mutationRequestVersion)
    && selectedSubmissionId.value === submissionId;
}

function compareStudentSubmissions(left: HomeworkSubmissionSummary, right: HomeworkSubmissionSummary) {
  if (left.version !== right.version) {
    return right.version - left.version;
  }
  return new Date(right.submittedAt).getTime() - new Date(left.submittedAt).getTime();
}

function submitTone(status: HomeworkSubmitStatus): StatusBadgeTone {
  if (status === 'REJECTED') {
    return 'danger';
  }
  return status === 'LATE' ? 'warning' : 'success';
}

function evaluationTone(status: HomeworkEvaluationStatus): StatusBadgeTone {
  if (status === 'ACCEPTED') {
    return 'success';
  }
  if (status === 'PENDING' || status === 'RUNNING') {
    return 'info';
  }
  if (status === 'NONE') {
    return 'neutral';
  }
  return 'danger';
}

function reviewTone(status: HomeworkReviewStatus): StatusBadgeTone {
  if (status === 'REVIEWED') {
    return 'success';
  }
  return status === 'NEED_REVIEW' ? 'warning' : 'neutral';
}

function toneValue(value: unknown): StatusBadgeTone {
  const tones: StatusBadgeTone[] = ['neutral', 'brand', 'success', 'warning', 'danger', 'info'];
  return typeof value === 'string' && tones.includes(value as StatusBadgeTone)
    ? value as StatusBadgeTone
    : 'neutral';
}

function studentRowLabel(row: DataTableRow) {
  return `版本 ${String(row.version)}，${String(row.submitLabel)}`;
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)));
</script>

<style scoped>
.hwk-history {
  display: grid;
  gap: 16px;
  color: var(--oj-ink);
  min-height: 100vh;
  padding-bottom: 40px;
}

.hwk-history--teacher {
  display: block;
  padding: 24px;
  background: #f6f8fb;
  color: #1f2937;
}

.hwk-history__student-workspace {
  display: grid;
  gap: 16px;
  min-width: 0;
  padding: 20px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  box-shadow: var(--oj-shadow-soft);
  backdrop-filter: var(--oj-blur);
  -webkit-backdrop-filter: var(--oj-blur);
}

.hwk-history__primary-link,
.hwk-history__row-actions a,
.hwk-history__row-actions button {
  min-height: 40px;
  box-sizing: border-box;
  border: 1px solid var(--oj-brand);
  border-radius: var(--oj-radius-control);
  background: var(--oj-brand);
  color: #fff;
  cursor: pointer;
  font: inherit;
  font-size: 0.82rem;
  font-weight: 800;
  line-height: 1.4;
  padding: 9px 12px;
  text-align: center;
  text-decoration: none;
}

.hwk-history__row-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.hwk-history__row-actions button {
  border-color: var(--oj-line-strong);
  background: var(--oj-surface-strong);
  color: var(--oj-brand);
}

.hwk-history__version-cell,
.hwk-history__markers {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
}

.hwk-history__version-cell {
  align-items: flex-start;
  flex-direction: column;
}

.hwk-history__student-detail {
  min-width: 0;
}

.hwk-history__detail-card,
.hwk-history__detail-hint {
  margin: 0;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface-strong);
  padding: 18px;
}

.hwk-history__detail-card header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.hwk-history__detail-card header p,
.hwk-history__detail-card h2,
.hwk-history__detail-card dl,
.hwk-history__detail-card dd,
.hwk-history__comment {
  margin: 0;
}

.hwk-history__detail-card header p,
.hwk-history__detail-card dt,
.hwk-history__detail-hint {
  color: var(--oj-muted);
  font-size: 0.78rem;
  font-weight: 700;
}

.hwk-history__detail-card h2 {
  margin-top: 4px;
  font-size: 1.18rem;
}

.hwk-history__detail-card dl {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.hwk-history__detail-card dl div {
  min-width: 0;
  padding-left: 11px;
  border-left: 2px solid var(--oj-line-strong);
}

.hwk-history__detail-card dd {
  margin-top: 4px;
  overflow-wrap: anywhere;
  font-weight: 800;
}

.hwk-history__comment {
  color: var(--oj-ink-soft);
  line-height: 1.65;
}

.hwk-history__panel {
  background: #ffffff;
  border: 1px solid #d7dde8;
  border-radius: 8px;
  display: grid;
  gap: 20px;
  margin: 0 auto;
  max-width: 1100px;
  padding: 24px;
}

.hwk-history__header,
.hwk-history__item-head,
.hwk-history__pager,
.hwk-history__content {
  display: flex;
  gap: 16px;
}

.hwk-history__header,
.hwk-history__item-head {
  align-items: center;
  justify-content: space-between;
}

.hwk-history__content {
  align-items: flex-start;
}

.hwk-history__list {
  display: grid;
  flex: 1;
  gap: 12px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.hwk-history__item,
.hwk-history__detail {
  background: #f8fafc;
  border: 1px solid #d7dde8;
  border-radius: 8px;
  padding: 16px;
}

.hwk-history__detail {
  min-width: 320px;
  width: 360px;
}

.hwk-history__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hwk-history__pager {
  align-items: center;
  flex-wrap: wrap;
}

.hwk-history__filters {
  align-items: end;
  display: grid;
  column-gap: 18px;
  row-gap: 12px;
  grid-template-columns: minmax(180px, 1fr) repeat(3, minmax(190px, 1fr)) minmax(120px, 0.7fr);
}

.hwk-history__filters label {
  display: grid;
  gap: 6px;
}

.hwk-history__filters input,
.hwk-history__filters select {
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 8px;
}

.hwk-history__tags span {
  background: #e9effb;
  border-radius: 999px;
  color: #175cd3;
  padding: 4px 10px;
}

.hwk-history__answer {
  background: #111827;
  border-radius: 8px;
  color: #f8fafc;
  overflow-x: auto;
  padding: 12px;
  white-space: pre-wrap;
}

.hwk-history__attachment {
  display: grid;
  gap: 8px;
  min-width: 0;
  margin-top: 14px;
  padding: 14px;
  border: 1px solid color-mix(in srgb, var(--oj-brand) 24%, var(--oj-line));
  border-radius: calc(var(--oj-radius) - 2px);
  background: color-mix(in srgb, var(--oj-brand) 6%, var(--oj-surface-strong));
}

.hwk-history__attachment strong,
.hwk-history__attachment span {
  overflow-wrap: anywhere;
}

.hwk-history__attachment span {
  color: var(--oj-muted);
  font-size: 0.8rem;
}

.hwk-history__attachment button {
  justify-self: start;
  min-height: 40px;
  padding: 8px 13px;
  border: 1px solid var(--oj-brand);
  border-radius: 9px;
  background: var(--oj-brand);
  color: #fff;
  cursor: pointer;
  font: inherit;
  font-weight: 800;
}

.hwk-history__attachment button:disabled {
  cursor: wait;
  opacity: 0.62;
}

.hwk-history__attachment p {
  margin: 0;
  line-height: 1.5;
}

.hwk-history__review,
.hwk-history__review form,
.hwk-history__logs,
.hwk-history__logs ul {
  display: grid;
  gap: 10px;
}

.hwk-history__review {
  border-top: 1px solid #d7dde8;
  margin-top: 16px;
  padding-top: 16px;
}

.hwk-history__review label {
  display: grid;
  gap: 6px;
}

.hwk-history__review input,
.hwk-history__review textarea {
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  padding: 8px;
}

.hwk-history__logs ul {
  list-style: none;
  margin: 0;
  padding: 0;
}

.hwk-history__logs li {
  border: 1px solid #d7dde8;
  border-radius: 6px;
  display: grid;
  gap: 4px;
  padding: 10px;
}

.hwk-history__back {
  color: #175cd3;
  text-decoration: none;
}

.hwk-history__feedback {
  color: #067647;
}

.hwk-history__error {
  color: #b42318;
}

.hwk-history__empty {
  color: #667085;
}

@media (max-width: 900px) {
  .hwk-history__filters {
    grid-template-columns: 1fr;
  }

  .hwk-history__content {
    flex-direction: column;
  }

  .hwk-history__detail {
    min-width: 0;
    width: 100%;
  }
}

@media (max-width: 760px) {
  .hwk-history {
    gap: 10px;
  }

  .hwk-history :deep(.foundation-page-header) {
    gap: 10px;
    padding: 14px;
  }

  .hwk-history :deep(.foundation-page-header__subtitle) {
    margin-top: 6px;
    font-size: 0.84rem;
    line-height: 1.5;
  }

  .hwk-history :deep(.summary-strip) {
    display: flex;
    gap: 8px;
    overflow-x: auto;
    padding-bottom: 2px;
    scroll-snap-type: x proximity;
  }

  .hwk-history :deep(.summary-strip__item) {
    flex: 0 0 135px;
    padding: 10px 11px;
    scroll-snap-align: start;
  }

  .hwk-history__student-workspace {
    padding: 12px;
  }

  .hwk-history__detail-card header,
  .hwk-history__row-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .hwk-history__detail-card dl {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .hwk-history__row-actions a,
  .hwk-history__row-actions button,
  .hwk-history__attachment button {
    width: 100%;
  }

  .hwk-history__attachment button {
    justify-self: stretch;
  }
}
</style>
