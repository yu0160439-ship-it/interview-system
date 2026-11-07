<template>
  <div class="display-page">
    <div class="bg-grid" />
    <div class="glow glow-1" />
    <div class="glow glow-2" />
    <div class="particles-layer">
      <span v-for="i in 24" :key="i" class="particle" :style="particleStyle(i)" />
    </div>

    <header class="header">
      <div class="header-inner">
        <div class="brand">
          <div class="logo-ring">
            <img src="@/assets/logo-placeholder.svg" alt="社团Logo占位" class="logo" />
          </div>
          <div>
            <h1>面试叫号系统</h1>
            <p class="sub">{{ data?.mode === 'second' ? '二面候场系统' : '面试候场系统' }}</p>
            <p class="dev">此系统由社团技术组开发</p>
          </div>
        </div>
        <div class="clock-area">
          <div class="clock">{{ nowTime }}</div>
          <div class="date">{{ nowDate }}</div>
        </div>
      </div>
      <div class="header-line" />
    </header>

    <div class="tip-bar">
      <span class="tip-tag">温馨提示</span>
      <span class="tip-text" :style="{ opacity: tipFading ? 0 : 1 }">{{ tips[tipIndex] }}</span>
    </div>

    <main class="main">
      <div v-if="loading || !data" class="loading-text">加载中...</div>
      <div v-else class="group-grid">
        <div v-for="g in data.groups" :key="g.groupName" class="group-card-wrap" :class="{ live: !!g.current }" :style="{ '--g': groupGradient(g.groupName) }">
          <div class="group-card">
            <div class="corner tl" /><div class="corner tr" /><div class="corner bl" /><div class="corner br" />
            <div v-if="g.current" class="live-tag">
              <span class="dot" />LIVE
            </div>
            <div class="group-head">
              <span class="group-name" :style="{ backgroundImage: groupGradient(g.groupName) }">{{ g.groupName }}</span>
              <span class="group-suffix">组</span>
              <span class="status-dot" :class="{ active: !!g.current }" />
            </div>
            <div class="current-area">
              <div class="current-label">正在面试</div>
              <div v-if="g.current" class="current-num" :class="{ fading: numFading[g.groupName] }">
                {{ displayNum[g.groupName] }}
              </div>
              <div v-else class="idle-num">— —</div>
            </div>
            <div class="next-area">
              <div class="next-label">NEXT →</div>
              <div v-if="g.waitingList.length > 0" class="next-row">
                <span class="next-num">{{ formatNumber(g.waitingList[0].globalNumber) }}</span>
                <span v-if="g.current?.status === 'pending_approval'" class="pending-tag">待审批</span>
              </div>
              <div v-else class="next-idle">—</div>
            </div>
          </div>
        </div>
      </div>
    </main>

    <footer class="footer">
      <div class="footer-line" />
      <div class="footer-inner">
        <div class="waiting-block">
          <span class="waiting-label">全场待面试</span>
          <div class="waiting-num">{{ totalWaiting }}</div>
          <span class="waiting-unit">人</span>
        </div>

        <div class="intro-card">
          <div class="intro-card-inner">
            <div class="intro-title">
              <el-icon :size="16" color="#22d3ee"><Tools /></el-icon>
              社团简介
            </div>
            <p class="intro-line1">学校学院名称 · <b>学生社团名称</b></p>
            <p class="intro-line2">校园知名社团</p>
            <div class="intro-grid">
              <p>宗旨：<b>提升自我，服务校园</b></p>
              <p>理念：<b>团结协作，共同成长</b></p>
            </div>
            <div class="tags">
              <span v-for="t in tags" :key="t">{{ t }}</span>
            </div>
          </div>
        </div>

        <div class="qr-card">
          <span class="qr-title">扫码签到</span>
          <div class="qr-box">
            <img v-if="qrDataUrl" :src="qrDataUrl" alt="扫码签到二维码" />
            <div class="qr-scan" />
          </div>
          <span class="qr-hint">微信扫码 · 信息登记</span>
        </div>
      </div>
      <div class="help-tip">💡 有问题请现场找黄色马甲或带工作证的师兄师姐</div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import QRCode from 'qrcode';
import { getBigScreenData } from '@/api/candidates';
import type { BigScreenData } from '@/types';

const data = ref<BigScreenData | null>(null);
const loading = ref(true);
const now = ref(new Date());
const tipIndex = ref(0);
const tipFading = ref(false);
const displayNum = reactive<Record<string, string>>({});
const numFading = reactive<Record<string, boolean>>({});
const qrDataUrl = ref('');
let prevNums: Record<string, string> = {};

const tags = ['技术分享', '社团活动', '校外实践', '团队建设', '技能学习', '培训交流', '社会实践', '能力提升'];

const quiet = [
  '请保持安静，给面试者一个良好的面试环境',
  '候场期间请保持安静，感谢您的配合',
];
const relax = [
  '别紧张，你已经准备得很好了',
  '放轻松，就当是一次普通的交流',
  '深呼吸，你可以的',
  '面试官都很和蔼，不用害怕',
  '把你最好的一面展示出来就行',
  '结果不重要，重要的是这个过程',
  '相信自己，你比想象中更优秀',
  '就当是来认识新朋友的',
  '慢慢来，不着急',
  '加油，我们在社团等你',
];
const tips = [
  quiet[0], relax[0], relax[1], relax[2], quiet[1], relax[3], relax[4], relax[5],
  quiet[0], relax[6], relax[7], relax[8], quiet[1], relax[9],
];

const groupGradients: Record<string, string> = {
  A: 'linear-gradient(135deg, #06b6d4, #3b82f6 50%, #8b5cf6)',
  B: 'linear-gradient(135deg, #10b981, #06b6d4 50%, #3b82f6)',
  C: 'linear-gradient(135deg, #f97316, #f59e0b 50%, #10b981)',
  D: 'linear-gradient(135deg, #8b5cf6, #ec4899 50%, #06b6d4)',
};
function groupGradient(name: string): string {
  return groupGradients[name] || groupGradients.A;
}

function formatNumber(n: number | null | undefined): string {
  if (n == null) return '--';
  return String(n).padStart(3, '0');
}

function pad2(n: number): string {
  return String(n).padStart(2, '0');
}

const nowTime = computed(() => `${pad2(now.value.getHours())}:${pad2(now.value.getMinutes())}:${pad2(now.value.getSeconds())}`);
const nowDate = computed(() => {
  const d = now.value;
  const weekdays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
  return `${d.getFullYear()}年${pad2(d.getMonth() + 1)}月${pad2(d.getDate())}日 ${weekdays[d.getDay()]}`;
});
const totalWaiting = computed(() => data.value?.totalWaiting ?? 0);

function particleStyle(i: number) {
  const left = (i * 41) % 100;
  const delay = (i * 0.7) % 12;
  const size = 2 + (i % 3);
  const dur = 14 + (i % 8);
  return {
    left: `${left}%`,
    top: `${(i * 37) % 100}%`,
    width: `${size}px`,
    height: `${size}px`,
    animationDelay: `${delay}s`,
    animationDuration: `${dur}s`,
  };
}

let pollTimer: ReturnType<typeof setInterval> | null = null;
let clockTimer: ReturnType<typeof setInterval> | null = null;
let tipTimer: ReturnType<typeof setInterval> | null = null;
let eventSource: EventSource | null = null;

async function fetchData() {
  try {
    const result = await getBigScreenData();
    data.value = result;
    for (const g of result.groups) {
      const newNum = g.current ? formatNumber(g.current.globalNumber) : '--';
      if (prevNums[g.groupName] !== newNum) {
        numFading[g.groupName] = true;
        setTimeout(() => {
          displayNum[g.groupName] = newNum;
          numFading[g.groupName] = false;
        }, 200);
        prevNums[g.groupName] = newNum;
      }
    }
  } catch (err) {
    console.error('大屏数据刷新失败', err);
  } finally {
    loading.value = false;
  }
}

function startPolling() {
  if (pollTimer) return;
  pollTimer = setInterval(() => void fetchData(), 8000);
}
function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

/** 连接模式变更 SSE：收到一面/二面切换推送立即刷新大屏；断线由 EventSource 自动重连，8 秒轮询继续兜底。 */
function connectModeStream() {
  if (eventSource) return;
  eventSource = new EventSource('/api/candidates/display/stream');
  eventSource.addEventListener('mode', () => { void fetchData(); });
  eventSource.addEventListener('data', () => { void fetchData(); });
  eventSource.onerror = () => { /* 忽略错误，依赖自动重连与轮询兜底 */ };
}

function closeModeStream() {
  if (eventSource) {
    eventSource.close();
    eventSource = null;
  }
}
function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    void fetchData();
    startPolling();
  } else {
    stopPolling();
  }
}

onMounted(() => {
  const signInUrl = window.location.origin;
  QRCode.toDataURL(signInUrl, { width: 220, margin: 1, errorCorrectionLevel: 'H' })
    .then((url) => { qrDataUrl.value = url; })
    .catch((err) => console.error('二维码生成失败', err));

  void fetchData();
  startPolling();
  connectModeStream();
  document.addEventListener('visibilitychange', handleVisibilityChange);

  clockTimer = setInterval(() => { now.value = new Date(); }, 1000);
  tipTimer = setInterval(() => {
    tipFading.value = true;
    setTimeout(() => {
      tipIndex.value = (tipIndex.value + 1) % tips.length;
      tipFading.value = false;
    }, 500);
  }, 5000);
});

onBeforeUnmount(() => {
  stopPolling();
  closeModeStream();
  document.removeEventListener('visibilitychange', handleVisibilityChange);
  if (clockTimer) clearInterval(clockTimer);
  if (tipTimer) clearInterval(tipTimer);
});
</script>

<style scoped>
.display-page {
  width: 100vw;
  min-height: 100vh;
  color: #fff;
  position: relative;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  background: radial-gradient(ellipse at top, #1e293b 0%, #0f172a 40%, #0a0a0a 100%);
}
.bg-grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(6, 182, 212, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(6, 182, 212, 0.03) 1px, transparent 1px);
  background-size: 60px 60px;
}
.glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(40px);
  animation: float-slow 12s ease-in-out infinite;
}
.glow-1 {
  top: 10%;
  left: 5%;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(6, 182, 212, 0.08) 0%, transparent 70%);
}
.glow-2 {
  bottom: 15%;
  right: 8%;
  width: 380px;
  height: 380px;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.06) 0%, transparent 70%);
  animation-direction: reverse;
  animation-duration: 15s;
}
.particles-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}
.particle {
  position: absolute;
  border-radius: 50%;
  background: rgba(34, 211, 238, 0.5);
  animation: float-slow 14s ease-in-out infinite alternate;
}
.header {
  position: relative;
  z-index: 10;
  padding: 14px 20px;
  flex-shrink: 0;
}
.header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  position: relative;
  flex-wrap: wrap;
}
.brand {
  display: flex;
  align-items: center;
  gap: 14px;
}
.logo-ring {
  position: relative;
  flex-shrink: 0;
  width: 52px;
  height: 52px;
  border-radius: 14px;
  padding: 2px;
  background: conic-gradient(from 0deg, #06b6d4, #3b82f6, #8b5cf6, #06b6d4);
  animation: spin-slow 10s linear infinite;
}
.logo {
  width: 100%;
  height: 100%;
  border-radius: 12px;
  object-fit: cover;
}
.brand h1 {
  font-size: 24px;
  font-weight: 700;
  background: linear-gradient(90deg, #fff, #a5f3fc 50%, #22d3ee);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  filter: drop-shadow(0 2px 8px rgba(6, 182, 212, 0.3));
}
.brand .sub {
  font-size: 13px;
  color: #22d3ee;
  letter-spacing: 2px;
  margin-top: 2px;
  font-family: 'SF Mono', 'JetBrains Mono', monospace;
}
.brand .dev {
  font-size: 12px;
  color: rgba(252, 211, 77, 0.9);
  margin-top: 4px;
  font-weight: 500;
  text-shadow: 0 0 8px rgba(251, 191, 36, 0.4);
}
.clock-area {
  text-align: right;
}
.clock {
  font-size: 38px;
  font-weight: 700;
  color: #22d3ee;
  font-family: 'SF Mono', 'JetBrains Mono', monospace;
  letter-spacing: 2px;
  text-shadow: 0 0 20px rgba(6, 182, 212, 0.5);
}
.date {
  font-size: 13px;
  color: #71717a;
  margin-top: 2px;
}
.header-line {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 1px;
  overflow: hidden;
}
.header-line::after {
  content: '';
  display: block;
  height: 100%;
  width: 50%;
  background: linear-gradient(90deg, transparent, rgba(6, 182, 212, 0.6), rgba(59, 130, 246, 0.6), transparent);
  animation: dataflow 4s linear infinite;
}
.tip-bar {
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 10px 16px;
  flex-shrink: 0;
}
.tip-tag {
  flex-shrink: 0;
  font-size: 13px;
  padding: 3px 10px;
  border-radius: 4px;
  background: rgba(6, 182, 212, 0.2);
  color: #67e8f9;
  font-weight: 600;
  border: 1px solid rgba(6, 182, 212, 0.4);
}
.tip-text {
  font-size: 16px;
  color: #e4e4e7;
  font-weight: 500;
  transition: opacity 0.5s;
  text-align: center;
}
.main {
  position: relative;
  z-index: 10;
  flex: 1;
  padding: 20px;
  min-height: 0;
}
.loading-text {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #71717a;
  font-size: 18px;
}
.group-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
  height: 100%;
}
.group-card-wrap {
  border-radius: 12px;
  overflow: hidden;
  padding: 1px;
  background: linear-gradient(135deg, #3f3f46, #52525b);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  transition: all 0.5s;
  opacity: 0.8;
}
.group-card-wrap.live {
  opacity: 1;
  transform: scale(1.02);
  background: var(--g);
  box-shadow: 0 0 40px rgba(6, 182, 212, 0.25), 0 0 80px rgba(59, 130, 246, 0.15);
  animation: card-glow 3s ease-in-out infinite;
}
.group-card {
  position: relative;
  border-radius: 11px;
  height: 100%;
  min-height: 220px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
  background: linear-gradient(145deg, rgba(30, 30, 35, 0.85), rgba(20, 20, 25, 0.9));
  backdrop-filter: blur(20px);
  overflow: hidden;
}
.corner {
  position: absolute;
  width: 16px;
  height: 16px;
  border-color: rgba(34, 211, 238, 0.5);
  border-style: solid;
}
.corner.tl { top: 8px; left: 8px; border-width: 2px 0 0 2px; }
.corner.tr { top: 8px; right: 8px; border-width: 2px 2px 0 0; }
.corner.bl { bottom: 8px; left: 8px; border-width: 0 0 2px 2px; }
.corner.br { bottom: 8px; right: 8px; border-width: 0 2px 2px 0; }
.live-tag {
  position: absolute;
  top: 10px;
  right: 16px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 700;
  color: #f87171;
  letter-spacing: 2px;
  z-index: 5;
}
.live-tag .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ef4444;
  animation: ping 1.2s ease-in-out infinite;
}
.group-head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.group-name {
  font-size: 30px;
  font-weight: 900;
  letter-spacing: 2px;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  font-family: 'SF Mono', 'JetBrains Mono', monospace;
}
.group-suffix {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.7);
  font-weight: 700;
}
.status-dot {
  margin-left: auto;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #52525b;
}
.status-dot.active {
  background: #34d399;
  box-shadow: 0 0 10px rgba(52, 211, 153, 0.8);
  animation: ping 1.5s ease-in-out infinite;
}
.current-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}
.current-label {
  font-size: 12px;
  color: rgba(103, 232, 249, 0.6);
  letter-spacing: 3px;
  margin-bottom: 8px;
  font-family: 'SF Mono', 'JetBrains Mono', monospace;
}
.current-num {
  font-size: 68px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: 3px;
  background: linear-gradient(180deg, #ffb547 0%, #ff6b35 50%, #ff4500 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  filter: drop-shadow(0 0 16px rgba(255, 107, 53, 0.5));
  font-family: 'SF Mono', 'JetBrains Mono', monospace;
  animation: num-pulse 2.5s ease-in-out infinite;
  transition: opacity 0.2s;
}
.current-num.fading {
  opacity: 0;
  animation: none;
}
.idle-num {
  font-size: 68px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: 3px;
  color: #3f3f46;
  font-family: 'SF Mono', 'JetBrains Mono', monospace;
}
.next-area {
  border-top: 1px solid rgba(113, 113, 122, 0.5);
  padding-top: 12px;
}
.next-label {
  color: #22d3ee;
  font-weight: 700;
  font-size: 13px;
  letter-spacing: 2px;
  margin-bottom: 6px;
  font-family: 'SF Mono', 'JetBrains Mono', monospace;
}
.next-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.next-num {
  font-size: 36px;
  font-weight: 700;
  color: #22d3ee;
  letter-spacing: 2px;
  font-family: 'SF Mono', 'JetBrains Mono', monospace;
  text-shadow: 0 0 12px rgba(6, 182, 212, 0.5);
}
.pending-tag {
  font-size: 12px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(234, 179, 8, 0.1);
  color: #facc15;
  border: 1px solid rgba(234, 179, 8, 0.4);
}
.next-idle {
  font-size: 30px;
  color: #3f3f46;
  font-weight: 700;
  font-family: 'SF Mono', 'JetBrains Mono', monospace;
}
.footer {
  position: relative;
  z-index: 10;
  padding: 16px 20px;
  flex-shrink: 0;
}
.footer-line {
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(6, 182, 212, 0.4), transparent);
  margin-bottom: 16px;
}
.footer-inner {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}
.waiting-block {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 140px;
}
.waiting-label {
  font-size: 13px;
  color: rgba(103, 232, 249, 0.7);
  letter-spacing: 2px;
  margin-bottom: 4px;
  font-family: 'SF Mono', 'JetBrains Mono', monospace;
}
.waiting-num {
  display: inline-block;
  font-size: 60px;
  font-weight: 900;
  line-height: 1;
  background: linear-gradient(180deg, #67e8f9 0%, #06b6d4 50%, #0891b2 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  filter: drop-shadow(0 0 16px rgba(6, 182, 212, 0.5));
  font-family: 'SF Mono', 'JetBrains Mono', monospace;
}
.waiting-unit {
  font-size: 16px;
  color: #a1a1aa;
}
.intro-card {
  flex: 1;
  max-width: 620px;
  border-radius: 12px;
  padding: 18px;
  padding: 1px;
  background: linear-gradient(135deg, #06b6d4, #3b82f6 50%, #8b5cf6);
  box-shadow: 0 0 24px rgba(6, 182, 212, 0.15);
}
.intro-card > * {
  /* 内部再铺一层背景 */
}
.intro-card {
  position: relative;
}
.intro-card-inner {
  border-radius: 11px;
  padding: 18px;
  background: linear-gradient(145deg, rgba(30, 30, 35, 0.8), rgba(20, 20, 25, 0.9));
}
.intro-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: #22d3ee;
  font-family: 'SF Mono', 'JetBrains Mono', monospace;
  margin-bottom: 10px;
}
.intro-line1 {
  font-size: 14px;
  color: #d4d4d8;
  margin-bottom: 4px;
}
.intro-line1 b {
  color: #fff;
  font-weight: 500;
}
.intro-line2 {
  font-size: 14px;
  color: #22d3ee;
  font-weight: 600;
  margin-bottom: 10px;
}
.intro-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 20px;
  margin-bottom: 12px;
}
.intro-grid p {
  font-size: 13px;
  color: #e4e4e7;
}
.intro-grid b {
  color: #22d3ee;
  font-weight: 600;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.tags span {
  font-size: 12px;
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid rgba(6, 182, 212, 0.4);
  color: #22d3ee;
  background: rgba(6, 182, 212, 0.05);
  transition: all 0.2s;
}
.tags span:hover {
  background: rgba(6, 182, 212, 0.15);
  box-shadow: 0 0 8px rgba(6, 182, 212, 0.3);
}
.qr-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  background: #fff;
  border-radius: 12px;
  padding: 14px;
  box-shadow: 0 0 24px rgba(6, 182, 212, 0.3);
  position: relative;
  flex-shrink: 0;
}
.qr-card::before {
  content: '';
  position: absolute;
  inset: -3px;
  border-radius: 14px;
  background: linear-gradient(135deg, #06b6d4, #3b82f6, #8b5cf6, #06b6d4);
  background-size: 300% 300%;
  animation: gradient-shift 4s ease infinite;
  filter: blur(6px);
  opacity: 0.7;
  z-index: -1;
}
.qr-title {
  font-size: 16px;
  font-weight: 700;
  background: linear-gradient(90deg, #0891b2, #2563eb);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.qr-box {
  position: relative;
  width: 200px;
  height: 200px;
}
.qr-box img {
  width: 100%;
  height: 100%;
}
.qr-scan {
  position: absolute;
  left: 0;
  right: 0;
  height: 20%;
  background: linear-gradient(180deg, transparent, rgba(6, 182, 212, 0.15), transparent);
  animation: scan-line 2.5s ease-in-out infinite;
}
.qr-hint {
  font-size: 12px;
  color: #71717a;
}
.help-tip {
  text-align: center;
  margin-top: 16px;
  font-size: 14px;
  color: rgba(252, 211, 77, 0.9);
  font-weight: 500;
  text-shadow: 0 0 10px rgba(251, 191, 36, 0.3);
}

@keyframes dataflow {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(300%); }
}
@keyframes spin-slow {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
@keyframes card-glow {
  0%, 100% { filter: brightness(1); }
  50% { filter: brightness(1.15); }
}
@keyframes num-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.03); }
}
@keyframes float-slow {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(30px, -20px); }
}
@keyframes gradient-shift {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}
@keyframes scan-line {
  0%, 100% { top: 0%; }
  50% { top: 100%; }
}
@keyframes ping {
  0% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.6); }
  70% { box-shadow: 0 0 0 10px rgba(239, 68, 68, 0); }
  100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0); }
}
</style>
