<template>
  <main class="statistics-page">
    <nav class="statistics-page__topbar" aria-label="页面导航">
      <a class="statistics-page__home" data-testid="lrn-home-entry" href="/learning/tasks" aria-label="返回学习任务中心">
        &lt;-
      </a>
    </nav>
    <section class="statistics-page__shell">
      <aside class="statistics-page__summary" aria-label="学习行为概览">
        <h1>学习行为仪表盘</h1>
        <p>查看近 7 天学习时长、资源访问、任务提交与完成情况。</p>
        <dl>
          <div>
            <dt>学习时长</dt>
            <dd>{{ formattedTotalDuration }}</dd>
          </div>
          <div>
            <dt>访问次数</dt>
            <dd>{{ overview?.summary.resourceAccessCount ?? 0 }}</dd>
          </div>
          <div>
            <dt>完成任务</dt>
            <dd>{{ overview?.summary.completedTaskCount ?? 0 }}</dd>
          </div>
        </dl>
      </aside>

      <section class="statistics-page__content" aria-label="近7天学习行为">
        <header class="statistics-page__header">
          <div>
            <h2>我的学习趋势</h2>
          </div>
          <button type="button" :disabled="loading" data-testid="retry-statistics" @click="loadStatistics">
            刷新
          </button>
        </header>

        <p v-if="loading" class="statistics-page__state">加载中...</p>
        <section v-else-if="errorMessage" class="statistics-page__state statistics-page__state--error">
          <p>{{ errorMessage }}</p>
          <button type="button" data-testid="retry-statistics" @click="loadStatistics">重试</button>
        </section>
        <p v-else-if="!overview" class="statistics-page__state">暂无学习行为数据</p>

        <template v-else>
          <p v-if="overview.fromCache" class="statistics-page__cache">当前展示本地缓存数据</p>

          <section class="metric-grid" aria-label="行为统计">
            <article class="metric-card">
              <span>总记录</span>
              <strong>{{ overview.summary.totalRecordCount }}</strong>
            </article>
            <article class="metric-card">
              <span>提交任务</span>
              <strong>{{ overview.summary.submittedTaskCount }}</strong>
            </article>
            <article class="metric-card">
              <span>完成任务</span>
              <strong>{{ overview.summary.completedTaskCount }}</strong>
            </article>
            <article class="metric-card">
              <span>访问次数</span>
              <strong>{{ overview.summary.resourceAccessCount }}</strong>
            </article>
          </section>

          <section class="trend-panel" aria-label="近7天趋势">
            <article v-for="point in overview.trends" :key="point.date" class="trend-day">
              <div class="trend-bar" :style="{ height: `${barHeight(point.durationSeconds)}%` }" />
              <strong>{{ shortDate(point.date) }}</strong>
              <span>{{ formatDuration(point.durationSeconds) }}</span>
            </article>
          </section>

          <section class="recent-panel" aria-label="最近学习行为">
            <header>
              <h3>最近学习行为</h3>
              <p>{{ overview.recentRecords.length }} 条</p>
            </header>
            <p v-if="overview.recentRecords.length === 0" class="statistics-page__state">暂无最近学习行为</p>
            <article v-for="record in overview.recentRecords" :key="record.id || `${record.sourceModule}-${record.sourceId}-${record.endedAt}`" class="record-row">
              <div>
                <strong>{{ record.courseName || `课程 ${record.courseId}` }}</strong>
                <p>{{ sourceLabel(record.sourceModule) }} · {{ actionLabel(record.actionType) }} · {{ formatDuration(record.durationSeconds) }}</p>
              </div>
              <time>{{ record.endedAt }}</time>
            </article>
          </section>
        </template>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getLearningStatistics } from '../../api/lrn/learningRecords';
import type { LearningProgressSourceModule, LearningRecordActionType, LearningStatisticsOverview } from '../../types/lrn';

const loading = ref(false);
const errorMessage = ref('');
const overview = ref<LearningStatisticsOverview | null>(null);

const formattedTotalDuration = computed(() => formatDuration(overview.value?.summary.totalDurationSeconds ?? 0));
const maxDuration = computed(() => Math.max(...(overview.value?.trends.map((point) => point.durationSeconds) ?? [0]), 1));

onMounted(loadStatistics);

async function loadStatistics() {
  loading.value = true;
  errorMessage.value = '';
  try {
    overview.value = await getLearningStatistics(undefined);
  } catch (error) {
    overview.value = null;
    errorMessage.value = error instanceof Error ? error.message : '统计加载失败';
  } finally {
    loading.value = false;
  }
}

function barHeight(durationSeconds: number) {
  if (durationSeconds <= 0) {
    return 8;
  }
  return Math.max(12, Math.round((durationSeconds / maxDuration.value) * 100));
}

function formatDuration(seconds: number) {
  if (seconds <= 0) {
    return '0分钟';
  }
  const minutes = Math.max(1, Math.round(seconds / 60));
  if (minutes < 60) {
    return `${minutes}分钟`;
  }
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  return remainingMinutes === 0 ? `${hours}小时` : `${hours}小时 ${remainingMinutes}分钟`;
}

function shortDate(date: string) {
  return date.slice(5);
}

function sourceLabel(sourceModule: LearningProgressSourceModule) {
  return {
    CRS: '课程资源',
    LAB: '实验',
    HWK: '作业'
  }[sourceModule];
}

function actionLabel(actionType: LearningRecordActionType) {
  return {
    ACCESS: '访问资源',
    DOWNLOAD: '下载资源',
    STUDY: '学习时长',
    SUBMIT: '提交任务',
    COMPLETE: '完成任务'
  }[actionType];
}
</script>

<style scoped>
.statistics-page {
  min-height: 100vh;
  background-image: url("../../assets/back.jpg");
  background-size: cover;
  background-position: top center;
  background-repeat: no-repeat;
  background-attachment: fixed;
  padding: 24px;
}

.statistics-page__topbar {
  display: flex;
  margin: 0 auto 18px;
  max-width: 1280px;
}

.statistics-page__home {
  align-items: center;
  background: #16423c;
  border: 1px solid #16423c;
  border-radius: 8px;
  color: #ffffff;
  display: inline-flex;
  font-weight: 800;
  min-height: 40px;
  padding: 0 14px;
  text-decoration: none;
}

.statistics-page__shell {
  display: grid;
  gap: 24px;
  grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
  margin: 0 auto;
  max-width: 1280px;
}

.statistics-page__summary,
.statistics-page__content,
.metric-card,
.trend-panel,
.recent-panel,
.record-row {
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.1);
}

.statistics-page__summary {
  align-self: start;
  display: grid;
  gap: 18px;
  padding: 24px;
  position: sticky;
  top: 24px;
}

.statistics-page__summary h1,
.statistics-page__header h2,
.recent-panel h3 {
  margin: 0;
}

.statistics-page__summary p,
.recent-panel p,
.record-row p {
  color: #000;
  margin: 0;
}

.statistics-page__summary dl {
  display: grid;
  gap: 12px;
  margin: 0;
}

.statistics-page__summary dl div,
.metric-card {
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  padding: 14px;
}

.metric-card {
  display: flex;
  align-items: flex-end;
  gap: 12px;
}


.statistics-page__summary dt,
.metric-card span {
  color: #000;
  font-size: 16px;
  line-height: 1;
}

.statistics-page__summary dd {
  color: #16423c;
  font-size: 24px;
  font-weight: 700;
  margin: 6px 0 0;
}

.metric-card strong {
  color: #16423c;
  font-size: 20px;
  font-weight: 700;
  margin: 4px 0 0;
  line-height: 1;
}

.statistics-page__content {
  display: grid;
  gap: 18px;
  padding: 24px;
}

.statistics-page__header,
.recent-panel header,
.record-row {
  align-items: center;
  display: grid;
  gap: 12px;
  grid-template-columns: 1fr auto;
}

.statistics-page__eyebrow {
  color: #16423c;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  margin: 0 0 6px;
}

button {
  background: #16423c;
  border: 0;
  border-radius: 8px;
  color: #fff;
  cursor: pointer;
  font-weight: 700;
  min-height: 40px;
  padding: 0 16px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.statistics-page__state,
.statistics-page__cache {
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 8px;
  color: #2c3e50;
  margin: 0;
  padding: 18px;
}

.statistics-page__state--error {
  color: #9f2f2f;
}

.statistics-page__cache {
  color: #16423c;
  font-weight: 700;
}

.metric-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.trend-panel {
  align-items: end;
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  min-height: 220px;
  padding: 18px;
}

.trend-day {
  align-items: center;
  display: grid;
  gap: 8px;
  grid-template-rows: 1fr auto auto;
  height: 180px;
  text-align: center;
}

.trend-bar {
  align-self: end;
  background: linear-gradient(180deg, #a8bcc9, #7898ab);
  border-radius: 0;
  min-height: 8px;
  width: 100%;
}

.trend-day strong,
.trend-day span {
  color: #2c3e50;
  font-size: 12px;
}

.recent-panel {
  display: grid;
  gap: 12px;
  padding: 18px;
}

.record-row {
  box-shadow: none;
  padding: 14px;
}

.record-row time {
  color: #66756f;
  font-size: 13px;
}

@media (max-width: 900px) {
  .statistics-page__shell,
  .statistics-page__header,
  .recent-panel header,
  .record-row {
    grid-template-columns: 1fr;
  }

  .statistics-page__summary {
    position: static;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .trend-panel {
    overflow-x: auto;
  }

  .trend-day {
    min-width: 72px;
  }
}
</style>
