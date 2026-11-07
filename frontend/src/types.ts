/** 与后端 /api/candidates 契约对齐的类型定义 */

export interface ScoreItem {
  id: string;
  interviewerName: string;
  groupName: string;
  round?: 'first' | 'second';
  score: number | null;
  judgeComment: string | null;
  createdAt: string;
}

export interface Candidate {
  id: string;
  name: string;
  gender: string | null;
  className: string | null;
  phone: string | null;
  firstDepartment: string | null;
  secondDepartment: string | null;
  acceptAdjust: string | null;
  globalNumber: number | null;
  groupName: string | null;
  groupNumber: number | null;
  status: string;
  scoreTotal: number;
  averageScore: number | null;
  scoredCount: number;
  groupInterviewerCount: number;
  scores: ScoreItem[];
  secondScores: ScoreItem[];
  secondAverageScore: number | null;
  secondScoredCount: number;
  secondScore: number | null;
  secondComment: string | null;
  secondInterviewer: string | null;
  finalDepartment: string | null;
  createdAt: string;
}

export interface CandidateListResponse {
  items: Candidate[];
  total: number;
  page: number;
  pageSize: number;
}

export interface StatsResponse {
  total: number;
  completed: number;
  averageScore: number;
  scoredCount: number;
}

export interface BatchImportResponse {
  success: number;
  failed: number;
  total: number;
  errors: string[];
}

export interface LoginResponse {
  success: boolean;
  name?: string;
  groupName?: string;
  role?: string;
  isAdmin?: boolean;
  isSecondRound?: boolean;
  token?: string;
  message?: string;
}

export interface BigScreenData {
  groups: {
    groupName: string;
    current: { globalNumber: number; groupNumber: number; status: string } | null;
    completedCount: number;
    waitingCount: number;
    waitingList: { globalNumber: number; groupNumber: number; status: string }[];
  }[];
  totalSignedIn: number;
  totalInterviewing: number;
  totalWaiting: number;
  totalCompleted: number;
  mode: string;
}

export interface GroupCountInfo {
  groupName: string;
  total: number;
  waiting: number;
  interviewing: number;
  pendingApproval: number;
  completed: number;
  absent: number;
  nextWaiting: { id: string; name: string; globalNumber: number; groupNumber: number } | null;
}

export interface SecondRoundStats {
  total: number;
  completed: number;
  waiting: number;
  interviewing: number;
  averageSecondScore: number;
}

export interface GroupQueueCandidate extends Candidate {}
