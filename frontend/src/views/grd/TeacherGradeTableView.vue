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
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import {
  listCourseGrades,
  recalculateCourseGrades,
  syncSourceGrades
} from '../../api/grd/gradeRecords';
import type { CourseGradeRow } from '../../types/grd';

const props = defineProps<{
  courseId: number;
}>();

const rows = ref<CourseGradeRow[]>([]);
const loading = ref(false);
const busy = ref(false);
const feedback = ref('');
const errorMessage = ref('');
const total = ref(0);

onMounted(loadRows);

async function loadRows() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const result = await listCourseGrades(props.courseId, { page: 1, size: 20 });
    rows.value = result.records;
    total.value = result.total;
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
    await loadRows();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '课程总评计算失败';
  } finally {
    busy.value = false;
  }
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
</style>
