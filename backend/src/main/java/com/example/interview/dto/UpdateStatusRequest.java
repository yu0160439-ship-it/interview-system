package com.example.interview.dto;

import lombok.Data;

@Data
public class UpdateStatusRequest {
    private String status;
    private String interviewerName;
}
