import { createApp } from 'vue';
import '@cnotes/design-tokens/tokens.css';
import './styles.css';
import App from './App.vue';
import { router } from './router';

createApp(App).use(router).mount('#app');
