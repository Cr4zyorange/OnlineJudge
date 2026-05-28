<template>
  <section class="hwk-evaluation" aria-label="作业评测结果">
    <header class="hwk-evaluation__header">
      <h1>评测结果</h1>
      <div class="hwk-evaluation__actions">
        <button type="button" :disabled="loading" @click="loadEvaluation">
          {{ loading ? '刷新中' : '刷新' }}
        </button>
        <button v-if="manageable" type="button" :disabled="runningReevaluation" @click="runReevaluation">
          {{ runningReevaluation ? '重评中' : '重评' }}
        </button>
      </div>
    </header>

    <p v-if="loading">加载中</p>
    <p v-else-if="!evaluation">暂无评测结果</p>
    <template v-else>
      <dl class="hwk-evaluation__summary">
        <div>
          <dt>状态</dt>
          <dd>{{ statusText(evaluation.status) }}</dd>
        </div>
        <div>
          <dt>得分</dt>
          <dd>{{ evaluation.score ?? '-' }} / {{ evaluation.totalScore }}</dd>
        </div>
        <div>
          <dt>通过</dt>
          <dd>{{ evaluation.passedCount }} / {{ evaluation.totalCount }}</dd>
        </div>
        <div>
          <dt>类型</dt>
          <dd>{{ evaluation.evaluatorType }}</dd>
        </div>
      </dl>

      <p v-if="evaluation.message" class="hwk-evaluation__message">{{ evaluation.message }}</p>
      <pre v-if="evaluation.caseResultsJson">{{ evaluation.caseResultsJson }}</pre>
    </template>

    <p v-if="feedback" class="hwk-evaluation__feedback">{{ feedback }}</p>
    <p v-if="errorMessage" class="hwk-evaluation__error">{{ errorMessage }}</p>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { getSubmissionEvaluation, reevaluateSubmission } from '../../api/hwk/homeworks';
import type { HomeworkEvaluation, HomeworkEvaluationStatus } from '../../types/hwk';

const props = withDefaults(defineProps<{
  submissionId: number;
  manageable?: boolean;
}>(), {
  manageable: false
});

const evaluation = ref<HomeworkEvaluation | null>(null);
const loading = ref(false);
const runningReevaluation = ref(false);
const feedback = ref('');
const errorMessage = ref('');

watch(() => props.submissionId, loadEvaluation);

onMounted(loadEvaluation);

async function loadEvaluation() {
  loading.value = true;
  feedback.value = '';
  errorMessage.value = '';
  try {
    evaluation.value = await getSubmissionEvaluation(props.submissionId);
  } catch (error) {
    evaluation.value = null;
    errorMessage.value = error instanceof Error ? error.message : '评测结果加载失败';
  } finally {
    loading.value = false;
  }
}

async function runReevaluation() {
  runningReevaluation.value = true;
  feedback.value = '';
  errorMessage.value = '';
  try {
    evaluation.value = await reevaluateSubmission(props.submissionId);
    feedback.value = '重评已完成';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '重评失败';
  } finally {
    runningReevaluation.value = false;
  }
}

function statusText(status: HomeworkEvaluationStatus) {
  const labels: Record<HomeworkEvaluationStatus, string> = {
    NONE: '未评测',
    PENDING: '等待中',
    RUNNING: '评测中',
    ACCEPTED: '通过',
    WRONG_ANSWER: '答案错误',
    COMPILE_ERROR: '编译错误',
    RUNTIME_ERROR: '运行错误',
    TIME_LIMIT_EXCEEDED: '超时',
    SYSTEM_ERROR: '系统错误'
  };
  return labels[status];
}
</script>

<style scoped>
.hwk-evaluation {
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  color: #1f2a37;
  padding: 18px;
}

.hwk-evaluation__header,
.hwk-evaluation__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.hwk-evaluation__header h1 {
  margin: 0;
  font-size: 22px;
}

.hwk-evaluation__summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 12px;
}

.hwk-evaluation__summary div {
  border: 1px solid #e1e9f2;
  border-radius: 8px;
  padding: 12px;
}

.hwk-evaluation__summary dt {
  color: #667085;
  font-size: 13px;
}

.hwk-evaluation__summary dd {
  margin: 4px 0 0;
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

pre {
  white-space: pre-wrap;
  word-break: break-word;
  border: 1px solid #e1e9f2;
  border-radius: 8px;
  padding: 12px;
}

.hwk-evaluation__feedback {
  color: #1d7a45;
}

.hwk-evaluation__error {
  color: #b42318;
}
</style>
