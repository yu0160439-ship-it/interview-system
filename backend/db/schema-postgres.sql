-- Student Club Interview Queuing System - PostgreSQL Schema (Spring Boot version)
-- Field-aligned with the original Cloudflare D1 schema
-- 说明：业务表由后端启动时自动创建（IF NOT EXISTS），本脚本仅作手动参考/兜底，无需导入。

-- Candidates table
CREATE TABLE IF NOT EXISTS candidates (
  id                  VARCHAR(36)  NOT NULL PRIMARY KEY,
  name                VARCHAR(50)  NOT NULL,
  gender              VARCHAR(10),
  class_name          VARCHAR(50),
  student_id          VARCHAR(30),
  phone               VARCHAR(20),
  qq                  VARCHAR(20),
  first_department    VARCHAR(50),
  second_department   VARCHAR(50),
  skills              TEXT,
  introduction        TEXT,
  free_time           VARCHAR(200),
  accept_transfer     SMALLINT     NOT NULL DEFAULT 1,
  global_number       INTEGER,
  group_name          VARCHAR(10),
  group_number        INTEGER,
  status              VARCHAR(30)  NOT NULL DEFAULT 'pre_registered',
  score_total         INTEGER      NOT NULL DEFAULT 0,
  accept_adjust       VARCHAR(10)  NOT NULL DEFAULT '否',
  second_interviewer  VARCHAR(50),
  second_score        DECIMAL(6,2),
  second_comment      TEXT,
  final_department    VARCHAR(50),
  _created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  _updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_candidates_status     ON candidates(status);
CREATE INDEX IF NOT EXISTS idx_candidates_group_name ON candidates(group_name);
CREATE INDEX IF NOT EXISTS idx_candidates_name      ON candidates(name);
CREATE INDEX IF NOT EXISTS idx_candidates_phone      ON candidates(phone);

-- Interviewer scores table
-- round: first = 一面评分；second = 二面评分（二面复用 A-D 三席打分，与一面评分按 round 区分）
CREATE TABLE IF NOT EXISTS interviewer_scores (
  id                VARCHAR(36) NOT NULL PRIMARY KEY,
  candidate_id      VARCHAR(36) NOT NULL,
  interviewer_name  VARCHAR(50) NOT NULL,
  group_name        VARCHAR(10) NOT NULL,
  round             VARCHAR(10) NOT NULL DEFAULT 'first',
  score             DECIMAL(6,2) NOT NULL,
  judge_comment     TEXT,
  _created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  _updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_candidate_interviewer_round UNIQUE (candidate_id, interviewer_name, round)
);

CREATE INDEX IF NOT EXISTS idx_interviewer_scores_candidate_id ON interviewer_scores(candidate_id);
CREATE INDEX IF NOT EXISTS idx_interviewer_scores_interviewer  ON interviewer_scores(interviewer_name);

-- 工作人员账号表：admin / interviewer(A-D) / floating；口令加盐哈希，种子数据由后端启动时初始化
CREATE TABLE IF NOT EXISTS staff (
  id            VARCHAR(36)  NOT NULL PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  role          VARCHAR(20)  NOT NULL,
  group_name    VARCHAR(10),
  password      VARCHAR(255) NOT NULL,
  _created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  _updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_staff_role ON staff(role);

-- Interviewers are hardcoded in backend config (InterviewersConfig), not stored in DB
-- Admin account: admin (hardcoded in backend auth module)
