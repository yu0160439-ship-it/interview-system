package com.example.interview.dto;

import lombok.Data;

@Data
public class CheckInRequest {
    private String candidateId;
    private String operatorName;
}
