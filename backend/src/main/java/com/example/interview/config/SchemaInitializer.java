package com.example.interview.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时自动创建业务表（IF NOT EXISTS）。
 * 便于外部空库直接上线：无需手动导入 DDL；表已存在时跳过，不做结构变更。
 * staff 表由 {@link StaffInitializer} 负责初始化。
 */
@Order(1)
@Component
public class SchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public SchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
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
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_candidates_status     ON candidates (status)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_candidates_group_name ON candidates (group_name)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_candidates_name        ON candidates (name)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_candidates_phone       ON candidates (phone)");

        jdbcTemplate.execute("""
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
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_interviewer_scores_candidate_id   ON interviewer_scores (candidate_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_interviewer_scores_interviewer    ON interviewer_scores (interviewer_name)");

        log.info("业务表结构校验完成（candidates / interviewer_scores，已存在则跳过）");
    }
}
