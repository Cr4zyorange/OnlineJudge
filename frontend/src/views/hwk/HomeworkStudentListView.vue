<template>
  <main class="homework-student-list">
    <section class="homework-student-list__panel" aria-label="student homework list">
      <header class="homework-student-list__header">
        <h1>Homework</h1>
      </header>

      <p v-if="loading">Loading</p>
      <p v-else-if="errorMessage" class="homework-student-list__error">{{ errorMessage }}</p>
      <p v-else-if="homeworks.length === 0">No visible homework</p>
      <ul v-else class="homework-student-list__items">
        <li v-for="homework in homeworks" :key="homework.id">
          <div>
            <h2>{{ homework.title }}</h2>
            <p>{{ homework.description }}</p>
            <p>{{ homework.type }} · {{ homework.status }} · due {{ formatDateTime(homework.deadline) }}</p>
          </div>
          <a
            :data-testid="`open-homework-${homework.id}`"
            :href="`/courses/${props.courseId}/homeworks/${homework.id}?role=student`"
          >Open</a>
        </li>
      </ul>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { listHomeworks } from '../../api/hwk/homeworks';
import type { HomeworkSummary } from '../../types/hwk';

const props = defineProps<{
  courseId: number;
}>();

const loading = ref(false);
const errorMessage = ref('');
const homeworks = ref<HomeworkSummary[]>([]);

onMounted(loadHomeworks);

async function loadHomeworks() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const page = await listHomeworks({ courseId: props.courseId, page: 1, size: 20 });
    homeworks.value = page.list;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Homework list failed to load';
  } finally {
    loading.value = false;
  }
}

function formatDateTime(value: string) {
  return value.replace('T', ' ').slice(0, 16);
}
</script>

<style scoped>
.homework-student-list {
  background: #f6f8fb;
  color: #1f2937;
  min-height: 100vh;
  padding: 24px;
}

.homework-student-list__panel {
  background: #ffffff;
  border: 1px solid #d7dde8;
  border-radius: 8px;
  display: grid;
  gap: 16px;
  margin: 0 auto;
  max-width: 960px;
  padding: 24px;
}

.homework-student-list__header h1 {
  margin: 0;
}

.homework-student-list__items {
  display: grid;
  gap: 12px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.homework-student-list__items li {
  align-items: center;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  display: flex;
  gap: 16px;
  justify-content: space-between;
  padding: 14px;
}

.homework-student-list__items h2,
.homework-student-list__items p {
  margin: 0 0 6px;
}

.homework-student-list__items a {
  border: 1px solid #aeb8c8;
  color: #175cd3;
  padding: 8px 12px;
  text-decoration: none;
}

.homework-student-list__error {
  color: #b42318;
}
</style>
