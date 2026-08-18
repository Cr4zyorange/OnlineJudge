<template>
  <main class="lab-task-list" data-testid="lab-task-list">
    <PageHeader
      title="课程实验"
      eyebrow="学生实验任务"
      subtitle="按截止时间查看当前课程的实验、提交进度与成绩状态。"
    >
      <template #actions>
        <a class="primary-link" href="/learning/tasks">查看全部学习任务</a>
      </template>
    </PageHeader>

    <SummaryStrip :items="summaryItems" aria-label="实验任务摘要" />

    <section class="lab-task-list__workspace" aria-label="实验任务列表">
      <header class="lab-task-list__toolbar">
        <label class="lab-task-list__search">
          <span>搜索实验</span>
          <input
            v-model="keyword"
            data-testid="lab-keyword-filter"
            type="search"
            placeholder="输入实验名称"
            @input="page = 1"
          />
        </label>
        <label>
          <span>任务状态</span>
          <select v-model="statusFilter" data-testid="lab-status-filter" @change="page = 1">
            <option value="all">全部状态</option>
            <option value="active">进行中</option>
            <option value="submitted">已有提交</option>
            <option value="graded">成绩已发布</option>
            <option value="closed">已截止</option>
          </select>
        </label>
        <span class="lab-task-list__result-count">{{ filteredCards.length }} 项</span>
      </header>

      <PageState v-if="loading" state="loading" title="正在加载实验" message="同步任务状态与最近提交。" />
      <PageState
        v-else-if="errorMessage"
        state="error"
        title="实验列表加载失败"
        :message="errorMessage"
      >
        <template #actions>
          <button data-testid="retry-lab-list" type="button" @click="loadLabs">重新加载</button>
        </template>
      </PageState>
      <PageState
        v-else-if="filteredCards.length === 0"
        state="empty"
        title="当前筛选下没有实验"
        :message="taskCards.length === 0 ? '课程暂未发布可进入的实验。' : '调整关键词或状态筛选后再试。'"
      >
        <template v-if="keyword || statusFilter !== 'all'" #actions>
          <button type="button" @click="clearFilters">清除筛选</button>
        </template>
      </PageState>

      <ol v-else class="lab-task-list__cards">
        <li v-for="task in pagedCards" :key="task.lab.id" class="lab-task-card">
          <div class="lab-task-card__topline">
            <span>实验 {{ String(task.sequence).padStart(2, '0') }}</span>
            <StatusBadge :label="task.statusLabel" :tone="task.tone" />
          </div>
          <div class="lab-task-card__content">
            <div>
              <h2>{{ task.lab.title }}</h2>
              <p>
                {{ evaluationLabel(task.lab) }} ·
                {{ task.lab.reportRequired ? '需提交实验报告' : '无需实验报告' }} ·
                满分 {{ task.lab.maxScore }}
              </p>
            </div>
            <dl>
              <div>
                <dt>截止时间</dt>
                <dd>{{ formatDateTime(task.lab.deadline) }}</dd>
                <small>{{ task.deadlineHint }}</small>
              </div>
              <div>
                <dt>提交进度</dt>
                <dd>{{ task.progressLabel }}</dd>
                <small>{{ task.submissionHint }}</small>
              </div>
            </dl>
          </div>
          <footer class="lab-task-card__actions">
            <span v-if="task.finalScore !== null">最终成绩 {{ task.finalScore }} 分</span>
            <span v-else>{{ task.nextStep }}</span>
            <a :data-testid="`open-lab-${task.lab.id}`" :href="`/courses/${courseId}/labs/${task.lab.id}`">
              {{ task.actionLabel }}
            </a>
          </footer>
        </li>
      </ol>

      <nav v-if="pageCount > 1" class="lab-task-list__pagination" aria-label="实验列表分页">
        <button type="button" :disabled="page === 1" @click="page -= 1">上一页</button>
        <span>第 {{ page }} / {{ pageCount }} 页</span>
        <button type="button" :disabled="page === pageCount" @click="page += 1">下一页</button>
      </nav>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { listLabs, listLabSubmissions } from '../../api/lab/labs';
import { currentUser } from '../../app/runtimeContext';
import PageHeader from '../../components/foundation/PageHeader.vue';
import PageState from '../../components/foundation/PageState.vue';
import StatusBadge, { type StatusBadgeTone } from '../../components/foundation/StatusBadge.vue';
import SummaryStrip, { type SummaryStripItem } from '../../components/foundation/SummaryStrip.vue';
import type { LabExperimentSummary, LabSubmissionHistoryItem } from '../../types/lab';
import { formatLabEvaluationMode, localizedLabError } from './labDisplay';

interface LabTaskCard {
  lab: LabExperimentSummary;
  sequence: number;
  statusKey: 'active' | 'submitted' | 'graded' | 'closed';
  statusLabel: string;
  tone: StatusBadgeTone;
  progressLabel: string;
  submissionHint: string;
  deadlineHint: string;
  actionLabel: string;
  nextStep: string;
  finalScore: number | null;
}

const props = defineProps<{ courseId: number }>();
const loading = ref(false);
const errorMessage = ref('');
const labs = ref<LabExperimentSummary[]>([]);
const submissionByLab = ref(new Map<number, LabSubmissionHistoryItem[]>());
const keyword = ref('');
const statusFilter = ref('all');
const page = ref(1);
const pageSize = 6;
let loadGeneration = 0;

const availableLabs = computed(() => labs.value.filter((lab) => (
  !lab.deleted && ['PUBLISHED', 'CLOSED', 'SCORE_PUBLISHED', 'ARCHIVED'].includes(lab.status)
)));
const taskCards = computed<LabTaskCard[]>(() => availableLabs.value
  .map((lab, index) => toTaskCard(lab, index + 1, submissionByLab.value.get(lab.id) ?? []))
  .sort((left, right) => new Date(left.lab.deadline).getTime() - new Date(right.lab.deadline).getTime()));
const filteredCards = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return taskCards.value.filter((task) => (
    (!query || task.lab.title.toLowerCase().includes(query))
    && (statusFilter.value === 'all' || task.statusKey === statusFilter.value)
  ));
});
const pageCount = computed(() => Math.max(1, Math.ceil(filteredCards.value.length / pageSize)));
const pagedCards = computed(() => filteredCards.value.slice((page.value - 1) * pageSize, page.value * pageSize));
const dueSoonCount = computed(() => taskCards.value.filter((task) => {
  const remaining = new Date(task.lab.deadline).getTime() - Date.now();
  return remaining > 0 && remaining <= 72 * 60 * 60 * 1000;
}).length);
const submittedCount = computed(() => taskCards.value.filter((task) => (
  (submissionByLab.value.get(task.lab.id)?.length ?? 0) > 0
)).length);
const gradedCount = computed(() => taskCards.value.filter((task) => task.statusKey === 'graded').length);
const summaryItems = computed<SummaryStripItem[]>(() => [
  { key: 'available', label: '可进入实验', value: taskCards.value.length, hint: `${taskCards.value.length} 个可进入实验`, tone: 'brand' },
  { key: 'due', label: '72 小时内截止', value: dueSoonCount.value, hint: '优先处理临近任务', tone: dueSoonCount.value ? 'warning' : 'neutral' },
  { key: 'submitted', label: '已有提交', value: submittedCount.value, hint: '含评测中版本' },
  { key: 'graded', label: '成绩可查看', value: gradedCount.value, hint: '以发布状态为准', tone: 'success' }
]);

watch(
  () => props.courseId,
  () => {
    page.value = 1;
    labs.value = [];
    submissionByLab.value = new Map();
    void loadLabs();
  },
  { immediate: true }
);
onBeforeUnmount(() => { loadGeneration += 1; });

async function loadLabs() {
  const generation = ++loadGeneration;
  const requestedCourseId = props.courseId;
  const requestedStudentId = currentUser.value?.id;
  loading.value = true;
  errorMessage.value = '';
  try {
    if (requestedStudentId === undefined || requestedStudentId === null) {
      throw new Error('无法确认当前学生身份，请重新登录后重试。');
    }
    const loadedLabs = await listLabs(requestedCourseId);
    if (!isCurrentLoad(generation, requestedCourseId)) return;
    if (loadedLabs.some((lab) => lab.courseId !== requestedCourseId)) {
      throw new Error('实验列表与当前课程不匹配，请重新加载。');
    }
    labs.value = loadedLabs;
    const visible = loadedLabs.filter((lab) => (
      !lab.deleted && ['PUBLISHED', 'CLOSED', 'SCORE_PUBLISHED', 'ARCHIVED'].includes(lab.status)
    ));
    const histories = await Promise.allSettled(visible.map((lab) => listLabSubmissions(lab.id)));
    if (!isCurrentLoad(generation, requestedCourseId)) return;
    if (histories.some((history) => history.status === 'rejected')) {
      throw new Error('提交记录同步失败，请重新加载后重试。');
    }
    if (currentUser.value?.id !== requestedStudentId
      || visible.some((lab, index) => {
        const history = histories[index];
        return history.status === 'fulfilled'
          && history.value.some((item) => item.labId !== lab.id || item.studentId !== requestedStudentId);
      })) {
      throw new Error('提交记录与当前实验或学生不匹配，请重新加载。');
    }
    submissionByLab.value = new Map(visible.map((lab, index) => {
      const history = histories[index];
      return [lab.id, history.status === 'fulfilled' ? history.value : []];
    }));
  } catch (error) {
    if (!isCurrentLoad(generation, requestedCourseId)) return;
    labs.value = [];
    submissionByLab.value = new Map();
    errorMessage.value = localizedLabError(error, '实验列表加载失败，请稍后重试。');
  } finally {
    if (isCurrentLoad(generation, requestedCourseId)) loading.value = false;
  }
}

function toTaskCard(lab: LabExperimentSummary, sequence: number, submissions: LabSubmissionHistoryItem[]): LabTaskCard {
  const latest = submissions.find((item) => item.isLatest) ?? submissions[0];
  const scoringBasis = submissions.find((item) => item.isScoringBasis)
    ?? submissions.find((item) => item.isFinal)
    ?? latest;
  const scoresPublished = lab.status === 'SCORE_PUBLISHED' || lab.status === 'ARCHIVED';
  const finalScore = scoresPublished ? scoringBasis?.finalScore ?? null : null;
  const overdue = new Date(lab.deadline).getTime() < Date.now();
  if (scoresPublished) {
    return task(lab, sequence, 'graded', lab.status === 'ARCHIVED' ? '已归档' : '成绩已发布', 'success', scoringBasis ? '已提交' : '未找到提交', scoringBasis ? `第 ${scoringBasis.version} 版作为评分依据` : '如有疑问请联系教师', '提交阶段已结束', '查看成绩', '查看评测反馈', finalScore);
  }
  if (lab.status === 'CLOSED' || overdue) {
    return task(lab, sequence, 'closed', overdue ? '已逾期' : '已截止', 'warning', latest ? '已提交' : '未提交', latest ? `最近为第 ${latest.version} 版` : '当前不可继续提交', '截止时间已过', latest ? '查看提交' : '查看说明', latest ? '等待成绩发布' : '提交入口已关闭', finalScore);
  }
  if (latest) {
    const evaluating = ['PENDING', 'RUNNING'].includes(latest.evaluationStatus);
    return task(lab, sequence, 'submitted', evaluating ? '评测中' : '已有提交', 'info', evaluating ? '等待评测' : '已提交', `最近为第 ${latest.version} 版`, remainingTime(lab.deadline), '继续实验', lab.reportRequired ? '请在实验详情核对报告状态' : '可在截止前更新版本', finalScore);
  }
  return task(lab, sequence, 'active', '进行中', 'info', '尚未提交', '进入实验后开始作答', remainingTime(lab.deadline), '开始实验', '下一步：阅读说明并提交', null);
}

function isCurrentLoad(generation: number, courseId: number) {
  return generation === loadGeneration && courseId === props.courseId;
}

function task(lab: LabExperimentSummary, sequence: number, statusKey: LabTaskCard['statusKey'], statusLabel: string, tone: StatusBadgeTone, progressLabel: string, submissionHint: string, deadlineHint: string, actionLabel: string, nextStep: string, finalScore: number | null): LabTaskCard {
  return { lab, sequence, statusKey, statusLabel, tone, progressLabel, submissionHint, deadlineHint, actionLabel, nextStep, finalScore };
}

function remainingTime(value: string) {
  const hours = Math.max(0, Math.ceil((new Date(value).getTime() - Date.now()) / 3_600_000));
  return hours < 24 ? `剩余约 ${hours} 小时` : `剩余约 ${Math.ceil(hours / 24)} 天`;
}

function evaluationLabel(lab: LabExperimentSummary) {
  return formatLabEvaluationMode(lab.evaluationMode);
}

function formatDateTime(value: string) {
  return value.replace('T', ' ').slice(0, 16);
}

function clearFilters() {
  keyword.value = '';
  statusFilter.value = 'all';
  page.value = 1;
}
</script>

<style scoped>
.lab-task-list { display: grid; gap: 16px; min-height: 100vh; padding-bottom: 40px; color: var(--oj-ink); }
.primary-link,
.lab-task-card__actions a,
.lab-task-list button { border: 0; border-radius: var(--oj-radius-control); background: var(--oj-brand); color: #fff; font: inherit; font-weight: 800; padding: 10px 14px; text-decoration: none; }
.lab-task-list__workspace { padding: 20px; border: 1px solid var(--oj-line); border-radius: var(--oj-radius); background: var(--oj-surface); box-shadow: var(--oj-shadow-soft); backdrop-filter: var(--oj-blur); }
.lab-task-list__toolbar { display: flex; align-items: end; gap: 10px; margin-bottom: 16px; }
.lab-task-list__toolbar label { display: grid; gap: 5px; color: var(--oj-ink-soft); font-size: .78rem; font-weight: 800; }
.lab-task-list__toolbar input,
.lab-task-list__toolbar select { box-sizing: border-box; min-height: 42px; border: 1px solid var(--oj-line-strong); border-radius: var(--oj-radius-control); background: #fff; color: var(--oj-ink); font: inherit; padding: 9px 11px; }
.lab-task-list__search { flex: 1; }
.lab-task-list__search input { width: 100%; }
.lab-task-list__result-count { margin-left: auto; padding-bottom: 11px; color: var(--oj-muted); white-space: nowrap; }
.lab-task-list__cards { display: grid; gap: 12px; margin: 0; padding: 0; list-style: none; }
.lab-task-card { overflow: hidden; border: 1px solid var(--oj-line); border-radius: var(--oj-radius); background: rgba(255,255,255,.76); transition: transform 160ms ease, box-shadow 160ms ease; }
.lab-task-card:hover { transform: translateY(-2px); box-shadow: var(--oj-shadow); }
.lab-task-card__topline,
.lab-task-card__actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.lab-task-card__topline { padding: 12px 16px 0; color: var(--oj-muted); font-size: .78rem; font-weight: 800; }
.lab-task-card__content { display: grid; grid-template-columns: minmax(0,1.2fr) minmax(320px,.8fr); gap: 20px; padding: 12px 16px 16px; }
.lab-task-card h2,
.lab-task-card p,
.lab-task-card dl,
.lab-task-card dd { margin: 0; }
.lab-task-card h2 { margin-bottom: 6px; font-size: 1.15rem; }
.lab-task-card p,
.lab-task-card dt,
.lab-task-card small { color: var(--oj-ink-soft); }
.lab-task-card dl { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12px; }
.lab-task-card dl div { padding-left: 12px; border-left: 2px solid var(--oj-line); }
.lab-task-card dt,
.lab-task-card small { display: block; font-size: .76rem; }
.lab-task-card dd { margin: 3px 0; font-weight: 800; }
.lab-task-card__actions { padding: 12px 16px; background: var(--oj-brand-soft); color: var(--oj-ink-soft); font-size: .88rem; font-weight: 700; }
.lab-task-list__pagination { display: flex; justify-content: center; align-items: center; gap: 12px; margin-top: 18px; }
.lab-task-list__pagination button:disabled { opacity: .45; }
@media (max-width: 760px) {
  .lab-task-list { gap: 10px; }
  .lab-task-list :deep(.foundation-page-header) { gap: 10px; padding: 14px; }
  .lab-task-list :deep(.foundation-page-header__actions) { display: none; }
  .lab-task-list :deep(.foundation-page-header__subtitle) { margin-top: 6px; font-size: .84rem; line-height: 1.5; }
  .lab-task-list :deep(.summary-strip) { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 2px; scroll-snap-type: x proximity; }
  .lab-task-list :deep(.summary-strip__item) { flex: 0 0 135px; padding: 10px 11px; scroll-snap-align: start; }
  .lab-task-list__workspace { padding: 14px; }
  .lab-task-list__toolbar { display: grid; grid-template-columns: minmax(0,1fr) 128px; align-items: center; margin-bottom: 10px; }
  .lab-task-list__toolbar label { gap: 0; }
  .lab-task-list__toolbar label > span { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0,0,0,0); }
  .lab-task-list__result-count { display: none; }
  .lab-task-card__content,
  .lab-task-card dl { grid-template-columns: minmax(0,1fr); }
  .lab-task-card__content { gap: 14px; }
  .lab-task-card__actions { align-items: stretch; flex-direction: column; }
  .lab-task-card__actions a { text-align: center; }
}
</style>
