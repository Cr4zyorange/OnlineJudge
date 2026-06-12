<template>
  <main class="hwk-history">
    <section class="hwk-history__panel" aria-label="提交历史">
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
            <p>附件：{{ detail.fileUrl ?? '无' }}</p>
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
import { computed, onMounted, ref } from 'vue';
import {
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

const isTeacher = computed(() => props.role === 'teacher');
const backHref = computed(() => `/courses/${props.courseId}/homeworks/${props.homeworkId}?role=${props.role}`);

onMounted(loadSubmissions);

async function loadSubmissions() {
  loading.value = true;
  errorMessage.value = '';
  try {
    if (isTeacher.value) {
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
      const result = await listHomeworkSubmissions(props.homeworkId, query);
      submissions.value = result.list;
      total.value = result.total;
      page.value = result.page;
      size.value = result.size;
    } else {
      submissions.value = await listMyHomeworkSubmissions(props.homeworkId);
      total.value = submissions.value.length;
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '提交历史加载失败';
  } finally {
    loading.value = false;
  }
}

async function openDetail(submissionId: number) {
  detailLoading.value = true;
  detailErrorMessage.value = '';
  reviewFeedback.value = '';
  reviewErrorMessage.value = '';
  reevaluationReason.value = '';
  reviewLogs.value = [];
  try {
    detail.value = await getHomeworkSubmission(submissionId);
    resetReviewForm(detail.value);
    if (isTeacher.value) {
      await loadReviewLogs(submissionId);
    }
  } catch (error) {
    detailErrorMessage.value = error instanceof Error ? error.message : '提交详情加载失败';
  } finally {
    detailLoading.value = false;
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
  detail.value = null;
  reviewLogs.value = [];
  await loadSubmissions();
}

async function saveReview() {
  if (!detail.value || !isTeacher.value) {
    return;
  }
  reviewSaving.value = true;
  reviewFeedback.value = '';
  reviewErrorMessage.value = '';
  try {
    const manualScore = parseReviewScore(reviewManualScore.value, 'manualScore');
    const finalScore = parseReviewScore(reviewFinalScore.value, 'finalScore');
    const updated = await reviewHomeworkSubmission(detail.value.submissionId, {
      manualScore,
      finalScore,
      comment: reviewComment.value.trim() || null
    });
    detail.value = updated;
    resetReviewForm(updated);
    submissions.value = submissions.value.map((item) => item.submissionId === updated.submissionId ? updated : item);
    reviewFeedback.value = '批阅已保存';
    await loadReviewLogs(updated.submissionId);
  } catch (error) {
    reviewErrorMessage.value = error instanceof Error ? error.message : '批阅失败';
  } finally {
    reviewSaving.value = false;
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
  reevaluationSubmitting.value = true;
  reviewFeedback.value = '';
  reviewErrorMessage.value = '';
  try {
    await reevaluateHomeworkSubmission(detail.value.submissionId, reason);
    const refreshed = await getHomeworkSubmission(detail.value.submissionId);
    detail.value = refreshed;
    resetReviewForm(refreshed);
    submissions.value = submissions.value.map((item) => item.submissionId === refreshed.submissionId ? refreshed : item);
    reevaluationReason.value = '';
    reviewFeedback.value = '重评完成';
    await loadReviewLogs(refreshed.submissionId);
  } catch (error) {
    reviewErrorMessage.value = error instanceof Error ? error.message : '重评失败';
  } finally {
    reevaluationSubmitting.value = false;
  }
}

async function loadReviewLogs(submissionId: number) {
  reviewLogsLoading.value = true;
  reviewErrorMessage.value = '';
  try {
    reviewLogs.value = await getHomeworkSubmissionReviewLogs(submissionId);
  } catch (error) {
    reviewLogs.value = [];
    reviewErrorMessage.value = error instanceof Error ? error.message : '批阅日志加载失败';
  } finally {
    reviewLogsLoading.value = false;
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

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)));
</script>

<style scoped>
.hwk-history {
  background: #f6f8fb;
  color: #1f2937;
  min-height: 100vh;
  padding: 24px;
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
</style>
