package com.example.interview.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 大屏 SSE 连接注册表。
 * 大屏页面建立一条长连接，模式（一面/二面）变更时由服务端主动广播，实现实时跟随。
 * 连接超时 / 异常 / 关闭后自动移除；客户端断开重连由 EventSource 负责。
 */
@Component
public class SseHub {

    /** 单条连接空闲超时：30 分钟，超时后由客户端 EventSource 自动重连 */
    private static final long EMITTER_TIMEOUT_MS = 30L * 60 * 1000;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    public SseHub(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 注册一条新的大屏连接，并返回 emitter 供控制器挂到响应上。 */
    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.add(emitter);

        Runnable cleanup = () -> emitters.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(err -> cleanup.run());
        return emitter;
    }

    /** 向所有在线大屏广播当前显示模式。 */
    public void broadcastMode(String mode) {
        Map<String, String> payload = Map.of("mode", mode == null ? ScreenStateService.MODE_FIRST : mode);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("mode").data(toJson(payload)));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }

    /**
     * 广播业务数据变更（签到/分组/状态/打分/录取/删除等）。
     * 各在线页面收到 {@code data} 事件后静默刷新当前列表；type 仅作区分与日志，不做精细过滤。
     */
    public void broadcastData(String type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type == null ? "change" : type);
        payload.put("at", System.currentTimeMillis());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("data").data(toJson(payload)));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            return "{\"mode\":\"" + ScreenStateService.MODE_FIRST + "\"}";
        }
    }
}
