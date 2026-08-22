<template>
  <main class="homework-editor" data-testid="homework-editor">
    <PageHeader
      :title="activeHomeworkId === undefined ? '创建作业' : '编辑作业草稿'"
      eyebrow="作业配置"
      subtitle="按基础信息、作业内容、提交规则和发布检查分阶段完成配置。"
    >
      <template #actions>
        <RouterLink class="button" :to="backRoute">返回作业管理</RouterLink>
      </template>
    </PageHeader>

    <PageState
      v-if="loading"
      state="loading"
      title="正在准备作业编辑器"
      message="正在同步作业基础信息、题目与测试用例。"
    />
    <PageState
      v-else-if="loadError"
      state="error"
      title="作业编辑器加载失败"
      :message="loadError"
      retry-label="重新加载"
      @retry="loadEditor"
    />
    <PageState
      v-else-if="editingBlocked"
      state="forbidden"
      title="只有草稿作业可以编辑"
      message="该作业已经进入发布流程。请返回作业管理查看提交、统计或生命周期状态。"
    >
      <template #actions>
        <RouterLink class="button button--primary" :to="backRoute">返回作业详情</RouterLink>
      </template>
    </PageState>

    <form
      v-else
      class="homework-editor__form"
      data-testid="homework-editor-form"
      novalidate
      @submit.prevent="saveDraft"
    >
      <p v-if="feedback" class="notice notice--success" role="status">{{ feedback }}</p>
      <p
        v-if="draftStatusMessage"
        class="notice notice--draft"
        data-testid="editor-draft-status"
        role="status"
      >
        {{ draftStatusMessage }}
      </p>
      <div
        v-if="validationErrors.length > 0"
        class="notice notice--danger"
        data-testid="editor-error"
        role="alert"
      >
        <strong>请完成以下必填项：</strong>
        <ul>
          <li v-for="message in validationErrors" :key="message">{{ message }}</li>
        </ul>
      </div>
      <p
        v-else-if="saveError"
        class="notice notice--danger"
        data-testid="editor-error"
        role="alert"
      >
        {{ saveError }}
      </p>

      <section class="editor-section" aria-labelledby="homework-basic-heading">
        <header class="editor-section__header">
          <span>01</span>
          <div>
            <h2 id="homework-basic-heading">基础信息</h2>
            <p>说明作业目标、截止时间、类型和评分上限。</p>
          </div>
        </header>
        <div class="editor-grid">
          <label class="field field--wide">
            <span>作业标题</span>
            <input v-model="form.title" name="title" type="text" autocomplete="off" />
          </label>
          <label class="field field--wide">
            <span>作业说明</span>
            <textarea v-model="form.description" name="description" rows="5" />
          </label>
          <label class="field">
            <span>作业类型</span>
            <select v-model="form.type" name="type">
              <option value="TEXT">文本作业</option>
              <option value="OBJECTIVE">客观题作业</option>
              <option value="FILE">附件作业</option>
              <option value="CODE">代码作业</option>
            </select>
          </label>
          <label class="field">
            <span>截止时间</span>
            <input v-model="form.deadline" name="deadline" type="datetime-local" />
          </label>
          <label class="field">
            <span>满分</span>
            <input v-model="form.totalScore" name="totalScore" type="number" min="1" step="1" />
          </label>
        </div>
      </section>

      <section class="editor-section" aria-labelledby="homework-content-heading">
        <header class="editor-section__header editor-section__header--action">
          <span>02</span>
          <div>
            <h2 id="homework-content-heading">作业内容</h2>
            <p>按照作业类型填写业务字段；页面不会要求手写 JSON。</p>
          </div>
          <button
            v-if="form.type === 'OBJECTIVE'"
            class="button"
            type="button"
            @click="addQuestion"
          >
            新增题目
          </button>
          <button
            v-else-if="form.type === 'CODE'"
            class="button"
            type="button"
            @click="addTestCase"
          >
            新增用例
          </button>
        </header>

        <div v-if="form.type === 'TEXT'" class="type-guidance">
          <strong>文本作业</strong>
          <p>学生将在提交页填写文本答案；具体要求请写在上方作业说明中。</p>
        </div>

        <div
          v-else-if="form.type === 'FILE'"
          class="type-guidance"
        >
          <strong>附件作业</strong>
          <p>学生通过受控上传通道提交单个附件；文件归属和下载权限由服务端校验。</p>
        </div>

        <div v-else-if="form.type === 'OBJECTIVE'" class="card-list">
          <article v-for="(question, questionIndex) in form.questions" :key="question.key" class="content-card">
            <header class="content-card__header">
              <h3>题目 {{ questionIndex + 1 }}</h3>
              <button
                v-if="form.questions.length > 1"
                type="button"
                @click="removeQuestion(questionIndex)"
              >
                移除
              </button>
            </header>
            <div class="editor-grid">
              <label class="field">
                <span>题型</span>
                <select
                  v-model="question.questionType"
                  :name="`question-type-${questionIndex}`"
                  @change="normalizeQuestionType(question)"
                >
                  <option value="SINGLE_CHOICE">单选题</option>
                  <option value="MULTIPLE_CHOICE">多选题</option>
                  <option value="TRUE_FALSE">判断题</option>
                </select>
              </label>
              <label class="field">
                <span>分值</span>
                <input
                  v-model="question.score"
                  :name="`question-score-${questionIndex}`"
                  type="number"
                  min="1"
                  step="1"
                />
              </label>
              <label class="field field--wide">
                <span>题干</span>
                <textarea v-model="question.stem" :name="`question-stem-${questionIndex}`" rows="3" />
              </label>
            </div>

            <fieldset class="option-editor">
              <legend>选项与正确答案</legend>
              <div v-for="(_option, optionIndex) in question.options" :key="optionIndex" class="option-row">
                <input
                  v-if="question.questionType === 'MULTIPLE_CHOICE'"
                  v-model="question.answerIndexes"
                  type="checkbox"
                  :value="optionIndex"
                  :data-testid="`question-answer-${questionIndex}-${optionIndex}`"
                  :aria-label="`将选项 ${optionIndex + 1} 设为正确答案`"
                />
                <input
                  v-else
                  type="radio"
                  :name="`question-answer-${questionIndex}`"
                  :checked="question.answerIndexes[0] === optionIndex"
                  :data-testid="`question-answer-${questionIndex}-${optionIndex}`"
                  :aria-label="`将选项 ${optionIndex + 1} 设为正确答案`"
                  @change="setSingleAnswer(question, optionIndex)"
                />
                <label class="field option-row__value">
                  <span>选项 {{ optionIndex + 1 }}</span>
                  <input
                    v-model="question.options[optionIndex]"
                    :name="`question-option-${questionIndex}-${optionIndex}`"
                    type="text"
                    :disabled="question.questionType === 'TRUE_FALSE'"
                  />
                </label>
                <button
                  v-if="question.questionType !== 'TRUE_FALSE' && question.options.length > 2"
                  type="button"
                  @click="removeOption(question, optionIndex)"
                >
                  删除选项
                </button>
              </div>
              <button
                v-if="question.questionType !== 'TRUE_FALSE'"
                class="button option-editor__add"
                type="button"
                @click="addOption(question)"
              >
                新增选项
              </button>
            </fieldset>
          </article>
        </div>

        <div v-else class="code-config">
          <fieldset class="choice-group">
            <legend>允许提交的语言</legend>
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

          <div
            v-if="unsupportedLanguages.length > 0"
            class="contract-notice unsupported-language-warning"
            data-testid="unsupported-language-warning"
            role="alert"
          >
            <strong>当前沙箱仅支持 Python</strong>
            <p>旧草稿包含无法评测的语言。保存前请显式移除；未处理时发布检查不会通过。</p>
            <ul>
              <li v-for="language in unsupportedLanguages" :key="language">
                <span>{{ languageLabel(language) }}</span>
                <button
                  type="button"
                  :data-testid="`remove-unsupported-language-${language}`"
                  @click="removeUnsupportedLanguage(language)"
                >显式移除</button>
              </li>
            </ul>
          </div>

          <div class="editor-grid">
            <label class="field">
              <span>默认时间限制（毫秒）</span>
              <input v-model="form.timeLimitMs" name="timeLimitMs" type="number" min="1" step="1" />
            </label>
            <label class="field">
              <span>默认内存限制（KB）</span>
              <input v-model="form.memoryLimitKb" name="memoryLimitKb" type="number" min="1" step="1" />
            </label>
          </div>

          <div class="type-guidance" data-testid="output-compare-notice" role="note">
            <strong>输出比对固定为忽略首尾空白</strong>
            <p>当前后端评测链统一执行 trim 比对；本页不提供尚未生效的比较模式选项。</p>
          </div>

          <div class="card-list">
            <article v-for="(testCase, testCaseIndex) in form.testCases" :key="testCase.key" class="content-card">
              <header class="content-card__header">
                <h3>测试用例 {{ testCaseIndex + 1 }}</h3>
                <button
                  v-if="form.testCases.length > 1"
                  type="button"
                  @click="removeTestCase(testCaseIndex)"
                >
                  移除
                </button>
              </header>
              <div class="editor-grid">
                <label class="field field--wide">
                  <span>标准输入</span>
                  <textarea
                    v-model="testCase.inputData"
                    :name="`testcase-input-${testCaseIndex}`"
                    rows="3"
                  />
                </label>
                <label class="field field--wide">
                  <span>期望输出</span>
                  <textarea
                    v-model="testCase.expectedOutput"
                    :name="`testcase-output-${testCaseIndex}`"
                    rows="3"
                  />
                </label>
                <label class="field">
                  <span>分值</span>
                  <input
                    v-model="testCase.scoreWeight"
                    :name="`testcase-weight-${testCaseIndex}`"
                    type="number"
                    min="0"
                    step="1"
                  />
                </label>
                <label class="field">
                  <span>时间限制（毫秒）</span>
                  <input
                    v-model="testCase.timeLimitMs"
                    :name="`testcase-time-${testCaseIndex}`"
                    type="number"
                    min="1"
                    step="1"
                  />
                </label>
                <label class="field">
                  <span>内存限制（KB）</span>
                  <input
                    v-model="testCase.memoryLimitKb"
                    :name="`testcase-memory-${testCaseIndex}`"
                    type="number"
                    min="1"
                    step="1"
                  />
                </label>
                <label class="switch-field">
                  <input v-model="testCase.hidden" :name="`testcase-hidden-${testCaseIndex}`" type="checkbox" />
                  <span><strong>隐藏用例</strong><small>不向学生公开输入与期望输出。</small></span>
                </label>
              </div>
            </article>
          </div>
        </div>
      </section>

      <section class="editor-section" aria-labelledby="homework-policy-heading">
        <header class="editor-section__header">
          <span>03</span>
          <div>
            <h2 id="homework-policy-heading">提交与反馈规则</h2>
            <p>明确补交、重复提交和评测结果的展示策略。</p>
          </div>
        </header>
        <div class="policy-grid">
          <label class="switch-field">
            <input v-model="form.allowResubmit" name="allowResubmit" type="checkbox" />
            <span><strong>允许多次提交</strong><small>最新有效版本参与最终成绩。</small></span>
          </label>
          <label class="switch-field">
            <input v-model="form.allowLateSubmit" name="allowLateSubmit" type="checkbox" />
            <span><strong>允许逾期提交</strong><small>逾期版本仍会保留迟交标识。</small></span>
          </label>
          <label class="switch-field">
            <input
              v-model="form.showEvaluationBeforePublish"
              name="showEvaluationBeforePublish"
              type="checkbox"
            />
            <span><strong>成绩发布前展示评测摘要</strong><small>最终分仍需教师发布后才对学生可见。</small></span>
          </label>
        </div>
      </section>

      <section class="editor-section publish-check" aria-labelledby="homework-publish-check-heading">
        <header class="editor-section__header">
          <span>04</span>
          <div>
            <h2 id="homework-publish-check-heading">发布检查</h2>
            <p>编辑器只保存草稿；发布动作在作业管理页完成，并再次要求确认。</p>
          </div>
        </header>
        <ul>
          <li :data-ready="Boolean(form.title.trim())">作业标题已填写</li>
          <li :data-ready="Boolean(form.description.trim())">作业说明已填写</li>
          <li :data-ready="deadlineIsFuture">截止时间晚于当前时间</li>
          <li data-testid="type-content-check" :data-ready="typeContentIsReady">
            {{ form.type === 'FILE'
              ? '附件提交契约已就绪'
              : '当前类型的题目或评测配置完整' }}
          </li>
        </ul>
      </section>

      <footer class="editor-actions">
        <RouterLink class="button" :to="backRoute">取消</RouterLink>
        <button
          class="button button--primary"
          data-testid="save-homework"
          type="submit"
          :disabled="saving"
        >
          {{ saving ? '保存中…' : activeHomeworkId === undefined ? '保存草稿' : '更新草稿' }}
        </button>
      </footer>
    </form>
  </main>
</template>

<script setup lang="ts">
import { computed, inject, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { matchedRouteKey, onBeforeRouteLeave, RouterLink, routerKey } from 'vue-router';
import { createHomework, getHomeworkDetail, updateHomework } from '../../api/hwk/homeworks';
import { currentUser } from '../../app/runtimeContext';
import PageHeader from '../../components/foundation/PageHeader.vue';
import PageState from '../../components/foundation/PageState.vue';
import type {
  HomeworkDetail,
  HomeworkPayload,
  HomeworkQuestionPayload,
  HomeworkTestCasePayload,
  HomeworkType
} from '../../types/hwk';

type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE';

interface QuestionDraft {
  key: number;
  questionType: QuestionType;
  stem: string;
  options: string[];
  optionKeys: string[] | null;
  answerIndexes: number[];
  score: string;
}

interface TestCaseDraft {
  key: number;
  inputData: string;
  expectedOutput: string;
  scoreWeight: string;
  hidden: boolean;
  timeLimitMs: string;
  memoryLimitKb: string;
}

interface EditorFormSnapshot {
  title: string;
  description: string;
  type: HomeworkType;
  deadline: string;
  totalScore: string;
  allowResubmit: boolean;
  allowLateSubmit: boolean;
  showEvaluationBeforePublish: boolean;
  timeLimitMs: string;
  memoryLimitKb: string;
  outputCompareMode: string;
  questions: Omit<QuestionDraft, 'key'>[];
  testCases: Omit<TestCaseDraft, 'key'>[];
}

interface LocalEditorDraft {
  version: 1;
  savedAt: number;
  courseId: number;
  homeworkId: number | null;
  sourceUpdatedAt: string | null;
  form: EditorFormSnapshot;
  selectedLanguages: string[];
}

const props = defineProps<{ courseId: number; homeworkId?: number }>();
const appRouter = inject(routerKey, null);
const matchedRoute = inject(matchedRouteKey, null);
const draftOwnerId = currentUser.value?.id ?? 'anonymous';
const activeHomeworkId = ref<number | undefined>(props.homeworkId);
const loadedDetail = ref<HomeworkDetail | null>(null);
const loading = ref(false);
const loadError = ref('');
const validationErrors = ref<string[]>([]);
const saveError = ref('');
const feedback = ref('');
const draftStatusMessage = ref('');
const saving = ref(false);
const selectedLanguages = ref<string[]>([]);
const savedState = ref('');
let editorGeneration = 0;
let questionKey = 1;
let testCaseKey = 1;
let draftTimer: ReturnType<typeof setTimeout> | undefined;
let draftWatchSuspended = true;
let editorContextReady = false;
let beforeUnloadRegistered = false;

const form = reactive<{
  title: string;
  description: string;
  type: HomeworkType;
  deadline: string;
  totalScore: string;
  allowResubmit: boolean;
  allowLateSubmit: boolean;
  showEvaluationBeforePublish: boolean;
  timeLimitMs: string;
  memoryLimitKb: string;
  outputCompareMode: string;
  questions: QuestionDraft[];
  testCases: TestCaseDraft[];
}>({
  title: '',
  description: '',
  type: 'TEXT',
  deadline: '',
  totalScore: '100',
  allowResubmit: true,
  allowLateSubmit: false,
  showEvaluationBeforePublish: true,
  timeLimitMs: '1000',
  memoryLimitKb: '65536',
  outputCompareMode: 'TRIM',
  questions: [],
  testCases: []
});

const languageOptions = [
  { value: 'python', label: 'Python' }
] as const;

const unsupportedLanguages = computed(() => [...new Set(
  selectedLanguages.value.filter((language) => language !== 'python')
)]);

const editingBlocked = computed(() => (
  activeHomeworkId.value !== undefined
  && loadedDetail.value !== null
  && loadedDetail.value.status !== 'DRAFT'
));
const backRoute = computed(() => activeHomeworkId.value === undefined
  ? { name: 'homework-manage', params: { courseId: props.courseId } }
  : { name: 'homework-manage-detail', params: { courseId: props.courseId, homeworkId: activeHomeworkId.value } });
const draftKey = computed(() => draftStorageKey(props.courseId, activeHomeworkId.value));
const hasUnsavedChanges = computed(() => (
  editorContextReady
  && serializeEditorState() !== savedState.value
));
const deadlineIsFuture = computed(() => {
  const timestamp = new Date(form.deadline).getTime();
  return Number.isFinite(timestamp) && timestamp > Date.now();
});
const typeContentIsReady = computed(() => {
  if (form.type === 'TEXT') return true;
  if (form.type === 'FILE') return true;
  if (form.type === 'OBJECTIVE') {
    return form.questions.length > 0
      && form.questions.every(questionIsReady)
      && scoreTotal(form.questions.map((question) => question.score)) === Number(form.totalScore);
  }
  return selectedLanguages.value.includes('python')
    && unsupportedLanguages.value.length === 0
    && form.testCases.length > 0
    && form.testCases.every(testCaseIsReady)
    && scoreTotal(form.testCases.map((testCase) => testCase.scoreWeight)) === Number(form.totalScore);
});

watch(
  () => [props.courseId, props.homeworkId] as const,
  (nextContext, previousContext) => {
    if (previousContext && editorContextReady) {
      saveLocalDraft(
        draftStorageKey(previousContext[0], previousContext[1]),
        previousContext[0],
        previousContext[1]
      );
    }
    cancelScheduledDraftSave();
    editorContextReady = false;
    activeHomeworkId.value = props.homeworkId;
    void loadEditor();
  },
  { immediate: true }
);

watch(
  [form, selectedLanguages],
  () => {
    if (draftWatchSuspended || !editorContextReady) return;
    if (!hasUnsavedChanges.value) {
      cancelScheduledDraftSave();
      clearLocalDraft();
      return;
    }
    scheduleDraftSave();
  },
  { deep: true, flush: 'sync' }
);

watch(
  () => form.type,
  (type) => {
    if (type === 'OBJECTIVE' && form.questions.length === 0) form.questions.push(emptyQuestion());
    if (type === 'CODE' && form.testCases.length === 0) form.testCases.push(emptyTestCase());
    validationErrors.value = [];
    saveError.value = '';
    feedback.value = '';
  },
  { flush: 'sync' }
);

onMounted(() => {
  if (matchedRoute) registerBeforeUnload();
});

onBeforeUnmount(() => {
  saveLocalDraft();
  unregisterBeforeUnload();
  cancelScheduledDraftSave();
  editorGeneration += 1;
});

if (matchedRoute) {
  onBeforeRouteLeave(() => {
    if (!hasUnsavedChanges.value) return true;
    const savedLocally = saveLocalDraft();
    return window.confirm(savedLocally
      ? '当前更改尚未保存到服务器，已保存在本机。确认离开编辑器吗？'
      : '当前更改尚未保存到服务器，且本机草稿保存失败。确认离开编辑器吗？');
  });
}

async function loadEditor() {
  const generation = ++editorGeneration;
  draftWatchSuspended = true;
  editorContextReady = false;
  saving.value = false;
  loadError.value = '';
  validationErrors.value = [];
  saveError.value = '';
  feedback.value = '';
  draftStatusMessage.value = '';
  if (props.homeworkId === undefined) {
    resetForm();
    loadedDetail.value = null;
    finishEditorHydration();
    loading.value = false;
    return;
  }
  loading.value = true;
  try {
    const detail = await getHomeworkDetail(props.homeworkId);
    if (generation !== editorGeneration) return;
    if (detail.courseId !== props.courseId || detail.id !== props.homeworkId) {
      throw new Error('作业与当前课程不匹配，请返回作业管理重新进入。');
    }
    loadedDetail.value = detail;
    hydrateForm(detail);
    finishEditorHydration();
  } catch (error) {
    if (generation !== editorGeneration) return;
    loadError.value = errorMessage(error, '作业编辑器加载失败，请稍后重试。');
  } finally {
    if (generation === editorGeneration) {
      loading.value = false;
      if (!editorContextReady) draftWatchSuspended = false;
    }
  }
}

function finishEditorHydration() {
  savedState.value = serializeEditorState();
  editorContextReady = true;
  restoreLocalDraft();
  draftWatchSuspended = false;
}

function resetForm() {
  form.title = '';
  form.description = '';
  form.type = 'TEXT';
  form.deadline = '';
  form.totalScore = '100';
  form.allowResubmit = true;
  form.allowLateSubmit = false;
  form.showEvaluationBeforePublish = true;
  form.timeLimitMs = '1000';
  form.memoryLimitKb = '65536';
  form.outputCompareMode = 'TRIM';
  form.questions = [];
  form.testCases = [];
  selectedLanguages.value = [];
}

function hydrateForm(detail: HomeworkDetail) {
  form.title = detail.title;
  form.description = detail.description;
  form.type = detail.type;
  form.deadline = detail.deadline.slice(0, 16);
  form.totalScore = String(detail.totalScore);
  form.allowResubmit = detail.allowResubmit;
  form.allowLateSubmit = detail.allowLateSubmit;
  form.showEvaluationBeforePublish = detail.showEvaluationBeforePublish;
  form.timeLimitMs = String(detail.timeLimitMs ?? 1000);
  form.memoryLimitKb = String(detail.memoryLimitKb ?? 65536);
  form.outputCompareMode = 'TRIM';
  form.questions = detail.questions.map((question) => {
    const configuredOptions = parseQuestionOptions(question.optionsJson);
    const questionType = normalizeQuestionTypeValue(question.questionType);
    const options = configuredOptions.options.length > 0
      ? configuredOptions.options
      : questionType === 'TRUE_FALSE'
        ? ['正确', '错误']
        : [];
    const answers = parseAnswerValues(question.answerJson);
    return {
      key: questionKey++,
      questionType,
      stem: question.stem,
      options: options.length > 0 ? options : ['', ''],
      optionKeys: configuredOptions.keys,
      answerIndexes: answers
        .map((answer) => (configuredOptions.keys ?? options).indexOf(answer))
        .filter((index) => index >= 0),
      score: String(question.score)
    };
  });
  form.testCases = detail.testCases.map((testCase) => ({
    key: testCaseKey++,
    inputData: testCase.inputData,
    expectedOutput: testCase.expectedOutput ?? '',
    scoreWeight: String(testCase.scoreWeight),
    hidden: testCase.hidden,
    timeLimitMs: String(testCase.timeLimitMs),
    memoryLimitKb: String(testCase.memoryLimitKb)
  }));
  selectedLanguages.value = parseLanguageLimits(detail.languageLimitJson);
  if (form.type === 'OBJECTIVE' && form.questions.length === 0) form.questions = [emptyQuestion()];
  if (form.type === 'CODE' && form.testCases.length === 0) form.testCases = [emptyTestCase()];
}

async function saveDraft() {
  if (saving.value) return;
  validationErrors.value = validateForm();
  saveError.value = '';
  feedback.value = '';
  if (validationErrors.value.length > 0) return;
  const generation = editorGeneration;
  const courseId = props.courseId;
  const homeworkId = props.homeworkId;
  const targetHomeworkId = activeHomeworkId.value;
  const localDraftKey = draftKey.value;
  saving.value = true;
  try {
    const result = targetHomeworkId === undefined
      ? await createHomework(buildPayload())
      : await updateHomework(targetHomeworkId, buildPayload());
    if (!saveContextIsCurrent(generation, courseId, homeworkId, targetHomeworkId)) return;
    loadedDetail.value = result;
    savedState.value = serializeEditorState();
    cancelScheduledDraftSave();
    clearLocalDraft(localDraftKey);
    draftStatusMessage.value = '';
    if (targetHomeworkId === undefined) {
      activeHomeworkId.value = result.id;
      clearLocalDraft(draftKey.value);
      if (appRouter) {
        await appRouter.replace({
          name: 'homework-edit',
          params: { courseId: props.courseId, homeworkId: result.id }
        }).catch(() => undefined);
      }
      feedback.value = '草稿已保存，可返回作业管理继续发布。';
    } else {
      feedback.value = '草稿已更新。';
    }
  } catch (error) {
    if (!saveContextIsCurrent(generation, courseId, homeworkId, targetHomeworkId)) return;
    saveError.value = errorMessage(error, '草稿保存失败，请检查内容后重试。');
  } finally {
    if (
      generation === editorGeneration
      && props.courseId === courseId
      && props.homeworkId === homeworkId
    ) {
      saving.value = false;
    }
  }
}

function saveContextIsCurrent(
  generation: number,
  courseId: number,
  homeworkId: number | undefined,
  activeId: number | undefined
) {
  return generation === editorGeneration
    && props.courseId === courseId
    && props.homeworkId === homeworkId
    && activeHomeworkId.value === activeId;
}

function scheduleDraftSave() {
  cancelScheduledDraftSave();
  draftStatusMessage.value = '更改将在本机自动保存';
  draftTimer = setTimeout(() => {
    saveLocalDraft();
    draftTimer = undefined;
  }, 500);
}

function saveLocalDraft(
  storageKey = draftKey.value,
  courseId = props.courseId,
  homeworkId = activeHomeworkId.value
) {
  if (!editorContextReady) return true;
  if (!hasUnsavedChanges.value) {
    clearLocalDraft(storageKey);
    draftStatusMessage.value = '';
    return true;
  }
  const draft: LocalEditorDraft = {
    version: 1,
    savedAt: Date.now(),
    courseId,
    homeworkId: homeworkId ?? null,
    sourceUpdatedAt: loadedDetail.value?.updatedAt ?? null,
    form: captureFormSnapshot(),
    selectedLanguages: [...selectedLanguages.value]
  };
  try {
    window.sessionStorage.setItem(storageKey, JSON.stringify(draft));
    draftStatusMessage.value = '未保存到服务器的更改已自动保存在本机';
    return true;
  } catch {
    draftStatusMessage.value = '本机恢复草稿暂时无法保存，请尽快保存到服务器';
    return false;
  }
}

function restoreLocalDraft() {
  let parsed: unknown;
  try {
    const stored = window.sessionStorage.getItem(draftKey.value);
    parsed = stored ? JSON.parse(stored) : undefined;
  } catch {
    clearLocalDraft();
    return;
  }
  if (parsed === undefined) return;
  if (!isLocalEditorDraft(parsed)
    || parsed.courseId !== props.courseId
    || parsed.homeworkId !== (activeHomeworkId.value ?? null)
    || Date.now() > parsed.savedAt + 24 * 60 * 60 * 1000) {
    clearLocalDraft();
    return;
  }
  if (parsed.sourceUpdatedAt !== (loadedDetail.value?.updatedAt ?? null)) {
    draftStatusMessage.value = '检测到旧的本机草稿，但服务器版本已更新，本次未自动覆盖';
    return;
  }
  applyFormSnapshot(parsed.form, parsed.selectedLanguages);
  if (serializeEditorState() === savedState.value) {
    clearLocalDraft();
    return;
  }
  draftStatusMessage.value = '已恢复 24 小时内未保存的编辑内容';
}

function captureFormSnapshot(): EditorFormSnapshot {
  return {
    title: form.title,
    description: form.description,
    type: form.type,
    deadline: form.deadline,
    totalScore: form.totalScore,
    allowResubmit: form.allowResubmit,
    allowLateSubmit: form.allowLateSubmit,
    showEvaluationBeforePublish: form.showEvaluationBeforePublish,
    timeLimitMs: form.timeLimitMs,
    memoryLimitKb: form.memoryLimitKb,
    outputCompareMode: form.outputCompareMode,
    questions: form.questions.map((question) => ({
      questionType: question.questionType,
      stem: question.stem,
      options: [...question.options],
      optionKeys: question.optionKeys ? [...question.optionKeys] : null,
      answerIndexes: [...question.answerIndexes],
      score: question.score
    })),
    testCases: form.testCases.map((testCase) => ({
      inputData: testCase.inputData,
      expectedOutput: testCase.expectedOutput,
      scoreWeight: testCase.scoreWeight,
      hidden: testCase.hidden,
      timeLimitMs: testCase.timeLimitMs,
      memoryLimitKb: testCase.memoryLimitKb
    }))
  };
}

function applyFormSnapshot(snapshot: EditorFormSnapshot, languages: string[]) {
  form.title = snapshot.title;
  form.description = snapshot.description;
  form.type = snapshot.type;
  form.deadline = snapshot.deadline;
  form.totalScore = snapshot.totalScore;
  form.allowResubmit = snapshot.allowResubmit;
  form.allowLateSubmit = snapshot.allowLateSubmit;
  form.showEvaluationBeforePublish = snapshot.showEvaluationBeforePublish;
  form.timeLimitMs = snapshot.timeLimitMs;
  form.memoryLimitKb = snapshot.memoryLimitKb;
  form.outputCompareMode = snapshot.outputCompareMode;
  form.questions = snapshot.questions.map((question) => ({
    key: questionKey++,
    questionType: question.questionType,
    stem: question.stem,
    options: [...question.options],
    optionKeys: question.optionKeys ? [...question.optionKeys] : null,
    answerIndexes: [...question.answerIndexes],
    score: question.score
  }));
  form.testCases = snapshot.testCases.map((testCase) => ({
    key: testCaseKey++,
    inputData: testCase.inputData,
    expectedOutput: testCase.expectedOutput,
    scoreWeight: testCase.scoreWeight,
    hidden: testCase.hidden,
    timeLimitMs: testCase.timeLimitMs,
    memoryLimitKb: testCase.memoryLimitKb
  }));
  selectedLanguages.value = [...languages];
}

function serializeEditorState() {
  return JSON.stringify({
    form: captureFormSnapshot(),
    selectedLanguages: [...selectedLanguages.value]
  });
}

function isLocalEditorDraft(value: unknown): value is LocalEditorDraft {
  if (!isRecord(value)
    || value.version !== 1
    || !Number.isFinite(value.savedAt)
    || !Number.isInteger(value.courseId)
    || !(value.homeworkId === null || Number.isInteger(value.homeworkId))
    || !(value.sourceUpdatedAt === null || typeof value.sourceUpdatedAt === 'string')
    || !Array.isArray(value.selectedLanguages)
    || !value.selectedLanguages.every((language) => typeof language === 'string')) {
    return false;
  }
  return isEditorFormSnapshot(value.form);
}

function isEditorFormSnapshot(value: unknown): value is EditorFormSnapshot {
  if (!isRecord(value)
    || typeof value.title !== 'string'
    || typeof value.description !== 'string'
    || !['TEXT', 'OBJECTIVE', 'FILE', 'CODE'].includes(String(value.type))
    || typeof value.deadline !== 'string'
    || typeof value.totalScore !== 'string'
    || typeof value.allowResubmit !== 'boolean'
    || typeof value.allowLateSubmit !== 'boolean'
    || typeof value.showEvaluationBeforePublish !== 'boolean'
    || typeof value.timeLimitMs !== 'string'
    || typeof value.memoryLimitKb !== 'string'
    || typeof value.outputCompareMode !== 'string'
    || !Array.isArray(value.questions)
    || !Array.isArray(value.testCases)) {
    return false;
  }
  return value.questions.every(isQuestionSnapshot)
    && value.testCases.every(isTestCaseSnapshot);
}

function isQuestionSnapshot(value: unknown): value is Omit<QuestionDraft, 'key'> {
  return isRecord(value)
    && ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'].includes(String(value.questionType))
    && typeof value.stem === 'string'
    && Array.isArray(value.options)
    && value.options.every((option) => typeof option === 'string')
    && (value.optionKeys === null
      || (Array.isArray(value.optionKeys) && value.optionKeys.every((key) => typeof key === 'string')))
    && Array.isArray(value.answerIndexes)
    && value.answerIndexes.every((index) => Number.isInteger(index))
    && typeof value.score === 'string';
}

function isTestCaseSnapshot(value: unknown): value is Omit<TestCaseDraft, 'key'> {
  return isRecord(value)
    && typeof value.inputData === 'string'
    && typeof value.expectedOutput === 'string'
    && typeof value.scoreWeight === 'string'
    && typeof value.hidden === 'boolean'
    && typeof value.timeLimitMs === 'string'
    && typeof value.memoryLimitKb === 'string';
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function clearLocalDraft(storageKey = draftKey.value) {
  try {
    window.sessionStorage.removeItem(storageKey);
  } catch {
    // Storage failures are surfaced on the next save attempt.
  }
}

function cancelScheduledDraftSave() {
  if (!draftTimer) return;
  clearTimeout(draftTimer);
  draftTimer = undefined;
}

function protectUnsavedDraft(event: BeforeUnloadEvent) {
  if (!hasUnsavedChanges.value) return;
  saveLocalDraft();
  event.preventDefault();
  event.returnValue = '';
}

function registerBeforeUnload() {
  if (beforeUnloadRegistered) return;
  window.addEventListener('beforeunload', protectUnsavedDraft);
  beforeUnloadRegistered = true;
}

function unregisterBeforeUnload() {
  if (!beforeUnloadRegistered) return;
  window.removeEventListener('beforeunload', protectUnsavedDraft);
  beforeUnloadRegistered = false;
}

function draftStorageKey(courseId: number, homeworkId?: number) {
  return [
    'oj:teacher-homework-draft:v1',
    draftOwnerId,
    courseId,
    homeworkId ?? 'new'
  ].join(':');
}

function validateForm() {
  const errors: string[] = [];
  if (!form.title.trim()) errors.push('作业标题不能为空');
  if (!form.description.trim()) errors.push('作业说明不能为空');
  if (!deadlineIsFuture.value) errors.push('截止时间必须晚于当前时间');
  if (!isPositiveInteger(form.totalScore)) errors.push('满分必须是正整数');
  if (form.type === 'OBJECTIVE') validateQuestions(errors);
  if (form.type === 'CODE') validateCodeConfiguration(errors);
  return [...new Set(errors)];
}

function validateQuestions(errors: string[]) {
  if (form.questions.length === 0) {
    errors.push('客观题至少需要一道题目');
    return;
  }
  form.questions.forEach((question, index) => {
    const label = `第 ${index + 1} 题`;
    if (!question.stem.trim()) errors.push(`${label}题干不能为空`);
    if (question.options.length < 2 || question.options.some((option) => !option.trim())) {
      errors.push(`${label}的选项不能为空`);
    }
    const normalizedOptions = question.options.map((option) => option.trim()).filter(Boolean);
    if (new Set(normalizedOptions).size !== normalizedOptions.length) errors.push(`${label}的选项不能重复`);
    if (question.answerIndexes.length === 0) errors.push(`${label}必须选择正确答案`);
    if (question.questionType !== 'MULTIPLE_CHOICE' && question.answerIndexes.length > 1) {
      errors.push(`${label}只能选择一个正确答案`);
    }
    if (!isPositiveInteger(question.score)) errors.push(`${label}分值必须是正整数`);
  });
  const total = scoreTotal(form.questions.map((question) => question.score));
  if (form.questions.length > 0 && Number.isFinite(Number(form.totalScore)) && total !== Number(form.totalScore)) {
    errors.push(`题目分值合计需等于满分（当前 ${total} 分）`);
  }
}

function validateCodeConfiguration(errors: string[]) {
  if (unsupportedLanguages.value.length > 0) {
    errors.push(`当前沙箱仅支持 Python，请先显式移除不受支持的语言：${unsupportedLanguages.value.map(languageLabel).join('、')}`);
  }
  if (!selectedLanguages.value.includes('python')) errors.push('代码作业至少选择当前沙箱支持的 Python 语言');
  if (!isPositiveInteger(form.timeLimitMs)) errors.push('默认时间限制必须是正整数');
  if (!isPositiveInteger(form.memoryLimitKb)) errors.push('默认内存限制必须是正整数');
  if (form.testCases.length === 0) {
    errors.push('代码作业至少需要一个测试用例');
    return;
  }
  form.testCases.forEach((testCase, index) => {
    const label = `测试用例 ${index + 1}`;
    if (!isNonNegativeInteger(testCase.scoreWeight)) errors.push(`${label}的分值必须是非负整数`);
    if (!isPositiveInteger(testCase.timeLimitMs)) errors.push(`${label}的时间限制必须是正整数`);
    if (!isPositiveInteger(testCase.memoryLimitKb)) errors.push(`${label}的内存限制必须是正整数`);
  });
  const total = scoreTotal(form.testCases.map((testCase) => testCase.scoreWeight));
  if (form.testCases.length > 0 && Number.isFinite(Number(form.totalScore)) && total !== Number(form.totalScore)) {
    errors.push(`测试用例分值合计需等于满分（当前 ${total} 分）`);
  }
}

function buildPayload(): HomeworkPayload {
  return {
    courseId: props.courseId,
    chapterId: loadedDetail.value?.chapterId ?? null,
    title: form.title.trim(),
    description: form.description.trim(),
    type: form.type,
    deadline: form.deadline.length === 16 ? `${form.deadline}:00` : form.deadline,
    totalScore: Number(form.totalScore),
    allowResubmit: form.allowResubmit,
    allowLateSubmit: form.allowLateSubmit,
    showEvaluationBeforePublish: form.showEvaluationBeforePublish,
    questions: form.type === 'OBJECTIVE' ? form.questions.map(toQuestionPayload) : [],
    testCases: form.type === 'CODE' ? form.testCases.map(toTestCasePayload) : [],
    languageLimitJson: form.type === 'CODE' ? JSON.stringify(['python']) : null,
    timeLimitMs: form.type === 'CODE' ? Number(form.timeLimitMs) : 1000,
    memoryLimitKb: form.type === 'CODE' ? Number(form.memoryLimitKb) : 65536,
    outputCompareMode: form.type === 'CODE' ? 'TRIM' : 'EXACT'
  };
}

function toQuestionPayload(question: QuestionDraft, index: number): HomeworkQuestionPayload {
  const options = question.options.map((option) => option.trim());
  const answerSource = question.optionKeys ?? options;
  return {
    questionType: question.questionType,
    stem: question.stem.trim(),
    optionsJson: question.optionKeys
      ? JSON.stringify(Object.fromEntries(question.optionKeys.map((key, optionIndex) => [key, options[optionIndex]])))
      : JSON.stringify(options),
    answerJson: JSON.stringify(
      [...question.answerIndexes]
        .sort((left, right) => left - right)
        .map((answerIndex) => answerSource[answerIndex])
    ),
    score: Number(question.score),
    sortOrder: index + 1
  };
}

function toTestCasePayload(testCase: TestCaseDraft, index: number): HomeworkTestCasePayload {
  return {
    inputData: testCase.inputData,
    expectedOutput: testCase.expectedOutput,
    scoreWeight: Number(testCase.scoreWeight),
    hidden: testCase.hidden,
    timeLimitMs: Number(testCase.timeLimitMs),
    memoryLimitKb: Number(testCase.memoryLimitKb),
    sortOrder: index + 1
  };
}

function addQuestion() {
  form.questions.push(emptyQuestion());
}

function removeQuestion(index: number) {
  form.questions.splice(index, 1);
}

function emptyQuestion(): QuestionDraft {
  return {
    key: questionKey++,
    questionType: 'SINGLE_CHOICE',
    stem: '',
    options: ['', ''],
    optionKeys: null,
    answerIndexes: [],
    score: '100'
  };
}

function normalizeQuestionType(question: QuestionDraft) {
  question.answerIndexes = [];
  question.optionKeys = null;
  if (question.questionType === 'TRUE_FALSE') question.options = ['正确', '错误'];
  else if (question.options.length < 2 || question.options.join('') === '正确错误') question.options = ['', ''];
}

function setSingleAnswer(question: QuestionDraft, optionIndex: number) {
  question.answerIndexes = [optionIndex];
}

function addOption(question: QuestionDraft) {
  question.options.push('');
  if (question.optionKeys) question.optionKeys.push(nextOptionKey(question.optionKeys));
}

function removeOption(question: QuestionDraft, optionIndex: number) {
  question.options.splice(optionIndex, 1);
  question.optionKeys?.splice(optionIndex, 1);
  question.answerIndexes = question.answerIndexes
    .filter((answerIndex) => answerIndex !== optionIndex)
    .map((answerIndex) => answerIndex > optionIndex ? answerIndex - 1 : answerIndex);
}

function addTestCase() {
  form.testCases.push(emptyTestCase());
}

function removeTestCase(index: number) {
  form.testCases.splice(index, 1);
}

function removeUnsupportedLanguage(language: string) {
  selectedLanguages.value = selectedLanguages.value.filter((candidate) => candidate !== language);
}

function emptyTestCase(): TestCaseDraft {
  return {
    key: testCaseKey++,
    inputData: '',
    expectedOutput: '',
    scoreWeight: '100',
    hidden: false,
    timeLimitMs: form?.timeLimitMs ?? '1000',
    memoryLimitKb: form?.memoryLimitKb ?? '65536'
  };
}

function questionIsReady(question: QuestionDraft) {
  return Boolean(question.stem.trim())
    && question.options.length >= 2
    && question.options.every((option) => Boolean(option.trim()))
    && question.answerIndexes.length > 0
    && isPositiveInteger(question.score);
}

function testCaseIsReady(testCase: TestCaseDraft) {
  return isNonNegativeInteger(testCase.scoreWeight)
    && isPositiveInteger(testCase.timeLimitMs)
    && isPositiveInteger(testCase.memoryLimitKb);
}

function parseStringArray(value: string | null | undefined) {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : [];
  } catch {
    return [];
  }
}

function parseQuestionOptions(value: string | null | undefined): { options: string[]; keys: string[] | null } {
  if (!value) return { options: [], keys: null };
  try {
    const parsed: unknown = JSON.parse(value);
    if (Array.isArray(parsed)) {
      return {
        options: parsed.map((item) => String(item)),
        keys: null
      };
    }
    if (parsed && typeof parsed === 'object') {
      const entries = Object.entries(parsed);
      return {
        options: entries.map(([, item]) => String(item)),
        keys: entries.map(([key]) => key)
      };
    }
  } catch {
    return { options: [], keys: null };
  }
  return { options: [], keys: null };
}

function parseAnswerValues(value: string | null | undefined) {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    if (Array.isArray(parsed)) return parsed.map((item) => String(item));
    if (typeof parsed === 'string' || typeof parsed === 'number' || typeof parsed === 'boolean') {
      return [String(parsed)];
    }
  } catch {
    return [];
  }
  return [];
}

function nextOptionKey(keys: string[]) {
  const used = new Set(keys);
  for (let code = 65; code <= 90; code += 1) {
    const candidate = String.fromCharCode(code);
    if (!used.has(candidate)) return candidate;
  }
  return `OPTION_${keys.length + 1}`;
}

function parseLanguageLimits(value: string | null | undefined) {
  const jsonLanguages = parseStringArray(value);
  if (jsonLanguages.length > 0) return jsonLanguages;
  return value?.split(',').map((language) => language.trim()).filter(Boolean) ?? [];
}

function normalizeQuestionTypeValue(value: string): QuestionType {
  if (value === 'MULTIPLE_CHOICE') return value;
  if (value === 'JUDGE' || value === 'TRUE_FALSE') return 'TRUE_FALSE';
  return 'SINGLE_CHOICE';
}

function languageLabel(value: string) {
  return ({
    python: 'Python',
    java: 'Java',
    cpp: 'C++',
    javascript: 'JavaScript'
  } as Record<string, string>)[value] ?? value;
}

function isPositiveInteger(value: string) {
  return Number.isInteger(Number(value)) && Number(value) > 0;
}

function isNonNegativeInteger(value: string) {
  return Number.isInteger(Number(value)) && Number(value) >= 0;
}

function scoreTotal(values: string[]) {
  return values.reduce((total, value) => total + (Number.isFinite(Number(value)) ? Number(value) : 0), 0);
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message.trim() ? error.message : fallback;
}
</script>

<style scoped>
.homework-editor {
  display: grid;
  gap: 18px;
  width: 100%;
  min-width: 0;
  padding-bottom: 42px;
  color: var(--oj-ink);
}

.homework-editor__form,
.card-list,
.code-config {
  display: grid;
  gap: 18px;
  min-width: 0;
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
.editor-section p,
.content-card h3,
.content-card p,
.type-guidance p,
.contract-notice p {
  margin: 0;
}

.editor-section h2 {
  font-size: 1.05rem;
}

.editor-section__header p,
.type-guidance p,
.contract-notice p {
  margin-top: 5px;
  color: var(--oj-muted);
  font-size: 0.83rem;
  line-height: 1.55;
}

.editor-grid,
.policy-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  min-width: 0;
}

.policy-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
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
  box-sizing: border-box;
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

.type-guidance,
.contract-notice {
  padding: 16px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: rgba(255, 255, 255, 0.45);
}

.contract-notice {
  border-color: rgba(164, 112, 34, 0.3);
  background: rgba(255, 247, 228, 0.72);
}

.content-card {
  display: grid;
  gap: 16px;
  min-width: 0;
  padding: 18px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: rgba(255, 255, 255, 0.38);
}

.content-card__header,
.editor-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.content-card__header button,
.option-row > button {
  border: 0;
  background: transparent;
  color: #8f2d24;
  cursor: pointer;
  font-weight: 800;
}

.option-editor,
.choice-group {
  display: grid;
  gap: 12px;
  min-width: 0;
  padding: 0;
  border: 0;
}

.option-editor legend,
.choice-group legend {
  margin-bottom: 4px;
  color: var(--oj-ink);
  font-size: 0.86rem;
  font-weight: 800;
}

.option-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: end;
  gap: 10px;
  min-width: 0;
}

.option-row > input {
  align-self: center;
}

.option-editor__add {
  justify-self: start;
}

.choice-group {
  display: flex;
  flex-wrap: wrap;
}

.choice-group legend {
  width: 100%;
}

.choice-card,
.switch-field {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 11px 12px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: rgba(255, 255, 255, 0.45);
}

.choice-card span,
.switch-field span {
  display: grid;
  gap: 3px;
}

.switch-field small {
  color: var(--oj-muted);
  font-weight: 600;
  line-height: 1.45;
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
  border-radius: var(--oj-radius);
  background: rgba(255, 255, 255, 0.45);
  color: var(--oj-muted);
  font-size: 0.84rem;
  font-weight: 700;
}

.publish-check li[data-ready='true'] {
  background: rgba(27, 123, 91, 0.1);
  color: #176147;
}

.editor-actions {
  justify-content: flex-end;
  padding: 4px 0 0;
}

.notice {
  margin: 0;
  padding: 13px 15px;
  border-radius: var(--oj-radius);
  line-height: 1.55;
}

.notice ul {
  margin: 8px 0 0;
  padding-left: 20px;
}

.notice--success {
  background: rgba(27, 123, 91, 0.12);
  color: #176147;
}

.notice--draft {
  border: 1px solid rgba(31, 91, 148, 0.2);
  background: rgba(31, 91, 148, 0.09);
  color: #245781;
}

.notice--danger {
  background: rgba(190, 49, 49, 0.1);
  color: #8f2d24;
}

.button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 8px 14px;
  border: 1px solid var(--oj-line-strong);
  border-radius: var(--oj-radius);
  background: var(--oj-surface-solid);
  color: var(--oj-ink);
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
  opacity: 0.65;
}

@media (max-width: 760px) {
  .editor-section,
  .content-card {
    padding: 18px;
  }

  .editor-section__header--action {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .editor-section__header--action > .button {
    grid-column: 1 / -1;
    width: 100%;
  }

  .editor-grid,
  .policy-grid,
  .publish-check ul {
    grid-template-columns: minmax(0, 1fr);
  }

  .option-row {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .option-row > button {
    grid-column: 2;
    justify-self: start;
  }
}

@media (max-width: 420px) {
  .editor-section,
  .content-card {
    padding: 15px;
  }

  .editor-section__header {
    gap: 10px;
  }

  .editor-actions > * {
    flex: 1 1 100%;
    min-width: 0;
  }
}
</style>
