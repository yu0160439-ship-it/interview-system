package com.example.interview.dto;

import lombok.Data;

@Data
public class ScoreRequest {
    private Double score;
    private String interviewerName;
    private String judgeComment;
}
