<template>
  <div class="workbench-page">
    <!-- 一面 / 二面集成工作台：管理员可切换并联动大屏，其他角色按身份锁定对应场次 -->
    <div class="wb-tabs">
      <button
        v-for="r in rounds"
        :key="r.value"
        type="button"
        class="wb-tab"
        :class="{ active: activeRound === r.value }"
        :disabled="!canSwitch || switching"
        @click="switchRound(r.value)"
      >
        {{ r.label }}
      </button>
      <span v-if="!canSwitch" class="wb-lock-hint">当前为{{ activeRound === 'second' ? '二面' : '一面' }}阶段，仅可进入对应工作台</span>
    </div>

    <component :is="activeComponent" :key="activeRound" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import InterviewPage from '@/pages/InterviewPage.vue';
import SecondRoundPage from '@/pages/SecondRoundPage.vue';
import { getDisplayMode, toggleDisplayMode } from '@/api/candidates';
import { getSession, isAdmin } from '@/utils/auth';

type Round = 'first' | 'second';

const rounds: { value: Round; label: string }[] = [
  { value: 'first', label: '一面工作台' },
  { value: 'second', label: '二面工作台' },
];

const session = getSession();
const canSwitch = isAdmin();

const activeRound = ref<Round>('first');
const switching = ref(false);
let eventSource: EventSource | null = null;

const activeComponent = computed(() =>
  activeRound.value === 'second' ? SecondRoundPage : InterviewPage,
);

function applyMode(mode: string) {
  activeRound.value = mode === 'second' ? 'second' : 'first';
}

/** 订阅场次变更推送：admin 切换后，已打开的面试官界面实时跟随到对应工作台。 */
function connectModeStream() {
  if (eventSource) return;
  eventSource = new EventSource('/api/candidates/display/stream');
  eventSource.addEventListener('mode', (ev) => {
    try {
      const payload = JSON.parse((ev as MessageEvent).data);
      if (payload?.mode) applyMode(payload.mode);
    } catch {
      // 解析失败忽略，依赖进入时拉取与下次推送
    }
  });
  eventSource.onerror = () => { /* EventSource 自动重连 */ };
}

function closeModeStream() {
  if (eventSource) {
    eventSource.close();
    eventSource = null;
  }
}

onMounted(async () => {
  let mode: Round = 'first';
  try {
    const res = await getDisplayMode(session?.name ?? 'admin');
    mode = res.mode === 'second' ? 'second' : 'first';
  } catch {
    // 拉取失败保持默认一面
  }
  if (canSwitch) {
    // 管理员可按链接指定场次，并联动大屏
    const q = new URLSearchParams(window.location.search).get('round');
    activeRound.value = q === 'first' || q === 'second' ? q : mode;
  } else {
    // 面试官跟随当前全局场次，不可越权进入另一场次工作台
    activeRound.value = mode;
  }
  connectModeStream();
});

onBeforeUnmount(() => {
  closeModeStream();
});

async function switchRound(target: Round) {
  if (!canSwitch || target === activeRound.value || switching.value) return;
  switching.value = true;
  const prev = activeRound.value;
  activeRound.value = target;
  try {
    await toggleDisplayMode(session?.name ?? 'admin', target);
    ElMessage.success(target === 'second' ? '已切换到二面，大屏同步二面视图' : '已切换到一面，大屏同步一面视图');
  } catch (err) {
    activeRound.value = prev;
    ElMessage.error(err instanceof Error ? err.message : '切换失败，已还原');
  } finally {
    switching.value = false;
  }
}
</script>

<style scoped>
.workbench-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.wb-tabs {
  display: flex;
  align-items: center;
  gap: 8px;
}

.wb-tab {
  border: 1px solid #d9dce3;
  background: #fff;
  color: #4b5563;
  border-radius: 8px;
  padding: 8px 18px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.wb-tab.active {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
  font-weight: 500;
}

.wb-tab:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.wb-lock-hint {
  margin-left: 8px;
  font-size: 13px;
  color: #9ca3af;
}
</style>
