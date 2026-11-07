package com.example.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作人员账号：管理员 / 面试官（A-D 组）/ 流动人员。
 * 登录账号为 username（姓名），口令以加盐哈希存储。
 */
@Data
@TableName("staff")
public class Staff {

    @TableId(type = IdType.NONE)
    private String id;

    /** 登录账号（姓名），唯一 */
    private String username;

    /** 角色：admin / interviewer / floating */
    private String role;

    /** 分组：A / B / C / D / FLOAT；管理员为空 */
    private String groupName;

    @JsonIgnore
    private String password;

    @TableField("_created_at")
    private LocalDateTime createdAt;

    @JsonIgnore
    @TableField("_updated_at")
    private LocalDateTime updatedAt;
}
