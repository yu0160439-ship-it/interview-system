package com.example.interview.dto;

import lombok.Data;

@Data
public class AdmitDecisionRequest {
    private String candidateId;
    /** admitted=录取，rejected=淘汰 */
    private String decision;
    /** 录取部门（decision=admitted 时必填，限部门A/部门B/部门C） */
    private String finalDepartment;
    private String operatorName;
}
