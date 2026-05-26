<template>
  <CourseManagementView v-if="viewMode === 'courses'" />
  <LabTeacherView
    v-else-if="viewMode === 'lab' && labRole === 'teacher' && courseId !== null"
    :course-id="courseId"
  />
  <LabStudentView
    v-else-if="viewMode === 'lab' && labRole === 'student' && courseId !== null && labId !== null"
    :course-id="courseId"
    :lab-id="labId"
  />
  <TeacherGradeTableView v-else-if="courseId !== null && page === 'grades'" :course-id="courseId" />
  <GradeItemConfigView v-else-if="courseId !== null" :course-id="courseId" />
  <main v-else class="app-empty-state">
    <p>缺少课程上下文</p>
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import CourseManagementView from '../views/crs/CourseManagementView.vue';
import GradeItemConfigView from '../views/grd/GradeItemConfigView.vue';
import LabStudentView from '../views/lab/LabStudentView.vue';
import LabTeacherView from '../views/lab/LabTeacherView.vue';
import TeacherGradeTableView from '../views/grd/TeacherGradeTableView.vue';

const pathname = computed(() => window.location.pathname);
const searchParams = computed(() => new URLSearchParams(window.location.search));

const page = computed(() => {
  const queryPage = searchParams.value.get('page');
  if (queryPage) {
    return queryPage;
  }
  return pathname.value.includes('/grades') ? 'grades' : 'grade-items';
});

const viewMode = computed(() => {
  if (pathname.value === '/' || pathname.value === '/courses' || pathname.value === '/courses/') {
    return 'courses';
  }
  if (pathname.value.includes('/labs')) {
    return 'lab';
  }
  return 'grade';
});

const labRole = computed(() => searchParams.value.get('role') === 'student' ? 'student' : 'teacher');

const courseId = computed(() => {
  const queryCourseId = parseCourseId(searchParams.value.get('courseId'));
  if (queryCourseId !== null) {
    return queryCourseId;
  }
  const pathCourseId = window.location.pathname.match(/\/courses\/(\d+)(?:\/|$)/)?.[1] ?? null;
  return parseCourseId(pathCourseId);
});

const labId = computed(() => {
  const queryLabId = parseCourseId(searchParams.value.get('labId'));
  if (queryLabId !== null) {
    return queryLabId;
  }
  const pathLabId = window.location.pathname.match(/\/labs\/(\d+)(?:\/|$)/)?.[1] ?? null;
  return parseCourseId(pathLabId);
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
