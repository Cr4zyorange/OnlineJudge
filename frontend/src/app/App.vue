<template>
  <AuthView v-if="viewMode === 'auth'" :initial-mode="authMode" />
  <AuthStatusView v-else-if="viewMode === 'forbidden'" kind="forbidden" />
  <AuthStatusView v-else-if="viewMode === 'session-expired'" kind="expired" />
  <AuthStatusView v-else-if="viewMode === 'account-disabled'" kind="account-disabled" />
  <AuthProfileView v-else-if="viewMode === 'profile'" />
  <AuthAdminView v-else-if="viewMode === 'auth-admin' && adminGate === 'allowed'" />
  <main v-else-if="viewMode === 'auth-admin'" class="app-empty-state">
    <p v-if="adminGate === 'checking'">正在校验登录状态</p>
    <p v-else-if="adminGate === 'expired'">登录已失效，请重新登录</p>
    <p v-else>无权限访问</p>
  </main>
  <CourseManagementView v-else-if="viewMode === 'courses'" />
  <LabTeacherView
    v-else-if="viewMode === 'lab' && labRole === 'teacher' && courseId !== null"
    :course-id="courseId"
  />
  <LabStudentView
    v-else-if="viewMode === 'lab' && labRole === 'student' && courseId !== null && labId !== null"
    :course-id="courseId"
    :lab-id="labId"
  />
  <StudentGradeView
    v-else-if="courseId !== null && page === 'grades' && gradeRole === 'student'"
    :course-id="courseId"
  />
  <TeacherGradeTableView v-else-if="courseId !== null && page === 'grades'" :course-id="courseId" />
  <GradeItemConfigView v-else-if="courseId !== null" :course-id="courseId" />
  <main v-else class="app-empty-state">
    <p>缺少课程上下文</p>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { getCurrentUser } from '../api/auth/auth';
import AuthProfileView from '../views/auth/AuthProfileView.vue';
import AuthStatusView from '../views/auth/AuthStatusView.vue';
import AuthView from '../views/auth/AuthView.vue';
import AuthAdminView from '../views/auth/AuthAdminView.vue';
import CourseManagementView from '../views/crs/CourseManagementView.vue';
import GradeItemConfigView from '../views/grd/GradeItemConfigView.vue';
import LabStudentView from '../views/lab/LabStudentView.vue';
import LabTeacherView from '../views/lab/LabTeacherView.vue';
import StudentGradeView from '../views/grd/StudentGradeView.vue';
import TeacherGradeTableView from '../views/grd/TeacherGradeTableView.vue';

const NAVIGATION_EVENT = 'onlinejudge:navigation';

const currentLocation = ref({
  pathname: window.location.pathname,
  search: window.location.search
});
const pathname = computed(() => currentLocation.value.pathname);
const searchParams = computed(() => new URLSearchParams(currentLocation.value.search));
const adminGate = ref<'idle' | 'checking' | 'allowed' | 'forbidden' | 'expired'>('idle');

onMounted(() => {
  syncLocation();
  window.addEventListener('popstate', syncLocation);
  window.addEventListener(NAVIGATION_EVENT, syncLocation);
  void validateAdminRoute();
});

onUnmounted(() => {
  window.removeEventListener('popstate', syncLocation);
  window.removeEventListener(NAVIGATION_EVENT, syncLocation);
});

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
  if (pathname.value === '/403') {
    return 'forbidden';
  }
  if (pathname.value === '/session-expired') {
    return 'session-expired';
  }
  if (pathname.value === '/account-disabled') {
    return 'account-disabled';
  }
  if (pathname.value === '/profile' || pathname.value === '/profile/password') {
    return 'profile';
  }
  if (pathname.value === '/admin/auth') {
    return 'auth-admin';
  }
  if (pathname.value === '/' || pathname.value === '/courses' || pathname.value === '/courses/') {
    return 'courses';
  }
  if (pathname.value.includes('/labs')) {
    return 'lab';
  }
  return 'grade';
});

const authMode = computed(() => pathname.value === '/register' ? 'register' : 'login');

const labRole = computed(() => {
  const queryRole = searchParams.value.get('role')?.toLowerCase();
  if (queryRole === 'student' || queryRole === 'teacher') {
    return queryRole;
  }
  const storedRole = window.localStorage.getItem('onlinejudge.userRole')
    ?? window.localStorage.getItem('onlinejudge.role');
  return storedRole === 'STUDENT' ? 'student' : 'teacher';
});

const gradeRole = computed(() => {
  const queryRole = searchParams.value.get('role')?.toLowerCase();
  if (queryRole === 'student' || queryRole === 'teacher') {
    return queryRole;
  }
  const storedRole = window.localStorage.getItem('onlinejudge.userRole')
    ?? window.localStorage.getItem('onlinejudge.role');
  return storedRole === 'STUDENT' ? 'student' : 'teacher';
});

const courseId = computed(() => {
  const queryCourseId = parseCourseId(searchParams.value.get('courseId'));
  if (queryCourseId !== null) {
    return queryCourseId;
  }
  const pathCourseId = pathname.value.match(/\/courses\/(\d+)(?:\/|$)/)?.[1] ?? null;
  return parseCourseId(pathCourseId);
});

const labId = computed(() => {
  const queryLabId = parseCourseId(searchParams.value.get('labId'));
  if (queryLabId !== null) {
    return queryLabId;
  }
  const pathLabId = pathname.value.match(/\/labs\/(\d+)(?:\/|$)/)?.[1] ?? null;
  return parseCourseId(pathLabId);
});

function parseCourseId(value: string | null) {
  const parsedCourseId = Number(value);
  return Number.isInteger(parsedCourseId) && parsedCourseId > 0 ? parsedCourseId : null;
}

function syncLocation() {
  currentLocation.value = {
    pathname: window.location.pathname,
    search: window.location.search
  };
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
