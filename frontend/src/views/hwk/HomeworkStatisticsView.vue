<template>
  <main class="homework-statistics" aria-labelledby="homework-statistics-title">
    <PageHeader
      id="homework-statistics-title"
      :title="detail?.title ?? '作业完成情况'"
      eyebrow="教师作业统计"
      subtitle="汇总提交、评测与批阅进度，并定位需要跟进的学生。"
    >
      <template #actions>
        <RouterLink class="button button--quiet" :to="teacherDetailRoute">返回作业详情</RouterLink>
        <RouterLink class="button button--secondary" :to="submissionWorkspaceRoute">查看提交队列</RouterLink>
        <button
          v-if="detail && statistics"
          class="button button--primary"
          data-action="refresh-statistics"
          type="button"
          :disabled="loading"
          @click="loadPage"
        >
          {{ loading ? '正在刷新…' : '刷新数据' }}
        </button>
      </template>
    </PageHeader>

    <PageState
      v-if="loading"
      state="loading"
      title="正在加载作业统计"
      message="正在同步作业、提交与课程学生信息。"
    />
    <PageState
      v-else-if="errorMessage"
      state="error"
      title="作业统计暂时无法加载"
      :message="errorMessage"
      retry-label="重新加载"
      @retry="loadPage"
    />

    <template v-else-if="detail && statistics">
      <section class="context-strip" aria-label="作业概要">
        <div>
          <span>作业状态</span>
          <strong>{{ statusLabel(detail.status) }}</strong>
        </div>
        <div>
          <span>作业类型</span>
          <strong>{{ typeLabel(detail.type) }}</strong>
        </div>
        <div>
          <span>满分</span>
          <strong>{{ numberLabel(detail.totalScore) }} 分</strong>
        </div>
        <div>
          <span>截止时间</span>
          <strong>{{ dateTimeLabel(detail.deadline) }}</strong>
        </div>
      </section>

      <section class="summary-grid" aria-label="作业统计概览">
        <article class="summary-card" data-testid="summary-total">
          <span>课程学生</span>
          <strong>{{ statistics.totalStudentCount }}</strong>
          <small>人</small>
        </article>
        <article class="summary-card summary-card--success" data-testid="summary-submitted">
          <span>已提交</span>
          <strong>{{ statistics.submittedCount }}</strong>
          <small>人</small>
        </article>
        <article class="summary-card summary-card--warning" data-testid="summary-unsubmitted">
          <span>未提交</span>
          <strong>{{ statistics.unsubmittedCount }}</strong>
          <small>人</small>
        </article>
        <article class="summary-card" data-testid="summary-evaluated">
          <span>已完成评测</span>
          <strong>{{ statistics.evaluatedCount }}</strong>
          <small>人</small>
        </article>
        <article class="summary-card" data-testid="summary-reviewed">
          <span>已完成批阅</span>
          <strong>{{ statistics.reviewedCount }}</strong>
          <small>人</small>
        </article>
        <article class="summary-card" data-testid="summary-pending-evaluation">
          <span>待评测</span>
          <strong>{{ statistics.pendingEvaluationCount }}</strong>
          <small>份</small>
        </article>
        <article class="summary-card" data-testid="summary-pending-review">
          <span>待批阅</span>
          <strong>{{ statistics.pendingReviewCount }}</strong>
          <small>份</small>
        </article>
        <article class="summary-card" data-testid="summary-scored">
          <span>已有成绩</span>
          <strong>{{ statistics.scoredCount }}</strong>
          <small>份</small>
        </article>
        <article class="summary-card" data-testid="summary-submission-rate">
          <span>提交率</span>
          <strong>{{ percentLabel(statistics.submittedCount, statistics.totalStudentCount) }}</strong>
        </article>
        <article class="summary-card" data-testid="summary-evaluation-rate">
          <span>评测完成率</span>
          <strong>{{ applicablePercentLabel(statistics.evaluatedCount, statistics.autoEvaluableCount) }}</strong>
          <small>{{ statistics.evaluatedCount }} / {{ statistics.autoEvaluableCount }}</small>
        </article>
        <article class="summary-card" data-testid="summary-review-rate">
          <span>批阅完成率</span>
          <strong>{{ percentLabel(statistics.reviewedCount, statistics.submittedCount) }}</strong>
        </article>
      </section>

      <div class="statistics-layout">
        <section class="work-surface score-panel" aria-labelledby="score-summary-title">
          <div class="section-heading">
            <div>
              <p>SCORE SUMMARY</p>
              <h2 id="score-summary-title">成绩概览</h2>
            </div>
            <span data-testid="statistics-generated-at">生成于 {{ dateTimeLabel(statistics.generatedAt) }}</span>
          </div>
          <div class="score-grid">
            <article>
              <span>平均分</span>
              <strong>{{ scoreLabel(statistics.averageScore) }}</strong>
            </article>
            <article>
              <span>最高分</span>
              <strong>{{ scoreLabel(statistics.maxScore) }}</strong>
            </article>
            <article>
              <span>最低分</span>
              <strong>{{ scoreLabel(statistics.minScore) }}</strong>
            </article>
          </div>
          <ul class="score-distribution" aria-label="成绩分布">
            <li
              v-for="bucket in scoreBuckets"
              :key="bucket.key"
              :data-score-bucket="bucket.key"
            >
              <span class="score-distribution__label">{{ bucket.label }}</span>
              <span class="score-distribution__track" aria-hidden="true">
                <span :style="{ width: `${bucketPercent(bucket.count)}%` }"></span>
              </span>
              <strong>{{ bucket.count }} 人</strong>
            </li>
          </ul>
          <p class="score-panel__note">共 {{ statistics.scoredCount }} 份有效成绩；无成绩提交不进入分布。</p>
        </section>

        <section class="work-surface follow-up-panel" :aria-labelledby="followUpTitleId">
          <nav class="follow-up-tabs" aria-label="待处理名单">
            <RouterLink
              v-for="tab in followUpTabs"
              :key="tab.key"
              :to="tab.route"
              :aria-current="tab.active ? 'page' : undefined"
              :class="{ 'follow-up-tab--active': tab.active }"
            >{{ tab.label }} {{ tab.count }}</RouterLink>
          </nav>
          <div class="section-heading">
            <div>
              <p>FOLLOW UP</p>
              <h2 :id="followUpTitleId">{{ followUpTitle }}</h2>
            </div>
            <span>{{ followUpTotal }} 人</span>
          </div>

          <p v-if="studentNameWarning" class="inline-warning" role="status">
            {{ studentNameWarning }}
          </p>

          <div v-if="followUpStudents.length === 0" class="empty-follow-up">
            <strong>{{ followUpEmptyTitle }}</strong>
            <p>{{ followUpEmptyMessage }}</p>
          </div>
          <ul v-else class="student-list" :aria-label="followUpListLabel">
            <li v-for="student in followUpStudents" :key="student.key">
              <RouterLink
                v-if="student.submissionId"
                class="student-list__link"
                :to="reviewRoute(student.submissionId)"
              >
                <span class="student-avatar" aria-hidden="true">{{ student.initial }}</span>
                <span class="student-list__copy">
                  <strong>{{ student.name }}</strong>
                  <span>{{ student.status }}</span>
                </span>
              </RouterLink>
              <template v-else>
                <span class="student-avatar" aria-hidden="true">{{ student.initial }}</span>
                <div>
                  <strong>{{ student.name }}</strong>
                  <span>{{ student.status }}</span>
                </div>
              </template>
            </li>
          </ul>

          <nav v-if="totalPages > 1" class="pagination" :aria-label="followUpPaginationLabel">
            <button
              type="button"
              :data-action="activeAttention ? 'previous-follow-up-page' : 'previous-unsubmitted-page'"
              :disabled="currentPage <= 1 || loading"
              @click="changePage(currentPage - 1)"
            >
              上一页
            </button>
            <span>第 {{ currentPage }} / {{ totalPages }} 页</span>
            <button
              type="button"
              :data-action="activeAttention ? 'next-follow-up-page' : 'next-unsubmitted-page'"
              :disabled="currentPage >= totalPages || loading"
              @click="changePage(currentPage + 1)"
            >
              下一页
            </button>
          </nav>

          <RouterLink class="button button--primary follow-up-panel__queue" :to="submissionWorkspaceRoute">
            {{ activeAttention ? '在提交队列中继续处理' : '查看全部提交' }}
          </RouterLink>
        </section>
      </div>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import {
  getHomeworkDetail,
  getHomeworkStatistics,
  listHomeworkSubmissions
} from '../../api/hwk/homeworks';
import { getTeacherLearningProgress } from '../../api/lrn/learningProgress';
import PageHeader from '../../components/foundation/PageHeader.vue';
import PageState from '../../components/foundation/PageState.vue';
import { formatEvaluationStatus, formatReviewStatus } from './hwkDisplay';
import type {
  HomeworkAttention,
  HomeworkDetail,
  HomeworkScoreBucket,
  HomeworkStatistics,
  HomeworkStatus,
  HomeworkSubmissionSummary,
  HomeworkType,
  PageResponse
} from '../../types/hwk';

const props = withDefaults(defineProps<{
  courseId: number;
  homeworkId: number;
  pageSize?: number;
  initialPage?: number;
  initialAttention?: HomeworkAttention;
}>(), {
  pageSize: 20,
  initialPage: 1
});

const router = useRouter();
const detail = ref<HomeworkDetail | null>(null);
const statistics = ref<HomeworkStatistics | null>(null);
const attentionPage = ref<PageResponse<HomeworkSubmissionSummary> | null>(null);
const studentNames = ref<Record<number, string>>({});
const loading = ref(false);
const errorMessage = ref('');
const studentNameWarning = ref('');
const currentPage = ref(normalizePage(props.initialPage));
const activeAttention = ref<HomeworkAttention | null>(normalizeAttention(props.initialAttention));
let activeRequestId = 0;

const scoreBucketDefinitions: { key: HomeworkScoreBucket; label: string }[] = [
  { key: '0-59', label: '0–59 分' },
  { key: '60-69', label: '60–69 分' },
  { key: '70-79', label: '70–79 分' },
  { key: '80-89', label: '80–89 分' },
  { key: '90-100', label: '90–100 分' }
];

const teacherDetailRoute = computed(() => ({
  name: 'homework-manage-detail',
  params: { courseId: props.courseId, homeworkId: props.homeworkId }
}));
const submissionWorkspaceRoute = computed(() => ({
  name: 'homework-submission-workspace',
  params: { courseId: props.courseId, homeworkId: props.homeworkId },
  ...(activeAttention.value ? { query: buildAttentionQuery(activeAttention.value, currentPage.value) } : {})
}));
const scoreBuckets = computed(() => scoreBucketDefinitions.map((bucket) => ({
  ...bucket,
  count: statistics.value?.scoreDistribution[bucket.key] ?? 0
})));
const maxBucketCount = computed(() => Math.max(1, ...scoreBuckets.value.map((bucket) => bucket.count)));
const followUpTotal = computed(() => activeAttention.value
  ? attentionPage.value?.total ?? 0
  : statistics.value?.unsubmittedTotal ?? 0);
const followUpSize = computed(() => activeAttention.value
  ? attentionPage.value?.size ?? props.pageSize
  : statistics.value?.unsubmittedSize ?? props.pageSize);
const totalPages = computed(() => Math.max(
  1,
  Math.ceil(followUpTotal.value / Math.max(1, followUpSize.value))
));
const unsubmittedStudents = computed(() => (statistics.value?.unsubmittedStudentIds ?? []).map((studentId) => {
  const synchronizedName = studentNames.value[studentId]?.trim();
  return {
    key: `unsubmitted-${studentId}`,
    name: synchronizedName || '姓名暂不可用',
    initial: synchronizedName?.slice(0, 1) || '待',
    status: '待提交',
    submissionId: null
  };
}));
const attentionStudents = computed(() => (attentionPage.value?.list ?? []).map((submission) => {
  const synchronizedName = studentNames.value[submission.studentId]?.trim();
  return {
    key: `submission-${submission.submissionId}`,
    name: synchronizedName || '姓名暂不可用',
    initial: synchronizedName?.slice(0, 1) || '待',
    status: activeAttention.value === 'EVALUATION_PENDING'
      ? formatEvaluationStatus(submission.evaluationStatus)
      : formatReviewStatus(submission.reviewStatus),
    submissionId: submission.submissionId
  };
}));
const followUpStudents = computed(() => activeAttention.value ? attentionStudents.value : unsubmittedStudents.value);
const followUpTitle = computed(() => activeAttention.value === 'EVALUATION_PENDING'
  ? '待评测学生'
  : activeAttention.value === 'REVIEW_PENDING' ? '待批阅学生' : '未提交学生');
const followUpTitleId = computed(() => activeAttention.value === 'EVALUATION_PENDING'
  ? 'evaluation-pending-title'
  : activeAttention.value === 'REVIEW_PENDING' ? 'review-pending-title' : 'unsubmitted-title');
const followUpListLabel = computed(() => activeAttention.value === 'EVALUATION_PENDING'
  ? '待评测学生名单'
  : activeAttention.value === 'REVIEW_PENDING' ? '待批阅学生名单' : '未提交学生名单');
const followUpPaginationLabel = computed(() => `${followUpTitle.value}分页`);
const followUpEmptyTitle = computed(() => activeAttention.value === 'EVALUATION_PENDING'
  ? '暂无待评测提交'
  : activeAttention.value === 'REVIEW_PENDING' ? '暂无待批阅提交' : '全员已提交');
const followUpEmptyMessage = computed(() => activeAttention.value
  ? '当前作业没有符合该待处理口径的有效提交。'
  : '当前作业没有需要跟进的未提交学生。');
const followUpTabs = computed(() => [
  followUpTab(null, '未提交', statistics.value?.unsubmittedCount ?? 0),
  followUpTab('EVALUATION_PENDING', '待评测', statistics.value?.pendingEvaluationCount ?? 0),
  followUpTab('REVIEW_PENDING', '待批阅', statistics.value?.pendingReviewCount ?? 0)
]);

watch(
  () => [props.courseId, props.homeworkId, props.pageSize, props.initialPage, props.initialAttention],
  () => {
    currentPage.value = normalizePage(props.initialPage);
    activeAttention.value = normalizeAttention(props.initialAttention);
    void loadPage();
  },
  { immediate: true }
);

async function loadPage() {
  const requestId = ++activeRequestId;
  const targetCourseId = props.courseId;
  const targetHomeworkId = props.homeworkId;
  const targetAttention = activeAttention.value;
  const targetPage = currentPage.value;
  loading.value = true;
  errorMessage.value = '';
  studentNameWarning.value = '';
  detail.value = null;
  statistics.value = null;
  attentionPage.value = null;
  studentNames.value = {};

  const [detailResult, statisticsResult, progressResult, attentionResult] = await Promise.allSettled([
    getHomeworkDetail(targetHomeworkId),
    getHomeworkStatistics(targetHomeworkId, {
      page: targetAttention ? 1 : targetPage,
      size: props.pageSize
    }),
    getTeacherLearningProgress(targetCourseId),
    targetAttention
      ? listHomeworkSubmissions(targetHomeworkId, {
        attention: targetAttention,
        page: targetPage,
        size: props.pageSize
      })
      : Promise.resolve(null)
  ]);

  if (requestId !== activeRequestId) {
    return;
  }
  if (detailResult.status === 'rejected') {
    errorMessage.value = errorLabel(detailResult.reason, '作业详情加载失败，请稍后重试');
    loading.value = false;
    return;
  }
  if (statisticsResult.status === 'rejected') {
    errorMessage.value = errorLabel(statisticsResult.reason, '作业统计加载失败，请稍后重试');
    loading.value = false;
    return;
  }
  if (attentionResult.status === 'rejected') {
    errorMessage.value = errorLabel(attentionResult.reason, '待处理名单加载失败，请稍后重试');
    loading.value = false;
    return;
  }
  if (detailResult.value.id !== targetHomeworkId || detailResult.value.courseId !== targetCourseId) {
    errorMessage.value = '作业详情归属与当前页面不一致，请重新加载。';
    loading.value = false;
    return;
  }
  if (statisticsResult.value.homeworkId !== targetHomeworkId || statisticsResult.value.courseId !== targetCourseId) {
    errorMessage.value = '作业统计归属与当前页面不一致，请重新加载。';
    loading.value = false;
    return;
  }
  if (progressResult.status === 'fulfilled' && progressResult.value.courseId !== targetCourseId) {
    errorMessage.value = '课程学生数据归属与当前页面不一致，请重新加载。';
    loading.value = false;
    return;
  }
  if (
    targetAttention
    && attentionResult.value
    && attentionResult.value.list.some((submission) => submission.homeworkId !== targetHomeworkId)
  ) {
    errorMessage.value = '待处理提交归属与当前页面不一致，请重新加载。';
    loading.value = false;
    return;
  }

  const attentionValue = targetAttention ? attentionResult.value : null;
  const pageTotal = attentionValue?.total ?? statisticsResult.value.unsubmittedTotal;
  const pageSize = attentionValue?.size ?? statisticsResult.value.unsubmittedSize;
  const pageItems = attentionValue?.list ?? statisticsResult.value.unsubmittedStudentIds;
  const lastAvailablePage = Math.max(1, Math.ceil(pageTotal / Math.max(1, pageSize)));
  if (
    pageTotal > 0
    && targetPage > lastAvailablePage
    && pageItems.length === 0
  ) {
    currentPage.value = lastAvailablePage;
    await syncPageQuery(lastAvailablePage, 'replace');
    return;
  }

  detail.value = detailResult.value;
  statistics.value = statisticsResult.value;
  attentionPage.value = attentionValue;
  currentPage.value = attentionValue?.page ?? statisticsResult.value.unsubmittedPage;

  if (progressResult.status === 'fulfilled') {
    studentNames.value = Object.fromEntries(
      progressResult.value.students
        .map((student) => [
          student.studentId,
          displayableStudentName(student.studentId, student.studentName)
        ] as const)
        .filter((entry) => Boolean(entry[1]))
    );
    const visibleStudentIds = targetAttention
      ? attentionValue?.list.map((submission) => submission.studentId) ?? []
      : statisticsResult.value.unsubmittedStudentIds;
    if (visibleStudentIds.some((studentId) => !studentNames.value[studentId])) {
      studentNameWarning.value = '部分学生姓名尚未同步，已使用待补充名称。';
    }
  } else {
    studentNameWarning.value = '未能同步学生姓名，已使用待补充名称。';
  }

  loading.value = false;
}

async function changePage(page: number) {
  if (page < 1 || page > totalPages.value || page === currentPage.value) {
    return;
  }
  currentPage.value = page;
  await syncPageQuery(page, 'push');
}

async function syncPageQuery(page: number, mode: 'push' | 'replace') {
  const target = { query: buildAttentionQuery(activeAttention.value, page) };
  await (mode === 'push' ? router.push(target) : router.replace(target));
}

function percentLabel(value: number, denominator: number) {
  if (denominator <= 0) {
    return '0%';
  }
  return `${numberLabel((value / denominator) * 100)}%`;
}

function applicablePercentLabel(value: number, denominator: number) {
  return denominator <= 0 ? '不适用' : percentLabel(value, denominator);
}

function bucketPercent(count: number) {
  return Math.max(0, Math.min(100, (count / maxBucketCount.value) * 100));
}

function scoreLabel(value: number | null) {
  return value === null ? '暂无成绩' : `${numberLabel(value)} 分`;
}

function numberLabel(value: number) {
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 1 }).format(value);
}

function dateTimeLabel(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? '时间待确认'
    : new Intl.DateTimeFormat('zh-CN', {
      year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
    }).format(date);
}

function statusLabel(status: HomeworkStatus) {
  return ({
    DRAFT: '草稿',
    NOT_OPEN: '待开放',
    PUBLISHED: '进行中',
    CLOSED: '已截止',
    SCORE_PUBLISHED: '成绩已发布',
    ARCHIVED: '已归档'
  } as const)[status];
}

function typeLabel(type: HomeworkType) {
  return ({ OBJECTIVE: '客观题', FILE: '文件作业', CODE: '编程题', TEXT: '文本作业' } as const)[type];
}

function followUpTab(attention: HomeworkAttention | null, label: string, count: number) {
  return {
    key: attention ?? 'UNSUBMITTED',
    label,
    count,
    active: activeAttention.value === attention,
    route: {
      name: 'homework-statistics',
      params: { courseId: props.courseId, homeworkId: props.homeworkId },
      query: buildAttentionQuery(attention, 1)
    }
  };
}

function reviewRoute(submissionId: number) {
  return {
    name: 'homework-submission-review',
    params: { courseId: props.courseId, homeworkId: props.homeworkId, submissionId },
    query: buildAttentionQuery(activeAttention.value, currentPage.value)
  };
}

function buildAttentionQuery(attention: HomeworkAttention | null, page: number) {
  return {
    ...(attention ? { attention } : {}),
    ...(page > 1 ? { page: String(page) } : {})
  };
}

function displayableStudentName(studentId: number, value: string) {
  const name = value.trim();
  const compactName = name.replace(/\s+/gu, '');
  return compactName === String(studentId) || compactName === ['学生', studentId].join('')
    ? ''
    : name;
}

function errorLabel(error: unknown, fallback: string) {
  return error instanceof Error && error.message.trim() ? error.message : fallback;
}

function normalizePage(value: number) {
  return Number.isInteger(value) && value > 0 ? value : 1;
}

function normalizeAttention(value: HomeworkAttention | undefined): HomeworkAttention | null {
  return value === 'EVALUATION_PENDING' || value === 'REVIEW_PENDING' ? value : null;
}
</script>

<style scoped>
.homework-statistics {
  display: grid;
  gap: 20px;
  width: 100%;
  max-width: 1440px;
  min-width: 0;
  margin: 0 auto;
  padding: clamp(18px, 3vw, 40px);
  color: var(--oj-ink);
  box-sizing: border-box;
}

.button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 9px 15px;
  border: 1px solid rgba(22, 66, 60, 0.2);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--oj-brand);
  font: inherit;
  font-weight: 750;
  text-decoration: none;
  cursor: pointer;
}

.button--primary {
  border-color: var(--oj-brand);
  background: var(--oj-brand);
  color: #fff;
}

.button--secondary { background: rgba(220, 235, 230, 0.8); }
.button:disabled { cursor: wait; opacity: 0.65; }

.context-strip,
.summary-grid,
.statistics-layout,
.score-grid {
  display: grid;
  gap: 14px;
}

.context-strip {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 18px 22px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  box-shadow: var(--oj-shadow-soft);
}

.context-strip div,
.summary-card,
.score-grid article {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.context-strip span,
.summary-card span,
.score-grid span { color: var(--oj-muted); font-size: 0.82rem; font-weight: 700; }
.context-strip strong { overflow-wrap: anywhere; }

.summary-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }

.summary-card {
  grid-template-columns: 1fr auto;
  align-items: end;
  padding: 18px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  box-shadow: var(--oj-shadow-soft);
}

.summary-card span { grid-column: 1 / -1; }
.summary-card strong { color: var(--oj-brand); font-size: clamp(1.45rem, 3vw, 2.1rem); }
.summary-card small { color: var(--oj-muted); }
.summary-card--success { border-color: rgba(38, 128, 96, 0.24); }
.summary-card--warning { border-color: rgba(172, 108, 20, 0.28); }

.statistics-layout { grid-template-columns: minmax(0, 1.35fr) minmax(300px, 0.65fr); }

.work-surface {
  min-width: 0;
  padding: clamp(18px, 2.5vw, 28px);
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  box-shadow: var(--oj-shadow-soft);
}

.section-heading {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 20px;
}

.section-heading p { margin: 0 0 4px; color: var(--oj-brand); font-size: 0.72rem; font-weight: 800; letter-spacing: 0.08em; }
.section-heading h2 { margin: 0; font-size: 1.25rem; }
.section-heading > span { color: var(--oj-muted); font-size: 0.82rem; }

.score-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.score-grid article { padding: 20px; border-radius: 14px; background: rgba(220, 235, 230, 0.55); }
.score-grid strong { color: var(--oj-brand); font-size: 1.35rem; overflow-wrap: anywhere; }
.score-panel__note { margin: 18px 0 0; color: var(--oj-muted); font-size: 0.85rem; line-height: 1.6; }

.score-distribution {
  display: grid;
  gap: 12px;
  margin: 22px 0 0;
  padding: 0;
  list-style: none;
}

.score-distribution li {
  display: grid;
  grid-template-columns: minmax(76px, auto) minmax(120px, 1fr) minmax(44px, auto);
  align-items: center;
  gap: 12px;
}

.score-distribution__label { color: var(--oj-muted); font-size: 0.84rem; font-weight: 750; }
.score-distribution__track { height: 12px; overflow: hidden; border-radius: 999px; background: rgba(22, 66, 60, 0.1); }
.score-distribution__track span { display: block; height: 100%; border-radius: inherit; background: var(--oj-brand); }
.score-distribution strong { color: var(--oj-brand); font-size: 0.86rem; text-align: right; }

.inline-warning { padding: 10px 12px; border-radius: 10px; background: rgba(255, 243, 214, 0.84); color: #80540d; }
.follow-up-tabs { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin-bottom: 20px; }
.follow-up-tabs a { padding: 9px 8px; border: 1px solid var(--oj-line); border-radius: 10px; color: var(--oj-brand); font-size: 0.82rem; font-weight: 750; text-align: center; text-decoration: none; }
.follow-up-tabs .follow-up-tab--active { border-color: var(--oj-brand); background: rgba(220, 235, 230, 0.8); box-shadow: inset 0 0 0 1px rgba(22, 66, 60, 0.1); }
.student-list { display: grid; gap: 10px; margin: 0; padding: 0; list-style: none; }
.student-list li { display: flex; align-items: center; gap: 12px; padding: 11px; border-radius: 12px; background: rgba(245, 248, 247, 0.86); }
.student-list li div { display: grid; gap: 2px; min-width: 0; }
.student-list li span { color: var(--oj-muted); font-size: 0.8rem; }
.student-list__link { display: flex; align-items: center; gap: 12px; width: 100%; color: inherit; text-decoration: none; }
.student-list__copy { display: grid; gap: 2px; min-width: 0; }
.student-list__copy strong { color: var(--oj-ink); font-size: 1rem; }
.student-avatar { display: grid; place-items: center; flex: 0 0 34px; height: 34px; border-radius: 50%; background: var(--oj-brand); color: #fff !important; font-weight: 800; }
.empty-follow-up { padding: 24px 12px; text-align: center; }
.empty-follow-up p { color: var(--oj-muted); }
.follow-up-panel__queue { width: 100%; margin-top: 16px; box-sizing: border-box; }

.pagination { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 16px; }
.pagination button { padding: 7px 10px; border: 1px solid var(--oj-line); border-radius: 8px; background: #fff; color: var(--oj-brand); font: inherit; }
.pagination span { color: var(--oj-muted); font-size: 0.82rem; }

@media (max-width: 900px) {
  .context-strip,
  .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .statistics-layout { grid-template-columns: 1fr; }
}

@media (max-width: 540px) {
  .homework-statistics { padding: 14px; }
  .context-strip,
  .summary-grid,
  .score-grid { grid-template-columns: 1fr; }
  .context-strip { padding: 16px; }
  .section-heading { align-items: flex-start; flex-direction: column; }
  .score-distribution li { grid-template-columns: minmax(68px, auto) minmax(70px, 1fr) minmax(42px, auto); gap: 8px; }
  .follow-up-tabs { grid-template-columns: 1fr; }
  .pagination { align-items: stretch; flex-direction: column; text-align: center; }
}
</style>
