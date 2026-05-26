<template>
  <CourseManagementView v-if="viewMode === 'courses'" />
  <TeacherGradeTableView v-else-if="courseId !== null && page === 'grades'" :course-id="courseId" />
  <GradeItemConfigView v-else-if="courseId !== null" :course-id="courseId" />
  <main v-else class="app-empty-state">
    <p>缺少课程上下文</p>
  </main>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, defineComponent, h } from 'vue';
import CourseManagementView from '../views/crs/CourseManagementView.vue';
import GradeItemConfigView from '../views/grd/GradeItemConfigView.vue';

const gradeTableModules = import.meta.glob('../views/grd/TeacherGradeTableView.vue');
const TeacherGradeTableViewFallback = defineComponent({
  props: {
    courseId: {
      type: Number,
      required: true
    }
  },
  setup() {
    return () => h('main', { class: 'app-empty-state' }, '成绩列表模块未加载');
  }
});
const TeacherGradeTableView = defineAsyncComponent(() => {
  const loader = gradeTableModules['../views/grd/TeacherGradeTableView.vue'];
  return loader ? loader() as Promise<typeof TeacherGradeTableViewFallback> : Promise.resolve(TeacherGradeTableViewFallback);
});

const pathname = computed(() => window.location.pathname);
const searchParams = computed(() => new URLSearchParams(window.location.search));

const page = computed(() => {
  const queryPage = searchParams.value.get('page');
  if (queryPage) {
    return queryPage;
  }
  if (pathname.value.includes('/grd/grades')) {
    return 'grades';
  }
  return 'grade-items';
});

const viewMode = computed(() => {
  if (pathname.value.includes('/grd/grade-items') || pathname.value.includes('/grd/grades')) {
    return 'grade';
  }
  if (pathname.value === '/' || pathname.value === '/courses' || pathname.value === '/courses/') {
    return 'courses';
  }
  return 'grade';
});

const courseId = computed(() => {
  const queryCourseId = parseCourseId(searchParams.value.get('courseId'));
  if (queryCourseId !== null) {
    return queryCourseId;
  }
  const pathCourseId = window.location.pathname.match(/\/courses\/(\d+)(?:\/|$)/)?.[1] ?? null;
  return parseCourseId(pathCourseId);
});

function parseCourseId(value: string | null) {
  const parsedCourseId = Number(value);
  return Number.isInteger(parsedCourseId) && parsedCourseId > 0 ? parsedCourseId : null;
}
</script>

<style scoped>
.app-empty-state {
  align-items: center;
  background: #f6f8fb;
  color: #4b5563;
  display: flex;
  min-height: 100vh;
  padding: 24px;
}
</style>
