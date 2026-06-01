<template>
  <main class="homework-student">
    <section class="homework-student__panel" aria-label="homework detail">
      <p v-if="loading">Loading</p>
      <p v-else-if="errorMessage" class="homework-student__error">{{ errorMessage }}</p>
      <template v-else-if="homework">
        <header class="homework-student__header">
          <h1>{{ homework.title }}</h1>
          <p>{{ homework.description }}</p>
        </header>

        <dl class="homework-student__meta">
          <div>
            <dt>Type</dt>
            <dd>{{ homework.type }}</dd>
          </div>
          <div>
            <dt>Status</dt>
            <dd>{{ homework.status }}</dd>
          </div>
          <div>
            <dt>Deadline</dt>
            <dd>{{ formatDateTime(homework.deadline) }}</dd>
          </div>
          <div>
            <dt>Total Score</dt>
            <dd>{{ homework.totalScore }}</dd>
          </div>
          <div>
            <dt>Resubmit</dt>
            <dd>{{ homework.allowResubmit ? 'Allowed' : 'Not allowed' }}</dd>
          </div>
          <div>
            <dt>Late Submit</dt>
            <dd>{{ homework.allowLateSubmit ? 'Allowed' : 'Not allowed' }}</dd>
          </div>
        </dl>

        <section v-if="homework.questions.length > 0" class="homework-student__questions" aria-label="questions">
          <h2>Questions</h2>
          <ol>
            <li v-for="question in homework.questions" :key="question.id">
              <strong>{{ question.questionType }}</strong>
              <p>{{ question.stem }}</p>
              <p v-if="question.optionsJson">{{ question.optionsJson }}</p>
            </li>
          </ol>
        </section>

        <section v-if="homework.testCases.length > 0" class="homework-student__questions" aria-label="public test cases">
          <h2>Public Test Cases</h2>
          <ol>
            <li v-for="testCase in homework.testCases" :key="testCase.id">
              <p>Input: {{ testCase.inputData }}</p>
              <p v-if="testCase.expectedOutput">Output: {{ testCase.expectedOutput }}</p>
            </li>
          </ol>
        </section>

        <form class="homework-student__form" @submit.prevent="submit">
          <label v-if="homework.type === 'OBJECTIVE'">
            <span>Answer JSON</span>
            <textarea v-model="answerJson" name="answerJson" rows="5" />
          </label>

          <label v-if="homework.type === 'TEXT'">
            <span>Answer</span>
            <textarea v-model="answerText" name="answerText" rows="8" />
          </label>

          <label v-if="homework.type === 'FILE' || homework.type === 'TEXT'">
            <span>Attachment IDs</span>
            <input v-model="fileIdsInput" name="fileIds" type="text" />
          </label>

          <template v-if="homework.type === 'CODE'">
            <label>
              <span>Language</span>
              <input v-model="language" name="language" type="text" />
            </label>
            <label>
              <span>Code</span>
              <textarea v-model="codeText" name="codeText" rows="10" />
            </label>
          </template>

          <div class="homework-student__actions">
            <button type="submit" :disabled="submitting || homework.status !== 'PUBLISHED'">Submit</button>
            <button type="button" :disabled="submitting" @click="resetForm">Clear</button>
          </div>
        </form>

        <p v-if="feedbackMessage" class="homework-student__feedback">{{ feedbackMessage }}</p>
        <p v-if="submitErrorMessage" class="homework-student__error">{{ submitErrorMessage }}</p>

        <section v-if="latestSubmission" class="homework-student__submission" aria-label="latest submission">
          <h2>Latest Submission</h2>
          <p>Submission {{ latestSubmission.submissionId }}</p>
          <p>{{ latestSubmission.submitStatus }}</p>
          <p>{{ latestSubmission.evaluationStatus }}</p>
          <p>{{ latestSubmission.reviewStatus }}</p>
          <p v-if="latestSubmission.finalScore !== null && latestSubmission.finalScore !== undefined">
            Score {{ latestSubmission.finalScore }}
          </p>
          <p>{{ formatDateTime(latestSubmission.submittedAt) }}</p>
        </section>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getHomeworkDetail, submitHomework } from '../../api/hwk/homeworks';
import type { HomeworkDetail, HomeworkSubmissionSummary } from '../../types/hwk';

const props = defineProps<{
  courseId: number;
  homeworkId: number;
}>();

const loading = ref(false);
const submitting = ref(false);
const homework = ref<HomeworkDetail | null>(null);
const latestSubmission = ref<HomeworkSubmissionSummary | null>(null);
const errorMessage = ref('');
const submitErrorMessage = ref('');
const feedbackMessage = ref('');
const answerText = ref('');
const answerJson = ref('');
const fileIdsInput = ref('');
const codeText = ref('');
const language = ref('');

onMounted(loadHomework);

async function loadHomework() {
  loading.value = true;
  errorMessage.value = '';
  try {
    homework.value = await getHomeworkDetail(props.homeworkId);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Homework detail failed to load';
  } finally {
    loading.value = false;
  }
}

async function submit() {
  submitErrorMessage.value = validateForm();
  feedbackMessage.value = '';
  if (submitErrorMessage.value) {
    return;
  }

  submitting.value = true;
  try {
    latestSubmission.value = await submitHomework(props.homeworkId, {
      answerText: answerText.value.trim() || undefined,
      answerJson: answerJson.value.trim() || undefined,
      fileIds: parseFileIds(),
      codeText: codeText.value.trim() || undefined,
      language: language.value.trim() || undefined
    });
    feedbackMessage.value = `Submission ${latestSubmission.value.submissionId} ${latestSubmission.value.submitStatus}`;
    resetForm();
  } catch (error) {
    submitErrorMessage.value = error instanceof Error ? error.message : 'Homework submission failed';
  } finally {
    submitting.value = false;
  }
}

function validateForm() {
  if (!homework.value) {
    return 'Homework detail is not loaded';
  }
  if (homework.value.type === 'OBJECTIVE' && !answerJson.value.trim()) {
    return 'Answer JSON is required';
  }
  if (homework.value.type === 'TEXT' && !answerText.value.trim() && parseFileIds().length === 0) {
    return 'Answer content is required';
  }
  if (homework.value.type === 'FILE' && parseFileIds().length === 0) {
    return 'Attachment IDs are required';
  }
  if (homework.value.type === 'CODE' && (!codeText.value.trim() || !language.value.trim())) {
    return 'Code and language are required';
  }
  return '';
}

function parseFileIds() {
  return fileIdsInput.value
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
}

function resetForm() {
  answerText.value = '';
  answerJson.value = '';
  fileIdsInput.value = '';
  codeText.value = '';
  language.value = '';
  submitErrorMessage.value = '';
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
  border-radius: 8px;
  display: grid;
  gap: 20px;
  margin: 0 auto;
  max-width: 960px;
  padding: 24px;
}

.homework-student__header,
.homework-student__questions,
.homework-student__form,
.homework-student__submission {
  display: grid;
  gap: 12px;
}

.homework-student__meta {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
}

.homework-student__meta div,
.homework-student__questions li,
.homework-student__submission {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
}

.homework-student__actions {
  display: flex;
  gap: 8px;
}

label {
  display: grid;
  gap: 6px;
}

input,
textarea,
button {
  background: #ffffff;
  border: 1px solid #b8c2d2;
  color: #111827;
  min-height: 40px;
  padding: 8px 10px;
}

button:disabled {
  color: #697386;
}

.homework-student__feedback {
  color: #116329;
}

.homework-student__error {
  color: #b42318;
}
</style>
