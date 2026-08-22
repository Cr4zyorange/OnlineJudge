<template>
  <main class="homework-manage" data-testid="homework-manage-detail">
    <PageState
      v-if="loading"
      state="loading"
      title="正在加载作业管理详情"
      message="同步作业配置与完成情况。"
    />
    <PageState
      v-else-if="loadError"
      state="error"
      title="作业管理详情加载失败"
      :message="loadError"
      retry-label="重新加载"
      @retry="loadManageView"
    />

    <template v-else-if="detail">
      <PageHeader
        :title="detail.title"
        eyebrow="单作业管理"
        :subtitle="`${courseName} · ${detail.description}`"
      >
        <template #meta>
          <StatusBadge :label="formatHomeworkStatus(detail.status)" :tone="homeworkStatusTone(detail.status)" />
          <span>{{ formatHomeworkType(detail.type) }}</span>
          <span>满分 {{ formatScore(detail.totalScore) }} 分</span>
          <span>截止 {{ formatDateTime(detail.deadline) }}</span>
        </template>
        <template #actions>
          <RouterLink class="button" :to="{ name: 'homework-manage', params: { courseId } }">
            返回作业列表
          </RouterLink>
          <RouterLink
            v-if="canEdit"
            class="button button--primary"
            :to="{ name: 'homework-edit', params: { courseId, homeworkId } }"
          >
            编辑作业
          </RouterLink>
        </template>
      </PageHeader>

      <SummaryStrip :items="summaryItems" aria-label="作业完成摘要" />

      <p v-if="statisticsWarning" class="notice notice--warning" data-testid="statistics-warning" role="status">
        {{ statisticsWarning }}；作业配置与提交队列仍可正常使用。
      </p>

      <section class="manage-grid" aria-label="作业教师任务入口">
        <RouterLink
          v-if="canProcessSubmissions"
          class="manage-card"
          :to="{ name: 'homework-submission-workspace', params: { courseId, homeworkId } }"
        >
          <span class="manage-card__eyebrow">批阅入口</span>
          <h2>提交与批阅</h2>
          <p>按学生、提交状态与评测结果定位有效版本，完成评分、评语和重评。</p>
          <strong>进入提交队列 →</strong>
        </RouterLink>

        <RouterLink
          v-if="canProcessSubmissions"
          class="manage-card"
          :to="{ name: 'homework-statistics', params: { courseId, homeworkId } }"
        >
          <span class="manage-card__eyebrow">班级视角</span>
          <h2>完成统计</h2>
          <p>查看提交、评测、批阅与成绩摘要，并定位尚未完成的学生。</p>
          <strong>查看作业统计 →</strong>
        </RouterLink>

        <RouterLink
          v-if="canEdit"
          class="manage-card"
          :to="{ name: 'homework-edit', params: { courseId, homeworkId } }"
        >
          <span class="manage-card__eyebrow">发布配置</span>
          <h2>作业内容与规则</h2>
          <p>核对基础信息、题目、测试用例、提交策略与发布检查。</p>
          <strong>进入作业编辑 →</strong>
        </RouterLink>

        <article
          v-if="!canProcessSubmissions"
          class="manage-card manage-card--disabled"
          data-testid="submission-workflow-locked"
        >
          <span class="manage-card__eyebrow">提交闭环</span>
          <h2>提交与统计尚未开放</h2>
          <p>当前状态不能接收学生提交；待作业进入已发布状态后开放队列与统计。</p>
        </article>

        <article
          v-if="!canEdit"
          class="manage-card manage-card--disabled"
          data-testid="configuration-locked"
        >
          <span class="manage-card__eyebrow">发布配置</span>
          <h2>配置已锁定</h2>
          <p>只有草稿作业可以修改；当前仍可查看配置、提交队列和统计证据。</p>
        </article>
      </section>

      <section class="configuration" aria-labelledby="homework-config-heading">
        <header>
          <div>
            <p>当前配置</p>
            <h2 id="homework-config-heading">发布与提交规则</h2>
          </div>
          <span>更新于 {{ formatDateTime(detail.updatedAt) }}</span>
        </header>
        <dl>
          <div><dt>生命周期</dt><dd>{{ formatHomeworkStatus(detail.status) }}</dd></div>
          <div><dt>作业类型</dt><dd>{{ formatHomeworkType(detail.type) }}</dd></div>
          <div><dt>满分</dt><dd>{{ formatScore(detail.totalScore) }} 分</dd></div>
          <div><dt>截止时间</dt><dd>{{ formatDateTime(detail.deadline) }}</dd></div>
          <div><dt>多次提交</dt><dd>{{ detail.allowResubmit ? '允许' : '不允许' }}</dd></div>
          <div><dt>逾期提交</dt><dd>{{ detail.allowLateSubmit ? '允许并标记' : '不允许' }}</dd></div>
          <div><dt>提前查看评测</dt><dd>{{ detail.showEvaluationBeforePublish ? '允许' : '成绩发布后可见' }}</dd></div>
          <div><dt>题目数量</dt><dd>{{ detail.questions.length }} 题</dd></div>
          <div><dt>测试用例</dt><dd>{{ detail.testCases.length }} 条</dd></div>
          <div v-if="detail.type === 'CODE'"><dt>允许语言</dt><dd>{{ languageLabel }}</dd></div>
          <div><dt>发布时间</dt><dd>{{ formatDateTime(detail.publishedAt) }}</dd></div>
          <div><dt>创建时间</dt><dd>{{ formatDateTime(detail.createdAt) }}</dd></div>
        </dl>
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import { getHomeworkDetail, getHomeworkStatistics } from '../../api/hwk/homeworks';
import { currentCourse } from '../../app/runtimeContext';
import PageHeader from '../../components/foundation/PageHeader.vue';
import PageState from '../../components/foundation/PageState.vue';
import StatusBadge, { type StatusBadgeTone } from '../../components/foundation/StatusBadge.vue';
import SummaryStrip, { type SummaryStripItem } from '../../components/foundation/SummaryStrip.vue';
import type { HomeworkDetail, HomeworkStatistics, HomeworkStatus } from '../../types/hwk';
import { formatHomeworkStatus, formatHomeworkType } from './hwkDisplay';

const props = defineProps<{ courseId: number; homeworkId: number }>();
const detail = ref<HomeworkDetail | null>(null);
const statistics = ref<HomeworkStatistics | null>(null);
const loading = ref(false);
const loadError = ref('');
const statisticsWarning = ref('');
let loadGeneration = 0;

const courseName = computed(() => (
  currentCourse.value?.id === props.courseId ? currentCourse.value.name : '当前课程'
));
const languageLabel = computed(() => formatLanguages(detail.value?.languageLimitJson));
const canEdit = computed(() => detail.value?.status === 'DRAFT');
const canProcessSubmissions = computed(() => Boolean(
  detail.value
  && ['PUBLISHED', 'CLOSED', 'SCORE_PUBLISHED', 'ARCHIVED'].includes(detail.value.status)
));
const summaryItems = computed<SummaryStripItem[]>(() => [
  {
    key: 'submitted',
    label: '已提交',
    value: statistics.value?.submittedCount ?? '暂不可用',
    hint: statistics.value
      ? `${statistics.value.totalStudentCount} 名课程学生`
      : '进入提交队列可继续处理',
    tone: 'brand'
  },
  {
    key: 'reviewed',
    label: '已完成批阅',
    value: statistics.value?.reviewedCount ?? '暂不可用',
    hint: statistics.value ? `${statistics.value.submittedCount} 份已提交 · 接口原始批阅计数` : '统计暂不可用',
    tone: (statistics.value?.reviewedCount ?? 0) > 0 ? 'success' : 'neutral'
  },
  {
    key: 'evaluated',
    label: '已评测',
    value: statistics.value?.evaluatedCount ?? '暂不可用',
    hint: statistics.value ? `${statistics.value.unsubmittedCount} 人未提交` : '提交队列仍可使用'
  },
  {
    key: 'average-score',
    label: '平均分',
    value: statistics.value?.averageScore == null ? '—' : `${formatScore(statistics.value.averageScore)} 分`,
    hint: `作业满分 ${detail.value?.totalScore ?? '—'} 分`,
    tone: 'success'
  }
]);

watch(
  () => [props.courseId, props.homeworkId] as const,
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
    const loaded = await getHomeworkDetail(props.homeworkId);
    if (generation !== loadGeneration) return;
    if (loaded.id !== props.homeworkId || loaded.courseId !== props.courseId) {
      throw new Error('作业与当前课程不匹配，请返回作业列表重新进入。');
    }
    detail.value = loaded;
    try {
      const loadedStatistics = await getHomeworkStatistics(props.homeworkId, { page: 1, size: 20 });
      if (generation !== loadGeneration) return;
      if (loadedStatistics.homeworkId !== props.homeworkId || loadedStatistics.courseId !== props.courseId) {
        throw new Error('统计结果与当前作业不匹配');
      }
      statistics.value = loadedStatistics;
    } catch (error) {
      if (generation !== loadGeneration) return;
      statisticsWarning.value = localizedError(error, '作业统计暂时不可用');
    }
  } catch (error) {
    if (generation !== loadGeneration) return;
    loadError.value = localizedError(error, '作业管理详情加载失败，请稍后重试。');
  } finally {
    if (generation === loadGeneration) loading.value = false;
  }
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

function formatDateTime(value: string | null | undefined) {
  return value?.trim() ? value.trim().replace('T', ' ').slice(0, 16) : '尚未发布';
}

function formatScore(value: number) {
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

function formatLanguages(value: string | null | undefined) {
  if (!value?.trim()) return '不限制';
  let languages: string[] = [];
  try {
    const parsed: unknown = JSON.parse(value);
    if (Array.isArray(parsed)) languages = parsed.filter((item): item is string => typeof item === 'string');
  } catch {
    languages = value.split(',');
  }
  const labels: Record<string, string> = {
    cpp: 'C++',
    c: 'C',
    java: 'Java',
    python: 'Python',
    javascript: 'JavaScript',
    typescript: 'TypeScript'
  };
  const result = languages.map((language) => language.trim()).filter(Boolean)
    .map((language) => labels[language.toLowerCase()] ?? language);
  return result.length > 0 ? result.join('、') : '不限制';
}

function localizedError(error: unknown, fallback: string) {
  const message = error instanceof Error ? error.message.trim() : '';
  return /[\u3400-\u9fff]/u.test(message) ? message : fallback;
}
</script>

<style scoped>
.homework-manage {
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

.manage-card--disabled {
  cursor: default;
  opacity: 0.78;
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

.homework-manage :where(a, button):focus-visible {
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
