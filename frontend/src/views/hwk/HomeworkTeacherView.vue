<template>
  <main class="hwk-teacher">
    <section class="hwk-teacher__panel" aria-label="作业创建与发布">
      <form class="hwk-teacher__form" @submit.prevent="submit">
        <div class="hwk-teacher__grid">
          <label>
            <span>作业标题</span>
            <input v-model="form.title" name="title" type="text" />
          </label>
          <label>
            <span>作业类型</span>
            <select v-model="form.type" name="type">
              <option value="OBJECTIVE">客观题</option>
              <option value="FILE">文件作业</option>
              <option value="CODE">代码作业</option>
            </select>
          </label>
          <label>
            <span>章节编号</span>
            <input v-model="form.chapterId" name="chapterId" type="number" min="1" />
          </label>
          <label>
            <span>满分</span>
            <input v-model="form.totalScore" name="totalScore" type="number" min="0" step="0.01" />
          </label>
          <label>
            <span>截止时间</span>
            <input v-model="form.deadline" name="deadline" type="datetime-local" />
          </label>
        </div>

        <label>
          <span>作业说明</span>
          <textarea v-model="form.description" name="description" rows="4" />
        </label>

        <div class="hwk-teacher__toggles" aria-label="提交与展示规则">
          <label>
            <input v-model="form.allowResubmit" name="allowResubmit" type="checkbox" />
            <span>允许重复提交</span>
          </label>
          <label>
            <input v-model="form.allowLateSubmit" name="allowLateSubmit" type="checkbox" />
            <span>允许逾期提交</span>
          </label>
          <label>
            <input
              v-model="form.showEvaluationBeforePublish"
              name="showEvaluationBeforePublish"
              type="checkbox"
            />
            <span>成绩发布前展示评测结果</span>
          </label>
        </div>

        <section v-if="form.type === 'OBJECTIVE'" class="hwk-teacher__subform" aria-label="客观题配置">
          <div class="hwk-teacher__subhead">
            <h2>客观题</h2>
            <button type="button" @click="addQuestion">新增题目</button>
          </div>
          <article v-for="(question, index) in questions" :key="index" class="hwk-teacher__item">
            <label>
              <span>题型</span>
              <select v-model="question.questionType" :name="`questionType-${index}`">
                <option value="SINGLE_CHOICE">单选</option>
                <option value="MULTIPLE_CHOICE">多选</option>
                <option value="TRUE_FALSE">判断</option>
              </select>
            </label>
            <label>
              <span>题干</span>
              <textarea v-model="question.stem" :name="`questionStem-${index}`" rows="2" />
            </label>
            <label>
              <span>选项 JSON</span>
              <textarea v-model="question.optionsJson" :name="`questionOptions-${index}`" rows="2" />
            </label>
            <label>
              <span>答案 JSON</span>
              <textarea v-model="question.answerJson" :name="`questionAnswer-${index}`" rows="2" />
            </label>
            <label>
              <span>分值</span>
              <input v-model="question.score" :name="`questionScore-${index}`" type="number" min="0" step="0.01" />
            </label>
            <div class="hwk-teacher__row-actions">
              <button type="button" :disabled="questions.length === 1" @click="removeQuestion(index)">删除</button>
            </div>
          </article>
        </section>

        <section v-if="form.type === 'CODE'" class="hwk-teacher__subform" aria-label="代码测试用例配置">
          <div class="hwk-teacher__subhead">
            <h2>测试用例</h2>
            <button type="button" @click="addTestCase">新增用例</button>
          </div>
          <article v-for="(testCase, index) in testCases" :key="index" class="hwk-teacher__item">
            <label>
              <span>输入</span>
              <textarea v-model="testCase.inputData" :name="`testInput-${index}`" rows="2" />
            </label>
            <label>
              <span>期望输出</span>
              <textarea v-model="testCase.expectedOutput" :name="`testOutput-${index}`" rows="2" />
            </label>
            <label>
              <span>权重</span>
              <input v-model="testCase.scoreWeight" :name="`testWeight-${index}`" type="number" min="0" step="0.01" />
            </label>
            <label>
              <span>时间限制 ms</span>
              <input v-model="testCase.timeLimitMs" :name="`testTime-${index}`" type="number" min="1" />
            </label>
            <label>
              <span>内存限制 KB</span>
              <input v-model="testCase.memoryLimitKb" :name="`testMemory-${index}`" type="number" min="1" />
            </label>
            <label class="hwk-teacher__inline">
              <input v-model="testCase.hidden" :name="`testHidden-${index}`" type="checkbox" />
              <span>隐藏用例</span>
            </label>
            <div class="hwk-teacher__row-actions">
              <button type="button" :disabled="testCases.length === 1" @click="removeTestCase(index)">删除</button>
            </div>
          </article>
        </section>

        <div class="hwk-teacher__form-actions">
          <button type="submit" :disabled="saving">{{ saving ? '保存中' : submitText }}</button>
          <button type="button" :disabled="saving" @click="resetForm">清空</button>
        </div>
      </form>

      <p v-if="feedback" class="hwk-teacher__feedback">{{ feedback }}</p>
      <p v-if="errorMessage" class="hwk-teacher__error">{{ errorMessage }}</p>
    </section>

    <section class="hwk-teacher__list" aria-label="作业列表">
      <p v-if="loading">加载中</p>
      <p v-else-if="homeworks.length === 0">暂无作业</p>
      <table v-else>
        <thead>
          <tr>
            <th>标题</th>
            <th>类型</th>
            <th>状态</th>
            <th>截止时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="homework in homeworks" :key="homework.id">
            <td>{{ homework.title }}</td>
            <td>{{ typeText(homework.type) }}</td>
            <td>{{ statusText(homework.status) }}</td>
            <td>{{ homework.deadline }}</td>
            <td class="hwk-teacher__row-actions">
              <button type="button" @click="editHomework(homework.id)">编辑</button>
              <button
                type="button"
                :disabled="homework.status !== 'DRAFT' && homework.status !== 'NOT_OPEN'"
                @click="publish(homework.id)"
              >
                发布
              </button>
              <button type="button" :disabled="homework.status !== 'PUBLISHED'" @click="close(homework.id)">
                关闭
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import {
  closeHomework,
  createHomework,
  getHomework,
  listHomeworks,
  publishHomework,
  updateHomework
} from '../../api/hwk/homeworks';
import type {
  HomeworkDetail,
  HomeworkPayload,
  HomeworkQuestionPayload,
  HomeworkSummary,
  HomeworkTestCasePayload,
  HomeworkType
} from '../../types/hwk';

const props = defineProps<{
  courseId: number;
}>();

const homeworks = ref<HomeworkSummary[]>([]);
const loading = ref(false);
const saving = ref(false);
const feedback = ref('');
const errorMessage = ref('');
const editingId = ref<number | null>(null);

const form = reactive({
  title: '',
  description: '',
  type: 'OBJECTIVE' as HomeworkType,
  chapterId: '',
  totalScore: '',
  deadline: '',
  allowResubmit: true,
  allowLateSubmit: false,
  showEvaluationBeforePublish: false
});

const questions = ref<HomeworkQuestionPayload[]>([defaultQuestion(1)]);
const testCases = ref<HomeworkTestCasePayload[]>([defaultTestCase(1)]);

const submitText = computed(() => (editingId.value === null ? '保存草稿' : '更新作业'));

watch(() => props.courseId, loadHomeworks);

onMounted(loadHomeworks);

async function loadHomeworks() {
  loading.value = true;
  errorMessage.value = '';
  try {
    homeworks.value = await listHomeworks(props.courseId);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '作业加载失败';
  } finally {
    loading.value = false;
  }
}

async function submit() {
  feedback.value = '';
  errorMessage.value = validateForm();
  if (errorMessage.value) {
    return;
  }

  saving.value = true;
  try {
    const payload = buildPayload();
    if (editingId.value === null) {
      await createHomework(payload);
      feedback.value = '作业草稿已保存';
    } else {
      await updateHomework(editingId.value, payload);
      feedback.value = '作业已更新';
    }
    resetForm();
    await loadHomeworks();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '作业保存失败';
  } finally {
    saving.value = false;
  }
}

async function editHomework(homeworkId: number) {
  feedback.value = '';
  errorMessage.value = '';
  try {
    const homework = await getHomework(homeworkId);
    fillForm(homework);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '作业详情加载失败';
  }
}

async function publish(homeworkId: number) {
  feedback.value = '';
  errorMessage.value = '';
  try {
    await publishHomework(homeworkId);
    feedback.value = '作业已发布';
    await loadHomeworks();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '作业发布失败';
  }
}

async function close(homeworkId: number) {
  feedback.value = '';
  errorMessage.value = '';
  try {
    await closeHomework(homeworkId);
    feedback.value = '作业已关闭';
    await loadHomeworks();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '作业关闭失败';
  }
}

function buildPayload(): HomeworkPayload {
  return {
    courseId: props.courseId,
    chapterId: form.chapterId ? Number(form.chapterId) : null,
    title: form.title.trim(),
    description: form.description.trim(),
    type: form.type,
    totalScore: Number(form.totalScore).toFixed(2),
    deadline: normalizeDeadline(form.deadline),
    allowResubmit: form.allowResubmit,
    allowLateSubmit: form.allowLateSubmit,
    showEvaluationBeforePublish: form.showEvaluationBeforePublish,
    questions: form.type === 'OBJECTIVE' ? normalizeQuestions() : [],
    testCases: form.type === 'CODE' ? normalizeTestCases() : []
  };
}

function validateForm() {
  const errors: string[] = [];
  const totalScore = Number(form.totalScore);
  const deadlineTime = form.deadline ? new Date(form.deadline).getTime() : NaN;

  if (!form.title.trim()) {
    errors.push('作业标题不能为空');
  }
  if (!form.description.trim()) {
    errors.push('作业说明不能为空');
  }
  if (!Number.isFinite(totalScore) || totalScore <= 0) {
    errors.push('满分必须大于 0');
  }
  if (!Number.isFinite(deadlineTime) || deadlineTime <= Date.now()) {
    errors.push('截止时间必须晚于当前时间');
  }
  if (form.type === 'OBJECTIVE') {
    const invalidQuestion = questions.value.some(
      (question) => !question.stem.trim() || !question.optionsJson.trim() || !question.answerJson.trim() || Number(question.score) <= 0
    );
    if (invalidQuestion) {
      errors.push('客观题必须填写题干、选项、答案和有效分值');
    }
  }
  if (form.type === 'CODE') {
    const invalidTestCase = testCases.value.some(
      (testCase) =>
        !testCase.expectedOutput.trim() ||
        Number(testCase.scoreWeight) <= 0 ||
        Number(testCase.timeLimitMs) <= 0 ||
        Number(testCase.memoryLimitKb) <= 0
    );
    if (invalidTestCase) {
      errors.push('代码作业必须配置有效测试用例');
    }
  }

  return errors.join('；');
}

function fillForm(homework: HomeworkDetail) {
  editingId.value = homework.id;
  form.title = homework.title;
  form.description = homework.description;
  form.type = homework.type;
  form.chapterId = homework.chapterId === null ? '' : String(homework.chapterId);
  form.totalScore = String(homework.totalScore);
  form.deadline = homework.deadline.slice(0, 16);
  form.allowResubmit = homework.allowResubmit;
  form.allowLateSubmit = homework.allowLateSubmit;
  form.showEvaluationBeforePublish = homework.showEvaluationBeforePublish;
  questions.value = homework.questions.length > 0 ? homework.questions.map(toQuestionPayload) : [defaultQuestion(1)];
  testCases.value = homework.testCases.length > 0 ? homework.testCases.map(toTestCasePayload) : [defaultTestCase(1)];
}

function resetForm() {
  editingId.value = null;
  form.title = '';
  form.description = '';
  form.type = 'OBJECTIVE';
  form.chapterId = '';
  form.totalScore = '';
  form.deadline = '';
  form.allowResubmit = true;
  form.allowLateSubmit = false;
  form.showEvaluationBeforePublish = false;
  questions.value = [defaultQuestion(1)];
  testCases.value = [defaultTestCase(1)];
}

function addQuestion() {
  questions.value.push(defaultQuestion(questions.value.length + 1));
}

function removeQuestion(index: number) {
  questions.value.splice(index, 1);
  questions.value = questions.value.map((question, currentIndex) => ({
    ...question,
    sortOrder: currentIndex + 1
  }));
}

function addTestCase() {
  testCases.value.push(defaultTestCase(testCases.value.length + 1));
}

function removeTestCase(index: number) {
  testCases.value.splice(index, 1);
  testCases.value = testCases.value.map((testCase, currentIndex) => ({
    ...testCase,
    sortOrder: currentIndex + 1
  }));
}

function normalizeQuestions() {
  return questions.value.map((question, index) => ({
    ...question,
    stem: question.stem.trim(),
    optionsJson: question.optionsJson.trim(),
    answerJson: question.answerJson.trim(),
    score: Number(question.score).toFixed(2),
    sortOrder: index + 1
  }));
}

function normalizeTestCases() {
  return testCases.value.map((testCase, index) => ({
    ...testCase,
    inputData: testCase.inputData,
    expectedOutput: testCase.expectedOutput,
    scoreWeight: Number(testCase.scoreWeight).toFixed(2),
    timeLimitMs: Number(testCase.timeLimitMs),
    memoryLimitKb: Number(testCase.memoryLimitKb),
    sortOrder: index + 1
  }));
}

function normalizeDeadline(value: string) {
  return value.length === 16 ? `${value}:00` : value;
}

function defaultQuestion(sortOrder: number): HomeworkQuestionPayload {
  return {
    questionType: 'SINGLE_CHOICE',
    stem: '',
    optionsJson: '["A","B","C","D"]',
    answerJson: '["A"]',
    score: '',
    sortOrder
  };
}

function defaultTestCase(sortOrder: number): HomeworkTestCasePayload {
  return {
    inputData: '',
    expectedOutput: '',
    scoreWeight: '1.00',
    hidden: true,
    timeLimitMs: 1000,
    memoryLimitKb: 262144,
    sortOrder
  };
}

function toQuestionPayload(question: HomeworkDetail['questions'][number]): HomeworkQuestionPayload {
  return {
    questionType: question.questionType,
    stem: question.stem,
    optionsJson: question.optionsJson,
    answerJson: question.answerJson ?? '',
    score: question.score,
    sortOrder: question.sortOrder
  };
}

function toTestCasePayload(testCase: HomeworkDetail['testCases'][number]): HomeworkTestCasePayload {
  return {
    inputData: testCase.inputData,
    expectedOutput: testCase.expectedOutput,
    scoreWeight: testCase.scoreWeight,
    hidden: testCase.hidden,
    timeLimitMs: testCase.timeLimitMs,
    memoryLimitKb: testCase.memoryLimitKb,
    sortOrder: testCase.sortOrder
  };
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
</script>

<style scoped>
.hwk-teacher {
  display: grid;
  gap: 20px;
  padding: 24px;
  color: #1f2a37;
}

.hwk-teacher__panel,
.hwk-teacher__list {
  border: 1px solid #d9e2ec;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  padding: 20px;
}

.hwk-teacher__form {
  display: grid;
  gap: 16px;
}

.hwk-teacher__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 14px;
}

label,
.hwk-teacher__item {
  display: grid;
  gap: 6px;
}

input,
select,
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

.hwk-teacher__toggles,
.hwk-teacher__form-actions,
.hwk-teacher__row-actions,
.hwk-teacher__subhead {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.hwk-teacher__toggles label,
.hwk-teacher__inline {
  display: inline-flex;
  align-items: center;
  width: auto;
}

.hwk-teacher__toggles input,
.hwk-teacher__inline input {
  width: auto;
}

.hwk-teacher__subform {
  display: grid;
  gap: 12px;
  border-top: 1px solid #e5edf5;
  padding-top: 14px;
}

.hwk-teacher__subhead {
  justify-content: space-between;
}

.hwk-teacher__subhead h2 {
  margin: 0;
  font-size: 18px;
}

.hwk-teacher__item {
  border: 1px solid #e1e9f2;
  border-radius: 8px;
  padding: 14px;
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
  border-color: #b8c4d0;
  background: #d7dee6;
  cursor: not-allowed;
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

.hwk-teacher__feedback {
  color: #1d7a45;
}

.hwk-teacher__error {
  color: #b42318;
}
</style>
