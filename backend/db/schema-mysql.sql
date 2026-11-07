-- Student Club Interview Queuing System - MySQL Schema (Spring Boot version)
-- Field-aligned with the original Cloudflare D1 schema

CREATE DATABASE IF NOT EXISTS interview_system
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE interview_system;

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
  accept_transfer     TINYINT      NOT NULL DEFAULT 1,
  global_number       INT,
  group_name          VARCHAR(10),
  group_number        INT,
  status              VARCHAR(30)  NOT NULL DEFAULT 'pre_registered',
  score_total         INT          NOT NULL DEFAULT 0,
  accept_adjust       VARCHAR(10)  NOT NULL DEFAULT '否',
  second_interviewer  VARCHAR(50),
  second_score        DECIMAL(6,2),
  second_comment      TEXT,
  final_department    VARCHAR(50),
  _created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  _updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_candidates_status      ON candidates(status);
CREATE INDEX idx_candidates_group_name  ON candidates(group_name);
CREATE INDEX idx_candidates_name        ON candidates(name);
CREATE INDEX idx_candidates_phone       ON candidates(phone);

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
  _created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  _updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_candidate_interviewer_round (candidate_id, interviewer_name, round)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 旧库升级（已存在旧表时执行一次）：
-- ALTER TABLE interviewer_scores ADD COLUMN round VARCHAR(10) NOT NULL DEFAULT 'first' AFTER group_name;
-- ALTER TABLE interviewer_scores DROP INDEX uk_candidate_interviewer;
-- ALTER TABLE interviewer_scores ADD UNIQUE KEY uk_candidate_interviewer_round (candidate_id, interviewer_name, round);

CREATE INDEX idx_interviewer_scores_candidate_id ON interviewer_scores(candidate_id);
CREATE INDEX idx_interviewer_scores_interviewer  ON interviewer_scores(interviewer_name);

-- 工作人员账号表：admin / interviewer(A-D) / floating；口令加盐哈希，种子数据由后端启动时初始化
CREATE TABLE IF NOT EXISTS staff (
  id            VARCHAR(36)  NOT NULL PRIMARY KEY,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  role          VARCHAR(20)  NOT NULL,
  group_name    VARCHAR(10),
  password      VARCHAR(255) NOT NULL,
  _created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  _updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_staff_role ON staff(role);

-- Interviewers are hardcoded in backend config (InterviewersConfig), not stored in DB
-- Admin account: admin (hardcoded in backend auth module)
