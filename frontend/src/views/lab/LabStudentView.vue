<template>
  <main class="lab-student">
    <section class="lab-student__panel" aria-label="实验详情">
      <p v-if="loading">加载中</p>
      <p v-else-if="errorMessage" class="lab-student__error">{{ errorMessage }}</p>
      <template v-else-if="labDetail">
        <header class="lab-student__header">
          <h1>{{ labDetail.title }}</h1>
          <p>{{ labDetail.description }}</p>
          <p v-if="resumeMessage" class="lab-student__feedback">{{ resumeMessage }}</p>
        </header>

        <dl class="lab-student__meta">
          <div>
            <dt>截止时间</dt>
            <dd>{{ formatDateTime(labDetail.deadline) }}</dd>
          </div>
          <div>
            <dt>评测方式</dt>
            <dd>{{ labDetail.evaluationMode }}</dd>
          </div>
          <div>
            <dt>语言限制</dt>
            <dd>{{ labDetail.allowedLanguages ?? '未限制' }}</dd>
          </div>
          <div>
            <dt>附件数量</dt>
            <dd>{{ labDetail.attachmentIds.length }}</dd>
          </div>
        </dl>

        <section class="lab-student__cases" aria-label="公开测试用例">
          <h2>公开测试用例</h2>
          <p v-if="publicTestcases.length === 0">暂无公开测试用例</p>
          <ul v-else>
            <li v-for="testcase in publicTestcases" :key="testcase.id">
              <strong>用例 {{ testcase.orderNum }}</strong>
              <span>输入：{{ testcase.input }}</span>
              <span>输出：{{ testcase.expectedOutput }}</span>
            </li>
          </ul>
        </section>

        <form class="lab-student__form" @submit.prevent="submit">
          <label>
            <span>编程语言</span>
            <select v-model="language" name="language">
              <option value="">请选择</option>
              <option v-for="item in languageOptions" :key="item" :value="item">{{ item }}</option>
            </select>
          </label>

          <label class="lab-student__code">
            <span>代码内容</span>
            <textarea v-model="code" name="code" rows="10" @blur="saveDraftProgress" />
          </label>

          <label>
            <span>或上传文件</span>
            <input name="file" type="file" @change="onFileChange" />
          </label>

          <div class="lab-student__actions">
            <button type="submit" :disabled="submitting">提交实验</button>
            <button type="button" :disabled="submitting" @click="resetForm">清空</button>
          </div>
        </form>

        <p v-if="feedbackMessage" class="lab-student__feedback">{{ feedbackMessage }}</p>
        <p v-if="submitErrorMessage" class="lab-student__error">{{ submitErrorMessage }}</p>
        <p v-if="historyErrorMessage" class="lab-student__error">{{ historyErrorMessage }}</p>

        <div class="lab-student__history-link">
          <a :href="historyHref">查看提交历史</a>
        </div>

        <section v-if="latestSubmission" class="lab-student__submission" aria-label="最近一次提交">
          <h2>最近一次提交</h2>
          <p>版本 {{ latestSubmission.version }}</p>
          <p>提交状态：{{ latestSubmission.submitStatus }}</p>
          <p>评测状态：{{ latestSubmission.evaluationStatus }}</p>
          <p>提交时间：{{ formatDateTime(latestSubmission.submittedAt) }}</p>
          <template v-if="latestEvaluationResult">
            <p>自动得分：{{ latestEvaluationResult.score }}</p>
            <p>通过用例：{{ latestEvaluationResult.passedCases }} / {{ latestEvaluationResult.totalCases }}</p>
            <p>{{ latestEvaluationResult.message }}</p>
            <ul class="lab-student__case-results">
              <li v-for="item in latestEvaluationResult.caseResults" :key="item.testcaseId">
                <strong>用例 {{ item.orderNum }}</strong>
                <span>状态：{{ item.passed ? '通过' : '失败' }}</span>
                <span>得分：{{ item.score }}</span>
                <span>{{ item.message }}</span>
              </li>
            </ul>
          </template>
        </section>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { getLabDetail, getLabSubmissionResult, listLabSubmissions, submitLab } from '../../api/lab/labs';
import { saveLearningProgress } from '../../api/lrn/learningProgress';
import type {
  LabExperimentDetail,
  LabSubmissionHistoryItem,
  LabSubmissionResult,
  LabSubmissionSummary
} from '../../types/lab';

const MAX_UPLOAD_SIZE_BYTES = 5 * 1024 * 1024;
const EVALUATION_POLL_INTERVAL_MS = 1000;
const TERMINAL_EVALUATION_STATUSES = new Set<LabSubmissionSummary['evaluationStatus']>([
  'NONE',
  'ACCEPTED',
  'WRONG_ANSWER',
  'COMPILE_ERROR',
  'RUNTIME_ERROR',
  'TIME_LIMIT_EXCEEDED',
  'SYSTEM_ERROR'
]);
const ALLOWED_FILE_EXTENSIONS: Record<string, string[]> = {
  java: ['java'],
  python: ['py'],
  cpp: ['cpp', 'cc', 'cxx'],
  c: ['c']
};

const props = defineProps<{
  courseId: number;
  labId: number;
}>();

const loading = ref(false);
const submitting = ref(false);
const labDetail = ref<LabExperimentDetail | null>(null);
const latestSubmission = ref<LabSubmissionSummary | LabSubmissionHistoryItem | null>(null);
const latestEvaluationResult = ref<LabSubmissionResult | null>(null);
const errorMessage = ref('');
const submitErrorMessage = ref('');
const feedbackMessage = ref('');
const resumeMessage = ref('');
const historyErrorMessage = ref('');
const language = ref('');
const code = ref('');
const selectedFile = ref<File | null>(null);
let evaluationPollTimer: number | null = null;

const publicTestcases = computed(() => (labDetail.value?.testcases ?? []).filter((testcase) => testcase.public));
const languageOptions = computed(() => {
  const raw = labDetail.value?.allowedLanguages;
  if (!raw) {
    return ['java', 'python'];
  }
  return raw.split(',').map((item) => item.trim()).filter(Boolean);
});
const historyHref = computed(() => `/courses/${props.courseId}/labs/${props.labId}/submissions?role=student`);

onMounted(loadLabDetail);
onUnmounted(clearEvaluationPoll);

async function loadLabDetail() {
  loading.value = true;
  errorMessage.value = '';
  try {
    labDetail.value = await getLabDetail(props.labId);
    const resumed = restoreResumeCode();
    if (languageOptions.value.length === 1) {
      language.value = languageOptions.value[0];
    }
    if (!resumed) {
      await recordProgress(10, `labId=${props.labId}`);
    }
    void loadLatestSubmission();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '实验详情加载失败';
  } finally {
    loading.value = false;
  }
}

async function loadLatestSubmission() {
  historyErrorMessage.value = '';
  try {
    const history = await listLabSubmissions(props.labId);
    latestSubmission.value = history[0] ?? null;
    if (latestSubmission.value) {
      await refreshLatestEvaluationResult(latestSubmission.value.submissionId);
    } else {
      clearEvaluationPoll();
      latestEvaluationResult.value = null;
    }
  } catch (error) {
    historyErrorMessage.value = error instanceof Error ? error.message : '提交历史加载失败';
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
    latestSubmission.value = await submitLab(props.labId, {
      language: language.value,
      code: code.value.trim() || undefined,
      file: selectedFile.value ?? undefined
    });
    latestEvaluationResult.value = null;
    await refreshLatestEvaluationResult(latestSubmission.value.submissionId);
    feedbackMessage.value = `提交成功，版本 ${latestSubmission.value.version}`;
    await recordProgress(100, `submittedVersion=${latestSubmission.value.version}`);
    code.value = '';
    selectedFile.value = null;
  } catch (error) {
    submitErrorMessage.value = error instanceof Error ? error.message : '实验提交失败';
  } finally {
    submitting.value = false;
  }
}

async function refreshLatestEvaluationResult(submissionId: number) {
  clearEvaluationPoll();
  try {
    const result = await getLabSubmissionResult(props.labId, submissionId);
    latestEvaluationResult.value = result;
    updateLatestSubmissionEvaluation(result);
    if (!isTerminalEvaluationStatus(result.evaluationStatus)) {
      evaluationPollTimer = window.setTimeout(() => {
        void refreshLatestEvaluationResult(submissionId);
      }, EVALUATION_POLL_INTERVAL_MS);
    }
  } catch (error) {
    historyErrorMessage.value = error instanceof Error ? error.message : '评测结果加载失败';
  }
}

function updateLatestSubmissionEvaluation(result: LabSubmissionResult) {
  if (!latestSubmission.value || latestSubmission.value.submissionId !== result.submissionId) {
    return;
  }
  latestSubmission.value = {
    ...latestSubmission.value,
    evaluationStatus: result.evaluationStatus,
    autoScore: result.score
  };
}

function isTerminalEvaluationStatus(status: LabSubmissionSummary['evaluationStatus']) {
  return TERMINAL_EVALUATION_STATUSES.has(status);
}

function clearEvaluationPoll() {
  if (evaluationPollTimer === null) {
    return;
  }
  window.clearTimeout(evaluationPollTimer);
  evaluationPollTimer = null;
}

async function saveDraftProgress() {
  if (!code.value.trim()) {
    return;
  }
  await recordProgress(40, `code=${code.value.slice(0, 450)}`);
}

async function recordProgress(progressPercent: number, lastPosition: string) {
  if (!labDetail.value) {
    return;
  }
  try {
    await saveLearningProgress({
      courseId: props.courseId,
      chapterId: labDetail.value.chapterId,
      sourceModule: 'LAB',
      sourceId: props.labId,
      progressPercent,
      lastPosition
    });
  } catch {
    // Progress persistence should not block lab reading or submission.
  }
}

function restoreResumeCode() {
  const resume = new URLSearchParams(window.location.search).get('resume');
  if (!resume) {
    return false;
  }
  resumeMessage.value = `已恢复上次断点：${resume}`;
  if (resume.startsWith('code=')) {
    code.value = resume.slice('code='.length);
  }
  return true;
}

function validateForm() {
  const errors: string[] = [];
  if (!language.value) {
    errors.push('请选择编程语言');
  }
  if (!code.value.trim() && !selectedFile.value) {
    errors.push('请填写代码或上传文件');
  }
  if (selectedFile.value) {
    const extension = getFileExtension(selectedFile.value.name);
    const allowedExtensions = ALLOWED_FILE_EXTENSIONS[language.value] ?? [];
    if (!allowedExtensions.includes(extension)) {
      const readableExtensions = allowedExtensions.length > 0
        ? allowedExtensions.map((item) => `.${item}`).join('、')
        : '当前语言对应的源码文件';
      errors.push(`仅支持 ${readableExtensions} 文件`);
    }
    if (selectedFile.value.size > MAX_UPLOAD_SIZE_BYTES) {
      errors.push('上传文件大小不能超过 5MB');
    }
  }
  return errors.join('；');
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  selectedFile.value = input.files?.[0] ?? null;
}

function resetForm() {
  language.value = languageOptions.value.length === 1 ? languageOptions.value[0] : '';
  code.value = '';
  selectedFile.value = null;
  submitErrorMessage.value = '';
  feedbackMessage.value = '';
}

function formatDateTime(value: string) {
  return value.replace('T', ' ').slice(0, 16);
}

function getFileExtension(fileName: string) {
  const separatorIndex = fileName.lastIndexOf('.');
  if (separatorIndex < 0 || separatorIndex === fileName.length - 1) {
    return '';
  }
  return fileName.slice(separatorIndex + 1).toLowerCase();
}
</script>

<style scoped>
.lab-student {
  background: #f6f8fb;
  color: #1f2937;
  min-height: 100vh;
  padding: 24px;
}

.lab-student__panel {
  background: #ffffff;
  border: 1px solid #d7dde8;
  border-radius: 12px;
  display: grid;
  gap: 20px;
  margin: 0 auto;
  max-width: 960px;
  padding: 24px;
}

.lab-student__header,
.lab-student__form,
.lab-student__cases,
.lab-student__submission {
  display: grid;
  gap: 12px;
}

.lab-student__meta {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.lab-student__meta div,
.lab-student__cases li,
.lab-student__submission {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
}

.lab-student__cases ul {
  display: grid;
  gap: 10px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.lab-student__case-results {
  display: grid;
  gap: 10px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.lab-student__cases li {
  display: grid;
  gap: 6px;
}

.lab-student__case-results li {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  display: grid;
  gap: 6px;
  padding: 12px;
}

.lab-student__form label {
  display: grid;
  gap: 6px;
}

.lab-student__code {
  grid-column: 1 / -1;
}

.lab-student__actions {
  display: flex;
  gap: 8px;
}

.lab-student__history-link a {
  color: #175cd3;
  text-decoration: none;
}

input,
select,
textarea,
button {
  background: #ffffff;
  border: 1px solid #b8c2d2;
  color: #111827;
  min-height: 40px;
  padding: 8px 10px;
}

textarea {
  resize: vertical;
}

.lab-student__feedback {
  color: #116329;
}

.lab-student__error {
  color: #b42318;
}
</style>
