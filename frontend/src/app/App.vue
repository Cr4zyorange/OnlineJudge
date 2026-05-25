<template>
  <CourseManagementView v-if="viewMode === 'courses'" />
  <GradeItemConfigView v-else-if="courseId !== null" :course-id="courseId" />
  <main v-else class="app-empty-state">
    <p>缺少课程上下文</p>
  </main>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import CourseManagementView from '../views/crs/CourseManagementView.vue';
import GradeItemConfigView from '../views/grd/GradeItemConfigView.vue';

const pathname = computed(() => window.location.pathname);

const viewMode = computed(() => {
  if (pathname.value.includes('/grd/grade-items')) {
    return 'grade-items';
  }
  if (pathname.value === '/' || pathname.value.startsWith('/courses')) {
    return 'courses';
  }
  return 'grade-items';
});

const courseId = computed(() => {
  const queryCourseId = parseCourseId(new URLSearchParams(window.location.search).get('courseId'));
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
