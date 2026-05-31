<template>
  <main class="homeworks">
    <section class="homeworks__panel" aria-label="作业创建与编辑">
      <form class="homeworks__form" @submit.prevent="submit">
        <label>
          <span>作业标题</span>
          <input v-model="form.title" name="title" type="text" />
        </label>
        <label>
          <span>作业类型</span>
          <select v-model="form.type" name="type">
            <option value="OBJECTIVE">OBJECTIVE</option>
            <option value="FILE">FILE</option>
            <option value="CODE">CODE</option>
          </select>
        </label>
        <label class="homeworks__wide">
          <span>作业说明</span>
          <textarea v-model="form.description" name="description" rows="4" />
        </label>
        <label>
          <span>截止时间</span>
          <input v-model="form.deadline" name="deadline" type="datetime-local" />
        </label>
        <label>
          <span>满分</span>
          <input v-model="form.totalScore" name="totalScore" type="number" min="1" />
        </label>
        <label class="homeworks__checkbox">
          <input v-model="form.allowResubmit" name="allowResubmit" type="checkbox" />
          <span>允许多次提交</span>
        </label>
        <label class="homeworks__checkbox">
          <input v-model="form.allowLateSubmit" name="allowLateSubmit" type="checkbox" />
          <span>允许逾期提交</span>
        </label>
        <label class="homeworks__checkbox">
          <input v-model="form.showEvaluationBeforePublish" name="showEvaluationBeforePublish" type="checkbox" />
          <span>成绩发布前显示评测</span>
        </label>

        <section v-if="form.type === 'OBJECTIVE'" class="homeworks__wide homeworks__config" aria-label="客观题配置">
          <header class="homeworks__section-header">
            <h2>客观题</h2>
            <button type="button" @click="addQuestion">新增题目</button>
          </header>
          <div v-for="(question, index) in form.questions" :key="`question-${index}`" class="homeworks__config-card">
            <label>
              <span>题型</span>
              <select v-model="question.questionType" :name="`question-type-${index}`">
                <option value="SINGLE_CHOICE">SINGLE_CHOICE</option>
                <option value="MULTIPLE_CHOICE">MULTIPLE_CHOICE</option>
                <option value="JUDGE">JUDGE</option>
              </select>
            </label>
            <label class="homeworks__wide">
              <span>题干</span>
              <textarea v-model="question.stem" :name="`question-stem-${index}`" rows="2" />
            </label>
            <label>
              <span>选项 JSON</span>
              <input v-model="question.optionsJson" :name="`question-options-${index}`" type="text" />
            </label>
            <label>
              <span>答案 JSON</span>
              <input v-model="question.answerJson" :name="`question-answer-${index}`" type="text" />
            </label>
            <label>
              <span>分值</span>
              <input v-model="question.score" :name="`question-score-${index}`" type="number" min="1" />
            </label>
            <button type="button" @click="removeQuestion(index)">删除题目</button>
          </div>
        </section>

        <section v-if="form.type === 'CODE'" class="homeworks__wide homeworks__config" aria-label="代码题配置">
          <header class="homeworks__section-header">
            <h2>测试用例</h2>
            <button type="button" @click="addTestCase">新增用例</button>
          </header>
          <label>
            <span>语言限制 JSON</span>
            <input v-model="form.languageLimitJson" name="languageLimitJson" type="text" />
          </label>
          <label>
            <span>时间限制(ms)</span>
            <input v-model="form.timeLimitMs" name="timeLimitMs" type="number" min="1" />
          </label>
          <label>
            <span>内存限制(KB)</span>
            <input v-model="form.memoryLimitKb" name="memoryLimitKb" type="number" min="1" />
          </label>
          <label>
            <span>输出比较</span>
            <select v-model="form.outputCompareMode" name="outputCompareMode">
              <option value="EXACT">EXACT</option>
              <option value="TRIM">TRIM</option>
            </select>
          </label>
          <div v-for="(testCase, index) in form.testCases" :key="`test-case-${index}`" class="homeworks__config-card">
            <label>
              <span>输入</span>
              <textarea v-model="testCase.inputData" :name="`testcase-input-${index}`" rows="2" />
            </label>
            <label>
              <span>期望输出</span>
              <textarea v-model="testCase.expectedOutput" :name="`testcase-output-${index}`" rows="2" />
            </label>
            <label>
              <span>权重</span>
              <input v-model="testCase.scoreWeight" :name="`testcase-weight-${index}`" type="number" min="0" />
            </label>
            <label>
              <span>时间限制(ms)</span>
              <input v-model="testCase.timeLimitMs" :name="`testcase-time-${index}`" type="number" min="1" />
            </label>
            <label>
              <span>内存限制(KB)</span>
              <input v-model="testCase.memoryLimitKb" :name="`testcase-memory-${index}`" type="number" min="1" />
            </label>
            <label class="homeworks__checkbox">
              <input v-model="testCase.hidden" :name="`testcase-hidden-${index}`" type="checkbox" />
              <span>隐藏用例</span>
            </label>
            <button type="button" @click="removeTestCase(index)">删除用例</button>
          </div>
        </section>

        <div class="homeworks__actions">
          <button type="submit" :disabled="saving">{{ editingId ? '保存修改' : '保存草稿' }}</button>
          <button type="button" :disabled="saving" @click="resetForm">清空</button>
        </div>
      </form>
      <p v-if="feedback" class="homeworks__feedback">{{ feedback }}</p>
      <p v-if="errorMessage" class="homeworks__error">{{ errorMessage }}</p>
    </section>

    <section class="homeworks__list" aria-label="作业发布管理">
      <div class="homeworks__toolbar">
        <label>
          <span>状态筛选</span>
          <select v-model="selectedStatus" @change="loadHomeworks">
            <option value="">全部</option>
            <option value="DRAFT">DRAFT</option>
            <option value="PUBLISHED">PUBLISHED</option>
            <option value="CLOSED">CLOSED</option>
          </select>
        </label>
        <label>
          <span>关键词</span>
          <input v-model="keyword" type="search" @change="loadHomeworks" />
        </label>
      </div>

      <p v-if="loading">加载中</p>
      <p v-else-if="homeworks.length === 0">暂无作业</p>
      <table v-else>
        <thead>
          <tr>
            <th>标题</th>
            <th>类型</th>
            <th>状态</th>
            <th>截止时间</th>
            <th>满分</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="homework in homeworks" :key="homework.id">
            <td>{{ homework.title }}</td>
            <td>{{ homework.type }}</td>
            <td>{{ homework.status }}</td>
            <td>{{ formatDeadline(homework.deadline) }}</td>
            <td>{{ homework.totalScore }}</td>
            <td class="homeworks__row-actions">
              <button
                v-if="homework.status === 'DRAFT'"
                :data-testid="`edit-homework-${homework.id}`"
                type="button"
                @click="edit(homework.id)"
              >编辑</button>
              <button
                :data-testid="`load-submissions-${homework.id}`"
                type="button"
                @click="loadSubmissions(homework.id, 1)"
              >提交</button>
              <button v-if="homework.status === 'DRAFT'" type="button" @click="publish(homework.id)">发布</button>
              <button v-if="homework.status === 'PUBLISHED'" type="button" @click="close(homework.id)">关闭</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="submissionHomeworkId !== null" class="homeworks__list" aria-label="提交历史">
      <div class="homeworks__toolbar">
        <h2>提交历史</h2>
        <label>
          <span>学生</span>
          <input v-model="submissionFilters.studentId" name="submissionStudentId" type="number" min="1" />
        </label>
        <label>
          <span>提交状态</span>
          <select v-model="submissionFilters.submitStatus" name="submissionSubmitStatus">
            <option value="">全部</option>
            <option value="SUBMITTED">SUBMITTED</option>
            <option value="LATE">LATE</option>
            <option value="REJECTED">REJECTED</option>
          </select>
        </label>
        <label>
          <span>评测状态</span>
          <select v-model="submissionFilters.evaluationStatus" name="submissionEvaluationStatus">
            <option value="">全部</option>
            <option value="NOT_REQUIRED">NOT_REQUIRED</option>
            <option value="NOT_EVALUATED">NOT_EVALUATED</option>
            <option value="PENDING">PENDING</option>
            <option value="RUNNING">RUNNING</option>
            <option value="ACCEPTED">ACCEPTED</option>
            <option value="WRONG_ANSWER">WRONG_ANSWER</option>
            <option value="COMPILE_ERROR">COMPILE_ERROR</option>
            <option value="RUNTIME_ERROR">RUNTIME_ERROR</option>
            <option value="TIME_LIMIT_EXCEEDED">TIME_LIMIT_EXCEEDED</option>
            <option value="SYSTEM_ERROR">SYSTEM_ERROR</option>
          </select>
        </label>
        <label>
          <span>批阅状态</span>
          <select v-model="submissionFilters.reviewStatus" name="submissionReviewStatus">
            <option value="">全部</option>
            <option value="UNREVIEWED">UNREVIEWED</option>
            <option value="REVIEWED">REVIEWED</option>
            <option value="NEED_REVIEW">NEED_REVIEW</option>
          </select>
        </label>
        <button
          data-testid="filter-submissions"
          type="button"
          :disabled="submissionsLoading"
          @click="applySubmissionFilters"
        >筛选</button>
        <button type="button" :disabled="submissionsLoading" @click="reloadSubmissions">刷新</button>
      </div>
      <p v-if="submissionsLoading">加载中</p>
      <p v-else-if="submissionError" class="homeworks__error">{{ submissionError }}</p>
      <p v-else-if="submissions.length === 0">暂无提交</p>
      <table v-else>
        <thead>
          <tr>
            <th>学生</th>
            <th>状态</th>
            <th>评测</th>
            <th>批阅</th>
            <th>提交时间</th>
            <th>内容</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="submission in submissions" :key="submission.id">
            <td>{{ submission.studentId }}</td>
            <td>
              {{ submission.submitStatus }}
              <span v-if="submission.latest" class="homeworks__badge">最新提交</span>
              <span v-if="submission.final" class="homeworks__badge">当前有效</span>
            </td>
            <td>{{ submission.evaluationStatus }}</td>
            <td>{{ submission.reviewStatus }}</td>
            <td>{{ formatDeadline(submission.submittedAt) }}</td>
            <td>
              <span v-if="submission.answerText">{{ submission.answerText }}</span>
              <span v-else-if="submission.answerJson">{{ submission.answerJson }}</span>
              <span v-else-if="submission.fileUrl">{{ submission.fileUrl }}</span>
              <span v-else-if="submission.language">{{ submission.language }}</span>
            </td>
            <td>
              <button
                :data-testid="`open-submission-${submission.id}`"
                type="button"
                @click="openSubmission(submission.id)"
              >详情</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="submissionTotal > 0" class="homeworks__pagination" aria-label="提交历史分页">
        <span data-testid="submission-page-summary">
          第 {{ submissionPage }} / {{ submissionTotalPages }} 页，共 {{ submissionTotal }} 条
        </span>
        <button
          data-testid="submission-prev-page"
          type="button"
          :disabled="submissionsLoading || submissionPage <= 1"
          @click="changeSubmissionPage(submissionPage - 1)"
        >上一页</button>
        <button
          data-testid="submission-next-page"
          type="button"
          :disabled="submissionsLoading || submissionPage >= submissionTotalPages"
          @click="changeSubmissionPage(submissionPage + 1)"
        >下一页</button>
      </div>
      <section v-if="selectedSubmission" class="homeworks__submission-detail" aria-label="提交详情">
        <h3>提交详情</h3>
        <p>学生：{{ selectedSubmission.studentId }}</p>
        <p>状态：{{ selectedSubmission.submitStatus }}</p>
        <p v-if="selectedSubmission.answerText">{{ selectedSubmission.answerText }}</p>
        <p v-else-if="selectedSubmission.answerJson">{{ selectedSubmission.answerJson }}</p>
        <p v-else-if="selectedSubmission.fileUrl">{{ selectedSubmission.fileUrl }}</p>
        <p v-else-if="selectedSubmission.language">{{ selectedSubmission.language }}</p>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import {
  closeHomework,
  createHomework,
  getHomeworkSubmission,
  getHomeworkDetail,
  listHomeworks,
  listHomeworkSubmissions,
  publishHomework,
  updateHomework
} from '../../api/hwk/homeworks';
import type {
  HomeworkDetail,
  HomeworkPayload,
  HomeworkQuestionPayload,
  HomeworkStatus,
  HomeworkSubmission,
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
const selectedStatus = ref('');
const keyword = ref('');
const editingId = ref<number | null>(null);
const submissionHomeworkId = ref<number | null>(null);
const submissions = ref<HomeworkSubmission[]>([]);
const selectedSubmission = ref<HomeworkSubmission | null>(null);
const submissionsLoading = ref(false);
const submissionError = ref('');
const submissionPage = ref(1);
const submissionSize = 20;
const submissionTotal = ref(0);
const submissionFilters = reactive({
  studentId: '',
  submitStatus: '',
  evaluationStatus: '',
  reviewStatus: ''
});
const submissionTotalPages = computed(() => Math.max(1, Math.ceil(submissionTotal.value / submissionSize)));

const form = reactive({
  title: '',
  description: '',
  type: 'OBJECTIVE' as HomeworkType,
  deadline: '',
  totalScore: '100',
  allowResubmit: true,
  allowLateSubmit: false,
  showEvaluationBeforePublish: true,
  languageLimitJson: '["java"]',
  timeLimitMs: '1000',
  memoryLimitKb: '65536',
  outputCompareMode: 'EXACT',
  questions: [createEmptyQuestion()],
  testCases: [createEmptyTestCase()]
});

onMounted(loadHomeworks);

watch(() => form.type, () => {
  errorMessage.value = '';
});

async function loadHomeworks() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const page = await listHomeworks({
      courseId: props.courseId,
      status: selectedStatus.value ? (selectedStatus.value as HomeworkStatus) : undefined,
      keyword: keyword.value
    });
    homeworks.value = page.list;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '作业列表加载失败';
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
    if (editingId.value) {
      await updateHomework(editingId.value, buildPayload());
    } else {
      await createHomework(buildPayload());
    }
    feedback.value = '保存成功';
    resetForm();
    await loadHomeworks();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '作业保存失败';
  } finally {
    saving.value = false;
  }
}

async function edit(homeworkId: number) {
  feedback.value = '';
  errorMessage.value = '';
  saving.value = true;
  try {
    const detail = await getHomeworkDetail(homeworkId);
    fillForm(detail);
    editingId.value = homeworkId;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '作业详情加载失败';
  } finally {
    saving.value = false;
  }
}

async function publish(homeworkId: number) {
  feedback.value = '';
  errorMessage.value = '';
  try {
    await publishHomework(homeworkId);
    feedback.value = '发布成功';
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
    feedback.value = '关闭成功';
    await loadHomeworks();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '作业关闭失败';
  }
}

async function loadSubmissions(homeworkId: number, pageNumber = submissionPage.value) {
  submissionHomeworkId.value = homeworkId;
  submissionsLoading.value = true;
  submissionError.value = '';
  selectedSubmission.value = null;
  try {
    const studentId = Number(submissionFilters.studentId);
    const query = {
      page: Math.max(1, pageNumber),
      size: submissionSize
    } as {
      studentId?: number;
      submitStatus?: string;
      evaluationStatus?: string;
      reviewStatus?: string;
      page: number;
      size: number;
    };
    if (Number.isInteger(studentId) && studentId > 0) {
      query.studentId = studentId;
    }
    if (submissionFilters.submitStatus) {
      query.submitStatus = submissionFilters.submitStatus;
    }
    if (submissionFilters.evaluationStatus) {
      query.evaluationStatus = submissionFilters.evaluationStatus;
    }
    if (submissionFilters.reviewStatus) {
      query.reviewStatus = submissionFilters.reviewStatus;
    }
    const page = await listHomeworkSubmissions(homeworkId, query);
    submissions.value = page.list;
    submissionPage.value = page.page;
    submissionTotal.value = page.total;
  } catch (error) {
    submissions.value = [];
    submissionTotal.value = 0;
    submissionError.value = error instanceof Error ? error.message : '提交历史加载失败';
  } finally {
    submissionsLoading.value = false;
  }
}

async function applySubmissionFilters() {
  if (submissionHomeworkId.value !== null) {
    await loadSubmissions(submissionHomeworkId.value, 1);
  }
}

async function reloadSubmissions() {
  if (submissionHomeworkId.value !== null) {
    await loadSubmissions(submissionHomeworkId.value, submissionPage.value);
  }
}

async function changeSubmissionPage(pageNumber: number) {
  if (submissionHomeworkId.value === null || pageNumber < 1 || pageNumber > submissionTotalPages.value) {
    return;
  }
  await loadSubmissions(submissionHomeworkId.value, pageNumber);
}

async function openSubmission(submissionId: number) {
  submissionError.value = '';
  try {
    selectedSubmission.value = await getHomeworkSubmission(submissionId);
  } catch (error) {
    selectedSubmission.value = null;
    submissionError.value = error instanceof Error ? error.message : '提交详情加载失败';
  }
}

function validateForm() {
  const errors: string[] = [];
  const deadline = new Date(form.deadline);
  const totalScore = Number(form.totalScore);
  if (!form.title.trim()) {
    errors.push('作业标题不能为空');
  }
  if (!form.description.trim()) {
    errors.push('作业说明不能为空');
  }
  if (!form.deadline || Number.isNaN(deadline.getTime()) || deadline.getTime() <= Date.now()) {
    errors.push('截止时间必须晚于当前时间');
  }
  if (!Number.isFinite(totalScore) || totalScore <= 0) {
    errors.push('满分必须大于 0');
  }
  if (form.type === 'OBJECTIVE' && collectQuestions().length === 0) {
    errors.push('客观题至少配置一个题目');
  }
  if (form.type === 'CODE' && collectTestCases().length === 0) {
    errors.push('代码题至少配置一个测试用例');
  }
  return errors.join('；');
}

function buildPayload(): HomeworkPayload {
  return {
    courseId: props.courseId,
    chapterId: null,
    title: form.title.trim(),
    description: form.description.trim(),
    type: form.type,
    deadline: `${form.deadline}:00`,
    totalScore: Number(form.totalScore),
    allowResubmit: form.allowResubmit,
    allowLateSubmit: form.allowLateSubmit,
    showEvaluationBeforePublish: form.showEvaluationBeforePublish,
    questions: form.type === 'OBJECTIVE' ? collectQuestions() : [],
    testCases: form.type === 'CODE' ? collectTestCases() : [],
    languageLimitJson: form.type === 'CODE' ? form.languageLimitJson.trim() || null : null,
    timeLimitMs: form.type === 'CODE' ? Number(form.timeLimitMs) : 1000,
    memoryLimitKb: form.type === 'CODE' ? Number(form.memoryLimitKb) : 65536,
    outputCompareMode: form.type === 'CODE' ? form.outputCompareMode : 'EXACT'
  };
}

function collectQuestions(): HomeworkQuestionPayload[] {
  return form.questions
    .map((question, index) => ({
      questionType: question.questionType,
      stem: question.stem.trim(),
      optionsJson: question.optionsJson.trim() || null,
      answerJson: question.answerJson.trim(),
      score: Number(question.score),
      sortOrder: index + 1
    }))
    .filter((question) => Boolean(question.stem || question.answerJson || question.optionsJson));
}

function collectTestCases(): HomeworkTestCasePayload[] {
  return form.testCases
    .map((testCase, index) => ({
      inputData: testCase.inputData,
      expectedOutput: testCase.expectedOutput,
      scoreWeight: Number(testCase.scoreWeight),
      hidden: testCase.hidden,
      timeLimitMs: Number(testCase.timeLimitMs),
      memoryLimitKb: Number(testCase.memoryLimitKb),
      sortOrder: index + 1
    }))
    .filter((testCase) => Boolean(testCase.inputData || testCase.expectedOutput));
}

function addQuestion() {
  form.questions.push(createEmptyQuestion());
}

function removeQuestion(index: number) {
  if (form.questions.length === 1) {
    form.questions[0] = createEmptyQuestion();
    return;
  }
  form.questions.splice(index, 1);
}

function addTestCase() {
  form.testCases.push(createEmptyTestCase());
}

function removeTestCase(index: number) {
  if (form.testCases.length === 1) {
    form.testCases[0] = createEmptyTestCase();
    return;
  }
  form.testCases.splice(index, 1);
}

function resetForm() {
  editingId.value = null;
  form.title = '';
  form.description = '';
  form.type = 'OBJECTIVE';
  form.deadline = '';
  form.totalScore = '100';
  form.allowResubmit = true;
  form.allowLateSubmit = false;
  form.showEvaluationBeforePublish = true;
  form.languageLimitJson = '["java"]';
  form.timeLimitMs = '1000';
  form.memoryLimitKb = '65536';
  form.outputCompareMode = 'EXACT';
  form.questions = [createEmptyQuestion()];
  form.testCases = [createEmptyTestCase()];
}

function fillForm(homework: HomeworkDetail) {
  form.title = homework.title;
  form.description = homework.description;
  form.type = homework.type;
  form.deadline = homework.deadline.slice(0, 16);
  form.totalScore = String(homework.totalScore);
  form.allowResubmit = homework.allowResubmit;
  form.allowLateSubmit = homework.allowLateSubmit;
  form.showEvaluationBeforePublish = homework.showEvaluationBeforePublish;
  form.languageLimitJson = homework.languageLimitJson ?? '["java"]';
  form.timeLimitMs = String(homework.timeLimitMs ?? 1000);
  form.memoryLimitKb = String(homework.memoryLimitKb ?? 65536);
  form.outputCompareMode = homework.outputCompareMode ?? 'EXACT';
  form.questions = homework.questions.length > 0
    ? homework.questions.map((question) => ({
      questionType: question.questionType,
      stem: question.stem,
      optionsJson: question.optionsJson ?? '',
      answerJson: question.answerJson ?? '',
      score: String(question.score)
    }))
    : [createEmptyQuestion()];
  form.testCases = homework.testCases.length > 0
    ? homework.testCases.map((testCase) => ({
      inputData: testCase.inputData,
      expectedOutput: testCase.expectedOutput ?? '',
      scoreWeight: String(testCase.scoreWeight),
      hidden: testCase.hidden,
      timeLimitMs: String(testCase.timeLimitMs),
      memoryLimitKb: String(testCase.memoryLimitKb)
    }))
    : [createEmptyTestCase()];
}

function createEmptyQuestion() {
  return {
    questionType: 'SINGLE_CHOICE',
    stem: '',
    optionsJson: '',
    answerJson: '',
    score: '100'
  };
}

function createEmptyTestCase() {
  return {
    inputData: '',
    expectedOutput: '',
    scoreWeight: '100',
    hidden: false,
    timeLimitMs: '1000',
    memoryLimitKb: '65536'
  };
}

function formatDeadline(value: string) {
  return value.replace('T', ' ').slice(0, 16);
}
</script>

<style scoped>
.homeworks {
  background: #f6f8fb;
  color: #1f2937;
  display: grid;
  gap: 20px;
  min-height: 100vh;
  padding: 24px;
}

.homeworks__panel,
.homeworks__list {
  background: #ffffff;
  border: 1px solid #d7dde8;
  border-radius: 8px;
  padding: 18px;
}

.homeworks__form {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
}

.homeworks__wide {
  grid-column: 1 / -1;
}

.homeworks__config {
  border-top: 1px solid #d7dde8;
  margin-top: 6px;
  padding-top: 14px;
}

.homeworks__section-header,
.homeworks__actions,
.homeworks__toolbar,
.homeworks__row-actions,
.homeworks__pagination,
.homeworks__checkbox {
  align-items: center;
  display: flex;
  gap: 8px;
}

.homeworks__section-header {
  justify-content: space-between;
}

.homeworks__pagination {
  justify-content: flex-end;
  margin-top: 12px;
}

.homeworks__config-card {
  border: 1px solid #d7dde8;
  border-radius: 8px;
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  margin-top: 12px;
  padding: 12px;
}

label {
  display: grid;
  gap: 6px;
}

input,
select,
textarea {
  background: #ffffff;
  border: 1px solid #b8c2d2;
  color: #111827;
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

.homeworks__feedback {
  color: #116329;
  margin-top: 14px;
}

.homeworks__error {
  color: #b42318;
  margin-top: 14px;
}

.homeworks__badge {
  border: 1px solid #94a3b8;
  border-radius: 6px;
  display: inline-block;
  margin-left: 8px;
  padding: 2px 6px;
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
</style>
