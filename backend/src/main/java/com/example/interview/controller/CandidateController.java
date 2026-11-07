package com.example.interview.controller;

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
import com.example.interview.dto.SecondStartRequest;
import com.example.interview.dto.ToggleModeRequest;
import com.example.interview.dto.UpdateDepartmentsRequest;
import com.example.interview.dto.UpdateStatusRequest;
import com.example.interview.entity.Candidate;
import com.example.interview.service.CandidateService;
import com.example.interview.service.SecondRoundService;
import com.example.interview.service.support.SseHub;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 全部 /api/candidates 路由（与原 Cloudflare Functions [[path]].js 路由表一一对应）。
 */
@RestController
@RequestMapping("/api/candidates")
public class CandidateController {

    private final CandidateService candidateService;
    private final SecondRoundService secondRoundService;
    private final SseHub sseHub;
    private final ObjectMapper objectMapper;

    public CandidateController(CandidateService candidateService,
                               SecondRoundService secondRoundService,
                               SseHub sseHub,
                               ObjectMapper objectMapper) {
        this.candidateService = candidateService;
        this.secondRoundService = secondRoundService;
        this.sseHub = sseHub;
        this.objectMapper = objectMapper;
    }

    // ===== 管理 / 报名 =====

    @PostMapping("/pre-register")
    public Candidate preRegister(@RequestBody PreRegisterRequest body) {
        return candidateService.preRegister(body);
    }

    @PostMapping("/pre-register/public")
    public Candidate preRegisterPublic(@RequestBody PreRegisterRequest body) {
        return candidateService.preRegisterPublic(body);
    }

    @PostMapping("/public/search-by-name")
    public List<Candidate> searchByNamePublic(@RequestBody Map<String, String> body) {
        return candidateService.searchByNamePublic(body.get("name"));
    }

    @PostMapping("/public/check-in")
    public Candidate checkInPublic(@RequestBody Map<String, String> body) {
        return candidateService.checkInPublic(body.get("candidateId"));
    }

    @GetMapping("/public/detail/{id}")
    public Candidate getDetailPublic(@PathVariable String id) {
        return candidateService.getDetailPublic(id);
    }

    @PostMapping("/public/update-departments")
    public Candidate updateDepartmentsPublic(@RequestBody UpdateDepartmentsRequest body) {
        return candidateService.updateDepartmentsPublic(body);
    }

    // ===== 查询 / 统计 / 导出 =====

    @GetMapping
    public Map<String, Object> search(@RequestParam Map<String, String> query) {
        return candidateService.search(query);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam Map<String, String> query) {
        return candidateService.stats(query);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam Map<String, String> query) {
        String csv = candidateService.exportCsv(query);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=candidates.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }

    // ===== 签到 / 分配 / 导入 =====

    @PostMapping("/check-in")
    public Candidate checkIn(@RequestBody CheckInRequest body) {
        return candidateService.checkIn(body);
    }

    @PostMapping("/assign-group")
    public Candidate assignGroup(@RequestBody AssignGroupRequest body) {
        return candidateService.assignGroup(body);
    }

    @PostMapping("/reassign-group")
    public Candidate reassignGroup(@RequestBody AssignGroupRequest body) {
        return candidateService.reassignGroup(body);
    }

    @PostMapping("/batch-import")
    public Map<String, Object> batchImport(@RequestBody BatchImportRequest body) {
        return candidateService.batchImport(body);
    }

    @GetMapping("/group/{groupName}")
    public List<Candidate> getGroupQueue(@PathVariable String groupName,
                                         @RequestParam(required = false) String interviewerName) {
        return candidateService.getGroupQueue(groupName, interviewerName);
    }

    // ===== 登录 =====

    @PostMapping("/interviewer-login")
    public Map<String, Object> interviewerLogin(@RequestBody LoginRequest body) {
        return candidateService.interviewerLogin(body);
    }

    // ===== 状态 / 打分 / 审批 / 删除 =====

    @PatchMapping("/{id}/status")
    public Candidate updateStatus(@PathVariable String id, @RequestBody UpdateStatusRequest body) {
        return candidateService.updateStatus(id, body);
    }

    @PostMapping("/{id}/score")
    public Candidate submitScore(@PathVariable String id, @RequestBody ScoreRequest body) {
        return candidateService.submitScore(id, body);
    }

    @PostMapping("/{id}/scores")
    public Candidate submitScores(@PathVariable String id, @RequestBody BatchScoreRequest body) {
        return candidateService.submitScores(id, body);
    }

    @GetMapping("/interviewers/first-round")
    public Map<String, List<String>> firstRoundInterviewers() {
        return candidateService.firstRoundInterviewerGroups();
    }

    @PostMapping("/{id}/approve")
    public Candidate approve(@PathVariable String id, @RequestBody OperatorRequest body) {
        return candidateService.approve(id, body);
    }

    @PostMapping("/{id}/admit")
    public Candidate admit(@PathVariable String id, @RequestBody AdmitDecisionRequest body) {
        return candidateService.decideAdmission(id, body);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteCandidate(@PathVariable String id) {
        return candidateService.deleteCandidate(id);
    }

    @PostMapping("/delete-all")
    public Map<String, Object> deleteAll(@RequestBody DeleteAllRequest body) {
        return candidateService.deleteAll(body);
    }

    // ===== 大屏 =====

    @GetMapping("/display/big-screen")
    public Object bigScreen() {
        return candidateService.getBigScreen();
    }

    @GetMapping("/group-counts")
    public List<Object> groupCounts() {
        return candidateService.getGroupCounts().stream().map(x -> (Object) x).toList();
    }

    // ===== 二面 =====

    @PostMapping("/second/login")
    public Map<String, Object> secondLogin(@RequestBody LoginRequest body) {
        return secondRoundService.secondLogin(body.getName(), body.getPassword());
    }

    @GetMapping("/second/queue")
    public Map<String, List<Candidate>> secondQueue() {
        return secondRoundService.getSecondRoundQueue();
    }

    @PostMapping("/second/move-to")
    public Candidate moveToSecondRound(@RequestBody Map<String, String> body) {
        OperatorRequest req = new OperatorRequest();
        req.setOperatorName(body.get("operatorName"));
        return secondRoundService.moveToSecondRound(body.get("candidateId"), req);
    }

    @PostMapping("/second/assign-group")
    public Candidate assignSecondGroup(@RequestBody AssignGroupRequest body) {
        return secondRoundService.assignSecondGroup(body);
    }

    @PostMapping("/second/check-in")
    public Candidate secondCheckInPublic(@RequestBody Map<String, String> body) {
        return secondRoundService.secondCheckInPublic(body.get("candidateId"));
    }

    @PostMapping("/second/start")
    public Candidate secondStart(@RequestBody SecondStartRequest body) {
        return secondRoundService.secondRoundStart(body);
    }

    @GetMapping("/second/stats")
    public Map<String, Object> secondStats() {
        return secondRoundService.secondRoundStats();
    }

    // ===== 大屏显示模式 =====

    @GetMapping("/display/mode")
    public Map<String, Object> displayMode(@RequestParam(required = false) String operatorName) {
        OperatorRequest req = new OperatorRequest();
        req.setOperatorName(operatorName);
        return secondRoundService.getDisplayMode(req);
    }

    @PostMapping("/display/toggle-mode")
    public Map<String, Object> toggleDisplayMode(@RequestBody ToggleModeRequest body) {
        return secondRoundService.toggleDisplayMode(body);
    }

    /**
     * 大屏模式实时推送（SSE）。大屏页面建立长连接，模式变更时服务端主动广播；
     * 连接建立即推送一次当前模式。公开页面，不强制鉴权。
     */
    @GetMapping("/display/stream")
    public SseEmitter displayStream() {
        SseEmitter emitter = sseHub.register();
        try {
            String json = objectMapper.writeValueAsString(
                    Map.of("mode", secondRoundService.currentDisplayMode()));
            emitter.send(SseEmitter.event().name("mode").data(json));
        } catch (IOException ignored) {
            // 连接已断开，emitter 会在清理回调中移除
        }
        return emitter;
    }
}
