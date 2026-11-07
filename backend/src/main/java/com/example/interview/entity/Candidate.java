package com.example.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 考生实体，字段与原 Cloudflare D1 版本 candidates 表完全对齐。
 * 序列化输出与原版 mapRowToCandidate 契约保持一致，扩展列不对外输出。
 */
@Data
@TableName("candidates")
public class Candidate {

    @TableId(type = IdType.NONE)
    private String id;

    private String name;
    private String gender;
    private String className;

    /** 扩展列（仅入库，不对外输出） */
    @JsonIgnore
    private String studentId;
    private String phone;
    @JsonIgnore
    private String qq;
    private String firstDepartment;
    private String secondDepartment;
    @JsonIgnore
    private String skills;
    @JsonIgnore
    private String introduction;
    @JsonIgnore
    private String freeTime;

    /** 是否服从调剂（0/1），仅入库 */
    @JsonIgnore
    private Integer acceptTransfer;

    /** 全场序号（签到顺序） */
    private Integer globalNumber;

    /** 分配组别 A/B/C/D 或二面 E/F */
    private String groupName;

    /** 组内序号 */
    private Integer groupNumber;

    /** 面试状态，见 CandidateStatus */
    private String status;

    /** 一面平均分 × 10（与原作者保持一致） */
    private Integer scoreTotal;

    /** 是否接受调剂，'是'/'否' */
    private String acceptAdjust;

    private String secondInterviewer;
    private BigDecimal secondScore;
    private String secondComment;
    private String finalDepartment;

    @TableField("_created_at")
    private LocalDateTime createdAt;

    @JsonIgnore
    @TableField("_updated_at")
    private LocalDateTime updatedAt;

    // ===== 计算字段（非表列，由 service 组装）=====
    @TableField(exist = false)
    private BigDecimal averageScore;

    @TableField(exist = false)
    private Integer scoredCount;

    @TableField(exist = false)
    private Integer groupInterviewerCount;

    @TableField(exist = false)
    private List<InterviewerScore> scores = new ArrayList<>();

    /** 二面打分明细（round=second） */
    @TableField(exist = false)
    private List<InterviewerScore> secondScores = new ArrayList<>();

    /** 二面平均分（round=second） */
    @TableField(exist = false)
    private BigDecimal secondAverageScore;

    /** 二面打分人数（round=second） */
    @TableField(exist = false)
    private Integer secondScoredCount;
}
