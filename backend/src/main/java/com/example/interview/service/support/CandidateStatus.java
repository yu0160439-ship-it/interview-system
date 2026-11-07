package com.example.interview.service.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 面试状态机（与原 Cloudflare 版本一致，并统一了 'completed' 的历史遗留：
 * 一面完成后状态为 first_completed，统计口径全部按 first_completed 计算）。
 */
public final class CandidateStatus {

    public static final String PRE_REGISTERED = "pre_registered";
    public static final String WAITING = "waiting";
    public static final String INTERVIEWING = "interviewing";
    public static final String PENDING_APPROVAL = "pending_approval";
    public static final String FIRST_COMPLETED = "first_completed";
    public static final String SECOND_WAITING = "second_waiting";
    public static final String SECOND_INTERVIEWING = "second_interviewing";
    public static final String SECOND_COMPLETED = "second_completed";
    public static final String FINAL_ADMITTED = "final_admitted";
    public static final String FINAL_REJECTED = "final_rejected";
    public static final String ABSENT = "absent";

    private static final Set<String> VALID = Set.of(
            PRE_REGISTERED, WAITING, INTERVIEWING, PENDING_APPROVAL, FIRST_COMPLETED,
            SECOND_WAITING, SECOND_INTERVIEWING, SECOND_COMPLETED,
            FINAL_ADMITTED, FINAL_REJECTED, ABSENT);

    private static final Map<String, List<String>> TRANSITIONS = new LinkedHashMap<>();

    static {
        TRANSITIONS.put(PRE_REGISTERED, List.of(WAITING));
        TRANSITIONS.put(WAITING, List.of(INTERVIEWING, FIRST_COMPLETED));
        TRANSITIONS.put(INTERVIEWING, List.of(PENDING_APPROVAL, FIRST_COMPLETED));
        TRANSITIONS.put(PENDING_APPROVAL, List.of(FIRST_COMPLETED));
        TRANSITIONS.put(FIRST_COMPLETED, List.of(SECOND_WAITING));
        TRANSITIONS.put(SECOND_WAITING, List.of(SECOND_INTERVIEWING));
        TRANSITIONS.put(SECOND_INTERVIEWING, List.of(SECOND_COMPLETED));
        TRANSITIONS.put(SECOND_COMPLETED, List.of());
        // 以下为历史遗留终态，保留兼容旧数据，新流程不再产生
        TRANSITIONS.put(FINAL_ADMITTED, List.of());
        TRANSITIONS.put(FINAL_REJECTED, List.of());
        TRANSITIONS.put(ABSENT, List.of());
    }

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(PRE_REGISTERED, "预报名");
        LABELS.put(WAITING, "待面试");
        LABELS.put(INTERVIEWING, "面试中");
        LABELS.put(PENDING_APPROVAL, "待审批");
        LABELS.put(FIRST_COMPLETED, "一面完成");
        LABELS.put(SECOND_WAITING, "二面待面试");
        LABELS.put(SECOND_INTERVIEWING, "二面面试中");
        LABELS.put(SECOND_COMPLETED, "二面完成");
        LABELS.put(FINAL_ADMITTED, "录取");
        LABELS.put(FINAL_REJECTED, "淘汰");
        LABELS.put(ABSENT, "缺考");
    }

    private CandidateStatus() {
    }

    public static boolean isValid(String status) {
        return status != null && VALID.contains(status);
    }

    public static List<String> allowedNext(String status) {
        return TRANSITIONS.getOrDefault(status, List.of());
    }

    public static String label(String status) {
        return LABELS.getOrDefault(status, status);
    }
}
