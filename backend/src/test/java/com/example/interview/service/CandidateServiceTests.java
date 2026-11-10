package com.example.interview.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.example.interview.common.ApiException;
import com.example.interview.config.AuthContext;
import com.example.interview.config.AuthUser;
import com.example.interview.config.InterviewersConfig;
import com.example.interview.config.JwtUtil;
import com.example.interview.dto.BatchScoreRequest;
import com.example.interview.dto.DeleteAllRequest;
import com.example.interview.dto.PreRegisterRequest;
import com.example.interview.dto.ScoreRequest;
import com.example.interview.entity.Candidate;
import com.example.interview.entity.InterviewerScore;
import com.example.interview.entity.Staff;
import com.example.interview.mapper.CandidateMapper;
import com.example.interview.mapper.InterviewerScoreMapper;
import com.example.interview.mapper.StaffMapper;
import com.example.interview.service.support.ScreenStateService;
import com.example.interview.service.support.SseHub;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CandidateServiceTests {
    private CandidateMapper candidates;
    private InterviewerScoreMapper scores;
    private StaffMapper staff;
    private CandidateService service;
    private InterviewersConfig config;

    @BeforeEach
    void setUp() {
        AuthContext.clear();
        MybatisConfiguration mybatis = new MybatisConfiguration();
        mybatis.addMapper(CandidateMapper.class);
        candidates = mock(CandidateMapper.class);
        scores = mock(InterviewerScoreMapper.class);
        staff = mock(StaffMapper.class);
        config = new InterviewersConfig();
        InterviewersConfig.Member member = new InterviewersConfig.Member();
        member.setName("面试官B1");
        member.setGroup("B");
        config.setMembers(List.of(member));
        service = new CandidateService(candidates, scores, config, new ScreenStateService(),
                mock(JwtUtil.class), staff, mock(SseHub.class));
    }

    @AfterEach
    void clearIdentity() {
        AuthContext.clear();
    }

    @Test
    void anonymousCannotDeleteCandidate() {
        ApiException error = assertThrows(ApiException.class, () -> service.deleteCandidate("candidate"));
        assertEquals(401, error.getStatus());
        verifyNoInteractions(candidates, scores, staff);
    }

    @Test
    void floatingCannotDeleteCandidateOrUseDeleteAllPassword() {
        login("流动人员1", "floating", "FLOAT");
        assertEquals(403, assertThrows(ApiException.class,
                () -> service.deleteCandidate("candidate")).getStatus());
        DeleteAllRequest request = new DeleteAllRequest();
        request.setPassword(config.getDeleteAllPassword());
        assertEquals(403, assertThrows(ApiException.class,
                () -> service.deleteAll(request)).getStatus());
        verifyNoInteractions(candidates, scores);
    }

    @Test
    void adminCanDeleteCandidateAndItsScores() {
        login("admin", "admin", null);
        Candidate candidate = candidate("candidate", "waiting");
        when(candidates.selectById(candidate.getId())).thenReturn(candidate);
        assertEquals(true, service.deleteCandidate(candidate.getId()).get("success"));
        verify(candidates).deleteById(candidate.getId());
        verify(scores).delete(any());
    }

    @Test
    void floatingCannotImpersonateInterviewerInSingleScore() {
        login("流动人员1", "floating", "FLOAT");
        ScoreRequest request = new ScoreRequest();
        request.setInterviewerName("面试官A1");
        request.setScore(9.0);
        assertEquals(403, assertThrows(ApiException.class,
                () -> service.submitScore("candidate", request)).getStatus());
        verifyNoInteractions(candidates, scores);
    }

    @Test
    void interviewerCannotImpersonateAnotherGroupInSingleScore() {
        login("面试官B1", "interviewer", "B");
        Candidate candidate = candidate("candidate", "interviewing");
        candidate.setGroupName("A");
        when(candidates.selectById(candidate.getId())).thenReturn(candidate);
        ScoreRequest request = new ScoreRequest();
        request.setInterviewerName("面试官A1");
        request.setScore(9.0);
        assertEquals(400, assertThrows(ApiException.class,
                () -> service.submitScore(candidate.getId(), request)).getStatus());
        verifyNoInteractions(scores);
        verify(candidates, never()).updateById(any(Candidate.class));
    }

    @Test
    void floatingCannotImpersonateAdminInBatchScore() {
        login("流动人员1", "floating", "FLOAT");
        BatchScoreRequest request = new BatchScoreRequest();
        request.setOperatorName("admin");
        assertEquals(403, assertThrows(ApiException.class,
                () -> service.submitScores("candidate", request)).getStatus());
        verifyNoInteractions(candidates, scores);
    }

    @Test
    void adminCanSubmitASingleScoreForARegisteredSeat() {
        login("admin", "admin", null);
        Candidate candidate = candidate("candidate", "interviewing");
        candidate.setGroupName("B");
        when(candidates.selectById(candidate.getId())).thenReturn(candidate);
        when(scores.selectList(any())).thenReturn(List.of());
        ScoreRequest request = new ScoreRequest();
        request.setInterviewerName("面试官B1");
        request.setScore(9.0);
        service.submitScore(candidate.getId(), request);
        ArgumentCaptor<InterviewerScore> saved = ArgumentCaptor.forClass(InterviewerScore.class);
        verify(scores).insert(saved.capture());
        assertEquals("面试官B1", saved.getValue().getInterviewerName());
        assertEquals("first", saved.getValue().getRound());
    }

    @Test
    void anonymousCannotUseAdminParameterAsIdentity() {
        BatchScoreRequest request = new BatchScoreRequest();
        request.setOperatorName("admin");
        assertEquals(401, assertThrows(ApiException.class,
                () -> service.submitScores("candidate", request)).getStatus());
        verifyNoInteractions(candidates, scores, staff);
    }

    @Test
    void removedAccountCannotReadGroupQueueOrSubmitScores() {
        AuthContext.set(AuthUser.of("面试官B1", "interviewer", "B", false));
        assertEquals(403, assertThrows(ApiException.class,
                () -> service.getGroupQueue("B", "admin")).getStatus());
        ScoreRequest request = new ScoreRequest();
        request.setInterviewerName("面试官B1");
        request.setScore(9.0);
        assertEquals(403, assertThrows(ApiException.class,
                () -> service.submitScore("candidate", request)).getStatus());
        verifyNoInteractions(candidates, scores);
    }

    @Test
    void duplicateRegistrationRequiresConfirmation() {
        when(candidates.selectOne(any())).thenReturn(candidate("existing", "pre_registered"));
        assertEquals(409, assertThrows(ApiException.class,
                () -> service.preRegisterPublic(registration(false))).getStatus());
        verify(candidates, never()).insert(any(Candidate.class));
    }

    @Test
    void confirmedDuplicateReusesRecordAndCurrentStatus() {
        Candidate existing = candidate("existing", "waiting");
        existing.setFirstDepartment("部门B");
        when(candidates.selectOne(any())).thenReturn(existing);
        when(scores.selectList(any())).thenReturn(List.of());
        Candidate result = service.preRegisterPublic(registration(true));
        assertEquals("existing", result.getId());
        assertEquals("waiting", result.getStatus());
        assertEquals("部门B", result.getFirstDepartment());
        verify(candidates, never()).insert(any(Candidate.class));
        verify(candidates, never()).updateById(any(Candidate.class));
    }

    @Test
    void confirmationWithAnotherNameDoesNotReuseOrCreateRecord() {
        Candidate existing = candidate("existing", "waiting");
        existing.setName("另一位考生");
        when(candidates.selectOne(any())).thenReturn(existing);
        assertEquals(409, assertThrows(ApiException.class,
                () -> service.preRegisterPublic(registration(true))).getStatus());
        verify(candidates, never()).insert(any(Candidate.class));
        verifyNoInteractions(scores);
    }

    @ParameterizedTest
    @CsvSource({",,", "部门A,,部门A", ",部门B,部门B", "部门A,部门B,部门A/部门B"})
    void exportAcceptsMissingDepartments(String first, String second, String expected) throws Exception {
        login("admin", "admin", null);
        Candidate candidate = candidate("candidate", "pre_registered");
        candidate.setFirstDepartment(first);
        candidate.setSecondDepartment(second);
        when(candidates.searchList(null, null, null, false, null, null,
                "globalNumber", false, null, 0)).thenReturn(List.of(candidate));
        when(scores.selectList(any())).thenReturn(List.of());
        String csv = service.exportCsv(Map.of());
        assertTrue(csv.startsWith("\uFEFF"));
        try (var parser = CSVFormat.DEFAULT.parse(new StringReader(csv.substring(1)))) {
            var records = parser.getRecords();
            assertEquals(2, records.size());
            assertEquals("测试考生", records.get(1).get(2));
            assertEquals(expected == null ? "" : expected, records.get(1).get(4));
        }
    }

    @Test
    void exportUsesListFiltersWithoutPaginationAndKeepsFirstRoundScores() throws Exception {
        login("admin", "admin", null);
        Candidate candidate = candidate("candidate", "second_completed");
        when(candidates.searchList("测试", "B", "second_completed", false, 8.0, 10.0,
                "globalNumber", false, null, 0)).thenReturn(List.of(candidate));
        InterviewerScore first = new InterviewerScore();
        first.setCandidateId(candidate.getId());
        first.setRound("first"); first.setScore(new BigDecimal("9")); first.setInterviewerName("面试官A1");
        InterviewerScore second = new InterviewerScore();
        second.setCandidateId(candidate.getId());
        second.setRound("second"); second.setScore(new BigDecimal("1")); second.setInterviewerName("面试官B1");
        when(scores.selectList(any())).thenReturn(List.of(first, second));
        String csv = service.exportCsv(Map.of("keyword", "测试", "groupName", "B",
                "status", "second_completed", "minScore", "8", "maxScore", "10"));
        try (var parser = CSVFormat.DEFAULT.parse(new StringReader(csv.substring(1)))) {
            assertEquals("9.00", parser.getRecords().get(1).get(7));
        }
        assertFalse(csv.contains("面试官B1"));
        verify(candidates).searchList("测试", "B", "second_completed", false, 8.0, 10.0,
                "globalNumber", false, null, 0);
    }

    @Test
    void floatingCannotExportResults() {
        login("流动人员1", "floating", "FLOAT");
        assertEquals(403, assertThrows(ApiException.class,
                () -> service.exportCsv(Map.of())).getStatus());
        verifyNoInteractions(candidates, scores);
    }

    private void login(String name, String role, String group) {
        AuthContext.set(AuthUser.of(name, role, group, "admin".equals(role)));
        Staff account = new Staff();
        account.setUsername(name); account.setRole(role); account.setGroupName(group);
        when(staff.selectOne(any())).thenReturn(account);
    }

    private Candidate candidate(String id, String status) {
        Candidate candidate = new Candidate();
        candidate.setId(id); candidate.setName("测试考生"); candidate.setStatus(status);
        return candidate;
    }

    private PreRegisterRequest registration(boolean force) {
        PreRegisterRequest request = new PreRegisterRequest();
        request.setName("测试考生"); request.setGender("男"); request.setClassName("测试班级");
        request.setPhone("13900000001"); request.setFirstDepartment("部门A");
        request.setAcceptAdjust("是"); request.setForce(force);
        return request;
    }
}
