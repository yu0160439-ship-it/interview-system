package com.example.interview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 面试官/管理员配置。
 * 分组约定：A/B/C/D = 一面 & 二面面试官（二面复用 A-D 三席名单）；FLOAT = 流动人员。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.interviewers")
public class InterviewersConfig {

    private String adminName = "admin";
    private String adminPassword = "change-me";
    private String deleteAllPassword = "change-me";
    private List<Member> members = new ArrayList<>();

    @Data
    public static class Member {
        private String name;
        private String group;
    }

    /** A/B/C/D 组面试官（一面与二面共用） */
    public List<String> firstRoundInterviewers() {
        return members.stream()
                .filter(m -> List.of("A", "B", "C", "D").contains(m.getGroup()))
                .map(Member::getName)
                .toList();
    }

    /** 流动人员 */
    public List<String> floatingStaff() {
        return members.stream()
                .filter(m -> "FLOAT".equals(m.getGroup()))
                .map(Member::getName)
                .toList();
    }

    public Member findMember(String name) {
        return members.stream().filter(m -> m.getName().equals(name)).findFirst().orElse(null);
    }
}
