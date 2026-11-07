<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <div class="card-header">
        <img src="@/assets/logo-placeholder.svg" alt="社团Logo占位" class="logo" />
        <h1 class="title">面试叫号系统</h1>
        <p class="desc">管理控制台登录</p>
      </div>
      <el-form @submit.prevent="handleSubmit">
        <el-form-item label="姓名">
          <el-input
            v-model="name"
            placeholder="请输入账号"
            autocomplete="username"
            :disabled="loading"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            show-password
            :disabled="loading"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-button
          type="primary"
          class="submit-btn"
          :loading="loading"
          @click="handleSubmit"
        >
          登录
        </el-button>
        <el-button class="back-btn" text @click="$router.push('/')">返回首页</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { interviewerLogin } from '@/api/candidates';
import { roleHome, setSession } from '@/utils/auth';

const router = useRouter();
const route = useRoute();
const name = ref('');
const password = ref('');
const loading = ref(false);

const redirect = (route.query.redirect as string) || '';

onMounted(() => {
  try {
    const raw = localStorage.getItem('admin_session');
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed?.name) {
        router.replace(redirect || roleHome(parsed.role));
      }
    }
  } catch (err) {
    console.warn('读取 admin_session 失败', err);
  }
});

async function handleSubmit() {
  if (!name.value.trim()) {
    ElMessage.error('请输入账号');
    return;
  }
  if (!password.value.trim()) {
    ElMessage.error('请输入密码');
    return;
  }
  loading.value = true;
  try {
    const res = await interviewerLogin({ name: name.value.trim(), password: password.value.trim() });
    if (res.success && res.name) {
      setSession({ name: res.name, groupName: res.groupName, role: res.role, isAdmin: res.isAdmin, isSecondRound: res.isSecondRound, token: res.token });
      ElMessage.success('登录成功');
      router.replace(redirect || roleHome(res.role));
      return;
    }
    ElMessage.error(res.message || '账号或密码错误');
  } catch (err) {
    console.error('登录请求失败', err);
    ElMessage.error('登录失败，请稍后重试');
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-wrap {
  min-height: 80vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}
.login-card {
  width: 100%;
  max-width: 420px;
  padding: 24px 8px;
}
.card-header {
  text-align: center;
  margin-bottom: 24px;
}
.logo {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  object-fit: cover;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
.title {
  margin-top: 12px;
  font-size: 22px;
  font-weight: 700;
  color: #1f2937;
}
.desc {
  margin-top: 4px;
  color: #6b7280;
  font-size: 14px;
}
.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  margin-top: 8px;
}
.back-btn {
  width: 100%;
  margin-top: 8px;
}
</style>
