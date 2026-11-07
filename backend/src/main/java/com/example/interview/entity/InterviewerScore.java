package com.example.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 打分明细实体。
 */
@Data
@TableName("interviewer_scores")
public class InterviewerScore {

    @TableId(type = IdType.NONE)
    private String id;

    private String candidateId;
    private String interviewerName;
    private String groupName;
    /** 场次：first 一面 / second 二面 */
    private String round;
    private BigDecimal score;
    private String judgeComment;

    @TableField("_created_at")
    private LocalDateTime createdAt;

    @JsonIgnore
    @TableField("_updated_at")
    private LocalDateTime updatedAt;
}
