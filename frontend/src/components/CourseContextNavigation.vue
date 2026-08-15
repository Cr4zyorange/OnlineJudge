<template>
  <nav class="course-context-nav" aria-label="课程内导航" data-testid="course-context-navigation">
    <a
      data-testid="course-nav-home"
      :href="courseHomeHref"
      :class="{ active: activeSection === 'home' }"
    >
      课程主页
    </a>
    <a
      data-testid="course-nav-labs"
      :href="labHref"
      :class="{ active: activeSection === 'labs' }"
    >
      实训模块
    </a>
    <a
      data-testid="course-nav-homeworks"
      :href="homeworkHref"
      :class="{ active: activeSection === 'homeworks' }"
    >
      作业评测
    </a>
    <a
      data-testid="course-nav-grades"
      :href="gradeHref"
      :class="{ active: activeSection === 'grades' }"
    >
      成绩分析
    </a>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  courseId: number;
  currentPath: string;
  manageable: boolean;
}>();

const courseHomeHref = computed(() => `/courses/${props.courseId}`);
const labHref = computed(() => (
  props.manageable
    ? `/courses/${props.courseId}/labs/manage`
    : `/courses/${props.courseId}/labs`
));
const homeworkHref = computed(() => (
  props.manageable
    ? `/courses/${props.courseId}/homeworks/manage`
    : `/courses/${props.courseId}/homeworks`
));
const gradeHref = computed(() => (
  props.manageable
    ? `/courses/${props.courseId}/grades/manage/table`
    : `/courses/${props.courseId}/grades`
));

const activeSection = computed(() => {
  if (props.currentPath.includes('/labs')) {
    return 'labs';
  }
  if (props.currentPath.includes('/homeworks')) {
    return 'homeworks';
  }
  if (props.currentPath.includes('/grades') || props.currentPath.includes('/grd')) {
    return 'grades';
  }
  return 'home';
});
</script>

<style scoped>
.course-context-nav {
  display: flex;
  justify-content: center;
  gap: 8px;
  width: min(960px, calc(100% - 32px));
  margin: 0 auto 16px;
  padding: 8px;
  border: 1px solid rgba(255, 255, 255, 0.32);
  border-radius: 8px;
  background: rgba(239, 247, 250, 0.3);
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.1);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
}

.course-context-nav a {
  min-width: 108px;
  padding: 10px 14px;
  border-radius: 8px;
  color: var(--oj-ink-soft);
  font-weight: 700;
  letter-spacing: 0;
  text-align: center;
  text-decoration: none;
}

.course-context-nav a:hover,
.course-context-nav a.active {
  background: var(--oj-brand);
  color: #fff;
}

@media (max-width: 760px) {
  .course-context-nav {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    width: min(100% - 24px, 520px);
  }

  .course-context-nav a {
    min-width: 0;
    padding: 9px 8px;
    font-size: 0.88rem;
  }
}
</style>
