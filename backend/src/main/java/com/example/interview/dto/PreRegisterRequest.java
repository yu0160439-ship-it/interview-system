package com.example.interview.dto;

import lombok.Data;

/**
 * 预报名请求（管理员创建 / 公开表单共用；force 仅公开接口使用）。
 */
@Data
public class PreRegisterRequest {
    private String name;
    private String gender;
    private String className;
    private String phone;
    private String firstDepartment;
    private String secondDepartment;
    private String acceptAdjust;
    private boolean force;
    // 可选扩展字段（表中有列，原版前端可能提交）
    private String studentId;
    private String qq;
    private String skills;
    private String introduction;
    private String freeTime;
}
