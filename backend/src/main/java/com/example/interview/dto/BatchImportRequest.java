package com.example.interview.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BatchImportRequest {
    private List<PreRegisterRequest> items = new ArrayList<>();
    private String operatorName;
}
