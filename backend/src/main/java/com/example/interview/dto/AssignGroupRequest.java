package com.example.interview.dto;

import lombok.Data;

@Data
public class AssignGroupRequest {
    private String candidateId;
    private String groupName;
    private String operatorName;
}
