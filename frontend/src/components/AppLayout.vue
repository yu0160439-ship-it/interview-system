<template>
  <div class="app-layout">
    <header class="top-bar">
      <div class="brand">
        <img src="@/assets/logo-placeholder.svg" alt="社团Logo占位" class="logo" />
        <span class="title">面试叫号系统</span>
      </div>
      <nav class="nav">
        <!-- 学生端：报名登记 / 候场大屏 -->
        <template v-if="!isLoggedIn()">
          <router-link to="/">报名登记</router-link>
          <router-link to="/display">候场大屏</router-link>
        </template>
        <!-- 工作人员端：按角色显示对应入口 -->
        <template v-else>
          <router-link v-if="canCheckin" to="/checkin">签到分流台</router-link>
          <router-link v-if="canWorkbench" to="/workbench">面试工作台</router-link>
          <router-link v-if="isAdmin()" to="/results">结果管理</router-link>
          <router-link v-if="canConsole" to="/console">控制台</router-link>
        </template>
      </nav>
      <div class="actions">
        <template v-if="isLoggedIn()">
          <el-tag size="small" type="info">{{ sessionName }}</el-tag>
          <el-button size="small" text @click="logout">退出</el-button>
        </template>
        <router-link v-else to="/login">
          <el-button size="small" type="primary">工作人员登录</el-button>
        </router-link>
      </div>
    </header>
    <main class="content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { clearSession, getRole, getSession, isAdmin, isLoggedIn } from '@/utils/auth';

const router = useRouter();
const role = computed(() => getRole());

const sessionName = computed(() => getSession()?.name ?? '');

const canCheckin = computed(() => ['admin', 'floating'].includes(role.value ?? ''));
const canWorkbench = computed(() => ['admin', 'interviewer'].includes(role.value ?? ''));
const canConsole = computed(() => ['admin', 'floating'].includes(role.value ?? ''));

function logout() {
  clearSession();
  router.push('/login');
}
</script>

<style scoped>
.app-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.top-bar {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 24px;
  height: 60px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  position: sticky;
  top: 0;
  z-index: 100;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.logo {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  object-fit: cover;
}
.title {
  font-size: 17px;
  font-weight: 600;
  color: #1f2937;
}
.nav {
  display: flex;
  gap: 16px;
  flex: 1;
}
.nav a {
  color: #4b5563;
  text-decoration: none;
  font-size: 14px;
  padding: 6px 10px;
  border-radius: 6px;
  transition: background 0.2s;
}
.nav a:hover {
  background: #f3f4f6;
  color: #1f2937;
}
.nav a.router-link-active {
  color: #2563eb;
  font-weight: 500;
}
.actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.content {
  flex: 1;
  padding: 24px;
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
}
</style>
