<template>
  <div class="console-page">
    <div class="head">
      <h1>管理控制台</h1>
      <div class="head-right">
        <div class="user-info">
          <span class="user-label">当前用户：<b>{{ adminName }}</b></span>
          <el-tag v-if="role === 'admin'" size="small" type="primary">管理员</el-tag>
          <el-tag v-else-if="role === 'floating'" size="small" type="info">流动人员</el-tag>
          <el-tag v-else-if="role === 'interviewer'" size="small" type="success">一面面试官</el-tag>
          <el-tag v-else-if="role === 'second_interviewer'" size="small" type="warning">二面面试官</el-tag>
        </div>
        <el-button size="small" @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
          退出登录
        </el-button>
      </div>
    </div>

    <div class="grid">
      <el-card v-for="entry in entries" :key="entry.path" class="entry-card" shadow="hover">
        <div class="entry-icon"><el-icon :size="24"><component :is="entry.icon" /></el-icon></div>
        <h3>{{ entry.title }}</h3>
        <p>{{ entry.description }}</p>
        <el-button type="primary" class="entry-btn" @click="$router.push(entry.path)">
          进入 <el-icon><ArrowRight /></el-icon>
        </el-button>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { clearSession, getRole, getSession } from '@/utils/auth';
import type { UserRole } from '@/utils/auth';

const router = useRouter();

const adminName = ref('');
const role = ref<UserRole | null>(getRole());

const session = getSession();
if (session?.name) {
  adminName.value = session.name;
}

function handleLogout() {
  clearSession();
  router.replace('/login');
}

const entries = computed(() => {
  const list: { title: string; description: string; path: string; icon: string }[] = [];
  const r = role.value;
  if (r === 'admin' || r === 'floating') {
    list.push({ title: '签到分流台', description: '新生签到、分组分配与队列管理', path: '/checkin', icon: 'UserFilled' });
    list.push({ title: '一面工作台', description: '叫号、面试打分与状态流转', path: '/workbench?round=first', icon: 'Document' });
  }
  if (r === 'interviewer') {
    list.push({ title: '一面工作台', description: '叫号、面试打分与状态流转', path: '/workbench?round=first', icon: 'Document' });
  }
  if (r === 'admin' || r === 'second_interviewer') {
    list.push({ title: '二面工作台', description: '二面叫号、三席打分与状态流转', path: '/workbench?round=second', icon: 'Connection' });
  }
  if (r === 'admin') {
    list.push({ title: '结果管理', description: '面试结果筛选、统计与导出', path: '/results', icon: 'DataAnalysis' });
  }
  return list;
});
</script>

<style scoped>
.console-page {
  padding: 8px;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 24px;
}
.head h1 {
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
}
.head-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-label {
  font-size: 14px;
  color: #6b7280;
}
.user-label b {
  color: #374151;
  font-weight: 500;
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}
.entry-card {
  text-align: center;
  padding: 12px 8px;
}
.entry-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  background: #e0e7ff;
  color: #4f46e5;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 12px;
}
.entry-card h3 {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 6px;
}
.entry-card p {
  font-size: 14px;
  color: #6b7280;
  margin-bottom: 16px;
}
.entry-btn {
  width: 100%;
}
</style>
