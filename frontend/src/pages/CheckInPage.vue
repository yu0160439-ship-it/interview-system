<template>
  <div class="checkin-page">
    <div v-if="!session" class="loading-text">加载中...</div>

    <template v-else>
      <!-- 顶部 -->
      <div class="topbar">
        <div>
          <h2>签到分流台</h2>
          <p class="user-line">
            <el-icon><User /></el-icon>
            操作员：{{ session.name }}
            <el-tag v-if="isAdmin" size="small" type="warning" effect="plain">管理员</el-tag>
            <el-tag v-else size="small" type="info" effect="plain">流动人员</el-tag>
            <span class="dim">· 共 {{ total }} 名报名考生</span>
          </p>
        </div>
        <div class="top-actions">
          <el-radio-group v-if="isAdmin" v-model="displayMode" size="small" @change="handleToggleDisplayMode">
            <el-radio-button label="first">一面大屏</el-radio-button>
            <el-radio-button label="second">二面大屏</el-radio-button>
          </el-radio-group>
          <el-button size="small" :loading="loading" @click="loadList()">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
          <el-button size="small" @click="logout">
            <el-icon><SwitchButton /></el-icon> 退出
          </el-button>
        </div>
      </div>

      <!-- 组别统计 -->
      <div class="group-stats">
        <div v-for="g in groupCounts" :key="g.groupName" class="gstat" :class="'g-' + g.groupName">
          <span class="gs-name">{{ g.groupName }} 组</span>
          <span class="gs-count">{{ g.count }}</span>
        </div>
        <div class="gstat g-total">
          <span class="gs-name">总计</span>
          <span class="gs-count">{{ total }}</span>
        </div>
      </div>

      <!-- 快速签到 -->
      <div class="panel">
        <div class="panel-head"><h3>快速签到</h3></div>
        <el-input
          v-model="checkInKeyword"
          placeholder="输入姓名搜索考生，选中后一键签到"
          clearable
          class="mb12"
          @input="onCheckInSearch"
        />
        <div v-if="checkInResults.length > 0" class="quick-results">
          <div
            v-for="c in checkInResults" :key="c.id"
            class="quick-item"
            :class="{ disabled: c.status !== 'pre_registered' }"
            @click="selectAndCheckIn(c)"
          >
            <div class="qi-left">
              <span class="qi-name">{{ c.name }}</span>
              <span class="qi-sub">{{ c.className || '未填写' }} · {{ c.firstDepartment || '未填写' }}</span>
            </div>
            <el-tag size="small" :type="c.status === 'pre_registered' ? 'primary' : 'info'">
              {{ statusLabel(c.status) }}
            </el-tag>
          </div>
        </div>
        <div v-else-if="checkInKeyword && checkInSearched" class="empty-hint">未找到匹配考生</div>
      </div>

      <!-- 筛选 -->
      <div class="panel">
        <div class="filters">
          <el-input v-model="filters.keyword" placeholder="搜索姓名/班级/手机" clearable class="f-keyword" @keyup.enter="doSearch" @clear="doSearch" />
          <el-select v-model="filters.status" placeholder="状态" clearable class="f-item" @change="doSearch">
            <el-option v-for="(label, val) in STATUS_OPTIONS" :key="val" :label="label" :value="val" />
          </el-select>
          <el-select v-model="filters.groupName" placeholder="组别" clearable class="f-item" @change="doSearch">
            <el-option v-for="g in ['A', 'B', 'C', 'D']" :key="g" :label="g + '组'" :value="g" />
          </el-select>
          <el-button type="primary" @click="doSearch"><el-icon><Search /></el-icon> 搜索</el-button>
        </div>
        <div class="filter-tags" v-if="activeFilterCount">
          <el-tag v-for="t in activeFilterTags" :key="t.k" closable @close="clearFilter(t.k)">
            {{ t.label }}：{{ t.value }}
          </el-tag>
        </div>
      </div>

      <!-- 操作按钮：手动签到 / 手动添加 / CSV 导入 -->
      <div class="toolbar">
        <el-button type="primary" plain @click="openManualDialog('checkin')">
          <el-icon><EditPen /></el-icon> 手动签到（录入并签到）
        </el-button>
        <el-button type="primary" plain @click="openManualDialog('add')">
          <el-icon><Plus /></el-icon> 手动添加
        </el-button>
        <el-upload v-if="isAdmin" :show-file-list="false" accept=".csv" :before-upload="beforeCsvUpload">
          <el-button plain><el-icon><Upload /></el-icon> CSV 导入</el-button>
        </el-upload>
        <el-button plain @click="downloadTemplate"><el-icon><Download /></el-icon> 下载模板</el-button>
      </div>

      <!-- 列表 -->
      <div class="panel">
        <div class="panel-head">
          <h3>考生列表</h3>
          <span class="panel-sub">第 {{ page }}/{{ totalPages }} 页 · 共 {{ total }} 人</span>
        </div>
        <el-table :data="items" v-loading="loading" size="default" :max-height="560">
          <el-table-column prop="globalNumber" label="序号" width="70" align="center">
            <template #default="{ row }">#{{ row.globalNumber ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="name" label="姓名" width="100" />
          <el-table-column prop="className" label="班级" min-width="110" show-overflow-tooltip />
          <el-table-column prop="phone" label="手机号" width="130" />
          <el-table-column prop="firstDepartment" label="第一意向" width="100" />
          <el-table-column prop="secondDepartment" label="第二意向" width="100" />
          <el-table-column label="组别" width="70" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.groupName" size="small">{{ row.groupName }}组</el-tag>
              <span v-else class="dim">-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="220" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'pre_registered'">
                <el-button size="small" type="primary" link @click="doCheckIn(row)">签到</el-button>
                <el-button size="small" type="primary" link @click="doAssign(row.id, row.name)">分配组别</el-button>
              </template>
              <template v-else-if="row.status === 'waiting'">
                <el-button size="small" type="primary" link @click="doAssign(row.id, row.name)">分配组别</el-button>
                <el-button size="small" link @click="openReassign(row)">重新分组</el-button>
              </template>
              <template v-else-if="row.status === 'first_completed'">
                <el-button v-if="isAdmin" size="small" type="warning" link @click="doMoveToSecond(row)">移入二面</el-button>
              </template>
              <template v-else-if="row.status === 'second_waiting'">
                <el-button size="small" type="warning" link @click="openAssignSecond(row)">分配二面组</el-button>
              </template>
              <template v-else>
                <span class="dim">-</span>
              </template>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="totalPages > 1" class="pager">
          <el-button size="small" :disabled="page <= 1" @click="changePage(page - 1)">上一页</el-button>
          <el-button size="small" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</el-button>
        </div>
      </div>
    </template>

    <!-- 手动签到/添加对话框 -->
    <el-dialog v-model="manualDialogVisible" :title="manualDialogMode === 'checkin' ? '手动签到' : '手动添加'" width="480px">
      <el-form :model="manualForm" label-width="90px">
        <el-form-item label="姓名" required><el-input v-model="manualForm.name" placeholder="请输入姓名" /></el-form-item>
        <el-form-item label="性别" required>
          <el-select v-model="manualForm.gender" style="width: 100%">
            <el-option label="男" value="男" /><el-option label="女" value="女" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级" required><el-input v-model="manualForm.className" placeholder="如：软件技术1班" /></el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="manualForm.phone" placeholder="11 位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="第一意向" required>
          <el-select v-model="manualForm.firstDepartment" style="width: 100%">
            <el-option v-for="d in DEPARTMENTS" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="第二意向">
          <el-select v-model="manualForm.secondDepartment" clearable style="width: 100%">
            <el-option v-for="d in DEPARTMENTS" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="服从调剂">
          <el-radio-group v-model="manualForm.acceptAdjust">
            <el-radio label="是" /><el-radio label="否" />
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="manualDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="manualSubmitting" @click="submitManual">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配组别对话框 -->
    <el-dialog v-model="assignVisible" :title="`分配组别 - ${assignTarget?.name || ''}`" width="360px">
      <el-radio-group v-model="assignGroupValue">
        <el-radio-button v-for="g in ['A', 'B', 'C', 'D']" :key="g" :label="g" />
      </el-radio-group>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assignSubmitting" @click="confirmAssign">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重新分组对话框 -->
    <el-dialog v-model="reassignVisible" :title="`重新分组 - ${reassignTarget?.name || ''}`" width="360px">
      <el-radio-group v-model="reassignGroupValue">
        <el-radio-button v-for="g in ['A', 'B', 'C', 'D']" :key="g" :label="g" />
      </el-radio-group>
      <template #footer>
        <el-button @click="reassignVisible = false">取消</el-button>
        <el-button type="primary" :loading="reassignSubmitting" @click="confirmReassign">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配二面组对话框 -->
    <el-dialog v-model="assignSecondVisible" :title="`分配二面组 - ${assignSecondTarget?.name || ''}`" width="360px">
      <el-radio-group v-model="assignSecondValue">
        <el-radio-button v-for="g in ['A', 'B', 'C', 'D']" :key="g" :label="g" />
      </el-radio-group>
      <template #footer>
        <el-button @click="assignSecondVisible = false">取消</el-button>
        <el-button type="primary" :loading="assignSecondSubmitting" @click="confirmAssignSecond">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  searchCandidates, getGroupCounts, checkInById, assignGroup, reassignGroup,
  moveToSecondRound, assignSecondGroup, createPreRegister,
  batchImportCandidates, exportCsvUrl, getDisplayMode, toggleDisplayMode,
} from '@/api/candidates';
import { clearSession, getSession } from '@/utils/auth';
import type { Candidate, GroupCountInfo } from '@/types';

const router = useRouter();
const SESSION_KEY = 'admin_session';
const ADMIN_NAME = 'admin';
const PAGE_SIZE = 20;
const REFRESH_INTERVAL = 15000;
const DEPARTMENTS = ['部门A', '部门B', '部门C'];

const STATUS_OPTIONS: Record<string, string> = {
  pre_registered: '预报名', waiting: '待面试', interviewing: '面试中',
  pending_approval: '待审批', first_completed: '一面完成', second_waiting: '二面待面',
  second_interviewing: '二面中', second_completed: '二面完成', final_admitted: '已录取', final_rejected: '未录取', absent: '缺考',
};

function statusLabel(s: string): string {
  return STATUS_OPTIONS[s] || s;
}
function statusTagType(s: string): 'info' | 'success' | 'warning' | 'danger' | 'primary' {
  if (s === 'final_admitted' || s === 'second_completed') return 'success';
  if (s === 'final_rejected' || s === 'absent') return 'danger';
  if (s === 'pending_approval' || s === 'interviewing' || s === 'second_interviewing') return 'warning';
  if (s === 'first_completed' || s === 'pre_registered') return 'primary';
  return 'info';
}

const session = ref(getSession());
const isAdmin = computed(() => session.value?.name === ADMIN_NAME);
const displayMode = ref<'first' | 'second'>('first');

async function loadDisplayMode() {
  if (!session.value) return;
  try {
    const res = await getDisplayMode(session.value.name);
    displayMode.value = res.mode === 'second' ? 'second' : 'first';
  } catch { /* ignore */ }
}

async function handleToggleDisplayMode(mode: 'first' | 'second') {
  if (!session.value) return;
  try {
    await toggleDisplayMode(session.value.name, mode);
    ElMessage.success(mode === 'second' ? '大屏已切换到二面视图' : '大屏已切换到一面视图');
  } catch (err) {
    ElMessage.error((err as Error).message || '切换大屏模式失败');
    void loadDisplayMode();
  }
}

const items = ref<Candidate[]>([]);
const total = ref(0);
const page = ref(1);
const totalPages = ref(1);
const loading = ref(false);
const groupCounts = ref<GroupCountInfo[]>([]);

const filters = ref<Record<string, string>>({ keyword: '', status: '', groupName: '' });

const checkInKeyword = ref('');
const checkInResults = ref<Candidate[]>([]);
const checkInSearched = ref(false);
let searchTimer: ReturnType<typeof setTimeout> | null = null;

const manualDialogVisible = ref(false);
const manualDialogMode = ref<'checkin' | 'add'>('checkin');
const manualSubmitting = ref(false);
const manualForm = ref({
  name: '', gender: '男', className: '', phone: '',
  firstDepartment: '', secondDepartment: '', acceptAdjust: '是',
});

const assignVisible = ref(false);
const assignSubmitting = ref(false);
const assignTarget = ref<Candidate | null>(null);
const assignGroupValue = ref('A');

const reassignVisible = ref(false);
const reassignSubmitting = ref(false);
const reassignTarget = ref<Candidate | null>(null);
const reassignGroupValue = ref('A');

const assignSecondVisible = ref(false);
const assignSecondSubmitting = ref(false);
const assignSecondTarget = ref<Candidate | null>(null);
const assignSecondValue = ref('A');

let pollTimer: ReturnType<typeof setInterval> | null = null;
let dataSource: EventSource | null = null;

const activeFilterCount = computed(() => [filters.value.status, filters.value.groupName].filter(Boolean).length + (filters.value.keyword ? 1 : 0));
const activeFilterTags = computed(() => {
  const tags: { k: string; label: string; value: string }[] = [];
  if (filters.value.keyword) tags.push({ k: 'keyword', label: '关键词', value: filters.value.keyword });
  if (filters.value.status) tags.push({ k: 'status', label: '状态', value: STATUS_OPTIONS[filters.value.status] || filters.value.status });
  if (filters.value.groupName) tags.push({ k: 'groupName', label: '组别', value: filters.value.groupName + '组' });
  return tags;
});

async function loadList(isSilent = false) {
  if (!session.value) return;
  if (!isSilent) loading.value = true;
  try {
    const res = await searchCandidates({
      page: page.value,
      pageSize: PAGE_SIZE,
      keyword: filters.value.keyword || undefined,
      status: filters.value.status || undefined,
      groupName: filters.value.groupName || undefined,
      sortBy: 'createdAt',
      sortOrder: 'desc',
    });
    items.value = res.items;
    total.value = res.total;
    totalPages.value = Math.max(1, Math.ceil(res.total / PAGE_SIZE));
  } catch (err) {
    console.error('加载列表失败', err);
    if (!isSilent) ElMessage.error('加载列表失败');
  } finally {
    loading.value = false;
  }
}

async function loadGroupCounts() {
  try {
    groupCounts.value = await getGroupCounts();
  } catch (err) {
    console.error('获取组统计失败', err);
  }
}

function startPolling() {
  if (pollTimer) return;
  pollTimer = setInterval(() => {
    void loadList(true);
    void loadGroupCounts();
  }, REFRESH_INTERVAL);
}
function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
}

/** 订阅业务变更 SSE：收到 data 事件静默刷新列表与组统计；轮询作为断线兜底。 */
function connectDataStream() {
  if (dataSource) return;
  dataSource = new EventSource('/api/candidates/display/stream');
  dataSource.addEventListener('data', () => {
    void loadList(true);
    void loadGroupCounts();
  });
  dataSource.onerror = () => { /* EventSource 自动重连，轮询兜底 */ };
}
function closeDataStream() {
  if (dataSource) { dataSource.close(); dataSource = null; }
}
function onVisibility() {
  if (document.visibilityState === 'visible') startPolling();
  else stopPolling();
}

void loadList();
void loadGroupCounts();
void loadDisplayMode();
startPolling();
connectDataStream();
document.addEventListener('visibilitychange', onVisibility);
onBeforeUnmount(() => {
  stopPolling();
  closeDataStream();
  document.removeEventListener('visibilitychange', onVisibility);
  if (searchTimer) clearTimeout(searchTimer);
});

function doSearch() {
  page.value = 1;
  void loadList();
}
function changePage(p: number) {
  page.value = p;
  void loadList();
}
function clearFilter(k: string) {
  if (k === 'keyword') filters.value.keyword = '';
  if (k === 'status') filters.value.status = '';
  if (k === 'groupName') filters.value.groupName = '';
  doSearch();
}
function logout() {
  clearSession();
  router.replace('/login');
}

function onCheckInSearch() {
  if (searchTimer) clearTimeout(searchTimer);
  if (!checkInKeyword.value.trim()) { checkInResults.value = []; checkInSearched.value = false; return; }
  searchTimer = setTimeout(async () => {
    try {
      const res = await searchCandidates({ keyword: checkInKeyword.value.trim(), page: 1, pageSize: 10 });
      checkInResults.value = res.items;
      checkInSearched.value = true;
    } catch { checkInResults.value = []; }
  }, 300);
}

async function selectAndCheckIn(c: Candidate) {
  if (c.status !== 'pre_registered') {
    ElMessage.warning(`该考生当前状态为「${statusLabel(c.status)}」，不可签到`);
    return;
  }
  try {
    await checkInById(c.id, session.value?.name);
    ElMessage.success(`已签到：${c.name}`);
    checkInKeyword.value = '';
    checkInResults.value = [];
    void loadList();
    void loadGroupCounts();
  } catch (err) {
    ElMessage.error((err as Error).message || '签到失败');
  }
}

async function doCheckIn(row: Candidate) {
  try {
    await checkInById(row.id, session.value?.name);
    ElMessage.success(`已签到：${row.name}`);
    void loadList();
    void loadGroupCounts();
  } catch (err) {
    ElMessage.error((err as Error).message || '签到失败');
  }
}

function doAssign(id: string, name: string) {
  assignTarget.value = { id, name } as Candidate;
  assignGroupValue.value = 'A';
  assignVisible.value = true;
}
async function confirmAssign() {
  if (!assignTarget.value || !session.value) return;
  assignSubmitting.value = true;
  try {
    await assignGroup(assignTarget.value.id, assignGroupValue.value, session.value.name);
    ElMessage.success('已分配组别');
    assignVisible.value = false;
    void loadList();
    void loadGroupCounts();
  } catch (err) {
    ElMessage.error((err as Error).message || '分配失败');
  } finally {
    assignSubmitting.value = false;
  }
}

function openReassign(row: Candidate) {
  reassignTarget.value = row;
  reassignGroupValue.value = row.groupName || 'A';
  reassignVisible.value = true;
}
async function confirmReassign() {
  if (!reassignTarget.value || !session.value) return;
  if (reassignGroupValue.value === reassignTarget.value.groupName) {
    ElMessage.info('组别未变化');
    reassignVisible.value = false;
    return;
  }
  reassignSubmitting.value = true;
  try {
    await reassignGroup(reassignTarget.value.id, reassignGroupValue.value, session.value.name);
    ElMessage.success('已重新分组');
    reassignVisible.value = false;
    void loadList();
    void loadGroupCounts();
  } catch (err) {
    ElMessage.error((err as Error).message || '重新分组失败');
  } finally {
    reassignSubmitting.value = false;
  }
}

async function doMoveToSecond(row: Candidate) {
  if (!session.value) return;
  try {
    await ElMessageBox.confirm(`确认将「${row.name}」移入二面？`, '移入二面', { type: 'warning' });
    await moveToSecondRound(row.id, session.value.name);
    ElMessage.success('已移入二面，请分配二面组');
    void loadList();
    void loadGroupCounts();
  } catch (err) {
    if (err === 'cancel') return;
    ElMessage.error((err as Error).message || '移入二面失败');
  }
}

function openAssignSecond(row: Candidate) {
  assignSecondTarget.value = row;
  assignSecondValue.value = 'A';
  assignSecondVisible.value = true;
}
async function confirmAssignSecond() {
  if (!assignSecondTarget.value || !session.value) return;
  assignSecondSubmitting.value = true;
  try {
    await assignSecondGroup(assignSecondTarget.value.id, assignSecondValue.value, session.value.name);
    ElMessage.success('已分配二面组');
    assignSecondVisible.value = false;
    void loadList();
  } catch (err) {
    ElMessage.error((err as Error).message || '分配二面组失败');
  } finally {
    assignSecondSubmitting.value = false;
  }
}

function openManualDialog(mode: 'checkin' | 'add') {
  manualDialogMode.value = mode;
  manualForm.value = { name: '', gender: '男', className: '', phone: '', firstDepartment: '', secondDepartment: '', acceptAdjust: '是' };
  manualDialogVisible.value = true;
}

async function submitManual() {
  const f = manualForm.value;
  if (!f.name.trim() || !f.className.trim() || !f.phone.trim() || !f.firstDepartment) {
    ElMessage.warning('请完整填写姓名、班级、手机号和第一意向部门');
    return;
  }
  if (!/^1\d{10}$/.test(f.phone.trim())) {
    ElMessage.warning('手机号格式不正确（需 11 位，1 开头）');
    return;
  }
  manualSubmitting.value = true;
  try {
    const body = {
      name: f.name.trim(), gender: f.gender, className: f.className.trim(),
      phone: f.phone.trim(), firstDepartment: f.firstDepartment,
      secondDepartment: f.secondDepartment || undefined,
      acceptAdjust: f.acceptAdjust,
    };
    const created = await createPreRegister(body);
    if (manualDialogMode.value === 'checkin') {
      await checkInById(created.id, session.value?.name);
      ElMessage.success(`已录入并签到：${created.name}`);
    } else {
      ElMessage.success(`已添加：${created.name}`);
    }
    manualDialogVisible.value = false;
    page.value = 1;
    void loadList();
    void loadGroupCounts();
  } catch (err) {
    ElMessage.error((err as Error).message || '操作失败');
  } finally {
    manualSubmitting.value = false;
  }
}

function downloadTemplate() {
  const csv = '姓名,性别,班级,手机号,第一意向,第二意向,服从调剂\n示例男,男,软件技术1班,13800000000,部门A,部门B,是\n';
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = '考生导入模板.csv';
  a.click();
  URL.revokeObjectURL(url);
}

async function beforeCsvUpload(file: File): Promise<boolean> {
  try {
    const text = await file.text();
    const lines = text.replace(/^\uFEFF/, '').split(/\r?\n/).filter((l) => l.trim());
    if (lines.length <= 1) { ElMessage.warning('CSV 文件没有数据行'); return false; }
    const headers = lines[0].split(',').map((h) => h.trim());
    const idx = (n: string) => headers.indexOf(n);
    const need = ['姓名', '性别', '班级', '手机号', '第一意向', '服从调剂'];
    for (const n of need) {
      if (idx(n) < 0) { ElMessage.warning(`模板缺少列：${n}`); return false; }
    }
    const items = lines.slice(1).map((l) => {
      const p = l.split(',');
      return {
        name: p[idx('姓名')]?.trim() ?? '',
        gender: p[idx('性别')]?.trim() ?? '',
        className: p[idx('班级')]?.trim() ?? '',
        phone: p[idx('手机号')]?.trim() ?? '',
        firstDepartment: p[idx('第一意向')]?.trim() ?? '',
        secondDepartment: p[idx('第二意向')]?.trim() || undefined,
        acceptAdjust: p[idx('服从调剂')]?.trim() || '是',
      };
    }).filter((i) => i.name && i.phone);
    if (items.length === 0) { ElMessage.warning('未解析出有效数据'); return false; }
    const invalid = items.filter((i) => !/^1\d{10}$/.test(i.phone)).length;
    if (invalid > 0) {
      const ok = await ElMessageBox.confirm(`${invalid} 行手机号格式不正确，将跳过这些行，继续导入？`, '导入确认', { type: 'warning' });
      if (ok !== 'confirm' && ok !== true) return false;
    }
    const validItems = items.filter((i) => /^1\d{10}$/.test(i.phone));
    await batchImportCandidates(validItems, session.value?.name);
    ElMessage.success(`导入成功 ${validItems.length} 条`);
    void loadList();
    void loadGroupCounts();
  } catch (err) {
    if (err === 'cancel') return false;
    ElMessage.error((err as Error).message || 'CSV 导入失败');
  }
  return false;
}
</script>

<style scoped>
.checkin-page { padding: 4px; }
.loading-text { padding: 24px; color: #6b7280; }
.topbar { display: flex; align-items: flex-start; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.topbar h2 { font-size: 24px; font-weight: 700; color: #1f2937; }
.user-line { display: flex; align-items: center; gap: 6px; font-size: 14px; color: #6b7280; margin-top: 6px; flex-wrap: wrap; }
.dim { color: #9ca3af; }
.top-actions { display: flex; gap: 8px; }
.group-stats { display: grid; grid-template-columns: repeat(6, 1fr); gap: 10px; margin-bottom: 16px; }
@media (max-width: 768px) { .group-stats { grid-template-columns: repeat(3, 1fr); } }
.gstat { background: #fff; border-radius: 10px; padding: 12px; display: flex; flex-direction: column; align-items: center; gap: 4px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.gstat .gs-name { font-size: 13px; color: #64748b; }
.gstat .gs-count { font-size: 24px; font-weight: 800; }
.g-A .gs-count { color: #4f46e5; }
.g-B .gs-count { color: #0891b2; }
.g-C .gs-count { color: #059669; }
.g-D .gs-count { color: #d97706; }
.g-total .gs-count { color: #dc2626; }
.panel { background: #fff; border-radius: 12px; padding: 16px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); margin-bottom: 16px; }
.panel-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.panel-head h3 { font-size: 17px; font-weight: 700; color: #1f2937; }
.panel-sub { font-size: 13px; color: #6b7280; }
.mb12 { margin-bottom: 12px; }
.quick-results { display: flex; flex-direction: column; gap: 8px; max-height: 260px; overflow-y: auto; }
.quick-item { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; border: 1px solid #e2e8f0; border-radius: 8px; cursor: pointer; transition: all .2s; }
.quick-item:hover { border-color: #6366f1; background: #eef2ff; }
.quick-item.disabled { cursor: not-allowed; opacity: 0.7; }
.qi-left { display: flex; flex-direction: column; }
.qi-name { font-weight: 500; color: #111827; }
.qi-sub { font-size: 12px; color: #6b7280; }
.empty-hint { text-align: center; color: #9ca3af; padding: 20px 0; font-size: 14px; }
.filters { display: flex; gap: 10px; flex-wrap: wrap; }
.f-keyword { width: 240px; }
.f-item { width: 150px; }
.filter-tags { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; }
.toolbar { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 16px; align-items: center; }
.pager { display: flex; justify-content: flex-end; gap: 8px; padding-top: 12px; }
</style>
