<template>
  <main class="homework-student">
    <section class="homework-student__panel" aria-label="作业详情">
      <p v-if="loading">加载中</p>
      <p v-else-if="errorMessage" class="homework-student__error">{{ errorMessage }}</p>
      <template v-else-if="homework">
        <header class="homework-student__header">
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
            <dt>状态</dt>
            <dd>{{ homework.status }}</dd>
          </div>
          <div>
            <dt>截止时间</dt>
            <dd>{{ formatDateTime(homework.deadline) }}</dd>
          </div>
          <div>
            <dt>满分</dt>
            <dd>{{ homework.totalScore }}</dd>
          </div>
          <div>
            <dt>多次提交</dt>
            <dd>{{ homework.allowResubmit ? '允许' : '不允许' }}</dd>
          </div>
          <div>
            <dt>逾期提交</dt>
            <dd>{{ homework.allowLateSubmit ? '允许' : '不允许' }}</dd>
          </div>
        </dl>

        <section v-if="homework.questions.length > 0" class="homework-student__block" aria-label="题目">
          <article v-for="question in homework.questions" :key="question.id">
            <strong>{{ question.sortOrder }}. {{ question.stem }}</strong>
            <p v-if="question.optionsJson">{{ question.optionsJson }}</p>
          </article>
        </section>

        <section v-if="homework.testCases.length > 0" class="homework-student__block" aria-label="公开测试用例">
          <article v-for="testCase in homework.testCases" :key="testCase.id">
            <strong>用例 {{ testCase.sortOrder }}</strong>
            <p>输入：{{ testCase.inputData }}</p>
            <p v-if="testCase.expectedOutput">输出：{{ testCase.expectedOutput }}</p>
          </article>
        </section>

        <form class="homework-student__form" @submit.prevent="submit">
          <label v-if="homework.type === 'OBJECTIVE'">
            <span>客观题答案 JSON</span>
            <textarea v-model="answerJson" name="answerJson" rows="5" />
          </label>

          <label v-if="homework.type === 'TEXT'">
            <span>文本答案</span>
            <textarea v-model="answerText" name="answerText" rows="8" />
          </label>

          <label v-if="homework.type === 'FILE' || homework.type === 'TEXT'">
            <span>附件 ID</span>
            <input v-model="fileIdsInput" name="fileIds" type="text" />
          </label>

          <template v-if="homework.type === 'CODE'">
            <label>
              <span>语言</span>
              <select v-if="allowedCodeLanguages.length > 0" v-model="language" name="language">
                <option v-for="item in allowedCodeLanguages" :key="item" :value="item">{{ item }}</option>
              </select>
              <input v-else v-model="language" name="language" type="text" />
            </label>
            <label>
              <span>代码</span>
              <textarea v-model="codeText" name="codeText" rows="10" />
            </label>
          </template>

          <div class="homework-student__actions">
            <button type="submit" :disabled="submitting || homework.status !== 'PUBLISHED'">
              {{ submitting ? '提交中' : '提交作业' }}
            </button>
            <button type="button" :disabled="submitting" @click="resetForm">清空</button>
            <button
              type="button"
              data-testid="complete-homework"
              :disabled="saving || submitting"
              @click="markCompleted"
            >
              {{ saving ? '记录中' : '标记完成' }}
            </button>
          </div>
        </form>

        <p v-if="feedbackMessage" class="homework-student__feedback">{{ feedbackMessage }}</p>
        <p v-if="submitErrorMessage" class="homework-student__error">{{ submitErrorMessage }}</p>

        <section v-if="latestSubmission" class="homework-student__submission" aria-label="最新提交">
          <h2>最新提交</h2>
          <p>Submission {{ latestSubmission.submissionId }}</p>
          <p>{{ latestSubmission.submitStatus }}</p>
          <p>{{ latestSubmission.evaluationStatus }}</p>
          <p>{{ latestSubmission.reviewStatus }}</p>
          <p v-if="latestSubmission.finalScore !== null && latestSubmission.finalScore !== undefined">
            得分 {{ latestSubmission.finalScore }}
          </p>
          <p>{{ formatDateTime(latestSubmission.submittedAt) }}</p>
        </section>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getHomeworkDetail, submitHomework } from '../../api/hwk/homeworks';
import { saveLearningProgress } from '../../api/lrn/learningProgress';
import { reportLearningRecord } from '../../api/lrn/learningRecords';
import type { HomeworkDetail, HomeworkSubmissionSummary } from '../../types/hwk';

const props = defineProps<{
  courseId: number;
  homeworkId: number;
}>();

const homework = ref<HomeworkDetail | null>(null);
const latestSubmission = ref<HomeworkSubmissionSummary | null>(null);
const loading = ref(false);
const submitting = ref(false);
const saving = ref(false);
const errorMessage = ref('');
const submitErrorMessage = ref('');
const feedbackMessage = ref('');
const resumeMessage = ref('');
const answerText = ref('');
const answerJson = ref('');
const fileIdsInput = ref('');
const codeText = ref('');
const language = ref('');
const openedAt = ref<Date | null>(null);
const allowedCodeLanguages = computed(() => parseLanguageLimit(homework.value?.languageLimitJson));

onMounted(loadHomework);

async function loadHomework() {
  loading.value = true;
  errorMessage.value = '';
  try {
    homework.value = await getHomeworkDetail(props.homeworkId);
    openedAt.value = new Date();
    syncDefaultCodeLanguage();
    restoreResume();
    await recordProgress(20, `homeworkId=${props.homeworkId}`);
    await recordBehavior('ACCESS', 0);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '作业详情加载失败';
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
    await recordProgress(100, `homeworkId=${props.homeworkId};submitted=${latestSubmission.value.submissionId}`);
    await recordBehavior('SUBMIT', elapsedSeconds());
    feedbackMessage.value = `Submission ${latestSubmission.value.submissionId} ${latestSubmission.value.submitStatus}`;
    resetForm();
  } catch (error) {
    submitErrorMessage.value = error instanceof Error ? error.message : 'Homework submission failed';
  } finally {
    submitting.value = false;
  }
}

async function markCompleted() {
  feedbackMessage.value = '';
  saving.value = true;
  try {
    await recordProgress(100, `homeworkId=${props.homeworkId};completed=true`);
    await recordBehavior('COMPLETE', elapsedSeconds());
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
    // Progress persistence should not block reading or submitting homework.
  }
}

async function recordBehavior(actionType: 'ACCESS' | 'SUBMIT' | 'COMPLETE', durationSeconds: number) {
  if (!homework.value) {
    return;
  }
  try {
    await reportLearningRecord({
      courseId: props.courseId,
      sourceModule: 'HWK',
      sourceId: props.homeworkId,
      actionType,
      durationSeconds
    });
  } catch {
    // Behavior tracking should not block reading or submitting homework.
  }
}

function elapsedSeconds() {
  if (!openedAt.value) {
    return 0;
  }
  return Math.max(0, Math.round((Date.now() - openedAt.value.getTime()) / 1000));
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
  if (
    homework.value.type === 'CODE'
    && allowedCodeLanguages.value.length > 0
    && !allowedCodeLanguages.value.includes(language.value.trim())
  ) {
    return 'Language is not allowed for this homework';
  }
  return '';
}

function parseLanguageLimit(value: string | null | undefined) {
  if (!value) {
    return [];
  }
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed)
      ? parsed.map((item) => String(item).trim()).filter(Boolean)
      : [];
  } catch {
    return [];
  }
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
  syncDefaultCodeLanguage();
  submitErrorMessage.value = '';
}

function syncDefaultCodeLanguage() {
  if (homework.value?.type !== 'CODE') {
    return;
  }
  if (allowedCodeLanguages.value.length > 0 && !allowedCodeLanguages.value.includes(language.value.trim())) {
    language.value = allowedCodeLanguages.value[0];
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
  border-radius: 8px;
  display: grid;
  gap: 20px;
  margin: 0 auto;
  max-width: 960px;
  padding: 24px;
}

.homework-student__header,
.homework-student__block,
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
.homework-student__block article,
.homework-student__submission {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
}

.homework-student__eyebrow {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
}

.homework-student__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

label {
  display: grid;
  gap: 6px;
}

input,
textarea,
select,
button {
  background: #ffffff;
  border: 1px solid #b8c2d2;
  border-radius: 8px;
  color: #111827;
  min-height: 40px;
  padding: 8px 10px;
}

button {
  cursor: pointer;
  font-weight: 700;
}

button:disabled {
  color: #697386;
  cursor: not-allowed;
}

.homework-student__feedback {
  color: #116329;
}

.homework-student__error {
  color: #b42318;
}
</style>
