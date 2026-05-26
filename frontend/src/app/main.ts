import { createApp } from 'vue';
import App from './App.vue';
import { configureDefaultAuthContext } from './authContext';
import '../assets/main.css';

configureDefaultAuthContext();

createApp(App).mount('#app');
