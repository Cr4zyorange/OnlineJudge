<template>
  <AuthView v-if="viewMode === 'auth'" :initial-mode="authMode" />
  <AuthAdminView v-else-if="viewMode === 'auth-admin' && adminGate === 'allowed'" />
  <main v-else-if="viewMode === 'auth-admin'" class="app-empty-state">
    <p v-if="adminGate === 'checking'">正在校验登录态</p>
    <p v-else-if="adminGate === 'expired'">登录已失效，请重新登录</p>
    <p v-else>无权限访问</p>
  </main>
  <CourseManagementView v-else-if="viewMode === 'courses'" />
  <TeacherGradeTableView v-else-if="courseId !== null && page === 'grades'" :course-id="courseId" />
  <GradeItemConfigView v-else-if="courseId !== null" :course-id="courseId" />
  <main v-else class="app-empty-state">
    <p>缺少课程上下文</p>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getCurrentUser } from '../api/auth/auth';
import AuthView from '../views/auth/AuthView.vue';
import AuthAdminView from '../views/auth/AuthAdminView.vue';
import CourseManagementView from '../views/crs/CourseManagementView.vue';
import GradeItemConfigView from '../views/grd/GradeItemConfigView.vue';
import TeacherGradeTableView from '../views/grd/TeacherGradeTableView.vue';

const pathname = computed(() => window.location.pathname);
const searchParams = computed(() => new URLSearchParams(window.location.search));
const adminGate = ref<'idle' | 'checking' | 'allowed' | 'forbidden' | 'expired'>('idle');

onMounted(validateAdminRoute);

const page = computed(() => {
  const queryPage = searchParams.value.get('page');
  if (queryPage) {
    return queryPage;
  }
  return pathname.value.includes('/grades') ? 'grades' : 'grade-items';
});

const viewMode = computed(() => {
  if (pathname.value === '/login' || pathname.value === '/register') {
    return 'auth';
  }
  if (pathname.value === '/admin/auth') {
    return 'auth-admin';
  }
  if (pathname.value === '/' || pathname.value === '/courses' || pathname.value === '/courses/') {
    return 'courses';
  }
  return 'grade';
});

const authMode = computed(() => pathname.value === '/register' ? 'register' : 'login');

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

async function validateAdminRoute() {
  if (viewMode.value !== 'auth-admin') {
    return;
  }
  adminGate.value = 'checking';
  try {
    const user = await getCurrentUser();
    adminGate.value = user.userType === 'ADMIN' || user.roles.includes('ADMIN') ? 'allowed' : 'forbidden';
  } catch {
    adminGate.value = 'expired';
  }
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
