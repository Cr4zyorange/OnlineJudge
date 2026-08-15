<template>
  <nav class="course-nav" aria-label="课程内导航" data-testid="course-context-navigation">
    <RouterLink :to="homeHref" data-testid="course-nav-home">课程主页</RouterLink>
    <RouterLink :to="labHref" data-testid="course-nav-labs">实训</RouterLink>
    <RouterLink :to="homeworkHref" data-testid="course-nav-homeworks">作业</RouterLink>
    <RouterLink :to="gradeHref" data-testid="course-nav-grades">成绩</RouterLink>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink } from 'vue-router';
import { currentCourse } from '../../app/runtimeContext';

const props = defineProps<{ courseId: number }>();
const canManage = computed(() => currentCourse.value?.manageable === true);
const homeHref = computed(() => `/courses/${props.courseId}`);
const labHref = computed(() => canManage.value ? `/courses/${props.courseId}/labs/manage` : `/courses/${props.courseId}/labs`);
const homeworkHref = computed(() => canManage.value ? `/courses/${props.courseId}/homeworks/manage` : `/courses/${props.courseId}/homeworks`);
const gradeHref = computed(() => canManage.value ? `/courses/${props.courseId}/grades/manage/table` : `/courses/${props.courseId}/grades`);
</script>

<style scoped>
.course-nav {
  position: sticky;
  top: 92px;
  display: grid;
  gap: 6px;
  align-self: start;
  padding: 12px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 12px;
  background: rgba(248, 251, 252, 0.9);
  box-shadow: 0 10px 28px rgba(15, 45, 41, 0.1);
  backdrop-filter: blur(12px);
}

.course-nav a {
  border-radius: 8px;
  color: var(--oj-ink-soft, #5d7177);
  font-weight: 700;
  padding: 11px 12px;
  text-decoration: none;
}

.course-nav a:hover,
.course-nav a.router-link-active {
  background: var(--oj-brand, #16423c);
  color: white;
}

@media (max-width: 1000px) {
  .course-nav {
    position: static;
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .course-nav a {
    padding-inline: 6px;
    text-align: center;
  }
}
</style>
