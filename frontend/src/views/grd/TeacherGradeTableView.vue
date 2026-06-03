<template>
  <main class="grade-table">
    <section class="grade-table__panel" aria-label="教师成绩总表">
      <div class="grade-table__actions">
        <button type="button" :disabled="busy" @click="syncGrades">
          {{ busy ? '处理中' : '同步来源成绩' }}
        </button>
        <button type="button" :disabled="busy" @click="recalculate">
          重新计算
        </button>
      </div>
      <form
        class="grade-table__filters"
        data-testid="grade-filter-form"
        @submit.prevent="applyFilters"
      >
        <label>
          学生
          <input
            v-model.trim="studentKeyword"
            data-testid="student-keyword"
            type="search"
            placeholder="学号"
          />
        </label>
        <label>
          成绩项
          <input
            v-model.trim="gradeItemIdInput"
            data-testid="grade-item-id"
            inputmode="numeric"
            min="1"
            type="number"
            placeholder="ID"
          />
        </label>
        <label>
          成绩状态
          <select v-model="gradeStatus" data-testid="grade-status">
            <option value="">全部</option>
            <option value="SCORED">已评分</option>
            <option value="UNSUBMITTED">未提交</option>
            <option value="UNGRADED">待评分</option>
            <option value="MISSING">缺失</option>
            <option value="ADJUSTED">已调整</option>
          </select>
        </label>
        <label>
          发布状态
          <select v-model="publishStatus" data-testid="publish-status">
            <option value="">全部</option>
            <option value="UNPUBLISHED">未发布</option>
            <option value="PUBLISHED">已发布</option>
          </select>
        </label>
        <label>
          每页
          <select v-model.number="size" data-testid="page-size">
            <option :value="10">10</option>
            <option :value="20">20</option>
            <option :value="50">50</option>
          </select>
        </label>
        <button type="submit" :disabled="loading">筛选</button>
        <button type="button" :disabled="loading" @click="resetFilters">重置</button>
      </form>
      <p v-if="feedback" class="grade-table__feedback">{{ feedback }}</p>
      <p v-if="errorMessage" class="grade-table__error">{{ errorMessage }}</p>
    </section>

    <section class="grade-table__analysis" data-testid="grade-analysis-panel" aria-label="教学分析">
      <div class="grade-table__detail-heading">
        <h2>教学分析</h2>
        <form class="grade-table__analysis-form" data-testid="analysis-form" @submit.prevent="refreshAnalysis">
          <label>
            统计目标
            <select v-model="analysisTargetType" data-testid="analysis-target-type">
              <option value="COURSE_TOTAL">课程总评</option>
              <option value="GRADE_ITEM">成绩项</option>
            </select>
          </label>
          <label v-if="analysisTargetType === 'GRADE_ITEM'">
            成绩项 ID
            <input
              v-model.trim="analysisGradeItemIdInput"
              data-testid="analysis-grade-item-id"
              inputmode="numeric"
              min="1"
              required
              type="number"
            />
          </label>
          <button type="submit" :disabled="analysisLoading">刷新</button>
        </form>
      </div>
      <p v-if="analysisLoading">加载中</p>
      <p v-else-if="analysisError" class="grade-table__error">{{ analysisError }}</p>
      <template v-else-if="analysis">
        <p class="grade-table__analysis-target">
          {{ analysis.targetType === 'COURSE_TOTAL' ? '课程总评' : `成绩项 ${analysis.gradeItemId}` }}
        </p>
        <dl class="grade-table__metrics">
          <div>
            <dt>均分</dt>
            <dd>{{ analysis.averageScore ?? '-' }}</dd>
          </div>
          <div>
            <dt>最高分</dt>
            <dd>{{ analysis.maxScore ?? '-' }}</dd>
          </div>
          <div>
            <dt>最低分</dt>
            <dd>{{ analysis.minScore ?? '-' }}</dd>
          </div>
          <div>
            <dt>及格率</dt>
            <dd>{{ formatRate(analysis.passRate) }}</dd>
          </div>
          <div>
            <dt>完成率</dt>
            <dd>{{ formatRate(analysis.completionRate) }}</dd>
          </div>
        </dl>
        <p class="grade-table__analysis-counts">
          共 {{ analysis.totalStudentCount }} 人，已提交 {{ analysis.submittedCount ?? analysis.completedCount }}，已完成 {{ analysis.completedCount }}，缺失 {{ analysis.missingCount }}，未提交 {{ analysis.unsubmittedCount }}，待评分 {{ analysis.ungradedCount }}
        </p>
        <ul class="grade-table__distribution">
          <li v-for="bucket in analysis.distribution" :key="bucket.label">
            {{ bucket.label }}：{{ bucket.count }}
          </li>
        </ul>
        <p class="grade-table__timestamp">数据时间点 {{ analysis.sourceDataTime }}</p>
      </template>
      <p v-else>暂无统计结果</p>
    </section>

    <section class="grade-table__list" aria-label="课程成绩总表">
      <p v-if="loading">加载中</p>
      <p v-else-if="rows.length === 0">暂无成绩记录</p>
      <template v-else>
        <p class="grade-table__total">共 {{ total }} 名学生</p>
        <table>
          <thead>
            <tr>
              <th>学生</th>
              <th>总评</th>
              <th>状态</th>
              <th>发布</th>
              <th>明细数</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.studentId">
              <td>{{ row.studentId }}</td>
              <td>{{ row.summary?.finalScore ?? '-' }}</td>
              <td>{{ row.summary?.finalStatus ?? 'INCOMPLETE' }}</td>
              <td>{{ row.summary?.publishStatus ?? 'UNPUBLISHED' }}</td>
              <td>{{ row.records.length }}</td>
              <td>
                <button
                  type="button"
                  :data-testid="`detail-student-${row.studentId}`"
                  @click="selectStudentDetail(row)"
                >
                  查看明细
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <div class="grade-table__pagination">
          <button
            type="button"
            data-testid="prev-page"
            :disabled="loading || page <= 1"
            @click="goToPage(page - 1)"
          >
            上一页
          </button>
          <span>第 {{ page }} / {{ totalPages }} 页</span>
          <button
            type="button"
            data-testid="next-page"
            :disabled="loading || page >= totalPages"
            @click="goToPage(page + 1)"
          >
            下一页
          </button>
        </div>
      </template>
    </section>

    <section
      v-if="selectedRow"
      class="grade-table__detail"
      aria-label="学生成绩明细"
    >
      <div class="grade-table__detail-heading">
        <h2>成绩明细</h2>
        <p>学生 {{ selectedRow.studentId }}</p>
        <button
          type="button"
          data-testid="publish-selected-student"
          :disabled="busy || !selectedRow.summary"
          @click="publishSelectedStudent"
        >
          发布该学生成绩
        </button>
      </div>
      <form
        v-if="selectedRow.summary"
        class="grade-table__adjustment"
        data-testid="submit-final-adjustment"
        @submit.prevent="submitFinalScoreAdjustment"
      >
        <label>
          调整后总评
          <input
            v-model.trim="finalScore"
            data-testid="final-score"
            inputmode="decimal"
            required
            type="number"
            min="0"
            step="0.01"
          />
        </label>
        <label>
          调整原因
          <textarea
            v-model.trim="finalReason"
            data-testid="final-reason"
            required
            maxlength="500"
            rows="3"
          />
        </label>
        <button type="submit" :disabled="busy">保存总评调整</button>
      </form>
      <table>
        <thead>
          <tr>
            <th>成绩项</th>
            <th>来源</th>
            <th>原始分</th>
            <th>折算分</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in selectedRow.records" :key="record.id">
            <td>{{ record.gradeItemId }}</td>
            <td>{{ record.sourceType }}</td>
            <td>{{ record.rawScore ?? '-' }}</td>
            <td>{{ record.weightedScore ?? '-' }}</td>
            <td>{{ record.gradeStatus }}</td>
            <td>
              <button type="button" @click="selectRecordForAdjustment(record)">
                调整
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <form
        v-if="selectedRecord"
        class="grade-table__adjustment"
        data-testid="submit-adjustment"
        @submit.prevent="submitAdjustment"
      >
        <label>
          调整后分数
          <input
            v-model.trim="adjustmentScore"
            data-testid="adjustment-score"
            inputmode="decimal"
            required
            type="number"
            min="0"
            step="0.01"
          />
        </label>
        <label>
          调整原因
          <textarea
            v-model.trim="adjustmentReason"
            data-testid="adjustment-reason"
            required
            maxlength="500"
            rows="3"
          />
        </label>
        <button type="submit" :disabled="busy">保存调整</button>
      </form>

      <div class="grade-table__logs" data-testid="change-log-list">
        <h2>变更记录</h2>
        <p v-if="logsLoading">加载中</p>
        <p v-else-if="changeLogs.length === 0">暂无变更记录</p>
        <ul v-else>
          <li v-for="log in changeLogs" :key="log.id">
            {{ log.changeType }}：{{ log.oldValue ?? '-' }} -> {{ log.newValue ?? '-' }}，{{ log.reason }}
          </li>
        </ul>
      </div>
    </section>

    <section class="grade-table__list" data-testid="publish-record-list" aria-label="成绩发布记录">
      <h2>发布记录</h2>
      <p v-if="publishRecordsLoading">加载中</p>
      <p v-else-if="publishRecords.length === 0">暂无发布记录</p>
      <ul v-else class="grade-table__publish-records">
        <li v-for="record in publishRecords" :key="record.id">
          {{ record.publishScope }}：{{ record.publishedCount }} 名学生，通知 {{ record.notificationStatus }}
        </li>
      </ul>
    </section>

    <section class="grade-table__list" data-testid="grade-review-list" aria-label="成绩复核">
      <div class="grade-table__detail-heading">
        <h2>成绩复核</h2>
        <div class="grade-table__review-toolbar">
          <label>
            复核状态
            <select
              v-model="reviewStatus"
              data-testid="review-status-filter"
              :disabled="reviewsLoading"
              @change="refreshReviewRequests"
            >
              <option value="">全部</option>
              <option value="PENDING">待处理</option>
              <option value="APPROVED">已同意</option>
              <option value="REJECTED">已驳回</option>
              <option value="CLOSED">已关闭</option>
            </select>
          </label>
          <button type="button" :disabled="reviewsLoading" @click="refreshReviewRequests">刷新</button>
        </div>
      </div>
      <p v-if="reviewsLoading">加载中</p>
      <p v-else-if="reviewRequests.length === 0">暂无复核记录</p>
      <ul v-else class="grade-table__reviews">
        <li v-for="request in reviewRequests" :key="request.requestId">
          <div>
            <strong>{{ request.status }}</strong>
            <span>学生 {{ request.studentId }}</span>
            <span>{{ request.targetType === 'FINAL_SCORE' ? '课程总评' : `成绩项 ${request.gradeItemId}` }}</span>
            <span>原成绩 {{ request.originalScore ?? '-' }}</span>
          </div>
          <p>{{ request.reason }}</p>
          <p v-if="request.adjustedScore !== null">调整后成绩 {{ request.adjustedScore }}</p>
          <p v-if="request.responseComment">{{ request.responseComment }}</p>
          <p v-if="request.processedAt">处理时间 {{ request.processedAt }}</p>
          <div v-if="request.status === 'PENDING'" class="grade-table__review-actions">
            <label>
              调整后成绩
              <input
                v-model.trim="reviewForms[request.requestId].adjustedScore"
                :data-testid="`review-adjusted-score-${request.requestId}`"
                inputmode="decimal"
                type="number"
                min="0"
                step="0.01"
              />
            </label>
            <label>
              处理说明
              <textarea
                v-model.trim="reviewForms[request.requestId].responseComment"
                :data-testid="`review-response-comment-${request.requestId}`"
                maxlength="1000"
                rows="3"
              />
            </label>
            <button
              type="button"
              :data-testid="`approve-review-${request.requestId}`"
              :disabled="busy"
              @click="processReview(request.requestId, 'APPROVE')"
            >
              同意修改
            </button>
            <button
              type="button"
              :data-testid="`reject-review-${request.requestId}`"
              :disabled="busy"
              @click="processReview(request.requestId, 'REJECT')"
            >
              驳回
            </button>
          </div>
        </li>
      </ul>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  adjustGradeRecord,
  adjustCourseFinalScore,
  getCourseGradeAnalysis,
  getGradeItemCompletion,
  type GradeTableQuery,
  listGradePublishRecords,
  listCourseGradeReviewRequests,
  listGradeChangeLogs,
  listCourseGrades,
  processGradeReviewRequest,
  publishCourseGrades,
  recalculateCourseGrades,
  syncSourceGrades
} from '../../api/grd/gradeRecords';
import type {
  CourseGradeRow,
  GradeAnalysisResult,
  GradeAnalysisTargetType,
  GradeItemCompletionResult,
  GradeChangeLog,
  GradePublishRecord,
  GradeReviewRequest,
  GradeReviewStatus,
  GradeRecord,
  GradeStatus,
  PublishStatus
} from '../../types/grd';

const props = defineProps<{
  courseId: number;
}>();

const rows = ref<CourseGradeRow[]>([]);
const loading = ref(false);
const busy = ref(false);
const feedback = ref('');
const errorMessage = ref('');
const total = ref(0);
const page = ref(1);
const size = ref(20);
const studentKeyword = ref('');
const gradeItemIdInput = ref('');
const gradeStatus = ref<GradeStatus | ''>('');
const publishStatus = ref<PublishStatus | ''>('');
const selectedRow = ref<CourseGradeRow | null>(null);
const selectedRecordId = ref<number | null>(null);
const adjustmentScore = ref('');
const adjustmentReason = ref('');
const finalScore = ref('');
const finalReason = ref('');
const changeLogs = ref<GradeChangeLog[]>([]);
const logsLoading = ref(false);
const publishRecords = ref<GradePublishRecord[]>([]);
const publishRecordsLoading = ref(false);
const reviewRequests = ref<GradeReviewRequest[]>([]);
const reviewsLoading = ref(false);
const reviewStatus = ref<GradeReviewStatus | ''>('PENDING');
const reviewForms = ref<Record<number, { adjustedScore: string; responseComment: string }>>({});
const analysis = ref<GradeAnalysisResult | null>(null);
const analysisLoading = ref(false);
const analysisError = ref('');
const analysisTargetType = ref<GradeAnalysisTargetType>('COURSE_TOTAL');
const analysisGradeItemIdInput = ref('');
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)));
const selectedRecord = computed(() => selectedRow.value?.records.find((record) => record.id === selectedRecordId.value) ?? null);

onMounted(async () => {
  await Promise.all([loadRows(), refreshPublishRecords(), refreshAnalysis(), refreshReviewRequests()]);
});

async function loadRows() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const result = await listCourseGrades(props.courseId, currentQuery());
    rows.value = result.records;
    total.value = result.total;
    page.value = result.page;
    size.value = result.size;
    refreshSelectedRow();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '成绩总表加载失败';
  } finally {
    loading.value = false;
  }
}

async function syncGrades() {
  busy.value = true;
  feedback.value = '';
  errorMessage.value = '';
  try {
    const result = await syncSourceGrades(props.courseId);
    feedback.value = `同步完成：${result.syncedCount} 条有效成绩，${result.ungradedCount} 条未评分，${result.missingCount} 条缺失`;
    page.value = 1;
    await Promise.all([loadRows(), refreshAnalysis()]);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '来源成绩同步失败';
  } finally {
    busy.value = false;
  }
}

async function recalculate() {
  busy.value = true;
  feedback.value = '';
  errorMessage.value = '';
  try {
    const result = await recalculateCourseGrades(props.courseId);
    feedback.value = `重新计算完成：${result.affectedCount} 名学生`;
    page.value = 1;
    await Promise.all([loadRows(), refreshAnalysis()]);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '课程总评计算失败';
  } finally {
    busy.value = false;
  }
}

async function applyFilters() {
  page.value = 1;
  await loadRows();
}

async function resetFilters() {
  studentKeyword.value = '';
  gradeItemIdInput.value = '';
  gradeStatus.value = '';
  publishStatus.value = '';
  page.value = 1;
  size.value = 20;
  await loadRows();
}

async function goToPage(nextPage: number) {
  page.value = Math.min(Math.max(nextPage, 1), totalPages.value);
  await loadRows();
}

async function selectStudentDetail(row: CourseGradeRow) {
  selectedRow.value = row;
  finalScore.value = row.summary?.finalScore ?? '';
  finalReason.value = '';
  selectRecordForAdjustment(row.records[0] ?? null);
  await refreshChangeLogs();
}

function selectRecordForAdjustment(record: GradeRecord | null) {
  selectedRecordId.value = record?.id ?? null;
  adjustmentScore.value = record?.rawScore ?? '';
  adjustmentReason.value = '';
}

async function submitAdjustment() {
  if (!selectedRecord.value || !selectedRow.value) {
    return;
  }
  busy.value = true;
  feedback.value = '';
  errorMessage.value = '';
  try {
    const result = await adjustGradeRecord(selectedRecord.value.id, {
      newScore: normalizeScoreInput(adjustmentScore.value),
      reason: adjustmentReason.value
    });
    feedback.value = `调整完成：${result.oldScore ?? '-'} -> ${result.newScore}`;
    await loadRows();
    await refreshChangeLogs();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '成绩调整失败';
  } finally {
    busy.value = false;
  }
}

async function submitFinalScoreAdjustment() {
  if (!selectedRow.value?.summary) {
    return;
  }
  busy.value = true;
  feedback.value = '';
  errorMessage.value = '';
  try {
    const result = await adjustCourseFinalScore(selectedRow.value.summary.id, {
      newScore: normalizeScoreInput(finalScore.value),
      reason: finalReason.value
    });
    feedback.value = `总评调整完成：${result.oldScore ?? '-'} -> ${result.newScore}`;
    finalScore.value = result.newScore;
    await loadRows();
    await refreshChangeLogs();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '课程总评调整失败';
  } finally {
    busy.value = false;
  }
}

async function publishSelectedStudent() {
  if (!selectedRow.value?.summary) {
    return;
  }
  busy.value = true;
  feedback.value = '';
  errorMessage.value = '';
  try {
    const result = await publishCourseGrades(props.courseId, {
      publishScope: 'PARTIAL_STUDENTS',
      studentIds: [selectedRow.value.studentId],
      gradeItemIds: []
    });
    feedback.value = `发布完成：${result.publishedCount} 名学生可查看成绩，通知状态 ${result.notificationStatus}`;
    await loadRows();
    await refreshPublishRecords();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '成绩发布失败';
  } finally {
    busy.value = false;
  }
}

async function refreshChangeLogs() {
  if (!selectedRow.value) {
    changeLogs.value = [];
    return;
  }
  logsLoading.value = true;
  try {
    const result = await listGradeChangeLogs(props.courseId, {
      studentId: selectedRow.value.studentId,
      page: 1,
      size: 20
    });
    changeLogs.value = result.records;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '变更记录加载失败';
  } finally {
    logsLoading.value = false;
  }
}

async function refreshPublishRecords() {
  publishRecordsLoading.value = true;
  try {
    const result = await listGradePublishRecords(props.courseId, {
      page: 1,
      size: 20
    });
    publishRecords.value = result.records;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '发布记录加载失败';
  } finally {
    publishRecordsLoading.value = false;
  }
}

async function refreshReviewRequests() {
  reviewsLoading.value = true;
  try {
    const query: {
      status?: GradeReviewStatus;
      page: number;
      size: number;
    } = {
      page: 1,
      size: 20
    };
    if (reviewStatus.value) {
      query.status = reviewStatus.value;
    }
    const result = await listCourseGradeReviewRequests(props.courseId, query);
    reviewRequests.value = result.records;
    for (const request of result.records) {
      if (!reviewForms.value[request.requestId]) {
        reviewForms.value[request.requestId] = {
          adjustedScore: request.adjustedScore ?? '',
          responseComment: request.responseComment ?? ''
        };
      }
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '复核申请加载失败';
  } finally {
    reviewsLoading.value = false;
  }
}

async function processReview(requestId: number, action: 'APPROVE' | 'REJECT') {
  busy.value = true;
  feedback.value = '';
  errorMessage.value = '';
  try {
    const form = reviewForms.value[requestId] ?? { adjustedScore: '', responseComment: '' };
    const result = await processGradeReviewRequest(requestId, {
      action,
      adjustedScore: action === 'APPROVE' ? normalizeScoreInput(form.adjustedScore) : null,
      responseComment: form.responseComment
    });
    feedback.value = `复核已处理：${result.status}`;
    await Promise.all([loadRows(), refreshChangeLogs(), refreshReviewRequests()]);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '复核处理失败';
  } finally {
    busy.value = false;
  }
}

async function refreshAnalysis() {
  analysisLoading.value = true;
  analysisError.value = '';
  try {
    if (analysisTargetType.value === 'GRADE_ITEM') {
      const gradeItemId = Number(analysisGradeItemIdInput.value);
      analysis.value = completionToAnalysis(await getGradeItemCompletion(props.courseId, gradeItemId));
      return;
    }
    analysis.value = await getCourseGradeAnalysis(props.courseId, {
      targetType: analysisTargetType.value
    });
  } catch (error) {
    analysisError.value = error instanceof Error ? error.message : '教学分析加载失败';
  } finally {
    analysisLoading.value = false;
  }
}

function completionToAnalysis(completion: GradeItemCompletionResult): GradeAnalysisResult {
  return {
    targetType: 'GRADE_ITEM',
    gradeItemId: completion.gradeItemId,
    totalStudentCount: completion.totalStudentCount,
    submittedCount: completion.submittedCount,
    completedCount: completion.completedCount,
    missingCount: completion.missingCount,
    unsubmittedCount: completion.unsubmittedCount,
    ungradedCount: completion.ungradedCount,
    averageScore: completion.averageScore,
    maxScore: null,
    minScore: null,
    passRate: '0.0000',
    completionRate: completion.completionRate,
    distribution: [],
    sourceDataTime: completion.sourceDataTime,
    generatedAt: completion.generatedAt
  };
}

function refreshSelectedRow() {
  if (!selectedRow.value) {
    return;
  }
  const refreshed = rows.value.find((row) => row.studentId === selectedRow.value?.studentId);
  if (!refreshed) {
    selectedRow.value = null;
    selectedRecordId.value = null;
    changeLogs.value = [];
    return;
  }
  selectedRow.value = refreshed;
  if (refreshed.summary) {
    finalScore.value = refreshed.summary.finalScore ?? '';
  }
  if (!refreshed.records.some((record) => record.id === selectedRecordId.value)) {
    selectRecordForAdjustment(refreshed.records[0] ?? null);
  }
}

function normalizeScoreInput(score: string | number) {
  const numericScore = Number(score);
  if (Number.isFinite(numericScore)) {
    return numericScore.toFixed(2);
  }
  return String(score);
}

function formatRate(value: string | null) {
  if (value === null) {
    return '-';
  }
  const numericValue = Number(value);
  if (!Number.isFinite(numericValue)) {
    return value;
  }
  return `${(numericValue * 100).toFixed(2)}%`;
}

function currentQuery() {
  const gradeItemId = Number(gradeItemIdInput.value);
  const query: GradeTableQuery = {
    page: page.value,
    size: size.value
  };
  if (studentKeyword.value) {
    query.studentKeyword = studentKeyword.value;
  }
  if (Number.isInteger(gradeItemId) && gradeItemId > 0) {
    query.gradeItemId = gradeItemId;
  }
  if (gradeStatus.value) {
    query.gradeStatus = gradeStatus.value;
  }
  if (publishStatus.value) {
    query.publishStatus = publishStatus.value;
  }
  return query;
}
</script>

<style scoped>
.grade-table {
  background: #f6f8fb;
  color: #1f2937;
  display: grid;
  gap: 16px;
  min-height: 100vh;
  padding: 24px;
}

.grade-table > * {
  min-width: 0;
}

.grade-table__panel,
.grade-table__list,
.grade-table__detail,
.grade-table__analysis {
  background: #ffffff;
  border: 1px solid #d8dee9;
  border-radius: 8px;
  overflow-x: auto;
  padding: 16px;
}

.grade-table__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.grade-table__filters {
  align-items: end;
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  margin-top: 16px;
}

label {
  color: #334155;
  display: grid;
  font-size: 13px;
  gap: 6px;
}

input,
select,
textarea {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  color: #1f2937;
  min-height: 36px;
  padding: 7px 10px;
}

textarea {
  resize: vertical;
}

button {
  background: #2563eb;
  border: 0;
  border-radius: 6px;
  color: #ffffff;
  cursor: pointer;
  padding: 8px 14px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

table {
  border-collapse: collapse;
  min-width: 640px;
  width: 100%;
}

th,
td {
  border-bottom: 1px solid #e5e7eb;
  padding: 10px;
  text-align: left;
}

.grade-table__feedback {
  color: #047857;
}

.grade-table__error {
  color: #b91c1c;
}

.grade-table__total {
  color: #475569;
  font-size: 14px;
  margin: 0 0 12px;
}

.grade-table__pagination {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 14px;
}

.grade-table__detail {
  display: grid;
  gap: 16px;
}

.grade-table__detail-heading {
  align-items: baseline;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.grade-table__detail-heading h2,
.grade-table__logs h2,
.grade-table__list h2 {
  font-size: 16px;
  margin: 0;
}

.grade-table__analysis {
  display: grid;
  gap: 12px;
}

.grade-table__analysis-form {
  align-items: end;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.grade-table__review-toolbar {
  align-items: end;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.grade-table__analysis-target,
.grade-table__timestamp {
  color: #475569;
  font-size: 14px;
  margin: 0;
}

.grade-table__metrics {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  margin: 0;
}

.grade-table__metrics div {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 10px;
}

.grade-table__metrics dt {
  color: #64748b;
  font-size: 12px;
}

.grade-table__metrics dd {
  color: #0f172a;
  font-size: 20px;
  font-weight: 700;
  margin: 4px 0 0;
}

.grade-table__analysis-counts {
  color: #334155;
  font-size: 14px;
  margin: 0;
}

.grade-table__distribution {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  list-style: none;
  margin: 0;
  padding: 0;
}

.grade-table__distribution li {
  background: #eef2ff;
  border-radius: 6px;
  color: #3730a3;
  font-size: 13px;
  padding: 6px 10px;
}

.grade-table__adjustment {
  align-items: end;
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(140px, 180px) minmax(220px, 1fr) auto;
}

.grade-table__logs ul {
  display: grid;
  gap: 8px;
  margin: 8px 0 0;
  padding-left: 18px;
}

.grade-table__publish-records {
  display: grid;
  gap: 8px;
  margin: 8px 0 0;
  padding-left: 18px;
}

.grade-table__reviews {
  display: grid;
  gap: 12px;
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
}

.grade-table__reviews li {
  border-bottom: 1px solid #e5e7eb;
  display: grid;
  gap: 8px;
  padding: 8px 0 12px;
}

.grade-table__reviews li > div:first-child {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.grade-table__reviews p {
  margin: 0;
}

.grade-table__review-actions {
  align-items: end;
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(120px, 160px) minmax(220px, 1fr) auto auto;
}

.grade-table__pagination span {
  color: #475569;
  font-size: 14px;
}

@media (max-width: 760px) {
  .grade-table__review-actions {
    grid-template-columns: 1fr;
  }
}
</style>
