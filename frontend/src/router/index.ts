import { createRouter, createWebHistory } from 'vue-router';
import CourseManagementView from '../views/crs/CourseManagementView.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/courses' },
    { path: '/courses', component: CourseManagementView }
  ]
});

export default router;
