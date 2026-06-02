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
            <option v-for="status in submitStatusOptions" :key="status" :value="status">{{ status }}</option>
          </select>
        </label>
        <label>
          评测状态
          <select v-model="evaluationStatusFilter" data-testid="history-evaluation-status">
            <option value="">全部</option>
            <option v-for="status in evaluationStatusOptions" :key="status" :value="status">{{ status }}</option>
          </select>
        </label>
        <label>
          批阅状态
          <select v-model="reviewStatusFilter" data-testid="history-review-status">
            <option value="">全部</option>
            <option v-for="status in reviewStatusOptions" :key="status" :value="status">{{ status }}</option>
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
            <p>提交状态：{{ item.submitStatus }}</p>
            <p>评测状态：{{ item.evaluationStatus }}</p>
            <p>复核状态：{{ item.reviewStatus }}</p>
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
            <p>作业类型：{{ detail.submitType ?? 'UNKNOWN' }}</p>
            <p>提交状态：{{ detail.submitStatus }}</p>
            <p>评测状态：{{ detail.evaluationStatus }}</p>
            <p>复核状态：{{ detail.reviewStatus }}</p>
            <p>附件：{{ detail.fileUrl ?? '无' }}</p>
            <p>语言：{{ detail.language ?? '无' }}</p>
            <pre class="hwk-history__answer">{{ detail.answerText || detail.answerJson || '本次提交没有文本内容' }}</pre>
            <p v-if="detail.comment">评语：{{ detail.comment }}</p>
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
  listHomeworkSubmissions,
  listMyHomeworkSubmissions
} from '../../api/hwk/homeworks';
import type { HomeworkSubmissionListQuery } from '../../api/hwk/homeworks';
import type {
  HomeworkEvaluationStatus,
  HomeworkReviewStatus,
  HomeworkSubmissionDetail,
  HomeworkSubmissionSummary,
  HomeworkSubmitStatus
} from '../../types/hwk';

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
  try {
    detail.value = await getHomeworkSubmission(submissionId);
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
  await loadSubmissions();
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
  gap: 12px;
  grid-template-columns: repeat(5, minmax(0, 1fr));
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

.hwk-history__back {
  color: #175cd3;
  text-decoration: none;
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
