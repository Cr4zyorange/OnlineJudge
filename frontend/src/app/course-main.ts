import { createApp } from 'vue';
import CourseManagementView from '../views/crs/CourseManagementView.vue';
import { configureDefaultAuthContext } from './authContext';
import '../assets/main.css';

configureDefaultAuthContext();

createApp(CourseManagementView).mount('#app');
