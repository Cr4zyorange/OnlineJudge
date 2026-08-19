<template>
  <main class="lab-teacher-index" data-testid="lab-teacher-index">
    <PageHeader
      title="实验管理"
      eyebrow="教师实验工作台"
      :subtitle="`${courseName} · 先定位实验，再进入编辑、提交队列或统计。`"
    >
      <template #actions>
        <RouterLink
          class="button button--primary"
          data-testid="create-lab"
          :to="{ name: 'lab-create', params: { courseId } }"
        >
          创建实验
        </RouterLink>
      </template>
    </PageHeader>

    <SummaryStrip :items="summaryItems" aria-label="实验管理摘要" />

    <FilterBar
      v-model="filterDraft"
      :fields="filterFields"
      aria-label="筛选实验"
      submit-label="应用筛选"
      @submit="applyFilters"
      @reset="resetFilters"
    />

    <p v-if="partialWarning" class="notice notice--warning" data-testid="submission-warning" role="status">
      {{ partialWarning }}
    </p>
    <p v-if="operationFeedback" class="notice notice--success" role="status">
      {{ operationFeedback }}
    </p>
    <p v-if="operationError" class="notice notice--danger" data-testid="operation-error" role="alert">
      {{ operationError }}
    </p>

    <PageState
      v-if="loading"
      state="loading"
      title="正在加载实验"
      message="同步生命周期、提交数与待批阅状态。"
    />
    <PageState
      v-else-if="loadError"
      state="error"
      title="实验管理加载失败"
      :message="loadError"
      retry-label="重新加载"
      @retry="loadLabs"
    />
    <DataTable
      v-else
      :columns="columns"
      :rows="filteredRows"
      caption="当前课程实验管理列表"
      row-key="id"
      :row-label="rowLabel"
      empty-title="当前筛选下没有实验"
      :empty-message="labs.length === 0 ? '创建第一项实验后会显示在这里。' : '调整关键词、状态或待处理条件后再试。'"
    >
      <template #cell-title="{ row }">
        <div class="lab-title-cell">
          <RouterLink
            class="lab-title-link"
            :to="{ name: 'lab-manage-detail', params: { courseId, labId: rowId(row) } }"
          >
            {{ row.title }}
          </RouterLink>
          <small>{{ row.modeLabel }} · 满分 {{ row.maxScore }}</small>
        </div>
      </template>

      <template #cell-status="{ row }">
        <StatusBadge :label="String(row.statusLabel)" :tone="rowTone(row)" />
      </template>

      <template #cell-deadline="{ row }">
        <div class="lab-deadline">
          <span>{{ row.deadlineLabel }}</span>
          <small>{{ row.deadlineHint }}</small>
        </div>
      </template>

      <template #cell-submissions="{ row }">
        <div class="lab-submission-counts">
          <strong>{{ row.submissionCountLabel }}</strong>
          <small>{{ row.pendingReviewLabel }}</small>
        </div>
      </template>

      <template #cell-actions="{ row }">
        <div class="lab-row-actions">
          <RouterLink
            :data-testid="`manage-lab-${rowId(row)}`"
            :to="{ name: 'lab-manage-detail', params: { courseId, labId: rowId(row) } }"
          >
            进入
          </RouterLink>
          <RouterLink
            v-if="rowStatus(row) === 'DRAFT'"
            :data-testid="`edit-lab-${rowId(row)}`"
            :to="{ name: 'lab-edit', params: { courseId, labId: rowId(row) } }"
          >
            编辑
          </RouterLink>
          <RouterLink
            v-if="rowStatus(row) !== 'DRAFT'"
            :data-testid="`submissions-lab-${rowId(row)}`"
            :to="{ name: 'lab-submission-workspace', params: { courseId, labId: rowId(row) } }"
          >
            提交队列
          </RouterLink>
          <RouterLink
            v-if="rowStatus(row) !== 'DRAFT'"
            :data-testid="`statistics-lab-${rowId(row)}`"
            :to="{ name: 'lab-statistics', params: { courseId, labId: rowId(row) } }"
          >
            统计
          </RouterLink>
          <button
            v-if="rowStatus(row) === 'DRAFT'"
            :data-testid="`publish-lab-${rowId(row)}`"
            type="button"
            :disabled="pendingLabId !== null"
            @click="runLifecycleRow(row, 'publish')"
          >
            {{ pendingLabel(rowId(row), '发布') }}
          </button>
          <button
            v-if="rowStatus(row) === 'PUBLISHED'"
            :data-testid="`close-lab-${rowId(row)}`"
            type="button"
            :disabled="pendingLabId !== null"
            @click="runLifecycleRow(row, 'close')"
          >
            {{ pendingLabel(rowId(row), '截止') }}
          </button>
          <button
            v-if="rowStatus(row) === 'PUBLISHED' || rowStatus(row) === 'CLOSED'"
            :data-testid="`release-lab-${rowId(row)}`"
            type="button"
            :disabled="pendingLabId !== null"
            @click="runLifecycleRow(row, 'release')"
          >
            {{ pendingLabel(rowId(row), '发布成绩') }}
          </button>
          <button
            v-if="rowStatus(row) === 'DRAFT'"
            class="danger-action"
            :data-testid="`delete-lab-${rowId(row)}`"
            type="button"
            :disabled="pendingLabId !== null"
            @click="runLifecycleRow(row, 'delete')"
          >
            {{ pendingLabel(rowId(row), '删除草稿') }}
          </button>
        </div>
      </template>
    </DataTable>
  </main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import {
  closeLab,
  deleteLab,
  listLabSubmissions,
  listLabs,
  publishLab,
  releaseLabScores
} from '../../api/lab/labs';
import { currentCourse } from '../../app/runtimeContext';
import DataTable, { type DataTableColumn, type DataTableRow } from '../../components/foundation/DataTable.vue';
import FilterBar, { type FilterFieldModel } from '../../components/foundation/FilterBar.vue';
import PageHeader from '../../components/foundation/PageHeader.vue';
import PageState from '../../components/foundation/PageState.vue';
import StatusBadge, { type StatusBadgeTone } from '../../components/foundation/StatusBadge.vue';
import SummaryStrip, { type SummaryStripItem } from '../../components/foundation/SummaryStrip.vue';
import type {
  LabExperimentStatus,
  LabExperimentSummary,
  LabSubmissionHistoryItem
} from '../../types/lab';
import {
  formatLabDateTime,
  formatLabEvaluationMode,
  formatLabExperimentStatus,
  labExperimentStatusTone,
  localizedLabError
} from './labDisplay';

type LifecycleAction = 'publish' | 'close' | 'release' | 'delete';

interface LabRow extends DataTableRow {
  id: number;
  title: string;
  status: LabExperimentStatus;
  statusLabel: string;
  statusTone: StatusBadgeTone;
  modeLabel: string;
  maxScore: number;
  deadlineLabel: string;
  deadlineHint: string;
  submissionCount: number | null;
  submissionCountLabel: string;
  pendingReviewCount: number | null;
  pendingReviewLabel: string;
  pendingEvaluationCount: number | null;
}

const props = defineProps<{ courseId: number }>();
const labs = ref<LabExperimentSummary[]>([]);
const submissionsByLab = ref(new Map<number, LabSubmissionHistoryItem[] | null>());
const loading = ref(false);
const loadError = ref('');
const partialWarning = ref('');
const operationFeedback = ref('');
const operationError = ref('');
const pendingLabId = ref<number | null>(null);
const filterDraft = ref<Record<string, string>>({ keyword: '', status: '', attention: '' });
const appliedFilters = ref<Record<string, string>>({ keyword: '', status: '', attention: '' });
let loadGeneration = 0;

const courseName = computed(() => (
  currentCourse.value?.id === props.courseId ? currentCourse.value.name : '当前课程'
));

const filterFields: readonly FilterFieldModel[] = [
  { key: 'keyword', label: '搜索实验', kind: 'search', placeholder: '输入实验名称' },
  {
    key: 'status',
    label: '生命周期',
    kind: 'select',
    options: [
      { value: '', label: '全部状态' },
      { value: 'DRAFT', label: '草稿' },
      { value: 'PUBLISHED', label: '进行中' },
      { value: 'CLOSED', label: '已截止' },
      { value: 'SCORE_PUBLISHED', label: '成绩已发布' },
      { value: 'ARCHIVED', label: '已归档' }
    ]
  },
  {
    key: 'attention',
    label: '待处理',
    kind: 'select',
    options: [
      { value: '', label: '全部实验' },
      { value: 'review', label: '有待批阅提交' },
      { value: 'evaluation', label: '有评测中提交' },
      { value: 'draft', label: '待发布草稿' }
    ]
  }
];

const columns: readonly DataTableColumn[] = [
  { key: 'title', label: '实验', width: '25%' },
  { key: 'status', label: '状态', width: '12%' },
  { key: 'deadline', label: '截止时间', width: '17%' },
  { key: 'submissions', label: '提交与批阅', width: '16%' },
  { key: 'actions', label: '操作', width: '30%' }
];

const rows = computed<LabRow[]>(() => labs.value.map((lab) => {
  const submissions = submissionsByLab.value.get(lab.id);
  const pendingReview = submissions?.filter((item) => (
    item.isScoringBasis && item.finalScore === null
  )).length ?? null;
  const pendingEvaluation = submissions?.filter((item) => (
    item.isLatest && ['NONE', 'PENDING', 'RUNNING'].includes(item.evaluationStatus)
  )).length ?? null;
  const remaining = new Date(lab.deadline).getTime() - Date.now();
  return {
    id: lab.id,
    title: lab.title,
    status: lab.status,
    statusLabel: formatLabExperimentStatus(lab.status),
    statusTone: labExperimentStatusTone(lab.status),
    modeLabel: formatLabEvaluationMode(lab.evaluationMode),
    maxScore: lab.maxScore,
    deadlineLabel: formatLabDateTime(lab.deadline),
    deadlineHint: remaining > 0
      ? `距截止 ${Math.max(1, Math.ceil(remaining / 86_400_000))} 天`
      : '截止时间已过',
    submissionCount: submissions?.length ?? null,
    submissionCountLabel: submissions === null || submissions === undefined
      ? '暂不可用'
      : `${submissions.length} 份提交`,
    pendingReviewCount: pendingReview,
    pendingReviewLabel: pendingReview === null
      ? '待批阅未知'
      : `${pendingReview} 份待批阅`,
    pendingEvaluationCount: pendingEvaluation
  };
}));

const filteredRows = computed(() => {
  const keyword = (appliedFilters.value.keyword ?? '').trim().toLowerCase();
  const status = appliedFilters.value.status ?? '';
  const attention = appliedFilters.value.attention ?? '';
  return rows.value.filter((row) => {
    if (keyword && !row.title.toLowerCase().includes(keyword)) return false;
    if (status && row.status !== status) return false;
    if (attention === 'review' && (row.pendingReviewCount ?? 0) === 0) return false;
    if (attention === 'evaluation' && Number(row.pendingEvaluationCount ?? 0) === 0) return false;
    if (attention === 'draft' && row.status !== 'DRAFT') return false;
    return true;
  });
});

const summaryItems = computed<SummaryStripItem[]>(() => [
  {
    key: 'total',
    label: '课程实验',
    value: labs.value.length,
    hint: '当前课程全部实验',
    tone: 'brand'
  },
  {
    key: 'active',
    label: '进行中',
    value: labs.value.filter((lab) => lab.status === 'PUBLISHED').length,
    hint: '学生可提交'
  },
  {
    key: 'review',
    label: '待批阅',
    value: rows.value.reduce((total, row) => total + (row.pendingReviewCount ?? 0), 0),
    hint: '当前有效版本未定分',
    tone: rows.value.some((row) => (row.pendingReviewCount ?? 0) > 0) ? 'warning' : 'neutral'
  },
  {
    key: 'released',
    label: '成绩已发布',
    value: labs.value.filter((lab) => ['SCORE_PUBLISHED', 'ARCHIVED'].includes(lab.status)).length,
    hint: '学生可查看最终成绩',
    tone: 'success'
  }
]);

watch(
  () => props.courseId,
  () => void loadLabs(),
  { immediate: true }
);

async function loadLabs() {
  const generation = ++loadGeneration;
  loading.value = true;
  loadError.value = '';
  partialWarning.value = '';
  operationError.value = '';
  try {
    const result = await listLabs(props.courseId);
    if (generation !== loadGeneration) return;
    if (result.some((lab) => lab.courseId !== props.courseId)) {
      throw new Error('实验列表与当前课程不匹配，请重新加载。');
    }
    labs.value = result.filter((lab) => !lab.deleted);
    const histories = await Promise.allSettled(
      labs.value.map((lab) => listLabSubmissions(lab.id))
    );
    if (generation !== loadGeneration) return;
    submissionsByLab.value = new Map(labs.value.map((lab, index) => {
      const history = histories[index];
      return [lab.id, history.status === 'fulfilled' ? history.value : null];
    }));
    if (histories.some((history) => history.status === 'rejected')) {
      partialWarning.value = '部分实验的提交摘要暂不可用；仍可进入实验详情或提交队列继续处理。';
    }
  } catch (error) {
    if (generation !== loadGeneration) return;
    labs.value = [];
    submissionsByLab.value = new Map();
    loadError.value = localizedLabError(error, '实验管理加载失败，请稍后重试。');
  } finally {
    if (generation === loadGeneration) loading.value = false;
  }
}

function applyFilters(filters: Record<string, string>) {
  appliedFilters.value = { ...filters };
}

function resetFilters() {
  filterDraft.value = { keyword: '', status: '', attention: '' };
  appliedFilters.value = { ...filterDraft.value };
}

async function runLifecycle(row: LabRow, action: LifecycleAction) {
  const copy = lifecycleCopy(action, row.title);
  if (!window.confirm(copy.confirm)) return;
  pendingLabId.value = row.id;
  operationFeedback.value = '';
  operationError.value = '';
  try {
    if (action === 'publish') await publishLab(row.id);
    if (action === 'close') await closeLab(row.id);
    if (action === 'release') await releaseLabScores(row.id);
    if (action === 'delete') await deleteLab(row.id);
    operationFeedback.value = copy.success;
    await loadLabs();
  } catch (error) {
    operationError.value = localizedLabError(error, copy.failure);
  } finally {
    pendingLabId.value = null;
  }
}

function lifecycleCopy(action: LifecycleAction, title: string) {
  return {
    publish: {
      confirm: `确认发布“${title}”？发布后学生将看到实验并可按规则提交。`,
      success: `“${title}”发布成功。`,
      failure: '实验发布失败，请核对配置后重试。'
    },
    close: {
      confirm: `确认将“${title}”设为截止？截止后将停止常规提交。`,
      success: `“${title}”已截止。`,
      failure: '实验截止操作失败，请刷新状态后重试。'
    },
    release: {
      confirm: `确认发布“${title}”的成绩？学生将看到最终分与公开反馈。`,
      success: `“${title}”成绩发布成功。`,
      failure: '成绩发布失败，请检查待批阅提交后重试。'
    },
    delete: {
      confirm: `确认删除草稿“${title}”？此操作只适用于未发布草稿。`,
      success: `草稿“${title}”已删除。`,
      failure: '草稿删除失败，请刷新状态后重试。'
    }
  }[action];
}

function pendingLabel(labId: number, label: string) {
  return pendingLabId.value === labId ? '处理中…' : label;
}

function rowId(row: DataTableRow) {
  return Number(row.id);
}

function rowStatus(row: DataTableRow) {
  return row.status as LabExperimentStatus;
}

function rowTone(row: DataTableRow) {
  return row.statusTone as StatusBadgeTone;
}

function runLifecycleRow(row: DataTableRow, action: LifecycleAction) {
  return runLifecycle(row as LabRow, action);
}

function rowLabel(row: DataTableRow) {
  return `实验：${String(row.title)}`;
}
</script>

<style scoped>
.lab-teacher-index {
  display: grid;
  gap: 18px;
  width: 100%;
  min-width: 0;
  padding-bottom: 36px;
  color: var(--oj-ink);
}

.button,
.lab-row-actions a,
.lab-row-actions button {
  min-height: 38px;
  padding: 8px 12px;
  border: 1px solid var(--oj-line-strong);
  border-radius: var(--oj-radius);
  background: var(--oj-surface-solid);
  color: var(--oj-brand);
  cursor: pointer;
  font: inherit;
  font-size: 0.78rem;
  font-weight: 800;
  line-height: 1.3;
  text-decoration: none;
}

.button--primary {
  border-color: var(--oj-brand);
  background: var(--oj-brand);
  color: #fff;
}

.lab-title-cell,
.lab-deadline,
.lab-submission-counts {
  display: grid;
  gap: 4px;
}

.lab-title-link {
  color: var(--oj-ink);
  font-weight: 800;
  text-decoration: none;
}

.lab-title-cell small,
.lab-deadline small,
.lab-submission-counts small {
  color: var(--oj-muted);
  font-size: 0.75rem;
}

.lab-row-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.lab-row-actions button:disabled {
  cursor: wait;
  opacity: 0.58;
}

.lab-row-actions .danger-action {
  border-color: rgba(180, 35, 24, 0.28);
  color: #8f2d24;
}

.notice {
  margin: 0;
  padding: 12px 14px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  font-size: 0.88rem;
  font-weight: 700;
  line-height: 1.6;
}

.notice--success {
  border-color: rgba(22, 101, 52, 0.24);
  color: #166534;
}

.notice--warning {
  border-color: rgba(146, 64, 14, 0.24);
  color: #7c4a03;
}

.notice--danger {
  border-color: rgba(180, 35, 24, 0.24);
  color: #8f2d24;
}

.lab-teacher-index :deep(a:focus-visible),
.lab-teacher-index button:focus-visible {
  outline: 3px solid var(--oj-brand);
  outline-offset: 2px;
}

@media (max-width: 640px) {
  .lab-teacher-index {
    gap: 14px;
  }

  .lab-row-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .lab-row-actions a,
  .lab-row-actions button {
    width: 100%;
    text-align: center;
  }
}
</style>
