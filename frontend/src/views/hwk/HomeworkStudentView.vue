<template>
  <main class="hwk-student">
    <section class="hwk-student__list" aria-label="学生作业列表">
      <p v-if="loading">加载中</p>
      <p v-else-if="homeworks.length === 0">暂无可提交作业</p>
      <template v-else>
        <button
          v-for="homework in homeworks"
          :key="homework.id"
          class="hwk-student__item"
          type="button"
          @click="selectHomework(homework.id)"
        >
          <strong>{{ homework.title }}</strong>
          <span>{{ typeText(homework.type) }} · {{ statusText(homework.status) }}</span>
          <span>{{ homework.deadline }}</span>
        </button>
      </template>
    </section>

    <section class="hwk-student__detail" aria-label="学生作业详情">
      <p v-if="detailLoading">详情加载中</p>
      <p v-else-if="!selectedHomework">请选择作业</p>
      <template v-else>
        <header class="hwk-student__header">
          <div>
            <h1>{{ selectedHomework.title }}</h1>
            <p>{{ selectedHomework.description }}</p>
          </div>
          <span :class="['hwk-student__status', deadlineExpired ? 'hwk-student__status--late' : '']">
            {{ deadlineExpired ? '已过截止时间' : '可提交' }}
          </span>
        </header>

        <dl class="hwk-student__meta">
          <div>
            <dt>类型</dt>
            <dd>{{ typeText(selectedHomework.type) }}</dd>
          </div>
          <div>
            <dt>满分</dt>
            <dd>{{ selectedHomework.totalScore }}</dd>
          </div>
          <div>
            <dt>截止时间</dt>
            <dd>{{ selectedHomework.deadline }}</dd>
          </div>
          <div>
            <dt>重复提交</dt>
            <dd>{{ selectedHomework.allowResubmit ? '允许' : '不允许' }}</dd>
          </div>
        </dl>

        <section v-if="selectedHomework.type === 'OBJECTIVE'" class="hwk-student__questions">
          <article v-for="question in selectedHomework.questions" :key="question.id">
            <h2>{{ question.stem }}</h2>
            <p>{{ question.optionsJson }}</p>
          </article>
        </section>

        <form class="hwk-student__form" @submit.prevent="submit">
          <label v-if="selectedHomework.type === 'OBJECTIVE'">
            <span>答案 JSON</span>
            <textarea v-model="form.answerJson" name="answerJson" rows="5" />
          </label>
          <template v-else-if="selectedHomework.type === 'CODE'">
            <label>
              <span>语言</span>
              <input v-model="form.language" name="language" type="text" />
            </label>
            <label>
              <span>代码</span>
              <textarea v-model="form.codeText" name="codeText" rows="9" />
            </label>
          </template>
          <template v-else>
            <label>
              <span>文本说明</span>
              <textarea v-model="form.answerText" name="answerText" rows="5" />
            </label>
            <label>
              <span>附件地址</span>
              <input v-model="form.fileUrl" name="fileUrl" type="text" />
            </label>
          </template>

          <div class="hwk-student__actions">
            <button type="submit" :disabled="submitting">{{ submitting ? '提交中' : '提交作业' }}</button>
          </div>
        </form>

        <p v-if="feedback" class="hwk-student__feedback">{{ feedback }}</p>
        <p v-if="errorMessage" class="hwk-student__error">{{ errorMessage }}</p>

        <section class="hwk-student__history" aria-label="我的提交记录">
          <h2>提交记录</h2>
          <p v-if="submissions.length === 0">暂无提交记录</p>
          <table v-else>
            <thead>
              <tr>
                <th>时间</th>
                <th>状态</th>
                <th>类型</th>
                <th>最新</th>
                <th>有效</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="submission in submissions" :key="submission.id">
                <td>{{ submission.submittedAt }}</td>
                <td>{{ submitStatusText(submission.submitStatus) }}</td>
                <td>{{ submission.submitType }}</td>
                <td>{{ submission.isLatest ? '是' : '否' }}</td>
                <td>{{ submission.isFinal ? '是' : '否' }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import {
  getHomework,
  listHomeworks,
  listMyHomeworkSubmissions,
  submitHomework
} from '../../api/hwk/homeworks';
import type {
  HomeworkDetail,
  HomeworkSubmission,
  HomeworkSubmissionPayload,
  HomeworkSummary,
  HomeworkType
} from '../../types/hwk';

const props = defineProps<{
  courseId: number;
}>();

const homeworks = ref<HomeworkSummary[]>([]);
const selectedHomework = ref<HomeworkDetail | null>(null);
const submissions = ref<HomeworkSubmission[]>([]);
const loading = ref(false);
const detailLoading = ref(false);
const submitting = ref(false);
const feedback = ref('');
const errorMessage = ref('');

const form = reactive({
  answerText: '',
  answerJson: '',
  fileUrl: '',
  codeText: '',
  language: ''
});

const deadlineExpired = computed(() => {
  if (!selectedHomework.value) {
    return false;
  }
  return new Date(selectedHomework.value.deadline).getTime() <= Date.now();
});

watch(() => props.courseId, loadHomeworks);

onMounted(loadHomeworks);

async function loadHomeworks() {
  loading.value = true;
  errorMessage.value = '';
  try {
    homeworks.value = await listHomeworks(props.courseId);
    if (homeworks.value.length > 0) {
      await selectHomework(homeworks.value[0].id);
    } else {
      selectedHomework.value = null;
      submissions.value = [];
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '作业加载失败';
  } finally {
    loading.value = false;
  }
}

async function selectHomework(homeworkId: number) {
  detailLoading.value = true;
  feedback.value = '';
  errorMessage.value = '';
  try {
    selectedHomework.value = await getHomework(homeworkId);
    resetForm();
    await loadSubmissions(homeworkId);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '作业详情加载失败';
  } finally {
    detailLoading.value = false;
  }
}

async function loadSubmissions(homeworkId: number) {
  submissions.value = await listMyHomeworkSubmissions(homeworkId);
}

async function submit() {
  feedback.value = '';
  errorMessage.value = validateForm();
  if (errorMessage.value || !selectedHomework.value) {
    return;
  }

  submitting.value = true;
  try {
    await submitHomework(selectedHomework.value.id, buildPayload(selectedHomework.value));
    feedback.value = '作业提交成功';
    resetForm();
    await loadSubmissions(selectedHomework.value.id);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '作业提交失败';
  } finally {
    submitting.value = false;
  }
}

function validateForm() {
  if (!selectedHomework.value) {
    return '请选择作业';
  }
  if (selectedHomework.value.status !== 'PUBLISHED') {
    return '当前作业不可提交';
  }
  if (selectedHomework.value.type === 'OBJECTIVE' && !form.answerJson.trim()) {
    return '客观题答案不能为空';
  }
  if (selectedHomework.value.type === 'CODE') {
    if (!form.language.trim()) {
      return '代码语言不能为空';
    }
    if (!form.codeText.trim()) {
      return '代码内容不能为空';
    }
  }
  if (selectedHomework.value.type === 'FILE' && !form.answerText.trim() && !form.fileUrl.trim()) {
    return '文件作业需提交文本或附件';
  }
  return '';
}

function buildPayload(homework: HomeworkDetail): HomeworkSubmissionPayload {
  if (homework.type === 'OBJECTIVE') {
    return { answerJson: form.answerJson.trim() };
  }
  if (homework.type === 'CODE') {
    return {
      codeText: form.codeText,
      language: form.language.trim()
    };
  }
  return {
    answerText: form.answerText.trim() || undefined,
    fileUrl: form.fileUrl.trim() || undefined
  };
}

function resetForm() {
  form.answerText = '';
  form.answerJson = '';
  form.fileUrl = '';
  form.codeText = '';
  form.language = '';
}

function typeText(type: HomeworkType) {
  const labels: Record<HomeworkType, string> = {
    OBJECTIVE: '客观题',
    FILE: '文件',
    CODE: '代码'
  };
  return labels[type];
}

function statusText(status: HomeworkSummary['status']) {
  const labels: Record<HomeworkSummary['status'], string> = {
    DRAFT: '草稿',
    NOT_OPEN: '未开放',
    PUBLISHED: '已发布',
    CLOSED: '已关闭',
    SCORE_PUBLISHED: '成绩已发布',
    ARCHIVED: '已归档'
  };
  return labels[status];
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
.hwk-student {
  display: grid;
  grid-template-columns: minmax(220px, 300px) 1fr;
  gap: 20px;
  padding: 24px;
  color: #1f2a37;
}

.hwk-student__list,
.hwk-student__detail {
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  padding: 18px;
}

.hwk-student__list {
  align-content: start;
  display: grid;
  gap: 10px;
}

.hwk-student__item {
  display: grid;
  gap: 6px;
  border: 1px solid #d3dde8;
  border-radius: 8px;
  background: #fff;
  color: inherit;
  cursor: pointer;
  padding: 12px;
  text-align: left;
}

.hwk-student__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.hwk-student__header h1 {
  margin: 0 0 8px;
  font-size: 24px;
}

.hwk-student__status {
  align-self: start;
  border-radius: 6px;
  background: #e8f5ee;
  color: #1d7a45;
  padding: 6px 10px;
}

.hwk-student__status--late {
  background: #fff1f0;
  color: #b42318;
}

.hwk-student__meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
  margin: 18px 0;
}

.hwk-student__meta div,
.hwk-student__questions article {
  border: 1px solid #e1e9f2;
  border-radius: 8px;
  padding: 12px;
}

.hwk-student__meta dt {
  color: #667085;
  font-size: 13px;
}

.hwk-student__meta dd {
  margin: 4px 0 0;
}

.hwk-student__form {
  display: grid;
  gap: 14px;
  margin-top: 16px;
}

label {
  display: grid;
  gap: 6px;
}

input,
textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #c8d3df;
  border-radius: 6px;
  padding: 9px 10px;
  font: inherit;
}

textarea {
  resize: vertical;
}

.hwk-student__actions {
  display: flex;
  gap: 10px;
}

button {
  font: inherit;
}

.hwk-student__actions button {
  border: 1px solid #2f6f9f;
  border-radius: 6px;
  background: #2f6f9f;
  color: #fff;
  cursor: pointer;
  padding: 8px 12px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
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

.hwk-student__feedback {
  color: #1d7a45;
}

.hwk-student__error {
  color: #b42318;
}

@media (max-width: 780px) {
  .hwk-student {
    grid-template-columns: 1fr;
  }
}
</style>
