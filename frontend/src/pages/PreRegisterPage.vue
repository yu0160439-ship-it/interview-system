<template>
  <div class="pre-register-wrap">
    <div class="card">
      <div class="card-accent" />
      <div class="card-header">
        <img src="@/assets/logo-placeholder.svg" alt="社团Logo占位" class="logo" />
        <h1 class="title">面试叫号系统</h1>
        <p class="desc">
          {{ descText }}
        </p>
      </div>

      <!-- 选择一面/二面 -->
      <div v-if="pageState === 'chooseType'" class="body">
        <div class="grid-1">
          <button class="type-btn first" @click="goQuery('first')">
            <div class="type-title">一面签到</div>
            <div class="type-sub">第一次参加面试的同学点这里</div>
          </button>
          <button class="type-btn second" @click="goQuery('second')">
            <div class="type-title">二面签到</div>
            <div class="type-sub">一面通过、参加二面的同学点这里</div>
          </button>
        </div>

        <div class="qr-panel">
          <div class="qr-box">
            <img v-if="qrDataUrl" :src="qrDataUrl" alt="微信扫码填写报名信息" />
            <div v-else class="qr-placeholder">二维码生成中…</div>
          </div>
          <div class="qr-meta">
            <div class="qr-title">微信扫码填写信息</div>
            <div class="qr-hint">手机扫码，在手机上完成报名登记</div>
          </div>
        </div>
      </div>

      <!-- 查询 -->
      <div v-else-if="pageState === 'query'" class="body">
        <p class="hint">请输入姓名，已报名可直接签到，未报名请填写信息</p>
        <el-input
          v-model="queryName"
          size="large"
          placeholder="请输入姓名"
          :class="{ 'input-error': queryError }"
          clearable
          @keyup.enter="handleSearch"
        />
        <p v-if="queryError" class="error-text">{{ queryError }}</p>
        <el-button type="primary" size="large" class="full-btn gradient-primary" :loading="searching" @click="handleSearch">
          查询 / 签到
        </el-button>
      </div>

      <!-- 查到信息 -->
      <div v-else-if="pageState === 'found'" class="body">
        <div class="notice success">
          <el-icon><CircleCheck /></el-icon>
          <span>已查到 {{ matchedCandidates.length }} 条报名信息</span>
        </div>

        <div class="candidate-list">
          <div
            v-for="c in matchedCandidates"
            :key="c.id"
            class="candidate-item"
            :class="{ selected: selectedCandidate?.id === c.id }"
            @click="selectedCandidate = c"
          >
            <div class="row">
              <span class="name">{{ c.name }}</span>
              <el-tag :type="statusTagType(c.status)" size="small">{{ statusLabel(c.status) }}</el-tag>
            </div>
            <div class="info-grid">
              <div class="info-item">班级：<span>{{ c.className || '-' }}</span></div>
              <div class="info-item">性别：<span>{{ c.gender || '-' }}</span></div>
              <div class="info-item full">手机号：<span>{{ c.phone || '-' }}</span></div>
              <div class="info-item full">
                意向：{{ deptBadge(c.firstDepartment) }}
                <span v-if="c.secondDepartment && c.secondDepartment !== c.firstDepartment" class="arrow">→</span>
                <span v-if="c.secondDepartment && c.secondDepartment !== c.firstDepartment">{{ deptBadge(c.secondDepartment) }}</span>
              </div>
            </div>
          </div>
        </div>

        <el-button
          v-if="selectedCandidate"
          type="primary"
          size="large"
          class="full-btn"
          :class="checkInBtnClass(selectedCandidate.status)"
          :loading="submitting"
          @click="handleDirectCheckIn(selectedCandidate)"
        >
          {{ submitting ? '签到中...' : checkInBtnText(selectedCandidate.status) }}
        </el-button>

        <el-button size="large" class="full-btn plain" @click="backToQuery">返回重新查询</el-button>
      </div>

      <!-- 未查到，填表 -->
      <div v-else-if="pageState === 'notFound'" class="body">
        <div class="notice warn">
          <el-icon><WarningFilled /></el-icon>
          <span>未查到报名信息，请填写以下信息完成签到</span>
        </div>

        <el-form label-position="top" size="large">
          <div class="form-grid-2">
            <el-form-item label="班级 *" :error="errors.className">
              <el-input v-model="form.className" placeholder="如26网络1班" maxlength="30" @input="clearError('className')" />
            </el-form-item>
            <el-form-item label="姓名 *" :error="errors.name">
              <el-input v-model="form.name" placeholder="请输入姓名" maxlength="20" @input="clearError('name')" />
            </el-form-item>
          </div>

          <el-form-item label="性别 *" :error="errors.gender">
            <div class="choice-grid-2">
              <button v-for="g in ['男', '女']" :key="g" type="button" class="choice-btn" :class="{ active: form.gender === g }" @click="setField('gender', g)">
                {{ g }}
              </button>
            </div>
          </el-form-item>

          <el-form-item label="第一意向部门 *" :error="errors.firstDepartment">
            <div class="choice-grid-3">
              <button v-for="d in deptOptions" :key="d.value" type="button" class="dept-btn" :class="{ active: form.firstDepartment === d.value }" @click="setField('firstDepartment', d.value)">
                <div class="dept-icon" :style="{ background: d.gradient }">{{ d.short }}</div>
                <span>{{ d.label }}</span>
              </button>
            </div>
          </el-form-item>

          <el-form-item label="第二意向部门（选填）">
            <div class="choice-grid-3">
              <button v-for="d in deptOptions" :key="d.value" type="button" class="dept-btn" :class="{ active: form.secondDepartment === d.value }" @click="toggleSecondDept(d.value)">
                <div class="dept-icon sm" :style="{ background: d.gradient }">{{ d.short }}</div>
                <span>{{ d.label }}</span>
              </button>
            </div>
            <div v-if="form.secondDepartment && form.secondDepartment === form.firstDepartment" class="tip-warn">
              提示：第二意向建议与第一意向不同
            </div>
          </el-form-item>

          <el-form-item label="手机号 *" :error="errors.phone">
            <el-input v-model="form.phone" placeholder="请输入手机号" inputmode="tel" maxlength="11" @input="clearError('phone')" />
          </el-form-item>

          <el-form-item label="是否服从调剂 *" :error="errors.acceptAdjust">
            <div class="choice-grid-2">
              <button v-for="v in ['是', '否']" :key="v" type="button" class="choice-btn" :class="{ active: form.acceptAdjust === v }" @click="setField('acceptAdjust', v)">
                {{ v }}
              </button>
            </div>
          </el-form-item>

          <el-button type="primary" size="large" class="full-btn gradient-primary" :loading="submitting" @click="handleRegisterAndCheckIn(false)">
            提交并签到
          </el-button>
        </el-form>

        <el-button size="large" class="full-btn plain" @click="backToQuery">返回重新查询</el-button>
      </div>
    </div>

    <div class="footer">此系统由社团技术组开发</div>

    <!-- 重复报名确认 -->
    <el-dialog v-model="duplicateDialogOpen" title="重复报名提示" width="360px">
      <p>该手机号已报名，是否直接使用已有信息签到？</p>
      <div class="dup-info">
        <p>姓名：<b>{{ form.name }}</b></p>
        <p>手机号：<b>{{ form.phone }}</b></p>
      </div>
      <template #footer>
        <el-button @click="duplicateDialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleConfirmDuplicate">
          确认覆盖并签到
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import QRCode from 'qrcode';
import { checkInPublic, preRegisterPublic, searchByNamePublic, secondCheckInPublic } from '@/api/candidates';
import type { Candidate } from '@/types';

const route = useRoute();

const deptOptions = [
  { value: '部门A', label: '部门A', short: '技', gradient: 'linear-gradient(135deg,#3b82f6,#4f46e5)' },
  { value: '部门B', label: '部门B', short: '设', gradient: 'linear-gradient(135deg,#ec4899,#e11d48)' },
  { value: '部门C', label: '部门C', short: '联', gradient: 'linear-gradient(135deg,#10b981,#0d9488)' },
];

type PageState = 'chooseType' | 'query' | 'found' | 'notFound';

const pageState = ref<PageState>('chooseType');
const interviewType = ref<'first' | 'second'>('first');
const queryName = ref('');
const queryError = ref('');
const searching = ref(false);
const matchedCandidates = ref<Candidate[]>([]);
const selectedCandidate = ref<Candidate | null>(null);
const submitting = ref(false);
const duplicateDialogOpen = ref(false);
const qrDataUrl = ref('');

const emptyForm = () => ({
  className: '',
  name: '',
  gender: '',
  firstDepartment: '',
  secondDepartment: '',
  phone: '',
  acceptAdjust: '',
});
const form = ref(emptyForm());
const errors = ref<Record<string, string>>({});

const descText = computed(() => {
  switch (pageState.value) {
    case 'query': return interviewType.value === 'first' ? '一面签到' : '二面签到';
    case 'found': return '已查到报名信息';
    case 'notFound': return '填写信息完成签到';
    default: return '请选择面试场次';
  }
});

function goQuery(type: 'first' | 'second') {
  interviewType.value = type;
  pageState.value = 'query';
}

onMounted(() => {
  const t = route.query.type;
  if (t === 'first' || t === 'second') goQuery(t);

  const signInUrl = window.location.origin + '/';
  QRCode.toDataURL(signInUrl, { width: 200, margin: 1, errorCorrectionLevel: 'H' })
    .then((url) => { qrDataUrl.value = url; })
    .catch((err) => console.error('二维码生成失败', err));
});

async function handleSearch() {
  const name = queryName.value.trim();
  if (!name) {
    queryError.value = '请输入姓名';
    return;
  }
  queryError.value = '';
  searching.value = true;
  try {
    const results = await searchByNamePublic(name);
    if (results.length > 0) {
      matchedCandidates.value = results;
      selectedCandidate.value = results.length === 1 ? results[0] : null;
      pageState.value = 'found';
    } else {
      form.value = { ...emptyForm(), name };
      pageState.value = 'notFound';
    }
  } catch (err: unknown) {
    console.error('姓名查询失败', err);
    ElMessage.error((err as Error).message || '查询失败，请重试');
  } finally {
    searching.value = false;
  }
}

function backToQuery() {
  pageState.value = 'query';
  queryName.value = '';
  queryError.value = '';
  matchedCandidates.value = [];
  selectedCandidate.value = null;
  form.value = emptyForm();
  errors.value = {};
}

function setField(key: keyof typeof form.value, value: string) {
  form.value[key] = value;
  clearError(key);
}

function clearError(key: string) {
  if (errors.value[key]) {
    errors.value[key] = '';
  }
}

function toggleSecondDept(value: string) {
  form.value.secondDepartment = form.value.secondDepartment === value ? '' : value;
}

function validateForm(): boolean {
  const newErrors: Record<string, string> = {};
  if (!form.value.className.trim()) newErrors.className = '请输入班级';
  if (!form.value.name.trim()) newErrors.name = '请输入姓名';
  if (!form.value.gender) newErrors.gender = '请选择性别';
  if (!form.value.firstDepartment) newErrors.firstDepartment = '请选择第一意向部门';
  if (!form.value.phone.trim()) newErrors.phone = '请输入手机号';
  else if (!/^1\d{10}$/.test(form.value.phone.trim())) newErrors.phone = '请输入正确的11位手机号';
  if (!form.value.acceptAdjust) newErrors.acceptAdjust = '请选择是否服从调剂';
  errors.value = newErrors;
  return Object.keys(newErrors).length === 0;
}

async function handleRegisterAndCheckIn(force: boolean) {
  if (!validateForm()) return;
  submitting.value = true;
  try {
    const created = await preRegisterPublic({
      name: form.value.name.trim(),
      gender: form.value.gender,
      className: form.value.className.trim(),
      phone: form.value.phone.trim(),
      firstDepartment: form.value.firstDepartment,
      secondDepartment: form.value.secondDepartment || form.value.firstDepartment,
      acceptAdjust: form.value.acceptAdjust,
      force,
    });
    await checkInPublic(created.id);
    duplicateDialogOpen.value = false;
    ElMessage.success('签到成功，请在候场区等待叫号');
    form.value = emptyForm();
    pageState.value = 'chooseType';
  } catch (err: unknown) {
    console.error('报名签到失败', err);
    const e = err as Error & { statusCode?: number };
    if (e.statusCode === 409) {
      duplicateDialogOpen.value = true;
    } else {
      ElMessage.error(e.message || '提交失败，请重试');
    }
  } finally {
    submitting.value = false;
  }
}

function handleConfirmDuplicate() {
  void handleRegisterAndCheckIn(true);
}

async function handleDirectCheckIn(candidate: Candidate) {
  // 已进入二面流程
  if (['second_waiting', 'second_interviewing', 'second_completed'].includes(candidate.status)) {
    submitting.value = true;
    try {
      await secondCheckInPublic(candidate.id);
      ElMessage.success('二面签到成功，请等待安排');
      void handleSearch();
    } catch (err: unknown) {
      console.error('二面签到失败', err);
      ElMessage.error((err as Error).message || '二面签到失败，请重试');
    } finally {
      submitting.value = false;
    }
    return;
  }

  // 一面完成但尚未被安排二面
  if (candidate.status === 'first_completed') {
    ElMessage.warning('您还未被安排进入二面，请等待工作人员通知');
    return;
  }

  if (candidate.status !== 'pre_registered') {
    return;
  }

  submitting.value = true;
  try {
    await checkInPublic(candidate.id);
    ElMessage.success('签到成功，请在候场区等待叫号');
    void handleSearch();
  } catch (err: unknown) {
    console.error('签到失败', err);
    ElMessage.error((err as Error).message || '签到失败，请重试');
  } finally {
    submitting.value = false;
  }
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    pre_registered: '预报名', waiting: '已签到', interviewing: '面试中',
    first_completed: '一面完成', second_waiting: '二面候场', second_interviewing: '二面中',
    second_completed: '二面完成', final_admitted: '已录取', final_rejected: '未录取', absent: '缺考',
  };
  return map[status] || status;
}

function statusTagType(status: string): 'info' | 'success' | 'warning' | 'primary' | 'danger' {
  if (status === 'waiting' || status === 'second_completed' || status === 'final_admitted') return 'success';
  if (status === 'interviewing' || status === 'second_interviewing' || status === 'second_waiting') return 'warning';
  if (status === 'absent' || status === 'final_rejected') return 'danger';
  return 'info';
}

function deptBadge(dept: string | null): string {
  if (!dept) return '无';
  const found = deptOptions.find((d) => d.value === dept);
  return found ? found.label : dept;
}

function checkInBtnClass(status: string): string {
  if (status === 'pre_registered') return 'btn-green';
  if (status === 'second_waiting') return 'btn-blue-purple';
  return 'gradient-primary';
}

function checkInBtnText(status: string): string {
  if (status === 'pre_registered') return '一面签到';
  if (status === 'second_waiting') return '二面签到';
  if (status === 'second_interviewing') return '二面进行中';
  if (status === 'second_completed') return '二面已完成';
  if (status === 'first_completed') return '等待二面安排';
  return '查看状态';
}
</script>

<style scoped>
.pre-register-wrap {
  min-height: calc(100vh - 108px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  background: linear-gradient(135deg, #eef2ff 0%, #ffffff 50%, #ecfdf5 100%);
}
.card {
  width: 100%;
  max-width: 560px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 20px 40px rgba(99, 102, 241, 0.12);
  overflow: hidden;
}
.card-accent {
  height: 6px;
  background: linear-gradient(90deg, #6366f1, #a855f7, #10b981);
}
.card-header {
  text-align: center;
  padding: 28px 24px 12px;
}
.logo {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  object-fit: cover;
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.25);
  margin-bottom: 12px;
}
.title {
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
}
.desc {
  margin-top: 6px;
  font-size: 15px;
  color: #6b7280;
}
.body {
  padding: 20px 24px 28px;
}
.grid-1 {
  display: grid;
  gap: 16px;
}
.qr-panel {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 20px;
  border: 1px dashed #c7d2fe;
  border-radius: 12px;
  background: #f8faff;
}
.qr-box {
  width: 168px;
  height: 168px;
  background: #fff;
  border-radius: 12px;
  padding: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.12);
}
.qr-box img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.qr-placeholder {
  color: #9ca3af;
  font-size: 13px;
}
.qr-meta {
  text-align: center;
}
.qr-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}
.qr-hint {
  margin-top: 4px;
  font-size: 13px;
  color: #6b7280;
}
.type-btn {
  width: 100%;
  padding: 24px;
  border: none;
  border-radius: 12px;
  color: #fff;
  cursor: pointer;
  transition: transform 0.2s, filter 0.2s;
  text-align: left;
}
.type-btn:hover {
  transform: scale(1.02);
  filter: brightness(1.05);
}
.type-btn.first {
  background: linear-gradient(135deg, #10b981, #0d9488);
  box-shadow: 0 10px 20px rgba(16, 185, 129, 0.3);
}
.type-btn.second {
  background: linear-gradient(135deg, #a855f7, #4f46e5);
  box-shadow: 0 10px 20px rgba(168, 85, 247, 0.3);
}
.type-title {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 6px;
}
.type-sub {
  font-size: 14px;
  opacity: 0.9;
}
.hint {
  font-size: 14px;
  color: #6b7280;
  text-align: center;
  margin-bottom: 14px;
}
.input-error :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #f87171 inset;
}
.error-text {
  color: #f43f5e;
  font-size: 12px;
  margin-top: 6px;
}
.full-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  margin-top: 16px;
  border: none;
}
.full-btn.plain {
  margin-top: 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  color: #374151;
}
.gradient-primary {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
  color: #fff;
}
.btn-green {
  background: linear-gradient(135deg, #10b981, #0d9488);
  color: #fff;
}
.btn-blue-purple {
  background: linear-gradient(135deg, #3b82f6, #7c3aed);
  color: #fff;
}
.notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-radius: 8px;
  font-size: 14px;
  margin-bottom: 14px;
}
.notice.success {
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  color: #059669;
}
.notice.warn {
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #d97706;
}
.candidate-list {
  display: grid;
  gap: 10px;
  margin-bottom: 8px;
}
.candidate-item {
  border: 2px solid #e5e7eb;
  border-radius: 10px;
  padding: 14px;
  cursor: pointer;
  transition: all 0.2s;
}
.candidate-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
}
.candidate-item.selected {
  border-color: #6366f1;
  background: #eef2ff;
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.name {
  font-weight: 600;
  color: #1f2937;
}
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  font-size: 14px;
}
.info-item {
  color: #6b7280;
}
.info-item span {
  color: #374151;
}
.info-item.full {
  grid-column: span 2;
}
.arrow {
  color: #9ca3af;
  margin: 0 4px;
}
.form-grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.choice-grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  width: 100%;
}
.choice-grid-3 {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  width: 100%;
}
.choice-btn {
  height: 48px;
  border-radius: 10px;
  border: 2px solid #e5e7eb;
  background: #fff;
  font-size: 16px;
  font-weight: 500;
  color: #374151;
  cursor: pointer;
  transition: all 0.2s;
}
.choice-btn:hover {
  border-color: #cbd5e1;
}
.choice-btn.active {
  border-color: #6366f1;
  background: #eef2ff;
  color: #4338ca;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.15);
}
.dept-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 4px;
  border-radius: 12px;
  border: 2px solid #e5e7eb;
  background: #fff;
  cursor: pointer;
  transition: all 0.2s;
}
.dept-btn:hover {
  border-color: #cbd5e1;
}
.dept-btn.active {
  border-color: #6366f1;
  background: #eef2ff;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.15);
}
.dept-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
}
.dept-icon.sm {
  width: 30px;
  height: 30px;
  font-size: 13px;
}
.dept-btn span {
  font-size: 14px;
  font-weight: 500;
  color: #374151;
}
.dept-btn.active span {
  color: #4338ca;
}
.tip-warn {
  font-size: 12px;
  color: #d97706;
  margin-top: 6px;
}
.dup-info {
  margin-top: 10px;
  font-size: 14px;
  color: #4b5563;
  line-height: 1.8;
}
.footer {
  margin-top: 28px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
}
</style>
