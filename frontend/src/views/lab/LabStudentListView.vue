<template>
  <main class="lab-student-list">
    <section class="lab-student-list__panel" aria-label="学生实验列表">
      <header class="lab-student-list__header">
        <h1>课程实验</h1>
        <p>查看当前课程已发布、已截止或已发布成绩的实验。</p>
      </header>

      <p v-if="loading">加载中</p>
      <p v-else-if="errorMessage" class="lab-student-list__error">{{ errorMessage }}</p>
      <p v-else-if="visibleLabs.length === 0">暂无可进入实验</p>
      <ul v-else class="lab-student-list__items">
        <li v-for="lab in visibleLabs" :key="lab.id">
          <div>
            <h2>{{ lab.title }}</h2>
            <p>{{ lab.status }} · {{ lab.evaluationMode }} · 截止 {{ formatDateTime(lab.deadline) }}</p>
            <p>满分 {{ lab.maxScore }} · {{ lab.reportRequired ? '需要实验报告' : '无需实验报告' }}</p>
          </div>
          <a
            :data-testid="`open-lab-${lab.id}`"
            :href="`/courses/${props.courseId}/labs/${lab.id}?role=student`"
          >
            进入实验
          </a>
        </li>
      </ul>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { listLabs } from '../../api/lab/labs';
import type { LabExperimentSummary } from '../../types/lab';

const props = defineProps<{
  courseId: number;
}>();

const loading = ref(false);
const errorMessage = ref('');
const labs = ref<LabExperimentSummary[]>([]);

const visibleLabs = computed(() => labs.value.filter((lab) => (
  lab.status === 'PUBLISHED'
  || lab.status === 'CLOSED'
  || lab.status === 'SCORE_PUBLISHED'
)));

onMounted(loadLabs);

async function loadLabs() {
  loading.value = true;
  errorMessage.value = '';
  try {
    labs.value = await listLabs(props.courseId);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '实验列表加载失败';
  } finally {
    loading.value = false;
  }
}

function formatDateTime(value: string) {
  return value.replace('T', ' ').slice(0, 16);
}
</script>

<style scoped>
.lab-student-list {
  min-height: 100vh;
  padding: 24px;
}

.lab-student-list__panel {
  display: grid;
  gap: 16px;
  max-width: 960px;
  margin: 0 auto;
  padding: 24px;
}

.lab-student-list__header h1,
.lab-student-list__header p {
  margin: 0 0 6px;
}

.lab-student-list__items {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.lab-student-list__items li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px;
  border: 1px solid rgba(22, 66, 60, 0.12);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.3);
}

.lab-student-list__items h2,
.lab-student-list__items p {
  margin: 0 0 6px;
}

.lab-student-list__items a {
  flex: 0 0 auto;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--oj-brand);
  color: #fff;
  font-weight: 700;
  text-decoration: none;
}

.lab-student-list__error {
  color: #b42318;
}

@media (max-width: 640px) {
  .lab-student-list__items li {
    align-items: stretch;
    flex-direction: column;
  }

  .lab-student-list__items a {
    text-align: center;
  }
}
</style>
