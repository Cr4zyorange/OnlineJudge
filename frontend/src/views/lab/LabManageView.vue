<template>
  <main class="lab-manage" data-testid="lab-manage-detail">
    <PageState
      v-if="loading"
      state="loading"
      title="正在加载实验管理详情"
      message="同步实验配置与完成情况。"
    />
    <PageState
      v-else-if="loadError"
      state="error"
      title="实验管理详情加载失败"
      :message="loadError"
      retry-label="重新加载"
      @retry="loadManageView"
    />

    <template v-else-if="detail">
      <PageHeader
        :title="detail.title"
        eyebrow="单实验管理"
        :subtitle="`${courseName} · ${detail.description}`"
      >
        <template #meta>
          <StatusBadge :label="formatLabExperimentStatus(detail.status)" :tone="labExperimentStatusTone(detail.status)" />
          <span>{{ formatLabEvaluationMode(detail.evaluationMode) }}</span>
          <span>截止 {{ formatLabDateTime(detail.deadline) }}</span>
        </template>
        <template #actions>
          <RouterLink class="button" :to="{ name: 'lab-manage', params: { courseId } }">返回实验列表</RouterLink>
          <RouterLink
            v-if="detail.status === 'DRAFT'"
            class="button button--primary"
            :to="{ name: 'lab-edit', params: { courseId, labId } }"
          >
            编辑草稿
          </RouterLink>
        </template>
      </PageHeader>

      <SummaryStrip :items="summaryItems" aria-label="实验完成摘要" />

      <p v-if="statisticsWarning" class="notice notice--warning" data-testid="statistics-warning" role="status">
        {{ statisticsWarning }}；实验配置与提交队列仍可正常使用。
      </p>

      <section class="manage-grid" aria-label="实验教师任务入口">
        <RouterLink class="manage-card" :to="{ name: 'lab-submission-workspace', params: { courseId, labId } }">
          <span class="manage-card__eyebrow">批阅入口</span>
          <h2>提交与评分</h2>
          <p>按学生、提交状态与评测结果定位有效版本，完成报告核对和教师评分。</p>
          <strong>进入提交队列 →</strong>
        </RouterLink>

        <RouterLink class="manage-card" :to="{ name: 'lab-statistics', params: { courseId, labId } }">
          <span class="manage-card__eyebrow">班级视角</span>
          <h2>完成统计</h2>
          <p>查看提交率、评测完成率、成绩分布和未提交学生，快速回到待处理队列。</p>
          <strong>查看实验统计 →</strong>
        </RouterLink>

        <RouterLink
          v-if="detail.status === 'DRAFT'"
          class="manage-card"
          :to="{ name: 'lab-edit', params: { courseId, labId } }"
        >
          <span class="manage-card__eyebrow">发布配置</span>
          <h2>实验内容与规则</h2>
          <p>核对章节、附件、评测方式、报告要求与测试用例，再从实验列表发布。</p>
          <strong>继续编辑草稿 →</strong>
        </RouterLink>
        <article v-else class="manage-card manage-card--static">
          <span class="manage-card__eyebrow">发布配置</span>
          <h2>实验内容与规则</h2>
          <p>实验已进入发布流程，当前配置只读；下方保留评测与提交规则供批阅核对。</p>
          <strong>配置已锁定</strong>
        </article>
      </section>

      <section class="configuration" aria-labelledby="lab-config-heading">
        <header>
          <div>
            <p>当前配置</p>
            <h2 id="lab-config-heading">发布与评测规则</h2>
          </div>
          <span>{{ detail.testcases.length }} 条测试用例</span>
        </header>
        <dl>
          <div><dt>满分</dt><dd>{{ detail.maxScore }} 分</dd></div>
          <div><dt>评测方式</dt><dd>{{ formatLabEvaluationMode(detail.evaluationMode) }}</dd></div>
          <div><dt>自动评测</dt><dd>{{ detail.autoEvaluate ? '已开启' : '未开启' }}</dd></div>
          <div><dt>实验报告</dt><dd>{{ detail.reportRequired ? '必须提交' : '不要求' }}</dd></div>
          <div><dt>允许语言</dt><dd>{{ languageLabel }}</dd></div>
          <div><dt>课程附件</dt><dd>{{ detail.attachmentIds.length }} 份</dd></div>
        </dl>
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import { getLabDetail, getLabStatistics } from '../../api/lab/labs';
import { currentCourse } from '../../app/runtimeContext';
import PageHeader from '../../components/foundation/PageHeader.vue';
import PageState from '../../components/foundation/PageState.vue';
import StatusBadge from '../../components/foundation/StatusBadge.vue';
import SummaryStrip, { type SummaryStripItem } from '../../components/foundation/SummaryStrip.vue';
import type { LabExperimentDetail, LabStatistics } from '../../types/lab';
import {
  formatLabDateTime,
  formatLabEvaluationMode,
  formatLabExperimentStatus,
  labExperimentStatusTone,
  localizedLabError
} from './labDisplay';

const props = defineProps<{ courseId: number; labId: number }>();
const detail = ref<LabExperimentDetail | null>(null);
const statistics = ref<LabStatistics | null>(null);
const loading = ref(false);
const loadError = ref('');
const statisticsWarning = ref('');
let loadGeneration = 0;

const courseName = computed(() => (
  currentCourse.value?.id === props.courseId ? currentCourse.value.name : '当前课程'
));
const languageLabel = computed(() => detail.value?.allowedLanguages
  ? detail.value.allowedLanguages.split(',').map(formatLanguage).join('、')
  : '不限制');
const summaryItems = computed<SummaryStripItem[]>(() => {
  const value = statistics.value;
  return [
    {
      key: 'submission-rate',
      label: '提交率',
      value: value ? formatPercentage(value.submissionRate) : '暂不可用',
      hint: value ? `${value.submittedCount} / ${value.totalStudentCount} 人已提交` : '进入统计页可重试',
      tone: 'brand'
    },
    {
      key: 'evaluation-rate',
      label: '评测完成率',
      value: value ? formatPercentage(value.evaluationCompletionRate) : '暂不可用',
      hint: value ? `${value.evaluatedCount} 份已评测` : '提交队列仍可使用'
    },
    {
      key: 'average-score',
      label: '平均分',
      value: value?.averageScore == null ? '—' : `${formatScore(value.averageScore)} 分`,
      hint: `实验满分 ${detail.value?.maxScore ?? '—'} 分`,
      tone: 'success'
    },
    {
      key: 'unsubmitted',
      label: '未提交',
      value: value?.unsubmittedCount ?? '—',
      hint: value ? `${value.lateSubmissionCount} 份逾期提交` : '统计暂不可用',
      tone: value && value.unsubmittedCount > 0 ? 'warning' : 'neutral'
    }
  ];
});

watch(
  () => [props.courseId, props.labId] as const,
  () => void loadManageView(),
  { immediate: true }
);

async function loadManageView() {
  const generation = ++loadGeneration;
  loading.value = true;
  loadError.value = '';
  statisticsWarning.value = '';
  detail.value = null;
  statistics.value = null;
  try {
    const loaded = await getLabDetail(props.labId);
    if (generation !== loadGeneration) return;
    if (loaded.id !== props.labId || loaded.courseId !== props.courseId) {
      throw new Error('实验与当前课程不匹配，请返回实验列表重新进入。');
    }
    detail.value = loaded;
    try {
      const loadedStatistics = await getLabStatistics(props.labId);
      if (generation !== loadGeneration) return;
      if (loadedStatistics.labId !== props.labId || loadedStatistics.courseId !== props.courseId) {
        throw new Error('统计结果与当前实验不匹配');
      }
      statistics.value = loadedStatistics;
    } catch (error) {
      if (generation !== loadGeneration) return;
      statisticsWarning.value = localizedLabError(error, '实验统计暂时不可用');
    }
  } catch (error) {
    if (generation !== loadGeneration) return;
    loadError.value = localizedLabError(error, '实验管理详情加载失败，请稍后重试。');
  } finally {
    if (generation === loadGeneration) loading.value = false;
  }
}

function formatPercentage(value: number) {
  return `${new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value)}%`;
}

function formatScore(value: number) {
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

function formatLanguage(language: string) {
  return {
    python: 'Python',
    java: 'Java',
    cpp: 'C++',
    javascript: 'JavaScript'
  }[language.trim()] ?? language.trim();
}
</script>

<style scoped>
.lab-manage {
  display: grid;
  gap: 18px;
  width: 100%;
  min-width: 0;
  padding-bottom: 38px;
  color: var(--oj-ink);
}

.manage-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.manage-card,
.configuration {
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  box-shadow: var(--oj-shadow-soft);
  backdrop-filter: var(--oj-blur);
  -webkit-backdrop-filter: var(--oj-blur);
}

.manage-card {
  display: grid;
  align-content: start;
  gap: 9px;
  min-height: 210px;
  padding: 20px;
  color: var(--oj-ink);
  text-decoration: none;
  transition: border-color 160ms ease, transform 160ms ease;
}

.manage-card--static:hover {
  border-color: var(--oj-line);
  transform: none;
}

.manage-card:hover {
  border-color: var(--oj-brand);
  transform: translateY(-2px);
}

.manage-card__eyebrow,
.configuration header p {
  margin: 0;
  color: var(--oj-brand);
  font-size: 0.73rem;
  font-weight: 900;
  letter-spacing: 0.05em;
}

.manage-card h2,
.manage-card p,
.configuration h2 {
  margin: 0;
}

.manage-card h2 {
  font-size: 1.08rem;
}

.manage-card p {
  color: var(--oj-ink-soft);
  font-size: 0.84rem;
  line-height: 1.65;
}

.manage-card strong {
  align-self: end;
  margin-top: auto;
  color: var(--oj-brand);
  font-size: 0.82rem;
}

.configuration {
  display: grid;
  gap: 18px;
  padding: 21px;
}

.configuration header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 14px;
}

.configuration header span {
  color: var(--oj-muted);
  font-size: 0.8rem;
  font-weight: 700;
}

.configuration dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.configuration dl > div {
  display: grid;
  gap: 5px;
  padding: 12px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: rgba(255, 255, 255, 0.4);
}

.configuration dt {
  color: var(--oj-muted);
  font-size: 0.74rem;
  font-weight: 800;
}

.configuration dd {
  margin: 0;
  color: var(--oj-ink);
  font-size: 0.88rem;
  font-weight: 800;
}

.button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 8px 15px;
  border: 1px solid var(--oj-line-strong);
  border-radius: var(--oj-radius);
  background: var(--oj-surface-solid);
  color: var(--oj-brand);
  font-size: 0.82rem;
  font-weight: 800;
  text-decoration: none;
}

.button--primary {
  border-color: var(--oj-brand);
  background: var(--oj-brand);
  color: #fff;
}

.notice {
  margin: 0;
  padding: 12px 14px;
  border: 1px solid rgba(146, 64, 14, 0.24);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  color: #7c4a03;
  font-size: 0.84rem;
  font-weight: 700;
  line-height: 1.6;
}

.lab-manage :where(a, button):focus-visible {
  outline: 3px solid var(--oj-brand);
  outline-offset: 2px;
}

@media (max-width: 900px) {
  .manage-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .manage-card {
    min-height: 0;
  }
}

@media (max-width: 640px) {
  .configuration dl {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .configuration header {
    align-items: start;
    flex-direction: column;
  }
}
</style>
