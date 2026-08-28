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
          <button
            type="button"
            class="homework-student__primary-action"
            data-testid="homework-load-retry"
            @click="loadHomework"
          >
            重新加载
          </button>
        </div>
      </section>

      <template v-else-if="homework">
        <PageHeader
          :title="homework.title"
          :subtitle="homework.description"
          :eyebrow="mode === 'detail' ? '学生作业详情 · HWK' : '学生作业提交 · HWK'"
        >
          <template #meta>
            <span>{{ formatDateTime(homework.deadline) }} 截止</span>
            <span v-if="resumeMessage" class="homework-student__feedback">{{ resumeMessage }}</span>
          </template>
          <template #actions>
            <StatusBadge :label="formatHomeworkType(homework.type)" tone="brand" />
            <StatusBadge :label="homeworkStatusSummary" :tone="homeworkStatusTone" />
          </template>
        </PageHeader>

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

          <aside
            v-if="mode === 'submit'"
            class="homework-student__submission-pane"
            aria-label="提交工作区"
          >
            <div class="homework-student__submission-heading">
              <div>
                <p class="homework-student__eyebrow">当前作答</p>
                <h2>提交作业</h2>
              </div>
              <RouterLink
                :to="historyHref"
                :href="historyHref"
                class="homework-student__history-link"
                data-testid="homework-history-link"
              >
                查看提交历史
              </RouterLink>
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
            <p
              v-if="draftStatusMessage"
              class="homework-student__draft-status"
              data-testid="homework-draft-status"
              role="status"
              aria-live="polite"
            >
              {{ draftStatusMessage }}
            </p>

            <form class="homework-student__submission-form" @submit.prevent="submit">
              <div v-if="homework.type === 'OBJECTIVE'" class="homework-student__objective-editor">
                <p class="homework-student__field-label">客观题答案</p>
                <fieldset
                  v-for="question in homework.questions"
                  :key="question.id"
                  class="homework-student__objective-question"
                >
                  <legend>{{ question.sortOrder }}. {{ question.stem }}</legend>
                  <label
                    v-for="option in objectiveOptions(question)"
                    :key="option.value"
                    class="homework-student__objective-option"
                  >
                    <input
                      :name="`objective-${question.id}`"
                      :type="isMultipleChoice(question) ? 'checkbox' : 'radio'"
                      :value="option.value"
                      :checked="objectiveAnswerValues(question).includes(option.value)"
                      :disabled="submitting || !canSubmit"
                      @change="updateObjectiveAnswer(question, option.value, $event)"
                    />
                    <span>{{ option.label }}</span>
                  </label>
                </fieldset>
              </div>

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

              <div v-if="homework.type === 'FILE'" class="homework-student__file-field">
                <label for="homework-file-input">作业附件</label>
                <input
                  id="homework-file-input"
                  ref="fileInput"
                  name="homeworkFile"
                  type="file"
                  :accept="homeworkFileAccept"
                  :disabled="submitting || fileUploading || fileRemoving || Boolean(uploadedAttachment)"
                  @change="selectHomeworkFile"
                />
                <small>单个附件，最大 10 MiB；支持 pdf/zip/docx/xlsx/pptx/txt/md/csv/png/jpg/jpeg</small>
                <small v-if="selectedFile && !uploadedAttachment">已选择：{{ selectedFile.name }}</small>
                <div
                  v-if="uploadedAttachment"
                  class="homework-student__file-summary"
                  data-testid="homework-file-name"
                >
                  <strong>{{ uploadedAttachment.originalFilename }}</strong>
                  <span>{{ uploadedAttachment.contentType }} · {{ formatFileSize(uploadedAttachment.fileSize) }}</span>
                </div>
                <div class="homework-student__file-actions">
                  <button
                    v-if="!uploadedAttachment"
                    type="button"
                    data-action="upload-homework-file"
                    :disabled="!selectedFile || fileUploading || fileRemoving || Boolean(submissionBlockedReason)"
                    @click="uploadSelectedHomeworkFile"
                  >{{ fileUploading ? '正在上传…' : '上传附件' }}</button>
                  <button
                    v-else
                    type="button"
                    data-action="remove-homework-file"
                    :disabled="fileRemoving || submitting"
                    @click="removeUploadedHomeworkFile"
                  >{{ fileRemoving ? '正在移除…' : '移除附件' }}</button>
                </div>
                <p
                  v-if="fileUploadError"
                  class="homework-student__warning"
                  data-testid="homework-file-upload-error"
                  role="alert"
                >{{ fileUploadError }}</p>
                <p
                  v-if="fileRestoreStatus"
                  class="homework-student__draft-status"
                  data-testid="homework-file-restore-status"
                  role="status"
                >{{ fileRestoreStatus }}</p>
              </div>

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
              <RouterLink
                v-if="latestSubmission"
                :to="resultHref"
                :href="resultHref"
                class="homework-student__history-link"
                data-testid="homework-result-link"
              >
                查看最新提交结果
              </RouterLink>
              <p
                v-if="submitErrorMessage"
                class="homework-student__error"
                data-testid="homework-submit-error"
                role="alert"
              >
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

          <aside v-else class="homework-student__submission-pane" aria-label="作业下一步">
            <div class="homework-student__submission-heading">
              <div>
                <p class="homework-student__eyebrow">下一步</p>
                <h2>完成这份作业</h2>
              </div>
            </div>
            <p v-if="submissionBlockedReason" class="homework-student__warning">
              {{ submissionBlockedReason }}
            </p>
            <nav class="homework-student__flow-actions" aria-label="作业任务流程">
              <RouterLink
                :to="submitHref"
                :href="submitHref"
                class="homework-student__primary-action"
                data-testid="homework-submit-link"
              >
                {{ latestSubmission ? '再次提交' : '去提交' }}
              </RouterLink>
              <RouterLink
                :to="historyHref"
                :href="historyHref"
                class="homework-student__history-link"
                data-testid="homework-history-link"
              >
                查看提交历史
              </RouterLink>
              <RouterLink
                :to="resultHref"
                :href="resultHref"
                class="homework-student__history-link"
                data-testid="homework-result-link"
              >
                查看最新结果
              </RouterLink>
            </nav>
          </aside>
        </div>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { matchedRouteKey, onBeforeRouteLeave, RouterLink } from 'vue-router';
import {
  deleteHomeworkAttachment,
  getHomeworkAttachment,
  getHomeworkDetail,
  getHomeworkSubmissionEvaluation,
  listMyHomeworkSubmissions,
  submitHomework,
  uploadHomeworkAttachment
} from '../../api/hwk/homeworks';
import { saveLearningProgress } from '../../api/lrn/learningProgress';
import { reportLearningRecord } from '../../api/lrn/learningRecords';
import { currentUser } from '../../app/runtimeContext';
import PageHeader from '../../components/foundation/PageHeader.vue';
import StatusBadge from '../../components/foundation/StatusBadge.vue';
import type {
  HomeworkAttachmentUpload,
  HomeworkDetail,
  HomeworkEvaluationResult,
  HomeworkQuestion,
  HomeworkSubmissionSummary
} from '../../types/hwk';
import {
  formatEvaluationStatus,
  formatHomeworkStatus,
  formatHomeworkType,
  formatReviewStatus,
  formatSubmitStatus
} from './hwkDisplay';

const props = withDefaults(defineProps<{
  courseId: number;
  homeworkId: number;
  mode?: 'detail' | 'submit';
}>(), {
  mode: 'submit'
});

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
const objectiveAnswers = ref<Record<string, string[]>>({});
const codeText = ref('');
const language = ref('');
const selectedFile = ref<File | null>(null);
const uploadedAttachment = ref<HomeworkAttachmentUpload | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);
const fileUploading = ref(false);
const fileRemoving = ref(false);
const fileUploadError = ref('');
const fileRestoreStatus = ref('');
const draftStatusMessage = ref('');
const openedAt = ref<Date | null>(null);
const homeworkFileAccept = '.pdf,.zip,.docx,.xlsx,.pptx,.txt,.md,.csv,.png,.jpg,.jpeg';
const homeworkFileExtensions = new Set(homeworkFileAccept.split(',').map((extension) => extension.slice(1)));
const homeworkFileMaxSize = 10 * 1024 * 1024;
let draftTimer: ReturnType<typeof setTimeout> | undefined;
let draftSaveToken = 0;
let attachmentExpiryTimer: ReturnType<typeof setTimeout> | undefined;
let beforeUnloadRegistered = false;
let draftWatchSuspended = false;
let loadGeneration = 0;
let submissionEditorGeneration = 0;
let attachmentRequestGeneration = 0;

interface HomeworkDraft {
  version: 1;
  savedAt: number;
  homeworkType: HomeworkDetail['type'];
  answerText: string;
  objectiveAnswers: Record<string, string[]>;
  codeText: string;
  language: string;
}

interface HomeworkAttachmentDraft {
  version: 1;
  savedAt: number;
  attachment: HomeworkAttachmentUpload;
}

interface ObjectiveOption {
  value: string;
  label: string;
}

const allowedCodeLanguages = computed(() => parseLanguageLimit(homework.value?.languageLimitJson));
const draftKey = computed(() => (
  draftStorageKey(props.courseId, props.homeworkId)
));
const isPastDeadline = computed(() => {
  if (!homework.value) {
    return false;
  }
  const deadline = new Date(homework.value.deadline).getTime();
  return Number.isFinite(deadline) && Date.now() > deadline;
});
const canViewEvaluation = computed(() => Boolean(
  homework.value
  && (
    homework.value.showEvaluationBeforePublish
    || homework.value.status === 'SCORE_PUBLISHED'
    || homework.value.status === 'ARCHIVED'
  )
));
const showFinalScore = computed(() => Boolean(
  (homework.value?.status === 'SCORE_PUBLISHED' || homework.value?.status === 'ARCHIVED')
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
const canSubmit = computed(() => (
  !submissionBlockedReason.value
  && !fileUploading.value
  && !fileRemoving.value
  && (homework.value?.type !== 'FILE' || Boolean(uploadedAttachment.value))
));
const homeworkStatusSummary = computed(() => {
  if (homework.value?.status === 'PUBLISHED' && isPastDeadline.value) {
    return '已截止';
  }
  return homework.value ? formatHomeworkStatus(homework.value.status) : '';
});
const homeworkStatusTone = computed<'neutral' | 'brand' | 'success' | 'warning' | 'danger'>(() => {
  if (homework.value?.status === 'SCORE_PUBLISHED') {
    return 'success';
  }
  if (homework.value?.status === 'CLOSED' || homework.value?.status === 'ARCHIVED' || isPastDeadline.value) {
    return 'warning';
  }
  return homework.value?.status === 'PUBLISHED' ? 'brand' : 'neutral';
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
const submitHref = computed(() => (
  `/courses/${props.courseId}/homeworks/${props.homeworkId}/submit`
));
const resultHref = computed(() => (
  `/courses/${props.courseId}/homeworks/${props.homeworkId}/result`
));

watch(
  [answerText, objectiveAnswers, codeText, language],
  () => {
    if (!draftWatchSuspended) {
      scheduleDraftSave();
    }
  },
  { deep: true }
);

watch(() => props.mode, (mode, previousMode) => {
  submissionEditorGeneration += 1;
  attachmentRequestGeneration += 1;
  fileUploading.value = false;
  fileRemoving.value = false;
  submitting.value = false;
  if (previousMode === 'submit' && mode !== 'submit') {
    saveDraftNow();
    clearAttachmentExpiryTimer();
    unregisterBeforeUnload();
  }
  if (mode === 'submit' && previousMode !== 'submit') {
    registerBeforeUnload();
    restoreDraft();
    void restoreHomeworkAttachment(loadGeneration, props.homeworkId);
  }
});

watch(
  [() => props.courseId, () => props.homeworkId],
  ([,], [previousCourseId, previousHomeworkId]) => {
    if (props.mode === 'submit' && homework.value) {
      saveDraftNow(draftStorageKey(previousCourseId, previousHomeworkId));
    }
    cancelScheduledDraftSave();
    resetEditorState();
    submissionEditorGeneration += 1;
    attachmentRequestGeneration += 1;
    submitting.value = false;
    void loadHomework();
  }
);

onMounted(() => {
  if (props.mode === 'submit') {
    registerBeforeUnload();
  }
  void loadHomework();
});

onBeforeUnmount(() => {
  if (props.mode === 'submit') {
    saveDraftNow();
  }
  unregisterBeforeUnload();
  cancelScheduledDraftSave();
  clearAttachmentExpiryTimer();
  loadGeneration += 1;
  submissionEditorGeneration += 1;
  attachmentRequestGeneration += 1;
});

if (inject(matchedRouteKey, null)) {
  onBeforeRouteLeave(() => {
    if (props.mode !== 'submit' || !hasUnsavedAnswer()) {
      return true;
    }
    if (homework.value?.type === 'FILE' && selectedFile.value && !uploadedAttachment.value) {
      return window.confirm('所选本地文件尚未上传，离开后无法恢复。确认离开提交页吗？');
    }
    if (homework.value?.type === 'FILE' && uploadedAttachment.value) {
      return window.confirm('附件已暂存但尚未提交，可返回提交页恢复。确认离开吗？');
    }
    saveDraftNow();
    return window.confirm('当前作答已自动保存。确认离开提交页吗？');
  });
}

async function loadHomework() {
  const generation = ++loadGeneration;
  const requestedHomeworkId = props.homeworkId;
  loading.value = true;
  errorMessage.value = '';
  submissionLoadError.value = '';
  latestSubmission.value = null;
  latestEvaluationResult.value = null;
  try {
    const loadedHomework = await getHomeworkDetail(requestedHomeworkId);
    if (!isCurrentLoad(generation)) {
      return;
    }
    homework.value = loadedHomework;
    openedAt.value = new Date();
    syncDefaultCodeLanguage();
    if (props.mode === 'submit') {
      restoreDraft();
      void restoreHomeworkAttachment(generation, requestedHomeworkId);
    }
    restoreResume();
    await loadLatestSubmission(requestedHomeworkId, generation);
    if (!isCurrentLoad(generation)) {
      return;
    }
    await recordProgress(20, `homeworkId=${requestedHomeworkId}`);
    await recordBehavior('ACCESS', 0);
  } catch (error) {
    if (isCurrentLoad(generation)) {
      errorMessage.value = localizedError(error, '请稍后重试，或返回作业列表。');
    }
  } finally {
    if (isCurrentLoad(generation)) {
      loading.value = false;
    }
  }
}

async function loadLatestSubmission(homeworkId: number, generation: number) {
  submissionLoading.value = true;
  submissionLoadError.value = '';
  latestEvaluationResult.value = null;
  evaluationErrorMessage.value = '';
  try {
    const submissions = await listMyHomeworkSubmissions(homeworkId);
    if (!isCurrentLoad(generation)) {
      return;
    }
    latestSubmission.value = selectCurrentSubmission(submissions);
    if (latestSubmission.value && latestSubmission.value.evaluationStatus !== 'NONE') {
      await refreshLatestEvaluationResult(latestSubmission.value.submissionId, generation);
    }
  } catch (error) {
    if (isCurrentLoad(generation)) {
      submissionLoadError.value = localizedError(error, '最近提交加载失败，仍可继续完成作业。');
    }
  } finally {
    if (isCurrentLoad(generation)) {
      submissionLoading.value = false;
    }
  }
}

async function submit() {
  feedbackMessage.value = '';
  if (expireUploadedAttachmentIfNeeded()) {
    submitErrorMessage.value = '附件已过期，请重新选择并上传。';
    return;
  }
  submitErrorMessage.value = validateForm();
  if (submitErrorMessage.value) {
    return;
  }

  const generation = loadGeneration;
  const editorGeneration = ++submissionEditorGeneration;
  const requestedHomeworkId = props.homeworkId;
  const requestedCourseId = props.courseId;
  const requestedUserId = currentUser.value?.id ?? 'anonymous';
  const submittedAttachmentFileId = homework.value?.type === 'FILE'
    ? uploadedAttachment.value?.fileId
    : undefined;
  const submittedAttachmentDraftKey = submittedAttachmentFileId
    ? attachmentStorageKey(requestedHomeworkId, requestedCourseId, requestedUserId)
    : undefined;
  submitting.value = true;
  try {
    const submitted = await submitHomework(requestedHomeworkId, {
      answerText: answerText.value.trim() || undefined,
      answerJson: homework.value?.type === 'OBJECTIVE' ? serializeObjectiveAnswers() : undefined,
      fileIds: homework.value?.type === 'FILE' && uploadedAttachment.value
        ? [uploadedAttachment.value.fileId]
        : undefined,
      codeText: codeText.value.trim() || undefined,
      language: language.value.trim() || undefined
    });
    if (
      submitted.homeworkId === requestedHomeworkId
      && submittedAttachmentDraftKey
      && submittedAttachmentFileId
    ) {
      clearStoredAttachmentDraft(submittedAttachmentDraftKey, submittedAttachmentFileId);
    }
    if (
      submitted.homeworkId !== requestedHomeworkId
      || !isCurrentSubmissionRequest(generation, editorGeneration, requestedHomeworkId)
    ) {
      return;
    }
    latestSubmission.value = submitted;
    await recordProgress(100, `homeworkId=${requestedHomeworkId};submitted=${submitted.submissionId}`);
    if (!isCurrentSubmissionRequest(generation, editorGeneration, requestedHomeworkId, submitted.submissionId)) {
      return;
    }
    await recordBehavior('SUBMIT', elapsedSeconds());
    if (!isCurrentSubmissionRequest(generation, editorGeneration, requestedHomeworkId, submitted.submissionId)) {
      return;
    }
    feedbackMessage.value = `提交 ${submitted.submissionId} ${formatSubmitStatus(submitted.submitStatus)}`;
    if (submitted.evaluationStatus !== 'NONE') {
      await refreshLatestEvaluationResult(submitted.submissionId, generation);
      if (!isCurrentSubmissionRequest(generation, editorGeneration, requestedHomeworkId, submitted.submissionId)) {
        return;
      }
    }
    clearDraft();
    clearAttachmentState(true);
    resetForm();
  } catch (error) {
    if (isCurrentSubmissionRequest(generation, editorGeneration, requestedHomeworkId)) {
      const attachmentError = attachmentSubmissionError(error);
      if (homework.value?.type === 'FILE' && attachmentError) {
        clearAttachmentState(true);
        submitErrorMessage.value = attachmentError;
      } else if (homework.value?.type === 'FILE' && apiErrorCode(error) === 'HWK_5002') {
        submitErrorMessage.value = '附件存储暂时不可用，请稍后重试提交。';
      } else {
        submitErrorMessage.value = localizedError(error, '作业提交失败，请检查内容后重试。');
      }
    }
  } finally {
    if (isCurrentSubmissionRequest(generation, editorGeneration, requestedHomeworkId)) {
      submitting.value = false;
    }
  }
}

async function refreshLatestEvaluationResult(submissionId: number, generation = loadGeneration) {
  if (!isCurrentEvaluationRequest(generation, submissionId)) {
    return;
  }
  latestEvaluationResult.value = null;
  evaluationErrorMessage.value = '';
  if (!canViewEvaluation.value) {
    return;
  }
  try {
    const result = await getHomeworkSubmissionEvaluation(submissionId);
    if (
      !result
      || result.submissionId !== submissionId
      || !isCurrentEvaluationRequest(generation, submissionId)
    ) {
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
    if (isCurrentEvaluationRequest(generation, submissionId)) {
      evaluationErrorMessage.value = localizedError(error, '评测结果暂时无法加载，请稍后在提交历史中查看。');
    }
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
  if (
    homework.value.type === 'OBJECTIVE'
    && homework.value.questions.some((question) => objectiveAnswerValues(question).length === 0)
  ) {
    return '请填写客观题答案：请完成所有题目';
  }
  if (homework.value.type === 'TEXT' && !answerText.value.trim()) {
    return '请填写文本答案';
  }
  if (homework.value.type === 'FILE') {
    return uploadedAttachment.value ? '' : '请先上传附件';
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

function parseQuestionOptions(value: string | null | undefined): ObjectiveOption[] {
  if (!value) {
    return [];
  }
  try {
    const parsed: unknown = JSON.parse(value);
    if (Array.isArray(parsed)) {
      return parsed
        .map((item) => String(item).trim())
        .filter(Boolean)
        .map((item) => ({ value: item, label: item }));
    }
    if (parsed && typeof parsed === 'object') {
      return Object.entries(parsed)
        .map(([key, item]) => ({ value: key, label: `${key}. ${String(item).trim()}` }))
        .filter((item) => item.label.trim());
    }
  } catch {
    return [{ value, label: value }];
  }
  return [];
}

function formatQuestionOptions(value: string | null | undefined) {
  return parseQuestionOptions(value).map((option) => option.label);
}

function objectiveOptions(question: HomeworkQuestion) {
  const configured = parseQuestionOptions(question.optionsJson);
  if (configured.length > 0) {
    return configured;
  }
  const questionType = question.questionType.toUpperCase();
  if (questionType.includes('JUDGE') || questionType.includes('TRUE_FALSE')) {
    return [
      { value: 'true', label: '正确' },
      { value: 'false', label: '错误' }
    ];
  }
  return [];
}

function objectiveAnswerKey(question: HomeworkQuestion) {
  return `q${question.sortOrder}`;
}

function objectiveAnswerValues(question: HomeworkQuestion) {
  return objectiveAnswers.value[objectiveAnswerKey(question)] ?? [];
}

function isMultipleChoice(question: HomeworkQuestion) {
  return question.questionType.toUpperCase().includes('MULTIPLE');
}

function updateObjectiveAnswer(question: HomeworkQuestion, value: string, event: Event) {
  const input = event.target as HTMLInputElement;
  const key = objectiveAnswerKey(question);
  if (!isMultipleChoice(question)) {
    objectiveAnswers.value = {
      ...objectiveAnswers.value,
      [key]: input.checked ? [value] : []
    };
    return;
  }

  const current = objectiveAnswers.value[key] ?? [];
  const next = input.checked
    ? [...new Set([...current, value])]
    : current.filter((item) => item !== value);
  objectiveAnswers.value = { ...objectiveAnswers.value, [key]: next };
}

function serializeObjectiveAnswers() {
  if (!homework.value) {
    return undefined;
  }
  const serialized = Object.fromEntries(
    [...homework.value.questions]
      .sort((left, right) => left.sortOrder - right.sortOrder)
      .map((question) => [objectiveAnswerKey(question), serializedObjectiveAnswerValues(question)])
  );
  return JSON.stringify(serialized);
}

function serializedObjectiveAnswerValues(question: HomeworkQuestion) {
  const selected = objectiveAnswerValues(question);
  if (!isMultipleChoice(question)) {
    return selected;
  }
  const selectedValues = new Set(selected);
  return objectiveOptions(question)
    .map((option) => option.value)
    .filter((value) => selectedValues.has(value));
}

function selectHomeworkFile(event: Event) {
  const input = event.target as HTMLInputElement;
  fileRestoreStatus.value = '';
  const file = input.files?.[0] ?? null;
  const validationError = validateSelectedHomeworkFile(file);
  if (validationError) {
    clearSelectedHomeworkFile();
    fileUploadError.value = validationError;
    return;
  }
  selectedFile.value = file;
  fileUploadError.value = '';
}

function validateSelectedHomeworkFile(file: File | null) {
  if (!file) {
    return '';
  }
  if (file.size <= 0) {
    return '附件为空或文件内容无效，请重新选择有效文件。';
  }
  if (file.size > homeworkFileMaxSize) {
    return '附件大小超过 10 MiB，请重新选择较小的文件。';
  }
  const extension = file.name.includes('.') ? file.name.split('.').pop()?.toLowerCase() : undefined;
  if (!extension || !homeworkFileExtensions.has(extension)) {
    return '不支持该附件类型，请重新选择允许的文件。';
  }
  return '';
}

function clearSelectedHomeworkFile() {
  selectedFile.value = null;
  if (fileInput.value) {
    fileInput.value.value = '';
  }
}

async function uploadSelectedHomeworkFile() {
  const file = selectedFile.value;
  if (!file || !homework.value || homework.value.type !== 'FILE' || submissionBlockedReason.value || fileUploading.value) {
    return;
  }
  const request = ++attachmentRequestGeneration;
  const homeworkId = props.homeworkId;
  const generation = loadGeneration;
  fileUploading.value = true;
  fileUploadError.value = '';
  fileRestoreStatus.value = '';
  try {
    const uploaded = await uploadHomeworkAttachment(homeworkId, file);
    if (!isCurrentAttachmentRequest(request, generation, homeworkId)) {
      return;
    }
    uploadedAttachment.value = uploaded;
    persistHomeworkAttachment(uploaded);
    scheduleAttachmentExpiry(uploaded);
    fileRestoreStatus.value = '附件上传成功，可提交作业。';
  } catch (error) {
    if (isCurrentAttachmentRequest(request, generation, homeworkId)) {
      const deterministicError = deterministicUploadError(error);
      if (deterministicError) {
        clearSelectedHomeworkFile();
        fileUploadError.value = deterministicError;
      } else {
        fileUploadError.value = localizedError(error, '附件上传失败，请保留当前文件并重试。');
      }
    }
  } finally {
    if (request === attachmentRequestGeneration) {
      fileUploading.value = false;
    }
  }
}

async function removeUploadedHomeworkFile() {
  const uploaded = uploadedAttachment.value;
  if (!uploaded || fileRemoving.value || submitting.value) {
    return;
  }
  const request = ++attachmentRequestGeneration;
  const homeworkId = props.homeworkId;
  const generation = loadGeneration;
  fileRemoving.value = true;
  fileUploadError.value = '';
  try {
    await deleteHomeworkAttachment(homeworkId, uploaded.fileId);
    if (!isCurrentAttachmentRequest(request, generation, homeworkId)) {
      return;
    }
    clearAttachmentState(true);
  } catch (error) {
    if (isCurrentAttachmentRequest(request, generation, homeworkId)) {
      const unavailableError = attachmentRemovalError(error);
      if (unavailableError) {
        clearAttachmentState(true);
        fileUploadError.value = unavailableError;
      } else {
        fileUploadError.value = localizedError(error, '附件移除失败，请重试。');
      }
    }
  } finally {
    if (request === attachmentRequestGeneration) {
      fileRemoving.value = false;
    }
  }
}

function resetForm() {
  if (homework.value?.type === 'FILE' && uploadedAttachment.value) {
    void removeUploadedHomeworkFile();
    return;
  }
  resetEditorState();
  submitErrorMessage.value = '';
}

function resetEditorState() {
  draftWatchSuspended = true;
  clearAttachmentExpiryTimer();
  answerText.value = '';
  objectiveAnswers.value = {};
  codeText.value = '';
  language.value = '';
  selectedFile.value = null;
  uploadedAttachment.value = null;
  fileUploadError.value = '';
  fileRestoreStatus.value = '';
  fileUploading.value = false;
  fileRemoving.value = false;
  if (fileInput.value) {
    fileInput.value.value = '';
  }
  syncDefaultCodeLanguage();
  draftStatusMessage.value = '';
  void nextTick(() => {
    draftWatchSuspended = false;
  });
}

function syncDefaultCodeLanguage() {
  if (homework.value?.type !== 'CODE') {
    return;
  }
  if (allowedCodeLanguages.value.length > 0 && !allowedCodeLanguages.value.includes(language.value.trim())) {
    language.value = allowedCodeLanguages.value[0];
  }
}

function scheduleDraftSave() {
  if (props.mode !== 'submit' || !homework.value) {
    return;
  }
  cancelScheduledDraftSave();
  draftStatusMessage.value = '草稿待保存';
  const token = ++draftSaveToken;
  draftTimer = setTimeout(() => {
    draftTimer = undefined;
    // 只保存仍属于当前编辑会话的调度：组件被重置、切换作业/模式或恢复草稿后，
    // 旧调度的保存不得再执行（空内容会误删刚恢复的草稿）。
    if (token !== draftSaveToken || props.mode !== 'submit' || !homework.value || draftWatchSuspended) {
      return;
    }
    saveDraftNow();
  }, 500);
}

function saveDraftNow(storageKey = draftKey.value) {
  if (!homework.value || homework.value.type === 'FILE') {
    return;
  }
  const draft: HomeworkDraft = {
    version: 1,
    savedAt: Date.now(),
    homeworkType: homework.value.type,
    answerText: answerText.value,
    objectiveAnswers: objectiveAnswers.value,
    codeText: codeText.value,
    language: language.value
  };
  const hasContent = Boolean(
    draft.answerText.trim()
    || draft.codeText.trim()
    || Object.values(draft.objectiveAnswers).some((answers) => answers.length > 0)
  );
  if (!hasContent) {
    window.sessionStorage.removeItem(storageKey);
    draftStatusMessage.value = '';
    return;
  }
  try {
    window.sessionStorage.setItem(storageKey, JSON.stringify(draft));
    draftStatusMessage.value = '草稿已自动保存';
  } catch {
    draftStatusMessage.value = '草稿暂时无法保存，请不要关闭页面';
  }
}

function restoreDraft() {
  if (!homework.value || homework.value.type === 'FILE') {
    return;
  }
  cancelScheduledDraftSave();
  let parsed: HomeworkDraft | undefined;
  try {
    const stored = window.sessionStorage.getItem(draftKey.value);
    parsed = stored ? JSON.parse(stored) as HomeworkDraft : undefined;
  } catch {
    window.sessionStorage.removeItem(draftKey.value);
    return;
  }
  const freshUntil = parsed ? parsed.savedAt + 24 * 60 * 60 * 1000 : 0;
  if (
    !parsed
    || parsed.version !== 1
    || parsed.homeworkType !== homework.value.type
    || !Number.isFinite(parsed.savedAt)
    || Date.now() > freshUntil
  ) {
    window.sessionStorage.removeItem(draftKey.value);
    return;
  }
  draftWatchSuspended = true;
  answerText.value = typeof parsed.answerText === 'string' ? parsed.answerText : '';
  codeText.value = typeof parsed.codeText === 'string' ? parsed.codeText : '';
  language.value = typeof parsed.language === 'string' ? parsed.language : language.value;
  objectiveAnswers.value = sanitizeObjectiveAnswers(parsed.objectiveAnswers);
  syncDefaultCodeLanguage();
  draftStatusMessage.value = '已恢复 24 小时内的自动草稿';
  void nextTick(() => {
    draftWatchSuspended = false;
  });
}

async function restoreHomeworkAttachment(generation: number, homeworkId: number) {
  if (props.mode !== 'submit' || homework.value?.type !== 'FILE') {
    return;
  }
  let draft: HomeworkAttachmentDraft | undefined;
  try {
    const stored = window.sessionStorage.getItem(attachmentStorageKey(homeworkId));
    draft = stored ? JSON.parse(stored) as HomeworkAttachmentDraft : undefined;
  } catch {
    window.sessionStorage.removeItem(attachmentStorageKey(homeworkId));
    return;
  }
  if (!isFreshAttachmentDraft(draft)) {
    window.sessionStorage.removeItem(attachmentStorageKey(homeworkId));
    return;
  }

  const request = ++attachmentRequestGeneration;
  fileUploadError.value = '';
  fileRestoreStatus.value = '正在验证已上传附件…';
  try {
    const restored = await getHomeworkAttachment(homeworkId, draft.attachment.fileId);
    if (!isCurrentAttachmentRequest(request, generation, homeworkId)) {
      return;
    }
    uploadedAttachment.value = restored;
    selectedFile.value = null;
    persistHomeworkAttachment(restored);
    scheduleAttachmentExpiry(restored);
    fileRestoreStatus.value = '已恢复并验证此前上传的附件。';
  } catch (error) {
    if (isCurrentAttachmentRequest(request, generation, homeworkId)) {
      uploadedAttachment.value = null;
      fileRestoreStatus.value = '';
      if (shouldForgetRestoredAttachment(error)) {
        fileUploadError.value = localizedError(error, '已上传附件已不可用，请重新选择并上传。');
        window.sessionStorage.removeItem(attachmentStorageKey(homeworkId));
      } else if (apiErrorCode(error) === 'HWK_5002') {
        fileUploadError.value = '附件存储暂时不可用，已保留恢复信息，请稍后刷新页面重试验证。';
      } else {
        fileUploadError.value = localizedError(
          error,
          '已上传附件验证暂时失败，已保留恢复信息，请稍后刷新页面重试验证。'
        );
      }
    }
  }
}

function persistHomeworkAttachment(attachment: HomeworkAttachmentUpload) {
  const draft: HomeworkAttachmentDraft = {
    version: 1,
    savedAt: Date.now(),
    attachment
  };
  try {
    window.sessionStorage.setItem(attachmentStorageKey(props.homeworkId), JSON.stringify(draft));
  } catch {
    fileUploadError.value = '附件已上传，但本机暂时无法保存恢复信息；请不要刷新页面。';
  }
}

function scheduleAttachmentExpiry(attachment: HomeworkAttachmentUpload) {
  clearAttachmentExpiryTimer();
  const expiresAt = new Date(attachment.expiresAt).getTime();
  const delay = expiresAt - Date.now();
  if (!Number.isFinite(delay) || delay <= 0) {
    expireUploadedAttachmentIfNeeded();
    return;
  }
  attachmentExpiryTimer = setTimeout(() => {
    attachmentExpiryTimer = undefined;
    if (uploadedAttachment.value?.fileId === attachment.fileId) {
      expireUploadedAttachmentIfNeeded();
    }
  }, delay);
}

function expireUploadedAttachmentIfNeeded() {
  const uploaded = uploadedAttachment.value;
  if (!uploaded) {
    return false;
  }
  const expiresAt = new Date(uploaded.expiresAt).getTime();
  if (Number.isFinite(expiresAt) && Date.now() < expiresAt) {
    return false;
  }
  clearAttachmentState(true);
  fileUploadError.value = '附件已过期，请重新选择并上传。';
  return true;
}

function clearAttachmentExpiryTimer() {
  if (attachmentExpiryTimer) {
    clearTimeout(attachmentExpiryTimer);
    attachmentExpiryTimer = undefined;
  }
}

function clearStoredAttachmentDraft(storageKey: string, expectedFileId: string) {
  const stored = window.sessionStorage.getItem(storageKey);
  if (!stored) {
    return;
  }
  try {
    const draft = JSON.parse(stored) as Partial<HomeworkAttachmentDraft>;
    if (draft.attachment?.fileId === expectedFileId) {
      window.sessionStorage.removeItem(storageKey);
    }
  } catch {
    // A malformed value is not proven to belong to this completed request.
  }
}

function isFreshAttachmentDraft(value: HomeworkAttachmentDraft | undefined): value is HomeworkAttachmentDraft {
  if (
    !value
    || value.version !== 1
    || !Number.isFinite(value.savedAt)
    || Date.now() > value.savedAt + 24 * 60 * 60 * 1000
    || !value.attachment
    || typeof value.attachment.fileId !== 'string'
    || !value.attachment.fileId.trim()
    || typeof value.attachment.originalFilename !== 'string'
    || typeof value.attachment.contentType !== 'string'
    || !Number.isFinite(value.attachment.fileSize)
    || typeof value.attachment.expiresAt !== 'string'
    || value.attachment.status !== 'UPLOADED'
    || typeof value.attachment.uploadedAt !== 'string'
  ) {
    return false;
  }
  const expiresAt = new Date(value.attachment.expiresAt).getTime();
  const uploadedAt = new Date(value.attachment.uploadedAt).getTime();
  return Number.isFinite(expiresAt) && Number.isFinite(uploadedAt) && Date.now() < expiresAt;
}

function clearAttachmentState(removeStoredDraft: boolean) {
  clearAttachmentExpiryTimer();
  selectedFile.value = null;
  uploadedAttachment.value = null;
  fileUploadError.value = '';
  fileRestoreStatus.value = '';
  fileUploading.value = false;
  fileRemoving.value = false;
  if (fileInput.value) {
    fileInput.value.value = '';
  }
  if (removeStoredDraft) {
    window.sessionStorage.removeItem(attachmentStorageKey(props.homeworkId));
  }
}

function sanitizeObjectiveAnswers(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {};
  }
  return Object.fromEntries(
    Object.entries(value)
      .filter(([, answers]) => Array.isArray(answers))
      .map(([key, answers]) => [key, (answers as unknown[]).map(String)])
  );
}

function clearDraft() {
  cancelScheduledDraftSave();
  window.sessionStorage.removeItem(draftKey.value);
  draftStatusMessage.value = '';
}

function protectUnsavedDraft(event: BeforeUnloadEvent) {
  if (!hasUnsavedAnswer()) {
    return;
  }
  saveDraftNow();
  event.preventDefault();
  event.returnValue = '';
}

function hasUnsavedAnswer() {
  return Boolean(
    answerText.value.trim()
    || codeText.value.trim()
    || Object.values(objectiveAnswers.value).some((answers) => answers.length > 0)
    || (homework.value?.type === 'FILE' && selectedFile.value && !uploadedAttachment.value)
    || (homework.value?.type === 'FILE' && uploadedAttachment.value)
  );
}

function cancelScheduledDraftSave() {
  if (draftTimer) {
    clearTimeout(draftTimer);
    draftTimer = undefined;
  }
  draftSaveToken += 1;
}

function registerBeforeUnload() {
  if (beforeUnloadRegistered) {
    return;
  }
  window.addEventListener('beforeunload', protectUnsavedDraft);
  beforeUnloadRegistered = true;
}

function unregisterBeforeUnload() {
  if (!beforeUnloadRegistered) {
    return;
  }
  window.removeEventListener('beforeunload', protectUnsavedDraft);
  beforeUnloadRegistered = false;
}

function draftStorageKey(courseId: number, homeworkId: number) {
  return `oj:draft:v1:${currentUser.value?.id ?? 'anonymous'}:${courseId}:HWK:${homeworkId}`;
}

function attachmentStorageKey(
  homeworkId: number,
  courseId = props.courseId,
  userId: number | string = currentUser.value?.id ?? 'anonymous'
) {
  return `oj:hwk-file-upload:v1:${userId}:${courseId}:${homeworkId}`;
}

function isCurrentAttachmentRequest(request: number, generation: number, homeworkId: number) {
  return request === attachmentRequestGeneration
    && generation === loadGeneration
    && homeworkId === props.homeworkId
    && props.mode === 'submit';
}

function formatFileSize(bytes: number) {
  if (!Number.isFinite(bytes) || bytes < 0) {
    return '大小未知';
  }
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function isCurrentLoad(generation: number) {
  return generation === loadGeneration;
}

function isCurrentEvaluationRequest(generation: number, submissionId: number) {
  return isCurrentLoad(generation) && latestSubmission.value?.submissionId === submissionId;
}

function isCurrentSubmissionRequest(
  generation: number,
  editorGeneration: number,
  homeworkId: number,
  submissionId?: number
) {
  return isCurrentLoad(generation)
    && editorGeneration === submissionEditorGeneration
    && props.mode === 'submit'
    && props.homeworkId === homeworkId
    && homework.value?.id === homeworkId
    && (submissionId === undefined || latestSubmission.value?.submissionId === submissionId);
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

function apiErrorCode(error: unknown) {
  if (!error || typeof error !== 'object' || !('code' in error)) {
    return '';
  }
  const code = (error as { code?: unknown }).code;
  return typeof code === 'string' || typeof code === 'number' ? String(code) : '';
}

function deterministicUploadError(error: unknown) {
  const messages: Record<string, string> = {
    HWK_4005: '附件为空或文件内容无效，请重新选择有效文件。',
    HWK_4031: '当前账号无权为该作业上传附件，请返回作业页确认权限。',
    HWK_4131: '附件大小超过 10 MiB，请重新选择较小的文件。',
    HWK_4151: '不支持该附件类型，请重新选择允许的文件。'
  };
  return messages[apiErrorCode(error)] ?? '';
}

function attachmentSubmissionError(error: unknown) {
  const messages: Record<string, string> = {
    HWK_4042: '附件不存在或不属于当前作业，请重新选择并上传。',
    HWK_4091: '附件已过期或不可用，请重新选择并上传。',
    HWK_4092: '附件已被提交绑定，不能重复使用，请重新选择并上传。'
  };
  return messages[apiErrorCode(error)] ?? '';
}

function shouldForgetRestoredAttachment(error: unknown) {
  return ['HWK_4001', 'HWK_4031', 'HWK_4042', 'HWK_4091', 'HWK_4092'].includes(apiErrorCode(error));
}

function attachmentRemovalError(error: unknown) {
  const messages: Record<string, string> = {
    HWK_4031: '当前账号无权移除该附件，已清除本地恢复信息。',
    HWK_4042: '附件已不存在，请重新选择并上传。',
    HWK_4091: '附件已过期或不可用，请重新选择并上传。',
    HWK_4092: '附件已绑定或不可移除，请重新选择并上传。'
  };
  return messages[apiErrorCode(error)] ?? '';
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

.homework-student__file-field {
  display: grid;
  gap: 10px;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--oj-line, #d8deea);
  border-radius: 12px;
  background: color-mix(in srgb, var(--oj-surface, #ffffff) 88%, transparent);
}

.homework-student__file-field > label {
  color: var(--oj-ink, #172033);
  font-size: 0.86rem;
  font-weight: 800;
}

.homework-student__file-field > small,
.homework-student__file-summary span {
  color: var(--oj-muted, #667085);
  font-size: 0.78rem;
  overflow-wrap: anywhere;
}

.homework-student__file-summary {
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 11px 12px;
  border: 1px solid color-mix(in srgb, var(--oj-brand, #2563eb) 24%, var(--oj-line, #d8deea));
  border-radius: 10px;
  background: color-mix(in srgb, var(--oj-brand, #2563eb) 7%, transparent);
}

.homework-student__file-summary strong {
  overflow-wrap: anywhere;
}

.homework-student__file-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.homework-student__file-actions button {
  min-height: 40px;
  padding: 8px 13px;
  border: 1px solid var(--oj-brand, #2563eb);
  border-radius: 10px;
  background: color-mix(in srgb, var(--oj-brand, #2563eb) 8%, var(--oj-surface, #ffffff));
  color: var(--oj-brand, #2563eb);
  cursor: pointer;
  font: inherit;
  font-weight: 800;
}

.homework-student__objective-editor,
.homework-student__flow-actions {
  display: grid;
  gap: 12px;
}

.homework-student__field-label,
.homework-student__objective-question legend {
  color: var(--oj-ink, #172033);
  font-size: 0.9rem;
  font-weight: 800;
}

.homework-student__field-label {
  margin: 0;
}

.homework-student__objective-question {
  border: 1px solid var(--oj-line, #d8deea);
  border-radius: 12px;
  display: grid;
  gap: 9px;
  margin: 0;
  min-width: 0;
  padding: 13px;
}

.homework-student__objective-question legend {
  line-height: 1.5;
  padding: 0 4px;
}

.homework-student__submission-form .homework-student__objective-option {
  align-items: flex-start;
  background: color-mix(in srgb, var(--oj-brand, #2563eb) 4%, transparent);
  border: 1px solid transparent;
  border-radius: 9px;
  cursor: pointer;
  display: flex;
  gap: 9px;
  padding: 9px 10px;
}

.homework-student__submission-form .homework-student__objective-option:focus-within {
  border-color: var(--oj-brand, #2563eb);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--oj-brand, #2563eb) 16%, transparent);
}

.homework-student__submission-form .homework-student__objective-option input {
  flex: 0 0 auto;
  min-height: 0;
  margin: 3px 0 0;
  padding: 0;
  width: 17px;
  height: 17px;
}

.homework-student__draft-status {
  color: var(--oj-muted, #667085);
  font-size: 0.78rem;
  margin: 0;
}

.homework-student__flow-actions a {
  align-items: center;
  border: 1px solid var(--oj-line-strong, #b8c2d2);
  border-radius: 10px;
  display: inline-flex;
  justify-content: center;
  min-height: 42px;
  padding: 8px 13px;
  text-align: center;
  text-decoration: none;
}

.homework-student__flow-actions .homework-student__primary-action {
  background: var(--oj-brand, #2563eb);
  border-color: var(--oj-brand, #2563eb);
  color: #fff;
  font-weight: 800;
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
.homework-student__error,
.homework-student__draft-status {
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

  .homework-student__file-field {
    padding: 12px;
  }

  .homework-student__file-actions,
  .homework-student__file-actions button {
    width: 100%;
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
