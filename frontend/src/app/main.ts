import { createApp } from 'vue';
import App from './App.vue';
import { configureDefaultAuthContext } from './authContext';
import { createAppRouter } from './router';
import '../assets/main.css';

configureDefaultAuthContext();

const app = createApp(App);
app.use(createAppRouter());
app.mount('#app');
