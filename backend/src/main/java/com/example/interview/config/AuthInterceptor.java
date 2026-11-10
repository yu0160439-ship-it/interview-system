package com.example.interview.config;

import com.example.interview.common.ApiException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.util.Set;

/**
 * 工作人员接口必须使用有效 Bearer token；公开入口按方法和路径单独放行。
 */
@Component
public class AuthInterceptor implements AsyncHandlerInterceptor {

    private static final Set<String> PUBLIC_ROUTES = Set.of(
            "GET /api/health",
            "POST /api/candidates/pre-register/public",
            "POST /api/candidates/public/search-by-name",
            "POST /api/candidates/public/check-in",
            "POST /api/candidates/public/update-departments",
            "POST /api/candidates/second/check-in",
            "POST /api/candidates/interviewer-login",
            "POST /api/candidates/second/login",
            "GET /api/candidates/display/big-screen",
            "GET /api/candidates/group-counts",
            "GET /api/candidates/display/stream",
            "GET /api/candidates/display/mode"
    );

    private static final String PUBLIC_DETAIL_PREFIX = "/api/candidates/public/detail/";

    private final JwtUtil jwtUtil;

    public AuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 拒绝请求或异步处理均可能不触发普通完成回调，进入请求时先移除旧身份。
        AuthContext.clear();
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        boolean publicRequest = isPublicRequest(request);
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            Claims claims = jwtUtil.parse(header.substring(7));
            if (claims != null) {
                try {
                    String name = claims.getSubject();
                    if (name != null && !name.isBlank()) {
                        AuthContext.set(AuthUser.of(
                                name,
                                claims.get("role", String.class),
                                claims.get("groupName", String.class),
                                Boolean.TRUE.equals(claims.get("isAdmin", Boolean.class))
                        ));
                    }
                } catch (RuntimeException ignored) {
                    // 签名有效但身份字段类型无效时，按无效令牌处理。
                    AuthContext.clear();
                }
            }
        }
        if (!publicRequest && AuthContext.get() == null) {
            throw ApiException.unauthorized("登录已失效，请重新登录");
        }
        return true;
    }

    private boolean isPublicRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (PUBLIC_ROUTES.contains(request.getMethod() + " " + path)) {
            return true;
        }
        if (!"GET".equals(request.getMethod()) || !path.startsWith(PUBLIC_DETAIL_PREFIX)) {
            return false;
        }
        String candidateId = path.substring(PUBLIC_DETAIL_PREFIX.length());
        return !candidateId.isBlank() && candidateId.indexOf('/') < 0;
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response,
                                              Object handler) {
        AuthContext.clear();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }
}
