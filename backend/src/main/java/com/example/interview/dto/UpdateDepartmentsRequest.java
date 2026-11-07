package com.example.interview.dto;

import lombok.Data;

@Data
public class UpdateDepartmentsRequest {
    private String candidateId;
    private String firstDepartment;
    private String secondDepartment;
}
