<template>
  <div class="results-page">
    <div class="topbar">
      <div>
        <h2>结果管理</h2>
        <p class="sub">管理员专用 · 查看统计、导出与清理数据</p>
      </div>
      <div class="top-actions">
        <el-button size="small" :loading="exporting" @click="handleExport">
          <el-icon><Download /></el-icon> 导出 CSV
        </el-button>
        <el-button size="small" type="danger" plain @click="deleteAllVisible = true">
          <el-icon><Delete /></el-icon> 清空全部数据
        </el-button>
      </div>
    </div>

    <!-- 统计卡 -->
    <div class="stat-grid">
      <div v-for="s in statCards" :key="s.label" class="stat-card">
        <span class="stat-label">{{ s.label }}</span>
        <span class="stat-value" :style="{ color: s.color }">{{ s.value }}</span>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="panel">
      <div class="filters">
        <el-input v-model="filterForm.keyword" placeholder="姓名/班级/手机" clearable class="f-keyword" @keyup.enter="applyFilter" @clear="applyFilter" />
        <el-select v-model="filterForm.status" placeholder="状态" clearable class="f-item">
          <el-option v-for="(label, val) in STATUS_OPTIONS" :key="val" :label="label" :value="val" />
        </el-select>
        <el-select v-model="filterForm.groupName" placeholder="组别" clearable class="f-item">
          <el-option v-for="g in ['A', 'B', 'C', 'D']" :key="g" :label="g + '组'" :value="g" />
        </el-select>
        <el-input v-model="filterForm.minScore" placeholder="最低分" class="f-score" type="number" />
        <el-input v-model="filterForm.maxScore" placeholder="最高分" class="f-score" type="number" />
        <el-button type="primary" @click="applyFilter"><el-icon><Search /></el-icon> 查询</el-button>
      </div>
    </div>

    <!-- 列表 -->
    <div class="panel">
      <div class="panel-head">
        <h3>考生结果列表</h3>
        <span class="panel-sub">第 {{ page }}/{{ totalPages }} 页 · 共 {{ total }} 人</span>
      </div>
      <el-table :data="items" v-loading="loading" :max-height="620">
        <el-table-column prop="globalNumber" label="序号" width="70" align="center">
          <template #default="{ row }">#{{ row.globalNumber ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="className" label="班级" min-width="110" show-overflow-tooltip />
        <el-table-column label="意向" width="130">
          <template #default="{ row }">
            <div class="dept-cell">
              <span>{{ row.firstDepartment || '-' }}</span>
              <span v-if="row.secondDepartment" class="dim"> / {{ row.secondDepartment }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="服从调剂" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.acceptAdjust" size="small" :type="row.acceptAdjust === '是' ? 'success' : 'info'">
              {{ row.acceptAdjust }}
            </el-tag>
            <span v-else class="dim">-</span>
          </template>
        </el-table-column>
        <el-table-column label="总分" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.scoreTotal !== null && row.scoreTotal !== undefined" class="score-total">{{ row.scoreTotal }}</span>
            <span v-else class="dim">-</span>
          </template>
        </el-table-column>
        <el-table-column label="平均分" width="80" align="center">
          <template #default="{ row }">
            {{ row.averageScore !== null && row.averageScore !== undefined ? row.averageScore.toFixed(1) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="一面评分" min-width="220">
          <template #default="{ row }">
            <div v-if="(row.scores?.length ?? 0) > 0" class="scores-cell">
              <div v-for="s in row.scores" :key="s.id" class="score-line">
                <span class="sl-name">{{ s.interviewerName }}</span>
                <span class="sl-score">{{ s.score !== null && s.score !== undefined ? Number(s.score).toFixed(1) : '-' }}</span>
                <span v-if="s.judgeComment" class="sl-comment">{{ s.judgeComment }}</span>
              </div>
              <div class="score-line">
                <span class="sl-name">一面平均分</span>
                <span class="sl-score">{{ row.averageScore !== null && row.averageScore !== undefined ? Number(row.averageScore).toFixed(1) : '-' }}</span>
              </div>
            </div>
            <span v-else class="dim">-</span>
          </template>
        </el-table-column>
        <el-table-column label="二面评分" min-width="220">
          <template #default="{ row }">
            <div v-if="(row.secondScores?.length ?? 0) > 0" class="scores-cell">
              <div v-for="s in row.secondScores" :key="s.id" class="score-line">
                <span class="sl-name">{{ s.interviewerName }}</span>
                <span class="sl-score">{{ s.score !== null && s.score !== undefined ? Number(s.score).toFixed(1) : '-' }}</span>
                <span v-if="s.judgeComment" class="sl-comment">{{ s.judgeComment }}</span>
              </div>
              <div class="score-line">
                <span class="sl-name">二面平均分</span>
                <span class="sl-score">{{ row.secondAverageScore !== null && row.secondAverageScore !== undefined ? Number(row.secondAverageScore).toFixed(1) : '-' }}</span>
              </div>
            </div>
            <span v-else class="dim">-</span>
          </template>
        </el-table-column>
        <el-table-column label="录取结果" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'final_admitted'" size="small" type="success">录取</el-tag>
            <el-tag v-else-if="row.status === 'final_rejected'" size="small" type="danger">淘汰</el-tag>
            <span v-else class="dim">待录取</span>
          </template>
        </el-table-column>
        <el-table-column label="录取部门" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.finalDepartment">{{ row.finalDepartment }}</span>
            <span v-else class="dim">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'first_completed'" size="small" type="primary" link @click="handleMoveToSecond(row)">
              推进二面
            </el-button>
            <el-button v-if="canDecide(row)" size="small" type="success" link @click="openAdmit(row)">
              录取
            </el-button>
            <el-button v-if="canDecide(row)" size="small" type="danger" link @click="handleReject(row)">
              淘汰
            </el-button>
            <el-button size="small" type="danger" link @click="openDelete(row)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="totalPages > 1" class="pager">
        <el-button size="small" :disabled="page <= 1" @click="changePage(page - 1)">上一页</el-button>
        <el-button size="small" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</el-button>
      </div>
    </div>

    <!-- 录取部门选择 -->
    <el-dialog v-model="admitVisible" title="录取 · 选择部门" width="400px">
      <p class="dialog-tip">确认录取考生「{{ admitTarget?.name }}」，请选择录取部门：</p>
      <el-select v-model="admitDept" placeholder="请选择部门" style="width: 100%">
        <el-option v-for="d in ADMIT_DEPARTMENTS" :key="d" :label="d" :value="d" />
      </el-select>
      <template #footer>
        <el-button @click="admitVisible = false">取消</el-button>
        <el-button type="success" :loading="admitSubmitting" :disabled="!admitDept" @click="confirmAdmit">
          确认录取
        </el-button>
      </template>
    </el-dialog>

    <!-- 删除单个确认 -->
    <el-dialog v-model="deleteVisible" title="删除考生" width="360px">
      <p>确认删除考生「{{ deleteTarget?.name }}」？此操作不可恢复。</p>
      <template #footer>
        <el-button @click="deleteVisible = false">取消</el-button>
        <el-button type="danger" :loading="deleting" @click="confirmDelete">删除</el-button>
      </template>
    </el-dialog>

    <!-- 清空全部确认 -->
    <el-dialog v-model="deleteAllVisible" title="清空全部数据" width="400px">
      <p class="danger-text">将删除全部考生及其评分数据，不可恢复！请输入清空密码：</p>
      <el-input v-model="deleteAllPassword" type="password" show-password placeholder="请输入清空密码" @keyup.enter="confirmDeleteAll" />
      <template #footer>
        <el-button @click="deleteAllVisible = false">取消</el-button>
        <el-button type="danger" :loading="deletingAll" :disabled="!deleteAllPassword" @click="confirmDeleteAll">确认清空</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  searchCandidates, getCandidatesStats, deleteCandidate, deleteAll,
  exportCandidatesCsv, batchImportCandidates, moveToSecondRound, decideAdmission,
} from '@/api/candidates';
import { getSession } from '@/utils/auth';
import type { Candidate, StatsResponse } from '@/types';

const PAGE_SIZE = 20;
const STATUS_OPTIONS: Record<string, string> = {
  pre_registered: '预报名', waiting: '待面试', interviewing: '面试中',
  pending_approval: '待审批', first_completed: '一面完成', second_waiting: '二面待面',
  second_interviewing: '二面中', second_completed: '二面完成',
  final_admitted: '已录取', final_rejected: '未录取', absent: '缺考',
};

function statusLabel(s: string): string {
  return STATUS_OPTIONS[s] || s;
}
function statusTagType(s: string): 'info' | 'success' | 'warning' | 'danger' | 'primary' {
  if (s === 'final_admitted' || s === 'second_completed') return 'success';
  if (s === 'final_rejected' || s === 'absent') return 'danger';
  if (s === 'pending_approval' || s === 'interviewing' || s === 'second_interviewing') return 'warning';
  return 'info';
}

const filterForm = ref({ keyword: '', status: '', groupName: '', minScore: '', maxScore: '' });
const appliedFilter = ref({ ...filterForm.value });
const items = ref<Candidate[]>([]);
const total = ref(0);
const page = ref(1);
const totalPages = ref(1);
const loading = ref(false);
const stats = ref<StatsResponse | null>(null);
const exporting = ref(false);
const deleteVisible = ref(false);
const deleting = ref(false);
const deleteTarget = ref<Candidate | null>(null);
const deleteAllVisible = ref(false);
const deleteAllPassword = ref('');
const deletingAll = ref(false);
let dataSource: EventSource | null = null;

const ADMIT_DEPARTMENTS = ['部门A', '部门B', '部门C'];
const admitVisible = ref(false);
const admitTarget = ref<Candidate | null>(null);
const admitDept = ref('');
const admitSubmitting = ref(false);

const statCards = computed(() => [
  { label: '总报名人数', value: stats.value?.total ?? 0, color: '#4f46e5' },
  { label: '一面完成', value: stats.value?.completed ?? 0, color: '#059669' },
  { label: '已评分人数', value: stats.value?.scoredCount ?? 0, color: '#0891b2' },
  { label: '平均分（0-10）', value: stats.value?.averageScore !== null && stats.value?.averageScore !== undefined ? Number(stats.value.averageScore).toFixed(1) : '-', color: '#d97706' },
]);

function buildParams() {
  const f = appliedFilter.value;
  const params: Record<string, unknown> = { page: page.value, pageSize: PAGE_SIZE };
  if (f.keyword) params.keyword = f.keyword;
  if (f.status) params.status = f.status;
  if (f.groupName) params.groupName = f.groupName;
  if (f.minScore !== '') params.minScore = Number(f.minScore);
  if (f.maxScore !== '') params.maxScore = Number(f.maxScore);
  return params;
}

async function fetchList() {
  loading.value = true;
  try {
    const res = await searchCandidates(buildParams());
    items.value = res.items;
    total.value = res.total;
    totalPages.value = Math.max(1, Math.ceil(res.total / PAGE_SIZE));
  } catch (err) {
    ElMessage.error((err as Error).message || '加载列表失败');
  } finally {
    loading.value = false;
  }
}

async function fetchStats() {
  const f = appliedFilter.value;
  const params: Record<string, unknown> = {};
  if (f.keyword) params.keyword = f.keyword;
  if (f.status) params.status = f.status;
  if (f.groupName) params.groupName = f.groupName;
  if (f.minScore !== '') params.minScore = Number(f.minScore);
  if (f.maxScore !== '') params.maxScore = Number(f.maxScore);
  try {
    stats.value = await getCandidatesStats(params);
  } catch { /* ignore */ }
}

/** 订阅业务变更 SSE：收到 data 事件静默刷新列表与统计，避免停在本页看不到另一端的新结果。 */
function connectDataStream() {
  if (dataSource) return;
  dataSource = new EventSource('/api/candidates/display/stream');
  dataSource.addEventListener('data', () => {
    void fetchList();
    void fetchStats();
  });
  dataSource.onerror = () => { /* EventSource 自动重连 */ };
}
function closeDataStream() {
  if (dataSource) { dataSource.close(); dataSource = null; }
}

onMounted(() => {
  void fetchList();
  void fetchStats();
  connectDataStream();
});

onBeforeUnmount(() => {
  closeDataStream();
});

function applyFilter() {
  if (filterForm.value.minScore !== '' && filterForm.value.maxScore !== '' && Number(filterForm.value.minScore) > Number(filterForm.value.maxScore)) {
    ElMessage.warning('最低分不能大于最高分');
    return;
  }
  appliedFilter.value = { ...filterForm.value };
  page.value = 1;
  void fetchList();
  void fetchStats();
}

function changePage(p: number) {
  page.value = p;
  void fetchList();
}

async function handleExport() {
  exporting.value = true;
  let downloadUrl: string | undefined;
  try {
    const f = appliedFilter.value;
    const params: Record<string, string | number | undefined> = {};
    if (f.keyword) params.keyword = f.keyword;
    if (f.status) params.status = f.status;
    if (f.groupName) params.groupName = f.groupName;
    if (f.minScore !== '') params.minScore = Number(f.minScore);
    if (f.maxScore !== '') params.maxScore = Number(f.maxScore);
    const blob = await exportCandidatesCsv(params);
    downloadUrl = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = downloadUrl;
    a.download = `面试结果_${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(a);
    try {
      a.click();
    } finally {
      a.remove();
    }
  } catch (err) {
    ElMessage.error((err as Error).message || '导出失败');
  } finally {
    if (downloadUrl) {
      const url = downloadUrl;
      setTimeout(() => URL.revokeObjectURL(url), 0);
    }
    exporting.value = false;
  }
}

async function handleMoveToSecond(row: Candidate) {
  try {
    await moveToSecondRound(row.id, getSession()?.name ?? 'admin');
    ElMessage.success(`已将 ${row.name} 的一面资料推送到二面`);
    void fetchList();
    void fetchStats();
  } catch (err) {
    ElMessage.error((err as Error).message || '推进二面失败');
  }
}

function canDecide(row: Candidate): boolean {
  return row.status === 'second_completed'
    || row.status === 'final_admitted'
    || row.status === 'final_rejected';
}

function openAdmit(row: Candidate) {
  admitTarget.value = row;
  admitDept.value = row.finalDepartment || '';
  admitVisible.value = true;
}

async function confirmAdmit() {
  if (!admitTarget.value || !admitDept.value) return;
  admitSubmitting.value = true;
  try {
    await decideAdmission(
      admitTarget.value.id,
      'admitted',
      admitDept.value,
      getSession()?.name ?? 'admin',
    );
    ElMessage.success(`已录取 ${admitTarget.value.name}（${admitDept.value}）`);
    admitVisible.value = false;
    void fetchList();
    void fetchStats();
  } catch (err) {
    ElMessage.error((err as Error).message || '录取操作失败');
  } finally {
    admitSubmitting.value = false;
  }
}

async function handleReject(row: Candidate) {
  try {
    await ElMessageBox.confirm(`确认淘汰考生「${row.name}」？`, '淘汰确认', {
      type: 'warning',
      confirmButtonText: '淘汰',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    await decideAdmission(row.id, 'rejected', undefined, getSession()?.name ?? 'admin');
    ElMessage.success(`已淘汰 ${row.name}`);
    void fetchList();
    void fetchStats();
  } catch (err) {
    ElMessage.error((err as Error).message || '淘汰操作失败');
  }
}

function openDelete(row: Candidate) {
  deleteTarget.value = row;
  deleteVisible.value = true;
}
async function confirmDelete() {
  if (!deleteTarget.value) return;
  deleting.value = true;
  try {
    await deleteCandidate(deleteTarget.value.id);
    ElMessage.success(`已删除：${deleteTarget.value.name}`);
    deleteVisible.value = false;
    void fetchList();
    void fetchStats();
  } catch (err) {
    ElMessage.error((err as Error).message || '删除失败');
  } finally {
    deleting.value = false;
  }
}

async function confirmDeleteAll() {
  if (!deleteAllPassword.value) return;
  deletingAll.value = true;
  try {
    const res = await deleteAll(deleteAllPassword.value);
    ElMessage.success(`已清空 ${res.deletedCount} 条数据`);
    deleteAllVisible.value = false;
    deleteAllPassword.value = '';
    void fetchList();
    void fetchStats();
  } catch (err) {
    ElMessage.error((err as Error).message || '清空失败');
  } finally {
    deletingAll.value = false;
  }
}
</script>

<style scoped>
.results-page { padding: 4px; }
.topbar { display: flex; align-items: flex-start; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.topbar h2 { font-size: 24px; font-weight: 700; color: #1f2937; }
.sub { font-size: 14px; color: #6b7280; margin-top: 6px; }
.top-actions { display: flex; gap: 8px; }
.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
@media (max-width: 768px) { .stat-grid { grid-template-columns: repeat(2, 1fr); } }
.stat-card { background: #fff; border-radius: 12px; padding: 16px; display: flex; flex-direction: column; gap: 6px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.stat-label { font-size: 13px; color: #64748b; }
.stat-value { font-size: 28px; font-weight: 800; }
.panel { background: #fff; border-radius: 12px; padding: 16px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); margin-bottom: 16px; }
.panel-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.panel-head h3 { font-size: 17px; font-weight: 700; color: #1f2937; }
.panel-sub { font-size: 13px; color: #6b7280; }
.filters { display: flex; gap: 10px; flex-wrap: wrap; }
.f-keyword { width: 200px; }
.f-item { width: 130px; }
.f-score { width: 100px; }
.dim { color: #9ca3af; }
.dept-cell { font-size: 13px; }
.score-total { font-weight: 700; color: #4f46e5; }
.scores-cell { display: flex; flex-direction: column; gap: 6px; }
.score-line { display: flex; align-items: baseline; flex-wrap: wrap; gap: 6px; font-size: 13px; line-height: 1.5; }
.sl-name { font-weight: 500; color: #111827; }
.sl-score { font-weight: 700; color: #4f46e5; }
.sl-comment { color: #4b5563; font-size: 12px; word-break: break-all; }
.second-cell { font-size: 13px; }
.pager { display: flex; justify-content: flex-end; gap: 8px; padding-top: 12px; }
.danger-text { color: #dc2626; margin-bottom: 10px; }
.dialog-tip { margin-bottom: 12px; color: #374151; font-size: 14px; }
</style>
