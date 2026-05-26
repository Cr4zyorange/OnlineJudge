<template>
  <main class="grade-table">
    <section class="grade-table__panel" aria-label="教师成绩总表">
      <div class="grade-table__actions">
        <button type="button" :disabled="busy" @click="syncGrades">
          {{ busy ? '处理中' : '同步来源成绩' }}
        </button>
        <button type="button" :disabled="busy" @click="recalculate">
          重新计算
        </button>
      </div>
      <form
        class="grade-table__filters"
        data-testid="grade-filter-form"
        @submit.prevent="applyFilters"
      >
        <label>
          学生
          <input
            v-model.trim="studentKeyword"
            data-testid="student-keyword"
            type="search"
            placeholder="学号"
          />
        </label>
        <label>
          成绩项
          <input
            v-model.trim="gradeItemIdInput"
            data-testid="grade-item-id"
            inputmode="numeric"
            min="1"
            type="number"
            placeholder="ID"
          />
        </label>
        <label>
          成绩状态
          <select v-model="gradeStatus" data-testid="grade-status">
            <option value="">全部</option>
            <option value="SCORED">已评分</option>
            <option value="UNSUBMITTED">未提交</option>
            <option value="UNGRADED">待评分</option>
            <option value="MISSING">缺失</option>
            <option value="ADJUSTED">已调整</option>
          </select>
        </label>
        <label>
          发布状态
          <select v-model="publishStatus" data-testid="publish-status">
            <option value="">全部</option>
            <option value="UNPUBLISHED">未发布</option>
            <option value="PUBLISHED">已发布</option>
          </select>
        </label>
        <label>
          每页
          <select v-model.number="size" data-testid="page-size">
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
          </select>
        </label>
        <button type="submit" :disabled="loading">筛选</button>
        <button type="button" :disabled="loading" @click="resetFilters">重置</button>
      </form>
      <p v-if="feedback" class="grade-table__feedback">{{ feedback }}</p>
      <p v-if="errorMessage" class="grade-table__error">{{ errorMessage }}</p>
    </section>

    <section class="grade-table__list" aria-label="课程成绩总表">
      <p v-if="loading">加载中</p>
      <p v-else-if="rows.length === 0">暂无成绩记录</p>
      <template v-else>
        <p class="grade-table__total">共 {{ total }} 名学生</p>
        <table>
          <thead>
            <tr>
              <th>学生</th>
              <th>总评</th>
              <th>状态</th>
              <th>发布</th>
              <th>明细数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.studentId">
              <td>{{ row.studentId }}</td>
              <td>{{ row.summary?.finalScore ?? '-' }}</td>
              <td>{{ row.summary?.finalStatus ?? 'INCOMPLETE' }}</td>
              <td>{{ row.summary?.publishStatus ?? 'UNPUBLISHED' }}</td>
              <td>{{ row.records.length }}</td>
            </tr>
          </tbody>
        </table>
        <div class="grade-table__pagination">
          <button
            type="button"
            data-testid="prev-page"
            :disabled="loading || page <= 1"
            @click="goToPage(page - 1)"
          >
            上一页
          </button>
          <span>第 {{ page }} / {{ totalPages }} 页</span>
          <button
            type="button"
            data-testid="next-page"
            :disabled="loading || page >= totalPages"
            @click="goToPage(page + 1)"
          >
            下一页
          </button>
        </div>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  type GradeTableQuery,
  listCourseGrades,
  recalculateCourseGrades,
  syncSourceGrades
} from '../../api/grd/gradeRecords';
import type { CourseGradeRow, GradeStatus, PublishStatus } from '../../types/grd';

const props = defineProps<{
  courseId: number;
}>();

const rows = ref<CourseGradeRow[]>([]);
const loading = ref(false);
const busy = ref(false);
const feedback = ref('');
const errorMessage = ref('');
const total = ref(0);
const page = ref(1);
const size = ref(20);
const studentKeyword = ref('');
const gradeItemIdInput = ref('');
const gradeStatus = ref<GradeStatus | ''>('');
const publishStatus = ref<PublishStatus | ''>('');
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)));

onMounted(loadRows);

async function loadRows() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const result = await listCourseGrades(props.courseId, currentQuery());
    rows.value = result.records;
    total.value = result.total;
    page.value = result.page;
    size.value = result.size;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '成绩总表加载失败';
  } finally {
    loading.value = false;
  }
}

async function syncGrades() {
  busy.value = true;
  feedback.value = '';
  errorMessage.value = '';
  try {
    const result = await syncSourceGrades(props.courseId);
    feedback.value = `同步完成：${result.syncedCount} 条有效成绩，${result.ungradedCount} 条未评分，${result.missingCount} 条缺失`;
    page.value = 1;
    await loadRows();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '来源成绩同步失败';
  } finally {
    busy.value = false;
  }
}

async function recalculate() {
  busy.value = true;
  feedback.value = '';
  errorMessage.value = '';
  try {
    const result = await recalculateCourseGrades(props.courseId);
    feedback.value = `重新计算完成：${result.affectedCount} 名学生`;
    page.value = 1;
    await loadRows();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '课程总评计算失败';
  } finally {
    busy.value = false;
  }
}

async function applyFilters() {
  page.value = 1;
  await loadRows();
}

async function resetFilters() {
  studentKeyword.value = '';
  gradeItemIdInput.value = '';
  gradeStatus.value = '';
  publishStatus.value = '';
  page.value = 1;
  size.value = 20;
  await loadRows();
}

async function goToPage(nextPage: number) {
  page.value = Math.min(Math.max(nextPage, 1), totalPages.value);
  await loadRows();
}

function currentQuery() {
  const gradeItemId = Number(gradeItemIdInput.value);
  const query: GradeTableQuery = {
    page: page.value,
    size: size.value
  };
  if (studentKeyword.value) {
    query.studentKeyword = studentKeyword.value;
  }
  if (Number.isInteger(gradeItemId) && gradeItemId > 0) {
    query.gradeItemId = gradeItemId;
  }
  if (gradeStatus.value) {
    query.gradeStatus = gradeStatus.value;
  }
  if (publishStatus.value) {
    query.publishStatus = publishStatus.value;
  }
  return query;
}
</script>

<style scoped>
.grade-table {
  background: #f6f8fb;
  color: #1f2937;
  display: grid;
  gap: 16px;
  min-height: 100vh;
  padding: 24px;
}

.grade-table__panel,
.grade-table__list {
  background: #ffffff;
  border: 1px solid #d8dee9;
  border-radius: 8px;
  padding: 16px;
}

.grade-table__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.grade-table__filters {
  align-items: end;
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  margin-top: 16px;
}

label {
  color: #334155;
  display: grid;
  font-size: 13px;
  gap: 6px;
}

input,
select {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  color: #1f2937;
  min-height: 36px;
  padding: 7px 10px;
}

button {
  background: #2563eb;
  border: 0;
  border-radius: 6px;
  color: #ffffff;
  cursor: pointer;
  padding: 8px 14px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

table {
  border-collapse: collapse;
  width: 100%;
}

th,
td {
  border-bottom: 1px solid #e5e7eb;
  padding: 10px;
  text-align: left;
}

.grade-table__feedback {
  color: #047857;
}

.grade-table__error {
  color: #b91c1c;
}

.grade-table__total {
  color: #475569;
  font-size: 14px;
  margin: 0 0 12px;
}

.grade-table__pagination {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 14px;
}

.grade-table__pagination span {
  color: #475569;
  font-size: 14px;
}
</style>
