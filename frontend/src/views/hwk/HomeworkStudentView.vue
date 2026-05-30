<template>
  <main class="student-homework">
    <section class="student-homework__panel" aria-label="学生作业列表">
      <div class="student-homework__heading">
        <h1>我的作业</h1>
        <button type="button" :disabled="loading" @click="loadHomeworks">刷新</button>
      </div>
      <p v-if="loading" class="student-homework__state">加载中</p>
      <p v-else-if="errorMessage" class="student-homework__state student-homework__state--error">{{ errorMessage }}</p>
      <p v-else-if="homeworks.length === 0" class="student-homework__state">暂无已发布作业</p>
      <table v-else>
        <thead>
          <tr>
            <th>标题</th>
            <th>类型</th>
            <th>截止时间</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="homework in homeworks" :key="homework.id">
            <td>{{ homework.title }}</td>
            <td>{{ homework.type }}</td>
            <td>{{ formatDate(homework.deadline) }}</td>
            <td>{{ homework.status }}</td>
            <td>
              <button :data-testid="`open-homework-${homework.id}`" type="button" @click="openHomework(homework.id)">
                查看
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="detail" class="student-homework__panel" aria-label="作业详情与提交">
      <div class="student-homework__heading">
        <div>
          <h2>{{ detail.title }}</h2>
          <p>{{ detail.description }}</p>
        </div>
        <span>{{ formatDate(detail.deadline) }}</span>
      </div>

      <section v-if="detail.questions.length > 0" class="student-homework__questions" aria-label="题目">
        <article v-for="question in detail.questions" :key="question.id">
          <h3>{{ question.stem }}</h3>
          <p>{{ question.optionsJson ?? '无选项' }}</p>
          <small>{{ question.score }} 分</small>
        </article>
      </section>

      <section v-if="detail.testCases.length > 0" class="student-homework__questions" aria-label="公开测试用例">
        <article v-for="testCase in detail.testCases" :key="testCase.id">
          <h3>公开用例 {{ testCase.sortOrder }}</h3>
          <p>输入：{{ testCase.inputData }}</p>
          <p v-if="testCase.expectedOutput">输出：{{ testCase.expectedOutput }}</p>
        </article>
      </section>

      <form class="student-homework__form" @submit.prevent="submit">
        <label v-if="detail.type === 'TEXT'">
          <span>文本答案</span>
          <textarea v-model="form.answerText" name="answerText" rows="5" />
        </label>
        <label v-else-if="detail.type === 'OBJECTIVE'">
          <span>答案 JSON</span>
          <textarea v-model="form.answerJson" name="answerJson" rows="5" />
        </label>
        <label v-else-if="detail.type === 'FILE'">
          <span>附件地址</span>
          <input v-model="form.fileUrl" name="fileUrl" type="text" />
        </label>
        <template v-else>
          <label>
            <span>语言</span>
            <input v-model="form.language" name="language" type="text" />
          </label>
          <label>
            <span>代码</span>
            <textarea v-model="form.codeText" name="codeText" rows="8" />
          </label>
        </template>
        <button type="submit" :disabled="submitting || !canSubmit">提交</button>
      </form>

      <p v-if="feedback" class="student-homework__state student-homework__state--success">{{ feedback }}</p>
      <p v-if="submitError" class="student-homework__state student-homework__state--error">{{ submitError }}</p>

      <section class="student-homework__history" aria-label="我的提交">
        <h3>我的提交</h3>
        <p
          v-if="historyError"
          class="student-homework__state student-homework__state--error"
          data-testid="submission-history-error"
        >
          {{ historyError }}
        </p>
        <p v-else-if="submissions.length === 0">暂无提交</p>
        <ul v-else>
          <li v-for="submission in submissions" :key="submission.id">
            <strong>{{ submission.submitStatus }}</strong>
            <span>{{ formatDate(submission.submittedAt) }}</span>
            <p v-if="submission.answerText">{{ submission.answerText }}</p>
            <p v-else-if="submission.answerJson">{{ submission.answerJson }}</p>
            <p v-else-if="submission.fileUrl">{{ submission.fileUrl }}</p>
          </li>
        </ul>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { getHomeworkDetail, listHomeworks, listMyHomeworkSubmissions, submitHomework } from '../../api/hwk/homeworks';
import type { HomeworkDetail, HomeworkSubmission, HomeworkSummary } from '../../types/hwk';

const props = defineProps<{
  courseId: number;
  initialHomeworkId?: number | null;
}>();

const homeworks = ref<HomeworkSummary[]>([]);
const detail = ref<HomeworkDetail | null>(null);
const submissions = ref<HomeworkSubmission[]>([]);
const loading = ref(false);
const submitting = ref(false);
const errorMessage = ref('');
const submitError = ref('');
const historyError = ref('');
const feedback = ref('');

const form = reactive({
  answerText: '',
  answerJson: '',
  fileUrl: '',
  codeText: '',
  language: 'java'
});

const canSubmit = computed(() => {
  if (!detail.value || detail.value.status !== 'PUBLISHED') {
    return false;
  }
  return true;
});

onMounted(async () => {
  await loadHomeworks();
  if (props.initialHomeworkId) {
    await openHomework(props.initialHomeworkId);
  }
});

async function loadHomeworks() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const page = await listHomeworks({ courseId: props.courseId });
    homeworks.value = page.list;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '作业列表加载失败';
  } finally {
    loading.value = false;
  }
}

async function openHomework(homeworkId: number) {
  submitError.value = '';
  feedback.value = '';
  historyError.value = '';
  errorMessage.value = '';
  try {
    detail.value = await getHomeworkDetail(homeworkId);
    resetForm();
    submissions.value = await loadSubmissions(homeworkId);
  } catch (error) {
    detail.value = null;
    submissions.value = [];
    errorMessage.value = error instanceof Error ? error.message : '作业详情加载失败';
  }
}

async function submit() {
  if (!detail.value) {
    return;
  }
  submitError.value = validateSubmission();
  feedback.value = '';
  if (submitError.value) {
    return;
  }
  submitting.value = true;
  try {
    await submitHomework(detail.value.id, {
      answerText: detail.value.type === 'TEXT' ? form.answerText.trim() : null,
      answerJson: detail.value.type === 'OBJECTIVE' ? form.answerJson.trim() : null,
      fileUrl: detail.value.type === 'FILE' ? form.fileUrl.trim() : null,
      codeText: detail.value.type === 'CODE' ? form.codeText : null,
      language: detail.value.type === 'CODE' ? form.language.trim() : null
    });
    feedback.value = '提交成功';
    submissions.value = await loadSubmissions(detail.value.id);
  } catch (error) {
    submitError.value = error instanceof Error ? error.message : '提交失败';
  } finally {
    submitting.value = false;
  }
}

function validateSubmission() {
  if (!detail.value) {
    return '请先选择作业';
  }
  if (detail.value.status !== 'PUBLISHED') {
    return '作业当前不可提交';
  }
  if (detail.value.type === 'TEXT' && !form.answerText.trim()) {
    return '文本答案不能为空';
  }
  if (detail.value.type === 'OBJECTIVE' && !form.answerJson.trim()) {
    return '答案不能为空';
  }
  if (detail.value.type === 'FILE' && !form.fileUrl.trim()) {
    return '附件地址不能为空';
  }
  if (detail.value.type === 'CODE' && (!form.codeText.trim() || !form.language.trim())) {
    return '代码和语言不能为空';
  }
  return '';
}

function resetForm() {
  form.answerText = '';
  form.answerJson = '';
  form.fileUrl = '';
  form.codeText = '';
  form.language = 'java';
}

async function loadSubmissions(homeworkId: number) {
  historyError.value = '';
  try {
    const records = await listMyHomeworkSubmissions(homeworkId);
    return Array.isArray(records) ? records : [];
  } catch (error) {
    historyError.value = error instanceof Error ? error.message : '提交历史加载失败';
    return [];
  }
}

function formatDate(value: string) {
  return value.replace('T', ' ').slice(0, 16);
}
</script>

<style scoped>
.student-homework {
  background: #f6f8fb;
  color: #1f2937;
  display: grid;
  gap: 18px;
  min-height: 100vh;
  padding: 24px;
}

.student-homework__panel {
  background: #ffffff;
  border: 1px solid #d7dde8;
  border-radius: 8px;
  min-width: 0;
  padding: 18px;
}

.student-homework__heading {
  align-items: center;
  display: flex;
  gap: 16px;
  justify-content: space-between;
}

.student-homework__heading h1,
.student-homework__heading h2 {
  margin: 0;
}

.student-homework__form,
.student-homework__questions {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}

.student-homework__questions article,
.student-homework__history li {
  border: 1px solid #d7dde8;
  border-radius: 8px;
  padding: 12px;
}

label {
  display: grid;
  gap: 6px;
}

input,
textarea {
  border: 1px solid #b8c2d2;
  min-height: 36px;
  padding: 6px 8px;
}

button {
  background: #ffffff;
  border: 1px solid #aeb8c8;
  color: #111827;
  min-height: 36px;
  padding: 6px 12px;
}

button:disabled {
  color: #697386;
}

.student-homework__state {
  color: #475569;
}

.student-homework__state--error {
  color: #b42318;
}

.student-homework__state--success {
  color: #116329;
}

table {
  border-collapse: collapse;
  width: 100%;
}

th,
td {
  border-bottom: 1px solid #d7dde8;
  padding: 10px;
  text-align: left;
}

ul {
  display: grid;
  gap: 10px;
  list-style: none;
  padding: 0;
}
</style>
