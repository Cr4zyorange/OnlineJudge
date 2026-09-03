<template>
  <main
    class="lab-student"
    :class="`lab-student--${mode}`"
    :data-testid="mode === 'detail' ? 'lab-detail-page' : 'lab-submit-page'"
  >
    <PageState
      v-if="loading"
      state="loading"
      title="正在加载实验"
      message="正在同步实验说明、截止状态与最近提交。"
    />
    <PageState
      v-else-if="errorMessage"
      state="error"
      title="实验暂时无法打开"
      :message="errorMessage"
      retry-label="重新加载"
      @retry="loadLabDetail"
    >
      <template #actions>
        <a class="button button--secondary" :href="labsHref">返回实验列表</a>
      </template>
    </PageState>

    <template v-else-if="labDetail">
      <PageHeader
        :title="labDetail.title"
        :eyebrow="mode === 'detail' ? '实验详情' : '实验提交'"
        :subtitle="labDetail.description"
      >
        <template #actions>
          <a class="button button--secondary" :href="labsHref">返回实验列表</a>
        </template>
      </PageHeader>

      <SummaryStrip :items="summaryItems" aria-label="实验状态摘要" />

      <nav class="lab-flow" aria-label="实验任务流程">
        <a
          :class="{ 'lab-flow__step--current': mode === 'detail' }"
          :href="detailHref"
          :aria-current="mode === 'detail' ? 'step' : undefined"
        >
          <span>1</span>阅读详情
        </a>
        <a
          :class="{ 'lab-flow__step--current': mode === 'submit' }"
          :href="submitHref"
          :aria-current="mode === 'submit' ? 'step' : undefined"
          data-testid="lab-submit-link"
        >
          <span>2</span>提交实验
        </a>
        <a :href="resultHref" data-testid="lab-result-link"><span>3</span>查看结果</a>
        <a :href="historyHref" data-testid="lab-history-link"><span>4</span>查看提交历史</a>
      </nav>

      <p v-if="resumeMessage" class="message message--success" role="status">
        {{ resumeMessage }}
      </p>
      <p v-if="historyErrorMessage" class="message message--warning" role="alert">
        {{ historyErrorMessage }}
        <button type="button" class="inline-button" @click="loadLatestSubmission(loadGeneration)">重试同步</button>
      </p>

      <div v-if="mode === 'detail'" class="lab-student__detail-layout">
        <div class="lab-student__main-column">
          <section class="work-surface lab-student__brief" aria-labelledby="lab-brief-title">
            <div class="section-heading">
              <div>
                <p>任务说明</p>
                <h2 id="lab-brief-title">完成要求</h2>
              </div>
              <StatusBadge
                :label="formatLabExperimentStatus(labDetail.status)"
                :tone="labExperimentStatusTone(labDetail.status)"
              />
            </div>
            <p class="lab-student__description">{{ labDetail.description }}</p>
            <dl class="detail-grid">
              <div>
                <dt>允许语言</dt>
                <dd>{{ allowedLanguagesLabel }}</dd>
              </div>
              <div>
                <dt>评测方式</dt>
                <dd>{{ formatLabEvaluationMode(labDetail.evaluationMode) }}</dd>
              </div>
              <div>
                <dt>运行限制</dt>
                <dd>{{ labDetail.timeLimitMs / 1000 }} 秒 · {{ memoryLimitLabel }}</dd>
              </div>
              <div>
                <dt>实验报告</dt>
                <dd>{{ labDetail.reportRequired ? '需要提交' : '无需提交' }}</dd>
              </div>
              <div>
                <dt>任务附件</dt>
                <dd>{{ labDetail.attachmentIds.length ? `已配置 ${labDetail.attachmentIds.length} 个附件` : '无附件' }}</dd>
              </div>
              <div>
                <dt>公开用例</dt>
                <dd>{{ publicTestcases.length }} 个</dd>
              </div>
            </dl>
          </section>

          <LabStudentAttachments
            class="work-surface"
            :course-id="courseId"
            :lab-id="labId"
            :attachment-ids="labDetail.attachmentIds"
          />

          <section class="work-surface lab-student__cases" aria-labelledby="public-case-title">
            <div class="section-heading section-heading--compact">
              <div>
                <p>公开验证</p>
                <h2 id="public-case-title">公开测试用例</h2>
              </div>
            </div>
            <PageState
              v-if="publicTestcases.length === 0"
              state="empty"
              title="暂无公开测试用例"
              message="提交后仍会按实验配置执行评测，请以最终反馈为准。"
            />
            <ol v-else class="case-list">
              <li v-for="testcase in publicTestcases" :key="testcase.id">
                <div><strong>用例 {{ testcase.orderNum }}</strong><span>{{ testcase.scoreWeight }} 分</span></div>
                <dl>
                  <div><dt>输入</dt><dd><code>{{ testcase.input || '空输入' }}</code></dd></div>
                  <div><dt>期望输出</dt><dd><code>{{ testcase.expectedOutput || '空输出' }}</code></dd></div>
                </dl>
              </li>
            </ol>
          </section>
        </div>

        <aside class="work-surface lab-student__next" aria-labelledby="next-step-title">
          <div class="section-heading section-heading--compact">
            <div>
              <p>当前进度</p>
              <h2 id="next-step-title">下一步</h2>
            </div>
          </div>
          <template v-if="latestSubmission">
            <div class="submission-summary">
              <span>最近提交</span>
              <strong>版本 {{ latestSubmission.version }}</strong>
              <StatusBadge
                :label="formatLabEvaluationStatus(latestSubmission.evaluationStatus)"
                :tone="labEvaluationStatusTone(latestSubmission.evaluationStatus)"
              />
              <small>{{ formatLabDateTime(latestSubmission.submittedAt) }}</small>
            </div>
            <a class="button button--primary" :href="resultHref">查看本次结果</a>
            <a class="button button--secondary" :href="submitHref">更新提交版本</a>
          </template>
          <template v-else>
            <div class="submission-summary">
              <span>提交进度</span>
              <strong>尚未提交</strong>
              <small>先核对任务要求，再进入提交工作区。</small>
            </div>
            <a
              v-if="!submissionBlockedReason"
              class="button button--primary"
              data-testid="lab-start-action"
              :href="submitHref"
            >开始实验</a>
            <button
              v-else
              class="button button--primary button--disabled"
              data-testid="lab-start-action"
              type="button"
              disabled
            >开始实验</button>
          </template>
          <p v-if="submissionBlockedReason" class="message message--warning">
            {{ submissionBlockedReason }}
          </p>
          <a class="text-link" :href="historyHref">查看提交历史</a>
        </aside>
      </div>

      <div v-else class="lab-student__submit-layout">
        <section class="work-surface lab-student__submission-pane" aria-labelledby="submit-workspace-title">
          <div class="section-heading">
            <div>
              <p>聚焦工作区</p>
              <h2 id="submit-workspace-title">提交代码或源码文件</h2>
              <span>代码与源码文件二选一；提交过程中请勿重复点击。</span>
            </div>
            <StatusBadge
              :label="submissionBlockedReason ? '当前不可提交' : '可以提交'"
              :tone="submissionBlockedReason ? 'warning' : 'success'"
            />
          </div>

          <p
            v-if="submissionBlockedReason"
            class="message message--warning"
            data-testid="lab-submit-blocked"
          >
            {{ submissionBlockedReason }}
          </p>
          <p
            v-if="draftStatusMessage"
            class="lab-student__draft-status"
            data-testid="lab-draft-status"
            aria-live="polite"
          >
            {{ draftStatusMessage }}
          </p>

          <form class="lab-student__form" data-action="submit-lab" @submit.prevent="submit">
            <label class="field">
              <span>编程语言</span>
              <select v-model="language" name="language" :disabled="Boolean(submissionBlockedReason) || submitting">
                <option value="">请选择编程语言</option>
                <option v-for="item in languageOptions" :key="item" :value="item">
                  {{ formatLabLanguage(item) }}
                </option>
              </select>
            </label>

            <label class="field lab-student__code">
              <span>在线代码</span>
              <textarea
                v-model="code"
                name="code"
                rows="14"
                spellcheck="false"
                placeholder="在这里粘贴或编写完整代码"
                :disabled="Boolean(submissionBlockedReason) || submitting"
                @blur="saveDraftProgress"
              />
            </label>

            <div class="file-picker">
              <label class="field">
                <span>或上传源码文件</span>
                <input
                  ref="sourceFileInput"
                  name="file"
                  type="file"
                  :disabled="Boolean(submissionBlockedReason) || submitting"
                  @change="onFileChange"
                />
              </label>
              <p>{{ selectedFile ? selectedFile.name : '未选择文件；最大 5MB，扩展名需匹配语言。' }}</p>
            </div>

            <p v-if="submitErrorMessage" class="message message--error" role="alert">
              {{ submitErrorMessage }}
            </p>
            <p v-if="feedbackMessage" class="message message--success" role="status">
              {{ feedbackMessage }}
            </p>
            <p
              v-if="reconcileMessage"
              class="message message--success"
              data-testid="lab-reconcile-message"
              role="status"
            >
              {{ reconcileMessage }}
            </p>

            <div class="lab-student__actions lab-student__actions--sticky">
              <button
                class="button button--primary"
                data-testid="submit-lab-button"
                type="submit"
                :disabled="Boolean(submissionBlockedReason) || submitting"
              >
                {{ submitting ? '正在提交并确认…' : '提交实验' }}
              </button>
              <button class="button button--secondary" type="button" :disabled="submitting" @click="resetForm">
                清空输入
              </button>
            </div>
          </form>

          <section
            v-if="labDetail.reportRequired"
            class="lab-student__report"
            aria-labelledby="lab-report-title"
          >
            <div class="section-heading section-heading--compact">
              <div>
                <p>实验报告</p>
                <h3 id="lab-report-title">上传报告文件</h3>
                <span>支持 PDF、DOCX 或 ZIP，单个文件不超过 10MB。</span>
              </div>
            </div>
            <p v-if="reportBlockedReason" class="message message--warning">{{ reportBlockedReason }}</p>
            <form class="lab-student__report-form" @submit.prevent="submitReport">
              <label class="field">
                <span>报告文件</span>
                <input
                  ref="reportFileInput"
                  name="reportFile"
                  type="file"
                  :disabled="reportSubmitting || Boolean(reportBlockedReason)"
                  @change="onReportFileChange"
                />
              </label>
              <div class="lab-student__actions">
                <button
                  class="button button--primary"
                  type="submit"
                  :disabled="reportSubmitting || Boolean(reportBlockedReason)"
                >
                  {{ reportSubmitting ? '正在上传…' : '上传报告' }}
                </button>
                <button class="button button--secondary" type="button" :disabled="reportSubmitting" @click="resetReportForm">
                  清空
                </button>
              </div>
            </form>
            <p v-if="reportFeedbackMessage" class="message message--success" role="status">
              {{ reportFeedbackMessage }}
            </p>
            <p v-if="reportErrorMessage" class="message message--error" role="alert">
              {{ reportErrorMessage }}
            </p>
            <div v-if="latestReport" class="lab-student__report-summary">
              <div>
                <strong>最新报告版本：{{ latestReport.version }}</strong>
                <span>{{ latestReport.fileName }} · {{ latestReport.fileType }}</span>
              </div>
              <button class="button button--secondary" type="button" @click="downloadLatestReport">
                下载最新报告
              </button>
            </div>
            <p v-else class="inline-empty">暂无实验报告</p>
          </section>
        </section>

        <aside class="lab-student__submit-aside">
          <section class="work-surface compact-context" aria-labelledby="submit-context-title">
            <div class="section-heading section-heading--compact">
              <div><p>提交前核对</p><h2 id="submit-context-title">实验上下文</h2></div>
            </div>
            <dl>
              <div><dt>截止时间</dt><dd>{{ formatLabDateTime(labDetail.deadline) }}</dd></div>
              <div><dt>允许语言</dt><dd>{{ allowedLanguagesLabel }}</dd></div>
              <div><dt>评测方式</dt><dd>{{ formatLabEvaluationMode(labDetail.evaluationMode) }}</dd></div>
              <div><dt>公开用例</dt><dd>{{ publicTestcases.length }} 个</dd></div>
            </dl>
            <a class="text-link" :href="detailHref">返回阅读完整说明</a>
          </section>

          <section v-if="latestSubmission" class="work-surface evaluation-preview" aria-labelledby="latest-evaluation-title">
            <div class="section-heading section-heading--compact">
              <div><p>最近反馈</p><h2 id="latest-evaluation-title">版本 {{ latestSubmission.version }}</h2></div>
              <StatusBadge
                :label="formatLabEvaluationStatus(latestSubmission.evaluationStatus)"
                :tone="labEvaluationStatusTone(latestSubmission.evaluationStatus)"
              />
            </div>
            <p>提交状态：{{ formatLabSubmitStatus(latestSubmission.submitStatus) }}</p>
            <p>评测状态：{{ formatLabEvaluationStatus(latestSubmission.evaluationStatus) }}</p>
            <p>提交时间：{{ formatLabDateTime(latestSubmission.submittedAt) }}</p>
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
            <template v-if="scoresPublished && latestScore">
              <p>最终得分：{{ latestScore.finalScore }}</p>
              <p>人工评分：{{ formatLabScore(latestScore.manualScore) }}</p>
              <p>报告评分：{{ formatLabScore(latestScore.reportScore) }}</p>
              <p>教师评语：{{ latestScore.comment ?? '暂无评语' }}</p>
              <p>评分已更新：{{ formatLabDateTime(latestScore.updatedAt) }}</p>
            </template>
            <p v-if="scoresPublished && latestReport">报告评语：{{ latestReport.comment ?? '暂无评语' }}</p>
            <a class="button button--secondary" :href="resultHref">打开完整结果</a>
          </section>
        </aside>
      </div>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { matchedRouteKey, onBeforeRouteLeave } from 'vue-router';
import {
  downloadLabReport,
  getLabDetail,
  getLabResult,
  getLabSubmissionDetail,
  getLabSubmissionResult,
  listLabSubmissions,
  submitLab,
  uploadLabReport
} from '../../api/lab/labs';
import { saveLearningProgress } from '../../api/lrn/learningProgress';
import { reportLearningRecord } from '../../api/lrn/learningRecords';
import { currentUser } from '../../app/runtimeContext';
import PageHeader from '../../components/foundation/PageHeader.vue';
import PageState from '../../components/foundation/PageState.vue';
import StatusBadge from '../../components/foundation/StatusBadge.vue';
import SummaryStrip, { type SummaryStripItem } from '../../components/foundation/SummaryStrip.vue';
import LabStudentAttachments from './LabStudentAttachments.vue';
import { labStudentIdsMatch } from '../../types/lab';
import type {
  LabExperimentDetail,
  LabReportSummary,
  LabScoreSummary,
  LabSubmissionDetail,
  LabSubmissionHistoryItem,
  LabSubmissionId,
  LabSubmissionResult,
  LabSubmissionSummary,
  LabStudentId
} from '../../types/lab';
import {
  formatLabDateTime,
  formatLabEvaluationMode,
  formatLabEvaluationStatus,
  formatLabExperimentStatus,
  formatLabLanguage,
  formatLabScore,
  formatLabSubmitStatus,
  labEvaluationStatusTone,
  labExperimentStatusTone,
  localizedLabError
} from './labDisplay';

type LabStudentMode = 'detail' | 'submit';

interface LabDraft {
  version: 1;
  savedAt: number;
  language: string;
  code: string;
}

interface SubmissionAttempt {
  labId: number;
  language: string;
  code: string;
  file: File | null;
  startedAt: number;
}

type ReconciliationResult = 'confirmed' | 'not-confirmed' | 'uncertain';

const MAX_UPLOAD_SIZE_BYTES = 5 * 1024 * 1024;
const MAX_REPORT_UPLOAD_SIZE_BYTES = 10 * 1024 * 1024;
const DRAFT_TTL_MS = 24 * 60 * 60 * 1000;
const EVALUATION_POLL_INTERVAL_MS = 1000;
const EVALUATION_POLL_TIMEOUT_MS = 60 * 1000;
const RECONCILIATION_CLOCK_SKEW_MS = 5 * 1000;
const TERMINAL_EVALUATION_STATUSES = new Set<LabSubmissionSummary['evaluationStatus']>([
  'NONE', 'ACCEPTED', 'WRONG_ANSWER', 'COMPILE_ERROR', 'RUNTIME_ERROR',
  'TIME_LIMIT_EXCEEDED', 'SYSTEM_ERROR'
]);
const ALLOWED_FILE_EXTENSIONS: Record<string, string[]> = {
  java: ['java'], python: ['py'], cpp: ['cpp', 'cc', 'cxx'], c: ['c']
};

const props = withDefaults(defineProps<{
  courseId: number;
  labId: number;
  mode?: LabStudentMode;
}>(), { mode: 'submit' });

const loading = ref(false);
const submitting = ref(false);
const reportSubmitting = ref(false);
const labDetail = ref<LabExperimentDetail | null>(null);
const latestSubmission = ref<LabSubmissionSummary | LabSubmissionHistoryItem | null>(null);
const latestEvaluationResult = ref<LabSubmissionResult | null>(null);
const latestReport = ref<LabReportSummary | null>(null);
const latestScore = ref<LabScoreSummary | null>(null);
const resultStatus = ref<LabExperimentDetail['status'] | null>(null);
const errorMessage = ref('');
const submitErrorMessage = ref('');
const feedbackMessage = ref('');
const reconcileMessage = ref('');
const resumeMessage = ref('');
const historyErrorMessage = ref('');
const reportErrorMessage = ref('');
const reportFeedbackMessage = ref('');
const draftStatusMessage = ref('');
const language = ref('');
const code = ref('');
const selectedFile = ref<File | null>(null);
const selectedReportFile = ref<File | null>(null);
const sourceFileInput = ref<HTMLInputElement | null>(null);
const reportFileInput = ref<HTMLInputElement | null>(null);
const openedAt = ref<Date | null>(null);
let evaluationPollTimer: number | undefined;
let evaluationWatchdogTimer: number | undefined;
let draftTimer: number | undefined;
let draftWatchSuspended = false;
let beforeUnloadRegistered = false;
let loadGeneration = 0;
let submissionGeneration = 0;
let reportGeneration = 0;
let evaluationRequestGeneration = 0;
let historyRequestGeneration = 0;

const mode = computed(() => props.mode);
const publicTestcases = computed(() => (labDetail.value?.testcases ?? []).filter((testcase) => testcase.public));
const languageOptions = computed(() => {
  const raw = labDetail.value?.allowedLanguages;
  return raw ? raw.split(',').map((item) => item.trim().toLowerCase()).filter(Boolean) : ['java', 'python'];
});
const allowedLanguagesLabel = computed(() => languageOptions.value.map(formatLabLanguage).join('、'));
const memoryLimitLabel = computed(() => {
  const kb = labDetail.value?.memoryLimitKb ?? 0;
  return kb >= 1024 ? `${Math.round(kb / 1024)} MB` : `${kb} KB`;
});
const labsHref = computed(() => `/courses/${props.courseId}/labs`);
const detailHref = computed(() => `/courses/${props.courseId}/labs/${props.labId}`);
const submitHref = computed(() => `${detailHref.value}/submit`);
const resultHref = computed(() => `${detailHref.value}/result`);
const historyHref = computed(() => `${detailHref.value}/submissions`);
const scoresPublished = computed(() => (
  isScorePublished(labDetail.value?.status)
  && isScorePublished(resultStatus.value)
));
const submissionBlockedReason = computed(() => {
  const lab = labDetail.value;
  if (!lab) return '实验详情尚未加载';
  if (lab.deleted || lab.status === 'ARCHIVED') return '实验已归档，当前不可提交';
  if (lab.status === 'DRAFT' || lab.status === 'NOT_OPEN') return '实验尚未开放，当前不可提交';
  if (lab.status === 'CLOSED' || lab.status === 'SCORE_PUBLISHED') return '实验提交阶段已结束';
  if (new Date(lab.deadline).getTime() <= Date.now()) return '实验已截止，当前不可提交';
  return '';
});
const reportBlockedReason = computed(() => {
  if (submissionBlockedReason.value) return submissionBlockedReason.value;
  if (!latestSubmission.value) return '请先提交实验代码，再上传实验报告';
  return '';
});
const summaryItems = computed<SummaryStripItem[]>(() => {
  const lab = labDetail.value;
  if (!lab) return [];
  return [
    {
      key: 'status', label: '任务状态', value: formatLabExperimentStatus(lab.status),
      hint: submissionBlockedReason.value || '可在截止前更新版本',
      tone: submissionBlockedReason.value ? 'warning' : 'brand'
    },
    {
      key: 'deadline', label: '截止时间', value: formatLabDateTime(lab.deadline),
      hint: deadlineHint(lab.deadline), tone: submissionBlockedReason.value ? 'warning' : 'neutral'
    },
    {
      key: 'submission', label: '最近提交',
      value: latestSubmission.value ? `版本 ${latestSubmission.value.version}` : '尚未提交',
      hint: latestSubmission.value ? formatLabEvaluationStatus(latestSubmission.value.evaluationStatus) : '进入工作区开始实验'
    },
    {
      key: 'score', label: '成绩',
      value: scoresPublished.value
        ? latestScore.value ? `${latestScore.value.finalScore} 分` : '已发布，暂无评分'
        : '尚未发布',
      hint: scoresPublished.value
        ? latestScore.value ? '以教师发布结果为准' : '成绩已发布，当前暂无教师评分'
        : '发布前隐藏教师评分',
      tone: scoresPublished.value ? 'success' : 'neutral'
    }
  ];
});
const draftKey = computed(() => `oj:draft:v1:${currentUser.value?.id ?? 'anonymous'}:${props.courseId}:LAB:${props.labId}`);

watch([code, language], () => {
  if (!draftWatchSuspended && mode.value === 'submit') scheduleDraftSave();
});
watch(() => props.mode, (nextMode, previousMode) => {
  if (previousMode === 'submit' && nextMode !== 'submit') {
    saveDraftNow();
    submissionGeneration += 1;
    submitting.value = false;
    reportGeneration += 1;
    reportSubmitting.value = false;
    unregisterBeforeUnload();
  }
  if (nextMode === 'submit' && previousMode !== 'submit') {
    restoreDraft();
    registerBeforeUnload();
  }
});
watch([() => props.courseId, () => props.labId], ([,], [previousCourseId, previousLabId]) => {
  if (mode.value === 'submit' && labDetail.value) {
    saveDraftNow(`oj:draft:v1:${currentUser.value?.id ?? 'anonymous'}:${previousCourseId}:LAB:${previousLabId}`);
  }
  resetPageState();
  void loadLabDetail();
});

onMounted(() => {
  if (mode.value === 'submit') registerBeforeUnload();
  void loadLabDetail();
});
onBeforeUnmount(() => {
  if (mode.value === 'submit') saveDraftNow();
  clearEvaluationPoll();
  cancelScheduledDraftSave();
  unregisterBeforeUnload();
  loadGeneration += 1;
  submissionGeneration += 1;
  reportGeneration += 1;
});

if (inject(matchedRouteKey, null)) {
  onBeforeRouteLeave(() => {
    if (mode.value !== 'submit' || !hasUnsavedInput()) return true;
    saveDraftNow();
    const fileWarning = selectedFile.value || selectedReportFile.value
      ? '已选择的文件离开后需要重新选择。'
      : '';
    return window.confirm(`当前代码草稿已自动保存。${fileWarning}确认离开提交页吗？`);
  });
}

async function loadLabDetail() {
  const generation = ++loadGeneration;
  const requestedLabId = props.labId;
  const requestedCourseId = props.courseId;
  loading.value = true;
  errorMessage.value = '';
  historyErrorMessage.value = '';
  clearEvaluationPoll();
  try {
    const detail = await getLabDetail(requestedLabId);
    if (!isCurrentLoad(generation, requestedLabId)) return;
    if (detail.id !== requestedLabId || detail.courseId !== requestedCourseId) {
      throw new Error('实验信息与当前课程不匹配，请重新加载。');
    }
    labDetail.value = detail;
    openedAt.value = new Date();
    syncDefaultLanguage();
    if (mode.value === 'submit') restoreDraft();
    const resumed = restoreResumeCode();
    await loadLatestSubmission(generation);
    if (!isCurrentLoad(generation, requestedLabId)) return;
    if (!resumed) await recordProgress(10, `labId=${requestedLabId}`);
    await recordBehavior('ACCESS', 0);
  } catch (error) {
    if (isCurrentLoad(generation, requestedLabId)) {
      errorMessage.value = localizedLabError(error, '实验详情加载失败，请稍后重试。');
    }
  } finally {
    if (isCurrentLoad(generation, requestedLabId)) loading.value = false;
  }
}

async function loadLatestSubmission(generation = loadGeneration) {
  const requestedLabId = props.labId;
  const requestedStudentId = currentUser.value?.id;
  const historyRequest = ++historyRequestGeneration;
  clearEvaluationPoll();
  historyErrorMessage.value = '';
  try {
    if (requestedStudentId === undefined || requestedStudentId === null) {
      throw new Error('无法确认当前学生身份，请重新登录后重试。');
    }
    const history = await listLabSubmissions(requestedLabId);
    if (!isCurrentHistoryRequest(generation, historyRequest, requestedLabId)) return;
    validateSubmissionHistory(history, requestedLabId, requestedStudentId);
    const latest = selectLatestSubmission(history);
    latestSubmission.value = latest;
    if (!latest) {
      clearEvaluationPoll();
      latestEvaluationResult.value = null;
      latestReport.value = null;
      latestScore.value = null;
      return;
    }
    await refreshLatestEvaluationResult(latest.submissionId, generation);
    if (isCurrentHistoryRequest(generation, historyRequest, requestedLabId)) {
      await refreshStudentLabResult(latest.studentId, generation, historyRequest);
    }
  } catch (error) {
    if (isCurrentHistoryRequest(generation, historyRequest, requestedLabId)) {
      historyErrorMessage.value = localizedLabError(error, '提交历史加载失败，请重试同步。');
    }
  }
}

async function submit() {
  if (submitting.value) return;
  submitErrorMessage.value = validateForm();
  feedbackMessage.value = '';
  reconcileMessage.value = '';
  if (submitErrorMessage.value) return;

  const generation = loadGeneration;
  const editorGeneration = ++submissionGeneration;
  const requestedLabId = props.labId;
  const previousVersion = latestSubmission.value?.version ?? 0;
  const attempt: SubmissionAttempt = {
    labId: requestedLabId,
    language: language.value.trim().toLowerCase(),
    code: code.value.trim(),
    file: selectedFile.value,
    startedAt: Date.now()
  };
  submitting.value = true;
  try {
    const submitted = await submitLab(requestedLabId, {
      language: attempt.language,
      code: attempt.code || undefined,
      file: attempt.file ?? undefined
    });
    if (!isCurrentSubmission(generation, editorGeneration, requestedLabId)) return;
    validateConfirmedSubmission(submitted, requestedLabId);
    await acceptConfirmedSubmission(submitted, generation, false);
  } catch (error) {
    if (!isCurrentSubmission(generation, editorGeneration, requestedLabId)) return;
    saveDraftNow();
    const reconciliation = await reconcileSubmission(previousVersion, generation, editorGeneration, attempt);
    if (reconciliation !== 'confirmed' && isCurrentSubmission(generation, editorGeneration, requestedLabId)) {
      if (reconciliation === 'uncertain') {
        submitErrorMessage.value = attempt.file
          ? '请求连接中断，文件提交结果不确定；已保留草稿和当前文件选择，请先核对提交历史。'
          : '请求连接中断，无法确认新版本来自本次代码提交；已保留草稿，请先核对提交历史。';
      } else {
        submitErrorMessage.value = localizedLabError(error, '实验提交失败，请检查网络后重试。');
      }
    }
  } finally {
    if (isCurrentSubmission(generation, editorGeneration, requestedLabId)) submitting.value = false;
  }
}

async function acceptConfirmedSubmission(
  submitted: LabSubmissionSummary | LabSubmissionHistoryItem,
  generation: number,
  reconciled: boolean
) {
  historyRequestGeneration += 1;
  latestSubmission.value = submitted;
  latestEvaluationResult.value = null;
  latestScore.value = null;
  if (reconciled) {
    reconcileMessage.value = `请求连接中断，但已在提交历史确认版本 ${submitted.version}，无需重复提交。`;
  } else {
    feedbackMessage.value = `提交成功，版本 ${submitted.version}`;
  }
  clearDraft();
  resetEditorInputs();
  const evaluationRefresh = refreshLatestEvaluationResult(submitted.submissionId, generation);
  void recordProgress(100, `submittedVersion=${submitted.version}`);
  void recordBehavior('SUBMIT', elapsedSeconds());
  await evaluationRefresh;
  if (isCurrentLoad(generation, props.labId)) await refreshStudentLabResult(submitted.studentId, generation);
}

async function reconcileSubmission(
  previousVersion: number,
  generation: number,
  editorGeneration: number,
  attempt: SubmissionAttempt
): Promise<ReconciliationResult> {
  try {
    const history = await listLabSubmissions(attempt.labId);
    if (!isCurrentSubmission(generation, editorGeneration, attempt.labId)) return 'not-confirmed';
    const requestedStudentId = currentUser.value?.id;
    if (requestedStudentId === undefined || requestedStudentId === null) return 'not-confirmed';
    validateSubmissionHistory(history, attempt.labId, requestedStudentId);
    const latest = selectLatestSubmission(history);
    if (!latest || latest.version <= previousVersion) {
      return attempt.file ? 'uncertain' : 'not-confirmed';
    }
    if (attempt.file) return 'uncertain';
    let detail: LabSubmissionDetail;
    try {
      detail = await getLabSubmissionDetail(attempt.labId, latest.submissionId);
    } catch {
      return 'uncertain';
    }
    if (!isCurrentSubmission(generation, editorGeneration, attempt.labId)) return 'not-confirmed';
    if (!matchesCodeSubmissionAttempt(detail, attempt)) return 'uncertain';
    await acceptConfirmedSubmission(latest, generation, true);
    return 'confirmed';
  } catch {
    return attempt.file ? 'uncertain' : 'not-confirmed';
  }
}

async function submitReport() {
  if (reportSubmitting.value) return;
  reportErrorMessage.value = validateReportForm();
  reportFeedbackMessage.value = '';
  if (reportErrorMessage.value) return;
  const generation = loadGeneration;
  const editorGeneration = ++reportGeneration;
  const requestedLabId = props.labId;
  const requestedSubmissionId = latestSubmission.value?.submissionId;
  const reportFile = selectedReportFile.value as File;
  reportSubmitting.value = true;
  try {
    const report = await uploadLabReport(requestedLabId, {
      submissionId: requestedSubmissionId,
      reportFile
    });
    if (!isCurrentReportRequest(generation, editorGeneration, requestedLabId)) return;
    latestReport.value = report;
    reportFeedbackMessage.value = `实验报告上传成功，版本 ${report.version}`;
    clearReportFileSelection();
  } catch (error) {
    if (isCurrentReportRequest(generation, editorGeneration, requestedLabId)) {
      reportErrorMessage.value = localizedLabError(error, '实验报告上传失败，请稍后重试。');
    }
  } finally {
    if (isCurrentReportRequest(generation, editorGeneration, requestedLabId)) {
      reportSubmitting.value = false;
    }
  }
}

async function refreshLatestEvaluationResult(
  submissionId: LabSubmissionId,
  generation = loadGeneration,
  pollStartedAt = Date.now()
) {
  clearEvaluationPoll();
  const requestGeneration = ++evaluationRequestGeneration;
  if (!isCurrentEvaluationRequest(generation, submissionId, requestGeneration)) return;
  const elapsed = Date.now() - pollStartedAt;
  const remaining = Math.max(0, EVALUATION_POLL_TIMEOUT_MS - elapsed);
  const requestTimedOut = Symbol('evaluation-request-timeout');
  let watchdogTimer: number | undefined;
  const timeoutResult = new Promise<typeof requestTimedOut>((resolve) => {
    watchdogTimer = window.setTimeout(() => resolve(requestTimedOut), remaining);
    evaluationWatchdogTimer = watchdogTimer;
  });
  try {
    const result = await Promise.race([
      getLabSubmissionResult(props.labId, submissionId),
      timeoutResult
    ]);
    if (result === requestTimedOut) {
      if (isCurrentEvaluationRequest(generation, submissionId, requestGeneration)) {
        evaluationRequestGeneration += 1;
        historyErrorMessage.value = '评测结果同步超过 60 秒，请手动重试同步。';
      }
      return;
    }
    if (!result
      || result.submissionId !== submissionId
      || !isCurrentEvaluationRequest(generation, submissionId, requestGeneration)) return;
    latestEvaluationResult.value = result;
    if (latestSubmission.value) {
      latestSubmission.value = { ...latestSubmission.value, evaluationStatus: result.evaluationStatus, autoScore: result.score };
    }
    if (!TERMINAL_EVALUATION_STATUSES.has(result.evaluationStatus)) {
      const responseElapsed = Date.now() - pollStartedAt;
      if (responseElapsed >= EVALUATION_POLL_TIMEOUT_MS) {
        historyErrorMessage.value = '评测结果同步超过 60 秒，请重试同步。';
        return;
      }
      const delay = Math.min(EVALUATION_POLL_INTERVAL_MS, EVALUATION_POLL_TIMEOUT_MS - responseElapsed);
      evaluationPollTimer = window.setTimeout(
        () => void refreshLatestEvaluationResult(submissionId, generation, pollStartedAt),
        delay
      );
    }
  } catch (error) {
    if (isCurrentEvaluationRequest(generation, submissionId, requestGeneration)) {
      historyErrorMessage.value = localizedLabError(error, '评测结果暂时无法同步，请稍后重试。');
    }
  } finally {
    if (watchdogTimer !== undefined) {
      window.clearTimeout(watchdogTimer);
      if (evaluationWatchdogTimer === watchdogTimer) {
        evaluationWatchdogTimer = undefined;
      }
    }
  }
}

async function refreshStudentLabResult(
  studentId: LabStudentId,
  generation = loadGeneration,
  historyRequest?: number
) {
  const requestedLabId = props.labId;
  if (!labStudentIdsMatch(currentUser.value?.id, studentId)) return;
  if (historyRequest !== undefined
    && !isCurrentHistoryRequest(generation, historyRequest, requestedLabId)) return;
  try {
    const result = await getLabResult(requestedLabId, studentId);
    if (!isCurrentLoad(generation, requestedLabId)) return;
    if (historyRequest !== undefined
      && !isCurrentHistoryRequest(generation, historyRequest, requestedLabId)) return;
    const currentSubmission = latestSubmission.value;
    if (!currentSubmission) return;
    validateStudentLabResult(result, requestedLabId, studentId, currentSubmission.submissionId);
    const directResult = latestEvaluationResult.value;
    const keepTerminalDirectResult = directResult?.submissionId === result.submission.submissionId
      && TERMINAL_EVALUATION_STATUSES.has(directResult.evaluationStatus)
      && !TERMINAL_EVALUATION_STATUSES.has(result.evaluationResult.evaluationStatus);
    latestSubmission.value = keepTerminalDirectResult
      ? {
          ...result.submission,
          evaluationStatus: directResult.evaluationStatus,
          autoScore: directResult.score
        }
      : result.submission;
    latestEvaluationResult.value = keepTerminalDirectResult ? directResult : result.evaluationResult;
    latestReport.value = result.latestReport;
    latestScore.value = result.latestScore ?? null;
    resultStatus.value = result.status;
  } catch {
    // The compact preview remains usable when the optional aggregate result is unavailable.
  }
}

async function downloadLatestReport() {
  if (!latestReport.value) return;
  reportErrorMessage.value = '';
  try {
    const { blob, filename } = await downloadLabReport(props.labId, latestReport.value.reportId);
    const objectUrl = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = filename || latestReport.value.fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(objectUrl);
  } catch (error) {
    reportErrorMessage.value = localizedLabError(error, '实验报告下载失败，请稍后重试。');
  }
}

async function saveDraftProgress() {
  saveDraftNow();
  if (!code.value.trim()) return;
  await recordProgress(40, `code=${code.value.slice(0, 450)}`);
  await recordBehavior('STUDY', elapsedSeconds());
}

async function recordProgress(progressPercent: number, lastPosition: string) {
  if (!labDetail.value) return;
  try {
    await saveLearningProgress({
      courseId: props.courseId,
      chapterId: labDetail.value.chapterId,
      sourceModule: 'LAB', sourceId: props.labId, progressPercent, lastPosition
    });
  } catch {
    // Progress persistence must not block the experiment flow.
  }
}

async function recordBehavior(actionType: 'ACCESS' | 'STUDY' | 'SUBMIT', durationSeconds: number) {
  if (!labDetail.value) return;
  try {
    await reportLearningRecord({
      courseId: props.courseId, sourceModule: 'LAB', sourceId: props.labId, actionType, durationSeconds
    });
  } catch {
    // Behavior tracking must not block the experiment flow.
  }
}

function validateForm() {
  const errors: string[] = [];
  if (submissionBlockedReason.value) errors.push(submissionBlockedReason.value);
  if (!language.value) errors.push('请选择编程语言');
  if (!code.value.trim() && !selectedFile.value) errors.push('请填写代码或上传文件');
  if (code.value.trim() && selectedFile.value) errors.push('在线代码和源码文件只能选择一种提交方式');
  if (selectedFile.value) {
    const extension = getFileExtension(selectedFile.value.name);
    const allowedExtensions = ALLOWED_FILE_EXTENSIONS[language.value] ?? [];
    if (!allowedExtensions.includes(extension)) {
      const readable = allowedExtensions.length
        ? allowedExtensions.map((item) => `.${item}`).join('、')
        : '当前语言对应的源码文件';
      errors.push(`仅支持 ${readable} 文件`);
    }
    if (selectedFile.value.size > MAX_UPLOAD_SIZE_BYTES) errors.push('上传文件大小不能超过 5MB');
  }
  return [...new Set(errors)].join('；');
}

function validateReportForm() {
  const errors: string[] = [];
  if (reportBlockedReason.value) errors.push(reportBlockedReason.value);
  if (!selectedReportFile.value) errors.push('请选择实验报告文件');
  if (selectedReportFile.value) {
    const extension = getFileExtension(selectedReportFile.value.name);
    if (!['pdf', 'docx', 'zip'].includes(extension)) errors.push('实验报告仅支持 PDF、DOCX 或 ZIP');
    if (selectedReportFile.value.size > MAX_REPORT_UPLOAD_SIZE_BYTES) errors.push('实验报告大小不能超过 10MB');
  }
  return [...new Set(errors)].join('；');
}

function selectLatestSubmission(history: LabSubmissionHistoryItem[] | null | undefined) {
  if (!Array.isArray(history) || history.length === 0) return null;
  const explicitlyLatest = history.filter((item) => item.isLatest);
  const candidates = explicitlyLatest.length ? explicitlyLatest : history;
  return [...candidates].sort((left, right) => {
    if (left.version !== right.version) return right.version - left.version;
    return new Date(right.submittedAt).getTime() - new Date(left.submittedAt).getTime();
  })[0] ?? null;
}

function validateSubmissionHistory(
  history: LabSubmissionHistoryItem[],
  labId: number,
  studentId: LabStudentId
) {
  if (!labStudentIdsMatch(currentUser.value?.id, studentId)
    || history.some((item) => item.labId !== labId || !labStudentIdsMatch(item.studentId, studentId))) {
    throw new Error('提交历史与当前实验或学生不匹配，请重新加载。');
  }
}

function validateConfirmedSubmission(submitted: LabSubmissionSummary, labId: number) {
  const studentId = currentUser.value?.id;
  if (studentId === undefined || studentId === null
    || submitted.labId !== labId
    || !labStudentIdsMatch(submitted.studentId, studentId)) {
    throw new Error('提交回执与当前实验或学生不匹配，请核对提交历史。');
  }
}

function validateStudentLabResult(
  result: Awaited<ReturnType<typeof getLabResult>>,
  labId: number,
  studentId: LabStudentId,
  submissionId: LabSubmissionId
) {
  if (!labStudentIdsMatch(currentUser.value?.id, studentId)
    || result.labId !== labId
    || !labStudentIdsMatch(result.studentId, studentId)
    || result.submission.labId !== labId
    || !labStudentIdsMatch(result.submission.studentId, studentId)
    || result.submission.submissionId !== submissionId
    || result.evaluationResult.submissionId !== submissionId) {
    throw new Error('返回的实验结果与当前提交不匹配。');
  }
}

function isScorePublished(status: LabExperimentDetail['status'] | null | undefined) {
  return status === 'SCORE_PUBLISHED' || status === 'ARCHIVED';
}

function scheduleDraftSave() {
  cancelScheduledDraftSave();
  if (!code.value.trim() && !selectedFile.value) {
    window.sessionStorage.removeItem(draftKey.value);
    draftStatusMessage.value = '';
    return;
  }
  draftStatusMessage.value = '草稿待保存';
  draftTimer = window.setTimeout(() => {
    saveDraftNow();
    draftTimer = undefined;
  }, 500);
}

function saveDraftNow(storageKey = draftKey.value) {
  if (!labDetail.value || mode.value !== 'submit') return;
  const draft: LabDraft = { version: 1, savedAt: Date.now(), language: language.value, code: code.value };
  if (!draft.code.trim() && !selectedFile.value) {
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
  if (!labDetail.value) return;
  let draft: LabDraft | undefined;
  try {
    const stored = window.sessionStorage.getItem(draftKey.value);
    draft = stored ? JSON.parse(stored) as LabDraft : undefined;
  } catch {
    window.sessionStorage.removeItem(draftKey.value);
    return;
  }
  if (!draft || draft.version !== 1 || !Number.isFinite(draft.savedAt) || Date.now() > draft.savedAt + DRAFT_TTL_MS) {
    window.sessionStorage.removeItem(draftKey.value);
    return;
  }
  draftWatchSuspended = true;
  language.value = languageOptions.value.includes(draft.language) ? draft.language : language.value;
  code.value = typeof draft.code === 'string' ? draft.code : '';
  draftStatusMessage.value = '已恢复 24 小时内的自动草稿';
  void nextTick(() => { draftWatchSuspended = false; });
}

function clearDraft() {
  cancelScheduledDraftSave();
  window.sessionStorage.removeItem(draftKey.value);
  draftStatusMessage.value = '';
}

function restoreResumeCode() {
  const resume = new URLSearchParams(window.location.search).get('resume');
  if (!resume) return false;
  resumeMessage.value = `已恢复上次断点：${resume}`;
  if (resume.startsWith('code=')) {
    draftWatchSuspended = true;
    code.value = resume.slice('code='.length);
    void nextTick(() => { draftWatchSuspended = false; });
  }
  return true;
}

function resetForm() {
  resetEditorInputs();
  clearDraft();
  submitErrorMessage.value = '';
  feedbackMessage.value = '';
  reconcileMessage.value = '';
}
function resetEditorInputs() {
  draftWatchSuspended = true;
  code.value = '';
  clearSourceFileSelection();
  language.value = languageOptions.value.length === 1 ? languageOptions.value[0] : '';
  void nextTick(() => { draftWatchSuspended = false; });
}
function resetReportForm() {
  clearReportFileSelection();
  reportErrorMessage.value = '';
  reportFeedbackMessage.value = '';
}
function resetPageState() {
  clearEvaluationPoll();
  historyRequestGeneration += 1;
  cancelScheduledDraftSave();
  submissionGeneration += 1;
  submitting.value = false;
  reportGeneration += 1;
  reportSubmitting.value = false;
  labDetail.value = null;
  latestSubmission.value = null;
  latestEvaluationResult.value = null;
  latestReport.value = null;
  latestScore.value = null;
  resultStatus.value = null;
  resetEditorInputs();
  resetReportForm();
  errorMessage.value = '';
  historyErrorMessage.value = '';
  resumeMessage.value = '';
}
function syncDefaultLanguage() {
  if (languageOptions.value.length === 1 || !languageOptions.value.includes(language.value)) {
    language.value = languageOptions.value.length === 1 ? languageOptions.value[0] : '';
  }
}
function onFileChange(event: Event) {
  selectedFile.value = (event.target as HTMLInputElement).files?.[0] ?? null;
  submitErrorMessage.value = '';
}
function onReportFileChange(event: Event) {
  selectedReportFile.value = (event.target as HTMLInputElement).files?.[0] ?? null;
  reportErrorMessage.value = '';
}
function clearSourceFileSelection() {
  selectedFile.value = null;
  if (sourceFileInput.value) sourceFileInput.value.value = '';
}
function clearReportFileSelection() {
  selectedReportFile.value = null;
  if (reportFileInput.value) reportFileInput.value.value = '';
}
function protectUnsavedDraft(event: BeforeUnloadEvent) {
  if (!hasUnsavedInput()) return;
  saveDraftNow();
  event.preventDefault();
  event.returnValue = '';
}
function hasUnsavedInput() {
  return Boolean(code.value.trim() || selectedFile.value || selectedReportFile.value);
}
function registerBeforeUnload() {
  if (beforeUnloadRegistered) return;
  window.addEventListener('beforeunload', protectUnsavedDraft);
  beforeUnloadRegistered = true;
}
function unregisterBeforeUnload() {
  if (!beforeUnloadRegistered) return;
  window.removeEventListener('beforeunload', protectUnsavedDraft);
  beforeUnloadRegistered = false;
}
function cancelScheduledDraftSave() {
  if (!draftTimer) return;
  window.clearTimeout(draftTimer);
  draftTimer = undefined;
}
function clearEvaluationPoll() {
  evaluationRequestGeneration += 1;
  if (evaluationPollTimer !== undefined) {
    window.clearTimeout(evaluationPollTimer);
    evaluationPollTimer = undefined;
  }
  if (evaluationWatchdogTimer !== undefined) {
    window.clearTimeout(evaluationWatchdogTimer);
    evaluationWatchdogTimer = undefined;
  }
}
function isCurrentLoad(generation: number, labId = props.labId) {
  return generation === loadGeneration && labId === props.labId;
}
function isCurrentHistoryRequest(generation: number, historyRequest: number, labId: number) {
  return isCurrentLoad(generation, labId) && historyRequest === historyRequestGeneration;
}
function isCurrentSubmission(generation: number, editorGeneration: number, labId: number) {
  return isCurrentLoad(generation, labId) && editorGeneration === submissionGeneration && mode.value === 'submit';
}
function isCurrentReportRequest(generation: number, editorGeneration: number, labId: number) {
  return isCurrentLoad(generation, labId) && editorGeneration === reportGeneration && mode.value === 'submit';
}
function isCurrentEvaluationRequest(generation: number, submissionId: LabSubmissionId, requestGeneration: number) {
  return isCurrentLoad(generation)
    && requestGeneration === evaluationRequestGeneration
    && latestSubmission.value?.submissionId === submissionId;
}
function matchesCodeSubmissionAttempt(detail: LabSubmissionDetail, attempt: SubmissionAttempt) {
  const submittedAt = new Date(detail.submittedAt).getTime();
  const currentStudentId = currentUser.value?.id;
  return !attempt.file
    && Boolean(attempt.code)
    && detail.labId === attempt.labId
    && String(detail.studentId) === String(currentStudentId)
    && detail.language.trim().toLowerCase() === attempt.language
    && (detail.code ?? '').trim() === attempt.code
    && Number.isFinite(submittedAt)
    && submittedAt >= attempt.startedAt - RECONCILIATION_CLOCK_SKEW_MS
    && submittedAt <= Date.now() + RECONCILIATION_CLOCK_SKEW_MS;
}
function elapsedSeconds() {
  return openedAt.value ? Math.max(0, Math.round((Date.now() - openedAt.value.getTime()) / 1000)) : 0;
}
function deadlineHint(value: string) {
  const remainingMs = new Date(value).getTime() - Date.now();
  if (remainingMs <= 0) return '截止时间已过';
  const hours = Math.ceil(remainingMs / 3_600_000);
  return hours < 24 ? `剩余约 ${hours} 小时` : `剩余约 ${Math.ceil(hours / 24)} 天`;
}
function getFileExtension(fileName: string) {
  const index = fileName.lastIndexOf('.');
  return index < 0 || index === fileName.length - 1 ? '' : fileName.slice(index + 1).toLowerCase();
}
</script>

<style scoped>
.lab-student { display: grid; gap: 16px; min-width: 0; padding-bottom: 44px; color: var(--oj-ink); }
.button, .inline-button { box-sizing: border-box; border: 1px solid transparent; border-radius: var(--oj-radius-control); font: inherit; font-weight: 800; text-decoration: none; cursor: pointer; }
.button { display: inline-flex; align-items: center; justify-content: center; min-height: 42px; padding: 9px 15px; }
.button--primary { background: var(--oj-brand); color: #fff; }
.button--secondary { border-color: var(--oj-line-strong); background: rgba(255,255,255,.72); color: var(--oj-brand); }
.button--disabled, .button:disabled { cursor: not-allowed; opacity: .5; }
.lab-flow { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); overflow: hidden; border: 1px solid var(--oj-line); border-radius: var(--oj-radius); background: var(--oj-surface); box-shadow: var(--oj-shadow-soft); }
.lab-flow a { display: flex; align-items: center; justify-content: center; gap: 8px; min-height: 48px; padding: 8px; border-right: 1px solid var(--oj-line); color: var(--oj-ink-soft); font-size: .86rem; font-weight: 800; text-decoration: none; }
.lab-flow a:last-child { border-right: 0; }
.lab-flow a:hover, .lab-flow a:focus-visible, .lab-flow__step--current { background: var(--oj-brand-soft); color: var(--oj-brand) !important; }
.lab-flow span { display: grid; width: 23px; height: 23px; border-radius: 50%; background: rgba(22,66,60,.1); place-items: center; }
.lab-student__detail-layout, .lab-student__submit-layout { display: grid; align-items: start; gap: 16px; }
.lab-student__detail-layout { grid-template-columns: minmax(0,1fr) 310px; }
.lab-student__submit-layout { grid-template-columns: minmax(0,1fr) 330px; }
.lab-student__main-column, .lab-student__submit-aside { display: grid; gap: 16px; min-width: 0; }
.work-surface { min-width: 0; padding: 20px; border: 1px solid var(--oj-line); border-radius: var(--oj-radius); background: var(--oj-surface); box-shadow: var(--oj-shadow-soft); backdrop-filter: var(--oj-blur); }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.section-heading--compact { margin-bottom: 14px; }
.section-heading p, .section-heading h2, .section-heading h3, .section-heading span { margin: 0; }
.section-heading p { color: var(--oj-brand); font-size: .72rem; font-weight: 900; letter-spacing: .08em; text-transform: uppercase; }
.section-heading h2, .section-heading h3 { margin-top: 3px; font-size: 1.12rem; }
.section-heading span { display: block; margin-top: 5px; color: var(--oj-muted); font-size: .82rem; line-height: 1.5; }
.lab-student__description { margin: 0 0 18px; color: var(--oj-ink-soft); line-height: 1.75; white-space: pre-wrap; }
.detail-grid, .compact-context dl { display: grid; gap: 10px; margin: 0; }
.detail-grid { grid-template-columns: repeat(2,minmax(0,1fr)); }
.detail-grid div, .compact-context dl div { padding: 11px 12px; border: 1px solid var(--oj-line); border-radius: var(--oj-radius-control); background: rgba(255,255,255,.5); }
.detail-grid dt, .compact-context dt { color: var(--oj-muted); font-size: .74rem; font-weight: 800; }
.detail-grid dd, .compact-context dd { margin: 4px 0 0; font-weight: 800; overflow-wrap: anywhere; }
.case-list, .lab-student__case-results { display: grid; gap: 10px; margin: 0; padding: 0; list-style: none; }
.case-list > li, .lab-student__case-results li { padding: 13px; border: 1px solid var(--oj-line); border-radius: var(--oj-radius-control); background: rgba(255,255,255,.55); }
.case-list > li > div { display: flex; justify-content: space-between; gap: 12px; }
.case-list > li > div span { color: var(--oj-muted); font-size: .78rem; }
.case-list dl { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 10px; margin: 10px 0 0; }
.case-list dt { color: var(--oj-muted); font-size: .72rem; }
.case-list dd { margin: 4px 0 0; }
.case-list code { display: block; padding: 8px; overflow-x: auto; border-radius: 7px; background: #152420; color: #f4faf8; white-space: pre-wrap; }
.lab-student__next { position: sticky; top: 86px; display: grid; gap: 11px; }
.submission-summary { display: grid; gap: 7px; padding: 15px; border-radius: var(--oj-radius-control); background: var(--oj-brand-soft); }
.submission-summary > span, .submission-summary small { color: var(--oj-ink-soft); }
.submission-summary strong { font-size: 1.35rem; }
.lab-student__form, .lab-student__report, .lab-student__report-form { display: grid; gap: 14px; }
.lab-student__report { margin-top: 22px; padding-top: 20px; border-top: 1px solid var(--oj-line); }
.field { display: grid; gap: 6px; color: var(--oj-ink-soft); font-size: .82rem; font-weight: 800; }
.field input, .field select, .field textarea { box-sizing: border-box; width: 100%; min-height: 42px; padding: 10px 12px; border: 1px solid var(--oj-line-strong); border-radius: var(--oj-radius-control); background: rgba(255,255,255,.86); color: var(--oj-ink); font: inherit; }
.field textarea { resize: vertical; font-family: ui-monospace,SFMono-Regular,Menlo,monospace; line-height: 1.55; tab-size: 2; }
.field input:focus, .field select:focus, .field textarea:focus { outline: 0; border-color: var(--oj-brand); box-shadow: 0 0 0 3px var(--oj-brand-soft); }
.file-picker { display: grid; gap: 5px; }
.file-picker p { margin: 0; color: var(--oj-muted); font-size: .77rem; }
.lab-student__actions { display: flex; flex-wrap: wrap; gap: 9px; }
.lab-student__actions--sticky { position: sticky; bottom: 12px; z-index: 2; padding: 12px; border: 1px solid var(--oj-line); border-radius: var(--oj-radius); background: rgba(250,252,251,.94); box-shadow: var(--oj-shadow-soft); }
.message { margin: 0; padding: 10px 12px; border-radius: var(--oj-radius-control); font-size: .84rem; line-height: 1.55; }
.message--success { background: rgba(22,66,60,.12); color: var(--oj-brand-strong); }
.message--warning { background: rgba(194,123,0,.12); color: #704400; }
.message--error { background: rgba(190,49,49,.11); color: #8f2d24; }
.inline-button { padding: 2px 6px; background: transparent; color: inherit; text-decoration: underline; }
.lab-student__draft-status { margin: -6px 0 0; color: var(--oj-brand); font-size: .8rem; font-weight: 800; }
.lab-student__report-summary { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px; border: 1px solid var(--oj-line); border-radius: var(--oj-radius-control); background: rgba(255,255,255,.55); }
.lab-student__report-summary div { display: grid; gap: 4px; }
.lab-student__report-summary span { color: var(--oj-muted); font-size: .78rem; }
.inline-empty { margin: 0; color: var(--oj-muted); }
.compact-context, .evaluation-preview { display: grid; gap: 10px; }
.evaluation-preview p { margin: 0; color: var(--oj-ink-soft); line-height: 1.5; }
.lab-student__case-results li { display: grid; gap: 4px; font-size: .8rem; }
.text-link { color: var(--oj-brand); font-weight: 800; text-decoration-thickness: 1px; text-underline-offset: 3px; }
@media (max-width: 1024px) {
  .lab-student__detail-layout, .lab-student__submit-layout { grid-template-columns: minmax(0,1fr); }
  .lab-student__next { position: static; }
  .lab-student__submit-aside { grid-template-columns: repeat(2,minmax(0,1fr)); }
}
@media (max-width: 640px) {
  .lab-student { gap: 10px; }
  .lab-student :deep(.foundation-page-header) { padding: 15px; }
  .lab-student :deep(.summary-strip) { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 2px; scroll-snap-type: x proximity; }
  .lab-student :deep(.summary-strip__item) { flex: 0 0 145px; scroll-snap-align: start; }
  .lab-flow { grid-template-columns: repeat(2,minmax(0,1fr)); }
  .lab-flow a:nth-child(2) { border-right: 0; }
  .lab-flow a:nth-child(-n+2) { border-bottom: 1px solid var(--oj-line); }
  .work-surface { padding: 15px; }
  .detail-grid, .case-list dl, .lab-student__submit-aside { grid-template-columns: minmax(0,1fr); }
  .lab-student__actions, .lab-student__actions .button, .lab-student__report-summary { width: 100%; }
  .lab-student__actions { flex-direction: column; }
  .lab-student__report-summary { box-sizing: border-box; align-items: stretch; flex-direction: column; }
}
</style>
