const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const vue = require('vue');
const { parse, compileScript } = require('@vue/compiler-sfc');

const root = path.resolve(__dirname, '..');
function loadModule(source, mocks) {
  const js = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
  }).outputText;
  const module = { exports: {} };
  new Function('require', 'module', 'exports', js)(
    (name) => Object.hasOwn(mocks, name) ? mocks[name] : require(name), module, module.exports,
  );
  return module.exports;
}
function loadApi() {
  return loadModule(fs.readFileSync(path.join(root, 'src/api/candidates.ts'), 'utf8'), {
    '@/utils/auth': { authHeader: () => ({ Authorization: 'Bearer regression-token' }) },
  });
}
const flush = () => new Promise((resolve) => setImmediate(resolve));

function mountPage(t, pageName, api, session = null) {
  const messages = [];
  const originalDocument = global.document;
  const originalEventSource = global.EventSource;
  const originalWindow = global.window;
  global.document = { addEventListener() {}, removeEventListener() {} };
  global.window = { location: { origin: 'https://example.test' } };
  global.EventSource = class { addEventListener() {} close() {} };
  const source = fs.readFileSync(path.join(root, `src/pages/${pageName}.vue`), 'utf8');
  const compiled = compileScript(parse(source).descriptor, { id: pageName }).content;
  const component = loadModule(compiled, {
    '@/api/candidates': api,
    '@/utils/auth': { getSession: () => session, clearSession() {}, setSession() {} },
    'vue-router': { useRouter: () => ({ replace() {} }), useRoute: () => ({ query: {} }) },
    'element-plus': {
      ElMessage: Object.fromEntries(['success', 'info', 'warning', 'error'].map((kind) =>
        [kind, (message) => messages.push({ kind, message })])),
      ElMessageBox: {},
    },
    qrcode: { default: { toDataURL: async () => 'data:image/png;base64,probe' } },
  }).default;
  let state;
  const renderer = vue.createRenderer({
    createComment: () => ({}), createText: () => ({}), createElement: () => ({}),
    insert() {}, remove() {}, setText() {}, setElementText() {}, patchProp() {},
    parentNode: () => null, nextSibling: () => null,
  });
  const app = renderer.createApp({ setup() {
    state = component.setup({}, { expose() {} });
    return () => null;
  } });
  app.mount({});
  t.after(() => {
    app.unmount();
    global.document = originalDocument;
    global.EventSource = originalEventSource;
    global.window = originalWindow;
  });
  return { state, messages };
}

test('API keeps HTTP 409 and server message for the duplicate-registration dialog', async (t) => {
  const previousFetch = global.fetch;
  t.after(() => { global.fetch = previousFetch; });
  global.fetch = async () => new Response(JSON.stringify({ message: '该手机号已报名' }), { status: 409 });
  const api = loadApi();
  await assert.rejects(api.preRegisterPublic({ name: '同学' }), (error) =>
    error instanceof api.ApiError && error.statusCode === 409 && error.message === '该手机号已报名');
});

test('CSV download authenticates, keeps all filters, and reports failed responses', async (t) => {
  const previousFetch = global.fetch;
  t.after(() => { global.fetch = previousFetch; });
  const api = loadApi();
  global.fetch = async (url, options) => {
    const query = new URL(url, 'https://example.test').searchParams;
    assert.equal(query.get('status'), 'first_completed');
    assert.equal(query.get('groupName'), 'B');
    assert.equal(query.get('minScore'), '0');
    assert.equal(query.get('maxScore'), '9');
    assert.equal(query.get('keyword'), '张');
    assert.equal(options.headers.Authorization, 'Bearer regression-token');
    return new Response('\uFEFF姓名\n张同学');
  };
  const filters = { status: 'first_completed', groupName: 'B', minScore: 0, maxScore: 9, keyword: '张' };
  assert.equal(await (await api.exportCandidatesCsv(filters)).text(), '姓名\n张同学');
  global.fetch = async () => new Response(JSON.stringify({ message: '请重新登录' }), { status: 401 });
  await assert.rejects(api.exportCandidatesCsv(filters), (error) =>
    error.statusCode === 401 && error.message === '请重新登录');
});

test('admin can start an idle second-round group while another group is interviewing', async (t) => {
  const activeA = { id: 'A-active', groupName: 'A', status: 'second_interviewing' };
  const waitingB = { id: 'B-waiting', groupName: 'B', status: 'second_waiting' };
  const started = [];
  const { state } = mountPage(t, 'SecondRoundPage', {
    fetchFirstRoundInterviewers: async () => ({ A: [], B: [] }),
    getSecondRoundQueue: async () => ({ interviewing: [activeA], waiting: [waitingB, { id: 'unassigned', groupName: null }] }),
    secondRoundStart: async (...args) => { started.push(args); },
  }, { name: 'admin', role: 'admin', isAdmin: true });
  await flush();
  state.handleGroupChange('B');
  await vue.nextTick();
  assert.equal(state.currentInterviewing.value, null);
  assert.equal(state.selectedCandidate.value, null);
  assert.deepEqual(state.displayedQueue.value.map((row) => row.id), ['B-waiting']);
  state.selectedCandidateId.value = 'B-waiting';
  await state.handleStart();
  assert.deepEqual(started, [['B-waiting', 'admin']]);
  state.handleGroupChange('A');
  await vue.nextTick();
  assert.equal(state.currentInterviewing.value.id, 'A-active');
  assert.equal(state.selectedCandidate.value.id, 'A-active');
});

test('duplicate registration shows confirmation and reuses already checked-in information', async (t) => {
  t.mock.method(console, 'error', () => {});
  const checkedIn = [];
  const requests = [];
  const existing = { id: 'existing', name: '张同学', status: 'waiting' };
  const { state, messages } = mountPage(t, 'PreRegisterPage', {
    preRegisterPublic: async (body) => {
      requests.push(body);
      if (!body.force) throw Object.assign(new Error('该手机号已报名'), { statusCode: 409 });
      return existing;
    },
    checkInPublic: async (id) => { checkedIn.push(id); },
  });
  state.form.value = { name: '张同学', gender: '男', className: '1班', phone: '13900000001',
    firstDepartment: '部门A', secondDepartment: '', acceptAdjust: '是' };
  await state.handleRegisterAndCheckIn(false);
  assert.equal(state.duplicateDialogOpen.value, true);
  await state.handleRegisterAndCheckIn(true);
  assert.deepEqual(requests.map((body) => body.force), [false, true]);
  assert.deepEqual(checkedIn, []);
  assert.equal(state.duplicateDialogOpen.value, false);
  assert.ok(messages.some((message) => message.kind === 'info' && message.message.includes('已签到')));
});

test('duplicate pre-registration checks in the existing ID and later statuses stay intact', async (t) => {
  const checkedIn = [];
  let candidate = { id: 'existing', name: '张同学', status: 'pre_registered' };
  const { state } = mountPage(t, 'PreRegisterPage', {
    preRegisterPublic: async () => candidate,
    checkInPublic: async (id) => { checkedIn.push(id); },
  });
  const form = { name: '张同学', gender: '男', className: '1班', phone: '13900000001',
    firstDepartment: '部门A', secondDepartment: '', acceptAdjust: '是' };
  state.form.value = { ...form };
  await state.handleRegisterAndCheckIn(true);
  assert.deepEqual(checkedIn, ['existing']);
  candidate = { ...candidate, status: 'second_completed' };
  state.form.value = { ...form };
  await state.handleRegisterAndCheckIn(true);
  assert.deepEqual(checkedIn, ['existing']);
  assert.equal(state.pageState.value, 'found');
  assert.equal(state.selectedCandidate.value.status, 'second_completed');
});

test('results export creates a download and releases its object URL', async (t) => {
  const filters = [];
  const { state } = mountPage(t, 'ResultsPage', {
    searchCandidates: async () => ({ items: [], total: 0 }),
    getCandidatesStats: async () => ({}),
    exportCandidatesCsv: async (params) => { filters.push(params); return new Blob(['姓名']); },
  });
  const previousCreate = URL.createObjectURL;
  const previousRevoke = URL.revokeObjectURL;
  const revoked = [];
  const anchors = [];
  URL.createObjectURL = () => 'blob:csv-regression';
  URL.revokeObjectURL = (url) => revoked.push(url);
  t.after(() => { URL.createObjectURL = previousCreate; URL.revokeObjectURL = previousRevoke; });
  global.document.body = { appendChild() {} };
  global.document.createElement = () => {
    const anchor = { click() { this.clicked = true; }, remove() { this.removed = true; } };
    anchors.push(anchor);
    return anchor;
  };
  state.appliedFilter.value = { keyword: '', status: 'first_completed', groupName: 'B', minScore: '', maxScore: '' };
  await state.handleExport();
  await new Promise((resolve) => setTimeout(resolve, 5));
  assert.equal(filters[0].status, 'first_completed');
  assert.equal(anchors[0].href, 'blob:csv-regression');
  assert.equal(anchors[0].clicked, true);
  assert.equal(anchors[0].removed, true);
  assert.deepEqual(revoked, ['blob:csv-regression']);
  assert.equal(state.exporting.value, false);
});
