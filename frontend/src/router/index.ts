import { createRouter, createWebHistory } from 'vue-router';
import { getRole, isLoggedIn, roleHome, staffRolesFor } from '@/utils/auth';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('@/pages/PreRegisterPage.vue'), meta: { public: true } },
    { path: '/checkin', name: 'checkin', component: () => import('@/pages/CheckInPage.vue') },
    { path: '/workbench', name: 'workbench', component: () => import('@/pages/WorkbenchPage.vue') },
    { path: '/interview', name: 'interview', component: () => import('@/pages/InterviewPage.vue') },
    { path: '/second-round', name: 'second-round', component: () => import('@/pages/SecondRoundPage.vue'), meta: { public: true } },
    { path: '/results', name: 'results', component: () => import('@/pages/ResultsPage.vue') },
    { path: '/console', name: 'console', component: () => import('@/pages/ConsolePage.vue') },
    { path: '/display', name: 'display', component: () => import('@/pages/DisplayPage.vue'), meta: { public: true, standalone: true } },
    { path: '/login', name: 'login', component: () => import('@/pages/LoginPage.vue'), meta: { public: true, standalone: true } },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/pages/NotFound.vue'), meta: { public: true, standalone: true } },
  ],
});

router.beforeEach((to) => {
  // 学生公开页：直接放行
  if (to.meta.public) return true;

  // 未登录：跳登录页并带回跳地址
  if (!isLoggedIn()) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }

  // 已登录但角色无权访问：跳该角色的默认落地页
  const roles = staffRolesFor(to.path);
  const role = getRole();
  if (roles && (!role || !roles.includes(role))) {
    return { path: roleHome(role) };
  }

  return true;
});

export default router;
