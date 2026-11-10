package com.example.interview.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.interview.common.ApiException;
import com.example.interview.config.AuthContext;
import com.example.interview.config.AuthUser;
import com.example.interview.config.InterviewersConfig;
import com.example.interview.config.JwtUtil;
import com.example.interview.dto.OperatorRequest;
import com.example.interview.dto.SecondStartRequest;
import com.example.interview.dto.ToggleModeRequest;
import com.example.interview.entity.Candidate;
import com.example.interview.entity.Staff;
import com.example.interview.mapper.CandidateMapper;
import com.example.interview.mapper.InterviewerScoreMapper;
import com.example.interview.mapper.StaffMapper;
import com.example.interview.service.support.ScreenStateService;
import com.example.interview.service.support.SseHub;
import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecondRoundServiceTests {
    private MybatisConfiguration configuration;
    private CandidateMapper candidateMapper;
    private StaffMapper staffMapper;
    private CandidateService candidateService;
    private SecondRoundService service;
    private ScreenStateService screenState;

    @BeforeEach
    void setUp() {
        configuration = new MybatisConfiguration();
        configuration.addMapper(CandidateMapper.class);
        configuration.addMapper(StaffMapper.class);
        candidateMapper = mock(CandidateMapper.class);
        staffMapper = mock(StaffMapper.class);
        InterviewerScoreMapper scoreMapper = mock(InterviewerScoreMapper.class);
        when(scoreMapper.selectList(any())).thenReturn(List.of());
        when(candidateMapper.selectList(any())).thenReturn(List.of());
        InterviewersConfig interviewers = new InterviewersConfig();
        screenState = new ScreenStateService();
        JwtUtil jwt = mock(JwtUtil.class);
        SseHub hub = mock(SseHub.class);
        candidateService = spy(new CandidateService(candidateMapper, scoreMapper, interviewers, screenState, jwt, staffMapper, hub));
        service = new SecondRoundService(candidateMapper, interviewers, screenState, jwt, candidateService, hub, staffMapper);
    }

    @AfterEach
    void clearIdentity() {
        AuthContext.clear();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void movingToSecondRoundWritesNullGroupAndReordersPreviousGroup() {
        authenticate("admin", "admin", null);
        Candidate candidate = new Candidate();
        candidate.setId("candidate-1");
        candidate.setStatus("first_completed");
        candidate.setGroupName("A");
        candidate.setGroupNumber(4);
        when(candidateMapper.selectById(candidate.getId())).thenReturn(candidate);
        OperatorRequest request = new OperatorRequest();
        request.setOperatorName("ignored-client-name");

        Candidate result = service.moveToSecondRound(candidate.getId(), request);

        ArgumentCaptor<LambdaUpdateWrapper<Candidate>> wrapper = ArgumentCaptor.forClass((Class) LambdaUpdateWrapper.class);
        verify(candidateMapper).update(isNull(), wrapper.capture());
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("et", null);
        parameters.put("ew", wrapper.getValue());
        BoundSql boundSql = configuration.getMappedStatement(CandidateMapper.class.getName() + ".update").getBoundSql(parameters);
        String sql = boundSql.getSql().replaceAll("\\s+", " ");
        int groupPosition = sql.indexOf("group_name");
        assertTrue(groupPosition >= 0, "Moving rounds must explicitly write the group column");
        int parameterPosition = (int) sql.substring(0, groupPosition).chars().filter(ch -> ch == '?').count();
        String property = boundSql.getParameterMappings().get(parameterPosition).getProperty();
        assertNull(configuration.newMetaObject(parameters).getValue(property), "The group column must bind SQL NULL");
        assertEquals("second_waiting", result.getStatus());
        assertNull(result.getGroupName());
        assertEquals(0, result.getGroupNumber());
        verify(candidateService).recalcGroupNumbers("A");
        verify(candidateMapper, never()).updateById(any(Candidate.class));
    }

    @Test
    void clientAdminNameCannotElevateFloatingUserToChangeDisplayMode() {
        authenticate("floating-user", "floating", "FLOAT");
        ToggleModeRequest request = new ToggleModeRequest();
        request.setOperatorName("admin");
        request.setMode("second");

        ApiException failure = assertThrows(ApiException.class, () -> service.toggleDisplayMode(request));

        assertEquals(403, failure.getStatus());
        assertEquals("first", screenState.getDisplayMode());
    }

    @Test
    void clientAdminNameCannotLetInterviewerStartAnotherGroup() {
        authenticate("interviewer-A", "interviewer", "A");
        screenState.setDisplayMode("second");
        Candidate candidate = new Candidate();
        candidate.setId("candidate-B");
        candidate.setStatus("second_waiting");
        candidate.setGroupName("B");
        when(candidateMapper.selectById(candidate.getId())).thenReturn(candidate);
        SecondStartRequest request = new SecondStartRequest();
        request.setCandidateId(candidate.getId());
        request.setInterviewerName("admin");

        assertThrows(ApiException.class, () -> service.secondRoundStart(request));

        assertEquals("second_waiting", candidate.getStatus());
        verify(candidateMapper, never()).updateById(any(Candidate.class));
    }

    @Test
    void displayModeIsPublicReadOnly() {
        screenState.setDisplayMode("second");
        assertEquals("second", service.getDisplayMode(new OperatorRequest()).get("mode"));
    }

    private void authenticate(String name, String role, String group) {
        AuthContext.set(AuthUser.of(name, role, group, "admin".equals(role)));
        Staff staff = new Staff();
        staff.setUsername(name);
        staff.setRole(role);
        staff.setGroupName(group);
        when(staffMapper.selectOne(any())).thenReturn(staff);
    }
}
