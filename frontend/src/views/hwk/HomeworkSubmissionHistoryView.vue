<template>
  <main class="hwk-history">
    <section class="hwk-history__panel" aria-label="作业提交历史">
      <header class="hwk-history__header">
        <h1>提交历史</h1>
        <button type="button" :disabled="loading" @click="loadSubmissions">
          {{ loading ? '刷新中' : '刷新' }}
        </button>
      </header>

      <p v-if="loading">加载中</p>
      <p v-else-if="submissions.length === 0">暂无提交记录</p>
      <table v-else>
        <thead>
          <tr>
            <th>学生</th>
            <th>提交时间</th>
            <th>类型</th>
            <th>状态</th>
            <th>最新</th>
            <th>有效</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="submission in submissions" :key="submission.id">
            <td>{{ submission.studentId }}</td>
            <td>{{ submission.submittedAt }}</td>
            <td>{{ submission.submitType }}</td>
            <td>{{ submitStatusText(submission.submitStatus) }}</td>
            <td>{{ submission.isLatest ? '是' : '否' }}</td>
            <td>{{ submission.isFinal ? '是' : '否' }}</td>
            <td>
              <button type="button" @click="selectSubmission(submission.id)">查看</button>
            </td>
          </tr>
        </tbody>
      </table>

      <p v-if="errorMessage" class="hwk-history__error">{{ errorMessage }}</p>
    </section>

    <section class="hwk-history__panel" aria-label="提交详情">
      <p v-if="detailLoading">详情加载中</p>
      <p v-else-if="!selectedSubmission">请选择提交记录</p>
      <template v-else>
        <h2>提交 #{{ selectedSubmission.id }}</h2>
        <dl class="hwk-history__detail">
          <div>
            <dt>学生</dt>
            <dd>{{ selectedSubmission.studentId }}</dd>
          </div>
          <div>
            <dt>最新</dt>
            <dd>{{ selectedSubmission.isLatest ? '是' : '否' }}</dd>
          </div>
          <div>
            <dt>有效</dt>
            <dd>{{ selectedSubmission.isFinal ? '是' : '否' }}</dd>
          </div>
          <div>
            <dt>评测状态</dt>
            <dd>{{ selectedSubmission.evaluationStatus }}</dd>
          </div>
        </dl>
        <pre>{{ submissionContent }}</pre>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { getHomeworkSubmission, listHomeworkSubmissions } from '../../api/hwk/homeworks';
import type { HomeworkSubmission } from '../../types/hwk';

const props = defineProps<{
  homeworkId: number;
}>();

const submissions = ref<HomeworkSubmission[]>([]);
const selectedSubmission = ref<HomeworkSubmission | null>(null);
const loading = ref(false);
const detailLoading = ref(false);
const errorMessage = ref('');

const submissionContent = computed(() => {
  if (!selectedSubmission.value) {
    return '';
  }
  return selectedSubmission.value.answerText
    ?? selectedSubmission.value.answerJson
    ?? selectedSubmission.value.fileUrl
    ?? selectedSubmission.value.language
    ?? '';
});

watch(() => props.homeworkId, loadSubmissions);

onMounted(loadSubmissions);

async function loadSubmissions() {
  loading.value = true;
  errorMessage.value = '';
  try {
    submissions.value = await listHomeworkSubmissions(props.homeworkId);
    selectedSubmission.value = null;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '提交历史加载失败';
  } finally {
    loading.value = false;
  }
}

async function selectSubmission(submissionId: number) {
  detailLoading.value = true;
  errorMessage.value = '';
  try {
    selectedSubmission.value = await getHomeworkSubmission(submissionId);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '提交详情加载失败';
  } finally {
    detailLoading.value = false;
  }
}

function submitStatusText(status: HomeworkSubmission['submitStatus']) {
  const labels: Record<HomeworkSubmission['submitStatus'], string> = {
    SUBMITTED: '已提交',
    LATE: '逾期提交',
    REJECTED: '已拒绝'
  };
  return labels[status];
}
</script>

<style scoped>
.hwk-history {
  display: grid;
  gap: 20px;
  padding: 24px;
  color: #1f2a37;
}

.hwk-history__panel {
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  padding: 18px;
}

.hwk-history__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.hwk-history__header h1,
.hwk-history__panel h2 {
  margin: 0;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  border-bottom: 1px solid #e5edf5;
  padding: 10px;
  text-align: left;
}

button {
  border: 1px solid #2f6f9f;
  border-radius: 6px;
  background: #2f6f9f;
  color: #fff;
  cursor: pointer;
  font: inherit;
  padding: 8px 12px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.hwk-history__detail {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}

.hwk-history__detail div {
  border: 1px solid #e1e9f2;
  border-radius: 8px;
  padding: 12px;
}

.hwk-history__detail dt {
  color: #667085;
  font-size: 13px;
}

.hwk-history__detail dd {
  margin: 4px 0 0;
}

pre {
  white-space: pre-wrap;
  word-break: break-word;
  border: 1px solid #e1e9f2;
  border-radius: 8px;
  padding: 12px;
}

.hwk-history__error {
  color: #b42318;
}
</style>
