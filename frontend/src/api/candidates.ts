/** 与后端 /api/candidates 契约对齐的 API 封装（原版 client/src/api/candidates.ts 的 Vue 移植版） */

import type {
  BatchImportResponse,
  BigScreenData,
  Candidate,
  CandidateListResponse,
  GroupCountInfo,
  LoginResponse,
  SecondRoundStats,
  StatsResponse,
} from '@/types';
import { authHeader } from '@/utils/auth';

const BASE_URL = '/';

async function request<T>(url: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(BASE_URL + url.replace(/^\//, ''), {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...authHeader(),
      ...options.headers,
    },
  });
  if (!res.ok) {
    let message = `请求失败 (${res.status})`;
    try {
      const data = await res.json();
      if (data && typeof data.message === 'string') {
        message = data.message;
      }
    } catch {
      // ignore parse errors
    }
    throw new Error(message);
  }
  return res.json();
}

function buildQueryString(params: Record<string, unknown>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '' && value !== null) {
      search.set(key, String(value));
    }
  });
  const str = search.toString();
  return str ? `?${str}` : '';
}

export interface SearchParams {
  keyword?: string;
  groupName?: string;
  status?: string;
  minScore?: number;
  maxScore?: number;
  page?: number;
  pageSize?: number;
  sortBy?: 'globalNumber' | 'score' | 'createdAt';
  sortOrder?: 'asc' | 'desc';
  excludeCompleted?: boolean;
}

export async function preRegister(body: Record<string, unknown>): Promise<Candidate> {
  return request<Candidate>('/api/candidates/pre-register', { method: 'POST', body: JSON.stringify(body) });
}

export async function searchCandidates(params: SearchParams): Promise<CandidateListResponse> {
  return request<CandidateListResponse>(`/api/candidates${buildQueryString(params as Record<string, unknown>)}`);
}

export async function getCandidatesStats(params: {
  groupName?: string;
  minScore?: number;
  maxScore?: number;
  keyword?: string;
}): Promise<StatsResponse> {
  return request<StatsResponse>(`/api/candidates/stats${buildQueryString(params as Record<string, unknown>)}`);
}

export async function checkInById(candidateId: string, operatorName?: string): Promise<Candidate> {
  return request<Candidate>('/api/candidates/check-in', {
    method: 'POST',
    body: JSON.stringify({ candidateId, operatorName }),
  });
}

export async function createPreRegister(body: Record<string, unknown>): Promise<Candidate> {
  return request<Candidate>('/api/candidates/pre-register', { method: 'POST', body: JSON.stringify(body) });
}

export async function preRegisterPublic(body: Record<string, unknown>): Promise<Candidate> {
  return request<Candidate>('/api/candidates/pre-register/public', { method: 'POST', body: JSON.stringify(body) });
}

export async function searchByNamePublic(name: string): Promise<Candidate[]> {
  return request<Candidate[]>('/api/candidates/public/search-by-name', {
    method: 'POST',
    body: JSON.stringify({ name }),
  });
}

export async function checkInPublic(candidateId: string): Promise<Candidate> {
  return request<Candidate>('/api/candidates/public/check-in', { method: 'POST', body: JSON.stringify({ candidateId }) });
}

export async function getCandidateById(id: string): Promise<Candidate> {
  return request<Candidate>(`/api/candidates/public/detail/${id}`);
}

export async function updateDepartmentsPublic(body: {
  candidateId: string;
  firstDepartment: string;
  secondDepartment?: string;
}): Promise<Candidate> {
  return request<Candidate>('/api/candidates/public/update-departments', { method: 'POST', body: JSON.stringify(body) });
}

export async function assignGroup(candidateId: string, groupName: string, operatorName?: string): Promise<Candidate> {
  return request<Candidate>('/api/candidates/assign-group', {
    method: 'POST',
    body: JSON.stringify({ candidateId, groupName, operatorName }),
  });
}

export async function reassignGroup(candidateId: string, groupName: string, operatorName?: string): Promise<Candidate> {
  return request<Candidate>('/api/candidates/reassign-group', {
    method: 'POST',
    body: JSON.stringify({ candidateId, groupName, operatorName }),
  });
}

export async function batchImportCandidates(
  items: Record<string, unknown>[],
  operatorName?: string,
): Promise<BatchImportResponse> {
  return request<BatchImportResponse>('/api/candidates/batch-import', {
    method: 'POST',
    body: JSON.stringify({ items, operatorName }),
  });
}

export async function getGroupQueue(groupName: string, interviewerName?: string): Promise<Candidate[]> {
  const query = interviewerName ? buildQueryString({ interviewerName }) : '';
  return request<Candidate[]>(`/api/candidates/group/${groupName}${query}`);
}

export async function interviewerLogin(body: { name: string; password?: string }): Promise<LoginResponse> {
  return request<LoginResponse>('/api/candidates/interviewer-login', { method: 'POST', body: JSON.stringify(body) });
}

export async function secondLogin(body: { name: string; password?: string }): Promise<LoginResponse> {
  return request<LoginResponse>('/api/candidates/second/login', { method: 'POST', body: JSON.stringify(body) });
}

export async function updateStatus(id: string, status: string, interviewerName?: string): Promise<Candidate> {
  return request<Candidate>(`/api/candidates/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status, interviewerName }),
  });
}

export async function submitScore(id: string, score: number, interviewerName: string, judgeComment?: string): Promise<Candidate> {
  return request<Candidate>(`/api/candidates/${id}/score`, {
    method: 'POST',
    body: JSON.stringify({ score, interviewerName, judgeComment }),
  });
}

export interface SeatScoreInput {
  interviewerName: string;
  score: number;
  judgeComment?: string;
}

export async function submitScores(
  id: string,
  operatorName: string,
  scores: SeatScoreInput[],
): Promise<Candidate> {
  return request<Candidate>(`/api/candidates/${id}/scores`, {
    method: 'POST',
    body: JSON.stringify({ operatorName, scores }),
  });
}

export async function fetchFirstRoundInterviewers(): Promise<Record<string, string[]>> {
  return request<Record<string, string[]>>('/api/candidates/interviewers/first-round');
}

export async function deleteCandidate(id: string): Promise<{ success: boolean; deletedId: string; name: string }> {
  return request<{ success: boolean; deletedId: string; name: string }>(`/api/candidates/${id}`, { method: 'DELETE' });
}

export async function decideAdmission(
  candidateId: string,
  decision: 'admitted' | 'rejected',
  finalDepartment: string | undefined,
  operatorName?: string,
): Promise<Candidate> {
  return request<Candidate>(`/api/candidates/${candidateId}/admit`, {
    method: 'POST',
    body: JSON.stringify({ candidateId, decision, finalDepartment, operatorName }),
  });
}

export async function deleteAll(password: string): Promise<{ success: boolean; deletedCount: number }> {
  return request<{ success: boolean; deletedCount: number }>('/api/candidates/delete-all', {
    method: 'POST',
    body: JSON.stringify({ password }),
  });
}

export async function moveToSecondRound(candidateId: string, operatorName: string): Promise<Candidate> {
  return request<Candidate>('/api/candidates/second/move-to', {
    method: 'POST',
    body: JSON.stringify({ candidateId, operatorName }),
  });
}

export async function assignSecondGroup(candidateId: string, groupName: string, operatorName: string): Promise<Candidate> {
  return request<Candidate>('/api/candidates/second/assign-group', {
    method: 'POST',
    body: JSON.stringify({ candidateId, groupName, operatorName }),
  });
}

export async function secondCheckInPublic(candidateId: string): Promise<Candidate> {
  return request<Candidate>('/api/candidates/second/check-in', { method: 'POST', body: JSON.stringify({ candidateId }) });
}

export async function secondRoundStart(candidateId: string, interviewerName: string): Promise<Candidate> {
  return request<Candidate>('/api/candidates/second/start', {
    method: 'POST',
    body: JSON.stringify({ candidateId, interviewerName }),
  });
}

export interface SecondRoundQueue {
  waiting: Candidate[];
  interviewing: Candidate[];
}

export async function getSecondRoundQueue(): Promise<SecondRoundQueue> {
  return request<SecondRoundQueue>('/api/candidates/second/queue');
}

export async function getSecondRoundStats(): Promise<SecondRoundStats> {
  return request<SecondRoundStats>('/api/candidates/second/stats');
}

export async function getBigScreenData(): Promise<BigScreenData> {
  return request<BigScreenData>('/api/candidates/display/big-screen');
}

export async function getGroupCounts(): Promise<GroupCountInfo[]> {
  return request<GroupCountInfo[]>('/api/candidates/group-counts');
}

export async function getDisplayMode(operatorName: string): Promise<{ mode: string }> {
  return request<{ mode: string }>(`/api/candidates/display/mode?operatorName=${encodeURIComponent(operatorName)}`);
}

export async function toggleDisplayMode(operatorName: string, mode: string): Promise<{ mode: string }> {
  return request<{ mode: string }>('/api/candidates/display/toggle-mode', {
    method: 'POST',
    body: JSON.stringify({ operatorName, mode }),
  });
}

export function exportCsvUrl(params: { groupName?: string; minScore?: number; maxScore?: number; keyword?: string }): string {
  const query = new URLSearchParams();
  if (params.groupName) query.set('groupName', params.groupName);
  if (params.minScore !== undefined) query.set('minScore', String(params.minScore));
  if (params.maxScore !== undefined) query.set('maxScore', String(params.maxScore));
  if (params.keyword) query.set('keyword', params.keyword);
  return `/api/candidates/export?${query.toString()}`;
}
