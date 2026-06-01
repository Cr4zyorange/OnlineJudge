<template>
  <main class="task-center">
    <section class="task-center__shell">
      <aside class="task-center__sidebar" aria-label="学习任务概览">
        <h1>学习任务中心</h1>
        <p>课程资源、实验和作业统一聚合展示。</p>
        <dl class="task-center__stats">
          <div>
            <dt>全部任务</dt>
            <dd>{{ taskPage?.total ?? 0 }}</dd>
          </div>
          <div>
            <dt>已逾期</dt>
            <dd>{{ overdueCount }}</dd>
          </div>
          <div>
            <dt>进行中</dt>
            <dd>{{ inProgressCount }}</dd>
          </div>
        </dl>
      </aside>

      <section class="task-center__content" aria-label="学习任务列表">
        <header class="task-center__header">
          <div>
            <p class="task-center__eyebrow">UI-LRN-01</p>
            <h2>我的学习任务</h2>
          </div>
          <button type="button" class="task-center__refresh" :disabled="loading" @click="loadTasks">
            刷新
          </button>
        </header>

        <form class="task-center__filters" aria-label="任务筛选">
          <label>
            <span>任务类型</span>
            <select v-model="selectedTaskType" name="taskType" @change="reloadFromFirstPage">
              <option value="">全部类型</option>
              <option value="RESOURCE">课程资源</option>
              <option value="EXPERIMENT">实验</option>
              <option value="HOMEWORK">作业</option>
            </select>
          </label>
          <label>
            <span>完成状态</span>
            <select v-model="selectedStatus" name="status" @change="reloadFromFirstPage">
              <option value="">全部状态</option>
              <option value="NOT_STARTED">未开始</option>
              <option value="IN_PROGRESS">进行中</option>
              <option value="COMPLETED">已完成</option>
              <option value="OVERDUE">已逾期</option>
            </select>
          </label>
          <label>
            <span>排序字段</span>
            <select v-model="sortBy" name="sortBy" @change="reloadFromFirstPage">
              <option value="deadline">截止时间</option>
              <option value="createdAt">创建时间</option>
            </select>
          </label>
          <label>
            <span>排序方向</span>
            <select v-model="order" name="order" @change="reloadFromFirstPage">
              <option value="asc">升序</option>
              <option value="desc">降序</option>
            </select>
          </label>
        </form>

        <p v-if="loading" class="task-center__state">加载中</p>
        <section v-else-if="errorMessage" class="task-center__state task-center__state--error">
          <p>{{ errorMessage }}</p>
          <button type="button" data-testid="retry-tasks" @click="loadTasks">重试</button>
        </section>
        <p v-else-if="tasks.length === 0" class="task-center__state">暂无符合条件的学习任务</p>

        <div v-else class="task-center__list">
          <article v-for="task in tasks" :key="`${task.taskType}-${task.taskId}`" class="task-card">
            <div class="task-card__main">
              <div class="task-card__badges">
                <span>{{ taskTypeLabel(task.taskType) }}</span>
                <span :class="['task-card__status', `task-card__status--${task.status.toLowerCase()}`]">
                  {{ statusLabel(task.status) }}
                </span>
              </div>
              <h3>{{ task.title }}</h3>
              <p>{{ task.courseName }}</p>
            </div>
            <dl class="task-card__meta">
              <div>
                <dt>截止时间</dt>
                <dd>{{ task.deadline ?? '无截止时间' }}</dd>
              </div>
              <div>
                <dt>完成进度</dt>
                <dd>{{ task.progress }}%</dd>
              </div>
            </dl>
            <div class="task-card__progress" aria-hidden="true">
              <span :style="{ width: `${task.progress}%` }" />
            </div>
            <a class="task-card__link" :href="task.actionUrl ?? '#'">进入任务</a>
          </article>
        </div>

        <nav
          v-if="taskPage && taskPage.total > 0"
          class="task-center__pagination"
          aria-label="任务列表分页"
        >
          <button
            type="button"
            data-testid="prev-page"
            :disabled="loading || !canGoPrevious"
            @click="goPreviousPage"
          >
            上一页
          </button>
          <span>第 {{ taskPage.page }} / {{ totalPages }} 页</span>
          <button
            type="button"
            data-testid="next-page"
            :disabled="loading || !canGoNext"
            @click="goNextPage"
          >
            下一页
          </button>
        </nav>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { listLearningTasks } from '../../api/lrn/learningTasks';
import type {
  LearningTask,
  LearningTaskPage,
  LearningTaskSortBy,
  LearningTaskStatus,
  LearningTaskType,
  SortOrder
} from '../../types/lrn';

const loading = ref(false);
const errorMessage = ref('');
const taskPage = ref<LearningTaskPage | null>(null);
const selectedTaskType = ref<'' | LearningTaskType>('');
const selectedStatus = ref<'' | LearningTaskStatus>('');
const sortBy = ref<LearningTaskSortBy>('deadline');
const order = ref<SortOrder>('asc');
const page = ref(1);
const size = ref(20);

const tasks = computed(() => taskPage.value?.records ?? []);
const overdueCount = computed(() => tasks.value.filter((task) => task.status === 'OVERDUE').length);
const inProgressCount = computed(() => tasks.value.filter((task) => task.status === 'IN_PROGRESS').length);
const totalPages = computed(() => {
  if (!taskPage.value || taskPage.value.total <= 0) {
    return 1;
  }
  return Math.max(1, Math.ceil(taskPage.value.total / taskPage.value.size));
});
const canGoPrevious = computed(() => page.value > 1);
const canGoNext = computed(() => page.value < totalPages.value);

onMounted(loadTasks);

async function reloadFromFirstPage() {
  page.value = 1;
  await loadTasks();
}

async function loadTasks() {
  loading.value = true;
  errorMessage.value = '';
  try {
    taskPage.value = await listLearningTasks({
      taskType: selectedTaskType.value ? [selectedTaskType.value] : undefined,
      status: selectedStatus.value || undefined,
      sortBy: sortBy.value,
      order: order.value,
      page: page.value,
      size: size.value
    });
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '任务列表加载失败';
  } finally {
    loading.value = false;
  }
}

async function goPreviousPage() {
  if (!canGoPrevious.value) {
    return;
  }
  page.value -= 1;
  await loadTasks();
}

async function goNextPage() {
  if (!canGoNext.value) {
    return;
  }
  page.value += 1;
  await loadTasks();
}

function taskTypeLabel(type: LearningTask['taskType']) {
  const labels: Record<LearningTask['taskType'], string> = {
    RESOURCE: '课程资源',
    EXPERIMENT: '实验',
    HOMEWORK: '作业'
  };
  return labels[type];
}

function statusLabel(status: LearningTask['status']) {
  const labels: Record<LearningTask['status'], string> = {
    NOT_STARTED: '未开始',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    OVERDUE: '已逾期'
  };
  return labels[status];
}
</script>

<style scoped>
/* 背景：纯清晰图，无模糊渐变（和style.css一致） */
.task-center {
  min-height: 100vh;
  background-image: url("../../assets/back.jpg");
  background-size: cover;
  background-position: top center;
  background-repeat: no-repeat;
  background-attachment: fixed;
}

/* 以下所有布局代码 100% 原样保留，未改动任何结构 */
.task-center__shell {
  display: grid;
  gap: 24px;
  grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
  margin: 0 auto;
  max-width: 1280px;
}

/* 穿透优先级，强制生效毛玻璃 + 透明度（唯一修改的样式） */
::v-deep .task-center__sidebar,
::v-deep .task-center__content {
  background: rgba(255, 255, 255, 0.15) !important;
  backdrop-filter: blur(12px) !important;
  -webkit-backdrop-filter: blur(12px) !important;
  border: 1px solid rgba(255, 255, 255, 0.2) !important;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.1);
}

.task-center__sidebar {
  align-self: start;
  display: grid;
  gap: 18px;
  padding: 24px;
  position: sticky;
  top: 24px;
}

.task-center__sidebar h1,
.task-center__header h2,
.task-card h3 {
  margin: 0;
}

.task-center__sidebar p,
.task-card p {
  color: #52615d;
  margin: 0;
}

.task-center__stats {
  display: grid;
  gap: 12px;
  margin: 0;
}

/* 卡片强制生效透明（核心修复） */
::v-deep .task-center__stats div,
::v-deep .task-center__list .task-card {
  background: rgba(255, 255, 255, 0.15) !important;
  backdrop-filter: blur(12px) !important;
  -webkit-backdrop-filter: blur(12px) !important;
  border: 1px solid rgba(255, 255, 255, 0.2) !important;
  border-radius: 8px;
}

.task-center__stats div {
  padding: 14px;
}

.task-center__stats dt,
.task-card__meta dt {
  color: #66756f;
  font-size: 13px;
}

.task-center__stats dd {
  font-size: 24px;
  font-weight: 700;
  margin: 4px 0 0;
}

.task-center__content {
  display: grid;
  gap: 18px;
  padding: 24px;
}

.task-center__header,
.task-center__filters,
.task-card,
.task-card__badges,
.task-card__meta {
  align-items: center;
  display: grid;
  gap: 12px;
}

.task-center__header {
  grid-template-columns: 1fr auto;
}

.task-center__eyebrow {
  color: #55746d;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  margin: 0 0 4px;
}

.task-center__filters {
  grid-template-columns: repeat(4, minmax(140px, 1fr));
}

.task-center__filters label {
  display: grid;
  gap: 6px;
}

.task-center__filters span {
  color: #41504c;
  font-size: 13px;
  font-weight: 600;
}

select,
button,
.task-card__link {
  border-radius: 8px;
  min-height: 40px;
}

select {
  background: #ffffff;
  border: 1px solid #becdc7;
  color: #20302c;
  padding: 0 10px;
}

button,
.task-card__link {
  background: #16423c;
  border: 1px solid #16423c;
  color: #ffffff;
  cursor: pointer;
  font-weight: 700;
  padding: 0 14px;
  text-decoration: none;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.task-center__state {
  background: rgba(255, 255, 255, 0.64);
  border: 1px dashed #b8c8c2;
  border-radius: 8px;
  margin: 0;
  padding: 24px;
}

.task-center__state--error {
  align-items: center;
  color: #9d2f22;
  display: flex;
  justify-content: space-between;
}

.task-center__list {
  display: grid;
  gap: 14px;
}

.task-center__pagination {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: flex-end;
}

.task-center__pagination span {
  color: #20302c;
  font-weight: 700;
}

.task-card {
  grid-template-columns: minmax(0, 1.3fr) minmax(240px, 0.9fr) 120px;
  padding: 18px;
}

.task-card__main {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.task-card__badges {
  display: flex;
  flex-wrap: wrap;
}

.task-card__badges span {
  background: rgba(22, 66, 60, 0.12);
  border-radius: 6px;
  color: #16423c;
  font-size: 12px;
  font-weight: 700;
  padding: 4px 8px;
}

.task-card__status--overdue {
  background: rgba(190, 49, 49, 0.14) !important;
  color: #9d2f22 !important;
}

.task-card__status--completed {
  background: rgba(36, 124, 87, 0.14) !important;
  color: #247c57 !important;
}

.task-card__meta {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
}

.task-card__meta dd {
  font-weight: 700;
  margin: 4px 0 0;
}

.task-card__progress {
  background: #d9e5df;
  border-radius: 999px;
  height: 8px;
  grid-column: 1 / 3;
  overflow: hidden;
  width: 100%;
}

.task-card__progress span {
  background: #16423c;
  display: block;
  height: 100%;
}

.task-card__link {
  align-items: center;
  display: inline-flex;
  grid-column: 3;
  grid-row: 1 / 3;
  justify-content: center;
}

@media (max-width: 980px) {
  .task-center {
    padding: 18px;
  }

  .task-center__shell,
  .task-card {
    grid-template-columns: 1fr;
  }

  .task-center__sidebar {
    position: static;
  }

  .task-center__filters {
    grid-template-columns: repeat(2, minmax(140px, 1fr));
  }

  .task-card__progress,
  .task-card__link {
    grid-column: auto;
    grid-row: auto;
  }
}

@media (max-width: 620px) {
  .task-center__filters,
  .task-center__header,
  .task-card__meta {
    grid-template-columns: 1fr;
  }

  .task-center__refresh {
    width: 100%;
  }

  .task-center__pagination {
    justify-content: stretch;
  }

  .task-center__pagination button {
    flex: 1 1 120px;
  }
}
</style>
