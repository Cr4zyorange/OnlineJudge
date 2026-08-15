<template>
  <div class="course-shell">
    <CourseNav :course-id="courseId" />
    <div class="course-shell__content">
      <RouterView />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { RouterView, useRoute } from 'vue-router';
import CourseNav from '../components/foundation/CourseNav.vue';

const route = useRoute();
const courseId = computed(() => Number(route.params.courseId));
</script>

<style scoped>
.course-shell {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 20px;
  width: min(1400px, calc(100% - 32px));
  margin: 0 auto;
}

.course-shell__content {
  min-width: 0;
}

@media (max-width: 1000px) {
  .course-shell {
    grid-template-columns: minmax(0, 1fr);
    width: min(100% - 20px, 760px);
    gap: 12px;
  }
}
</style>
