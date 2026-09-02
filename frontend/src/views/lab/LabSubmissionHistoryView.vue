<template>
  <main class="lab-history" data-testid="lab-submission-history">
    <PageHeader
      :title="experiment?.title ?? '实验提交历史'"
      eyebrow="实验提交记录"
      subtitle="按版本核对提交内容、评测状态与当前评分依据。"
    >
      <template #meta>
        <template v-if="experiment">
          <span>截止时间：{{ formatLabDateTime(experiment.deadline) }}</span>
          <StatusBadge
            :label="formatLabExperimentStatus(experiment.status)"
            :tone="labExperimentStatusTone(experiment.status)"
          />
          <StatusBadge
            :label="formatLabEvaluationMode(experiment.evaluationMode)"
            tone="info"
          />
        </template>
        <span v-else-if="experimentLoading">正在同步实验信息</span>
      </template>
      <template #actions>
        <RouterLink
          class="lab-history__header-link"
          :to="{ name: 'lab-detail', params: { courseId, labId } }"
        >
          返回实验详情
        </RouterLink>
      </template>
    </PageHeader>

    <div v-if="experimentErrorMessage" class="lab-history__context-error" role="alert">
      <span>{{ experimentErrorMessage }}</span>
      <button type="button" @click="loadExperiment">重试实验信息</button>
    </div>

    <SummaryStrip :items="summaryItems" aria-label="提交历史摘要" />

    <section class="lab-history__workspace" aria-label="提交历史工作区">
      <PageState
        v-if="loading"
        class="lab-history__list-state"
        state="loading"
        title="正在加载提交历史"
        message="正在同步所有提交版本与评分依据。"
      />
      <PageState
        v-else-if="errorMessage"
        class="lab-history__list-state"
        state="error"
        title="提交历史加载失败"
        :message="errorMessage"
        retry-label="重新加载"
        @retry="loadHistory"
      />
      <PageState
        v-else-if="history.length === 0"
        class="lab-history__list-state"
        state="empty"
        title="还没有提交记录"
        message="第一次提交后，每个版本都会保留在这里。"
      >
        <template #actions>
          <RouterLink
            class="lab-history__primary-link"
            data-testid="history-empty-submit"
            :to="{ name: 'lab-submit', params: { courseId, labId } }"
          >
            去提交实验
          </RouterLink>
        </template>
      </PageState>

      <div v-else class="lab-history__content">
        <section class="lab-history__versions" aria-label="提交版本列表">
          <header class="lab-history__section-header">
            <div>
              <p>版本记录</p>
              <h2>我的全部提交</h2>
            </div>
            <span>{{ history.length }} 个版本</span>
          </header>

          <ol class="lab-history__version-list">
            <li
              v-for="item in history"
              :key="item.submissionId"
              class="lab-history__version"
              :class="{
                'lab-history__version--selected': selectedSubmissionId === item.submissionId
              }"
              :data-submission-id="item.submissionId"
            >
              <button
                type="button"
                class="lab-history__version-button"
                :data-testid="`history-select-${item.submissionId}`"
                :aria-pressed="selectedSubmissionId === item.submissionId"
                :aria-label="`查看版本 ${item.version} 的提交内容`"
                @click="openDetail(item.submissionId)"
              >
                <span class="lab-history__version-heading">
                  <strong>版本 {{ item.version }}</strong>
                  <time :datetime="item.submittedAt">{{ formatLabDateTime(item.submittedAt) }}</time>
                </span>

                <span class="lab-history__badges">
                  <StatusBadge
                    :label="formatLabSubmitStatus(item.submitStatus)"
                    :tone="labSubmitStatusTone(item.submitStatus)"
                  />
                  <StatusBadge
                    :label="formatLabEvaluationStatus(item.evaluationStatus)"
                    :tone="labEvaluationStatusTone(item.evaluationStatus)"
                  />
                </span>

                <span class="lab-history__version-facts">
                  <span><small>语言</small>{{ formatLabLanguage(item.language) }}</span>
                  <span><small>自动评测</small>{{ formatLabScore(item.autoScore) }}</span>
                  <span><small>最终得分</small>{{ formatVisibleFinalScore(item.finalScore) }}</span>
                </span>

                <span class="lab-history__markers">
                  <StatusBadge v-if="item.isLatest" label="最新版本" tone="brand" />
                  <StatusBadge v-if="item.isFinal" label="当前有效版本" tone="success" />
                  <StatusBadge v-if="item.isScoringBasis" label="当前评分依据" tone="warning" />
                  <StatusBadge v-if="item.hasFile" label="包含提交文件" tone="neutral" />
                </span>
              </button>

              <RouterLink
                class="lab-history__result-link"
                :data-testid="`history-result-${item.submissionId}`"
                :to="{
                  name: 'lab-submission-result',
                  params: { courseId, labId, submissionId: item.submissionId }
                }"
              >
                查看本次结果
              </RouterLink>
            </li>
          </ol>
        </section>

        <aside class="lab-history__detail" aria-label="所选提交详情" aria-live="polite">
          <PageState
            v-if="detailLoading"
            class="lab-history__detail-state"
            state="loading"
            title="正在加载提交内容"
            message="正在读取所选版本保存的代码与提交信息。"
          />
          <PageState
            v-else-if="detailErrorMessage"
            class="lab-history__detail-state"
            state="error"
            title="提交内容加载失败"
            :message="detailErrorMessage"
            retry-label="重试"
            @retry="retrySelectedDetail"
          />
          <PageState
            v-else-if="detail === null"
            class="lab-history__detail-state"
            state="empty"
            title="请选择一个版本"
            message="从左侧版本列表选择一条记录查看提交内容。"
          />
          <article v-else class="lab-history__detail-card">
            <header class="lab-history__detail-header">
              <div>
                <p>所选提交</p>
                <h2>版本 {{ detail.version }}</h2>
              </div>
              <div class="lab-history__badges">
                <StatusBadge
                  :label="formatLabSubmitStatus(detail.submitStatus)"
                  :tone="labSubmitStatusTone(detail.submitStatus)"
                />
                <StatusBadge
                  :label="formatLabEvaluationStatus(detail.evaluationStatus)"
                  :tone="labEvaluationStatusTone(detail.evaluationStatus)"
                />
              </div>
            </header>

            <div class="lab-history__markers">
              <StatusBadge v-if="detail.isLatest" label="最新版本" tone="brand" />
              <StatusBadge v-if="detail.isFinal" label="当前有效版本" tone="success" />
              <StatusBadge v-if="detail.isScoringBasis" label="当前评分依据" tone="warning" />
            </div>

            <dl class="lab-history__detail-facts">
              <div><dt>程序语言</dt><dd>{{ formatLabLanguage(detail.language) }}</dd></div>
              <div><dt>提交时间</dt><dd>{{ formatLabDateTime(detail.submittedAt) }}</dd></div>
              <div><dt>自动评测分</dt><dd>{{ formatLabScore(detail.autoScore) }}</dd></div>
              <div><dt>最终得分</dt><dd>{{ formatVisibleFinalScore(detail.finalScore) }}</dd></div>
              <div>
                <dt>提交文件</dt>
                <dd>{{ detail.hasFile ? '已随本版本保存' : '未包含独立文件' }}</dd>
              </div>
              <div>
                <dt>实验报告</dt>
                <dd>{{ detail.latestReport?.fileName ?? '未上传报告' }}</dd>
              </div>
            </dl>

            <section class="lab-history__code-panel" aria-label="提交代码">
              <header>
                <h3>提交代码</h3>
                <span>{{ formatLabLanguage(detail.language) }}</span>
              </header>
              <pre>{{ detail.code || '本次提交未包含可展示的在线代码' }}</pre>
            </section>

            <RouterLink
              class="lab-history__primary-link"
              :to="{
                name: 'lab-submission-result',
                params: { courseId, labId, submissionId: detail.submissionId }
              }"
            >
              查看该版本评测结果
            </RouterLink>
          </article>
        </aside>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import { getLabDetail, getLabSubmissionDetail, listLabSubmissions } from '../../api/lab/labs';
import { currentUser } from '../../app/runtimeContext';
import PageHeader from '../../components/foundation/PageHeader.vue';
import PageState from '../../components/foundation/PageState.vue';
import StatusBadge from '../../components/foundation/StatusBadge.vue';
import SummaryStrip, { type SummaryStripItem } from '../../components/foundation/SummaryStrip.vue';
import { labStudentIdsMatch } from '../../types/lab';
import type {
  LabExperimentDetail,
  LabSubmissionDetail,
  LabSubmissionHistoryItem,
  LabSubmissionId
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
  labSubmitStatusTone,
  localizedLabError
} from './labDisplay';

const props = defineProps<{
  courseId: number;
  labId: number;
}>();

const experiment = ref<LabExperimentDetail | null>(null);
const history = ref<LabSubmissionHistoryItem[]>([]);
const detail = ref<LabSubmissionDetail | null>(null);
const selectedSubmissionId = ref<LabSubmissionId | null>(null);
const experimentLoading = ref(false);
const loading = ref(false);
const detailLoading = ref(false);
const experimentErrorMessage = ref('');
const errorMessage = ref('');
const detailErrorMessage = ref('');

let contextVersion = 0;
let experimentRequestVersion = 0;
let historyRequestVersion = 0;
let detailRequestVersion = 0;

const latestSubmission = computed(() => (
  history.value.find((item) => item.isLatest) ?? history.value[0] ?? null
));
const effectiveSubmission = computed(() => (
  history.value.find((item) => item.isFinal) ?? null
));
const scoringBasisSubmission = computed(() => (
  history.value.find((item) => item.isScoringBasis) ?? null
));
const scoresPublished = computed(() => (
  experiment.value?.status === 'SCORE_PUBLISHED' || experiment.value?.status === 'ARCHIVED'
));
const summaryItems = computed<SummaryStripItem[]>(() => [
  {
    key: 'total',
    label: '已提交版本',
    value: history.value.length,
    hint: '所有版本均会保留',
    tone: history.value.length > 0 ? 'brand' : 'neutral'
  },
  {
    key: 'latest',
    label: '最新版本',
    value: latestSubmission.value ? `版本 ${latestSubmission.value.version}` : '—',
    hint: '最近一次提交'
  },
  {
    key: 'effective',
    label: '当前有效版本',
    value: effectiveSubmission.value ? `版本 ${effectiveSubmission.value.version}` : '—',
    hint: '当前生效的提交',
    tone: effectiveSubmission.value ? 'success' : 'neutral'
  },
  {
    key: 'scoring',
    label: '当前评分依据',
    value: scoringBasisSubmission.value ? `版本 ${scoringBasisSubmission.value.version}` : '—',
    hint: '最终成绩采用的版本',
    tone: scoringBasisSubmission.value ? 'warning' : 'neutral'
  }
]);

onMounted(resetContextAndReload);
watch(
  () => [props.courseId, props.labId] as const,
  resetContextAndReload
);
onBeforeUnmount(invalidateContext);

function resetContextAndReload() {
  invalidateContext();
  experiment.value = null;
  history.value = [];
  detail.value = null;
  selectedSubmissionId.value = null;
  experimentLoading.value = false;
  loading.value = true;
  detailLoading.value = false;
  experimentErrorMessage.value = '';
  errorMessage.value = '';
  detailErrorMessage.value = '';
  void loadExperiment();
}

function invalidateContext() {
  contextVersion += 1;
  experimentRequestVersion += 1;
  historyRequestVersion += 1;
  detailRequestVersion += 1;
}

async function loadExperiment() {
  const context = contextVersion;
  const request = ++experimentRequestVersion;
  const labId = props.labId;
  const courseId = props.courseId;
  experimentLoading.value = true;
  loading.value = true;
  experimentErrorMessage.value = '';
  try {
    const result = await getLabDetail(labId);
    if (!isCurrentRequest(context, request, experimentRequestVersion)) {
      return;
    }
    if (result.id !== labId || result.courseId !== courseId) {
      throw new Error('实验信息与当前课程不匹配，请重新加载。');
    }
    experiment.value = result;
    void loadHistory();
  } catch (error) {
    if (isCurrentRequest(context, request, experimentRequestVersion)) {
      historyRequestVersion += 1;
      detailRequestVersion += 1;
      experiment.value = null;
      history.value = [];
      detail.value = null;
      selectedSubmissionId.value = null;
      loading.value = false;
      detailLoading.value = false;
      errorMessage.value = '';
      detailErrorMessage.value = '';
      experimentErrorMessage.value = localizedLabError(error, '实验信息加载失败，请稍后重试');
    }
  } finally {
    if (isCurrentRequest(context, request, experimentRequestVersion)) {
      experimentLoading.value = false;
    }
  }
}

async function loadHistory() {
  const context = contextVersion;
  const request = ++historyRequestVersion;
  const labId = props.labId;
  const studentId = currentUser.value?.id;
  detailRequestVersion += 1;
  loading.value = true;
  errorMessage.value = '';
  detail.value = null;
  selectedSubmissionId.value = null;
  detailLoading.value = false;
  detailErrorMessage.value = '';
  try {
    if (experiment.value?.id !== labId || experiment.value.courseId !== props.courseId) {
      throw new Error('请先重新加载并验证实验信息。');
    }
    if (studentId === undefined || studentId === null) {
      throw new Error('无法确认当前学生身份，请重新登录后重试。');
    }
    const result = await listLabSubmissions(labId);
    if (!isCurrentRequest(context, request, historyRequestVersion)) {
      return;
    }
    if (!labStudentIdsMatch(currentUser.value?.id, studentId)
      || result.some((item) => item.labId !== labId || !labStudentIdsMatch(item.studentId, studentId))) {
      throw new Error('提交历史与当前实验或学生不匹配，请重新加载。');
    }
    history.value = result.slice().sort(compareSubmissions);
    const preferred = history.value.find((item) => item.isFinal)
      ?? history.value.find((item) => item.isLatest)
      ?? history.value[0];
    if (preferred) {
      void openDetail(preferred.submissionId);
    }
  } catch (error) {
    if (isCurrentRequest(context, request, historyRequestVersion)) {
      history.value = [];
      errorMessage.value = localizedLabError(error, '提交历史加载失败，请稍后重试');
    }
  } finally {
    if (isCurrentRequest(context, request, historyRequestVersion)) {
      loading.value = false;
    }
  }
}

async function openDetail(submissionId: LabSubmissionId) {
  const context = contextVersion;
  const request = ++detailRequestVersion;
  const labId = props.labId;
  const studentId = currentUser.value?.id;
  selectedSubmissionId.value = submissionId;
  detail.value = null;
  detailLoading.value = true;
  detailErrorMessage.value = '';
  try {
    const result = await getLabSubmissionDetail(labId, submissionId);
    if (!isCurrentDetailRequest(context, request, submissionId)) {
      return;
    }
    if (result.labId !== labId
      || result.submissionId !== submissionId
      || studentId === undefined
      || studentId === null
      || !labStudentIdsMatch(currentUser.value?.id, studentId)
      || !labStudentIdsMatch(result.studentId, studentId)) {
      throw new Error('提交内容与所选版本不匹配，请重新加载。');
    }
    detail.value = result;
  } catch (error) {
    if (isCurrentDetailRequest(context, request, submissionId)) {
      detailErrorMessage.value = localizedLabError(error, '提交内容加载失败，请稍后重试');
    }
  } finally {
    if (isCurrentDetailRequest(context, request, submissionId)) {
      detailLoading.value = false;
    }
  }
}

function retrySelectedDetail() {
  if (selectedSubmissionId.value !== null) {
    void openDetail(selectedSubmissionId.value);
  }
}

function formatVisibleFinalScore(score: number | null) {
  if (!scoresPublished.value) return '待发布';
  return score === null ? '未评分' : formatLabScore(score);
}

function isCurrentRequest(context: number, request: number, latestRequest: number) {
  return context === contextVersion && request === latestRequest;
}

function isCurrentDetailRequest(context: number, request: number, submissionId: LabSubmissionId) {
  return isCurrentRequest(context, request, detailRequestVersion)
    && selectedSubmissionId.value === submissionId;
}

function compareSubmissions(left: LabSubmissionHistoryItem, right: LabSubmissionHistoryItem) {
  if (left.version !== right.version) {
    return right.version - left.version;
  }
  return new Date(right.submittedAt).getTime() - new Date(left.submittedAt).getTime();
}
</script>

<style scoped>
.lab-history {
  display: grid;
  gap: 16px;
  min-width: 0;
  color: var(--oj-ink);
}

.lab-history__header-link,
.lab-history__primary-link,
.lab-history__result-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  border-radius: var(--oj-radius);
  font-weight: 800;
  text-decoration: none;
}

.lab-history__header-link,
.lab-history__result-link {
  border: 1px solid var(--oj-line-strong);
  background: rgba(255, 255, 255, 0.58);
  color: var(--oj-brand);
}

.lab-history__header-link {
  padding: 8px 16px;
}

.lab-history__primary-link {
  padding: 9px 18px;
  background: var(--oj-brand);
  color: #fff;
}

.lab-history__context-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid rgba(157, 47, 34, 0.2);
  border-radius: var(--oj-radius);
  background: rgba(248, 239, 238, 0.78);
  color: #8f2d24;
  font-weight: 700;
}

.lab-history__context-error button {
  min-height: 36px;
  padding: 7px 12px;
  border: 1px solid currentColor;
  border-radius: var(--oj-radius);
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-weight: 800;
}

.lab-history__workspace,
.lab-history__versions,
.lab-history__detail {
  min-width: 0;
}

.lab-history__content {
  display: grid;
  grid-template-columns: minmax(330px, 0.9fr) minmax(0, 1.25fr);
  align-items: start;
  gap: 16px;
}

.lab-history__versions,
.lab-history__detail {
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  box-shadow: var(--oj-shadow-soft);
  backdrop-filter: var(--oj-blur);
  -webkit-backdrop-filter: var(--oj-blur);
}

.lab-history__versions {
  padding: 18px;
}

.lab-history__detail {
  position: sticky;
  top: 16px;
  overflow: hidden;
}

.lab-history__section-header,
.lab-history__detail-header,
.lab-history__version-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.lab-history__section-header {
  padding: 2px 2px 14px;
  border-bottom: 1px solid var(--oj-line);
}

.lab-history__section-header p,
.lab-history__section-header h2,
.lab-history__detail-header p,
.lab-history__detail-header h2 {
  margin: 0;
}

.lab-history__section-header p,
.lab-history__detail-header p {
  color: var(--oj-brand);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.lab-history__section-header h2,
.lab-history__detail-header h2 {
  margin-top: 3px;
  font-size: 1.22rem;
}

.lab-history__section-header > span {
  color: var(--oj-muted);
  font-size: 0.82rem;
  font-weight: 700;
}

.lab-history__version-list {
  display: grid;
  gap: 12px;
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
}

.lab-history__version {
  overflow: hidden;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: rgba(255, 255, 255, 0.48);
  transition: border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
}

.lab-history__version:hover,
.lab-history__version--selected {
  border-color: var(--oj-brand);
  box-shadow: 0 10px 24px rgba(22, 66, 60, 0.09);
}

.lab-history__version--selected {
  transform: translateY(-1px);
  background: rgba(241, 247, 245, 0.86);
}

.lab-history__version-button {
  display: grid;
  gap: 12px;
  width: 100%;
  padding: 15px;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.lab-history__version-button:focus-visible,
.lab-history__header-link:focus-visible,
.lab-history__primary-link:focus-visible,
.lab-history__result-link:focus-visible,
.lab-history__context-error button:focus-visible {
  outline: 3px solid var(--oj-brand-soft);
  outline-offset: 2px;
}

.lab-history__version-heading strong {
  font-size: 1rem;
}

.lab-history__version-heading time {
  color: var(--oj-muted);
  font-size: 0.76rem;
  white-space: nowrap;
}

.lab-history__badges,
.lab-history__markers {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.lab-history__version-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.lab-history__version-facts > span {
  display: grid;
  gap: 2px;
  min-width: 0;
  color: var(--oj-ink);
  font-size: 0.82rem;
  font-weight: 800;
}

.lab-history__version-facts small {
  color: var(--oj-muted);
  font-size: 0.7rem;
  font-weight: 700;
}

.lab-history__result-link {
  min-height: 38px;
  margin: 0 15px 15px;
  padding: 7px 12px;
  font-size: 0.82rem;
}

.lab-history__detail-state {
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

.lab-history__detail-card {
  display: grid;
  gap: 18px;
  padding: 22px;
}

.lab-history__detail-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 0;
}

.lab-history__detail-facts div {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--oj-line);
  border-radius: calc(var(--oj-radius) - 4px);
  background: rgba(255, 255, 255, 0.45);
}

.lab-history__detail-facts dt {
  color: var(--oj-muted);
  font-size: 0.72rem;
  font-weight: 800;
}

.lab-history__detail-facts dd {
  margin: 5px 0 0;
  color: var(--oj-ink);
  font-size: 0.9rem;
  font-weight: 800;
  overflow-wrap: anywhere;
}

.lab-history__code-panel {
  overflow: hidden;
  border-radius: var(--oj-radius);
  background: #10201d;
  color: #eff8f5;
}

.lab-history__code-panel header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}

.lab-history__code-panel h3 {
  margin: 0;
  font-size: 0.86rem;
}

.lab-history__code-panel header span {
  color: rgba(239, 248, 245, 0.72);
  font-size: 0.74rem;
  font-weight: 700;
}

.lab-history__code-panel pre {
  max-height: 420px;
  margin: 0;
  padding: 16px;
  overflow: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.84rem;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 900px) {
  .lab-history__content {
    grid-template-columns: minmax(0, 1fr);
  }

  .lab-history__detail {
    position: static;
  }
}

@media (max-width: 640px) {
  .lab-history {
    gap: 12px;
  }

  .lab-history__context-error,
  .lab-history__section-header,
  .lab-history__detail-header {
    align-items: stretch;
    flex-direction: column;
  }

  .lab-history__versions {
    padding: 14px;
  }

  .lab-history__version-facts,
  .lab-history__detail-facts {
    grid-template-columns: minmax(0, 1fr);
  }

  .lab-history__detail-card {
    padding: 16px;
  }

  .lab-history__version-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .lab-history__version-heading time {
    white-space: normal;
  }
}

@media (prefers-reduced-motion: reduce) {
  .lab-history__version {
    transition: none;
  }
}
</style>
