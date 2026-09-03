<template>
  <main class="lab-editor" data-testid="lab-editor">
    <PageHeader
      :title="activeLabId === undefined ? '创建实验' : '编辑实验草稿'"
      eyebrow="实验配置"
      subtitle="按发布所需信息分区填写；保存后仍可在草稿阶段继续调整。"
    >
      <template #actions>
        <RouterLink class="button" :to="backRoute">返回实验管理</RouterLink>
      </template>
    </PageHeader>

    <PageState
      v-if="loading"
      state="loading"
      title="正在准备实验编辑器"
      message="同步课程章节、资源与实验配置。"
    />
    <PageState
      v-else-if="loadError"
      state="error"
      title="实验编辑器加载失败"
      :message="loadError"
      retry-label="重新加载"
      @retry="loadEditor"
    />
    <PageState
      v-else-if="editingBlocked"
      state="forbidden"
      title="只有草稿实验可以编辑"
      message="该实验已经进入发布流程。请返回实验管理查看提交、统计或生命周期状态。"
    >
      <template #actions>
        <RouterLink class="button button--primary" :to="backRoute">返回实验详情</RouterLink>
      </template>
    </PageState>

    <form
      v-else
      class="lab-editor__form"
      data-testid="lab-editor-form"
      novalidate
      @submit.prevent="saveDraft"
    >
      <p v-if="feedback" class="notice notice--success" role="status">{{ feedback }}</p>
      <div v-if="validationErrors.length > 0" class="notice notice--danger" data-testid="editor-error" role="alert">
        <strong>请完成以下发布必填项：</strong>
        <ul>
          <li v-for="message in validationErrors" :key="message">{{ message }}</li>
        </ul>
      </div>
      <p v-else-if="saveError" class="notice notice--danger" data-testid="editor-error" role="alert">
        {{ saveError }}
      </p>

      <section class="editor-section" aria-labelledby="lab-basic-heading">
        <header class="editor-section__header">
          <span>01</span>
          <div>
            <h2 id="lab-basic-heading">基础信息</h2>
            <p>说明实验目标、归属章节、截止时间与评分上限。</p>
          </div>
        </header>
        <div class="editor-grid">
          <label class="field field--wide">
            <span>实验名称</span>
            <input v-model="form.title" name="title" type="text" autocomplete="off" />
          </label>
          <label class="field field--wide">
            <span>实验说明</span>
            <textarea v-model="form.description" name="description" rows="6" />
          </label>
          <label class="field">
            <span>课程章节</span>
            <select v-model="form.chapterId" name="chapterId">
              <option value="">不关联章节</option>
              <option v-for="chapter in flatChapters" :key="chapter.id" :value="String(chapter.id)">
                {{ chapter.label }}
              </option>
            </select>
          </label>
          <label class="field">
            <span>截止时间</span>
            <input v-model="form.deadline" name="deadline" type="datetime-local" />
          </label>
          <label class="field">
            <span>满分</span>
            <input v-model="form.maxScore" name="maxScore" type="number" min="1" step="1" />
          </label>
        </div>
      </section>

      <section class="editor-section" aria-labelledby="lab-content-heading">
        <header class="editor-section__header">
          <span>02</span>
          <div>
            <h2 id="lab-content-heading">内容与附件</h2>
            <p>从课程资源中按名称选择；也可以先上传新资源并自动选中。</p>
          </div>
        </header>

        <fieldset class="choice-group">
          <legend>允许使用的语言</legend>
          <label v-for="language in languageOptions" :key="language.value" class="choice-card">
            <input
              v-model="selectedLanguages"
              type="checkbox"
              :value="language.value"
              :data-testid="`language-${language.value}`"
            />
            <span>{{ language.label }}</span>
          </label>
        </fieldset>

        <fieldset class="resource-picker">
          <legend>学生可见附件</legend>
          <p v-if="resources.length === 0" class="muted">当前课程还没有可选资源，可在下方直接上传。</p>
          <label v-for="resource in resources" :key="resource.id" class="resource-option">
            <input
              v-model="selectedResourceIds"
              type="checkbox"
              :value="resource.id"
              :data-testid="`resource-${resource.id}`"
            />
            <span>
              <strong>{{ resource.name }}</strong>
              <small>{{ resourceTypeLabel(resource.resourceType) }} · {{ formatFileSize(resource.fileSize) }}</small>
            </span>
          </label>
        </fieldset>

        <div class="resource-upload">
          <label class="field field--wide">
            <span>上传新的课程附件</span>
            <input name="attachmentUpload" type="file" @change="selectUploadFile" />
          </label>
          <button
            class="button"
            data-testid="upload-resource"
            type="button"
            :disabled="uploading || uploadFile === null"
            @click="uploadAttachment"
          >
            {{ uploading ? '上传中…' : '上传并选中' }}
          </button>
          <p v-if="uploadError" class="inline-error" role="alert">{{ uploadError }}</p>
        </div>
      </section>

      <section class="editor-section" aria-labelledby="lab-evaluation-heading">
        <header class="editor-section__header">
          <span>03</span>
          <div>
            <h2 id="lab-evaluation-heading">评测与提交规则</h2>
            <p>使用业务名称配置评测方式、自动评测和报告要求。</p>
          </div>
        </header>
        <div class="editor-grid">
          <label class="field">
            <span>评测方式</span>
            <select v-model="form.evaluationMode" name="evaluationMode">
              <option value="DOCKER_IO">自动评测</option>
              <option value="MIXED">自动评测 + 教师评分</option>
              <option value="MANUAL">教师评分</option>
            </select>
          </label>
          <label class="field">
            <span>默认时间限制（毫秒）</span>
            <input v-model="form.timeLimitMs" name="timeLimitMs" type="number" min="1" step="1" />
          </label>
          <label class="field">
            <span>默认内存限制（KB）</span>
            <input v-model="form.memoryLimitKb" name="memoryLimitKb" type="number" min="1" step="1" />
          </label>
          <label class="switch-field">
            <input v-model="form.autoEvaluate" name="autoEvaluate" type="checkbox" />
            <span><strong>提交后自动评测</strong><small>适用于包含自动评测的实验。</small></span>
          </label>
          <label class="switch-field">
            <input v-model="form.reportRequired" name="reportRequired" type="checkbox" />
            <span><strong>要求实验报告</strong><small>学生提交时需补充报告材料。</small></span>
          </label>
        </div>
      </section>

      <section class="editor-section" aria-labelledby="lab-testcase-heading">
        <header class="editor-section__header editor-section__header--action">
          <span>04</span>
          <div>
            <h2 id="lab-testcase-heading">测试用例</h2>
            <p>自动或混合评测至少需要一条完整用例；分值总和应等于满分。</p>
          </div>
          <button class="button" type="button" @click="addTestcase">新增用例</button>
        </header>

        <p v-if="form.testcases.length === 0" class="muted">教师评分实验可以不配置测试用例。</p>
        <article v-for="(testcase, index) in form.testcases" :key="testcase.key" class="testcase-card">
          <header>
            <h3>用例 {{ index + 1 }}</h3>
            <button v-if="form.testcases.length > 1" type="button" @click="removeTestcase(index)">移除</button>
          </header>
          <div class="editor-grid">
            <label class="field field--wide">
              <span>标准输入</span>
              <textarea v-model="testcase.input" :name="`testcase-input-${index}`" rows="3" />
            </label>
            <label class="field field--wide">
              <span>预期输出</span>
              <textarea v-model="testcase.expectedOutput" :name="`testcase-output-${index}`" rows="3" />
            </label>
            <label class="field">
              <span>分值</span>
              <input v-model="testcase.scoreWeight" :name="`testcase-weight-${index}`" type="number" min="0" />
            </label>
            <label class="field">
              <span>时间限制（毫秒）</span>
              <input v-model="testcase.timeLimitMs" :name="`testcase-time-${index}`" type="number" min="1" />
            </label>
            <label class="field">
              <span>内存限制（KB）</span>
              <input v-model="testcase.memoryLimitKb" :name="`testcase-memory-${index}`" type="number" min="1" />
            </label>
            <label class="switch-field">
              <input v-model="testcase.public" :name="`testcase-public-${index}`" type="checkbox" />
              <span><strong>向学生公开</strong><small>公开输入与期望输出。</small></span>
            </label>
          </div>
        </article>
      </section>

      <section class="editor-section publish-check" aria-labelledby="lab-publish-check-heading">
        <header class="editor-section__header">
          <span>05</span>
          <div>
            <h2 id="lab-publish-check-heading">发布检查</h2>
            <p>编辑器只保存草稿；发布动作在实验管理页完成，并再次要求确认。</p>
          </div>
        </header>
        <ul>
          <li :data-ready="Boolean(form.title.trim())">实验名称已填写</li>
          <li :data-ready="Boolean(form.description.trim())">实验说明已填写</li>
          <li :data-ready="deadlineIsFuture">截止时间晚于当前时间</li>
          <li :data-ready="testcasesAreReady">评测用例与分值配置可发布</li>
        </ul>
      </section>

      <footer class="editor-actions">
        <RouterLink class="button" :to="backRoute">取消</RouterLink>
        <button class="button button--primary" type="submit" :disabled="saving || uploading">
          {{ saving ? '保存中…' : activeLabId === undefined ? '保存草稿' : '更新草稿' }}
        </button>
      </footer>
    </form>
  </main>
</template>

<script setup lang="ts">
import { computed, inject, reactive, ref, watch } from 'vue';
import { RouterLink, routerKey } from 'vue-router';
import { listChapters, listResources, uploadResource } from '../../api/crs/courses';
import { createLab, getLabDetail, updateLab } from '../../api/lab/labs';
import PageHeader from '../../components/foundation/PageHeader.vue';
import PageState from '../../components/foundation/PageState.vue';
import type { Chapter, CourseResource, ResourceType } from '../../types/crs';
import type {
  LabEvaluationMode,
  LabExperimentDetail,
  LabExperimentPayload,
  LabTestcasePayload
} from '../../types/lab';
import { localizedLabError } from './labDisplay';

interface TestcaseDraft {
  key: number;
  input: string;
  expectedOutput: string;
  scoreWeight: string;
  public: boolean;
  timeLimitMs: string;
  memoryLimitKb: string;
}

const props = defineProps<{ courseId: number; labId?: number }>();
const appRouter = inject(routerKey, null);
const activeLabId = ref<number | undefined>(props.labId);
const chapters = ref<Chapter[]>([]);
const resources = ref<CourseResource[]>([]);
const selectedResourceIds = ref<number[]>([]);
const selectedLanguages = ref<string[]>([]);
const loadedDetail = ref<LabExperimentDetail | null>(null);
const loading = ref(false);
const loadError = ref('');
const validationErrors = ref<string[]>([]);
const saveError = ref('');
const feedback = ref('');
const saving = ref(false);
const uploadFile = ref<File | null>(null);
const uploadError = ref('');
const uploading = ref(false);
let editorGeneration = 0;
let testcaseKey = 1;

const form = reactive({
  title: '',
  description: '',
  chapterId: '',
  deadline: '',
  maxScore: '100',
  evaluationMode: 'DOCKER_IO' as LabEvaluationMode,
  autoEvaluate: true,
  reportRequired: false,
  timeLimitMs: '60000',
  memoryLimitKb: '262144',
  testcases: [emptyTestcase()]
});

const languageOptions = [
  { value: 'python', label: 'Python' },
  { value: 'java', label: 'Java' },
  { value: 'cpp', label: 'C++' },
  { value: 'javascript', label: 'JavaScript' }
] as const;

const flatChapters = computed(() => flattenChapters(chapters.value));
const editingBlocked = computed(() => (
  activeLabId.value !== undefined && loadedDetail.value !== null && loadedDetail.value.status !== 'DRAFT'
));
const backRoute = computed(() => activeLabId.value === undefined
  ? { name: 'lab-manage', params: { courseId: props.courseId } }
  : { name: 'lab-manage-detail', params: { courseId: props.courseId, labId: activeLabId.value } });
const deadlineIsFuture = computed(() => {
  const timestamp = new Date(form.deadline).getTime();
  return Number.isFinite(timestamp) && timestamp > Date.now();
});
const completeTestcases = computed(() => form.testcases.filter(hasTestcaseContent));
const testcasesAreReady = computed(() => {
  if (form.evaluationMode === 'MANUAL') return true;
  const maxScore = Number(form.maxScore);
  return completeTestcases.value.length > 0
    && completeTestcases.value.every((testcase) => testcase.input.trim() && testcase.expectedOutput.trim())
    && completeTestcases.value.reduce((total, testcase) => total + Number(testcase.scoreWeight), 0) === maxScore;
});

watch(
  () => [props.courseId, props.labId] as const,
  () => {
    activeLabId.value = props.labId;
    void loadEditor();
  },
  { immediate: true }
);

async function loadEditor() {
  const generation = ++editorGeneration;
  loading.value = true;
  loadError.value = '';
  feedback.value = '';
  validationErrors.value = [];
  saveError.value = '';
  try {
    const [chapterResult, resourceResult, detailResult] = await Promise.all([
      listChapters(props.courseId),
      listResources(props.courseId),
      props.labId === undefined ? Promise.resolve(null) : getLabDetail(props.labId)
    ]);
    if (generation !== editorGeneration) return;
    if (detailResult && (Number(detailResult.courseId) !== props.courseId || detailResult.id !== props.labId)) {
      throw new Error('实验与当前课程不匹配，请返回实验管理重新进入。');
    }
    chapters.value = chapterResult;
    resources.value = resourceResult.filter((resource) => resource.courseId === props.courseId);
    loadedDetail.value = detailResult;
    if (detailResult) hydrateForm(detailResult);
    else resetForm();
  } catch (error) {
    if (generation !== editorGeneration) return;
    loadError.value = localizedLabError(error, '实验编辑器加载失败，请稍后重试。');
  } finally {
    if (generation === editorGeneration) loading.value = false;
  }
}

function hydrateForm(detail: LabExperimentDetail) {
  form.title = detail.title;
  form.description = detail.description;
  form.chapterId = detail.chapterId === null ? '' : String(detail.chapterId);
  form.deadline = detail.deadline.slice(0, 16);
  form.maxScore = String(detail.maxScore);
  form.evaluationMode = detail.evaluationMode;
  form.autoEvaluate = detail.autoEvaluate;
  form.reportRequired = detail.reportRequired;
  form.timeLimitMs = String(detail.timeLimitMs);
  form.memoryLimitKb = String(detail.memoryLimitKb);
  form.testcases = detail.testcases.length > 0
    ? detail.testcases.map((testcase) => ({
        key: testcaseKey++,
        input: testcase.input,
        expectedOutput: testcase.expectedOutput,
        scoreWeight: String(testcase.scoreWeight),
        public: testcase.public,
        timeLimitMs: String(testcase.timeLimitMs),
        memoryLimitKb: String(testcase.memoryLimitKb)
      }))
    : [];
  selectedLanguages.value = detail.allowedLanguages
    ? detail.allowedLanguages.split(',').map((value) => value.trim()).filter(Boolean)
    : [];
  selectedResourceIds.value = detail.attachmentIds.filter((id) => resources.value.some((resource) => resource.id === id));
}

function resetForm() {
  form.title = '';
  form.description = '';
  form.chapterId = '';
  form.deadline = '';
  form.maxScore = '100';
  form.evaluationMode = 'DOCKER_IO';
  form.autoEvaluate = true;
  form.reportRequired = false;
  form.timeLimitMs = '60000';
  form.memoryLimitKb = '262144';
  form.testcases = [emptyTestcase()];
  selectedLanguages.value = [];
  selectedResourceIds.value = [];
  loadedDetail.value = null;
}

async function saveDraft() {
  if (saving.value) return;
  validationErrors.value = validateForm();
  saveError.value = '';
  feedback.value = '';
  if (validationErrors.value.length > 0) return;
  saving.value = true;
  try {
    const payload = buildPayload();
    const targetLabId = activeLabId.value;
    const result = targetLabId === undefined
      ? await createLab(props.courseId, payload)
      : await updateLab(targetLabId, payload);
    loadedDetail.value = result;
    if (targetLabId === undefined) {
      activeLabId.value = result.id;
      if (appRouter) {
        await appRouter.replace({
          name: 'lab-edit',
          params: { courseId: props.courseId, labId: result.id }
        }).catch(() => undefined);
      }
      feedback.value = '草稿已保存，可返回实验管理继续发布。';
    } else {
      feedback.value = '草稿已更新。';
    }
  } catch (error) {
    saveError.value = localizedLabError(error, '草稿保存失败，请检查内容后重试。');
  } finally {
    saving.value = false;
  }
}

function validateForm() {
  const errors: string[] = [];
  if (!form.title.trim()) errors.push('实验名称不能为空');
  if (!form.description.trim()) errors.push('实验说明不能为空');
  if (!deadlineIsFuture.value) errors.push('截止时间必须晚于当前时间');
  if (!isPositiveInteger(form.maxScore)) errors.push('满分必须是正整数');
  if (!isPositiveInteger(form.timeLimitMs)) errors.push('默认时间限制必须是正整数');
  if (!isPositiveInteger(form.memoryLimitKb)) errors.push('默认内存限制必须是正整数');
  if (form.evaluationMode !== 'MANUAL') {
    if (completeTestcases.value.length === 0) errors.push('自动或混合评测至少需要一条测试用例');
    completeTestcases.value.forEach((testcase, index) => {
      if (!testcase.input.trim() || !testcase.expectedOutput.trim()) {
        errors.push(`测试用例 ${index + 1} 的输入和输出不能为空`);
      }
      if (!isNonNegativeInteger(testcase.scoreWeight)) errors.push(`测试用例 ${index + 1} 的分值必须是非负整数`);
      if (!isPositiveInteger(testcase.timeLimitMs)) errors.push(`测试用例 ${index + 1} 的时间限制必须是正整数`);
      if (!isPositiveInteger(testcase.memoryLimitKb)) errors.push(`测试用例 ${index + 1} 的内存限制必须是正整数`);
    });
    const scoreTotal = completeTestcases.value.reduce((total, testcase) => total + Number(testcase.scoreWeight), 0);
    if (completeTestcases.value.length > 0 && scoreTotal !== Number(form.maxScore)) {
      errors.push(`测试用例分值合计需等于满分（当前 ${scoreTotal} 分）`);
    }
  }
  return [...new Set(errors)];
}

function buildPayload(): LabExperimentPayload {
  return {
    chapterId: form.chapterId ? Number(form.chapterId) : null,
    title: form.title.trim(),
    description: form.description.trim(),
    deadline: toUtcInstant(form.deadline),
    maxScore: Number(form.maxScore),
    attachmentIds: [...selectedResourceIds.value],
    allowedLanguages: selectedLanguages.value.length > 0 ? selectedLanguages.value.join(',') : null,
    evaluationMode: form.evaluationMode,
    autoEvaluate: form.evaluationMode === 'MANUAL' ? false : form.autoEvaluate,
    reportRequired: form.reportRequired,
    timeLimitMs: Number(form.timeLimitMs),
    memoryLimitKb: Number(form.memoryLimitKb),
    testcases: form.evaluationMode === 'MANUAL' ? [] : completeTestcases.value.map(toTestcasePayload)
  };
}

function toUtcInstant(value: string) {
  const localDateTime = value.length === 16 ? `${value}:00` : value;
  return new Date(localDateTime).toISOString();
}

function toTestcasePayload(testcase: TestcaseDraft, index: number): LabTestcasePayload {
  return {
    input: testcase.input,
    expectedOutput: testcase.expectedOutput,
    scoreWeight: Number(testcase.scoreWeight),
    public: testcase.public,
    timeLimitMs: Number(testcase.timeLimitMs),
    memoryLimitKb: Number(testcase.memoryLimitKb),
    orderNum: index + 1
  };
}

function addTestcase() {
  form.testcases.push(emptyTestcase());
}

function removeTestcase(index: number) {
  form.testcases.splice(index, 1);
}

function emptyTestcase(): TestcaseDraft {
  return {
    key: testcaseKey++,
    input: '',
    expectedOutput: '',
    scoreWeight: '100',
    public: true,
    timeLimitMs: '1000',
    memoryLimitKb: '65536'
  };
}

function hasTestcaseContent(testcase: TestcaseDraft) {
  return Boolean(testcase.input.trim() || testcase.expectedOutput.trim());
}

function isPositiveInteger(value: string) {
  return Number.isInteger(Number(value)) && Number(value) > 0;
}

function isNonNegativeInteger(value: string) {
  return Number.isInteger(Number(value)) && Number(value) >= 0;
}

function selectUploadFile(event: Event) {
  uploadFile.value = (event.target as HTMLInputElement).files?.[0] ?? null;
  uploadError.value = '';
}

async function uploadAttachment() {
  const file = uploadFile.value;
  if (!file) return;
  uploading.value = true;
  uploadError.value = '';
  try {
    const uploaded = await uploadResource(props.courseId, {
      chapterId: form.chapterId ? Number(form.chapterId) : null,
      name: file.name,
      resourceType: resourceTypeForFile(file),
      visibility: 'STUDENT'
    }, file);
    resources.value = [...resources.value.filter((resource) => resource.id !== uploaded.id), uploaded];
    if (!selectedResourceIds.value.includes(uploaded.id)) {
      selectedResourceIds.value = [...selectedResourceIds.value, uploaded.id];
    }
    uploadFile.value = null;
  } catch (error) {
    uploadError.value = localizedLabError(error, '附件上传失败，请稍后重试。');
  } finally {
    uploading.value = false;
  }
}

function resourceTypeForFile(file: File): ResourceType {
  const name = file.name.toLowerCase();
  if (/\.(zip|rar|7z|tar|gz)$/.test(name)) return 'ARCHIVE';
  if (/\.(pdf|doc|docx|txt|md)$/.test(name)) return 'DOCUMENT';
  if (/\.(ppt|pptx)$/.test(name)) return 'COURSEWARE';
  if (/\.(png|jpe?g|gif|webp|svg)$/.test(name)) return 'IMAGE';
  if (/\.(mp4|webm|mov)$/.test(name)) return 'VIDEO';
  return 'OTHER';
}

function resourceTypeLabel(type: ResourceType) {
  return {
    DOCUMENT: '文档',
    COURSEWARE: '课件',
    VIDEO: '视频',
    IMAGE: '图片',
    ARCHIVE: '压缩包',
    LINK: '链接',
    OTHER: '其他'
  }[type];
}

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`;
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}

function flattenChapters(items: Chapter[], depth = 0): Array<{ id: number; label: string }> {
  return items.flatMap((chapter) => [
    { id: chapter.id, label: `${'— '.repeat(depth)}${chapter.chapterName}` },
    ...flattenChapters(chapter.children ?? [], depth + 1)
  ]);
}
</script>

<style scoped>
.lab-editor {
  display: grid;
  gap: 18px;
  width: 100%;
  min-width: 0;
  padding-bottom: 42px;
  color: var(--oj-ink);
}

.lab-editor__form {
  display: grid;
  gap: 18px;
}

.editor-section {
  display: grid;
  gap: 18px;
  min-width: 0;
  padding: 22px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  box-shadow: var(--oj-shadow-soft);
  backdrop-filter: var(--oj-blur);
  -webkit-backdrop-filter: var(--oj-blur);
}

.editor-section__header {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: start;
  gap: 14px;
}

.editor-section__header--action {
  grid-template-columns: auto minmax(0, 1fr) auto;
}

.editor-section__header > span {
  display: grid;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  place-items: center;
  background: var(--oj-brand-soft);
  color: var(--oj-brand);
  font-size: 0.76rem;
  font-weight: 900;
}

.editor-section h2,
.editor-section h3,
.editor-section p {
  margin: 0;
}

.editor-section h2 {
  font-size: 1.05rem;
}

.editor-section__header p,
.muted {
  margin-top: 5px;
  color: var(--oj-muted);
  font-size: 0.83rem;
  line-height: 1.55;
}

.editor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.field,
.switch-field {
  display: grid;
  gap: 7px;
  min-width: 0;
  color: var(--oj-ink-soft);
  font-size: 0.82rem;
  font-weight: 800;
}

.field--wide {
  grid-column: 1 / -1;
}

.field input,
.field textarea,
.field select {
  width: 100%;
  min-width: 0;
  padding: 10px 11px;
  border: 1px solid var(--oj-line-strong);
  border-radius: var(--oj-radius);
  background: var(--oj-surface-solid);
  color: var(--oj-ink);
  font: inherit;
  font-weight: 600;
}

.field textarea {
  resize: vertical;
}

.choice-group,
.resource-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  min-width: 0;
  padding: 0;
  border: 0;
}

.choice-group legend,
.resource-picker legend {
  width: 100%;
  margin-bottom: 8px;
  color: var(--oj-ink);
  font-size: 0.86rem;
  font-weight: 800;
}

.choice-card,
.resource-option,
.switch-field {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 11px 12px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: rgba(255, 255, 255, 0.45);
}

.resource-option {
  flex: 1 1 240px;
}

.resource-option span,
.switch-field span {
  display: grid;
  gap: 3px;
}

.resource-option small,
.switch-field small {
  color: var(--oj-muted);
  font-size: 0.72rem;
  font-weight: 600;
}

.resource-upload {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 12px;
}

.inline-error {
  grid-column: 1 / -1;
  margin: 0;
  color: #8f2d24;
  font-size: 0.82rem;
  font-weight: 700;
}

.testcase-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: rgba(255, 255, 255, 0.38);
}

.testcase-card > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.testcase-card > header button {
  border: 0;
  background: transparent;
  color: #8f2d24;
  cursor: pointer;
  font: inherit;
  font-size: 0.78rem;
  font-weight: 800;
}

.publish-check ul {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.publish-check li {
  padding: 11px 12px;
  border: 1px solid rgba(143, 45, 36, 0.18);
  border-radius: var(--oj-radius);
  color: #8f2d24;
  font-size: 0.82rem;
  font-weight: 750;
}

.publish-check li::before {
  margin-right: 8px;
  content: '○';
}

.publish-check li[data-ready='true'] {
  border-color: rgba(22, 101, 52, 0.22);
  color: #166534;
}

.publish-check li[data-ready='true']::before {
  content: '✓';
}

.editor-actions {
  position: sticky;
  bottom: 12px;
  z-index: 2;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 13px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  box-shadow: var(--oj-shadow-soft);
  backdrop-filter: var(--oj-blur);
  -webkit-backdrop-filter: var(--oj-blur);
}

.button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 8px 16px;
  border: 1px solid var(--oj-line-strong);
  border-radius: var(--oj-radius);
  background: var(--oj-surface-solid);
  color: var(--oj-brand);
  cursor: pointer;
  font: inherit;
  font-size: 0.84rem;
  font-weight: 800;
  text-decoration: none;
}

.button--primary {
  border-color: var(--oj-brand);
  background: var(--oj-brand);
  color: #fff;
}

.button:disabled {
  cursor: wait;
  opacity: 0.56;
}

.notice {
  margin: 0;
  padding: 13px 15px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  font-size: 0.86rem;
  font-weight: 700;
  line-height: 1.6;
}

.notice ul {
  margin: 6px 0 0;
  padding-left: 20px;
}

.notice--success {
  border-color: rgba(22, 101, 52, 0.24);
  color: #166534;
}

.notice--danger {
  border-color: rgba(143, 45, 36, 0.24);
  color: #8f2d24;
}

.lab-editor :where(a, button, input, select, textarea):focus-visible {
  outline: 3px solid var(--oj-brand);
  outline-offset: 2px;
}

@media (max-width: 640px) {
  .editor-section {
    padding: 17px;
  }

  .editor-section__header,
  .editor-section__header--action,
  .editor-grid,
  .resource-upload,
  .publish-check ul {
    grid-template-columns: minmax(0, 1fr);
  }

  .editor-section__header > span {
    width: 32px;
    height: 32px;
  }

  .field--wide {
    grid-column: auto;
  }

  .editor-actions {
    position: static;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
