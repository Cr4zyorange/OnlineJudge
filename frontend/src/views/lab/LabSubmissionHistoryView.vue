<template>
  <main class="lab-history">
    <section class="lab-history__panel" aria-label="提交历史">
      <header class="lab-history__header">
        <div>
          <h1>提交历史</h1>
          <p>查看当前实验的所有提交版本与评分依据。</p>
        </div>
        <a class="lab-history__back" :href="backHref">返回实验详情</a>
      </header>

      <p v-if="loading">加载中</p>
      <p v-else-if="errorMessage" class="lab-history__error">{{ errorMessage }}</p>
      <p v-else-if="history.length === 0" class="lab-history__empty">还没有提交记录</p>
      <div v-else class="lab-history__content">
        <ul class="lab-history__list">
          <li
            v-for="item in history"
            :key="item.submissionId"
            :data-submission-id="item.submissionId"
            class="lab-history__card"
          >
            <div class="lab-history__card-head">
              <strong>版本 {{ item.version }}</strong>
              <button type="button" @click="openDetail(item.submissionId)">查看详情</button>
            </div>
            <p>提交状态：{{ item.submitStatus }}</p>
            <p>评测状态：{{ item.evaluationStatus }}</p>
            <p>自动评测分：{{ formatScore(item.autoScore) }}</p>
            <p>最终得分：{{ formatScore(item.finalScore) }}</p>
            <p>提交时间：{{ formatDateTime(item.submittedAt) }}</p>
            <div class="lab-history__tags">
              <span v-if="item.isLatest">最新版本</span>
              <span v-if="item.isFinal">当前有效版本</span>
              <span v-if="item.isScoringBasis">当前评分依据</span>
              <span v-if="item.hasFile">包含文件</span>
            </div>
          </li>
        </ul>

        <aside class="lab-history__detail" aria-label="提交详情">
          <p v-if="detailLoading">详情加载中</p>
          <p v-else-if="detailErrorMessage" class="lab-history__error">{{ detailErrorMessage }}</p>
          <p v-else-if="detail === null">请选择一个版本查看详情</p>
          <template v-else>
            <h2>版本 {{ detail.version }}</h2>
            <p>语言：{{ detail.language }}</p>
            <p>提交状态：{{ detail.submitStatus }}</p>
            <p>评测状态：{{ detail.evaluationStatus }}</p>
            <p>文件标识：{{ detail.fileId ?? '无' }}</p>
            <pre class="lab-history__code">{{ detail.code || '本次提交未包含在线代码' }}</pre>
          </template>
        </aside>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getLabSubmissionDetail, listLabSubmissions } from '../../api/lab/labs';
import type { LabSubmissionDetail, LabSubmissionHistoryItem } from '../../types/lab';

const props = defineProps<{
  courseId: number;
  labId: number;
}>();

const loading = ref(false);
const detailLoading = ref(false);
const history = ref<LabSubmissionHistoryItem[]>([]);
const detail = ref<LabSubmissionDetail | null>(null);
const errorMessage = ref('');
const detailErrorMessage = ref('');

const backHref = computed(() => `/courses/${props.courseId}/labs/${props.labId}`);

onMounted(loadHistory);

async function loadHistory() {
  loading.value = true;
  errorMessage.value = '';
  try {
    history.value = await listLabSubmissions(props.labId);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '提交历史加载失败';
  } finally {
    loading.value = false;
  }
}

async function openDetail(submissionId: number) {
  detailLoading.value = true;
  detailErrorMessage.value = '';
  try {
    detail.value = await getLabSubmissionDetail(props.labId, submissionId);
  } catch (error) {
    detailErrorMessage.value = error instanceof Error ? error.message : '提交详情加载失败';
  } finally {
    detailLoading.value = false;
  }
}

function formatDateTime(value: string) {
  return value.replace('T', ' ').slice(0, 16);
}

function formatScore(value: number | null) {
  return value ?? '未生成';
}
</script>

<style scoped>
.lab-history {
  background: #f6f8fb;
  color: #1f2937;
  min-height: 100vh;
  padding: 24px;
}

.lab-history__panel {
  background: #ffffff;
  border: 1px solid #d7dde8;
  border-radius: 12px;
  display: grid;
  gap: 20px;
  margin: 0 auto;
  max-width: 1100px;
  padding: 24px;
}

.lab-history__header,
.lab-history__card-head,
.lab-history__content {
  display: flex;
  gap: 16px;
}

.lab-history__header,
.lab-history__card-head {
  align-items: center;
  justify-content: space-between;
}

.lab-history__content {
  align-items: flex-start;
}

.lab-history__list {
  display: grid;
  flex: 1;
  gap: 12px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.lab-history__card,
.lab-history__detail {
  background: #f8fafc;
  border: 1px solid #d7dde8;
  border-radius: 10px;
  padding: 16px;
}

.lab-history__detail {
  min-width: 320px;
  width: 360px;
}

.lab-history__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.lab-history__tags span {
  background: #e9effb;
  border-radius: 999px;
  color: #175cd3;
  padding: 4px 10px;
}

.lab-history__code {
  background: #111827;
  border-radius: 8px;
  color: #f8fafc;
  overflow-x: auto;
  padding: 12px;
  white-space: pre-wrap;
}

.lab-history__back {
  color: #175cd3;
  text-decoration: none;
}

.lab-history__error {
  color: #b42318;
}

.lab-history__empty {
  color: #667085;
}

@media (max-width: 900px) {
  .lab-history__content {
    flex-direction: column;
  }

  .lab-history__detail {
    min-width: 0;
    width: 100%;
  }
}
</style>
