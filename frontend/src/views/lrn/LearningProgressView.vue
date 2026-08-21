<template>
  <main class="progress-page">
    <section class="progress-page__shell">
      <aside class="progress-page__summary" aria-label="学习进度概览">
        <h1>学习进度</h1>
        <p>按课程和章节查看学习进展，并从上次断点继续。</p>
        <dl>
          <div>
            <dt>课程数量</dt>
            <dd>{{ progressOverview?.total ?? 0 }}</dd>
          </div>
          <div>
            <dt>平均进度</dt>
            <dd>{{ averageProgress }}%</dd>
          </div>
        </dl>
      </aside>

      <section class="progress-page__content" aria-label="课程与章节学习进度">
        <header class="progress-page__header">
          <div>
            <h2>我的课程进度</h2>
          </div>
          <button type="button" :disabled="loading" @click="loadProgress">刷新</button>
        </header>

        <PageState v-if="loading" state="loading" title="正在加载学习进度" />
        <PageState
          v-else-if="errorMessage"
          state="error"
          title="学习进度加载失败"
          :message="errorMessage"
          retry-label="重试"
          @retry="loadProgress"
        />
        <PageState
          v-else-if="courses.length === 0"
          state="empty"
          title="暂无学习进度记录"
          message="开始课程学习后，进度和继续学习入口会显示在这里。"
        />

        <div v-else class="progress-page__list">
          <article v-for="course in courses" :key="course.courseId" class="course-progress">
            <header class="course-progress__header">
              <div>
                <h3>{{ course.courseName }}</h3>
                <p v-if="course.lastPosition">上次位置：{{ course.lastPosition }}</p>
              </div>
              <a
                v-if="course.continueUrl"
                class="course-progress__continue"
                :href="course.continueUrl"
              >
                继续学习
              </a>
            </header>

            <div class="course-progress__bar" aria-hidden="true">
              <span :style="{ width: `${course.progressPercent}%` }" />
            </div>
            <p class="course-progress__percent">{{ course.progressPercent }}%</p>

            <section class="course-progress__chapters" aria-label="章节进度">
              <article v-for="chapter in course.chapters" :key="chapter.chapterId" class="chapter-progress">
                <div>
                  <h4>{{ chapter.chapterName }}</h4>
                  <p v-if="chapter.lastPosition">断点：{{ chapter.lastPosition }}</p>
                </div>
                <strong>{{ chapter.progressPercent }}%</strong>
              </article>
            </section>
          </article>
        </div>
      </section>

      <section v-if="isTeacher" class="progress-page__content" aria-label="教师课程学习统计">
        <header class="progress-page__header">
          <div>
            <h2>课程学习统计</h2>
          </div>
          <button type="button" :disabled="teacherLoading" @click="loadTeacherProgress">查询</button>
        </header>
        <label class="teacher-query">
          <span>课程 ID</span>
          <input v-model.number="teacherCourseId" type="number" min="1" />
        </label>
        <p v-if="teacherError" class="progress-page__state progress-page__state--error">{{ teacherError }}</p>
        <article v-if="teacherOverview" class="course-progress">
          <header class="course-progress__header">
            <div>
              <h3>{{ teacherOverview.courseName || `课程 ${teacherOverview.courseId}` }}</h3>
              <p>{{ teacherOverview.studentCount }} 名学生</p>
            </div>
            <strong>{{ teacherOverview.averageProgressPercent }}%</strong>
          </header>
          <section class="course-progress__chapters" aria-label="学生进度">
            <article v-for="student in teacherOverview.students" :key="student.studentId" class="chapter-progress">
              <div>
                <h4>{{ student.studentName }}</h4>
                <p>{{ progressStatusLabel(student.status) }}</p>
              </div>
              <strong>{{ student.progressPercent }}%</strong>
            </article>
          </section>
        </article>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getLearningProgress, getTeacherLearningProgress } from '../../api/lrn/learningProgress';
import { currentUser } from '../../app/runtimeContext';
import PageState from '../../components/foundation/PageState.vue';
import type { LearningProgressStatus } from '../../types/lrn';
import type { LearningCourseProgressAggregate, LearningProgressOverview } from '../../types/lrn';

const loading = ref(false);
const errorMessage = ref('');
const progressOverview = ref<LearningProgressOverview | null>(null);
const teacherLoading = ref(false);
const teacherError = ref('');
const teacherOverview = ref<LearningCourseProgressAggregate | null>(null);
const teacherCourseId = ref<number | null>(Number(new URLSearchParams(window.location.search).get('courseId')) || null);

const courses = computed(() => progressOverview.value?.courses ?? []);
const isTeacher = computed(() => {
  const trustedRoles = new Set([
    currentUser.value?.userType,
    ...(currentUser.value?.roles ?? [])
  ].filter((role): role is string => Boolean(role)));
  return trustedRoles.has('TEACHER') || trustedRoles.has('ADMIN');
});
const averageProgress = computed(() => {
  if (courses.value.length === 0) {
    return 0;
  }
  return Math.round(
    courses.value.reduce((sum, course) => sum + course.progressPercent, 0) / courses.value.length
  );
});

onMounted(loadProgress);

async function loadProgress() {
  loading.value = true;
  errorMessage.value = '';
  try {
    progressOverview.value = await getLearningProgress(undefined);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '进度加载失败';
  } finally {
    loading.value = false;
  }
}

async function loadTeacherProgress() {
  teacherError.value = '';
  teacherOverview.value = null;
  if (!teacherCourseId.value || teacherCourseId.value <= 0) {
    teacherError.value = '请输入课程 ID';
    return;
  }
  teacherLoading.value = true;
  try {
    teacherOverview.value = await getTeacherLearningProgress(teacherCourseId.value);
  } catch (error) {
    teacherError.value = error instanceof Error ? error.message : '课程学习统计加载失败';
  } finally {
    teacherLoading.value = false;
  }
}

function progressStatusLabel(status: LearningProgressStatus) {
  const labels: Record<LearningProgressStatus, string> = {
    NOT_STARTED: '未开始',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成'
  };
  return labels[status] ?? '未知状态';
}
</script>

<style scoped>
.progress-page {
  padding: 24px;
}

.progress-page__shell {
  display: grid;
  gap: 24px;
  grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
  margin: 0 auto;
  max-width: 1280px;
}

.progress-page__summary,
.progress-page__content,
.course-progress,
.chapter-progress {
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.1);
}

.progress-page__summary {
  align-self: start;
  display: grid;
  gap: 18px;
  padding: 24px;
  position: sticky;
  top: 24px;
}

.progress-page__summary h1,
.progress-page__header h2,
.course-progress h3,
.chapter-progress h4 {
  margin: 0;
}

.progress-page__summary p,
.course-progress p,
.chapter-progress p {
  color: #000;
  margin: 0;
}

.progress-page__summary dl {
  display: grid;
  gap: 12px;
  margin: 0;
}

.progress-page__summary dl div {
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  padding: 14px;
}

.progress-page__summary dt {
  color: #070707;
  font-size: 13px;
}

.progress-page__summary dd {
  font-size: 24px;
  font-weight: 700;
  margin: 4px 0 0;
}

.progress-page__content {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 30px 16px 18px;
  align-self: start;
}

.progress-page__header,
.course-progress__header,
.chapter-progress {
  align-items: center;
  display: grid;
  gap: 12px;
}

.progress-page__header,
.course-progress__header {
  grid-template-columns: 1fr auto;
}

.progress-page__header {
  min-height: 0;
  align-items: center;
  margin-bottom: 24px;
}

.progress-page__header h2 {
  line-height: 1;
  margin: 0;
}

.progress-page__header button {
  min-height: 36px;
  min-width: 64px;
  padding: 0 16px;
}

.progress-page__eyebrow {
  color: #55746d;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  margin: 0 0 4px;
}

button,
.teacher-query input,
.course-progress__continue {
  align-items: center;
  background: #16423c;
  border: 1px solid #16423c;
  border-radius: 8px;
  color: #ffffff;
  cursor: pointer;
  display: inline-flex;
  font-weight: 700;
  justify-content: center;
  min-height: 40px;
  padding: 0 14px;
  text-decoration: none;
}

.teacher-query {
  display: grid;
  gap: 6px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.progress-page__state {
  background: rgba(255, 255, 255, 0.64);
  border: 1px dashed #b8c8c2;
  border-radius: 8px;
  margin: 0;
  padding: 36px;
}

.progress-page__state--error {
  align-items: center;
  color: #9d2f22;
  display: flex;
  justify-content: space-between;
}

.progress-page__list {
  display: grid;
  gap: 14px;
}

.course-progress {
  display: grid;
  gap: 14px;
  padding: 18px;
}

.course-progress__bar {
  background: rgba(217, 229, 223, 0.9);
  border-radius: 999px;
  height: 10px;
  overflow: hidden;
}

.course-progress__bar span {
  background: #16423c;
  display: block;
  height: 100%;
}

.course-progress__percent {
  color: #16423c !important;
  font-size: 24px;
  font-weight: 800;
}

.course-progress__chapters {
  display: grid;
  gap: 10px;
}

.chapter-progress {
  grid-template-columns: 1fr auto;
  padding: 14px;
}

.chapter-progress strong {
  color: #16423c;
}

@media (max-width: 980px) {
  .progress-page__shell,
  .course-progress__header {
    grid-template-columns: 1fr;
  }

  .progress-page__summary {
    position: static;
  }
}

@media (max-width: 620px) {
  .progress-page {
    padding: 18px;
  }

  .progress-page__header,
  .chapter-progress {
    grid-template-columns: 1fr;
  }
}
</style>
