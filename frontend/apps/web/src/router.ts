import { createRouter, createWebHistory } from 'vue-router';
import { getToken } from '@cnotes/api-client';
import HomeView from './views/HomeView.vue';
import LoginView from './views/LoginView.vue';
import RegisterView from './views/RegisterView.vue';

/**
 * 路由 + 未登录守卫。公开路由 /login、/register;其余需 localStorage 存在 token,
 * 否则跳 /login。已登录访问公开路由则跳 /。token 校验由后端 JWT 过滤器把关,
 * 此处只做前端导航兜底(避免未登录直接渲染主视图触发一堆 401)。
 */
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginView, meta: { public: true } },
    { path: '/register', component: RegisterView, meta: { public: true } },
    { path: '/', component: HomeView },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
});

router.beforeEach((to) => {
  const hasToken = getToken() !== null;
  if (!to.meta.public && !hasToken) return { path: '/login' };
  if (to.meta.public && hasToken) return { path: '/' };
});
