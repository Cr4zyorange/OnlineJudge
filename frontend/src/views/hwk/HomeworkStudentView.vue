<template>
  <main class="homework-student">
    <section class="homework-student__panel" aria-label="作业详情">
      <section
        v-if="loading"
        class="homework-student__state"
        data-testid="homework-page-loading"
        role="status"
        aria-live="polite"
      >
        <span class="homework-student__state-mark" aria-hidden="true" />
        <div>
          <p class="homework-student__eyebrow">课程作业</p>
          <h1>正在加载作业</h1>
          <p>正在同步作业内容与最近提交状态。</p>
        </div>
      </section>

      <section
        v-else-if="errorMessage"
        class="homework-student__state homework-student__state--error"
        data-testid="homework-page-error"
        role="alert"
      >
        <span class="homework-student__state-mark" aria-hidden="true">!</span>
        <div>
          <p class="homework-student__eyebrow">加载失败</p>
          <h1>作业详情加载失败</h1>
          <p>{{ errorMessage }}</p>
        </div>
      </section>

      <template v-else-if="homework">
        <header class="homework-student__header">
          <div class="homework-student__heading-copy">
            <p class="homework-student__eyebrow">学生作业台 · HWK</p>
            <h1>{{ homework.title }}</h1>
            <p class="homework-student__lede">{{ homework.description }}</p>
            <p v-if="resumeMessage" class="homework-student__feedback">{{ resumeMessage }}</p>
          </div>
          <span class="homework-student__type-chip">{{ formatHomeworkType(homework.type) }}</span>
        </header>

        <dl class="homework-student__summary" aria-label="作业概览">
          <div data-testid="homework-status-summary">
            <dt>当前状态</dt>
            <dd>{{ homeworkStatusSummary }}</dd>
            <small>{{ submitAvailabilitySummary }}</small>
          </div>
          <div data-testid="homework-deadline-summary">
            <dt>截止时间</dt>
            <dd>{{ formatDateTime(homework.deadline) }}</dd>
            <small>{{ homework.allowLateSubmit ? '支持逾期提交' : '截止后不可提交' }}</small>
          </div>
          <div data-testid="homework-submission-summary">
            <dt>当前提交</dt>
            <dd>{{ currentSubmissionSummary }}</dd>
            <small v-if="latestSubmission">{{ formatDateTime(latestSubmission.submittedAt) }}</small>
            <small v-else>提交后可在历史中持续跟踪</small>
          </div>
        </dl>

        <div class="homework-student__workspace">
          <div class="homework-student__content-pane">
            <section class="homework-student__content-card" aria-labelledby="homework-brief-heading">
              <div class="homework-student__section-heading">
                <div>
                  <p class="homework-student__eyebrow">任务说明</p>
                  <h2 id="homework-brief-heading">完成要求</h2>
                </div>
                <span>{{ homework.totalScore }} 分</span>
              </div>
              <p class="homework-student__description">{{ homework.description }}</p>
              <dl class="homework-student__details">
                <div>
                  <dt>作业类型</dt>
                  <dd>{{ formatHomeworkType(homework.type) }}</dd>
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
            </section>

            <section
              v-if="homework.questions.length > 0"
              class="homework-student__content-card homework-student__block"
              aria-label="题目"
            >
              <div class="homework-student__section-heading">
                <div>
                  <p class="homework-student__eyebrow">作答内容</p>
                  <h2>题目</h2>
                </div>
                <span>{{ homework.questions.length }} 题</span>
              </div>
              <article v-for="question in homework.questions" :key="question.id">
                <strong>{{ question.sortOrder }}. {{ question.stem }}</strong>
                <ul v-if="formatQuestionOptions(question.optionsJson).length > 0" class="homework-student__options">
                  <li v-for="option in formatQuestionOptions(question.optionsJson)" :key="option">{{ option }}</li>
                </ul>
              </article>
            </section>

            <section
              v-if="homework.testCases.length > 0"
              class="homework-student__content-card homework-student__block"
              aria-label="公开测试用例"
            >
              <div class="homework-student__section-heading">
                <div>
                  <p class="homework-student__eyebrow">运行参考</p>
                  <h2>公开测试用例</h2>
                </div>
                <span>{{ homework.testCases.length }} 组</span>
              </div>
              <article v-for="testCase in homework.testCases" :key="testCase.id">
                <strong>用例 {{ testCase.sortOrder }}</strong>
                <p>输入：{{ testCase.inputData }}</p>
                <p v-if="testCase.expectedOutput">输出：{{ testCase.expectedOutput }}</p>
              </article>
            </section>

            <section
              v-if="latestSubmission"
              class="homework-student__content-card homework-student__submission"
              data-testid="homework-latest-submission"
              aria-label="最新提交"
            >
              <div class="homework-student__section-heading">
                <div>
                  <p class="homework-student__eyebrow">提交回执</p>
                  <h2>最新提交</h2>
                </div>
                <span>版本 {{ latestSubmission.version }}</span>
              </div>
              <div class="homework-student__receipt-grid">
                <p>提交编号 {{ latestSubmission.submissionId }}</p>
                <p>提交状态：{{ formatSubmitStatus(latestSubmission.submitStatus) }}</p>
                <p v-if="canViewEvaluation">
                  评测状态：{{ formatEvaluationStatus(latestSubmission.evaluationStatus) }}
                </p>
                <p v-else>评测结果：待教师发布</p>
                <p>批阅状态：{{ formatReviewStatus(latestSubmission.reviewStatus) }}</p>
                <p v-if="showFinalScore">得分 {{ latestSubmission.finalScore }}</p>
                <p>{{ formatDateTime(latestSubmission.submittedAt) }}</p>
              </div>
            </section>

            <section
              v-if="latestEvaluationResult"
              class="homework-student__content-card homework-student__submission"
              aria-label="评测结果"
            >
              <div class="homework-student__section-heading">
                <div>
                  <p class="homework-student__eyebrow">可见结果</p>
                  <h2>评测结果</h2>
                </div>
                <span>{{ formatEvaluationStatus(latestEvaluationResult.evaluationStatus) }}</span>
              </div>
              <div class="homework-student__evaluation-score">
                <strong>{{ latestEvaluationResult.score }}</strong>
                <span>分</span>
              </div>
              <p>通过用例 {{ latestEvaluationResult.passedCases }} / {{ latestEvaluationResult.totalCases }}</p>
              <p v-if="latestEvaluationResult.feedback">{{ latestEvaluationResult.feedback }}</p>
              <p v-if="latestEvaluationResult.errorMessage" class="homework-student__error">
                {{ latestEvaluationResult.errorMessage }}
              </p>
            </section>
          </div>

          <aside class="homework-student__submission-pane" aria-label="提交工作区">
            <div class="homework-student__submission-heading">
              <div>
                <p class="homework-student__eyebrow">当前作答</p>
                <h2>提交作业</h2>
              </div>
              <a
                :href="historyHref"
                class="homework-student__history-link"
                data-testid="homework-history-link"
              >
                查看提交历史
              </a>
            </div>

            <p v-if="submissionLoading" class="homework-student__inline-state" role="status" aria-live="polite">
              正在同步最近提交…
            </p>
            <p v-if="submissionLoadError" class="homework-student__warning" role="alert">
              {{ submissionLoadError }}
            </p>
            <p v-if="submissionBlockedReason" class="homework-student__warning" data-testid="homework-submit-blocked">
              {{ submissionBlockedReason }}
            </p>
            <p v-else-if="isPastDeadline" class="homework-student__warning homework-student__warning--late">
              已过截止时间，本次将按逾期提交。
            </p>
            <p v-if="evaluationErrorMessage" class="homework-student__warning" role="alert">
              {{ evaluationErrorMessage }}
            </p>

            <form class="homework-student__submission-form" @submit.prevent="submit">
              <label v-if="homework.type === 'OBJECTIVE'">
                <span>客观题答案</span>
                <textarea
                  v-model="answerJson"
                  name="answerJson"
                  rows="6"
                  :disabled="submitting || !canSubmit"
                  placeholder="按题号填写答案"
                />
              </label>

              <label v-if="homework.type === 'TEXT'">
                <span>文本答案</span>
                <textarea
                  v-model="answerText"
                  name="answerText"
                  rows="8"
                  :disabled="submitting || !canSubmit"
                  placeholder="在这里整理并填写你的答案"
                />
              </label>

              <label v-if="homework.type === 'FILE' || homework.type === 'TEXT'">
                <span>附件编号</span>
                <input
                  v-model="fileIdsInput"
                  name="fileIds"
                  type="text"
                  :disabled="submitting || !canSubmit"
                  placeholder="多个编号请用逗号分隔"
                />
              </label>

              <template v-if="homework.type === 'CODE'">
                <label>
                  <span>语言</span>
                  <select
                    v-if="allowedCodeLanguages.length > 0"
                    v-model="language"
                    name="language"
                    :disabled="submitting || !canSubmit"
                  >
                    <option v-for="item in allowedCodeLanguages" :key="item" :value="item">{{ item }}</option>
                  </select>
                  <input
                    v-else
                    v-model="language"
                    name="language"
                    type="text"
                    :disabled="submitting || !canSubmit"
                    placeholder="请填写编程语言"
                  />
                </label>
                <label>
                  <span>代码</span>
                  <textarea
                    v-model="codeText"
                    name="codeText"
                    rows="16"
                    :disabled="submitting || !canSubmit"
                    spellcheck="false"
                    placeholder="在这里编写完整代码"
                  />
                </label>
              </template>

              <p v-if="feedbackMessage" class="homework-student__feedback" role="status" aria-live="polite">
                {{ feedbackMessage }}
              </p>
              <p v-if="submitErrorMessage" class="homework-student__error" role="alert">
                {{ submitErrorMessage }}
              </p>

              <div class="homework-student__actions">
                <button
                  type="submit"
                  class="homework-student__primary-action"
                  data-testid="homework-primary-submit"
                  :disabled="submitting || !canSubmit"
                >
                  {{ submitActionLabel }}
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
          </aside>
        </div>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  getHomeworkDetail,
  getHomeworkSubmissionEvaluation,
  listMyHomeworkSubmissions,
  submitHomework
} from '../../api/hwk/homeworks';
import { saveLearningProgress } from '../../api/lrn/learningProgress';
import { reportLearningRecord } from '../../api/lrn/learningRecords';
import type { HomeworkDetail, HomeworkEvaluationResult, HomeworkSubmissionSummary } from '../../types/hwk';
import {
  formatEvaluationStatus,
  formatHomeworkStatus,
  formatHomeworkType,
  formatReviewStatus,
  formatSubmitStatus
} from './hwkDisplay';

const props = defineProps<{
  courseId: number;
  homeworkId: number;
}>();

const homework = ref<HomeworkDetail | null>(null);
const latestSubmission = ref<HomeworkSubmissionSummary | null>(null);
const latestEvaluationResult = ref<HomeworkEvaluationResult | null>(null);
const loading = ref(true);
const submissionLoading = ref(false);
const submitting = ref(false);
const saving = ref(false);
const errorMessage = ref('');
const submissionLoadError = ref('');
const evaluationErrorMessage = ref('');
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
const isPastDeadline = computed(() => {
  if (!homework.value) {
    return false;
  }
  const deadline = new Date(homework.value.deadline).getTime();
  return Number.isFinite(deadline) && Date.now() > deadline;
});
const canViewEvaluation = computed(() => Boolean(
  homework.value
  && (homework.value.showEvaluationBeforePublish || homework.value.status === 'SCORE_PUBLISHED')
));
const showFinalScore = computed(() => Boolean(
  homework.value?.status === 'SCORE_PUBLISHED'
  && latestSubmission.value?.finalScore !== null
  && latestSubmission.value?.finalScore !== undefined
));
const submissionBlockedReason = computed(() => {
  if (!homework.value) {
    return '作业详情尚未加载';
  }
  if (homework.value.status !== 'PUBLISHED') {
    return `作业当前为${formatHomeworkStatus(homework.value.status)}，暂不可提交`;
  }
  if (isPastDeadline.value && !homework.value.allowLateSubmit) {
    return '已超过截止时间，当前不允许提交';
  }
  if (latestSubmission.value && !homework.value.allowResubmit) {
    return '本次作业不允许重复提交，你可以继续查看提交历史';
  }
  return '';
});
const canSubmit = computed(() => !submissionBlockedReason.value);
const homeworkStatusSummary = computed(() => {
  if (homework.value?.status === 'PUBLISHED' && isPastDeadline.value) {
    return '已截止';
  }
  return homework.value ? formatHomeworkStatus(homework.value.status) : '';
});
const submitAvailabilitySummary = computed(() => {
  if (submissionBlockedReason.value) {
    return '当前不可提交';
  }
  if (isPastDeadline.value) {
    return '可逾期提交';
  }
  return '可正常提交';
});
const currentSubmissionSummary = computed(() => {
  if (submissionLoading.value) {
    return '正在同步提交状态';
  }
  if (!latestSubmission.value) {
    return '尚未提交';
  }
  const parts = [
    `版本 ${latestSubmission.value.version}`,
    formatSubmitStatus(latestSubmission.value.submitStatus)
  ];
  parts.push(canViewEvaluation.value
    ? formatEvaluationStatus(latestSubmission.value.evaluationStatus)
    : '评测结果待发布');
  return parts.join(' · ');
});
const submitActionLabel = computed(() => {
  if (submitting.value) {
    return '提交中';
  }
  if (isPastDeadline.value && homework.value?.allowLateSubmit) {
    return '逾期提交';
  }
  return latestSubmission.value ? '再次提交' : '提交作业';
});
const historyHref = computed(() => (
  `/courses/${props.courseId}/homeworks/${props.homeworkId}/submissions`
));

onMounted(loadHomework);

async function loadHomework() {
  loading.value = true;
  errorMessage.value = '';
  try {
    homework.value = await getHomeworkDetail(props.homeworkId);
    openedAt.value = new Date();
    syncDefaultCodeLanguage();
    restoreResume();
    await loadLatestSubmission();
    await recordProgress(20, `homeworkId=${props.homeworkId}`);
    await recordBehavior('ACCESS', 0);
  } catch (error) {
    errorMessage.value = localizedError(error, '请稍后重试，或返回作业列表。');
  } finally {
    loading.value = false;
  }
}

async function loadLatestSubmission() {
  submissionLoading.value = true;
  submissionLoadError.value = '';
  latestEvaluationResult.value = null;
  evaluationErrorMessage.value = '';
  try {
    const submissions = await listMyHomeworkSubmissions(props.homeworkId);
    latestSubmission.value = selectCurrentSubmission(submissions);
    if (latestSubmission.value && latestSubmission.value.evaluationStatus !== 'NONE') {
      await refreshLatestEvaluationResult(latestSubmission.value.submissionId);
    }
  } catch (error) {
    submissionLoadError.value = localizedError(error, '最近提交加载失败，仍可继续完成作业。');
  } finally {
    submissionLoading.value = false;
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
    feedbackMessage.value = `提交 ${latestSubmission.value.submissionId} ${formatSubmitStatus(latestSubmission.value.submitStatus)}`;
    await refreshLatestEvaluationResult(latestSubmission.value.submissionId);
    resetForm();
  } catch (error) {
    submitErrorMessage.value = localizedError(error, '作业提交失败，请检查内容后重试。');
  } finally {
    submitting.value = false;
  }
}

async function refreshLatestEvaluationResult(submissionId: number) {
  latestEvaluationResult.value = null;
  evaluationErrorMessage.value = '';
  if (!canViewEvaluation.value) {
    return;
  }
  try {
    const result = await getHomeworkSubmissionEvaluation(submissionId);
    if (!result) {
      return;
    }
    latestEvaluationResult.value = result;
    if (latestSubmission.value) {
      latestSubmission.value = {
        ...latestSubmission.value,
        evaluationStatus: result.evaluationStatus,
        autoScore: result.score
      };
    }
  } catch (error) {
    evaluationErrorMessage.value = localizedError(error, '评测结果暂时无法加载，请稍后在提交历史中查看。');
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

function selectCurrentSubmission(submissions: HomeworkSubmissionSummary[] | null | undefined) {
  if (!Array.isArray(submissions) || submissions.length === 0) {
    return null;
  }
  const finalSubmissions = submissions.filter((submission) => submission.final);
  const candidates = finalSubmissions.length > 0 ? finalSubmissions : submissions;
  return [...candidates].sort((left, right) => {
    if (left.version !== right.version) {
      return right.version - left.version;
    }
    return new Date(right.submittedAt).getTime() - new Date(left.submittedAt).getTime();
  })[0] ?? null;
}

function elapsedSeconds() {
  if (!openedAt.value) {
    return 0;
  }
  return Math.max(0, Math.round((Date.now() - openedAt.value.getTime()) / 1000));
}

function validateForm() {
  if (submissionBlockedReason.value) {
    return submissionBlockedReason.value;
  }
  if (!homework.value) {
    return '作业详情尚未加载';
  }
  if (homework.value.type === 'OBJECTIVE' && !answerJson.value.trim()) {
    return '请填写客观题答案';
  }
  if (homework.value.type === 'TEXT' && !answerText.value.trim() && parseFileIds().length === 0) {
    return '请填写文本答案或附件编号';
  }
  if (homework.value.type === 'FILE' && parseFileIds().length === 0) {
    return '请填写附件编号';
  }
  if (homework.value.type === 'CODE' && (!codeText.value.trim() || !language.value.trim())) {
    return '请填写代码和语言';
  }
  if (
    homework.value.type === 'CODE'
    && allowedCodeLanguages.value.length > 0
    && !allowedCodeLanguages.value.includes(language.value.trim())
  ) {
    return '当前作业不允许使用该语言';
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

function formatQuestionOptions(value: string | null | undefined) {
  if (!value) {
    return [];
  }
  try {
    const parsed: unknown = JSON.parse(value);
    if (Array.isArray(parsed)) {
      return parsed.map((item) => String(item).trim()).filter(Boolean);
    }
    if (parsed && typeof parsed === 'object') {
      return Object.entries(parsed)
        .map(([key, item]) => `${key}. ${String(item).trim()}`)
        .filter((item) => item.trim());
    }
  } catch {
    return [value];
  }
  return [];
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

function localizedError(error: unknown, fallback: string) {
  const message = error instanceof Error ? error.message.trim() : '';
  return /[\u3400-\u9fff]/u.test(message) ? message : fallback;
}

function formatDateTime(value: string) {
  return value.replace('T', ' ').slice(0, 16);
}
</script>

<style scoped>
.homework-student {
  min-height: 100vh;
  padding: 24px;
}

.homework-student__panel {
  display: grid;
  gap: 20px;
  margin: 0 auto;
  max-width: 1180px;
  padding: 24px;
}

.homework-student__state {
  align-items: center;
  display: flex;
  gap: 18px;
  min-height: 220px;
  padding: 28px;
}

.homework-student__state h1,
.homework-student__state p,
.homework-student__header h1,
.homework-student__header p,
.homework-student__section-heading h2,
.homework-student__section-heading p,
.homework-student__submission-heading h2,
.homework-student__submission-heading p {
  margin: 0;
}

.homework-student__state h1 {
  font-size: clamp(1.5rem, 3vw, 2rem);
  margin: 5px 0 8px;
}

.homework-student__state-mark {
  align-items: center;
  background: color-mix(in srgb, var(--oj-brand, #2563eb) 14%, transparent);
  border: 1px solid color-mix(in srgb, var(--oj-brand, #2563eb) 34%, transparent);
  border-radius: 16px;
  color: var(--oj-brand, #2563eb);
  display: inline-flex;
  flex: 0 0 52px;
  font-size: 22px;
  font-weight: 800;
  height: 52px;
  justify-content: center;
}

.homework-student__state-mark:empty::before {
  animation: homework-pulse 1.1s ease-in-out infinite;
  background: currentColor;
  border-radius: 999px;
  content: '';
  height: 12px;
  width: 12px;
}

.homework-student__state--error .homework-student__state-mark {
  background: #fff1f0;
  border-color: #f5b7b1;
  color: #b42318;
}

.homework-student__header {
  align-items: flex-start;
  display: flex;
  gap: 18px;
  justify-content: space-between;
  padding: 6px 4px 0;
}

.homework-student__heading-copy {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.homework-student__header h1 {
  color: var(--oj-ink, #172033);
  font-size: clamp(1.9rem, 4vw, 3rem);
  letter-spacing: -0.035em;
  line-height: 1.08;
  overflow-wrap: anywhere;
}

.homework-student__lede,
.homework-student__description {
  color: var(--oj-muted, #667085);
  line-height: 1.7;
  max-width: 760px;
}

.homework-student__eyebrow {
  color: var(--oj-brand-strong, #1d4ed8);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.homework-student__type-chip,
.homework-student__section-heading > span {
  background: color-mix(in srgb, var(--oj-brand, #2563eb) 10%, var(--oj-surface, #ffffff));
  border: 1px solid color-mix(in srgb, var(--oj-brand, #2563eb) 24%, transparent);
  border-radius: 999px;
  color: var(--oj-brand-strong, #1d4ed8);
  flex: 0 0 auto;
  font-size: 0.8rem;
  font-weight: 800;
  padding: 7px 11px;
}

.homework-student__summary {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0;
}

.homework-student__summary > div {
  background: color-mix(in srgb, var(--oj-surface, #ffffff) 88%, transparent);
  border: 1px solid var(--oj-line, #d8deea);
  border-radius: 14px;
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 16px;
}

.homework-student__summary dt,
.homework-student__details dt {
  color: var(--oj-muted, #667085);
  font-size: 0.75rem;
  font-weight: 700;
}

.homework-student__summary dd,
.homework-student__details dd {
  color: var(--oj-ink, #172033);
  font-weight: 800;
  line-height: 1.35;
  margin: 0;
  overflow-wrap: anywhere;
}

.homework-student__summary small {
  color: var(--oj-muted, #667085);
  line-height: 1.35;
}

.homework-student__workspace {
  align-items: start;
  display: grid;
  gap: 20px;
  grid-template-columns: minmax(0, 1fr) minmax(310px, 380px);
}

.homework-student__content-pane {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.homework-student__content-card,
.homework-student__submission-pane {
  background: color-mix(in srgb, var(--oj-surface, #ffffff) 94%, transparent);
  border: 1px solid var(--oj-line, #d8deea);
  border-radius: 16px;
  box-shadow: 0 14px 36px rgb(32 55 92 / 8%);
  min-width: 0;
  padding: 20px;
}

.homework-student__submission-pane {
  display: grid;
  gap: 14px;
  max-height: calc(100vh - 32px);
  overflow: auto;
  position: sticky;
  top: 16px;
}

.homework-student__section-heading,
.homework-student__submission-heading {
  align-items: flex-start;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.homework-student__section-heading h2,
.homework-student__submission-heading h2 {
  color: var(--oj-ink, #172033);
  font-size: 1.1rem;
  margin-top: 4px;
}

.homework-student__description {
  margin: 18px 0;
  white-space: pre-wrap;
}

.homework-student__details,
.homework-student__receipt-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
}

.homework-student__details > div {
  background: color-mix(in srgb, var(--oj-brand, #2563eb) 4%, transparent);
  border-radius: 10px;
  display: grid;
  gap: 3px;
  padding: 10px 12px;
}

.homework-student__block {
  display: grid;
  gap: 12px;
}

.homework-student__block article {
  background: color-mix(in srgb, var(--oj-surface, #ffffff) 75%, transparent);
  border: 1px solid var(--oj-line, #d8deea);
  border-radius: 12px;
  display: grid;
  gap: 7px;
  padding: 14px;
}

.homework-student__block article p {
  color: var(--oj-muted, #667085);
  margin: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.homework-student__options {
  display: grid;
  gap: 6px;
  list-style-position: inside;
  margin: 3px 0 0;
  padding: 0;
}

.homework-student__submission {
  display: grid;
  gap: 14px;
}

.homework-student__receipt-grid p {
  background: color-mix(in srgb, var(--oj-brand, #2563eb) 4%, transparent);
  border-radius: 10px;
  margin: 0;
  overflow-wrap: anywhere;
  padding: 10px 12px;
}

.homework-student__evaluation-score {
  align-items: baseline;
  color: var(--oj-brand-strong, #1d4ed8);
  display: flex;
  gap: 5px;
}

.homework-student__evaluation-score strong {
  font-size: 2.4rem;
  line-height: 1;
}

.homework-student__history-link {
  align-self: center;
  flex: 0 0 auto;
  font-size: 0.82rem;
  font-weight: 800;
  text-decoration: none;
}

.homework-student__submission-form {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.homework-student__submission-form label {
  display: grid;
  gap: 7px;
  min-width: 0;
}

.homework-student__submission-form label > span {
  color: var(--oj-ink, #172033);
  font-size: 0.86rem;
  font-weight: 800;
}

.homework-student__submission-form input,
.homework-student__submission-form textarea,
.homework-student__submission-form select {
  background: color-mix(in srgb, var(--oj-surface, #ffffff) 92%, transparent);
  border: 1px solid var(--oj-line-strong, #b8c2d2);
  border-radius: 10px;
  box-sizing: border-box;
  color: var(--oj-ink, #172033);
  font: inherit;
  line-height: 1.55;
  min-height: 42px;
  padding: 9px 11px;
  resize: vertical;
  width: 100%;
}

.homework-student__submission-form input:focus,
.homework-student__submission-form textarea:focus,
.homework-student__submission-form select:focus {
  border-color: var(--oj-brand, #2563eb);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--oj-brand, #2563eb) 16%, transparent);
  outline: none;
}

.homework-student__submission-form textarea[name='codeText'] {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.84rem;
  tab-size: 2;
}

.homework-student__inline-state,
.homework-student__warning,
.homework-student__feedback,
.homework-student__error {
  border-radius: 10px;
  line-height: 1.45;
  margin: 0;
  padding: 10px 12px;
}

.homework-student__inline-state {
  background: #eef5ff;
  color: #1d4ed8;
}

.homework-student__warning {
  background: #fff4e5;
  color: #8a4b08;
}

.homework-student__warning--late {
  background: #fff8db;
}

.homework-student__feedback {
  background: #ecfdf3;
  color: #116329;
}

.homework-student__error {
  background: #fff1f0;
  color: #b42318;
}

.homework-student__actions {
  background: color-mix(in srgb, var(--oj-surface, #ffffff) 94%, transparent);
  border-top: 1px solid var(--oj-line, #d8deea);
  bottom: 0;
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 2px -2px -2px;
  padding: 12px 2px 2px;
  position: sticky;
  z-index: 2;
}

.homework-student__actions button {
  background: color-mix(in srgb, var(--oj-surface, #ffffff) 92%, transparent);
  border: 1px solid var(--oj-line-strong, #b8c2d2);
  border-radius: 10px;
  color: var(--oj-ink, #172033);
  cursor: pointer;
  font: inherit;
  font-weight: 800;
  min-height: 42px;
  padding: 9px 12px;
}

.homework-student__actions .homework-student__primary-action {
  background: var(--oj-brand, #2563eb);
  border-color: var(--oj-brand, #2563eb);
  color: #ffffff;
  grid-column: 1 / -1;
}

.homework-student__actions button:disabled,
.homework-student__submission-form :disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

@keyframes homework-pulse {
  0%,
  100% {
    opacity: 0.35;
    transform: scale(0.82);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}

@media (max-width: 820px) {
  .homework-student {
    padding-bottom: 112px;
  }

  .homework-student__panel {
    backdrop-filter: none !important;
    -webkit-backdrop-filter: none !important;
  }

  .homework-student__workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .homework-student__submission-pane {
    max-height: none;
    order: -1;
    overflow: visible;
    position: static;
  }

  .homework-student__actions {
    bottom: max(8px, env(safe-area-inset-bottom));
    left: 12px;
    right: 12px;
    border: 1px solid var(--oj-line, #d8deea);
    border-radius: 12px;
    box-shadow: 0 12px 30px rgb(32 55 92 / 18%);
    margin: 2px 0 0;
    padding: 8px;
    position: fixed;
  }
}

@media (max-width: 520px) {
  .homework-student__panel {
    gap: 14px;
    padding: 14px;
  }

  .homework-student__header {
    display: grid;
    gap: 12px;
  }

  .homework-student__header h1 {
    font-size: clamp(1.75rem, 9vw, 2.35rem);
  }

  .homework-student__type-chip {
    justify-self: start;
  }

  .homework-student__summary,
  .homework-student__details,
  .homework-student__receipt-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .homework-student__summary > div,
  .homework-student__content-card,
  .homework-student__submission-pane {
    padding: 14px;
  }

  .homework-student__section-heading,
  .homework-student__submission-heading {
    align-items: flex-start;
    display: grid;
  }

  .homework-student__history-link {
    justify-self: start;
  }

  .homework-student__state {
    align-items: flex-start;
    display: grid;
    min-height: 180px;
    padding: 18px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .homework-student__state-mark:empty::before {
    animation: none;
  }
}
</style>
