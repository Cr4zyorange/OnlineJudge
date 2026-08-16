<template>
  <LabTeacherView v-if="currentCourse?.manageable" :course-id="courseId" />
  <LabStudentListView v-else :course-id="courseId" />
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { currentCourse } from './runtimeContext';
import LabStudentListView from '../views/lab/LabStudentListView.vue';
import LabTeacherView from '../views/lab/LabTeacherView.vue';

const props = defineProps<{ courseId: number }>();
const courseId = computed(() => props.courseId);
</script>
