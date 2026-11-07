/** 登录态存储：与旧版前端保持同一 localStorage 键，便于迁移过渡 */
import { ref } from 'vue';

const SESSION_KEY = 'admin_session';

export type UserRole = 'admin' | 'floating' | 'interviewer' | 'second_interviewer';

export interface Session {
  name: string;
  groupName?: string;
  role?: string;
  isAdmin?: boolean;
  isSecondRound?: boolean;
  token?: string;
}

function loadFromStorage(): Session | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return parsed?.name ? parsed : null;
  } catch {
    return null;
  }
}

/** 模块级响应式会话：登录 / 退出后导航条可自动更新 */
const sessionRef = ref<Session | null>(loadFromStorage());

export function getSession(): Session | null {
  return sessionRef.value;
}

export function setSession(session: Session): void {
  localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  sessionRef.value = session;
}

export function clearSession(): void {
  localStorage.removeItem(SESSION_KEY);
  sessionRef.value = null;
}

export function isLoggedIn(): boolean {
  return sessionRef.value !== null;
}

export function getRole(): UserRole | null {
  const role = sessionRef.value?.role;
  if (role === 'admin' || role === 'floating' || role === 'interviewer' || role === 'second_interviewer') {
    return role;
  }
  return null;
}

export function isAdmin(): boolean {
  return sessionRef.value?.isAdmin === true || sessionRef.value?.name === 'admin';
}

/** 各工作人员页面允许的角色；未列入的路径视为学生公开页 */
const STAFF_ACCESS: Record<string, UserRole[]> = {
  '/checkin': ['admin', 'floating'],
  '/workbench': ['admin', 'interviewer'],
  '/interview': ['admin', 'interviewer'],
  '/second-round': ['admin', 'interviewer'],
  '/results': ['admin'],
  '/console': ['admin', 'floating'],
};

/** 返回某路径允许的角色列表；公开页返回 null */
export function staffRolesFor(path: string): UserRole[] | null {
  return STAFF_ACCESS[path] ?? null;
}

/** 当前会话是否可访问该路径（公开页恒为 true） */
export function canAccess(path: string): boolean {
  const roles = STAFF_ACCESS[path];
  if (!roles) return true;
  const session = sessionRef.value;
  if (!session) return false;
  const role = getRole();
  return role !== null && roles.includes(role);
}

/** 按角色返回默认落地页；面试官落地工作台，场次由当前全局场次决定 */
export function roleHome(role: string | undefined | null): string {
  switch (role) {
    case 'floating':
      return '/checkin';
    case 'interviewer':
    case 'second_interviewer':
      return '/workbench';
    case 'admin':
      return '/console';
    default:
      return '/login';
  }
}

export function authHeader(): Record<string, string> {
  const token = sessionRef.value?.token;
  return token ? { Authorization: `Bearer ${token}` } : {};
}
