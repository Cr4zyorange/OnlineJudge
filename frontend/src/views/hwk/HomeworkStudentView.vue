<template>
  <main class="homework-student">
    <section class="homework-student__panel" aria-label="作业详情">
      <p v-if="loading">加载中</p>
      <p v-else-if="errorMessage" class="homework-student__error">{{ errorMessage }}</p>
      <template v-else-if="homework">
        <header>
          <p class="homework-student__eyebrow">HWK</p>
          <h1>{{ homework.title }}</h1>
          <p>{{ homework.description }}</p>
          <p v-if="resumeMessage" class="homework-student__feedback">{{ resumeMessage }}</p>
        </header>

        <dl class="homework-student__meta">
          <div>
            <dt>作业类型</dt>
            <dd>{{ homework.type }}</dd>
          </div>
          <div>
            <dt>截止时间</dt>
            <dd>{{ formatDateTime(homework.deadline) }}</dd>
          </div>
          <div>
            <dt>满分</dt>
            <dd>{{ homework.totalScore }}</dd>
          </div>
        </dl>

        <section v-if="homework.questions.length > 0" class="homework-student__block" aria-label="题目">
          <article v-for="question in homework.questions" :key="question.id">
            <strong>{{ question.sortOrder }}. {{ question.stem }}</strong>
            <p v-if="question.optionsJson">{{ question.optionsJson }}</p>
          </article>
        </section>

        <section v-if="homework.testCases.length > 0" class="homework-student__block" aria-label="测试用例">
          <article v-for="testCase in homework.testCases" :key="testCase.id">
            <strong>用例 {{ testCase.sortOrder }}</strong>
            <p>输入：{{ testCase.inputData }}</p>
          </article>
        </section>

        <button type="button" data-testid="complete-homework" :disabled="saving" @click="markCompleted">
          {{ saving ? '记录中' : '标记完成' }}
        </button>
        <p v-if="feedbackMessage" class="homework-student__feedback">{{ feedbackMessage }}</p>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getHomeworkDetail } from '../../api/hwk/homeworks';
import { saveLearningProgress } from '../../api/lrn/learningProgress';
import type { HomeworkDetail } from '../../types/hwk';

const props = defineProps<{
  courseId: number;
  homeworkId: number;
}>();

const homework = ref<HomeworkDetail | null>(null);
const loading = ref(false);
const saving = ref(false);
const errorMessage = ref('');
const feedbackMessage = ref('');
const resumeMessage = ref('');

onMounted(loadHomework);

async function loadHomework() {
  loading.value = true;
  errorMessage.value = '';
  try {
    homework.value = await getHomeworkDetail(props.homeworkId);
    restoreResume();
    await recordProgress(20, `homeworkId=${props.homeworkId}`);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '作业详情加载失败';
  } finally {
    loading.value = false;
  }
}

async function markCompleted() {
  feedbackMessage.value = '';
  saving.value = true;
  try {
    await recordProgress(100, `homeworkId=${props.homeworkId};completed=true`);
    feedbackMessage.value = '已记录完成进度';
  } finally {
    saving.value = false;
  }
}

async function recordProgress(progressPercent: number, lastPosition: string) {
  if (!homework.value) {
    return;
  }
  try {
    await saveLearningProgress({
      courseId: props.courseId,
      chapterId: homework.value.chapterId,
      sourceModule: 'HWK',
      sourceId: props.homeworkId,
      progressPercent,
      lastPosition
    });
  } catch {
    // Progress persistence should not block reading homework content.
  }
}

function restoreResume() {
  const resume = new URLSearchParams(window.location.search).get('resume');
  if (resume) {
    resumeMessage.value = `已恢复上次断点：${resume}`;
  }
}

function formatDateTime(value: string) {
  return value.replace('T', ' ').slice(0, 16);
}
</script>

<style scoped>
.homework-student {
  background: #f6f8fb;
  color: #1f2937;
  min-height: 100vh;
  padding: 24px;
}

.homework-student__panel {
  background: #ffffff;
  border: 1px solid #d7dde8;
  border-radius: 12px;
  display: grid;
  gap: 20px;
  margin: 0 auto;
  max-width: 920px;
  padding: 24px;
}

.homework-student__meta {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
}

.homework-student__meta div,
.homework-student__block article {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
}

.homework-student__block {
  display: grid;
  gap: 10px;
}

.homework-student__eyebrow {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
}

.homework-student__feedback {
  color: #116329;
}

.homework-student__error {
  color: #b42318;
}

button {
  background: #16423c;
  border: 1px solid #16423c;
  border-radius: 8px;
  color: #ffffff;
  cursor: pointer;
  font-weight: 700;
  min-height: 40px;
  padding: 0 14px;
}
</style>
