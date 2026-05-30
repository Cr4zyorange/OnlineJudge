<template>
  <main class="student-grade">
    <section class="student-grade__summary" aria-label="学生个人成绩">
      <div>
        <h1>我的成绩</h1>
        <p v-if="publishedAt">发布于 {{ publishedAt }}</p>
        <p v-else>仅展示已发布成绩</p>
      </div>
      <div class="student-grade__score" data-testid="final-score">
        <span>课程总评</span>
        <strong>{{ gradeRow?.summary?.finalScore ?? '-' }}</strong>
        <small>{{ gradeRow?.summary?.finalStatus ?? '未发布' }}</small>
      </div>
    </section>

    <section class="student-grade__content" aria-label="成绩构成">
      <p v-if="loading" class="student-grade__state">加载中</p>
      <p v-else-if="errorMessage" class="student-grade__state student-grade__state--error">
        {{ errorMessage }}
      </p>
      <p v-else-if="records.length === 0" class="student-grade__state">暂无已发布成绩</p>
      <table v-else>
        <thead>
          <tr>
            <th>成绩项</th>
            <th>来源任务</th>
            <th>原始分</th>
            <th>折算分</th>
            <th>状态</th>
            <th>反馈</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in records" :key="record.id">
            <td>成绩项 {{ record.gradeItemId }}</td>
            <td>{{ sourceLabel(record) }}</td>
            <td>{{ record.rawScore ?? '-' }}</td>
            <td>{{ record.weightedScore ?? '-' }}</td>
            <td>{{ gradeStatusLabel(record.gradeStatus) }}</td>
            <td>{{ record.comment || '暂无反馈' }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getMyPublishedGrades } from '../../api/grd/gradeRecords';
import type { CourseGradeRow, GradeRecord, GradeStatus } from '../../types/grd';

const props = defineProps<{
  courseId: number;
}>();

const gradeRow = ref<CourseGradeRow | null>(null);
const loading = ref(false);
const errorMessage = ref('');

const records = computed(() => gradeRow.value?.records ?? []);
const publishedAt = computed(() => formatDateTime(gradeRow.value?.summary?.publishedAt ?? null));

onMounted(loadMyGrades);

async function loadMyGrades() {
  loading.value = true;
  errorMessage.value = '';
  try {
    gradeRow.value = await getMyPublishedGrades(props.courseId);
  } catch (error) {
    gradeRow.value = null;
    errorMessage.value = error instanceof Error ? error.message : '成绩加载失败';
  } finally {
    loading.value = false;
  }
}

function sourceLabel(record: GradeRecord) {
  return record.sourceId ? `${record.sourceType} #${record.sourceId}` : record.sourceType;
}

function gradeStatusLabel(status: GradeStatus) {
  const labels: Record<GradeStatus, string> = {
    SCORED: '已评分',
    UNSUBMITTED: '未提交',
    UNGRADED: '待评分',
    MISSING: '缺失',
    ADJUSTED: '已调整'
  };
  return labels[status];
}

function formatDateTime(value: string | null) {
  if (!value) {
    return '';
  }
  return value.replace('T', ' ').slice(0, 16);
}
</script>

<style scoped>
.student-grade {
  align-content: start;
  background: #f6f8fb;
  color: #1f2937;
  display: grid;
  gap: 16px;
  min-height: 100vh;
  padding: 24px;
}

.student-grade__summary,
.student-grade__content {
  background: #ffffff;
  border: 1px solid #d8dee9;
  border-radius: 8px;
  min-width: 0;
  padding: 18px;
}

.student-grade__summary {
  align-items: center;
  display: flex;
  gap: 20px;
  justify-content: space-between;
}

.student-grade__summary h1 {
  font-size: 22px;
  margin: 0 0 6px;
}

.student-grade__summary p,
.student-grade__score span,
.student-grade__score small {
  color: #64748b;
  margin: 0;
}

.student-grade__score {
  display: grid;
  gap: 4px;
  justify-items: end;
  min-width: 140px;
}

.student-grade__score strong {
  color: #16423c;
  font-size: 32px;
  line-height: 1;
}

.student-grade__content {
  overflow-x: auto;
}

.student-grade__state {
  color: #475569;
  margin: 0;
}

.student-grade__state--error {
  color: #b91c1c;
}

table {
  border-collapse: collapse;
  min-width: 760px;
  width: 100%;
}

th,
td {
  border-bottom: 1px solid #e5e7eb;
  padding: 11px 10px;
  text-align: left;
}

th {
  color: #334155;
  font-size: 13px;
}

td {
  color: #1f2937;
}

@media (max-width: 640px) {
  .student-grade {
    padding: 16px;
  }

  .student-grade__summary {
    align-items: flex-start;
    display: grid;
  }

  .student-grade__score {
    justify-items: start;
  }
}
</style>
