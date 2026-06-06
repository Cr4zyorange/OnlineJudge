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
              <button type="button" @click="openSubmissionPanel(lab.id, lab.title)">查看提交</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="labs__submissions" aria-label="提交版本查看">
      <header class="labs__submissions-header">
        <div>
          <h2>提交版本查看</h2>
          <p v-if="selectedSubmissionLabId === null">请选择实验查看提交</p>
          <p v-else>当前实验：{{ selectedSubmissionLabTitle }}</p>
        </div>
      </header>

      <div v-if="selectedSubmissionLabId !== null" class="labs__submission-layout">
        <form class="labs__submission-filters" @submit.prevent="searchSubmissions">
          <label>
            <span>学生 ID</span>
            <input v-model="submissionFilters.studentId" name="studentId" type="number" min="1" />
          </label>
          <label>
            <span>提交状态</span>
            <select v-model="submissionFilters.submitStatus" name="submitStatus">
              <option value="">全部</option>
              <option value="SUBMITTED">SUBMITTED</option>
              <option value="LATE">LATE</option>
              <option value="WITHDRAWN">WITHDRAWN</option>
            </select>
          </label>
          <label>
            <span>评测状态</span>
            <select v-model="submissionFilters.evaluationStatus" name="evaluationStatus">
              <option value="">全部</option>
              <option value="NONE">NONE</option>
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
            <span>逾期提交</span>
            <select v-model="submissionFilters.overdue" name="overdue">
              <option value="">全部</option>
              <option value="true">是</option>
              <option value="false">否</option>
            </select>
          </label>
          <button data-action="search-submissions" type="button" @click="searchSubmissions">查询提交</button>
        </form>

        <div class="labs__submission-results">
          <p v-if="submissionLoading">加载中</p>
          <p v-else-if="submissionErrorMessage" class="labs__error">{{ submissionErrorMessage }}</p>
          <p v-else-if="submissions.length === 0">暂无提交记录</p>
          <table v-else>
            <thead>
              <tr>
                <th>学生</th>
                <th>版本</th>
                <th>版本标识</th>
                <th>提交状态</th>
                <th>评测状态</th>
                <th>最终得分</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="submission in submissions"
                :key="submission.submissionId"
                :data-submission-id="submission.submissionId"
              >
                <td>{{ submission.studentId }}</td>
                <td>{{ submission.version }}</td>
                <td>
                  <div class="labs__submission-flags">
                    <span
                      v-for="flag in getSubmissionFlags(submission)"
                      :key="`${submission.submissionId}-${flag}`"
                      class="labs__submission-flag"
                    >
                      {{ flag }}
                    </span>
                    <span v-if="getSubmissionFlags(submission).length === 0">无</span>
                  </div>
                </td>
                <td>{{ submission.submitStatus }}</td>
                <td>{{ submission.evaluationStatus }}</td>
                <td>{{ submission.finalScore ?? '未生成' }}</td>
                <td>
                  <button type="button" @click="openSubmissionDetail(submission.submissionId)">查看详情</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <aside class="labs__submission-detail">
          <p v-if="submissionDetailLoading">详情加载中</p>
          <p v-else-if="submissionDetailErrorMessage" class="labs__error">{{ submissionDetailErrorMessage }}</p>
          <p v-else-if="submissionDetail === null">请选择一个提交版本查看详情</p>
          <template v-else>
            <p v-if="submissionDetailFeedbackMessage" class="labs__feedback">{{ submissionDetailFeedbackMessage }}</p>
            <h3>提交详情</h3>
            <p>学生 ID：{{ submissionDetail.studentId }}</p>
            <p>版本：{{ submissionDetail.version }}</p>
            <div class="labs__submission-flags">
              <span
                v-for="flag in getSubmissionFlags(submissionDetail)"
                :key="`detail-${submissionDetail.submissionId}-${flag}`"
                class="labs__submission-flag"
              >
                {{ flag }}
              </span>
              <span v-if="getSubmissionFlags(submissionDetail).length === 0">无版本标识</span>
            </div>
            <p>文件标识：{{ submissionDetail.fileId ?? '无' }}</p>
            <section class="labs__submission-score" aria-label="教师评分">
              <h4>教师评分</h4>
              <p>自动得分：{{ formatScore(submissionDetail.latestScore?.autoScore ?? submissionDetail.autoScore) }}</p>
              <p>人工评分：{{ formatScore(submissionDetail.latestScore?.manualScore) }}</p>
              <p>报告评分：{{ formatScore(submissionDetail.latestScore?.reportScore ?? submissionDetail.latestReport?.score) }}</p>
              <p>最终得分：{{ formatScore(submissionDetail.latestScore?.finalScore ?? submissionDetail.finalScore) }}</p>
              <p>教师评语：{{ submissionDetail.latestScore?.comment ?? '暂无评语' }}</p>
              <p>评分留痕：{{ submissionDetail.latestScore?.hasChangeLogs ? '已记录' : '暂无' }}</p>
              <form class="labs__submission-score-form" @submit.prevent="saveSubmissionScore">
                <label>
                  <span>人工评分</span>
                  <input v-model="submissionScoreForm.manualScore" name="manualScore" type="number" min="0" />
                </label>
                <label>
                  <span>报告评分</span>
                  <input
                    v-model="submissionScoreForm.reportScore"
                    name="submissionReportScore"
                    type="number"
                    min="0"
                  />
                </label>
                <label>
                  <span>最终得分</span>
                  <input v-model="submissionScoreForm.finalScore" name="finalScore" type="number" min="0" />
                </label>
                <label class="labs__wide">
                  <span>教师评语</span>
                  <textarea v-model="submissionScoreForm.comment" name="scoreComment" rows="3" />
                </label>
                <label class="labs__wide">
                  <span>修改原因</span>
                  <textarea
                    v-model="submissionScoreForm.changeReason"
                    name="changeReason"
                    rows="2"
                    placeholder="修改已评分记录时必须填写"
                  />
                </label>
                <button
                  data-action="score-submission"
                  type="button"
                  :disabled="submissionScoreSaving"
                  @click="saveSubmissionScore"
                >
                  保存提交评分
                </button>
              </form>
            </section>
            <template v-if="submissionDetail.latestReport">
              <div class="labs__report-detail">
                <p>报告版本：{{ submissionDetail.latestReport.version }}</p>
                <p>报告文件：{{ submissionDetail.latestReport.fileName }}</p>
                <p>报告类型：{{ submissionDetail.latestReport.fileType }}</p>
                <p>报告评分：{{ submissionDetail.latestReport.score ?? '未评分' }}</p>
                <p>报告评语：{{ submissionDetail.latestReport.comment ?? '暂无评语' }}</p>
                <button type="button" @click="downloadSubmissionReport">下载报告</button>
                <form class="labs__report-score-form" @submit.prevent="saveReportScore">
                  <label>
                    <span>报告评分</span>
                    <input v-model="reportScoreForm.score" name="reportScore" type="number" min="0" />
                  </label>
                  <label>
                    <span>报告评语</span>
                    <textarea v-model="reportScoreForm.comment" name="reportComment" rows="3" />
                  </label>
                  <button
                    data-action="score-report"
                    type="button"
                    :disabled="reportScoreSaving"
                    @click="saveReportScore"
                  >
                    保存报告评分
                  </button>
                </form>
              </div>
            </template>
            <p v-else>暂无实验报告</p>
            <pre class="labs__submission-code">{{ submissionDetail.code || '本次提交未包含在线代码' }}</pre>
          </template>
        </aside>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import {
  closeLab,
  createLab,
  deleteLab,
  downloadLabReport,
  getLabSubmissionDetail,
  getLabDetail,
  listLabSubmissions,
  listLabs,
  publishLab,
  scoreLabSubmission,
  scoreLabReport,
  updateLab
} from '../../api/lab/labs';
import type {
  LabSubmissionDetail,
  LabSubmissionHistoryItem,
  LabSubmissionListFilters,
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
const selectedSubmissionLabId = ref<number | null>(null);
const selectedSubmissionLabTitle = ref('');
const submissions = ref<LabSubmissionHistoryItem[]>([]);
const submissionLoading = ref(false);
const submissionErrorMessage = ref('');
const submissionDetail = ref<LabSubmissionDetail | null>(null);
const submissionDetailLoading = ref(false);
const submissionDetailErrorMessage = ref('');
const submissionDetailFeedbackMessage = ref('');
const reportScoreSaving = ref(false);
const submissionScoreSaving = ref(false);
const submissionFilters = reactive({
  studentId: '',
  submitStatus: '',
  evaluationStatus: '',
  overdue: ''
});
const reportScoreForm = reactive({
  score: '',
  comment: ''
});
const submissionScoreForm = reactive({
  manualScore: '',
  reportScore: '',
  finalScore: '',
  comment: '',
  changeReason: ''
});

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

async function openSubmissionPanel(labId: number, title: string) {
  selectedSubmissionLabId.value = labId;
  selectedSubmissionLabTitle.value = title;
  submissionDetail.value = null;
  submissionDetailErrorMessage.value = '';
  submissionDetailFeedbackMessage.value = '';
  await loadSubmissions();
}

async function searchSubmissions() {
  await loadSubmissions();
}

async function loadSubmissions() {
  if (selectedSubmissionLabId.value === null) {
    return;
  }
  submissionLoading.value = true;
  submissionErrorMessage.value = '';
  try {
    submissions.value = await listLabSubmissions(selectedSubmissionLabId.value, buildSubmissionFilters());
  } catch (error) {
    submissionErrorMessage.value = error instanceof Error ? error.message : '提交列表加载失败';
  } finally {
    submissionLoading.value = false;
  }
}

async function openSubmissionDetail(submissionId: number) {
  if (selectedSubmissionLabId.value === null) {
    return;
  }
  submissionDetailLoading.value = true;
  submissionDetailErrorMessage.value = '';
  submissionDetailFeedbackMessage.value = '';
  try {
    submissionDetail.value = await getLabSubmissionDetail(selectedSubmissionLabId.value, submissionId);
    syncReportScoreForm();
    syncSubmissionScoreForm();
  } catch (error) {
    submissionDetailErrorMessage.value = error instanceof Error ? error.message : '提交详情加载失败';
  } finally {
    submissionDetailLoading.value = false;
  }
}

async function saveReportScore() {
  if (selectedSubmissionLabId.value === null || !submissionDetail.value?.latestReport) {
    return;
  }
  const score = Number(reportScoreForm.score);
  if (!Number.isFinite(score) || score < 0) {
    submissionDetailErrorMessage.value = '报告评分不能为负数';
    return;
  }

  reportScoreSaving.value = true;
  submissionDetailErrorMessage.value = '';
  submissionDetailFeedbackMessage.value = '';
  try {
    const updatedReport = await scoreLabReport(
      selectedSubmissionLabId.value,
      submissionDetail.value.latestReport.reportId,
      {
        score,
        comment: reportScoreForm.comment.trim()
      }
    );
    submissionDetail.value = {
      ...submissionDetail.value,
      latestReport: updatedReport
    };
    syncReportScoreForm();
    syncSubmissionScoreForm();
    submissionDetailFeedbackMessage.value = '报告评分已保存';
  } catch (error) {
    submissionDetailErrorMessage.value = error instanceof Error ? error.message : '报告评分保存失败';
  } finally {
    reportScoreSaving.value = false;
  }
}

async function saveSubmissionScore() {
  if (selectedSubmissionLabId.value === null || submissionDetail.value === null) {
    return;
  }

  const currentDetail = submissionDetail.value;
  let manualScore: number;
  let reportScore: number | null;
  let finalScore: number;
  try {
    manualScore = parseScoreInput(submissionScoreForm.manualScore, '人工评分');
    reportScore = parseOptionalScoreInput(submissionScoreForm.reportScore, '报告评分');
    finalScore = parseScoreInput(submissionScoreForm.finalScore, '最终得分');
  } catch (error) {
    submissionDetailErrorMessage.value = error instanceof Error ? error.message : '提交评分保存失败';
    submissionDetailFeedbackMessage.value = '';
    return;
  }
  const comment = normalizeText(submissionScoreForm.comment);
  const changeReason = normalizeText(submissionScoreForm.changeReason);
  const existingScore = currentDetail.latestScore;
  const changed = existingScore === undefined || existingScore === null
    ? true
    : manualScore !== existingScore.manualScore
      || reportScore !== existingScore.reportScore
      || finalScore !== existingScore.finalScore
      || comment !== existingScore.comment;

  if (existingScore && changed && !changeReason) {
    submissionDetailErrorMessage.value = '修改已评分记录时必须填写修改原因';
    return;
  }

  submissionScoreSaving.value = true;
  submissionDetailErrorMessage.value = '';
  submissionDetailFeedbackMessage.value = '';
  try {
    const updatedScore = await scoreLabSubmission(selectedSubmissionLabId.value, currentDetail.submissionId, {
      manualScore,
      reportScore,
      finalScore,
      comment,
      changeReason
    });
    submissionDetail.value = {
      ...currentDetail,
      finalScore: updatedScore.finalScore,
      latestReport: updateReportScoreSummary(currentDetail.latestReport, updatedScore.reportScore),
      latestScore: updatedScore
    };
    submissions.value = submissions.value.map((item) => item.submissionId === currentDetail.submissionId
      ? {
          ...item,
          finalScore: updatedScore.finalScore,
          autoScore: updatedScore.autoScore
        }
      : item);
    syncReportScoreForm();
    syncSubmissionScoreForm();
    submissionDetailFeedbackMessage.value = '提交评分已保存';
  } catch (error) {
    submissionDetailErrorMessage.value = error instanceof Error ? error.message : '提交评分保存失败';
  } finally {
    submissionScoreSaving.value = false;
  }
}

async function downloadSubmissionReport() {
  if (selectedSubmissionLabId.value === null || !submissionDetail.value?.latestReport) {
    return;
  }
  submissionDetailErrorMessage.value = '';
  try {
    const { blob, filename } = await downloadLabReport(
      selectedSubmissionLabId.value,
      submissionDetail.value.latestReport.reportId
    );
    const objectUrl = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = filename || submissionDetail.value.latestReport.fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(objectUrl);
  } catch (error) {
    submissionDetailErrorMessage.value = error instanceof Error ? error.message : '实验报告下载失败';
  }
}

function syncReportScoreForm() {
  const latestReport = submissionDetail.value?.latestReport;
  reportScoreForm.score = latestReport?.score == null ? '' : String(latestReport.score);
  reportScoreForm.comment = latestReport?.comment ?? '';
}

function syncSubmissionScoreForm() {
  const latestScore = submissionDetail.value?.latestScore;
  const latestReport = submissionDetail.value?.latestReport;
  submissionScoreForm.manualScore = latestScore?.manualScore == null ? '' : String(latestScore.manualScore);
  submissionScoreForm.reportScore = latestScore?.reportScore != null
    ? String(latestScore.reportScore)
    : latestReport?.score == null
      ? ''
      : String(latestReport.score);
  submissionScoreForm.finalScore = latestScore?.finalScore != null
    ? String(latestScore.finalScore)
    : submissionDetail.value?.finalScore == null
      ? ''
      : String(submissionDetail.value.finalScore);
  submissionScoreForm.comment = latestScore?.comment ?? '';
  submissionScoreForm.changeReason = '';
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

function buildSubmissionFilters(): LabSubmissionListFilters {
  const filters: LabSubmissionListFilters = {};
  const studentId = Number(submissionFilters.studentId);
  if (Number.isInteger(studentId) && studentId > 0) {
    filters.studentId = studentId;
  }
  if (submissionFilters.submitStatus) {
    filters.submitStatus = submissionFilters.submitStatus as LabSubmissionListFilters['submitStatus'];
  }
  if (submissionFilters.evaluationStatus) {
    filters.evaluationStatus = submissionFilters.evaluationStatus as LabSubmissionListFilters['evaluationStatus'];
  }
  if (submissionFilters.overdue === 'true') {
    filters.overdue = true;
  }
  if (submissionFilters.overdue === 'false') {
    filters.overdue = false;
  }
  return filters;
}

function getSubmissionFlags(submission: Pick<LabSubmissionHistoryItem, 'isLatest' | 'isFinal' | 'isScoringBasis' | 'hasFile'>) {
  const flags: string[] = [];
  if (submission.isLatest) {
    flags.push('最新版本');
  }
  if (submission.isFinal) {
    flags.push('当前有效版本');
  }
  if (submission.isScoringBasis) {
    flags.push('当前评分依据');
  }
  if (submission.hasFile) {
    flags.push('包含文件');
  }
  return flags;
}

function parseScoreInput(value: string, label: string) {
  const normalized = String(value ?? '').trim();
  if (!normalized) {
    throw new Error(`${label}不能为空`);
  }
  const score = Number(normalized);
  if (!Number.isFinite(score) || score < 0) {
    throw new Error(`${label}不能为负数`);
  }
  return score;
}

function parseOptionalScoreInput(value: string, label: string) {
  const normalized = String(value ?? '').trim();
  if (!normalized) {
    return null;
  }
  const score = Number(normalized);
  if (!Number.isFinite(score) || score < 0) {
    throw new Error(`${label}不能为负数`);
  }
  return score;
}

function normalizeText(value: string) {
  const normalized = value.trim();
  return normalized ? normalized : null;
}

function updateReportScoreSummary(report: LabSubmissionDetail['latestReport'], reportScore: number | null) {
  if (!report || reportScore === null) {
    return report;
  }
  return {
    ...report,
    score: reportScore
  };
}

function formatScore(value: number | null | undefined) {
  return value ?? '未生成';
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
.labs__list,
.labs__submissions {
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
.labs__checkbox,
.labs__submissions-header {
  align-items: center;
  display: flex;
  gap: 8px;
}

.labs__testcases-header {
  justify-content: space-between;
}

.labs__submissions-header {
  justify-content: space-between;
}

.labs__submission-layout {
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
  margin-top: 16px;
}

.labs__submission-filters {
  display: grid;
  gap: 12px;
  grid-column: 1 / -1;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
}

.labs__submission-results,
.labs__submission-detail {
  background: #f8fafc;
  border: 1px solid #d7dde8;
  border-radius: 8px;
  padding: 12px;
}

.labs__report-detail {
  display: grid;
  gap: 6px;
}

.labs__submission-score,
.labs__submission-score-form {
  display: grid;
  gap: 8px;
}

.labs__submission-flags {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.labs__submission-flag {
  background: #e8f0ff;
  border: 1px solid #bfd3ff;
  border-radius: 999px;
  color: #1d4ed8;
  font-size: 12px;
  padding: 2px 8px;
}

.labs__submission-code {
  background: #111827;
  border-radius: 8px;
  color: #f8fafc;
  overflow-x: auto;
  padding: 12px;
  white-space: pre-wrap;
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

@media (max-width: 960px) {
  .labs__submission-layout {
    grid-template-columns: 1fr;
  }
}
</style>
