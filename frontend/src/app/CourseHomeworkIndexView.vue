<template>
  <HomeworkTeacherView v-if="currentCourse?.manageable" :course-id="courseId" />
  <HomeworkStudentListView v-else :course-id="courseId" />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { currentCourse } from './runtimeContext';
import HomeworkStudentListView from '../views/hwk/HomeworkStudentListView.vue';
import HomeworkTeacherView from '../views/hwk/HomeworkTeacherView.vue';

const props = defineProps<{ courseId: number }>();
const courseId = computed(() => props.courseId);
</script>
