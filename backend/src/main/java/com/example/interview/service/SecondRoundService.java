package com.example.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.interview.common.ApiException;
import com.example.interview.common.PasswordUtil;
import com.example.interview.config.InterviewersConfig;
import com.example.interview.config.JwtUtil;
import com.example.interview.dto.AssignGroupRequest;
import com.example.interview.dto.OperatorRequest;
import com.example.interview.dto.SecondStartRequest;
import com.example.interview.dto.ToggleModeRequest;
import com.example.interview.entity.Candidate;
import com.example.interview.entity.InterviewerScore;
import com.example.interview.entity.Staff;
import com.example.interview.mapper.CandidateMapper;
import com.example.interview.mapper.StaffMapper;
import com.example.interview.service.support.CandidateStatus;
import com.example.interview.service.support.ScreenStateService;
import com.example.interview.service.support.SseHub;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 二面业务：复用一面 A-D 三席位打分模式。
 * 流程：一面完成(first_completed) → 管理员推进(second_waiting) → 二面扫码签到 → 分配 A-D 组
 *       → 叫号(second_interviewing) → 三位二面官打分+备注打满 → second_completed。
 * 二面只打分与备注，不产生录取/淘汰/部门结论。
 */
@Service
public class SecondRoundService {

    private static final List<String> SECOND_GROUPS = List.of("A", "B", "C", "D");

    private final CandidateMapper candidateMapper;
    private final InterviewersConfig config;
    private final ScreenStateService screenState;
    private final JwtUtil jwtUtil;
    private final CandidateService candidateService;
    private final SseHub sseHub;
    private final StaffMapper staffMapper;

    public SecondRoundService(CandidateMapper candidateMapper,
                              InterviewersConfig config,
                              ScreenStateService screenState,
                              JwtUtil jwtUtil,
                              CandidateService candidateService,
                              SseHub sseHub,
                              StaffMapper staffMapper) {
        this.candidateMapper = candidateMapper;
        this.config = config;
        this.screenState = screenState;
        this.jwtUtil = jwtUtil;
        this.candidateService = candidateService;
        this.sseHub = sseHub;
        this.staffMapper = staffMapper;
    }

    /** 二面面试官登录：校验账号口令；二面官沿用 A-D 名单，随当前场次进入工作台。 */
    public Map<String, Object> secondLogin(String name, String password) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.badRequest("请输入账号");
        }
        Staff staff = staffMapper.selectOne(
                new LambdaQueryWrapper<Staff>().eq(Staff::getUsername, trimmed));
        if (staff == null || !PasswordUtil.matches(password, staff.getPassword())) {
            throw ApiException.badRequest("账号或密码错误");
        }
        boolean isAdmin = "admin".equals(staff.getRole());
        if (!isAdmin && (!"interviewer".equals(staff.getRole()) || !SECOND_GROUPS.contains(staff.getGroupName()))) {
            throw ApiException.badRequest("二面面试官身份未登记");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("name", trimmed);
        result.put("role", isAdmin ? "admin" : "interviewer");
        result.put("isAdmin", isAdmin);
        result.put("groupName", staff.getGroupName());
        result.put("token", jwtUtil.generate(trimmed, isAdmin ? "admin" : "interviewer",
                staff.getGroupName(), isAdmin));
        return result;
    }

    /** 二面候场队列（A-D，带一面/二面评分明细），按状态拆分为 waiting / interviewing。面试官仅见本组。 */
    public Map<String, List<Candidate>> getSecondRoundQueue() {
        Staff staff = currentStaff();
        List<Candidate> rows = candidateMapper.selectList(
                new LambdaQueryWrapper<Candidate>()
                        .in(Candidate::getGroupName, SECOND_GROUPS)
                        .in(Candidate::getStatus, CandidateStatus.SECOND_WAITING,
                                CandidateStatus.SECOND_INTERVIEWING)
                        .orderByAsc(Candidate::getGroupNumber)
                        .orderByAsc(Candidate::getGlobalNumber));
        if (!"admin".equals(staff.getRole())) {
            if (!"interviewer".equals(staff.getRole()) || !SECOND_GROUPS.contains(staff.getGroupName())) {
                throw ApiException.forbidden("流动人员不可进入面试工作台");
            }
            candidateService.requireRound(staff.getUsername(), ScreenStateService.MODE_SECOND);
            rows.removeIf(c -> !staff.getGroupName().equals(c.getGroupName()));
        }
        Map<String, List<InterviewerScore>> scoreMap =
                candidateService.scoreMapOf(rows.stream().map(Candidate::getId).toList());
        List<Candidate> voList = candidateService.toVOList(rows, scoreMap);
        List<Candidate> waiting = new ArrayList<>();
        List<Candidate> interviewing = new ArrayList<>();
        for (Candidate c : voList) {
            if (CandidateStatus.SECOND_INTERVIEWING.equals(c.getStatus())) {
                interviewing.add(c);
            } else {
                waiting.add(c);
            }
        }
        Map<String, List<Candidate>> out = new LinkedHashMap<>();
        out.put("waiting", waiting);
        out.put("interviewing", interviewing);
        return out;
    }

    /** 推进到二面（一面完成 -> 二面待面试），清空组别待二面重新分配。 */
    @Transactional
    public Candidate moveToSecondRound(String candidateId, OperatorRequest req) {
        if (candidateId == null || candidateId.isBlank()) {
            throw ApiException.badRequest("缺少考生ID");
        }
        requireAdmin();
        Candidate existing = candidateService.mustFind(candidateId);
        if (!CandidateStatus.FIRST_COMPLETED.equals(existing.getStatus())) {
            throw ApiException.badRequest("仅一面完成状态可推进到二面");
        }
        String oldGroup = existing.getGroupName();
        existing.setStatus(CandidateStatus.SECOND_WAITING);
        existing.setGroupName(null);
        existing.setGroupNumber(0);
        existing.setUpdatedAt(LocalDateTime.now());
        candidateMapper.update(null, new LambdaUpdateWrapper<Candidate>()
                .eq(Candidate::getId, existing.getId())
                .set(Candidate::getStatus, existing.getStatus())
                .set(Candidate::getGroupName, null)
                .set(Candidate::getGroupNumber, existing.getGroupNumber())
                .set(Candidate::getUpdatedAt, existing.getUpdatedAt()));
        if (oldGroup != null) {
            candidateService.recalcGroupNumbers(oldGroup);
        }
        candidateService.clearCache();
        sseHub.broadcastData("move-second");
        return candidateService.toVO(existing,
                candidateService.scoreMapOf(List.of(candidateId)).getOrDefault(candidateId, List.of()));
    }

    /** 二面分配组别（A-D）。 */
    @Transactional
    public Candidate assignSecondGroup(AssignGroupRequest req) {
        if (req.getCandidateId() == null || req.getCandidateId().isBlank()) {
            throw ApiException.badRequest("缺少考生ID");
        }
        if (req.getGroupName() == null || req.getGroupName().isBlank()) {
            throw ApiException.badRequest("缺少组别");
        }
        candidateService.requireAdminOrFloating(candidateService.effectiveOperator(req.getOperatorName()));
        if (!SECOND_GROUPS.contains(req.getGroupName())) {
            throw ApiException.badRequest("无效的二面组别，仅支持 A/B/C/D");
        }
        Candidate existing = candidateService.mustFind(req.getCandidateId());
        if (!CandidateStatus.SECOND_WAITING.equals(existing.getStatus())) {
            throw ApiException.badRequest("仅二面待面试状态可分配二面组别");
        }
        String oldGroup = existing.getGroupName();
        existing.setGroupName(req.getGroupName());
        existing.setGroupNumber(0);
        existing.setUpdatedAt(LocalDateTime.now());
        candidateMapper.updateById(existing);
        candidateService.recalcGroupNumbers(req.getGroupName());
        if (oldGroup != null && !oldGroup.equals(req.getGroupName())) {
            candidateService.recalcGroupNumbers(oldGroup);
        }
        candidateService.clearCache();
        sseHub.broadcastData("second-assign");
        return candidateService.toVO(existing,
                candidateService.scoreMapOf(List.of(existing.getId())).getOrDefault(existing.getId(), List.of()));
    }

    /** 二面签到（学生扫码）：仅对已推进到 second_waiting 的考生幂等签到。 */
    @Transactional
    public Candidate secondCheckInPublic(String candidateId) {
        if (candidateId == null || candidateId.isBlank()) {
            throw ApiException.badRequest("缺少考生ID");
        }
        Candidate existing = candidateService.mustFind(candidateId);
        String current = existing.getStatus();
        if (CandidateStatus.SECOND_WAITING.equals(current)
                || CandidateStatus.SECOND_INTERVIEWING.equals(current)
                || CandidateStatus.SECOND_COMPLETED.equals(current)) {
            return candidateService.toVO(existing,
                    candidateService.scoreMapOf(List.of(candidateId)).getOrDefault(candidateId, List.of()));
        }
        throw ApiException.badRequest("您还未被安排进入二面，请等待工作人员通知");
    }

    /** 二面开始面试：同组内当前仅允许一位考生面试中，不同组可并行。 */
    @Transactional
    public Candidate secondRoundStart(SecondStartRequest req) {
        if (req.getCandidateId() == null || req.getCandidateId().isBlank()) {
            throw ApiException.badRequest("缺少考生ID");
        }
        String interviewer = candidateService.effectiveOperator(req.getInterviewerName());
        String role = candidateService.roleOf(interviewer);
        boolean isAdmin = "admin".equals(role);
        Staff staff = isAdmin ? null : candidateService.findStaff(interviewer);
        if (!isAdmin) {
            if (staff == null || !"interviewer".equals(staff.getRole()) || !SECOND_GROUPS.contains(staff.getGroupName())) {
                throw ApiException.forbidden("无权限操作，仅二面面试官可开始二面");
            }
            candidateService.requireRound(interviewer, ScreenStateService.MODE_SECOND);
        }
        Candidate existing = candidateService.mustFind(req.getCandidateId());
        if (!CandidateStatus.SECOND_WAITING.equals(existing.getStatus())) {
            throw ApiException.badRequest("该考生当前状态不可开始二面");
        }
        if (existing.getGroupName() == null) {
            throw ApiException.badRequest("考生尚未分配二面组别，无法叫号");
        }
        if (!isAdmin && !existing.getGroupName().equals(staff.getGroupName())) {
            throw ApiException.badRequest("只能叫号本组考生");
        }
        Long active = candidateMapper.selectCount(new LambdaQueryWrapper<Candidate>()
                .eq(Candidate::getGroupName, existing.getGroupName())
                .eq(Candidate::getStatus, CandidateStatus.SECOND_INTERVIEWING));
        if (active > 0) {
            throw ApiException.badRequest("本组已有考生正在二面面试中，请先完成当前考生");
        }
        existing.setStatus(CandidateStatus.SECOND_INTERVIEWING);
        existing.setUpdatedAt(LocalDateTime.now());
        candidateMapper.updateById(existing);
        candidateService.clearCache();
        sseHub.broadcastData("second-start");
        return candidateService.toVO(existing,
                candidateService.scoreMapOf(List.of(existing.getId())).getOrDefault(existing.getId(), List.of()));
    }

    /** 二面统计（A-D，只统计二面场次）。 */
    public Map<String, Object> secondRoundStats() {
        List<Candidate> rows = candidateMapper.selectList(
                new LambdaQueryWrapper<Candidate>().in(Candidate::getGroupName, SECOND_GROUPS)
                        .in(Candidate::getStatus, CandidateStatus.SECOND_WAITING,
                                CandidateStatus.SECOND_INTERVIEWING, CandidateStatus.SECOND_COMPLETED,
                                CandidateStatus.FINAL_ADMITTED, CandidateStatus.FINAL_REJECTED));
        long waiting = rows.stream().filter(r -> CandidateStatus.SECOND_WAITING.equals(r.getStatus())).count();
        long interviewing = rows.stream().filter(r -> CandidateStatus.SECOND_INTERVIEWING.equals(r.getStatus())).count();
        long completed = rows.stream().filter(r -> CandidateStatus.SECOND_COMPLETED.equals(r.getStatus())).count();

        Map<String, List<InterviewerScore>> scoreMap =
                candidateService.scoreMapOf(rows.stream().map(Candidate::getId).toList());
        BigDecimal sum = BigDecimal.ZERO;
        int n = 0;
        for (Candidate r : rows) {
            List<InterviewerScore> second = scoreMap.getOrDefault(r.getId(), List.of()).stream()
                    .filter(s -> "second".equals(s.getRound()) && s.getScore() != null)
                    .toList();
            if (!second.isEmpty()) {
                BigDecimal s = second.stream().map(InterviewerScore::getScore).reduce(BigDecimal.ZERO, BigDecimal::add);
                sum = sum.add(s.divide(BigDecimal.valueOf(second.size()), 2, RoundingMode.HALF_UP));
                n++;
            }
        }
        BigDecimal avg = n > 0 ? sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("completed", completed);
        result.put("waiting", waiting);
        result.put("interviewing", interviewing);
        result.put("averageSecondScore", avg);
        return result;
    }

    /** 候场大屏显示模式。 */
    public Map<String, Object> getDisplayMode(OperatorRequest req) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", screenState.getDisplayMode());
        return result;
    }

    @Transactional
    public Map<String, Object> toggleDisplayMode(ToggleModeRequest req) {
        requireAdmin();
        if (req.getMode() == null || req.getMode().isBlank()) {
            throw ApiException.badRequest("缺少 mode 参数");
        }
        if (!ScreenStateService.MODE_FIRST.equals(req.getMode())
                && !ScreenStateService.MODE_SECOND.equals(req.getMode())) {
            throw ApiException.badRequest("mode 仅支持 first / second");
        }
        screenState.setDisplayMode(req.getMode());
        candidateService.clearCache();
        sseHub.broadcastMode(req.getMode());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", req.getMode());
        return result;
    }

    /** 仅供 SSE 长连接读取当前模式，不做管理员鉴权（大屏为公开页面）。 */
    public String currentDisplayMode() {
        return screenState.getDisplayMode();
    }

    private void requireAdmin() {
        candidateService.requireAdmin(candidateService.effectiveOperator(null));
    }

    /** 队列只接受当前认证账号，禁止匿名或已删除账号获得全部分组数据。 */
    private Staff currentStaff() {
        String name = candidateService.effectiveOperator(null);
        Staff staff = staffMapper.selectOne(
                new LambdaQueryWrapper<Staff>().eq(Staff::getUsername, name.trim()));
        if (staff == null) {
            throw ApiException.forbidden("工作人员身份无效，请重新登录");
        }
        return staff;
    }
}
