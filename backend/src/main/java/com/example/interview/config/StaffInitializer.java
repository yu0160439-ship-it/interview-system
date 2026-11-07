package com.example.interview.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.interview.common.PasswordUtil;
import com.example.interview.entity.Staff;
import com.example.interview.mapper.StaffMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 启动时初始化工作人员账号：自动建表，仅当 staff 表为空时写入种子数据。
 * 管理员口令取配置 admin-password；面试官 / 流动人员统一初始密码 {@link #DEFAULT_INITIAL_PASSWORD}。
 */
@Component
public class StaffInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaffInitializer.class);
    public static final String DEFAULT_INITIAL_PASSWORD = "123456";

    private final StaffMapper staffMapper;
    private final InterviewersConfig config;
    private final JdbcTemplate jdbcTemplate;

    public StaffInitializer(StaffMapper staffMapper, InterviewersConfig config, JdbcTemplate jdbcTemplate) {
        this.staffMapper = staffMapper;
        this.config = config;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureTable();

        Long count = staffMapper.selectCount(new LambdaQueryWrapper<>());
        if (count != null && count > 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // 管理员
        insert(config.getAdminName(), "admin", null, config.getAdminPassword(), now);

        // 面试官（A-D）与流动人员（FLOAT），名单沿用配置；统一初始密码
        for (InterviewersConfig.Member m : config.getMembers()) {
            String role = "FLOAT".equals(m.getGroup()) ? "floating" : "interviewer";
            insert(m.getName(), role, m.getGroup(), DEFAULT_INITIAL_PASSWORD, now);
        }

        log.info("工作人员种子账号初始化完成：admin 1 名，面试官与流动人员 {} 名", config.getMembers().size());
    }

    private void ensureTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS staff (
                  id            VARCHAR(36)  NOT NULL PRIMARY KEY,
                  username      VARCHAR(50)  NOT NULL UNIQUE,
                  role          VARCHAR(20)  NOT NULL,
                  group_name    VARCHAR(10),
                  password      VARCHAR(255) NOT NULL,
                  _created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  _updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void insert(String username, String role, String groupName, String rawPassword, LocalDateTime now) {
        Staff staff = new Staff();
        staff.setId(UUID.randomUUID().toString());
        staff.setUsername(username);
        staff.setRole(role);
        staff.setGroupName(groupName);
        staff.setPassword(PasswordUtil.hash(rawPassword));
        staff.setCreatedAt(now);
        staff.setUpdatedAt(now);
        staffMapper.insert(staff);
    }
}
