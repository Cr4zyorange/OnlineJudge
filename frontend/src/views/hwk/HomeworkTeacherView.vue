<template>
  <main class="homework-teacher-index" data-testid="homework-teacher-index">
    <PageHeader
      title="作业管理"
      eyebrow="教师作业工作台"
      :subtitle="`${courseName} · 先定位已有作业，再进入编辑、提交队列或统计。`"
    >
      <template #actions>
        <RouterLink
          class="button button--primary"
          data-testid="create-homework"
          :to="{ name: 'homework-create', params: { courseId } }"
        >
          新建作业
        </RouterLink>
      </template>
    </PageHeader>

    <SummaryStrip :items="summaryItems" aria-label="作业管理摘要" />

    <FilterBar
      v-model="filterDraft"
      :fields="filterFields"
      aria-label="筛选作业"
      submit-label="应用筛选"
      @submit="applyFilters"
      @reset="resetFilters"
    />
    <p
      v-if="attentionIsApplied"
      class="notice notice--warning"
      data-testid="attention-scope-note"
      role="status"
    >
      待发布草稿条件只细化当前页；可继续翻页查看。总数与页码仍以关键词和生命周期的服务端筛选为准。
    </p>

    <p v-if="partialWarning" class="notice notice--warning" data-testid="statistics-warning" role="status">
      {{ partialWarning }}
    </p>
    <p
      v-if="operationFeedback"
      class="notice notice--success"
      data-testid="operation-feedback"
      role="status"
    >
      {{ operationFeedback }}
    </p>
    <p v-if="operationError" class="notice notice--danger" data-testid="operation-error" role="alert">
      {{ operationError }}
    </p>

    <PageState
      v-if="loading"
      state="loading"
      title="正在加载作业"
      message="同步生命周期、提交数与已完成批阅摘要。"
    />
    <PageState
      v-else-if="loadError"
      state="error"
      title="作业管理加载失败"
      :message="loadError"
      retry-label="重新加载"
      @retry="loadHomeworks"
    />
    <DataTable
      v-else
      :columns="columns"
      :rows="filteredRows"
      caption="当前课程作业管理列表"
      row-key="id"
      :row-label="rowLabel"
      empty-title="当前筛选下没有作业"
      :empty-message="homeworks.length === 0 ? '创建第一份作业后会显示在这里。' : '调整关键词、状态或本页草稿条件后再试。'"
    >
      <template #cell-title="{ row }">
        <div class="homework-title-cell">
          <RouterLink
            class="homework-title-link"
            :to="{ name: 'homework-manage-detail', params: { courseId, homeworkId: rowId(row) } }"
          >
            {{ row.title }}
          </RouterLink>
          <small>{{ row.typeLabel }} · 满分 {{ row.totalScore }}</small>
        </div>
      </template>

      <template #cell-status="{ row }">
        <StatusBadge :label="String(row.statusLabel)" :tone="rowTone(row)" />
      </template>

      <template #cell-deadline="{ row }">
        <div class="homework-deadline">
          <span>{{ row.deadlineLabel }}</span>
          <small>{{ row.deadlineHint }}</small>
        </div>
      </template>

      <template #cell-submissions="{ row }">
        <div class="homework-submission-counts">
          <strong>{{ row.submissionCountLabel }}</strong>
          <small>{{ row.reviewedLabel }}</small>
        </div>
      </template>

      <template #cell-actions="{ row }">
        <div class="homework-row-actions">
          <RouterLink
            :data-testid="`manage-homework-${rowId(row)}`"
            :to="{ name: 'homework-manage-detail', params: { courseId, homeworkId: rowId(row) } }"
          >
            进入
          </RouterLink>
          <RouterLink
            v-if="isEditable(rowStatus(row))"
            :data-testid="`edit-homework-${rowId(row)}`"
            :to="{ name: 'homework-edit', params: { courseId, homeworkId: rowId(row) } }"
          >
            编辑
          </RouterLink>
          <RouterLink
            v-if="isReleased(rowStatus(row))"
            :data-testid="`submissions-homework-${rowId(row)}`"
            :to="{ name: 'homework-submission-workspace', params: { courseId, homeworkId: rowId(row) } }"
          >
            提交队列
          </RouterLink>
          <RouterLink
            v-if="isReleased(rowStatus(row))"
            :data-testid="`statistics-homework-${rowId(row)}`"
            :to="{ name: 'homework-statistics', params: { courseId, homeworkId: rowId(row) } }"
          >
            统计
          </RouterLink>
          <button
            v-if="isEditable(rowStatus(row)) && rowType(row) !== 'FILE'"
            :data-testid="`publish-homework-${rowId(row)}`"
            type="button"
            :disabled="pendingAction !== null"
            @click="runLifecycleRow(row, 'publish')"
          >
            {{ pendingLabel(rowId(row), 'publish', '发布') }}
          </button>
          <span
            v-if="isEditable(rowStatus(row)) && rowType(row) === 'FILE'"
            class="contract-blocker"
            :data-testid="`file-contract-blocked-${rowId(row)}`"
          >
            #214 附件提交契约待补齐，暂不可发布
          </span>
          <button
            v-if="rowStatus(row) === 'PUBLISHED'"
            :data-testid="`close-homework-${rowId(row)}`"
            type="button"
            :disabled="pendingAction !== null"
            @click="runLifecycleRow(row, 'close')"
          >
            {{ pendingLabel(rowId(row), 'close', '关闭') }}
          </button>
          <button
            v-if="rowStatus(row) === 'PUBLISHED' || rowStatus(row) === 'CLOSED'"
            :data-testid="`release-homework-${rowId(row)}`"
            type="button"
            :disabled="pendingAction !== null"
            @click="runLifecycleRow(row, 'release')"
          >
            {{ pendingLabel(rowId(row), 'release', '发布成绩') }}
          </button>
          <button
            v-if="isEditable(rowStatus(row))"
            class="danger-action"
            :data-testid="`delete-homework-${rowId(row)}`"
            type="button"
            :disabled="pendingAction !== null"
            @click="runLifecycleRow(row, 'delete')"
          >
            {{ pendingLabel(rowId(row), 'delete', '删除草稿') }}
          </button>
        </div>
      </template>
    </DataTable>

    <nav v-if="!loading && !loadError && total > 0" class="homework-pager" aria-label="作业分页">
      <button
        class="button button--secondary"
        data-action="previous-homework-page"
        type="button"
        :disabled="currentPage <= 1 || loading"
        @click="goToPage(currentPage - 1)"
      >上一页</button>
      <span>
        第 {{ currentPage }} / {{ totalPages }} 页 · 服务端筛选共 {{ total }} 份
        <template v-if="attentionIsApplied"> · 本页显示 {{ filteredRows.length }} 份</template>
      </span>
      <button
        class="button button--secondary"
        data-action="next-homework-page"
        type="button"
        :disabled="currentPage >= totalPages || loading"
        @click="goToPage(currentPage + 1)"
      >下一页</button>
    </nav>
  </main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import {
  closeHomework,
  deleteHomework,
  getHomeworkDetail,
  getHomeworkStatistics,
  listHomeworks,
  publishHomework,
  publishHomeworkScores
} from '../../api/hwk/homeworks';
import { currentCourse } from '../../app/runtimeContext';
import DataTable, { type DataTableColumn, type DataTableRow } from '../../components/foundation/DataTable.vue';
import FilterBar, { type FilterFieldModel } from '../../components/foundation/FilterBar.vue';
import PageHeader from '../../components/foundation/PageHeader.vue';
import PageState from '../../components/foundation/PageState.vue';
import StatusBadge, { type StatusBadgeTone } from '../../components/foundation/StatusBadge.vue';
import SummaryStrip, { type SummaryStripItem } from '../../components/foundation/SummaryStrip.vue';
import type { HomeworkStatistics, HomeworkStatus, HomeworkSummary, HomeworkType } from '../../types/hwk';
import { formatHomeworkStatus, formatHomeworkType } from './hwkDisplay';

type LifecycleAction = 'publish' | 'close' | 'release' | 'delete';

interface HomeworkRow extends DataTableRow {
  id: number;
  title: string;
  status: HomeworkStatus;
  statusLabel: string;
  statusTone: StatusBadgeTone;
  type: HomeworkType;
  typeLabel: string;
  totalScore: number;
  deadlineLabel: string;
  deadlineHint: string;
  submissionCount: number | null;
  submissionCountLabel: string;
  reviewedCount: number | null;
  reviewedLabel: string;
}

const props = defineProps<{ courseId: number }>();
const homeworks = ref<HomeworkSummary[]>([]);
const statisticsByHomework = ref(new Map<number, HomeworkStatistics | null>());
const loading = ref(false);
const loadError = ref('');
const partialWarning = ref('');
const operationFeedback = ref('');
const operationError = ref('');
const pendingAction = ref<{ homeworkId: number; action: LifecycleAction } | null>(null);
const filterDraft = ref<Record<string, string>>({ keyword: '', status: '', attention: '' });
const appliedFilters = ref<Record<string, string>>({ keyword: '', status: '', attention: '' });
const currentPage = ref(1);
const total = ref(0);
const pageSize = 20;
let loadGeneration = 0;

const courseName = computed(() => (
  currentCourse.value?.id === props.courseId ? currentCourse.value.name : '当前课程'
));

const filterFields: readonly FilterFieldModel[] = [
  { key: 'keyword', label: '搜索作业', kind: 'search', placeholder: '输入作业名称' },
  {
    key: 'status',
    label: '生命周期',
    kind: 'select',
    options: [
      { value: '', label: '全部状态' },
      { value: 'DRAFT', label: '草稿' },
      { value: 'NOT_OPEN', label: '未开放' },
      { value: 'PUBLISHED', label: '已发布' },
      { value: 'CLOSED', label: '已关闭' },
      { value: 'SCORE_PUBLISHED', label: '成绩已发布' },
      { value: 'ARCHIVED', label: '已归档' }
    ]
  },
  {
    key: 'attention',
    label: '本页待处理',
    kind: 'select',
    options: [
      { value: '', label: '全部作业' },
      { value: 'draft', label: '本页待发布草稿' }
    ]
  }
];

const columns: readonly DataTableColumn[] = [
  { key: 'title', label: '作业', width: '25%' },
  { key: 'status', label: '状态', width: '12%' },
  { key: 'deadline', label: '截止时间', width: '17%' },
  { key: 'submissions', label: '提交与批阅', width: '16%' },
  { key: 'actions', label: '操作', width: '30%' }
];

const rows = computed<HomeworkRow[]>(() => homeworks.value.map((homework) => {
  const statistics = statisticsByHomework.value.get(homework.id);
  const reviewedCount = statistics?.reviewedCount ?? null;
  const remaining = new Date(homework.deadline).getTime() - Date.now();
  const unpublished = !isReleased(homework.status);
  return {
    id: homework.id,
    title: homework.title,
    status: homework.status,
    statusLabel: formatHomeworkStatus(homework.status),
    statusTone: homeworkStatusTone(homework.status),
    type: homework.type,
    typeLabel: formatHomeworkType(homework.type),
    totalScore: homework.totalScore,
    deadlineLabel: formatDateTime(homework.deadline),
    deadlineHint: remaining > 0
      ? `距截止 ${Math.max(1, Math.ceil(remaining / 86_400_000))} 天`
      : '截止时间已过',
    submissionCount: statistics?.submittedCount ?? null,
    submissionCountLabel: unpublished
      ? '尚未发布'
      : statistics
        ? `${statistics.submittedCount} / ${statistics.totalStudentCount} 人已提交`
        : '提交摘要暂不可用',
    reviewedCount,
    reviewedLabel: unpublished
      ? '发布后生成提交摘要'
      : reviewedCount === null
        ? '已完成批阅数量未知'
        : `${reviewedCount} 份已完成批阅`
  };
}));

const filteredRows = computed(() => {
  const keyword = (appliedFilters.value.keyword ?? '').trim().toLowerCase();
  const status = appliedFilters.value.status ?? '';
  const attention = appliedFilters.value.attention ?? '';
  return rows.value.filter((row) => {
    if (keyword && !row.title.toLowerCase().includes(keyword)) return false;
    if (status && row.status !== status) return false;
    if (attention === 'draft' && !isEditable(row.status)) return false;
    return true;
  });
});

const attentionIsApplied = computed(() => Boolean(appliedFilters.value.attention));
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));

const summaryItems = computed<SummaryStripItem[]>(() => [
  {
    key: 'total',
    label: '服务端匹配',
    value: total.value,
    hint: '关键词与生命周期命中的作业总数',
    tone: 'brand'
  },
  {
    key: 'active',
    label: '已发布',
    value: homeworks.value.filter((homework) => homework.status === 'PUBLISHED').length,
    hint: '当前页学生可提交作业'
  },
  {
    key: 'review',
    label: '已完成批阅',
    value: rows.value.reduce((total, row) => total + (row.reviewedCount ?? 0), 0),
    hint: partialWarning.value ? '部分摘要暂不可用' : '当前页接口原始批阅计数合计',
    tone: rows.value.some((row) => (row.reviewedCount ?? 0) > 0) ? 'success' : 'neutral'
  },
  {
    key: 'released',
    label: '成绩已发布',
    value: homeworks.value.filter((homework) => ['SCORE_PUBLISHED', 'ARCHIVED'].includes(homework.status)).length,
    hint: '当前页学生可查看成绩',
    tone: 'success'
  }
]);

watch(
  () => props.courseId,
  () => void loadHomeworks(),
  { immediate: true }
);

async function loadHomeworks() {
  const generation = ++loadGeneration;
  loading.value = true;
  loadError.value = '';
  partialWarning.value = '';
  try {
    const keyword = (appliedFilters.value.keyword ?? '').trim();
    const status = (appliedFilters.value.status ?? '') as HomeworkStatus | '';
    const result = await listHomeworks({
      courseId: props.courseId,
      page: currentPage.value,
      size: pageSize,
      ...(keyword ? { keyword } : {}),
      ...(status ? { status } : {})
    });
    if (generation !== loadGeneration) return;
    if (result.list.some((homework) => homework.courseId !== props.courseId)) {
      throw new Error('作业列表与当前课程不匹配，请重新加载。');
    }
    total.value = result.total;
    const lastPage = Math.max(1, Math.ceil(result.total / pageSize));
    if (result.total > 0 && result.list.length === 0 && result.page > lastPage) {
      currentPage.value = lastPage;
      await loadHomeworks();
      return;
    }
    currentPage.value = result.page;
    homeworks.value = result.list.filter((homework) => !homework.deleted);
    const statisticTargets = homeworks.value.filter((homework) => isReleased(homework.status));
    const statisticResults = await Promise.allSettled(
      statisticTargets.map((homework) => getHomeworkStatistics(homework.id, { page: 1, size: 20 }))
    );
    if (generation !== loadGeneration) return;
    statisticsByHomework.value = new Map(homeworks.value.map((homework) => [homework.id, null]));
    statisticTargets.forEach((homework, index) => {
      const result = statisticResults[index];
      if (result.status === 'fulfilled'
        && result.value.homeworkId === homework.id
        && result.value.courseId === props.courseId) {
        statisticsByHomework.value.set(homework.id, result.value);
      }
    });
    if (statisticResults.some((result, index) => (
      result.status === 'rejected'
      || result.value.homeworkId !== statisticTargets[index].id
      || result.value.courseId !== props.courseId
    ))) {
      partialWarning.value = '部分作业的提交与批阅摘要暂不可用；仍可进入作业详情继续处理。';
    }
  } catch (error) {
    if (generation !== loadGeneration) return;
    homeworks.value = [];
    total.value = 0;
    statisticsByHomework.value = new Map();
    loadError.value = localizedError(error, '作业管理加载失败，请稍后重试。');
  } finally {
    if (generation === loadGeneration) loading.value = false;
  }
}

function applyFilters(filters: Record<string, string>) {
  appliedFilters.value = { ...filters };
  currentPage.value = 1;
  void loadHomeworks();
}

function resetFilters() {
  filterDraft.value = { keyword: '', status: '', attention: '' };
  appliedFilters.value = { ...filterDraft.value };
  currentPage.value = 1;
  void loadHomeworks();
}

function goToPage(nextPage: number) {
  if (loading.value || nextPage < 1 || nextPage > totalPages.value) return;
  currentPage.value = nextPage;
  void loadHomeworks();
}

async function runLifecycle(row: HomeworkRow, action: LifecycleAction) {
  if (pendingAction.value) return;
  if (action === 'publish' && row.type === 'FILE') {
    operationFeedback.value = '';
    operationError.value = '#214 附件上传与安全提交链路完成前不可发布 FILE 作业。';
    return;
  }
  const copy = lifecycleCopy(action, row.title);
  operationFeedback.value = '';
  operationError.value = '';
  if (action === 'publish' && row.type === 'CODE') {
    pendingAction.value = { homeworkId: row.id, action };
    try {
      const detail = await getHomeworkDetail(row.id);
      if (detail.id !== row.id || detail.courseId !== props.courseId || detail.type !== 'CODE') {
        throw new Error('作业配置与当前课程不匹配，请重新加载后再发布。');
      }
      if (!usesOnlySupportedCodeLanguages(detail.languageLimitJson)) {
        operationError.value = '当前评测沙箱仅支持 Python；请先进入编辑器移除 Java、C++ 或 JavaScript 等未支持语言。';
        pendingAction.value = null;
        return;
      }
    } catch (error) {
      operationError.value = localizedError(error, '发布前无法读取代码语言配置，请稍后重试。');
      pendingAction.value = null;
      return;
    }
  }
  if (!window.confirm(copy.confirm)) {
    pendingAction.value = null;
    return;
  }
  pendingAction.value ??= { homeworkId: row.id, action };
  try {
    if (action === 'publish') await publishHomework(row.id);
    if (action === 'close') await closeHomework(row.id);
    if (action === 'release') await publishHomeworkScores(row.id);
    if (action === 'delete') await deleteHomework(row.id);
    operationFeedback.value = copy.success;
    await loadHomeworks();
  } catch (error) {
    operationError.value = localizedError(error, copy.failure);
  } finally {
    pendingAction.value = null;
  }
}

function usesOnlySupportedCodeLanguages(value: string | null | undefined) {
  if (!value?.trim()) return false;
  try {
    const languages: unknown = JSON.parse(value);
    return Array.isArray(languages)
      && languages.length > 0
      && languages.every((language) => typeof language === 'string' && language.toLowerCase() === 'python');
  } catch {
    return false;
  }
}

function lifecycleCopy(action: LifecycleAction, title: string) {
  return {
    publish: {
      confirm: `确认发布“${title}”？发布后课程学生将看到作业并可按规则提交。`,
      success: `“${title}”发布成功。`,
      failure: '作业发布失败，请核对题目、用例与截止时间后重试。'
    },
    close: {
      confirm: `确认关闭“${title}”？关闭后将停止常规提交。`,
      success: `“${title}”已关闭。`,
      failure: '作业关闭失败，请刷新状态后重试。'
    },
    release: {
      confirm: `确认发布“${title}”的成绩？学生将看到最终分与公开反馈。`,
      success: `“${title}”成绩发布成功。`,
      failure: '成绩发布失败，请确认批阅状态后重试。'
    },
    delete: {
      confirm: `确认删除草稿“${title}”？作业将从列表隐藏，但题目、用例与历史记录会保留。`,
      success: `草稿“${title}”已删除。`,
      failure: '草稿删除失败，请刷新状态后重试。'
    }
  }[action];
}

function pendingLabel(homeworkId: number, action: LifecycleAction, label: string) {
  return pendingAction.value?.homeworkId === homeworkId && pendingAction.value.action === action
    ? '处理中…'
    : label;
}

function rowId(row: DataTableRow) {
  return Number(row.id);
}

function rowStatus(row: DataTableRow) {
  return row.status as HomeworkStatus;
}

function rowType(row: DataTableRow) {
  return row.type as HomeworkType;
}

function rowTone(row: DataTableRow) {
  return row.statusTone as StatusBadgeTone;
}

function runLifecycleRow(row: DataTableRow, action: LifecycleAction) {
  return runLifecycle(row as HomeworkRow, action);
}

function rowLabel(row: DataTableRow) {
  return `作业：${String(row.title)}`;
}

function isEditable(status: HomeworkStatus) {
  return status === 'DRAFT';
}

function isReleased(status: HomeworkStatus) {
  return status === 'PUBLISHED'
    || status === 'CLOSED'
    || status === 'SCORE_PUBLISHED'
    || status === 'ARCHIVED';
}

function homeworkStatusTone(status: HomeworkStatus): StatusBadgeTone {
  const tones: Record<HomeworkStatus, StatusBadgeTone> = {
    DRAFT: 'neutral',
    NOT_OPEN: 'neutral',
    PUBLISHED: 'brand',
    CLOSED: 'warning',
    SCORE_PUBLISHED: 'success',
    ARCHIVED: 'neutral'
  };
  return tones[status];
}

function formatDateTime(value: string) {
  return value.replace('T', ' ').slice(0, 16);
}

function localizedError(error: unknown, fallback: string) {
  const message = error instanceof Error ? error.message.trim() : '';
  return /[\u3400-\u9fff]/u.test(message) ? message : fallback;
}
</script>

<style scoped>
.homework-teacher-index {
  display: grid;
  gap: 18px;
  width: 100%;
  min-width: 0;
  padding-bottom: 36px;
  color: var(--oj-ink);
}

.button,
.homework-row-actions a,
.homework-row-actions button {
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

.homework-title-cell,
.homework-deadline,
.homework-submission-counts {
  display: grid;
  gap: 4px;
}

.homework-title-link {
  color: var(--oj-ink);
  font-weight: 800;
  text-decoration: none;
}

.homework-title-cell small,
.homework-deadline small,
.homework-submission-counts small {
  color: var(--oj-muted);
  font-size: 0.75rem;
}

.homework-row-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.homework-row-actions button:disabled {
  cursor: wait;
  opacity: 0.58;
}

.homework-row-actions .danger-action {
  border-color: rgba(180, 35, 24, 0.28);
  color: #8f2d24;
}

.contract-blocker {
  max-width: 190px;
  color: #7c4a03;
  font-size: 0.74rem;
  font-weight: 800;
  line-height: 1.45;
}

.homework-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--oj-muted);
  font-size: 0.82rem;
  font-weight: 800;
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

.homework-teacher-index :deep(a:focus-visible),
.homework-teacher-index button:focus-visible {
  outline: 3px solid var(--oj-brand);
  outline-offset: 2px;
}

@media (max-width: 640px) {
  .homework-teacher-index {
    gap: 14px;
  }

  .homework-row-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .homework-row-actions a,
  .homework-row-actions button {
    width: 100%;
    text-align: center;
  }

  .contract-blocker {
    max-width: none;
    padding: 8px 2px;
    text-align: left;
  }

  .homework-pager {
    align-items: stretch;
    flex-direction: column;
    text-align: center;
  }
}
</style>
