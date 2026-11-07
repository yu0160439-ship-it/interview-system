<template>
  <div class="second-page">
    <div v-if="!session" class="login-box">
      <div class="login-card">
        <h1>二面面试官工作台</h1>
        <el-input
          v-model="nameInput"
          size="large"
          placeholder="请输入你的姓名"
          @keyup.enter="handleLogin"
        />
        <el-input
          v-model="passwordInput"
          type="password"
          size="large"
          class="pwd-input"
          placeholder="管理员必填，其他角色可留空"
          autocomplete="current-password"
          show-password
          @keyup.enter="handleLogin"
        />
        <el-button type="primary" size="large" class="login-btn" :loading="logging" @click="handleLogin">登录</el-button>
        <p v-if="error" class="err-text">{{ error }}</p>
      </div>
    </div>

    <div v-else class="flex flex-col gap-4">
      <!-- 顶部 -->
      <div class="topbar">
        <div>
          <h2>二面面试官工作台</h2>
          <p class="user-line">
            面试官：{{ session.name }}
            <el-tag v-if="isAdmin" size="small" type="warning" effect="plain" class="ml2">管理员模式</el-tag>
            <el-tag v-else-if="session.groupName" size="small" type="info" effect="plain" class="ml2">{{ session.groupName }}组</el-tag>
          </p>
        </div>
        <div class="top-actions">
          <el-button size="small" :loading="loading" @click="handleRefresh">
            <el-icon><Refresh /></el-icon> 手动刷新
          </el-button>
          <el-button size="small" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon> 退出
          </el-button>
        </div>
      </div>

      <!-- 组别切换 -->
      <div v-if="canViewAllGroups" class="tab-group">
        <button v-for="g in allGroups" :key="g" type="button" class="tab-btn" :class="{ active: activeGroup === g }" @click="handleGroupChange(g)">
          {{ g }}组
        </button>
      </div>

      <div class="work-grid">
        <!-- 左列：队列 -->
        <div class="queue-col">
          <div class="panel">
            <div class="panel-head">
              <h3>{{ currentGroup }} 组二面队列</h3>
              <span class="panel-sub">共 {{ displayedQueue.length }} 人</span>
            </div>
            <div class="queue-list">
              <div v-if="displayedQueue.length === 0 && !loading" class="empty-hint">暂无待二面考生</div>
              <button
                v-for="c in displayedQueue" :key="c.id" type="button"
                class="queue-item"
                :class="{ selected: selectedCandidateId === c.id, interviewing: c.status === 'second_interviewing' }"
                @click="selectedCandidateId = c.id"
              >
                <div class="qi-left">
                  <span class="qi-num">{{ c.groupNumber ?? '-' }}</span>
                  <div>
                    <div class="qi-name">
                      {{ c.name }}
                      <span v-if="c.globalNumber" class="qi-global">全场#{{ c.globalNumber }}</span>
                    </div>
                    <div class="qi-meta">{{ c.className || '未填写' }}</div>
                    <div class="qi-meta">意向：{{ c.firstDepartment || '未填写' }}</div>
                    <div class="qi-score">一面平均分：{{ formatScore(c.averageScore) }}</div>
                  </div>
                </div>
                <el-tag size="small" :type="statusTagType(c.status)">{{ statusLabel(c.status) }}</el-tag>
              </button>
            </div>
          </div>
        </div>

        <!-- 右列：详情 + 打分 -->
        <div class="detail-col">
          <div v-if="!selectedCandidate" class="panel empty-detail">
            <el-icon :size="44" class="dim"><User /></el-icon>
            <p>请从左侧选择一位考生</p>
          </div>

          <template v-else>
            <!-- 考生信息 -->
            <div class="panel" :class="{ highlight: isHighlight }">
              <div class="detail-head">
                <div class="dh-left">
                  <span class="dh-number">{{ selectedCandidate.groupNumber ?? '-' }}</span>
                  <div>
                    <span class="dh-name">{{ selectedCandidate.name }}</span>
                    <div v-if="selectedCandidate.globalNumber" class="dh-global">全场序号：#{{ selectedCandidate.globalNumber }}</div>
                  </div>
                </div>
                <el-tag :type="statusTagType(selectedCandidate.status)" size="large">{{ statusLabel(selectedCandidate.status) }}</el-tag>
              </div>
              <div class="detail-meta">
                <span>{{ selectedCandidate.gender || '未填写' }}</span>
                <span>{{ selectedCandidate.className || '未填写' }}</span>
                <el-tag v-if="selectedCandidate.firstDepartment" size="small" type="primary" effect="plain">第一意向：{{ selectedCandidate.firstDepartment }}</el-tag>
                <el-tag v-if="selectedCandidate.secondDepartment" size="small" type="danger" effect="plain">第二意向：{{ selectedCandidate.secondDepartment }}</el-tag>
              </div>
            </div>

            <!-- 一面评分参考 -->
            <div class="panel">
              <div class="panel-head">
                <h3>一面评分参考</h3>
                <span class="panel-sub">平均分 {{ formatScore(selectedCandidate.averageScore) }} · {{ selectedCandidate.scoredCount }} 位面试官</span>
              </div>
              <div v-if="(selectedCandidate.scores?.length ?? 0) === 0" class="empty-hint">暂无一面评分</div>
              <div v-else class="score-list">
                <div v-for="s in selectedCandidate.scores" :key="s.id" class="score-item">
                  <div class="si-body">
                    <div class="si-head">
                      <span>{{ s.interviewerName }}</span>
                      <span class="si-score">{{ formatScore(s.score) }}</span>
                    </div>
                    <p v-if="s.judgeComment" class="si-comment">{{ s.judgeComment }}</p>
                  </div>
                </div>
              </div>
            </div>

            <!-- 二面打分情况 -->
            <div class="panel">
              <div class="panel-head">
                <h3>二面打分情况</h3>
                <span class="panel-sub">
                  已打分 {{ selectedCandidate.secondScoredCount ?? 0 }}/3 人
                  <span v-if="(selectedCandidate.secondScoredCount ?? 0) >= 3" class="green">三人已打满，二面自动完成</span>
                  <span class="indigo">当前二面平均分 {{ formatScore(selectedCandidate.secondAverageScore) }}</span>
                </span>
              </div>
              <div v-if="(selectedCandidate.secondScores?.length ?? 0) === 0" class="empty-hint">
                <el-icon><Clock /></el-icon>
                <p>暂无二面打分</p>
              </div>
              <div v-else class="score-list">
                <div v-for="s in selectedCandidate.secondScores" :key="s.id" class="score-item">
                  <div class="si-body">
                    <div class="si-head">
                      <span>{{ s.interviewerName }}</span>
                      <span class="si-score">{{ formatScore(s.score) }}</span>
                    </div>
                    <p v-if="s.judgeComment" class="si-comment">{{ s.judgeComment }}</p>
                  </div>
                </div>
              </div>
            </div>

            <!-- 三席位打分面板 -->
            <div class="panel">
              <div class="panel-head">
                <h3>二面三席打分</h3>
                <span class="panel-sub">三位面试官独立打分与备注，打满 3 人后二面自动结束</span>
              </div>
              <div v-if="seats.length === 0" class="empty-hint">本组暂未配置面试官席位</div>
              <div v-else class="seat-grid">
                <div v-for="(seat, idx) in seats" :key="seat.name" class="seat-card" :class="{ filled: seat.score !== null && seat.score !== undefined }">
                  <div class="seat-head">
                    <span class="seat-index">席位 {{ idx + 1 }}</span>
                    <span class="seat-name">{{ seat.name }}</span>
                  </div>
                  <el-input-number
                    v-model="seat.score"
                    :min="0" :max="10" :step="0.1" :precision="1"
                    controls-position="right" class="seat-score" placeholder="0-10"
                    :disabled="!canEditScore"
                  />
                  <el-input
                    v-model="seat.comment" type="textarea" :rows="2"
                    placeholder="请输入备注..." :disabled="!canEditScore"
                  />
                </div>
              </div>
              <div class="action-row">
                <el-button
                  v-if="selectedCandidate.status === 'second_waiting' && canOperate"
                  type="primary" :disabled="!!currentInterviewing" @click="handleStart"
                >
                  <el-icon><VideoPlay /></el-icon> 叫号（开始二面）
                </el-button>
                <el-button
                  v-if="canEditScore"
                  type="primary" class="ml-auto" :loading="submitting" @click="handleSubmitScores"
                >
                  <el-icon><CircleCheck /></el-icon>
                  {{ submitting ? '提交中...' : '提交二面打分' }}
                </el-button>
              </div>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  fetchFirstRoundInterviewers,
  getSecondRoundQueue,
  secondLogin,
  secondRoundStart,
  submitScores,
} from '@/api/candidates';
import type { SeatScoreInput } from '@/api/candidates';
import { clearSession, getSession, setSession } from '@/utils/auth';
import type { Candidate } from '@/types';

const router = useRouter();

const REFRESH_INTERVAL = 10000;
const ADMIN_NAME = 'admin';
const allGroups = ['A', 'B', 'C', 'D'];

interface SecondSession {
  name: string;
  role: string;
  groupName?: string;
  isAdmin: boolean;
}

function formatScore(v: number | null | undefined): string {
  if (v === null || v === undefined) return '-';
  return Number(v).toFixed(1);
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    second_waiting: '二面待面试',
    second_interviewing: '二面中',
    second_completed: '二面完成',
  };
  return map[status] || status;
}
function statusTagType(status: string): 'info' | 'success' | 'warning' {
  if (status === 'second_completed') return 'success';
  if (status === 'second_interviewing' || status === 'second_waiting') return 'warning';
  return 'info';
}

const session = ref<SecondSession | null>(getSession());
const nameInput = ref('');
const passwordInput = ref('');
const logging = ref(false);
const error = ref('');
const activeGroup = ref('A');
const waiting = ref<Candidate[]>([]);
const interviewing = ref<Candidate[]>([]);
const loading = ref(false);
const submitting = ref(false);
const selectedCandidateId = ref<string | null>(null);
const interviewerGroups = ref<Record<string, string[]>>({});
const seats = ref<Array<{ name: string; score: number | null; comment: string }>>([]);
let pollTimer: ReturnType<typeof setInterval> | null = null;
let dataSource: EventSource | null = null;

const isAdmin = computed(() => session.value?.name === ADMIN_NAME || session.value?.isAdmin === true);
const canViewAllGroups = computed(() => isAdmin.value);
const canOperate = computed(() => true);
const currentGroup = computed(() => canViewAllGroups.value ? activeGroup.value : (session.value?.groupName ?? 'A'));

const allQueue = computed(() => [...waiting.value, ...interviewing.value]);
const displayedQueue = computed(() =>
  allQueue.value.filter((c) => (c.groupName ?? 'A') === currentGroup.value),
);

const currentInterviewing = computed(() => interviewing.value[0] ?? null);
const selectedCandidate = computed(() => {
  if (selectedCandidateId.value) {
    return allQueue.value.find((c) => c.id === selectedCandidateId.value) ?? null;
  }
  return currentInterviewing.value;
});
const seatNames = computed<string[]>(() => {
  const g = selectedCandidate.value?.groupName ?? currentGroup.value;
  return interviewerGroups.value[g] ?? [];
});
const canEditScore = computed(() => {
  const c = selectedCandidate.value;
  if (!c) return false;
  if (c.status === 'second_completed') return false;
  return canOperate.value;
});
const isHighlight = computed(() => selectedCandidate.value?.status === 'second_interviewing');

function rebuildSeats() {
  const c = selectedCandidate.value;
  const existing = c?.secondScores ?? [];
  seats.value = seatNames.value.map((name) => {
    const hit = existing.find((s) => s.interviewerName === name);
    return {
      name,
      score: hit && hit.score !== null && hit.score !== undefined ? Number(hit.score) : null,
      comment: hit?.judgeComment ?? '',
    };
  });
}

watch([() => selectedCandidate.value?.id, currentGroup], rebuildSeats, { immediate: true });

async function handleLogin() {
  if (!nameInput.value.trim()) return;
  error.value = '';
  logging.value = true;
  try {
    const res = await secondLogin({ name: nameInput.value.trim(), password: passwordInput.value.trim() });
    if (!res.success) throw new Error(res.message || '登录失败');
    session.value = {
      name: res.name as string,
      role: res.role as string,
      groupName: res.groupName,
      isAdmin: res.isAdmin === true,
    };
    setSession({
      name: res.name as string,
      groupName: res.groupName,
      role: res.role as string,
      isAdmin: res.isAdmin === true,
      isSecondRound: true,
      token: res.token,
    });
    if (res.groupName) activeGroup.value = res.groupName;
    nameInput.value = '';
    passwordInput.value = '';
    void fetchQueue();
  } catch (err: unknown) {
    error.value = (err as Error).message;
  } finally {
    logging.value = false;
  }
}

function handleLogout() {
  clearSession();
  session.value = null;
  router.replace('/login');
}

async function fetchQueue(isSilent = false) {
  if (!session.value) return;
  if (!isSilent) loading.value = true;
  try {
    const res = await getSecondRoundQueue();
    waiting.value = res?.waiting ?? [];
    interviewing.value = res?.interviewing ?? [];
  } catch (err) {
    console.error('获取二面队列失败', err);
    if (!isSilent) ElMessage.error('获取队列失败，请刷新重试');
  } finally {
    loading.value = false;
  }
}

async function fetchInterviewers() {
  try {
    interviewerGroups.value = await fetchFirstRoundInterviewers();
    rebuildSeats();
  } catch (err) {
    console.error('获取面试官名单失败', err);
  }
}

function startPolling() {
  if (pollTimer) return;
  pollTimer = setInterval(() => void fetchQueue(true), REFRESH_INTERVAL);
}
function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

/** 订阅业务变更 SSE：收到 data 事件静默刷新二面队列；轮询作为断线兜底。 */
function connectDataStream() {
  if (dataSource) return;
  dataSource = new EventSource('/api/candidates/display/stream');
  dataSource.addEventListener('data', () => void fetchQueue(true));
  dataSource.onerror = () => { /* EventSource 自动重连，轮询兜底 */ };
}
function closeDataStream() {
  if (dataSource) { dataSource.close(); dataSource = null; }
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') startPolling();
  else stopPolling();
}

if (session.value?.groupName) activeGroup.value = session.value.groupName;
void fetchInterviewers();
void fetchQueue();
startPolling();
connectDataStream();
document.addEventListener('visibilitychange', handleVisibilityChange);

onBeforeUnmount(() => {
  stopPolling();
  closeDataStream();
  document.removeEventListener('visibilitychange', handleVisibilityChange);
});

function handleGroupChange(group: string) {
  activeGroup.value = group;
  selectedCandidateId.value = null;
}

function handleRefresh() {
  void fetchQueue();
}

async function handleStart() {
  if (!selectedCandidate.value || !session.value) return;
  try {
    await secondRoundStart(selectedCandidate.value.id, session.value.name);
    ElMessage.success(`已开始二面：${selectedCandidate.value.name}`);
    void fetchQueue(true);
  } catch (err) {
    console.error('开始二面失败', err);
    ElMessage.error((err as Error).message || '开始二面失败');
  }
}

async function handleSubmitScores() {
  if (!selectedCandidate.value || !session.value) return;
  const filled: SeatScoreInput[] = [];
  for (const seat of seats.value) {
    if (seat.score === null || seat.score === undefined || Number.isNaN(seat.score)) continue;
    if (seat.score < 0 || seat.score > 10) {
      ElMessage.error(`「${seat.name}」分数需在 0-10 之间`);
      return;
    }
    filled.push({ interviewerName: seat.name, score: Number(seat.score), judgeComment: seat.comment?.trim() || undefined });
  }
  if (filled.length === 0) {
    ElMessage.error('请至少填写一位面试官的分数');
    return;
  }
  submitting.value = true;
  try {
    await submitScores(selectedCandidate.value.id, session.value.name, filled);
    ElMessage.success(filled.length >= 3 ? '三人评分已提交，二面自动完成' : '二面打分已提交');
    await fetchQueue(true);
    rebuildSeats();
  } catch (err) {
    console.error('提交二面打分失败', err);
    ElMessage.error((err as Error).message || '提交打分失败');
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.second-page {
  padding: 4px;
}
.login-box {
  min-height: 70vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
.login-card {
  background: #fff;
  border-radius: 12px;
  padding: 32px;
  width: 100%;
  max-width: 400px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}
.login-card h1 {
  font-size: 20px;
  font-weight: 700;
  color: #1f2937;
  text-align: center;
  margin-bottom: 20px;
}
.login-btn {
  width: 100%;
  margin-top: 16px;
}
.pwd-input {
  margin-top: 12px;
}
.err-text {
  color: #f87171;
  font-size: 14px;
  text-align: center;
  margin-top: 16px;
}
.flex {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.topbar h2 {
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
}
.user-line {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #6b7280;
  margin-top: 6px;
  flex-wrap: wrap;
}
.ml2 {
  margin-left: 8px;
}
.top-actions {
  display: flex;
  gap: 8px;
}
.tab-group {
  display: inline-flex;
  gap: 2px;
  background: #f1f5f9;
  border-radius: 8px;
  padding: 4px;
  width: fit-content;
  flex-wrap: wrap;
}
.tab-btn {
  padding: 7px 16px;
  border: none;
  border-radius: 6px;
  background: transparent;
  font-size: 14px;
  font-weight: 500;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s;
}
.tab-btn.active {
  background: #fff;
  color: #4f46e5;
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}
.work-grid {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 16px;
  align-items: start;
}
@media (max-width: 1024px) {
  .work-grid {
    grid-template-columns: 1fr;
  }
}
.queue-col {
  position: sticky;
  top: 84px;
}
@media (max-width: 1024px) {
  .queue-col {
    position: static;
  }
}
.panel {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  margin-bottom: 16px;
}
.panel.highlight {
  border: 1px solid #fcd34d;
  box-shadow: 0 0 0 3px rgba(252, 211, 77, 0.2);
  background: rgba(255, 251, 235, 0.5);
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.panel-head h3 {
  font-size: 17px;
  font-weight: 700;
  color: #1f2937;
}
.panel-sub {
  font-size: 13px;
  color: #6b7280;
}
.panel-sub .green {
  color: #059669;
  font-weight: 500;
  margin-left: 8px;
}
.panel-sub .indigo {
  color: #4f46e5;
  font-weight: 600;
  margin-left: 8px;
}
.queue-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 60vh;
  overflow-y: auto;
}
.queue-item {
  width: 100%;
  text-align: left;
  padding: 12px;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
  cursor: pointer;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  transition: all 0.2s;
}
.queue-item.selected {
  border-color: #6366f1;
  background: #eef2ff;
  box-shadow: 0 0 0 1px #6366f1;
}
.queue-item.interviewing {
  box-shadow: 0 0 0 2px #fbbf24;
}
.qi-left {
  display: flex;
  gap: 10px;
  min-width: 0;
}
.qi-num {
  font-size: 18px;
  font-weight: 700;
  color: #374151;
  width: 32px;
  flex-shrink: 0;
  text-align: center;
}
.qi-name {
  font-weight: 500;
  color: #111827;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.qi-global {
  font-size: 12px;
  background: #e0e7ff;
  color: #4338ca;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 400;
}
.qi-meta {
  font-size: 12px;
  color: #6b7280;
  margin-top: 3px;
}
.qi-score {
  font-size: 12px;
  color: #059669;
  font-weight: 600;
  margin-top: 4px;
}
.empty-hint {
  text-align: center;
  color: #9ca3af;
  padding: 32px 0;
  font-size: 14px;
}
.empty-detail {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  color: #9ca3af;
}
.empty-detail p {
  margin-top: 10px;
}
.dim {
  opacity: 0.5;
}
.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
}
.dh-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.dh-number {
  font-size: 36px;
  font-weight: 900;
  color: #4f46e5;
}
.dh-name {
  font-size: 24px;
  font-weight: 700;
  color: #111827;
}
.dh-global {
  font-size: 12px;
  color: #4f46e5;
  font-weight: 500;
  margin-top: 2px;
}
.detail-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
  color: #4b5563;
}
.score-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.score-item {
  display: flex;
  gap: 10px;
  background: #f8fafc;
  border-radius: 8px;
  padding: 10px;
}
.si-body {
  flex: 1;
  min-width: 0;
}
.si-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.si-head span:first-child {
  font-weight: 500;
  color: #111827;
}
.si-score {
  font-size: 20px;
  font-weight: 700;
  color: #4f46e5;
}
.si-comment {
  font-size: 13px;
  color: #4b5563;
  margin-top: 4px;
  word-break: break-all;
}
.action-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}
.ml-auto {
  margin-left: auto;
}
.seat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
@media (max-width: 1024px) {
  .seat-grid {
    grid-template-columns: 1fr;
  }
}
.seat-card {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #f8fafc;
}
.seat-card.filled {
  border-color: #a5b4fc;
  background: #eef2ff;
}
.seat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.seat-index {
  font-size: 12px;
  color: #9ca3af;
}
.seat-name {
  font-weight: 600;
  color: #111827;
}
.seat-score {
  width: 100%;
}
.seat-score :deep(.el-input__inner) {
  font-weight: 700;
  text-align: center;
}
</style>
