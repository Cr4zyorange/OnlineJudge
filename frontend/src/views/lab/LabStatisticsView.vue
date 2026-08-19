<template>
  <main class="lab-statistics" aria-labelledby="lab-statistics-title">
    <header class="lab-statistics__hero">
      <div>
        <p class="lab-statistics__eyebrow">实验统计</p>
        <h1 id="lab-statistics-title">{{ detail?.title ?? '实验完成情况' }}</h1>
        <p class="lab-statistics__intro">
          汇总提交、评测和成绩数据，快速找到需要跟进的学生。
        </p>
      </div>

      <nav class="lab-statistics__actions" aria-label="实验统计操作">
        <RouterLink class="button button--quiet" :to="teacherDetailRoute">返回实验详情</RouterLink>
        <RouterLink class="button button--secondary" :to="submissionWorkspaceRoute">查看提交队列</RouterLink>
        <button
          v-if="detail && statistics"
          class="button button--primary"
          data-action="refresh-statistics"
          type="button"
          :disabled="loading"
          @click="loadPage"
        >
          {{ loading ? '正在刷新…' : '刷新数据' }}
        </button>
      </nav>
    </header>

    <section v-if="loading" class="state-panel" aria-live="polite">
      <span class="state-panel__spinner" aria-hidden="true" />
      <div>
        <strong>正在加载实验统计…</strong>
        <p>正在同步实验、提交与课程学生信息。</p>
      </div>
    </section>

    <section v-else-if="errorMessage" class="state-panel state-panel--error" role="alert">
      <div>
        <strong>实验统计暂时无法加载</strong>
        <p>{{ errorMessage }}</p>
      </div>
      <button
        class="button button--secondary"
        data-action="retry-statistics"
        type="button"
        @click="loadPage"
      >
        重新加载
      </button>
    </section>

    <template v-else-if="detail && statistics">
      <section class="experiment-strip" aria-label="实验概要">
        <div>
          <span>实验状态</span>
          <StatusBadge
            :label="formatLabExperimentStatus(detail.status)"
            :tone="labExperimentStatusTone(detail.status)"
          />
        </div>
        <div>
          <span>满分</span>
          <strong>{{ formatNumber(detail.maxScore) }} 分</strong>
        </div>
        <div>
          <span>截止时间</span>
          <strong>{{ formatLabDateTime(detail.deadline) }}</strong>
        </div>
        <div>
          <span>数据更新于</span>
          <strong>{{ formatLabDateTime(statistics.generatedAt) }}</strong>
        </div>
      </section>

      <section class="summary-grid" aria-label="实验统计概览">
        <article class="summary-card" data-testid="summary-total">
          <span>课程学生</span>
          <strong>{{ statistics.totalStudentCount }}</strong>
          <small>人</small>
        </article>
        <article class="summary-card summary-card--success" data-testid="summary-submitted">
          <span>已提交</span>
          <strong>{{ statistics.submittedCount }}</strong>
          <small>人</small>
        </article>
        <article class="summary-card summary-card--warning" data-testid="summary-unsubmitted">
          <span>未提交</span>
          <strong>{{ statistics.unsubmittedCount }}</strong>
          <small>人</small>
        </article>
        <article class="summary-card" data-testid="summary-evaluated">
          <span>已完成评测</span>
          <strong>{{ statistics.evaluatedCount }}</strong>
          <small>人</small>
        </article>
        <article class="summary-card" data-testid="summary-submission-rate">
          <span>提交率</span>
          <strong>{{ formatPercentage(statistics.submissionRate) }}</strong>
        </article>
        <article class="summary-card" data-testid="summary-evaluation-rate">
          <span>评测完成率</span>
          <strong>{{ formatPercentage(statistics.evaluationCompletionRate) }}</strong>
        </article>
        <article class="summary-card" data-testid="summary-average-score">
          <span>平均分</span>
          <strong>{{ scoreLabel(statistics.averageScore) }}</strong>
        </article>
        <article class="summary-card summary-card--warning" data-testid="summary-late">
          <span>逾期提交</span>
          <strong>{{ statistics.lateSubmissionCount }}</strong>
          <small>人</small>
        </article>
      </section>

      <div class="statistics-layout">
        <section class="work-surface distribution-panel" aria-labelledby="score-distribution-title">
          <div class="section-heading">
            <div>
              <p class="section-heading__kicker">SCORE DISTRIBUTION</p>
              <h2 id="score-distribution-title">分数段分布</h2>
            </div>
            <span>{{ scoredStudentCount }} 人已计入</span>
          </div>

          <div
            class="distribution-chart"
            data-testid="score-distribution-chart"
            role="img"
            :aria-label="distributionAriaLabel"
          >
            <p v-if="!hasScoreDistribution" class="distribution-chart__empty">暂无分数分布数据</p>
            <template v-else>
              <div
                v-for="entry in distributionEntries"
                :key="entry.key"
                class="distribution-bar"
                data-testid="score-distribution-bar"
                :aria-label="`${entry.label} ${entry.count} 人`"
              >
                <strong>{{ entry.count }} 人</strong>
                <span class="distribution-bar__track">
                  <span
                    class="distribution-bar__fill"
                    :style="{ height: `${distributionHeight(entry.count)}%` }"
                    aria-hidden="true"
                  />
                </span>
                <span>{{ entry.label }}</span>
              </div>
            </template>
          </div>
        </section>

        <section class="work-surface unsubmitted-panel" aria-labelledby="unsubmitted-title">
          <div class="section-heading">
            <div>
              <p class="section-heading__kicker">FOLLOW UP</p>
              <h2 id="unsubmitted-title">未提交学生</h2>
            </div>
            <span>{{ statistics.unsubmittedCount }} 人</span>
          </div>

          <p v-if="studentNameWarning" class="inline-warning" role="status">
            {{ studentNameWarning }}
          </p>

          <div v-if="unsubmittedStudents.length === 0" class="unsubmitted-panel__empty">
            <strong>全员已提交</strong>
            <p>当前实验没有需要跟进的未提交学生。</p>
          </div>
          <ul v-else class="student-list" aria-label="未提交学生名单">
            <li v-for="student in unsubmittedStudents" :key="student.id">
              <span class="student-list__avatar" aria-hidden="true">{{ student.initial }}</span>
              <div>
                <strong>{{ student.name }}</strong>
                <span>待提交</span>
              </div>
            </li>
          </ul>

          <RouterLink class="button button--primary unsubmitted-panel__queue" :to="submissionWorkspaceRoute">
            进入提交队列
          </RouterLink>
        </section>
      </div>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import { getLabDetail, getLabStatistics } from '../../api/lab/labs';
import { getTeacherLearningProgress } from '../../api/lrn/learningProgress';
import StatusBadge from '../../components/foundation/StatusBadge.vue';
import type { LabExperimentDetail, LabStatistics } from '../../types/lab';
import {
  formatLabDateTime,
  formatLabExperimentStatus,
  labExperimentStatusTone,
  localizedLabError
} from './labDisplay';

const props = defineProps<{
  courseId: number;
  labId: number;
}>();

const scoreBuckets = [
  { key: '0-59', label: '0–59 分' },
  { key: '60-69', label: '60–69 分' },
  { key: '70-79', label: '70–79 分' },
  { key: '80-89', label: '80–89 分' },
  { key: '90-100', label: '90–100 分' }
] as const;

const detail = ref<LabExperimentDetail | null>(null);
const statistics = ref<LabStatistics | null>(null);
const studentNames = ref<Record<number, string>>({});
const loading = ref(false);
const errorMessage = ref('');
const studentNameWarning = ref('');
let activeRequestId = 0;

const teacherDetailRoute = computed(() => ({
  name: 'lab-manage-detail',
  params: { courseId: props.courseId, labId: props.labId }
}));
const submissionWorkspaceRoute = computed(() => ({
  name: 'lab-submission-workspace',
  params: { courseId: props.courseId, labId: props.labId }
}));
const distributionEntries = computed(() => scoreBuckets.map((bucket) => ({
  ...bucket,
  count: statistics.value?.scoreDistribution[bucket.key] ?? 0
})));
const scoredStudentCount = computed(() => distributionEntries.value.reduce((total, entry) => total + entry.count, 0));
const hasScoreDistribution = computed(() => scoredStudentCount.value > 0);
const maximumDistributionCount = computed(() => Math.max(...distributionEntries.value.map((entry) => entry.count), 0));
const distributionAriaLabel = computed(() => {
  if (!hasScoreDistribution.value) {
    return '分数分布柱状图：暂无分数分布数据';
  }
  const summary = distributionEntries.value
    .map((entry) => `${entry.label} ${entry.count} 人`)
    .join('，');
  return `分数分布柱状图：${summary}`;
});
const unsubmittedStudents = computed(() => (statistics.value?.unsubmittedStudentIds ?? []).map((studentId) => {
  const synchronizedName = studentNames.value[studentId]?.trim();
  const name = synchronizedName || '姓名暂不可用';
  return {
    id: studentId,
    name,
    initial: synchronizedName?.slice(0, 1) || '待'
  };
}));

watch(
  () => [props.courseId, props.labId],
  () => {
    void loadPage();
  },
  { immediate: true }
);

async function loadPage() {
  const requestId = ++activeRequestId;
  const targetCourseId = props.courseId;
  const targetLabId = props.labId;
  loading.value = true;
  errorMessage.value = '';
  studentNameWarning.value = '';
  detail.value = null;
  statistics.value = null;
  studentNames.value = {};

  const [detailResult, statisticsResult, progressResult] = await Promise.allSettled([
    getLabDetail(targetLabId),
    getLabStatistics(targetLabId),
    getTeacherLearningProgress(targetCourseId)
  ]);

  if (requestId !== activeRequestId) {
    return;
  }

  if (detailResult.status === 'rejected') {
    errorMessage.value = localizedLabError(detailResult.reason, '实验详情加载失败，请稍后重试');
    loading.value = false;
    return;
  }

  if (statisticsResult.status === 'rejected') {
    errorMessage.value = localizedLabError(statisticsResult.reason, '实验统计加载失败，请稍后重试');
    loading.value = false;
    return;
  }

  if (detailResult.value.id !== targetLabId || detailResult.value.courseId !== targetCourseId) {
    errorMessage.value = '实验详情归属与当前页面不一致，请重新加载。';
    loading.value = false;
    return;
  }

  if (statisticsResult.value.labId !== targetLabId || statisticsResult.value.courseId !== targetCourseId) {
    errorMessage.value = '实验统计归属与当前页面不一致，请重新加载。';
    loading.value = false;
    return;
  }

  if (progressResult.status === 'fulfilled' && progressResult.value.courseId !== targetCourseId) {
    errorMessage.value = '课程学生数据归属与当前页面不一致，请重新加载。';
    loading.value = false;
    return;
  }

  detail.value = detailResult.value;
  statistics.value = statisticsResult.value;

  if (progressResult.status === 'fulfilled') {
    studentNames.value = Object.fromEntries(
      progressResult.value.students
        .map((student) => [student.studentId, student.studentName.trim()] as const)
        .filter((entry) => Boolean(entry[1]))
    );
    const hasMissingNames = statisticsResult.value.unsubmittedStudentIds.some(
      (studentId) => !studentNames.value[studentId]
    );
    if (hasMissingNames) {
      studentNameWarning.value = '部分学生姓名尚未同步，已使用待补充名称。';
    }
  } else {
    studentNameWarning.value = '未能同步学生姓名，已使用待补充名称。';
  }

  loading.value = false;
}

function distributionHeight(count: number) {
  if (count <= 0 || maximumDistributionCount.value <= 0) {
    return 0;
  }
  return Math.max(6, Math.round((count / maximumDistributionCount.value) * 100));
}

function formatPercentage(value: number) {
  return `${formatNumber(value)}%`;
}

function scoreLabel(value: number | null) {
  return value === null ? '暂无成绩' : `${formatNumber(value)} 分`;
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value);
}

</script>

<style scoped>
.lab-statistics {
  --brand: #16423c;
  --brand-deep: #0e302c;
  --brand-soft: #dcebe6;
  --ink: #172b35;
  --muted: #66757d;
  --line: rgba(22, 66, 60, 0.16);
  --surface: rgba(250, 252, 252, 0.95);
  --danger: #a33a36;
  --danger-soft: #f9e9e7;
  --warning: #925f0b;
  --warning-soft: #fff3d6;
  box-sizing: border-box;
  color: var(--ink);
  margin: 0 auto;
  max-width: 1440px;
  min-width: 0;
  padding: clamp(18px, 3vw, 40px);
  width: 100%;
}

.lab-statistics *,
.lab-statistics *::before,
.lab-statistics *::after {
  box-sizing: border-box;
}

.lab-statistics :where(a, button):focus-visible {
  outline: 3px solid #2b7a70;
  outline-offset: 2px;
}

.lab-statistics__hero {
  align-items: flex-end;
  display: flex;
  gap: 24px;
  justify-content: space-between;
  margin-bottom: 22px;
}

.lab-statistics__eyebrow,
.section-heading__kicker {
  color: var(--brand);
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  margin: 0 0 7px;
  text-transform: uppercase;
}

.lab-statistics__hero h1,
.section-heading h2 {
  color: var(--ink);
  margin: 0;
}

.lab-statistics__hero h1 {
  font-size: clamp(1.75rem, 3vw, 2.65rem);
  letter-spacing: -0.035em;
  line-height: 1.08;
}

.lab-statistics__intro {
  color: var(--muted);
  line-height: 1.65;
  margin: 10px 0 0;
  max-width: 620px;
}

.lab-statistics__actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 9px;
  justify-content: flex-end;
}

.button {
  align-items: center;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  display: inline-flex;
  font: inherit;
  font-size: 0.86rem;
  font-weight: 750;
  justify-content: center;
  min-height: 40px;
  padding: 0 14px;
  text-decoration: none;
}

.button:disabled {
  cursor: wait;
  opacity: 0.68;
}

.button--primary {
  background: var(--brand);
  color: #fff;
}

.button--secondary {
  background: var(--brand-soft);
  border-color: rgba(22, 66, 60, 0.24);
  color: var(--brand-deep);
}

.button--quiet {
  background: rgba(255, 255, 255, 0.62);
  border-color: var(--line);
  color: var(--brand-deep);
}

.state-panel,
.experiment-strip,
.summary-card,
.work-surface {
  backdrop-filter: blur(14px);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 12px;
  box-shadow: 0 12px 34px rgba(18, 47, 48, 0.08);
}

.state-panel {
  align-items: center;
  display: flex;
  gap: 16px;
  justify-content: center;
  min-height: 220px;
  padding: 28px;
  text-align: left;
}

.state-panel strong {
  display: block;
  font-size: 1.05rem;
}

.state-panel p {
  color: var(--muted);
  margin: 7px 0 0;
}

.state-panel--error {
  background: var(--danger-soft);
  border-color: rgba(163, 58, 54, 0.28);
  flex-wrap: wrap;
}

.state-panel--error strong,
.state-panel--error p {
  color: var(--danger);
}

.state-panel__spinner {
  animation: spin 0.8s linear infinite;
  border: 3px solid var(--brand-soft);
  border-radius: 50%;
  border-top-color: var(--brand);
  height: 32px;
  width: 32px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.experiment-strip {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 14px;
  padding: 15px 18px;
}

.experiment-strip > div {
  border-left: 1px solid var(--line);
  display: grid;
  gap: 5px;
  min-width: 0;
  padding-left: 16px;
}

.experiment-strip > div:first-child {
  border-left: 0;
  padding-left: 0;
}

.experiment-strip span,
.summary-card span,
.summary-card small,
.section-heading > span,
.student-list div > span {
  color: var(--muted);
  font-size: 0.78rem;
}

.experiment-strip strong {
  font-size: 0.9rem;
  overflow-wrap: anywhere;
}

.summary-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: 16px;
}

.summary-card {
  min-width: 0;
  overflow: hidden;
  padding: 18px 20px;
}

.summary-card span {
  display: block;
  font-weight: 700;
}

.summary-card strong {
  color: var(--brand-deep);
  display: inline-block;
  font-size: 1.8rem;
  line-height: 1;
  margin: 7px 5px 0 0;
}

.summary-card--success {
  border-top: 3px solid #4e9a79;
}

.summary-card--warning {
  border-top: 3px solid #c28a2d;
}

.statistics-layout {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(0, 1.55fr) minmax(280px, 0.85fr);
}

.work-surface {
  min-width: 0;
  padding: clamp(18px, 2.2vw, 26px);
}

.section-heading {
  align-items: center;
  display: flex;
  gap: 18px;
  justify-content: space-between;
  margin-bottom: 20px;
}

.section-heading h2 {
  font-size: 1.18rem;
}

.section-heading > span {
  background: var(--brand-soft);
  border-radius: 999px;
  color: var(--brand-deep);
  font-weight: 750;
  padding: 5px 9px;
  white-space: nowrap;
}

.distribution-chart {
  align-items: end;
  background: linear-gradient(180deg, rgba(239, 247, 245, 0.62), rgba(220, 235, 230, 0.78));
  border: 1px solid var(--line);
  border-radius: 10px;
  display: grid;
  gap: clamp(8px, 1.4vw, 16px);
  grid-template-columns: repeat(5, minmax(0, 1fr));
  min-height: 280px;
  padding: 20px 14px 14px;
}

.distribution-chart__empty {
  align-self: center;
  color: var(--muted);
  grid-column: 1 / -1;
  justify-self: center;
  margin: 0;
}

.distribution-bar {
  display: grid;
  gap: 9px;
  grid-template-rows: auto minmax(170px, 1fr) auto;
  height: 100%;
  min-width: 0;
  text-align: center;
}

.distribution-bar > strong {
  color: var(--brand-deep);
  font-size: 0.88rem;
}

.distribution-bar > span:last-child {
  color: var(--muted);
  font-size: 0.72rem;
  overflow-wrap: anywhere;
}

.distribution-bar__track {
  align-items: end;
  background: rgba(22, 66, 60, 0.1);
  border-radius: 8px 8px 3px 3px;
  display: flex;
  min-height: 170px;
  overflow: hidden;
}

.distribution-bar__fill {
  background: linear-gradient(180deg, #4f9186, var(--brand));
  border-radius: 8px 8px 3px 3px;
  display: block;
  transition: height 0.25s ease;
  width: 100%;
}

.inline-warning {
  background: var(--warning-soft);
  border: 1px solid rgba(146, 95, 11, 0.22);
  border-radius: 8px;
  color: var(--warning);
  font-size: 0.82rem;
  line-height: 1.5;
  margin: 0 0 14px;
  padding: 10px 12px;
}

.unsubmitted-panel {
  align-self: start;
}

.unsubmitted-panel__empty {
  background: rgba(220, 235, 230, 0.45);
  border: 1px dashed rgba(22, 66, 60, 0.28);
  border-radius: 10px;
  padding: 22px;
  text-align: center;
}

.unsubmitted-panel__empty p {
  color: var(--muted);
  font-size: 0.84rem;
  line-height: 1.55;
  margin: 7px 0 0;
}

.student-list {
  display: grid;
  gap: 9px;
  list-style: none;
  margin: 0;
  max-height: 330px;
  overflow-y: auto;
  padding: 0 3px 0 0;
}

.student-list li {
  align-items: center;
  background: rgba(255, 255, 255, 0.66);
  border: 1px solid var(--line);
  border-radius: 9px;
  display: flex;
  gap: 11px;
  padding: 11px;
}

.student-list__avatar {
  align-items: center;
  background: var(--brand-soft);
  border-radius: 50%;
  color: var(--brand-deep);
  display: inline-flex;
  flex: 0 0 34px;
  font-size: 0.85rem;
  font-weight: 800;
  height: 34px;
  justify-content: center;
}

.student-list div {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.student-list strong {
  overflow-wrap: anywhere;
}

.unsubmitted-panel__queue {
  margin-top: 16px;
  width: 100%;
}

@media (max-width: 1100px) {
  .experiment-strip,
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .experiment-strip > div:nth-child(3) {
    border-left: 0;
    padding-left: 0;
  }

  .statistics-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .lab-statistics {
    padding: 16px 0;
  }

  .lab-statistics__hero {
    align-items: stretch;
    flex-direction: column;
  }

  .lab-statistics__actions {
    justify-content: stretch;
  }

  .lab-statistics__actions .button {
    flex: 1 1 150px;
  }

  .experiment-strip,
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .experiment-strip > div,
  .experiment-strip > div:nth-child(3) {
    border-left: 0;
    border-top: 1px solid var(--line);
    padding-left: 0;
    padding-top: 11px;
  }

  .experiment-strip > div:first-child {
    border-top: 0;
    padding-top: 0;
  }

  .distribution-chart {
    gap: 6px;
    min-height: 230px;
    padding-inline: 8px;
  }

  .distribution-bar {
    grid-template-rows: auto minmax(130px, 1fr) auto;
  }

  .distribution-bar__track {
    min-height: 130px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .state-panel__spinner {
    animation: none;
  }

  .distribution-bar__fill {
    transition: none;
  }
}
</style>
