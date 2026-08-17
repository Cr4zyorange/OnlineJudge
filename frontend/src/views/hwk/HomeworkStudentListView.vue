<template>
  <main class="homework-student-list" data-testid="homework-student-list">
    <PageHeader
      title="课程作业"
      :eyebrow="`UI-HWK-01 · 课程 #${courseId}`"
      subtitle="按发布状态和截止时间查找作业，并进入详情继续完成提交闭环。"
    />

    <SummaryStrip :items="summaryItems" aria-label="当前页作业摘要" />

    <FilterBar
      v-model="filterModel"
      :fields="filterFields"
      :disabled="loading"
      aria-label="筛选课程作业"
      submit-label="查询"
      @submit="applyFilters"
      @reset="resetFilters"
    />

    <PageState
      v-if="loading"
      state="loading"
      title="正在加载作业"
      message="正在同步当前课程的作业与发布状态。"
    />
    <PageState
      v-else-if="errorMessage"
      state="error"
      title="作业列表加载失败"
      :message="errorMessage"
    >
      <template #actions>
        <button
          class="homework-student-list__state-action"
          data-testid="retry-homework-list"
          type="button"
          @click="loadHomeworks"
        >
          重新加载
        </button>
      </template>
    </PageState>
    <PageState
      v-else-if="visibleRows.length === 0"
      state="empty"
      title="暂无可见作业"
      :message="hasAppliedFilters ? '当前筛选条件下没有可进入的作业，请调整条件后重试。' : '课程暂未发布学生可进入的作业。'"
    >
      <template v-if="hasAppliedFilters" #actions>
        <button class="homework-student-list__state-action" type="button" @click="resetFilters">
          清除筛选
        </button>
      </template>
    </PageState>

    <template v-else>
      <DataTable
        :columns="columns"
        :rows="visibleRows"
        caption="课程作业列表"
        row-key="id"
        :row-label="(row) => `${homeworkOf(row).title}作业`"
      >
        <template #cell-title="{ row }">
          <div class="homework-student-list__title-cell">
            <strong>{{ homeworkOf(row).title }}</strong>
            <span>{{ homeworkOf(row).description || '暂无作业说明' }}</span>
          </div>
        </template>
        <template #cell-type="{ row }">
          {{ formatHomeworkType(homeworkOf(row).type) }}
        </template>
        <template #cell-status="{ row }">
          <StatusBadge
            :label="formatHomeworkStatus(homeworkOf(row).status)"
            :tone="statusTone(homeworkOf(row).status)"
          />
        </template>
        <template #cell-deadline="{ row }">
          <div class="homework-student-list__deadline">
            <span>截止 {{ formatDateTime(homeworkOf(row).deadline) }}</span>
            <small>{{ deadlineHint(homeworkOf(row).deadline) }}</small>
          </div>
        </template>
        <template #cell-score="{ row }">
          {{ homeworkOf(row).totalScore }} 分
        </template>
        <template #cell-action="{ row }">
          <RouterLink
            class="homework-student-list__detail-link"
            :data-testid="`open-homework-${homeworkOf(row).id}`"
            :to="`/courses/${courseId}/homeworks/${homeworkOf(row).id}`"
          >
            查看
          </RouterLink>
        </template>
      </DataTable>

      <nav class="homework-student-list__pagination" aria-label="作业列表分页">
        <span>共 {{ total }} 项</span>
        <div>
          <button
            data-testid="previous-homework-page"
            type="button"
            :disabled="page <= 1 || loading"
            @click="changePage(page - 1)"
          >
            上一页
          </button>
          <span>第 {{ page }} / {{ pageCount }} 页</span>
          <button
            data-testid="next-homework-page"
            type="button"
            :disabled="page >= pageCount || loading"
            @click="changePage(page + 1)"
          >
            下一页
          </button>
        </div>
      </nav>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import { listHomeworks, type HomeworkListQuery } from '../../api/hwk/homeworks';
import DataTable, {
  type DataTableColumn,
  type DataTableRow
} from '../../components/foundation/DataTable.vue';
import FilterBar, { type FilterFieldModel } from '../../components/foundation/FilterBar.vue';
import PageHeader from '../../components/foundation/PageHeader.vue';
import PageState from '../../components/foundation/PageState.vue';
import StatusBadge, { type StatusBadgeTone } from '../../components/foundation/StatusBadge.vue';
import SummaryStrip, { type SummaryStripItem } from '../../components/foundation/SummaryStrip.vue';
import type { HomeworkStatus, HomeworkSummary } from '../../types/hwk';
import { formatHomeworkStatus, formatHomeworkType } from './hwkDisplay';

interface HomeworkListRow extends HomeworkSummary, DataTableRow {}

const props = defineProps<{
  courseId: number;
}>();

const defaultFilters = Object.freeze({
  keyword: '',
  status: '',
  size: '20'
});

const filterFields: readonly FilterFieldModel[] = [
  {
    key: 'keyword',
    label: '关键词',
    kind: 'search',
    placeholder: '搜索作业标题'
  },
  {
    key: 'status',
    label: '发布状态',
    kind: 'select',
    options: [
      { value: '', label: '全部学生可见状态' },
      { value: 'PUBLISHED', label: '已发布' },
      { value: 'CLOSED', label: '已关闭' },
      { value: 'SCORE_PUBLISHED', label: '成绩已发布' },
      { value: 'ARCHIVED', label: '已归档' }
    ]
  },
  {
    key: 'size',
    label: '每页数量',
    kind: 'select',
    options: [
      { value: '10', label: '10 项 / 页' },
      { value: '20', label: '20 项 / 页' },
      { value: '50', label: '50 项 / 页' }
    ]
  }
];

const columns: readonly DataTableColumn[] = [
  { key: 'title', label: '作业', mobileLabel: '作业' },
  { key: 'type', label: '类型', mobileLabel: '类型', width: '120px' },
  { key: 'status', label: '状态', mobileLabel: '状态', width: '128px' },
  { key: 'deadline', label: '截止时间', mobileLabel: '截止时间', width: '190px' },
  { key: 'score', label: '满分', mobileLabel: '满分', align: 'end', width: '80px' },
  { key: 'action', label: '操作', mobileLabel: '操作', align: 'end', width: '92px' }
];

const loading = ref(true);
const errorMessage = ref('');
const homeworks = ref<HomeworkSummary[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const filterModel = ref<Record<string, string>>({ ...defaultFilters });
const appliedKeyword = ref('');
const appliedStatus = ref<HomeworkStatus | ''>('');
let latestRequest = 0;

const studentVisibleStatuses: readonly HomeworkStatus[] = [
  'PUBLISHED',
  'CLOSED',
  'SCORE_PUBLISHED',
  'ARCHIVED'
];
const visibleRows = computed<HomeworkListRow[]>(() => homeworks.value
  .filter((homework) => !homework.deleted && studentVisibleStatuses.includes(homework.status))
  .map((homework) => ({ ...homework })));
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
const hasAppliedFilters = computed(() => Boolean(appliedKeyword.value || appliedStatus.value));
const summaryItems = computed<SummaryStripItem[]>(() => {
  const statusCount = (status: HomeworkStatus) => visibleRows.value.filter((item) => item.status === status).length;
  return [
    {
      key: 'visible',
      label: '本页可见',
      value: visibleRows.value.length,
      hint: total.value ? `服务端共 ${total.value} 项` : '等待课程发布',
      tone: 'brand'
    },
    {
      key: 'active',
      label: '可继续完成',
      value: statusCount('PUBLISHED'),
      hint: '以发布状态为准'
    },
    {
      key: 'closed',
      label: '已关闭',
      value: statusCount('CLOSED'),
      hint: '提交入口已关闭',
      tone: statusCount('CLOSED') ? 'warning' : 'neutral'
    },
    {
      key: 'score',
      label: '成绩可查看',
      value: statusCount('SCORE_PUBLISHED') + statusCount('ARCHIVED'),
      hint: '含成绩已发布与归档作业',
      tone: statusCount('SCORE_PUBLISHED') + statusCount('ARCHIVED') ? 'success' : 'neutral'
    }
  ];
});

onMounted(loadHomeworks);
watch(() => props.courseId, () => {
  page.value = 1;
  void loadHomeworks();
});

async function loadHomeworks() {
  const requestNumber = ++latestRequest;
  loading.value = true;
  errorMessage.value = '';

  const query: HomeworkListQuery = {
    courseId: props.courseId,
    page: page.value,
    size: pageSize.value
  };
  if (appliedKeyword.value) {
    query.keyword = appliedKeyword.value;
  }
  if (appliedStatus.value) {
    query.status = appliedStatus.value;
  }

  try {
    const response = await listHomeworks(query);
    if (requestNumber !== latestRequest) {
      return;
    }
    homeworks.value = response.list;
    total.value = response.total;
    page.value = response.page;
    pageSize.value = response.size;
  } catch (error) {
    if (requestNumber !== latestRequest) {
      return;
    }
    homeworks.value = [];
    total.value = 0;
    errorMessage.value = error instanceof Error ? error.message : '请稍后重试';
  } finally {
    if (requestNumber === latestRequest) {
      loading.value = false;
    }
  }
}

function applyFilters(value: Record<string, string>) {
  filterModel.value = { ...value };
  appliedKeyword.value = value.keyword?.trim() ?? '';
  appliedStatus.value = homeworkStatus(value.status);
  pageSize.value = normalizedPageSize(value.size);
  page.value = 1;
  void loadHomeworks();
}

function resetFilters() {
  filterModel.value = { ...defaultFilters };
  appliedKeyword.value = '';
  appliedStatus.value = '';
  pageSize.value = 20;
  page.value = 1;
  void loadHomeworks();
}

function changePage(nextPage: number) {
  if (nextPage < 1 || nextPage > pageCount.value || nextPage === page.value) {
    return;
  }
  page.value = nextPage;
  void loadHomeworks();
}

function homeworkStatus(value: string | undefined): HomeworkStatus | '' {
  return studentVisibleStatuses.includes(value as HomeworkStatus)
    ? value as HomeworkStatus
    : '';
}

function normalizedPageSize(value: string | undefined) {
  const size = Number(value);
  return [10, 20, 50].includes(size) ? size : 20;
}

function homeworkOf(row: DataTableRow) {
  return row as HomeworkListRow;
}

function statusTone(status: HomeworkStatus): StatusBadgeTone {
  const tones: Record<HomeworkStatus, StatusBadgeTone> = {
    DRAFT: 'neutral',
    NOT_OPEN: 'neutral',
    PUBLISHED: 'info',
    CLOSED: 'warning',
    SCORE_PUBLISHED: 'success',
    ARCHIVED: 'neutral'
  };
  return tones[status];
}

function deadlineHint(value: string) {
  const difference = new Date(value).getTime() - Date.now();
  if (!Number.isFinite(difference)) {
    return '截止时间待确认';
  }
  if (difference <= 0) {
    return '截止时间已过';
  }
  const hours = Math.ceil(difference / 3_600_000);
  return hours < 24 ? `剩余约 ${hours} 小时` : `剩余约 ${Math.ceil(hours / 24)} 天`;
}

function formatDateTime(value: string) {
  return value.replace('T', ' ').slice(0, 16);
}
</script>

<style scoped>
.homework-student-list {
  display: grid;
  gap: 16px;
  min-width: 0;
  min-height: 100vh;
  padding-bottom: 40px;
  color: var(--oj-ink);
}

.homework-student-list__title-cell,
.homework-student-list__deadline {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.homework-student-list__title-cell strong {
  color: var(--oj-ink);
  font-size: 0.94rem;
  overflow-wrap: anywhere;
}

.homework-student-list__title-cell span,
.homework-student-list__deadline small {
  color: var(--oj-ink-soft);
  font-size: 0.76rem;
  line-height: 1.5;
}

.homework-student-list__detail-link,
.homework-student-list__state-action,
.homework-student-list__pagination button {
  min-height: 38px;
  padding: 8px 13px;
  border: 1px solid var(--oj-brand);
  border-radius: var(--oj-radius-control);
  background: var(--oj-brand);
  color: #fff;
  cursor: pointer;
  font: inherit;
  font-weight: 800;
  text-decoration: none;
}

.homework-student-list__detail-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.homework-student-list__pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-width: 0;
  color: var(--oj-ink-soft);
  font-size: 0.86rem;
  font-weight: 700;
}

.homework-student-list__pagination > div {
  display: flex;
  align-items: center;
  gap: 10px;
}

.homework-student-list__pagination button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.homework-student-list__detail-link:hover,
.homework-student-list__detail-link:focus-visible,
.homework-student-list__state-action:hover,
.homework-student-list__state-action:focus-visible,
.homework-student-list__pagination button:hover:not(:disabled),
.homework-student-list__pagination button:focus-visible:not(:disabled) {
  background: var(--oj-brand-strong);
}

@media (max-width: 640px) {
  .homework-student-list {
    gap: 10px;
    padding-bottom: 24px;
  }

  .homework-student-list__pagination {
    align-items: stretch;
    flex-direction: column;
  }

  .homework-student-list__pagination > div {
    display: grid;
    grid-template-columns: minmax(86px, 1fr) auto minmax(86px, 1fr);
  }

  .homework-student-list__detail-link,
  .homework-student-list__pagination button {
    width: 100%;
  }
}
</style>
