<template>
  <main class="labs">
    <section class="labs__panel" aria-label="实验创建与编辑">
      <form class="labs__form" @submit.prevent="submit">
        <label>
          <span>实验名称</span>
          <input v-model="form.title" name="title" type="text" />
        </label>
        <label class="labs__wide">
          <span>实验说明</span>
          <textarea v-model="form.description" name="description" rows="4" />
        </label>
        <label>
          <span>截止时间</span>
          <input v-model="form.deadline" name="deadline" type="datetime-local" />
        </label>
        <label>
          <span>满分</span>
          <input v-model="form.maxScore" name="maxScore" type="number" min="1" />
        </label>
        <label>
          <span>评测方式</span>
          <select v-model="form.evaluationMode" name="evaluationMode">
            <option value="DOCKER_IO">DOCKER_IO</option>
            <option value="MIXED">MIXED</option>
          </select>
        </label>
        <label>
          <span>语言限制</span>
          <input v-model="form.allowedLanguages" name="allowedLanguages" type="text" />
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
          <span>附件占位(ID 逗号分隔)</span>
          <input v-model="form.attachmentIds" name="attachmentIds" type="text" />
        </label>
        <label class="labs__checkbox">
          <input v-model="form.autoEvaluate" name="autoEvaluate" type="checkbox" />
          <span>自动评测</span>
        </label>
        <label class="labs__checkbox">
          <input v-model="form.reportRequired" name="reportRequired" type="checkbox" />
          <span>要求实验报告</span>
        </label>

        <section class="labs__testcases" aria-label="测试用例列表">
          <header class="labs__testcases-header">
            <h2>测试用例</h2>
            <button type="button" @click="addTestcase">新增用例</button>
          </header>
          <div
            v-for="(testcase, index) in form.testcases"
            :key="`testcase-${index}`"
            class="labs__testcase-card"
          >
            <label>
              <span>输入</span>
              <textarea v-model="testcase.input" :name="`testcase-input-${index}`" rows="2" />
            </label>
            <label>
              <span>输出</span>
              <textarea v-model="testcase.expectedOutput" :name="`testcase-output-${index}`" rows="2" />
            </label>
            <label>
              <span>分值</span>
              <input v-model="testcase.scoreWeight" :name="`testcase-weight-${index}`" type="number" min="0" />
            </label>
            <label>
              <span>时间限制(ms)</span>
              <input v-model="testcase.timeLimitMs" :name="`testcase-time-${index}`" type="number" min="1" />
            </label>
            <label>
              <span>内存限制(KB)</span>
              <input v-model="testcase.memoryLimitKb" :name="`testcase-memory-${index}`" type="number" min="1" />
            </label>
            <label>
              <span>排序</span>
              <input v-model="testcase.orderNum" :name="`testcase-order-${index}`" type="number" min="0" />
            </label>
            <label class="labs__checkbox">
              <input v-model="testcase.public" :name="`testcase-public-${index}`" type="checkbox" />
              <span>公开用例</span>
            </label>
            <button type="button" @click="removeTestcase(index)">删除用例</button>
          </div>
        </section>

        <div class="labs__form-actions">
          <button type="submit" :disabled="saving">{{ submitText }}</button>
          <button type="button" :disabled="saving" @click="resetForm">清空</button>
        </div>
      </form>

      <p v-if="feedback" class="labs__feedback">{{ feedback }}</p>
      <p v-if="errorMessage" class="labs__error">{{ errorMessage }}</p>
    </section>

    <section class="labs__list" aria-label="实验列表">
      <div class="labs__toolbar">
        <label>
          <span>状态筛选</span>
          <select v-model="selectedStatus" @change="loadLabs">
            <option value="">全部</option>
            <option value="DRAFT">DRAFT</option>
            <option value="PUBLISHED">PUBLISHED</option>
            <option value="CLOSED">CLOSED</option>
          </select>
        </label>
      </div>

      <p v-if="loading">加载中</p>
      <p v-else-if="labs.length === 0">暂无实验</p>
      <table v-else>
        <thead>
          <tr>
            <th>名称</th>
            <th>状态</th>
            <th>截止时间</th>
            <th>满分</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="lab in labs" :key="lab.id">
            <td>{{ lab.title }}</td>
            <td>{{ lab.status }}</td>
            <td>{{ formatDeadline(lab.deadline) }}</td>
            <td>{{ lab.maxScore }}</td>
            <td class="labs__row-actions">
              <button v-if="lab.status === 'DRAFT'" type="button" @click="editLab(lab.id)">编辑</button>
              <button v-if="lab.status === 'DRAFT'" type="button" @click="publish(lab.id)">发布</button>
              <button v-if="lab.status === 'PUBLISHED'" type="button" @click="close(lab.id)">截止</button>
              <button v-if="lab.status === 'DRAFT'" type="button" @click="removeLab(lab.id)">删除草稿</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import {
  closeLab,
  createLab,
  deleteLab,
  getLabDetail,
  listLabs,
  publishLab,
  updateLab
} from '../../api/lab/labs';
import type {
  LabEvaluationMode,
  LabExperimentPayload,
  LabExperimentStatus,
  LabExperimentSummary,
  LabTestcase,
  LabTestcasePayload
} from '../../types/lab';

const props = defineProps<{
  courseId: number;
}>();

const labs = ref<LabExperimentSummary[]>([]);
const loading = ref(false);
const saving = ref(false);
const feedback = ref('');
const errorMessage = ref('');
const editingId = ref<number | null>(null);
const selectedStatus = ref('');

const form = reactive({
  title: '',
  description: '',
  deadline: '',
  maxScore: '100',
  attachmentIds: '',
  allowedLanguages: '',
  evaluationMode: 'DOCKER_IO' as LabEvaluationMode,
  autoEvaluate: true,
  reportRequired: false,
  timeLimitMs: '60000',
  memoryLimitKb: '262144',
  testcases: [createEmptyTestcase()]
});

const submitText = computed(() => (editingId.value === null ? '保存' : '更新'));

onMounted(loadLabs);

async function loadLabs() {
  loading.value = true;
  errorMessage.value = '';
  try {
    labs.value = await listLabs(
      props.courseId,
      selectedStatus.value ? (selectedStatus.value as LabExperimentStatus) : undefined
    );
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '实验列表加载失败';
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
      await createLab(props.courseId, payload);
      feedback.value = '保存成功';
    } else {
      await updateLab(editingId.value, payload);
      feedback.value = '更新成功';
    }
    resetForm();
    await loadLabs();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '实验保存失败';
  } finally {
    saving.value = false;
  }
}

async function editLab(labId: number) {
  feedback.value = '';
  errorMessage.value = '';
  try {
    const detail = await getLabDetail(labId);
    editingId.value = detail.id;
    form.title = detail.title;
    form.description = detail.description;
    form.deadline = toDateTimeLocal(detail.deadline);
    form.maxScore = String(detail.maxScore);
    form.attachmentIds = detail.attachmentIds.join(',');
    form.allowedLanguages = detail.allowedLanguages ?? '';
    form.evaluationMode = detail.evaluationMode;
    form.autoEvaluate = detail.autoEvaluate;
    form.reportRequired = detail.reportRequired;
    form.timeLimitMs = String(detail.timeLimitMs);
    form.memoryLimitKb = String(detail.memoryLimitKb);
    form.testcases = detail.testcases.length === 0
      ? [createEmptyTestcase()]
      : detail.testcases.map((testcase) => mapTestcase(testcase));
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '实验详情加载失败';
  }
}

async function publish(labId: number) {
  feedback.value = '';
  errorMessage.value = '';
  try {
    await publishLab(labId);
    feedback.value = '发布成功';
    await loadLabs();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '实验发布失败';
  }
}

async function close(labId: number) {
  feedback.value = '';
  errorMessage.value = '';
  try {
    await closeLab(labId);
    feedback.value = '截止成功';
    await loadLabs();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '实验截止失败';
  }
}

async function removeLab(labId: number) {
  feedback.value = '';
  errorMessage.value = '';
  try {
    await deleteLab(labId);
    feedback.value = '草稿已删除';
    if (editingId.value === labId) {
      resetForm();
    }
    await loadLabs();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '草稿删除失败';
  }
}

function addTestcase() {
  form.testcases.push(createEmptyTestcase(form.testcases.length));
}

function removeTestcase(index: number) {
  if (form.testcases.length === 1) {
    form.testcases[0] = createEmptyTestcase();
    return;
  }
  form.testcases.splice(index, 1);
  form.testcases.forEach((testcase, testcaseIndex) => {
    testcase.orderNum = testcaseIndex + 1;
  });
}

function validateForm() {
  const errors: string[] = [];
  const deadline = new Date(form.deadline);
  const maxScore = Number(form.maxScore);
  const timeLimitMs = Number(form.timeLimitMs);
  const memoryLimitKb = Number(form.memoryLimitKb);
  const activeTestcases = collectActiveTestcases();

  if (!form.title.trim()) {
    errors.push('实验名称不能为空');
  }
  if (!form.description.trim()) {
    errors.push('实验说明不能为空');
  }
  if (!form.deadline || Number.isNaN(deadline.getTime()) || deadline.getTime() <= Date.now()) {
    errors.push('截止时间必须晚于当前时间');
  }
  if (!Number.isFinite(maxScore) || maxScore <= 0) {
    errors.push('满分必须大于 0');
  }
  if (!Number.isFinite(timeLimitMs) || timeLimitMs <= 0) {
    errors.push('时间限制必须大于 0');
  }
  if (!Number.isFinite(memoryLimitKb) || memoryLimitKb <= 0) {
    errors.push('内存限制必须大于 0');
  }

  activeTestcases.forEach((testcase, index) => {
    if (!testcase.input) {
      errors.push(`测试用例 ${index + 1} 输入不能为空`);
    }
    if (!testcase.expectedOutput) {
      errors.push(`测试用例 ${index + 1} 输出不能为空`);
    }
    if (!Number.isFinite(testcase.scoreWeight) || testcase.scoreWeight < 0) {
      errors.push(`测试用例 ${index + 1} 分值不能为负数`);
    }
  });

  return errors.join('；');
}

function buildPayload(): LabExperimentPayload {
  return {
    title: form.title.trim(),
    description: form.description.trim(),
    deadline: formatForApi(form.deadline),
    maxScore: Number(form.maxScore),
    attachmentIds: parseAttachmentIds(form.attachmentIds),
    allowedLanguages: form.allowedLanguages.trim() || null,
    evaluationMode: form.evaluationMode,
    autoEvaluate: form.autoEvaluate,
    reportRequired: form.reportRequired,
    timeLimitMs: Number(form.timeLimitMs),
    memoryLimitKb: Number(form.memoryLimitKb),
    testcases: collectActiveTestcases()
  };
}

function resetForm() {
  editingId.value = null;
  form.title = '';
  form.description = '';
  form.deadline = '';
  form.maxScore = '100';
  form.attachmentIds = '';
  form.allowedLanguages = '';
  form.evaluationMode = 'DOCKER_IO';
  form.autoEvaluate = true;
  form.reportRequired = false;
  form.timeLimitMs = '60000';
  form.memoryLimitKb = '262144';
  form.testcases = [createEmptyTestcase()];
}

function createEmptyTestcase(index = 0) {
  return reactive({
    input: '',
    expectedOutput: '',
    scoreWeight: '100',
    public: true,
    timeLimitMs: '1000',
    memoryLimitKb: '65536',
    orderNum: index + 1
  });
}

function mapTestcase(testcase: LabTestcase) {
  return reactive({
    input: testcase.input,
    expectedOutput: testcase.expectedOutput,
    scoreWeight: String(testcase.scoreWeight),
    public: testcase.public,
    timeLimitMs: String(testcase.timeLimitMs),
    memoryLimitKb: String(testcase.memoryLimitKb),
    orderNum: testcase.orderNum
  });
}

function parseAttachmentIds(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => Number(item))
    .filter((item) => Number.isInteger(item) && item > 0);
}

function collectActiveTestcases(): LabTestcasePayload[] {
  return form.testcases
    .map((testcase, index) => ({
      input: testcase.input.trim(),
      expectedOutput: testcase.expectedOutput.trim(),
      scoreWeight: Number(testcase.scoreWeight),
      public: testcase.public,
      timeLimitMs: Number(testcase.timeLimitMs),
      memoryLimitKb: Number(testcase.memoryLimitKb),
      orderNum: Number(testcase.orderNum || index + 1)
    }))
    .filter((testcase) => {
      const hasContent = testcase.input || testcase.expectedOutput;
      const hasCustomLimits =
        testcase.scoreWeight !== 100 || testcase.timeLimitMs !== 1000 || testcase.memoryLimitKb !== 65536;
      return Boolean(hasContent || hasCustomLimits);
    });
}

function formatForApi(value: string) {
  return `${value}:00`;
}

function toDateTimeLocal(value: string) {
  return value.slice(0, 16);
}

function formatDeadline(value: string) {
  return value.replace('T', ' ').slice(0, 16);
}
</script>

<style scoped>
.labs {
  background: #f6f8fb;
  color: #1f2937;
  display: grid;
  gap: 20px;
  min-height: 100vh;
  padding: 24px;
}

.labs__panel,
.labs__list {
  background: #ffffff;
  border: 1px solid #d7dde8;
  border-radius: 8px;
  padding: 18px;
}

.labs__form {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.labs__wide,
.labs__testcases {
  grid-column: 1 / -1;
}

.labs__testcases {
  border-top: 1px solid #d7dde8;
  margin-top: 8px;
  padding-top: 16px;
}

.labs__testcases-header,
.labs__form-actions,
.labs__toolbar,
.labs__row-actions,
.labs__checkbox {
  align-items: center;
  display: flex;
  gap: 8px;
}

.labs__testcases-header {
  justify-content: space-between;
}

.labs__testcase-card {
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

.labs__feedback {
  color: #116329;
  margin-top: 14px;
}

.labs__error {
  color: #b42318;
  margin-top: 14px;
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
