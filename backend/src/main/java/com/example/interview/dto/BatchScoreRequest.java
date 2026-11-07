package com.example.interview.dto;

import lombok.Data;

import java.util.List;

/**
 * 三席位批量打分：一次提交本组三名面试官的分数与备注。
 */
@Data
public class BatchScoreRequest {
    private String operatorName;
    private List<ScoreRequest> scores;
}
