<template>
  <div class="interview-page">
    <div v-if="!session" class="loading-text">加载中...</div>

    <div v-else class="flex flex-col gap-4">
      <!-- 顶部 -->
      <div class="topbar">
        <div>
          <h2>面试官工作台</h2>
          <p class="user-line">
            <el-icon><User /></el-icon>
            面试官：{{ session.name }}（{{ role === 'floating' ? '流动人员' : `${session.groupName}组` }}）
            <el-tag v-if="isAdmin" size="small" type="warning" effect="plain" class="ml2">管理员模式</el-tag>
            <el-tag v-else-if="role === 'floating'" size="small" type="info" effect="plain" class="ml2">流动人员·查看模式</el-tag>
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

      <!-- 队列状态切换 -->
      <div class="tab-group">
        <button type="button" class="tab-btn" :class="{ active: queueViewMode === 'active' }" @click="switchMode('active')">
          待面试 · {{ activeQueue.length }}
        </button>
        <button type="button" class="tab-btn" :class="{ active: queueViewMode === 'completed' }" @click="switchMode('completed')">
          已完成 · {{ completedQueue.length }}
        </button>
      </div>

      <!-- 桌面双栏 / 移动端堆叠 -->
      <div class="work-grid">
        <!-- 左列：队列 -->
        <div class="queue-col">
          <div class="panel">
            <div class="panel-head">
              <h3>{{ currentGroup }} 组{{ queueViewMode === 'active' ? '待面试' : '已完成' }}队列</h3>
              <span class="panel-sub">
                共 {{ displayedQueue.length }} 人
                <span v-if="currentInterviewing && queueViewMode === 'active'" class="amber">
                  · {{ currentInterviewing.status === 'pending_approval' ? '待审批' : '面试中' }} 1 人
                </span>
              </span>
            </div>
            <div class="queue-list">
              <div v-if="displayedQueue.length === 0 && !loading" class="empty-hint">
                {{ queueViewMode === 'active' ? '暂无待面试考生' : '暂无已完成考生' }}
              </div>
              <button
                v-for="c in pagedQueue" :key="c.id" type="button"
                class="queue-item"
                :class="{
                  selected: selectedCandidateId === c.id,
                  interviewing: c.status === 'interviewing',
                }"
                @click="selectedCandidateId = c.id"
              >
                <div class="qi-left">
                  <span class="qi-num">{{ c.groupNumber ?? '-' }}</span>
                  <div>
                    <div class="qi-name">
                      {{ c.name }}
                      <span v-if="c.globalNumber" class="qi-global">全场#{{ c.globalNumber }}</span>
                    </div>
                    <div class="qi-meta"><el-icon><School /></el-icon> {{ c.className || '未填写' }}</div>
                    <div class="qi-meta"><el-icon><OfficeBuilding /></el-icon> {{ c.firstDepartment || '未填写' }}</div>
                    <div v-if="queueViewMode === 'completed' && c.status === 'first_completed'" class="qi-score">
                      最终得分：{{ formatScore(c.averageScore) }}
                    </div>
                  </div>
                </div>
                <el-tag size="small" :type="statusTagType(c.status)">{{ statusLabel(c.status) }}</el-tag>
              </button>
            </div>
            <div v-if="totalPages > 1" class="pager">
              <span>第 {{ currentPage }}/{{ totalPages }} 页 · 共 {{ displayedQueue.length }} 人</span>
              <div>
                <el-button size="small" :disabled="currentPage === 1" @click="currentPage--">上一页</el-button>
                <el-button size="small" :disabled="currentPage === totalPages" @click="currentPage++">下一页</el-button>
              </div>
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
            <!-- 考生基本信息 -->
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
                <span><el-icon><User /></el-icon> {{ selectedCandidate.gender || '未填写' }}</span>
                <span><el-icon><School /></el-icon> {{ selectedCandidate.className || '未填写' }}</span>
                <span><el-icon><Phone /></el-icon> {{ selectedCandidate.phone || '未填写' }}</span>
                <el-tag v-if="selectedCandidate.firstDepartment" size="small" type="primary" effect="plain">第一意向：{{ selectedCandidate.firstDepartment }}</el-tag>
                <el-tag v-if="selectedCandidate.secondDepartment" size="small" type="danger" effect="plain">第二意向：{{ selectedCandidate.secondDepartment }}</el-tag>
                <el-tag size="small" :type="selectedCandidate.acceptAdjust === '是' ? 'success' : 'info'" effect="plain">
                  服从调剂：{{ selectedCandidate.acceptAdjust || '未填写' }}
                </el-tag>
              </div>
            </div>

            <!-- 打分情况 -->
            <div class="panel">
              <div class="panel-head">
                <h3>打分情况</h3>
                <span class="panel-sub">
                  已打分 {{ selectedCandidate.scoredCount }}/{{ selectedCandidate.groupInterviewerCount || 3 }} 人
                  <span v-if="selectedCandidate.scoredCount >= 3" class="green">三人已打满，一面自动完成</span>
                  <span class="indigo">当前平均分 {{ formatScore(selectedCandidate.averageScore) }}</span>
                </span>
              </div>
              <div v-if="(selectedCandidate.scores?.length ?? 0) === 0" class="empty-hint">
                <el-icon><Clock /></el-icon>
                <p>暂无面试官打分</p>
              </div>
              <div v-else class="score-list">
                <div v-for="s in selectedCandidate.scores" :key="s.id" class="score-item">
                  <div class="si-avatar"><el-icon><User /></el-icon></div>
                  <div class="si-body">
                    <div class="si-head">
                      <span>{{ s.interviewerName }}</span>
                      <span class="si-score">{{ formatScore(s.score) }}</span>
                    </div>
                    <p v-if="s.judgeComment" class="si-comment">
                      <el-icon><ChatDotRound /></el-icon>
                      <span>{{ s.judgeComment }}</span>
                    </p>
                  </div>
                </div>
              </div>
            </div>

            <!-- 三席位打分面板 -->
            <div class="panel">
              <div class="panel-head">
                <h3>三席打分</h3>
                <span class="panel-sub">三位面试官独立打分与备注，打满 3 人后一面自动结束</span>
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
                  v-if="selectedCandidate.status === 'waiting' && canOperate"
                  type="primary" :disabled="!!currentInterviewing" @click="handleStartInterview"
                >
                  <el-icon><VideoPlay /></el-icon> 叫号（开始面试）
                </el-button>
                <el-button
                  v-if="canEditScore"
                  type="primary" class="ml-auto" :loading="submitting" @click="handleSubmitScores"
                >
                  <el-icon><CircleCheck /></el-icon>
                  {{ submitting ? '提交中...' : '提交打分' }}
                </el-button>
                <el-tag v-if="!canOperate" type="info" effect="plain">
                  <el-icon><View /></el-icon> 流动人员·仅查看
                </el-tag>
                <el-tag v-if="selectedCandidate.status === 'absent'" effect="plain">已缺考，不可打分</el-tag>
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
import { fetchFirstRoundInterviewers, getGroupQueue, submitScores, updateStatus } from '@/api/candidates';
import type { SeatScoreInput } from '@/api/candidates';
import { clearSession, getSession } from '@/utils/auth';
import type { Candidate } from '@/types';

const router = useRouter();

const SESSION_KEY = 'admin_session';
const REFRESH_INTERVAL = 10000;
const ADMIN_NAME = 'admin';
const allGroups = ['A', 'B', 'C', 'D'];

type UserRole = 'admin' | 'floating' | 'interviewer';
type QueueViewMode = 'active' | 'completed';

function formatScore(v: number | null | undefined): string {
  if (v === null || v === undefined) return '-';
  return Number(v).toFixed(1);
}

function statusLabel(status: string): string {
  const map: Record<string, string> = {
    pre_registered: '预报名', waiting: '待面试', interviewing: '面试中',
    pending_approval: '待审批', first_completed: '已完成', absent: '缺考',
  };
  return map[status] || status;
}
function statusTagType(status: string): 'info' | 'success' | 'warning' | 'danger' | 'primary' {
  if (status === 'first_completed') return 'success';
  if (status === 'interviewing' || status === 'pending_approval') return 'warning';
  if (status === 'absent') return 'danger';
  return 'info';
}

const session = ref(getSession());
const activeGroup = ref('A');
const queue = ref<Candidate[]>([]);
const loading = ref(false);
const selectedCandidateId = ref<string | null>(null);
const queueViewMode = ref<QueueViewMode>('active');
const currentPage = ref(1);
const submitting = ref(false);
const interviewerGroups = ref<Record<string, string[]>>({});
const seats = ref<Array<{ name: string; score: number | null; comment: string }>>([]);
let pollTimer: ReturnType<typeof setInterval> | null = null;
let dataSource: EventSource | null = null;

const isAdmin = computed(() => session.value?.isAdmin === true || session.value?.name === ADMIN_NAME);
const role = computed<UserRole>(() => {
  const r = session.value?.role;
  return r === 'admin' || r === 'floating' ? r : 'interviewer';
});
const canViewAllGroups = computed(() => role.value === 'admin');
const canOperate = computed(() => role.value === 'admin' || role.value === 'interviewer');
const currentGroup = computed(() => canViewAllGroups.value ? activeGroup.value : (session.value?.groupName ?? 'A'));

const activeQueue = computed(() => queue.value.filter((c) => ['waiting', 'interviewing', 'pending_approval'].includes(c.status)));
const completedQueue = computed(() => queue.value.filter((c) => ['first_completed', 'absent'].includes(c.status)));
const displayedQueue = computed(() => queueViewMode.value === 'active' ? activeQueue.value : completedQueue.value);

const PAGE_SIZE = 5;
const totalPages = computed(() => Math.max(1, Math.ceil(displayedQueue.value.length / PAGE_SIZE)));
const pagedQueue = computed(() => displayedQueue.value.slice((currentPage.value - 1) * PAGE_SIZE, currentPage.value * PAGE_SIZE));

const currentInterviewing = computed(() => queue.value.find((c) => c.status === 'interviewing' || c.status === 'pending_approval') ?? null);
const selectedCandidate = computed(() => {
  if (selectedCandidateId.value) {
    return queue.value.find((c) => c.id === selectedCandidateId.value) ?? null;
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
  if (c.status === 'first_completed' || c.status === 'pending_approval') return false;
  return canOperate.value;
});
const isHighlight = computed(() => {
  const st = selectedCandidate.value?.status;
  return st === 'interviewing' || st === 'pending_approval';
});

function rebuildSeats() {
  const c = selectedCandidate.value;
  const existing = c?.scores ?? [];
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

watch([queueViewMode, currentGroup], () => {
  currentPage.value = 1;
});

async function fetchQueue(isSilent = false) {
  if (!session.value) return;
  if (!isSilent) loading.value = true;
  try {
    const data = await getGroupQueue(currentGroup.value, session.value.name);
    queue.value = [...data].sort((a, b) => (a.groupNumber ?? 0) - (b.groupNumber ?? 0));
  } catch (err) {
    console.error('获取分组队列失败', err);
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

/** 订阅业务变更 SSE：收到 data 事件静默刷新队列；轮询作为断线兜底。 */
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

if (session.value?.groupName) {
  activeGroup.value = session.value.groupName;
}

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
  void fetchQueue();
}

function switchMode(mode: QueueViewMode) {
  queueViewMode.value = mode;
}

function handleRefresh() {
  void fetchQueue();
}

function handleLogout() {
  clearSession();
  router.replace('/login');
  ElMessage.info('已退出登录');
}

async function handleStartInterview() {
  if (!selectedCandidate.value || !session.value) return;
  try {
    await updateStatus(selectedCandidate.value.id, 'interviewing', session.value.name);
    ElMessage.success(`已开始面试：${selectedCandidate.value.name}`);
    void fetchQueue(true);
  } catch (err) {
    console.error('开始面试失败', err);
    ElMessage.error((err as Error).message || '开始面试失败');
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
    ElMessage.success(filled.length >= 3 ? '三人评分已提交，一面自动完成' : '打分已提交');
    await fetchQueue(true);
    rebuildSeats();
  } catch (err) {
    console.error('提交打分失败', err);
    ElMessage.error((err as Error).message || '提交打分失败');
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.interview-page {
  padding: 4px;
}
.loading-text {
  padding: 24px;
  color: #6b7280;
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
.tab-btn:hover {
  color: #1e293b;
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
.panel-sub .amber {
  color: #d97706;
  margin-left: 8px;
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
.panel-sub .dim {
  color: #9ca3af;
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
.queue-item:hover {
  border-color: #cbd5e1;
  background: #f8fafc;
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
  display: flex;
  align-items: center;
  gap: 3px;
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
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px solid #f1f5f9;
  font-size: 13px;
  color: #6b7280;
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
.detail-meta > span {
  display: flex;
  align-items: center;
  gap: 4px;
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
.si-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #e0e7ff;
  color: #4f46e5;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
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
  display: flex;
  align-items: flex-start;
  gap: 4px;
  font-size: 13px;
  color: #4b5563;
  margin-top: 4px;
  word-break: break-all;
}
.score-label {
  display: flex;
  align-items: center;
  gap: 8px;
}
.score-label em {
  font-style: normal;
  font-size: 12px;
  color: #9ca3af;
  font-weight: 400;
}
.score-input {
  width: 100%;
}
.score-input :deep(.el-input__inner) {
  font-size: 22px;
  font-weight: 700;
  text-align: center;
  height: 48px;
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
