package com.example.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.interview.common.ApiException;
import com.example.interview.common.PasswordUtil;
import com.example.interview.config.AuthContext;
import com.example.interview.config.AuthUser;
import com.example.interview.config.InterviewersConfig;
import com.example.interview.config.JwtUtil;
import com.example.interview.dto.AdmitDecisionRequest;
import com.example.interview.dto.AssignGroupRequest;
import com.example.interview.dto.BatchImportRequest;
import com.example.interview.dto.BatchScoreRequest;
import com.example.interview.dto.CheckInRequest;
import com.example.interview.dto.DeleteAllRequest;
import com.example.interview.dto.LoginRequest;
import com.example.interview.dto.OperatorRequest;
import com.example.interview.dto.PreRegisterRequest;
import com.example.interview.dto.ScoreRequest;
import com.example.interview.dto.UpdateDepartmentsRequest;
import com.example.interview.dto.UpdateStatusRequest;
import com.example.interview.entity.Candidate;
import com.example.interview.entity.InterviewerScore;
import com.example.interview.entity.Staff;
import com.example.interview.mapper.CandidateMapper;
import com.example.interview.mapper.InterviewerScoreMapper;
import com.example.interview.mapper.StaffMapper;
import com.example.interview.service.support.CandidateStatus;
import com.example.interview.service.support.ScreenStateService;
import com.example.interview.service.support.SseHub;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 一面 + 管理 + 大屏业务逻辑（对应原 Cloudflare Functions 的 candidates-handler.js）。
 * 身份约定：请求携带 Authorization: Bearer token 时优先使用 token 身份；
 * 否则从请求参数 operatorName / interviewerName 读取（兼容旧前端）。
 */
@Slf4j
@Service
public class CandidateService {

    private static final List<String> FIRST_GROUPS = List.of("A", "B", "C", "D");
    private static final List<String> VALID_DEPARTMENTS = List.of("部门A", "部门B", "部门C");

    private final CandidateMapper candidateMapper;
    private final InterviewerScoreMapper scoreMapper;
    private final InterviewersConfig config;
    private final ScreenStateService screenState;
    private final JwtUtil jwtUtil;
    private final StaffMapper staffMapper;
    private final SseHub sseHub;

    public CandidateService(CandidateMapper candidateMapper,
                            InterviewerScoreMapper scoreMapper,
                            InterviewersConfig config,
                            ScreenStateService screenState,
                            JwtUtil jwtUtil,
                            StaffMapper staffMapper,
                            SseHub sseHub) {
        this.candidateMapper = candidateMapper;
        this.scoreMapper = scoreMapper;
        this.config = config;
        this.screenState = screenState;
        this.jwtUtil = jwtUtil;
        this.staffMapper = staffMapper;
        this.sseHub = sseHub;
    }

    /** 业务变更后广播 data 事件，通知各在线页面静默刷新列表。 */
    private void notifyDataChanged(String type) {
        try {
            sseHub.broadcastData(type);
        } catch (Exception e) {
            log.warn("广播数据变更失败: {}", e.getMessage());
        }
    }

    // ==================== 工具方法 ====================

    /** 按登录账号查询工作人员；不存在返回 null。角色 / 分组以 staff 表为准。 */
    public Staff findStaff(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return staffMapper.selectOne(
                new LambdaQueryWrapper<Staff>().eq(Staff::getUsername, name.trim()));
    }

    public String roleOf(String name) {
        Staff staff = findStaff(name);
        return staff == null ? null : staff.getRole();
    }

    public String groupOf(String name) {
        Staff staff = findStaff(name);
        return staff == null ? null : staff.getGroupName();
    }

    /** 工作台场次校验：非管理员只能在当前开放场次进入对应工作台；admin 不受限。 */
    public void requireRound(String operator, String expectedRound) {
        String role = roleOf(operator);
        if (role == null) {
            throw ApiException.badRequest("身份无效，请重新登录");
        }
        if ("admin".equals(role)) {
            return;
        }
        String current = screenState.getDisplayMode();
        if (!expectedRound.equals(current)) {
            String label = ScreenStateService.MODE_SECOND.equals(current) ? "二面" : "一面";
            throw ApiException.forbidden("当前为" + label + "阶段，请进入对应工作台");
        }
    }

    /** 当前请求身份：优先 token，其次显式参数。 */
    private String effectiveOperator(String explicit) {
        AuthUser user = AuthContext.get();
        if (user != null && user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return explicit;
    }

    protected String generateId() {
        return UUID.randomUUID().toString();
    }

    protected LocalDateTime now() {
        return LocalDateTime.now();
    }

    /** 组装 VO：按 round 拆分一面/二面评分，分别计算平均分、已评分人数、组内面试官人数。 */
    public Candidate toVO(Candidate c, List<InterviewerScore> scores) {
        if (c == null) {
            return null;
        }
        List<InterviewerScore> all = scores == null ? List.of() : scores;
        List<InterviewerScore> first = new ArrayList<>();
        List<InterviewerScore> second = new ArrayList<>();
        for (InterviewerScore s : all) {
            if ("second".equals(s.getRound())) {
                second.add(s);
            } else {
                first.add(s);
            }
        }
        BigDecimal[] firstAgg = aggregate(first);
        BigDecimal[] secondAgg = aggregate(second);
        int groupInterviewerCount = c.getGroupName() == null ? 0
                : (int) config.firstRoundInterviewers().stream()
                        .filter(n -> {
                            InterviewersConfig.Member m = config.findMember(n);
                            return m != null && m.getGroup().equals(c.getGroupName());
                        })
                        .count();
        c.setScores(first);
        c.setAverageScore(firstAgg[1]);
        c.setScoredCount(firstAgg[0].intValue());
        c.setSecondScores(second);
        c.setSecondAverageScore(secondAgg[1]);
        c.setSecondScoredCount(secondAgg[0].intValue());
        c.setGroupInterviewerCount(groupInterviewerCount);
        return c;
    }

    /** 返回 [已评分人数, 平均分]；无评分时人数 0、平均分为 null。 */
    private BigDecimal[] aggregate(List<InterviewerScore> rows) {
        int count = 0;
        BigDecimal sum = BigDecimal.ZERO;
        for (InterviewerScore s : rows) {
            if (s.getScore() != null) {
                count++;
                sum = sum.add(s.getScore());
            }
        }
        BigDecimal avg = count > 0
                ? sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                : null;
        return new BigDecimal[]{BigDecimal.valueOf(count), avg};
    }

    public List<Candidate> toVOList(List<Candidate> list, Map<String, List<InterviewerScore>> scoreMap) {
        List<Candidate> out = new ArrayList<>();
        for (Candidate c : list) {
            out.add(toVO(c, scoreMap.getOrDefault(c.getId(), List.of())));
        }
        return out;
    }

    protected Map<String, List<InterviewerScore>> scoreMapOf(List<String> candidateIds) {
        Map<String, List<InterviewerScore>> map = new LinkedHashMap<>();
        if (candidateIds == null || candidateIds.isEmpty()) {
            return map;
        }
        List<InterviewerScore> rows = scoreMapper.selectList(
                new LambdaQueryWrapper<InterviewerScore>()
                        .in(InterviewerScore::getCandidateId, candidateIds)
                        .orderByAsc(InterviewerScore::getCreatedAt));
        for (InterviewerScore s : rows) {
            map.computeIfAbsent(s.getCandidateId(), k -> new ArrayList<>()).add(s);
        }
        return map;
    }

    /** 重新计算某组所有考生的组内序号（严格按全场序号升序，先来先面）。 */
    @Transactional
    public void recalcGroupNumbers(String groupName) {
        List<Candidate> all = candidateMapper.selectList(
                new LambdaQueryWrapper<Candidate>()
                        .eq(Candidate::getGroupName, groupName)
                        .orderByAsc(Candidate::getGlobalNumber)
                        .orderByAsc(Candidate::getCreatedAt));
        for (int i = 0; i < all.size(); i++) {
            Candidate c = all.get(i);
            if (!Integer.valueOf(i + 1).equals(c.getGroupNumber())) {
                c.setGroupNumber(i + 1);
                candidateMapper.updateById(c);
            }
        }
    }

    public void clearCache() {
        screenState.clearScreenCache();
    }

    // ==================== 公开接口 ====================

    /** 管理员创建预报名（仅校验姓名）。 */
    @Transactional
    public Candidate preRegister(PreRegisterRequest dto) {
        String name = trim(dto.getName());
        if (name.isEmpty()) {
            throw ApiException.badRequest("姓名为必填项");
        }
        Candidate c = newCandidate(name, dto);
        candidateMapper.insert(c);
        notifyDataChanged("register");
        return toVO(c, List.of());
    }

    /** 公开预报名（前端表单）。 */
    @Transactional
    public Candidate preRegisterPublic(PreRegisterRequest dto) {
        String name = trim(dto.getName());
        String className = trim(dto.getClassName());
        String phone = trim(dto.getPhone());
        if (name.isEmpty()) {
            throw ApiException.badRequest("姓名为必填项");
        }
        if (dto.getGender() == null || dto.getGender().isBlank()) {
            throw ApiException.badRequest("性别为必填项");
        }
        if (className.isEmpty()) {
            throw ApiException.badRequest("班级为必填项");
        }
        if (phone.isEmpty()) {
            throw ApiException.badRequest("联系电话为必填项");
        }
        if (!phone.matches("1\\d{10}")) {
            throw ApiException.badRequest("请输入正确的11位手机号");
        }
        if (dto.getFirstDepartment() == null || dto.getFirstDepartment().isBlank()) {
            throw ApiException.badRequest("意向部门为必填项");
        }
        if (dto.getAcceptAdjust() == null || dto.getAcceptAdjust().isBlank()) {
            throw ApiException.badRequest("请选择是否服从调剂");
        }

        Candidate existing = candidateMapper.selectOne(
                new LambdaQueryWrapper<Candidate>().eq(Candidate::getPhone, phone).last("LIMIT 1"));
        if (existing != null && !dto.isForce()) {
            throw ApiException.conflict("该手机号已报名，请勿重复提交");
        }

        Candidate c = newCandidate(name, dto);
        if (c.getSecondDepartment() == null || c.getSecondDepartment().isBlank()) {
            c.setSecondDepartment(c.getFirstDepartment());
        }
        candidateMapper.insert(c);
        notifyDataChanged("register");
        return toVO(c, List.of());
    }

    private Candidate newCandidate(String name, PreRegisterRequest dto) {
        Candidate c = new Candidate();
        c.setId(generateId());
        c.setName(name);
        c.setGender(nullSafeTrim(dto.getGender()));
        c.setClassName(nullSafeTrim(dto.getClassName()));
        c.setPhone(nullSafeTrim(dto.getPhone()));
        c.setFirstDepartment(nullSafeTrim(dto.getFirstDepartment()));
        c.setSecondDepartment(nullSafeTrim(dto.getSecondDepartment()));
        c.setAcceptAdjust(dto.getAcceptAdjust() == null || dto.getAcceptAdjust().isBlank()
                ? "否" : dto.getAcceptAdjust().trim());
        c.setStatus(CandidateStatus.PRE_REGISTERED);
        c.setScoreTotal(0);
        // 可选扩展字段
        c.setStudentId(nullSafeTrim(dto.getStudentId()));
        c.setQq(nullSafeTrim(dto.getQq()));
        c.setSkills(nullSafeTrim(dto.getSkills()));
        c.setIntroduction(nullSafeTrim(dto.getIntroduction()));
        c.setFreeTime(nullSafeTrim(dto.getFreeTime()));
        c.setCreatedAt(now());
        c.setUpdatedAt(now());
        return c;
    }

    /** 公开按姓名搜索。 */
    public List<Candidate> searchByNamePublic(String name) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("请输入姓名");
        }
        List<Candidate> rows = candidateMapper.selectList(
                new LambdaQueryWrapper<Candidate>()
                        .eq(Candidate::getName, name.trim())
                        .orderByDesc(Candidate::getCreatedAt));
        return toVOList(rows, scoreMapOf(rows.stream().map(Candidate::getId).toList()));
    }

    /** 公开签到：pre_registered -> waiting，分配全场序号。 */
    @Transactional
    public Candidate checkInPublic(String candidateId) {
        if (candidateId == null || candidateId.isBlank()) {
            throw ApiException.badRequest("缺少考生ID");
        }
        Candidate existing = mustFind(candidateId);
        if (CandidateStatus.WAITING.equals(existing.getStatus())) {
            throw ApiException.badRequest("该考生已签到，请勿重复签到");
        }
        if (!CandidateStatus.PRE_REGISTERED.equals(existing.getStatus())) {
            throw ApiException.badRequest("该考生当前状态为「" + CandidateStatus.label(existing.getStatus()) + "」，无法签到");
        }
        int newGlobal = nextGlobalNumber();
        existing.setStatus(CandidateStatus.WAITING);
        existing.setGlobalNumber(newGlobal);
        existing.setUpdatedAt(now());
        candidateMapper.updateById(existing);
        clearCache();
        notifyDataChanged("checkin");
        return toVO(existing, List.of());
    }

    /** 公开修改意向部门（仅 waiting 状态）。 */
    @Transactional
    public Candidate updateDepartmentsPublic(UpdateDepartmentsRequest dto) {
        if (dto.getCandidateId() == null || dto.getCandidateId().isBlank()) {
            throw ApiException.badRequest("缺少考生ID");
        }
        Candidate existing = mustFind(dto.getCandidateId());
        if (!CandidateStatus.WAITING.equals(existing.getStatus())) {
            throw ApiException.badRequest("仅待面试状态可修改意向部门");
        }
        if (dto.getFirstDepartment() == null || dto.getFirstDepartment().isBlank()) {
            throw ApiException.badRequest("第一意向部门不能为空");
        }
        existing.setFirstDepartment(dto.getFirstDepartment().trim());
        existing.setSecondDepartment(dto.getSecondDepartment() == null ? null : dto.getSecondDepartment().trim());
        existing.setUpdatedAt(now());
        candidateMapper.updateById(existing);
        notifyDataChanged("department");
        return toVO(existing, scoreMapOf(List.of(existing.getId())).getOrDefault(existing.getId(), List.of()));
    }

    /** 公开详情。 */
    public Candidate getDetailPublic(String id) {
        Candidate existing = mustFind(id);
        return toVO(existing, scoreMapOf(List.of(id)).getOrDefault(id, List.of()));
    }

    // ==================== 搜索 / 统计 ====================

    public Map<String, Object> search(Map<String, String> q) {
        int page = parseInt(q.get("page"), 1);
        int pageSize = Math.min(parseInt(q.get("pageSize"), 20), 100);
        int offset = (page - 1) * pageSize;
        String sortBy = q.getOrDefault("sortBy", "score");
        boolean desc = !"asc".equalsIgnoreCase(q.getOrDefault("sortOrder", "desc"));

        Double minScore = parseDoubleOrNull(q.get("minScore"));
        Double maxScore = parseDoubleOrNull(q.get("maxScore"));
        boolean excludeCompleted = "true".equalsIgnoreCase(q.get("excludeCompleted"));

        long total = candidateMapper.searchCount(
                q.get("keyword"), q.get("groupName"), q.get("status"),
                excludeCompleted, minScore, maxScore);
        List<Candidate> rows = candidateMapper.searchList(
                q.get("keyword"), q.get("groupName"), q.get("status"),
                excludeCompleted, minScore, maxScore, sortBy, desc, pageSize, offset);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", toVOList(rows, scoreMapOf(rows.stream().map(Candidate::getId).toList())));
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    public Map<String, Object> stats(Map<String, String> q) {
        String groupName = q.get("groupName");
        String keyword = q.get("keyword");
        Double minScore = parseDoubleOrNull(q.get("minScore"));
        Double maxScore = parseDoubleOrNull(q.get("maxScore"));

        long total = candidateMapper.searchCount(keyword, groupName, null, false, null, null);

        long completed;
        {
            LambdaQueryWrapper<Candidate> w = baseWhere(keyword, groupName, null, false);
            // 一面完成：一面结束并进入二面链路的所有状态（含二面候场/进行/完成/终态录取淘汰），
            // 推进二面后不应让一面完成数回退。
            w.in(Candidate::getStatus, CandidateStatus.FIRST_COMPLETED,
                    CandidateStatus.SECOND_WAITING, CandidateStatus.SECOND_INTERVIEWING,
                    CandidateStatus.SECOND_COMPLETED, CandidateStatus.FINAL_ADMITTED,
                    CandidateStatus.FINAL_REJECTED);
            completed = candidateMapper.selectCount(w);
        }

        long scoredCount;
        {
            LambdaQueryWrapper<InterviewerScore> w = new LambdaQueryWrapper<>();
            if (groupName != null && !groupName.isBlank()) {
                w.eq(InterviewerScore::getGroupName, groupName);
            }
            w.eq(InterviewerScore::getRound, "first");
            scoredCount = scoreMapper.selectCount(w);
        }

        BigDecimal averageScore = BigDecimal.ZERO;
        {
            List<Candidate> all = candidateMapper.selectList(new LambdaQueryWrapper<>());
            List<String> ids = all.stream().map(Candidate::getId).toList();
            Map<String, List<InterviewerScore>> map = scoreMapOf(ids);
            BigDecimal sum = BigDecimal.ZERO;
            int n = 0;
            for (Candidate c : all) {
                List<InterviewerScore> sc = map.getOrDefault(c.getId(), List.of());
                int cnt = 0;
                BigDecimal s = BigDecimal.ZERO;
                for (InterviewerScore x : sc) {
                    if (x.getScore() != null && !"second".equals(x.getRound())) {
                        cnt++;
                        s = s.add(x.getScore());
                    }
                }
                if (cnt > 0) {
                    sum = sum.add(s.divide(BigDecimal.valueOf(cnt), 2, RoundingMode.HALF_UP));
                    n++;
                }
            }
            if (n > 0) {
                averageScore = sum.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("completed", completed);
        result.put("scoredCount", scoredCount);
        result.put("averageScore", averageScore);
        return result;
    }

    private LambdaQueryWrapper<Candidate> baseWhere(String keyword, String groupName, String status, boolean excludeCompleted) {
        LambdaQueryWrapper<Candidate> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(Candidate::getName, keyword)
                    .or().like(Candidate::getClassName, keyword)
                    .or().like(Candidate::getPhone, keyword));
        }
        if (groupName != null && !groupName.isBlank()) {
            w.eq(Candidate::getGroupName, groupName);
        }
        if (status != null && !status.isBlank()) {
            w.eq(Candidate::getStatus, status);
        }
        if (excludeCompleted) {
            w.ne(Candidate::getStatus, CandidateStatus.FIRST_COMPLETED);
        }
        return w;
    }

    // ==================== 管理操作（签到/分配/导入/导出） ====================

    /** 后台签到（管理员或流动人员）。 */
    @Transactional
    public Candidate checkIn(CheckInRequest dto) {
        if (dto.getCandidateId() == null || dto.getCandidateId().isBlank()) {
            throw ApiException.badRequest("缺少考生ID");
        }
        String operator = effectiveOperator(dto.getOperatorName());
        String role = roleOf(operator);
        if (role == null || role.equals("interviewer")) {
            throw ApiException.forbidden("无权限操作，仅管理员和流动人员可执行");
        }
        Candidate existing = mustFind(dto.getCandidateId());
        if (!CandidateStatus.PRE_REGISTERED.equals(existing.getStatus())) {
            throw ApiException.badRequest("该考生状态不允许签到");
        }
        int newGlobal = nextGlobalNumber();
        existing.setStatus(CandidateStatus.WAITING);
        existing.setGlobalNumber(newGlobal);
        existing.setUpdatedAt(now());
        candidateMapper.updateById(existing);
        notifyDataChanged("checkin");
        return toVO(existing, List.of());
    }

    @Transactional
    public Candidate assignGroup(AssignGroupRequest dto) {
        if (dto.getCandidateId() == null || dto.getCandidateId().isBlank()) {
            throw ApiException.badRequest("缺少考生ID");
        }
        if (dto.getGroupName() == null || dto.getGroupName().isBlank()) {
            throw ApiException.badRequest("缺少组别");
        }
        requireAdminOrFloating(effectiveOperator(dto.getOperatorName()));
        if (!FIRST_GROUPS.contains(dto.getGroupName())) {
            throw ApiException.badRequest("无效的组别");
        }
        Candidate existing = mustFind(dto.getCandidateId());
        if (!CandidateStatus.WAITING.equals(existing.getStatus())) {
            throw ApiException.badRequest("该考生当前状态无法分配组别");
        }
        if (existing.getGroupName() != null) {
            throw ApiException.badRequest("该考生已分配组别，请使用改派接口");
        }
        existing.setGroupName(dto.getGroupName());
        existing.setGroupNumber(0);
        existing.setUpdatedAt(now());
        candidateMapper.updateById(existing);
        recalcGroupNumbers(dto.getGroupName());
        clearCache();
        Candidate refreshed = mustFind(dto.getCandidateId());
        notifyDataChanged("assign-group");
        return toVO(refreshed, List.of());
    }

    @Transactional
    public Candidate reassignGroup(AssignGroupRequest dto) {
        if (dto.getCandidateId() == null || dto.getCandidateId().isBlank()) {
            throw ApiException.badRequest("缺少考生ID");
        }
        if (dto.getGroupName() == null || dto.getGroupName().isBlank()) {
            throw ApiException.badRequest("缺少组别");
        }
        requireAdminOrFloating(effectiveOperator(dto.getOperatorName()));
        if (!FIRST_GROUPS.contains(dto.getGroupName())) {
            throw ApiException.badRequest("无效的组别");
        }
        Candidate existing = mustFind(dto.getCandidateId());
        if (!CandidateStatus.WAITING.equals(existing.getStatus())) {
            throw ApiException.badRequest("该考生当前状态无法改派");
        }
        if (existing.getGroupName() == null) {
            throw ApiException.badRequest("该考生尚未分配组别，请先分配组别");
        }
        if (existing.getGroupName().equals(dto.getGroupName())) {
            throw ApiException.badRequest("考生已在该组别中");
        }
        String oldGroup = existing.getGroupName();
        existing.setGroupName(dto.getGroupName());
        existing.setGroupNumber(0);
        existing.setUpdatedAt(now());
        candidateMapper.updateById(existing);
        recalcGroupNumbers(dto.getGroupName());
        recalcGroupNumbers(oldGroup);
        clearCache();
        Candidate refreshed = mustFind(dto.getCandidateId());
        notifyDataChanged("reassign-group");
        return toVO(refreshed, List.of());
    }

    public Map<String, Object> batchImport(BatchImportRequest req) {
        String operator = effectiveOperator(req.getOperatorName());
        if (!config.getAdminName().equals(operator)) {
            throw ApiException.forbidden("无权限操作，仅管理员可执行");
        }
        List<PreRegisterRequest> items = req.getItems() == null ? List.of() : req.getItems();
        int success = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            PreRegisterRequest item = items.get(i);
            int line = i + 1;
            try {
                if (item.getName() == null || item.getName().isBlank()) {
                    throw ApiException.badRequest("姓名为必填项");
                }
                String name = item.getName().trim();
                String className = item.getClassName() == null ? "" : item.getClassName().trim();
                if (!className.isEmpty()) {
                    Long exists = candidateMapper.selectCount(
                            new LambdaQueryWrapper<Candidate>()
                                    .eq(Candidate::getName, name)
                                    .eq(Candidate::getClassName, className)
                                    .last("LIMIT 1"));
                    if (exists > 0) {
                        success++;
                        continue;
                    }
                }
                Candidate c = newCandidate(name, item);
                candidateMapper.insert(c);
                success++;
            } catch (Exception e) {
                failed++;
                errors.add("第" + line + "行：" + (e.getMessage() == null ? String.valueOf(e) : e.getMessage()));
            }
        }
        if (success > 0) {
            notifyDataChanged("import");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        result.put("total", items.size());
        result.put("errors", errors);
        return result;
    }

    /** CSV 导出（UTF-8 BOM，Excel 可直接打开）。 */
    public String exportCsv(Map<String, String> q) {
        List<Candidate> rows = candidateMapper.selectList(
                new LambdaQueryWrapper<Candidate>()
                        .apply(baseWhereSql(q))
                        .orderByAsc(Candidate::getGlobalNumber));
        Map<String, List<InterviewerScore>> scoreMap = scoreMapOf(rows.stream().map(Candidate::getId).toList());
        try {
            StringWriter sw = new StringWriter();
            CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
            printer.printRecord("全场序号", "组内序号", "姓名", "班级", "意向部门", "分配组别",
                    "面试状态", "最终总分", "面试官1姓名", "面试官1分数", "面试官1备注",
                    "面试官2姓名", "面试官2分数", "面试官2备注", "面试官3姓名", "面试官3分数", "面试官3备注");
            for (Candidate row : rows) {
                List<InterviewerScore> scores = scoreMap.getOrDefault(row.getId(), List.of());
                // 一面评分（round 非 second）用于导出平均分与三席明细；二面评分不混入一面总分。
                List<InterviewerScore> first = scores.stream()
                        .filter(s -> s.getScore() != null && !"second".equals(s.getRound()))
                        .toList();
                String avg = first.isEmpty() ? ""
                        : String.format("%.2f", first.stream().map(InterviewerScore::getScore)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .divide(BigDecimal.valueOf(first.size()), 2, RoundingMode.HALF_UP));
                String dept = List.of(row.getFirstDepartment(), row.getSecondDepartment()).stream()
                        .filter(x -> x != null && !x.isBlank()).reduce((a, b) -> a + "/" + b).orElse("");
                printer.printRecord(
                        row.getGlobalNumber() == null ? "" : row.getGlobalNumber(),
                        row.getGroupNumber() == null ? "" : row.getGroupNumber(),
                        row.getName(),
                        row.getClassName() == null ? "" : row.getClassName(),
                        dept,
                        row.getGroupName() == null ? "" : row.getGroupName(),
                        CandidateStatus.label(row.getStatus()),
                        avg,
                        seatName(first, 0), seatScore(first, 0), seatComment(first, 0),
                        seatName(first, 1), seatScore(first, 1), seatComment(first, 1),
                        seatName(first, 2), seatScore(first, 2), seatComment(first, 2));
            }
            printer.close();
            return "\uFEFF" + sw;
        } catch (Exception e) {
            throw new IllegalStateException("CSV 导出失败", e);
        }
    }

    private InterviewerScore seatAt(List<InterviewerScore> list, int index) {
        return index < list.size() ? list.get(index) : null;
    }

    private String seatName(List<InterviewerScore> list, int index) {
        InterviewerScore s = seatAt(list, index);
        return s == null ? "" : nullSafeTrim(s.getInterviewerName());
    }

    private String seatScore(List<InterviewerScore> list, int index) {
        InterviewerScore s = seatAt(list, index);
        return s == null || s.getScore() == null ? "" : s.getScore().toPlainString();
    }

    private String seatComment(List<InterviewerScore> list, int index) {
        InterviewerScore s = seatAt(list, index);
        return s == null ? "" : nullSafeTrim(s.getJudgeComment());
    }

    private String baseWhereSql(Map<String, String> q) {
        List<String> conds = new ArrayList<>();
        if (q.containsKey("groupName") && q.get("groupName") != null && !q.get("groupName").isBlank()) {
            conds.add("group_name = '" + q.get("groupName").replace("'", "''") + "'");
        }
        if (q.containsKey("keyword") && q.get("keyword") != null && !q.get("keyword").isBlank()) {
            String kw = q.get("keyword").replace("'", "''");
            conds.add("(name LIKE '%" + kw + "%' OR class_name LIKE '%" + kw + "%' OR phone LIKE '%" + kw + "%')");
        }
        if (conds.isEmpty()) {
            return "1=1";
        }
        return String.join(" AND ", conds);
    }

    // ==================== 大屏 / 统计 ====================

    public ScreenStateService.BigScreenData getBigScreen() {
        ScreenStateService.ScreenCache cache = screenState.getCache();
        if (cache != null && cache.getBigScreen() != null) {
            return cache.getBigScreen();
        }
        boolean secondMode = ScreenStateService.MODE_SECOND.equals(screenState.getDisplayMode());
        List<String> groupNames = FIRST_GROUPS;

        List<Candidate> allRows = candidateMapper.selectList(new LambdaQueryWrapper<>());

        List<ScreenStateService.GroupItem> groups = new ArrayList<>();
        int totalInterviewing = 0;
        int totalWaiting = 0;

        for (String g : groupNames) {
            List<Candidate> groupRows = allRows.stream().filter(r -> g.equals(r.getGroupName())).toList();
            List<Candidate> interviewingRows = secondMode
                    ? groupRows.stream().filter(r -> CandidateStatus.SECOND_INTERVIEWING.equals(r.getStatus())).toList()
                    : groupRows.stream()
                            .filter(r -> CandidateStatus.INTERVIEWING.equals(r.getStatus())
                                    || CandidateStatus.PENDING_APPROVAL.equals(r.getStatus()))
                            .sorted((a, b) -> {
                                int oa = CandidateStatus.PENDING_APPROVAL.equals(a.getStatus()) ? 0 : 1;
                                int ob = CandidateStatus.PENDING_APPROVAL.equals(b.getStatus()) ? 0 : 1;
                                if (oa != ob) {
                                    return oa - ob;
                                }
                                return Integer.compare(groupNumOrMax(a), groupNumOrMax(b));
                            })
                            .toList();
            List<Candidate> waitingRows = secondMode
                    ? groupRows.stream().filter(r -> CandidateStatus.SECOND_WAITING.equals(r.getStatus())).toList()
                    : groupRows.stream()
                            .filter(r -> CandidateStatus.WAITING.equals(r.getStatus()))
                            .sorted((a, b) -> Integer.compare(groupNumOrMax(a), groupNumOrMax(b)))
                            .toList();

            long completedCount = secondMode
                    ? groupRows.stream().filter(r -> CandidateStatus.SECOND_COMPLETED.equals(r.getStatus())).count()
                    : groupRows.stream().filter(r -> CandidateStatus.FIRST_COMPLETED.equals(r.getStatus())).count();

            ScreenStateService.GroupItem item = new ScreenStateService.GroupItem();
            item.setGroupName(g);
            item.setCurrent(toCurrent(interviewingRows.isEmpty() ? null : interviewingRows.get(0)));
            item.setCompletedCount((int) completedCount);
            item.setWaitingCount(waitingRows.size());
            item.setWaitingList(waitingRows.stream().map(this::toCurrent).toList());
            groups.add(item);

            totalInterviewing += interviewingRows.size();
            totalWaiting += waitingRows.size();
        }

        int totalCompleted;
        int totalSignedIn;
        if (secondMode) {
            totalCompleted = (int) allRows.stream().filter(r -> CandidateStatus.SECOND_COMPLETED.equals(r.getStatus())).count();
            totalSignedIn = (int) allRows.stream().filter(r -> List.of(
                    CandidateStatus.SECOND_WAITING, CandidateStatus.SECOND_INTERVIEWING,
                    CandidateStatus.SECOND_COMPLETED).contains(r.getStatus())).count();
        } else {
            totalCompleted = (int) allRows.stream().filter(r -> CandidateStatus.FIRST_COMPLETED.equals(r.getStatus())).count();
            totalSignedIn = (int) allRows.stream().filter(r -> !CandidateStatus.PRE_REGISTERED.equals(r.getStatus())).count();
        }

        ScreenStateService.BigScreenData data = new ScreenStateService.BigScreenData();
        data.setGroups(groups);
        data.setTotalSignedIn(totalSignedIn);
        data.setTotalInterviewing(totalInterviewing);
        data.setTotalWaiting(totalWaiting);
        data.setTotalCompleted(totalCompleted);
        data.setMode(screenState.getDisplayMode());

        ScreenStateService.ScreenCache cache2 = screenState.getCache();
        ScreenStateService.ScreenCache nc = cache2 == null ? new ScreenStateService.ScreenCache() : cache2;
        nc.setBigScreen(data);
        nc.setAllRows(allRows);
        screenState.putCache(nc);
        return data;
    }

    private int groupNumOrMax(Candidate c) {
        return c.getGroupNumber() == null ? Integer.MAX_VALUE : c.getGroupNumber();
    }

    private ScreenStateService.CurrentItem toCurrent(Candidate c) {
        if (c == null) {
            return null;
        }
        ScreenStateService.CurrentItem item = new ScreenStateService.CurrentItem();
        item.setGlobalNumber(c.getGlobalNumber() == null ? 0 : c.getGlobalNumber());
        item.setGroupNumber(c.getGroupNumber() == null ? 0 : c.getGroupNumber());
        item.setStatus(c.getStatus());
        return item;
    }

    public List<ScreenStateService.GroupCount> getGroupCounts() {
        ScreenStateService.ScreenCache cache = screenState.getCache();
        List<Candidate> allRows;
        if (cache != null && cache.getAllRows() != null) {
            allRows = cache.getAllRows();
        } else {
            allRows = candidateMapper.selectList(new LambdaQueryWrapper<>());
        }
        boolean secondMode = ScreenStateService.MODE_SECOND.equals(screenState.getDisplayMode());
        List<String> groupNames = FIRST_GROUPS;
        List<ScreenStateService.GroupCount> result = new ArrayList<>();
        for (String g : groupNames) {
            List<Candidate> groupRows = allRows.stream().filter(r -> g.equals(r.getGroupName())).toList();
            List<Candidate> waitingRows = groupRows.stream()
                    .filter(r -> (secondMode ? CandidateStatus.SECOND_WAITING : CandidateStatus.WAITING).equals(r.getStatus()))
                    .sorted((a, b) -> Integer.compare(groupNumOrMax(a), groupNumOrMax(b)))
                    .toList();
            ScreenStateService.GroupCount gc = new ScreenStateService.GroupCount();
            gc.setGroupName(g);
            gc.setTotal(groupRows.size());
            gc.setWaiting(waitingRows.size());
            gc.setInterviewing(groupRows.stream().filter(r ->
                    (secondMode ? CandidateStatus.SECOND_INTERVIEWING : CandidateStatus.INTERVIEWING).equals(r.getStatus())).count());
            gc.setPendingApproval(secondMode ? 0
                    : groupRows.stream().filter(r -> CandidateStatus.PENDING_APPROVAL.equals(r.getStatus())).count());
            gc.setCompleted(groupRows.stream().filter(r ->
                    (secondMode ? CandidateStatus.SECOND_COMPLETED : CandidateStatus.FIRST_COMPLETED).equals(r.getStatus())).count());
            gc.setAbsent(groupRows.stream().filter(r -> CandidateStatus.ABSENT.equals(r.getStatus())).count());
            if (!waitingRows.isEmpty()) {
                Candidate next = waitingRows.get(0);
                ScreenStateService.NextWaiting nw = new ScreenStateService.NextWaiting();
                nw.setId(next.getId());
                nw.setName(next.getName());
                nw.setGlobalNumber(next.getGlobalNumber());
                nw.setGroupNumber(next.getGroupNumber());
                gc.setNextWaiting(nw);
            }
            result.add(gc);
        }
        if (cache != null) {
            cache.setGroupCounts(result);
            screenState.putCache(cache);
        } else {
            ScreenStateService.ScreenCache nc = new ScreenStateService.ScreenCache();
            nc.setAllRows(allRows);
            nc.setGroupCounts(result);
            screenState.putCache(nc);
        }
        return result;
    }

    // ==================== 登录 ====================

    /** 面试官登录（管理员/流动人员/一面面试官/二面面试官，按姓名匹配）。 */
    public Map<String, Object> interviewerLogin(LoginRequest req) {
        String name = trim(req.getName());
        Map<String, Object> result = new LinkedHashMap<>();
        if (name.isEmpty()) {
            result.put("success", false);
            result.put("message", "请输入账号");
            return result;
        }
        Staff staff = findStaff(name);
        if (staff == null || !PasswordUtil.matches(req.getPassword(), staff.getPassword())) {
            result.put("success", false);
            result.put("message", "账号或密码错误");
            return result;
        }
        String role = staff.getRole();
        boolean isAdmin = "admin".equals(role);
        result.put("success", true);
        result.put("name", staff.getUsername());
        result.put("groupName", isAdmin || "floating".equals(role) ? "A" : staff.getGroupName());
        result.put("role", role);
        result.put("isAdmin", isAdmin);
        result.put("isSecondRound", false);
        result.put("token", jwtUtil.generate(staff.getUsername(), role, staff.getGroupName(), isAdmin));
        return result;
    }

    // ==================== 组队列 / 状态 / 打分 / 审批 / 删除 ====================

    public List<Candidate> getGroupQueue(String groupName, String interviewerName) {
        String effective = effectiveOperator(interviewerName);
        Staff staff = effective == null || effective.isBlank() ? null : findStaff(effective);
        if (staff != null) {
            String role = staff.getRole();
            if ("floating".equals(role)) {
                throw ApiException.forbidden("流动人员不可进入面试工作台");
            }
            if (!"admin".equals(role)) {
                requireRound(effective, ScreenStateService.MODE_FIRST);
                if (!staff.getGroupName().equals(groupName)) {
                    throw ApiException.badRequest("无权查看其他组别队列");
                }
            }
        }
        List<Candidate> rows = candidateMapper.selectList(
                new LambdaQueryWrapper<Candidate>()
                        .eq(Candidate::getGroupName, groupName)
                        .notIn(Candidate::getStatus,
                                CandidateStatus.SECOND_WAITING, CandidateStatus.SECOND_INTERVIEWING,
                                CandidateStatus.SECOND_COMPLETED,
                                CandidateStatus.FINAL_ADMITTED, CandidateStatus.FINAL_REJECTED)
                        .orderByAsc(Candidate::getGroupNumber));
        return toVOList(rows, scoreMapOf(rows.stream().map(Candidate::getId).toList()));
    }

    @Transactional
    public Candidate updateStatus(String id, UpdateStatusRequest req) {
        if (!CandidateStatus.isValid(req.getStatus())) {
            throw ApiException.badRequest("非法状态值: " + req.getStatus());
        }
        Candidate existing = mustFind(id);
        String operator = effectiveOperator(req.getInterviewerName());
        String role = roleOf(operator);
        if (role == null) {
            throw ApiException.badRequest("面试官身份无效");
        }
        if ("floating".equals(role)) {
            throw ApiException.forbidden("无权限操作，流动人员仅可查看，不可修改面试状态");
        }
        if (!"admin".equals(role)) {
            // 仅一面面试官可修改本组状态；二面面试官不通过此接口
            if (!"interviewer".equals(role)) {
                throw ApiException.badRequest("面试官身份无效");
            }
            InterviewersConfig.Member member = config.findMember(operator);
            if (member == null || !member.getGroup().equals(existing.getGroupName())) {
                throw ApiException.badRequest("只能修改本组考生状态");
            }
        }

        List<String> allowedNext = CandidateStatus.allowedNext(existing.getStatus());
        if (!allowedNext.contains(req.getStatus())) {
            throw ApiException.badRequest("不允许从 " + existing.getStatus() + " 流转到 " + req.getStatus());
        }

        // 开始面试：同组仅允许一个 interviewing / pending_approval
        if (CandidateStatus.INTERVIEWING.equals(req.getStatus()) && existing.getGroupName() != null) {
            Long active = candidateMapper.selectCount(new LambdaQueryWrapper<Candidate>()
                    .eq(Candidate::getGroupName, existing.getGroupName())
                    .in(Candidate::getStatus, CandidateStatus.INTERVIEWING, CandidateStatus.PENDING_APPROVAL));
            if (active > 0) {
                throw ApiException.badRequest("本组已有面试者正在面试或待审批，请等审批完成后再叫下一位");
            }
        }

        // 提交审批：评分人数必须达标（一面场次）
        if (CandidateStatus.PENDING_APPROVAL.equals(req.getStatus())) {
            Long cnt = scoreMapper.selectCount(new LambdaQueryWrapper<InterviewerScore>()
                    .eq(InterviewerScore::getCandidateId, id)
                    .eq(InterviewerScore::getRound, "first"));
            int minRequired = minScoreRequired(existing.getGroupName());
            if (cnt < minRequired) {
                throw ApiException.badRequest("至少需要 " + minRequired + " 位面试官评分才能提交审批（当前 " + cnt + " 人）");
            }
        }

        existing.setStatus(req.getStatus());
        existing.setUpdatedAt(now());
        candidateMapper.updateById(existing);

        if (existing.getGroupName() != null) {
            recalcGroupNumbers(existing.getGroupName());
        }
        clearCache();
        notifyDataChanged("status");
        return toVO(existing, scoreMapOf(List.of(id)).getOrDefault(id, List.of()));
    }

    @Transactional
    public Candidate submitScore(String id, ScoreRequest req) {
        String interviewerName = trim(req.getInterviewerName());
        if (interviewerName.isEmpty()) {
            throw ApiException.badRequest("缺少面试官姓名");
        }
        String role = roleOf(interviewerName);
        if ("floating".equals(role)) {
            throw ApiException.forbidden("无权限操作，流动人员不可打分");
        }
        boolean isAdmin = "admin".equals(role);
        InterviewersConfig.Member member = isAdmin ? null : config.findMember(interviewerName);
        if (!isAdmin && member == null) {
            throw ApiException.badRequest("面试官身份无效");
        }
        Candidate candidate = mustFind(id);
        if (candidate.getGroupName() == null) {
            throw ApiException.badRequest("考生尚未分配组别，无法打分");
        }
        if (!isAdmin && !candidate.getGroupName().equals(member.getGroup())) {
            throw ApiException.badRequest("只能给本组考生打分");
        }
        if (req.getScore() == null) {
            throw ApiException.badRequest("缺少分数");
        }
        BigDecimal score = BigDecimal.valueOf(req.getScore());
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(new BigDecimal("10")) > 0) {
            throw ApiException.badRequest("综合分范围 0-10，支持一位小数");
        }
        String round = activeRound(candidate);
        upsertScore(candidate, interviewerName, score, nullSafeTrim(req.getJudgeComment()), round);
        List<InterviewerScore> allScores = recomputeScore(candidate);
        autoCompleteIfFull(candidate);
        clearCache();
        notifyDataChanged("score");
        return toVO(candidate, allScores);
    }

    @Transactional
    public Candidate submitScores(String id, BatchScoreRequest req) {
        String operator = effectiveOperator(req.getOperatorName());
        String role = roleOf(operator);
        if ("floating".equals(role)) {
            throw ApiException.forbidden("无权限操作，流动人员不可打分");
        }
        boolean isAdmin = "admin".equals(role);
        InterviewersConfig.Member opMember = isAdmin ? null : config.findMember(operator);
        if (!isAdmin && opMember == null) {
            throw ApiException.badRequest("面试官身份无效");
        }
        Candidate candidate = mustFind(id);
        if (candidate.getGroupName() == null) {
            throw ApiException.badRequest("考生尚未分配组别，无法打分");
        }
        if (!isAdmin && !candidate.getGroupName().equals(opMember.getGroup())) {
            throw ApiException.badRequest("只能给本组考生打分");
        }
        List<ScoreRequest> scores = req.getScores();
        if (scores == null || scores.isEmpty()) {
            throw ApiException.badRequest("请至少提交一位面试官评分");
        }
        Set<String> submittedNames = new HashSet<>();
        String round = activeRound(candidate);
        for (ScoreRequest s : scores) {
            String name = trim(s.getInterviewerName());
            if (name.isEmpty()) {
                throw ApiException.badRequest("存在未填写面试官姓名的评分");
            }
            if (!submittedNames.add(name)) {
                throw ApiException.badRequest("同一面试官不可重复打分：" + name);
            }
            InterviewersConfig.Member seat = config.findMember(name);
            if (seat == null || !candidate.getGroupName().equals(seat.getGroup())) {
                throw ApiException.badRequest("评分人 " + name + " 不属于 " + candidate.getGroupName() + " 组");
            }
            if (s.getScore() == null) {
                throw ApiException.badRequest("缺少分数：" + name);
            }
            BigDecimal score = BigDecimal.valueOf(s.getScore());
            if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(new BigDecimal("10")) > 0) {
                throw ApiException.badRequest("综合分范围 0-10：" + name);
            }
            upsertScore(candidate, name, score, nullSafeTrim(s.getJudgeComment()), round);
        }
        List<InterviewerScore> allScores = recomputeScore(candidate);
        autoCompleteIfFull(candidate);
        clearCache();
        notifyDataChanged("score");
        return toVO(candidate, allScores);
    }

    /** 新增或更新某考生某位面试官在当前场次的评分。 */
    private void upsertScore(Candidate candidate, String interviewerName, BigDecimal score, String comment, String round) {
        InterviewerScore existingScore = scoreMapper.selectOne(
                new LambdaQueryWrapper<InterviewerScore>()
                        .eq(InterviewerScore::getCandidateId, candidate.getId())
                        .eq(InterviewerScore::getInterviewerName, interviewerName)
                        .eq(InterviewerScore::getRound, round)
                        .last("LIMIT 1"));
        if (existingScore != null) {
            existingScore.setScore(score);
            existingScore.setJudgeComment(comment);
            existingScore.setUpdatedAt(now());
            scoreMapper.updateById(existingScore);
        } else {
            InterviewerScore s = new InterviewerScore();
            s.setId(generateId());
            s.setCandidateId(candidate.getId());
            s.setInterviewerName(interviewerName);
            s.setGroupName(candidate.getGroupName());
            s.setRound(round);
            s.setScore(score);
            s.setJudgeComment(comment);
            s.setCreatedAt(now());
            s.setUpdatedAt(now());
            scoreMapper.insert(s);
        }
    }

    /** 按当前状态推断场次：二面面试中为 second，其余为 first。 */
    private String roundOf(Candidate candidate) {
        return CandidateStatus.SECOND_INTERVIEWING.equals(candidate.getStatus()) ? "second" : "first";
    }

    /**
     * 打分入口校验：仅一面面试中/待审批可打一面分，仅二面面试中可打二面分；
     * 其余状态（待面试、已完成、终态等）一律拒绝，避免把二面分误写入一面轮次污染成绩。
     */
    private String activeRound(Candidate candidate) {
        String st = candidate.getStatus();
        if (CandidateStatus.INTERVIEWING.equals(st) || CandidateStatus.PENDING_APPROVAL.equals(st)) {
            return "first";
        }
        if (CandidateStatus.SECOND_INTERVIEWING.equals(st)) {
            return "second";
        }
        throw ApiException.badRequest("考生当前为「" + CandidateStatus.label(st) + "」状态，不可打分");
    }

    /** 重算一面平均分与总分并写库，返回全部评分记录（一面+二面）。 */
    private List<InterviewerScore> recomputeScore(Candidate candidate) {
        List<InterviewerScore> allScores = scoreMapper.selectList(
                new LambdaQueryWrapper<InterviewerScore>()
                        .eq(InterviewerScore::getCandidateId, candidate.getId())
                        .orderByAsc(InterviewerScore::getCreatedAt));
        List<InterviewerScore> firstValid = allScores.stream()
                .filter(x -> x.getScore() != null && !"second".equals(x.getRound()))
                .toList();
        BigDecimal avg = firstValid.isEmpty() ? BigDecimal.ZERO
                : firstValid.stream().map(InterviewerScore::getScore).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(firstValid.size()), 2, RoundingMode.HALF_UP);
        candidate.setScoreTotal(avg.multiply(BigDecimal.TEN).setScale(0, RoundingMode.HALF_UP).intValue());
        candidate.setUpdatedAt(now());
        candidateMapper.updateById(candidate);
        return allScores;
    }

    /** 当前场次有效评分达标后自动完成：一面 → first_completed；二面 → second_completed。 */
    private void autoCompleteIfFull(Candidate candidate) {
        String round = roundOf(candidate);
        long valid = scoreMapper.selectList(
                new LambdaQueryWrapper<InterviewerScore>()
                        .eq(InterviewerScore::getCandidateId, candidate.getId())
                        .eq(InterviewerScore::getRound, round))
                .stream().filter(x -> x.getScore() != null).count();
        if (valid < minScoreRequired(candidate.getGroupName())) {
            return;
        }
        if ("second".equals(round)) {
            if (CandidateStatus.SECOND_INTERVIEWING.equals(candidate.getStatus())) {
                candidate.setStatus(CandidateStatus.SECOND_COMPLETED);
                candidate.setUpdatedAt(now());
                candidateMapper.updateById(candidate);
                if (candidate.getGroupName() != null) {
                    recalcGroupNumbers(candidate.getGroupName());
                }
            }
            return;
        }
        if (CandidateStatus.INTERVIEWING.equals(candidate.getStatus())
                || CandidateStatus.PENDING_APPROVAL.equals(candidate.getStatus())) {
            candidate.setStatus(CandidateStatus.FIRST_COMPLETED);
            candidate.setUpdatedAt(now());
            candidateMapper.updateById(candidate);
            if (candidate.getGroupName() != null) {
                recalcGroupNumbers(candidate.getGroupName());
            }
        }
    }

    @Transactional
    public Candidate approve(String id, OperatorRequest req) {
        String operator = effectiveOperator(req.getOperatorName());
        requireAdmin(operator);
        Candidate existing = mustFind(id);
        if (!CandidateStatus.PENDING_APPROVAL.equals(existing.getStatus())) {
            throw ApiException.badRequest("当前考生状态不是待审批，无法审批通过");
        }
        existing.setStatus(CandidateStatus.FIRST_COMPLETED);
        existing.setUpdatedAt(now());
        candidateMapper.updateById(existing);
        if (existing.getGroupName() != null) {
            recalcGroupNumbers(existing.getGroupName());
        }
        clearCache();
        notifyDataChanged("approve");
        return toVO(existing, scoreMapOf(List.of(id)).getOrDefault(id, List.of()));
    }

    /**
     * 录取/淘汰决策：二面完成（second_completed）后由管理员/流动人员拍板；
     * 已是终态（录取/淘汰）时允许管理员改判。录取须指定部门（部门A/B/C），淘汰清空部门。
     */
    @Transactional
    public Candidate decideAdmission(String id, AdmitDecisionRequest req) {
        String operator = effectiveOperator(req.getOperatorName());
        requireAdmin(operator);
        Candidate existing = mustFind(id);
        String current = existing.getStatus();
        boolean terminal = CandidateStatus.FINAL_ADMITTED.equals(current)
                || CandidateStatus.FINAL_REJECTED.equals(current);
        if (!CandidateStatus.SECOND_COMPLETED.equals(current) && !terminal) {
            throw ApiException.badRequest("仅二面完成的考生可做录取决策");
        }
        String decision = req.getDecision() == null ? "" : req.getDecision().trim();
        if ("admitted".equals(decision)) {
            String dept = req.getFinalDepartment() == null ? "" : req.getFinalDepartment().trim();
            if (!VALID_DEPARTMENTS.contains(dept)) {
                throw ApiException.badRequest("录取时请选择有效部门（部门A/部门B/部门C）");
            }
            existing.setStatus(CandidateStatus.FINAL_ADMITTED);
            existing.setFinalDepartment(dept);
        } else if ("rejected".equals(decision)) {
            existing.setStatus(CandidateStatus.FINAL_REJECTED);
            existing.setFinalDepartment(null);
        } else {
            throw ApiException.badRequest("录取决策仅支持 admitted / rejected");
        }
        existing.setUpdatedAt(now());
        candidateMapper.updateById(existing);
        // 淘汰时部门须落库为空：updateById 默认忽略 null 字段，这里显式清空。
        if (CandidateStatus.FINAL_REJECTED.equals(existing.getStatus())) {
            candidateMapper.update(null, new LambdaUpdateWrapper<Candidate>()
                    .eq(Candidate::getId, existing.getId())
                    .set(Candidate::getFinalDepartment, null));
        }
        clearCache();
        notifyDataChanged("admit");
        return toVO(existing, scoreMapOf(List.of(id)).getOrDefault(id, List.of()));
    }

    @Transactional
    public Map<String, Object> deleteCandidate(String id) {
        Candidate existing = mustFind(id);
        scoreMapper.delete(new LambdaQueryWrapper<InterviewerScore>().eq(InterviewerScore::getCandidateId, id));
        candidateMapper.deleteById(id);
        if (existing.getGroupName() != null) {
            recalcGroupNumbers(existing.getGroupName());
        }
        clearCache();
        notifyDataChanged("delete");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("deletedId", id);
        result.put("name", existing.getName());
        return result;
    }

    @Transactional
    public Map<String, Object> deleteAll(DeleteAllRequest req) {
        if (req.getPassword() == null || req.getPassword().isEmpty()) {
            throw ApiException.badRequest("请输入删除密码");
        }
        if (!config.getDeleteAllPassword().equals(req.getPassword())) {
            throw ApiException.forbidden("密码错误，无法全部删除");
        }
        Long count = candidateMapper.selectCount(new LambdaQueryWrapper<>());
        scoreMapper.delete(new LambdaQueryWrapper<>());
        candidateMapper.delete(new LambdaQueryWrapper<>());
        clearCache();
        notifyDataChanged("delete-all");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("deletedCount", count);
        return result;
    }

    // ==================== 内部工具 ====================

    protected Candidate mustFind(String id) {
        Candidate c = candidateMapper.selectById(id);
        if (c == null) {
            throw ApiException.notFound("考生不存在");
        }
        return c;
    }

    protected void requireAdminOrFloating(String name) {
        String role = roleOf(name);
        if (role == null || "interviewer".equals(role)) {
            throw ApiException.forbidden("无权限操作，仅管理员和流动人员可执行");
        }
    }

    /** 仅管理员可执行（二面推进/分组、审批、录取决策等）；流动人员与面试官均被拒绝。 */
    protected void requireAdmin(String name) {
        String role = roleOf(name);
        if (role == null || !"admin".equals(role)) {
            throw ApiException.forbidden("无权限操作，仅管理员可执行");
        }
    }

    protected int nextGlobalNumber() {
        Candidate max = candidateMapper.selectOne(
                new LambdaQueryWrapper<Candidate>()
                        .orderByDesc(Candidate::getGlobalNumber)
                        .last("LIMIT 1"));
        return (max == null || max.getGlobalNumber() == null) ? 1 : max.getGlobalNumber() + 1;
    }

    protected int minScoreRequired(String groupName) {
        return 3;
    }

    /** 一面官分组名单：{A:[...], B:[...], C:[...], D:[...]}，供前端三席位绑定。 */
    public Map<String, List<String>> firstRoundInterviewerGroups() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String g : FIRST_GROUPS) {
            List<String> names = config.getMembers().stream()
                    .filter(m -> g.equals(m.getGroup()))
                    .map(InterviewersConfig.Member::getName)
                    .toList();
            out.put(g, names);
        }
        return out;
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private String nullSafeTrim(String s) {
        return s == null ? null : s.trim();
    }

    private int parseInt(String s, int def) {
        try {
            return s == null ? def : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private Double parseDoubleOrNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
