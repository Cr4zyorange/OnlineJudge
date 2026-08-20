<template>
  <main class="homework-submission-workspace" data-testid="homework-submission-workspace">
    <PageHeader
      eyebrow="教师作业台 · 提交队列"
      :title="pageTitle"
      subtitle="按学生姓名、提交、评测与批阅状态定位提交版本，再进入独立批阅页完成评分。"
    >
      <template #meta>
        <span>{{ courseName || '当前课程' }}</span>
        <StatusBadge v-if="homework" :label="formatHomeworkType(homework.type)" tone="info" />
      </template>
      <template #actions>
        <RouterLink class="workspace-link" :to="manageDetailRoute">返回作业管理</RouterLink>
        <button
          class="button button--secondary"
          type="button"
          :disabled="queueLoading"
          @click="loadSubmissions"
        >{{ queueLoading ? '正在刷新…' : '刷新队列' }}</button>
      </template>
    </PageHeader>

    <section
      v-if="fatalError"
      class="state-panel state-panel--error"
      data-testid="workspace-fatal-error"
      role="alert"
    >
      <strong>作业上下文无法安全展示</strong>
      <p>{{ fatalError }}</p>
      <button class="button button--secondary" type="button" @click="loadWorkspace">重新加载</button>
    </section>

    <template v-else>
      <div
        v-if="studentNameNotice"
        class="context-warning"
        data-testid="student-name-warning"
        role="status"
      >
        <span>{{ studentNameNotice }}</span>
        <button
          v-if="!studentNamesReady"
          class="button button--quiet"
          data-action="retry-student-names"
          type="button"
          :disabled="studentNamesLoading"
          @click="retryStudentNames"
        >{{ studentNamesLoading ? '正在重试…' : '重试姓名服务' }}</button>
        <button
          v-else-if="studentFilterBlocked"
          class="button button--quiet"
          data-action="clear-student-filter"
          type="button"
          @click="clearStudentFilter"
        >清除姓名筛选</button>
      </div>

      <section class="summary-grid" aria-label="提交队列摘要">
        <article class="summary-card">
          <span>全部提交</span>
          <strong>{{ total }}</strong>
          <small>个版本</small>
        </article>
        <article class="summary-card">
          <span>本页有效提交</span>
          <strong>{{ finalCount }}</strong>
          <small>份</small>
        </article>
        <article class="summary-card">
          <span>本页已完成批阅版本</span>
          <strong>{{ reviewedVersionCount }}</strong>
          <small>个</small>
        </article>
        <article class="summary-card">
          <span>本页评测处理中</span>
          <strong>{{ pendingEvaluationCount }}</strong>
          <small>份</small>
        </article>
      </section>

      <section class="work-surface filter-surface" aria-labelledby="homework-submission-filter-title">
        <header class="section-heading">
          <div>
            <p>QUEUE FILTER</p>
            <h2 id="homework-submission-filter-title">筛选提交</h2>
          </div>
          <button class="button button--quiet" type="button" @click="resetFilters">清除筛选</button>
        </header>

        <form
          class="filter-form"
          data-action="filter-submissions"
          @submit.prevent="applyFilters"
        >
          <label class="field">
            <span>学生姓名</span>
            <select
              v-model="filters.studentRef"
              name="studentName"
              :disabled="studentNameFilterDisabled"
              @change="onStudentSelectionChange"
            >
              <option value="">{{ studentNameFilterPlaceholder }}</option>
              <option
                v-for="student in studentOptions"
                :key="student.ref"
                :value="student.ref"
              >{{ student.label }}</option>
            </select>
          </label>
          <label class="field">
            <span>提交状态</span>
            <select v-model="filters.submitStatus" name="submitStatus">
              <option value="">全部提交状态</option>
              <option value="SUBMITTED">已提交</option>
              <option value="LATE">逾期提交</option>
              <option value="REJECTED">已拒绝</option>
            </select>
          </label>
          <label class="field">
            <span>评测状态</span>
            <select v-model="filters.evaluationStatus" name="evaluationStatus">
              <option value="">全部评测状态</option>
              <option value="NONE">未评测</option>
              <option value="PENDING">等待评测</option>
              <option value="RUNNING">评测中</option>
              <option value="ACCEPTED">通过</option>
              <option value="WRONG_ANSWER">答案错误</option>
              <option value="COMPILE_ERROR">编译错误</option>
              <option value="RUNTIME_ERROR">运行错误</option>
              <option value="TIME_LIMIT_EXCEEDED">运行超时</option>
              <option value="SYSTEM_ERROR">系统错误</option>
            </select>
          </label>
          <label class="field">
            <span>批阅状态</span>
            <select v-model="filters.reviewStatus" name="reviewStatus">
              <option value="">全部批阅状态</option>
              <option value="UNREVIEWED">待批阅</option>
              <option value="NEED_REVIEW">需批阅</option>
              <option value="REVIEWED">已批阅</option>
            </select>
          </label>
          <button class="button button--primary" type="submit" :disabled="queueLoading">
            {{ queueLoading ? '查询中…' : '查询提交' }}
          </button>
        </form>
      </section>

      <section class="work-surface queue-surface" aria-labelledby="homework-submission-queue-title">
        <header class="section-heading section-heading--compact">
          <div>
            <p>SUBMISSION QUEUE</p>
            <h2 id="homework-submission-queue-title">提交队列</h2>
          </div>
          <span class="count-chip">第 {{ page }} / {{ totalPages }} 页</span>
        </header>

        <div v-if="queueLoading" class="state-panel" data-testid="queue-loading" role="status">
          <strong>正在加载提交队列</strong>
          <p>正在同步当前筛选条件下的提交版本。</p>
        </div>

        <div v-else-if="queueError" class="state-panel state-panel--error" data-testid="queue-error" role="alert">
          <strong>提交队列暂时无法加载</strong>
          <p>{{ queueError }}</p>
          <button
            class="button button--secondary"
            data-action="retry-submissions"
            type="button"
            @click="loadSubmissions"
          >重新加载</button>
        </div>

        <div v-else-if="submissions.length === 0" class="state-panel" data-testid="queue-empty">
          <strong>暂无符合条件的提交</strong>
          <p>调整状态筛选后重新查询，或等待学生提交作业。</p>
        </div>

        <template v-else>
          <ul class="submission-list" data-testid="queue-list" aria-label="作业提交列表">
            <li v-for="submission in submissions" :key="submission.submissionId">
              <RouterLink
                class="submission-card"
                :to="reviewRoute(submission.submissionId)"
                :aria-label="`批阅${studentDisplayName(submission.studentId)}的版本 ${submission.version}`"
              >
                <span class="submission-card__topline">
                  <strong>{{ studentDisplayName(submission.studentId) }}</strong>
                  <span>版本 {{ submission.version }}</span>
                </span>
                <span class="submission-card__statuses">
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
                </span>
                <span class="submission-card__facts">
                  <span>{{ submission.final ? '当前有效提交' : '历史版本' }}</span>
                  <span>{{ formatDateTime(submission.submittedAt) }}</span>
                </span>
                <span class="submission-card__scores">
                  <span>自动分 <b>{{ formatScore(submission.autoScore) }}</b></span>
                  <span>人工分 <b>{{ formatScore(submission.manualScore) }}</b></span>
                  <span>最终分 <b>{{ formatScore(submission.finalScore) }}</b></span>
                </span>
                <span class="submission-card__footer">进入批阅 <span aria-hidden="true">→</span></span>
              </RouterLink>
            </li>
          </ul>

          <nav class="pager" aria-label="提交分页">
            <button
              class="button button--secondary"
              data-action="previous-page"
              type="button"
              :disabled="page <= 1 || queueLoading"
              @click="goToPage(page - 1)"
            >上一页</button>
            <span>第 {{ page }} 页，共 {{ total }} 个版本</span>
            <button
              class="button button--secondary"
              data-action="next-page"
              type="button"
              :disabled="page >= totalPages || queueLoading"
              @click="goToPage(page + 1)"
            >下一页</button>
          </nav>
        </template>
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { getHomeworkDetail, listHomeworkSubmissions } from '../../api/hwk/homeworks';
import { getTeacherLearningProgress } from '../../api/lrn/learningProgress';
import PageHeader from '../../components/foundation/PageHeader.vue';
import StatusBadge, { type StatusBadgeTone } from '../../components/foundation/StatusBadge.vue';
import type {
  HomeworkDetail,
  HomeworkEvaluationStatus,
  HomeworkReviewStatus,
  HomeworkSubmissionSummary,
  HomeworkSubmitStatus
} from '../../types/hwk';
import {
  formatEvaluationStatus,
  formatHomeworkType,
  formatReviewStatus,
  formatSubmitStatus
} from './hwkDisplay';

const props = defineProps<{
  courseId: number;
  homeworkId: number;
}>();

interface QueueFilters {
  studentName: string;
  studentRef: string;
  submitStatus: '' | HomeworkSubmitStatus;
  evaluationStatus: '' | HomeworkEvaluationStatus;
  reviewStatus: '' | HomeworkReviewStatus;
}

const submitStatuses: HomeworkSubmitStatus[] = ['SUBMITTED', 'LATE', 'REJECTED'];
const evaluationStatuses: HomeworkEvaluationStatus[] = [
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
const reviewStatuses: HomeworkReviewStatus[] = ['UNREVIEWED', 'NEED_REVIEW', 'REVIEWED'];
const pageSize = 20;
const candidatePageSize = 100;

const route = useRoute();
const router = useRouter();
const filters = reactive<QueueFilters>({
  studentName: queryText(route.query.keyword),
  studentRef: studentRefFromQuery(route.query.studentRef),
  submitStatus: statusFromQuery(route.query.submit, submitStatuses),
  evaluationStatus: statusFromQuery(route.query.evaluation, evaluationStatuses),
  reviewStatus: statusFromQuery(route.query.review, reviewStatuses)
});
const homework = ref<HomeworkDetail | null>(null);
const submissions = ref<HomeworkSubmissionSummary[]>([]);
const studentNames = ref<Record<number, string>>({});
const courseName = ref('');
const page = ref(pageFromQuery(route.query.page));
const total = ref(0);
const homeworkLoading = ref(false);
const queueLoading = ref(false);
const studentNamesLoading = ref(false);
const fatalError = ref('');
const queueError = ref('');
const studentNameWarning = ref('');
const studentNamesReady = ref(false);
let homeworkRequestId = 0;
let queueRequestId = 0;
let studentNamesRequestId = 0;
let workspaceRequestId = 0;
let exactQueueCache: { key: string; items: HomeworkSubmissionSummary[] } | null = null;

const pageTitle = computed(() => homework.value ? `${homework.value.title} · 提交队列` : '作业提交队列');
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));
const finalCount = computed(() => submissions.value.filter((item) => item.final).length);
const reviewedVersionCount = computed(() => (
  submissions.value.filter((item) => item.reviewStatus === 'REVIEWED').length
));
const pendingEvaluationCount = computed(() => submissions.value.filter((item) => (
  item.evaluationStatus === 'PENDING'
  || item.evaluationStatus === 'RUNNING'
)).length);
const studentOptions = computed(() => {
  const students = Object.entries(studentNames.value)
    .map(([studentId, studentName]) => ({ id: Number(studentId), name: studentName }))
    .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN') || left.id - right.id);
  const nameCounts = new Map<string, number>();
  const nameOccurrences = new Map<string, number>();
  students.forEach((student) => nameCounts.set(student.name, (nameCounts.get(student.name) ?? 0) + 1));
  return students.map((student) => {
    const occurrence = (nameOccurrences.get(student.name) ?? 0) + 1;
    nameOccurrences.set(student.name, occurrence);
    const duplicate = (nameCounts.get(student.name) ?? 0) > 1;
    const label = duplicate ? `${student.name}（同名 ${occurrence}）` : student.name;
    return { ...student, ref: studentReference(student.id), label };
  });
});
const selectedStudentOption = computed(() => {
  const selectedRef = filters.studentRef.trim();
  if (!selectedRef || !studentNamesReady.value) {
    return null;
  }
  return studentOptions.value.find((student) => student.ref === selectedRef) ?? null;
});
const studentFilterBlocked = computed(() => (
  (filters.studentName.trim().length > 0 || filters.studentRef.length > 0)
  && (!studentNamesReady.value || selectedStudentOption.value === null)
));
const studentNameNotice = computed(() => {
  if (studentFilterBlocked.value) {
    if (studentNamesReady.value) {
      return '姓名筛选已过期或无法定位；已停止加载提交队列，请清除姓名筛选后重新选择学生。';
    }
    const rosterMessage = studentNameWarning.value || '课程名单暂时不可用';
    return `${rosterMessage}；当前姓名筛选无法验证，已停止加载提交队列。`;
  }
  return studentNameWarning.value
    ? `${studentNameWarning.value}；提交队列与状态筛选仍可使用，学生姓名与姓名筛选将暂时隐藏。`
    : '';
});
const studentNameFilterDisabled = computed(() => (
  studentNamesLoading.value
  || !studentNamesReady.value
  || studentFilterBlocked.value
  || studentOptions.value.length === 0
));
const studentNameFilterPlaceholder = computed(() => {
  if (studentNamesLoading.value) return '正在加载课程名单';
  if (!studentNamesReady.value) return '姓名筛选暂不可用';
  return studentOptions.value.length > 0 ? '全部学生' : '暂无可选学生';
});
const manageDetailRoute = computed(() => ({
  name: 'homework-manage-detail',
  params: { courseId: props.courseId, homeworkId: props.homeworkId }
}));

watch(
  () => `${props.courseId}:${props.homeworkId}`,
  () => {
    workspaceRequestId += 1;
    homeworkRequestId += 1;
    queueRequestId += 1;
    studentNamesRequestId += 1;
  },
  { flush: 'sync' }
);

watch(
  () => `${props.courseId}:${props.homeworkId}`,
  () => {
    restoreFiltersFromRoute();
    void loadWorkspace();
  },
  { immediate: true }
);

watch(
  () => [
    route.query.keyword,
    route.query.studentRef,
    route.query.submit,
    route.query.evaluation,
    route.query.review,
    route.query.page
  ],
  () => {
    if (routeQueryMatchesState()) {
      return;
    }
    void restoreRouteAndLoadSubmissions();
  }
);

async function loadWorkspace() {
  const requestId = ++workspaceRequestId;
  homeworkRequestId += 1;
  queueRequestId += 1;
  studentNamesRequestId += 1;
  fatalError.value = '';
  queueError.value = '';
  studentNameWarning.value = '';
  homework.value = null;
  submissions.value = [];
  studentNames.value = {};
  studentNamesReady.value = false;
  courseName.value = '';
  exactQueueCache = null;
  queueLoading.value = true;
  await Promise.all([syncQuery(), loadHomeworkContext(), loadStudentNames()]);
  if (requestId !== workspaceRequestId) {
    return;
  }
  if (fatalError.value) {
    queueLoading.value = false;
    return;
  }
  if (normalizeStudentSelection()) {
    await syncQuery();
    if (requestId !== workspaceRequestId) {
      return;
    }
  }
  await loadSubmissions();
}

async function loadHomeworkContext() {
  const requestId = ++homeworkRequestId;
  const targetHomeworkId = props.homeworkId;
  const targetCourseId = props.courseId;
  homeworkLoading.value = true;
  try {
    const detail = await getHomeworkDetail(targetHomeworkId);
    if (requestId !== homeworkRequestId) {
      return;
    }
    if (detail.id !== targetHomeworkId || detail.courseId !== targetCourseId) {
      setFatalError('作业与当前课程不匹配，请返回作业管理重新进入。');
      return;
    }
    homework.value = detail;
  } catch (error) {
    if (requestId === homeworkRequestId) {
      setFatalError(errorMessage(error, '作业信息加载失败，请重试。'));
    }
  } finally {
    if (requestId === homeworkRequestId) {
      homeworkLoading.value = false;
    }
  }
}

async function loadStudentNames() {
  const requestId = ++studentNamesRequestId;
  const targetCourseId = props.courseId;
  studentNamesLoading.value = true;
  studentNameWarning.value = '';
  studentNamesReady.value = false;
  studentNames.value = {};
  courseName.value = '';
  try {
    const progress = await getTeacherLearningProgress(targetCourseId);
    if (requestId !== studentNamesRequestId) {
      return;
    }
    if (progress.courseId !== targetCourseId) {
      studentNameWarning.value = '课程名单与当前课程不匹配';
      return;
    }
    courseName.value = progress.courseName.trim();
    studentNames.value = Object.fromEntries(progress.students
      .map((student) => [student.studentId, student.studentName.trim()] as const)
      .filter(([studentId, name]) => name.length > 0 && !isSyntheticStudentName(name, studentId)));
    studentNamesReady.value = true;
  } catch (error) {
    if (requestId === studentNamesRequestId) {
      studentNameWarning.value = errorMessage(error, '学生姓名加载失败');
    }
  } finally {
    if (requestId === studentNamesRequestId) {
      studentNamesLoading.value = false;
    }
  }
}

async function retryStudentNames() {
  exactQueueCache = null;
  await loadStudentNames();
  if (studentNamesReady.value) {
    if (normalizeStudentSelection()) {
      await syncQuery();
    }
    await loadSubmissions();
  }
}

async function loadSubmissions() {
  const requestId = ++queueRequestId;
  const targetHomeworkId = props.homeworkId;
  const selectedStudent = selectedStudentOption.value;
  exactQueueCache = null;
  queueLoading.value = true;
  queueError.value = '';
  if (studentFilterBlocked.value) {
    submissions.value = [];
    total.value = 0;
    queueError.value = '当前姓名筛选无法安全恢复，请清除姓名筛选后重新选择学生。';
    queueLoading.value = false;
    return;
  }
  try {
    if (selectedStudent) {
      const exactItems = await loadExactStudentCandidates(
        targetHomeworkId,
        selectedStudent.id,
        requestId
      );
      if (exactItems === null || requestId !== queueRequestId) {
        return;
      }
      const cacheKey = exactQueueCacheKey(targetHomeworkId, selectedStudent.ref);
      exactQueueCache = { key: cacheKey, items: exactItems };
      total.value = exactItems.length;
      const lastPage = Math.max(1, Math.ceil(exactItems.length / pageSize));
      if (exactItems.length > 0 && page.value > lastPage) {
        page.value = lastPage;
        await syncQuery();
        if (requestId !== queueRequestId) {
          return;
        }
      }
      renderExactQueuePage(exactItems);
      return;
    }
    const result = await listHomeworkSubmissions(targetHomeworkId, buildApiQuery());
    if (requestId !== queueRequestId) {
      return;
    }
    if (result.list.some((item) => item.homeworkId !== targetHomeworkId)) {
      submissions.value = [];
      total.value = 0;
      queueError.value = '提交数据与当前作业不匹配，请重新加载。';
      return;
    }
    total.value = result.total;
    const lastPage = Math.max(1, Math.ceil(result.total / pageSize));
    if (result.total > 0 && result.list.length === 0 && result.page > lastPage) {
      page.value = lastPage;
      await syncQuery();
      if (requestId === queueRequestId) {
        await loadSubmissions();
      }
      return;
    }
    submissions.value = result.list;
    page.value = result.page;
  } catch (error) {
    if (requestId === queueRequestId) {
      submissions.value = [];
      total.value = 0;
      queueError.value = errorMessage(error, '提交队列加载失败，请重试。');
    }
  } finally {
    if (requestId === queueRequestId) {
      queueLoading.value = false;
    }
  }
}

async function applyFilters() {
  if (!studentFilterBlocked.value) {
    const selectedStudent = selectedStudentOption.value;
    filters.studentName = selectedStudent?.name ?? '';
    filters.studentRef = selectedStudent?.ref ?? '';
  }
  exactQueueCache = null;
  page.value = 1;
  await syncQuery();
  await loadSubmissions();
}

function onStudentSelectionChange() {
  const selectedStudent = selectedStudentOption.value;
  filters.studentName = selectedStudent?.name ?? '';
  exactQueueCache = null;
}

async function resetFilters() {
  filters.studentName = '';
  filters.studentRef = '';
  filters.submitStatus = '';
  filters.evaluationStatus = '';
  filters.reviewStatus = '';
  exactQueueCache = null;
  page.value = 1;
  await syncQuery();
  await loadSubmissions();
}

async function clearStudentFilter() {
  filters.studentName = '';
  filters.studentRef = '';
  exactQueueCache = null;
  page.value = 1;
  await syncQuery();
  await loadSubmissions();
}

async function goToPage(nextPage: number) {
  if (nextPage < 1 || nextPage > totalPages.value || queueLoading.value) {
    return;
  }
  page.value = nextPage;
  await syncQuery();
  const selectedStudent = selectedStudentOption.value;
  if (
    selectedStudent
    && exactQueueCache?.key === exactQueueCacheKey(props.homeworkId, selectedStudent.ref)
  ) {
    renderExactQueuePage(exactQueueCache.items);
    return;
  }
  await loadSubmissions();
}

async function loadExactStudentCandidates(
  homeworkId: number,
  studentId: number,
  requestId: number
) {
  const exactItems = new Map<number, HomeworkSubmissionSummary>();
  let candidatePage = 1;
  let candidateTotalPages = 1;
  do {
    const result = await listHomeworkSubmissions(
      homeworkId,
      buildApiQuery(candidatePage, candidatePageSize, studentId)
    );
    if (requestId !== queueRequestId) {
      return null;
    }
    if (result.list.some((item) => item.homeworkId !== homeworkId)) {
      throw new Error('提交数据与当前作业不匹配，请重新加载。');
    }
    result.list.forEach((item) => {
      if (item.studentId === studentId) {
        exactItems.set(item.submissionId, item);
      }
    });
    const effectiveSize = result.size > 0
      ? Math.min(result.size, candidatePageSize)
      : candidatePageSize;
    candidateTotalPages = Math.max(1, Math.ceil(result.total / effectiveSize));
    candidatePage += 1;
  } while (candidatePage <= candidateTotalPages);
  return [...exactItems.values()];
}

function renderExactQueuePage(items: HomeworkSubmissionSummary[]) {
  total.value = items.length;
  const start = (page.value - 1) * pageSize;
  submissions.value = items.slice(start, start + pageSize);
}

function exactQueueCacheKey(homeworkId: number, studentRef: string) {
  return JSON.stringify([
    homeworkId,
    studentRef,
    filters.submitStatus,
    filters.evaluationStatus,
    filters.reviewStatus
  ]);
}

function buildApiQuery(
  requestPage = page.value,
  requestSize = pageSize,
  studentId = selectedStudentId()
) {
  return {
    page: requestPage,
    size: requestSize,
    ...(studentId === null ? {} : { studentKeyword: String(studentId) }),
    ...(filters.submitStatus ? { submitStatus: filters.submitStatus } : {}),
    ...(filters.evaluationStatus ? { evaluationStatus: filters.evaluationStatus } : {}),
    ...(filters.reviewStatus ? { reviewStatus: filters.reviewStatus } : {})
  };
}

function buildSafeQuery() {
  return {
    ...(filters.studentName.trim() ? { keyword: filters.studentName.trim() } : {}),
    ...(filters.studentRef ? { studentRef: filters.studentRef } : {}),
    ...(filters.submitStatus ? { submit: filters.submitStatus } : {}),
    ...(filters.evaluationStatus ? { evaluation: filters.evaluationStatus } : {}),
    ...(filters.reviewStatus ? { review: filters.reviewStatus } : {}),
    ...(page.value > 1 ? { page: String(page.value) } : {})
  };
}

async function syncQuery() {
  await router.replace({ query: buildSafeQuery() });
}

function reviewRoute(submissionId: number) {
  const query = buildSafeQuery();
  return {
    name: 'homework-submission-review',
    params: { courseId: props.courseId, homeworkId: props.homeworkId, submissionId },
    ...(Object.keys(query).length > 0 ? { query } : {})
  };
}

function restoreFiltersFromRoute() {
  filters.studentName = queryText(route.query.keyword);
  filters.studentRef = studentRefFromQuery(route.query.studentRef);
  filters.submitStatus = statusFromQuery(route.query.submit, submitStatuses);
  filters.evaluationStatus = statusFromQuery(route.query.evaluation, evaluationStatuses);
  filters.reviewStatus = statusFromQuery(route.query.review, reviewStatuses);
  page.value = pageFromQuery(route.query.page);
}

function routeQueryMatchesState() {
  return filters.studentName === queryText(route.query.keyword)
    && filters.studentRef === studentRefFromQuery(route.query.studentRef)
    && queryText(route.query.studentRef).toLowerCase() === studentRefFromQuery(route.query.studentRef)
    && filters.submitStatus === statusFromQuery(route.query.submit, submitStatuses)
    && filters.evaluationStatus === statusFromQuery(route.query.evaluation, evaluationStatuses)
    && filters.reviewStatus === statusFromQuery(route.query.review, reviewStatuses)
    && page.value === pageFromQuery(route.query.page);
}

function selectedStudentId() {
  return selectedStudentOption.value?.id ?? null;
}

async function restoreRouteAndLoadSubmissions() {
  restoreFiltersFromRoute();
  exactQueueCache = null;
  if (normalizeStudentSelection()) {
    await syncQuery();
  }
  await loadSubmissions();
}

function normalizeStudentSelection() {
  if (!studentNamesReady.value) {
    return false;
  }
  const selectedRef = filters.studentRef;
  if (selectedRef) {
    const matchedByRef = studentOptions.value.find((student) => student.ref === selectedRef);
    if (!matchedByRef) {
      return false;
    }
    const changed = filters.studentName !== matchedByRef.name
      || filters.studentRef !== matchedByRef.ref;
    filters.studentName = matchedByRef.name;
    filters.studentRef = matchedByRef.ref;
    return changed;
  }
  const selectedName = filters.studentName.trim();
  if (!selectedName) {
    return false;
  }
  const nameMatches = studentOptions.value.filter((student) => student.name === selectedName);
  if (nameMatches.length !== 1) {
    return false;
  }
  filters.studentName = nameMatches[0].name;
  filters.studentRef = nameMatches[0].ref;
  return true;
}

function statusFromQuery<T extends string>(value: unknown, allowed: readonly T[]): '' | T {
  const candidate = queryText(value);
  return allowed.includes(candidate as T) ? candidate as T : '';
}

function pageFromQuery(value: unknown) {
  const parsed = Number(queryText(value));
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 1;
}

function queryText(value: unknown) {
  const candidate = Array.isArray(value) ? value[0] : value;
  return typeof candidate === 'string' ? candidate.trim() : '';
}

function studentRefFromQuery(value: unknown) {
  const candidate = queryText(value).toLowerCase();
  return /^[0-9a-f]{16}$/.test(candidate) ? candidate : '';
}

function studentDisplayName(studentId: number) {
  return studentNames.value[studentId] || '学生姓名暂不可用';
}

function isSyntheticStudentName(name: string, studentId: number) {
  const compactName = name.replace(/\s+/gu, '');
  return compactName === String(studentId) || compactName === `学生${studentId}`;
}

function studentReference(studentId: number) {
  let hash = 0xcbf29ce484222325n;
  const source = `onlinejudge-hwk-student-ref:v1:${studentId}`;
  for (const character of source) {
    hash ^= BigInt(character.codePointAt(0) ?? 0);
    hash = BigInt.asUintN(64, hash * 0x100000001b3n);
  }
  return hash.toString(16).padStart(16, '0');
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

function formatScore(score: number | null | undefined) {
  return score === null || score === undefined ? '待定' : `${score} 分`;
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

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message.trim() ? error.message : fallback;
}

function setFatalError(message: string) {
  fatalError.value = message;
  homework.value = null;
  submissions.value = [];
  studentNames.value = {};
  studentNamesReady.value = false;
  courseName.value = '';
  total.value = 0;
}
</script>

<style scoped>
.homework-submission-workspace {
  display: grid;
  gap: 18px;
  width: 100%;
  max-width: none;
  min-width: 0;
  color: var(--oj-ink);
}

.homework-submission-workspace,
.homework-submission-workspace * {
  box-sizing: border-box;
}

.workspace-link,
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

.workspace-link,
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
  padding: 6px 10px;
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

.context-warning {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 14px;
  border: 1px solid rgba(194, 123, 0, 0.24);
  border-radius: var(--oj-radius);
  background: rgba(255, 243, 214, 0.72);
  color: #714b0e;
  font-size: 0.82rem;
  font-weight: 700;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-card,
.work-surface {
  min-width: 0;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  box-shadow: var(--oj-shadow-soft);
  backdrop-filter: var(--oj-blur);
  -webkit-backdrop-filter: var(--oj-blur);
}

.summary-card {
  padding: 17px 19px;
}

.summary-card span,
.summary-card small {
  color: var(--oj-muted);
}

.summary-card span {
  display: block;
  font-size: 0.8rem;
  font-weight: 700;
}

.summary-card strong {
  display: inline-block;
  margin: 6px 5px 0 0;
  color: var(--oj-brand-strong);
  font-size: 1.75rem;
}

.work-surface {
  padding: clamp(18px, 2.4vw, 25px);
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.section-heading--compact {
  align-items: flex-start;
}

.section-heading p,
.section-heading h2 {
  margin: 0;
}

.section-heading p {
  margin-bottom: 5px;
  color: var(--oj-brand);
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.section-heading h2 {
  font-size: 1.18rem;
}

.filter-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(140px, 1fr)) auto;
  align-items: end;
  gap: 12px;
}

.field {
  display: grid;
  min-width: 0;
  gap: 7px;
  color: var(--oj-ink-soft);
  font-size: 0.82rem;
  font-weight: 800;
}

.field select {
  width: 100%;
  min-width: 0;
  min-height: 42px;
  padding: 0 11px;
  border: 1px solid var(--oj-line-strong);
  border-radius: calc(var(--oj-radius) - 4px);
  background: rgba(255, 255, 255, 0.76);
  color: var(--oj-ink);
  font: inherit;
  font-weight: 500;
}

.count-chip {
  border-radius: 999px;
  padding: 5px 9px;
  background: var(--oj-brand-soft);
  color: var(--oj-brand-strong);
  font-size: 0.74rem;
  font-weight: 800;
}

.submission-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(290px, 1fr));
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
  gap: 11px;
  padding: 16px;
  border: 1px solid var(--oj-line);
  border-radius: calc(var(--oj-radius) - 2px);
  background: rgba(255, 255, 255, 0.66);
  color: var(--oj-ink);
  text-decoration: none;
  transition: transform 140ms ease, border-color 140ms ease, box-shadow 140ms ease;
}

.submission-card:hover {
  border-color: var(--oj-brand);
  box-shadow: var(--oj-shadow-soft);
  transform: translateY(-1px);
}

.submission-card__topline,
.submission-card__statuses,
.submission-card__facts,
.submission-card__scores {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
  flex-wrap: wrap;
}

.submission-card__topline,
.submission-card__facts {
  justify-content: space-between;
}

.submission-card__topline strong {
  overflow-wrap: anywhere;
}

.submission-card__topline span,
.submission-card__facts,
.submission-card__scores {
  color: var(--oj-muted);
  font-size: 0.78rem;
}

.submission-card__scores {
  gap: 16px;
}

.submission-card__scores b {
  color: var(--oj-ink);
}

.submission-card__footer {
  justify-self: end;
  padding-top: 9px;
  border-top: 1px solid var(--oj-line);
  color: var(--oj-brand);
  font-size: 0.78rem;
  font-weight: 800;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 18px;
  color: var(--oj-muted);
  font-size: 0.82rem;
  font-weight: 700;
}

.state-panel {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 9px;
  min-height: 250px;
  padding: 28px 20px;
  border: 1px dashed var(--oj-line-strong);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  color: var(--oj-muted);
  text-align: center;
}

.state-panel strong {
  color: var(--oj-ink);
}

.state-panel p {
  max-width: 52ch;
  margin: 0;
  line-height: 1.6;
}

.state-panel--error {
  border-color: rgba(190, 49, 49, 0.24);
  background: rgba(248, 239, 238, 0.72);
}

@media (max-width: 980px) {
  .summary-grid,
  .filter-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .homework-submission-workspace {
    gap: 14px;
  }

  .context-warning,
  .section-heading,
  .pager {
    align-items: stretch;
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .filter-form,
  .submission-list {
    grid-template-columns: minmax(0, 1fr);
  }

  .filter-form .button,
  .context-warning .button,
  .pager .button {
    width: 100%;
  }
}

@media (max-width: 430px) {
  .summary-card,
  .work-surface {
    padding: 14px;
  }

  .submission-card__facts {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (prefers-reduced-motion: reduce) {
  .submission-card {
    transition: none;
  }
}
</style>
