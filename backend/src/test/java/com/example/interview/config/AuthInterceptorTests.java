package com.example.interview.config;

import com.example.interview.common.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthInterceptorTests {

    private JwtUtil jwt;
    private AuthInterceptor interceptor;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret", "test-secret-for-auth-interceptor-0123456789");
        ReflectionTestUtils.setField(jwt, "expireHours", 24);
        interceptor = new AuthInterceptor(jwt);
        mvc = MockMvcBuilders.standaloneSetup(new IdentityController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(interceptor)
                .build();
    }

    @AfterEach
    void clearContext() {
        AuthContext.clear();
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/health",
            "POST, /api/candidates/pre-register/public",
            "POST, /api/candidates/public/search-by-name",
            "POST, /api/candidates/public/check-in",
            "POST, /api/candidates/public/update-departments",
            "GET, /api/candidates/public/detail/candidate-id",
            "POST, /api/candidates/second/check-in",
            "POST, /api/candidates/interviewer-login",
            "POST, /api/candidates/second/login",
            "GET, /api/candidates/display/big-screen",
            "GET, /api/candidates/group-counts",
            "GET, /api/candidates/display/stream",
            "GET, /api/candidates/display/mode"
    })
    void publicRoutesRemainAccessibleWithExpiredBrowserSession(String method, String path) throws Exception {
        ReflectionTestUtils.setField(jwt, "expireHours", -1);
        String expired = jwt.generate("admin", "admin", null, true);
        mvc.perform(MockMvcRequestBuilders.request(method, URI.create(path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("anonymous"));
        assertNull(AuthContext.get());
    }

    @ParameterizedTest
    @CsvSource({
            "DELETE, /api/candidates/candidate-id",
            "POST, /api/candidates/delete-all",
            "POST, /api/candidates/pre-register",
            "POST, /api/candidates/second/move-to",
            "POST, /api/candidates/display/toggle-mode",
            "GET, /api/candidates/export",
            "GET, /api/candidates",
            "GET, /api/candidates/group/A",
            "GET, /api/candidates/second/queue",
            "GET, /api/candidates/public/unknown",
            "DELETE, /api/candidates/public/detail/candidate-id",
            "GET, /api/candidates/public/detail/candidate-id/extra",
            "POST, /api/candidates/display/mode"
    })
    void staffRoutesAndUnlistedMethodsRequireAuthentication(String method, String path) throws Exception {
        mvc.perform(MockMvcRequestBuilders.request(method, URI.create(path)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value(401));
        assertNull(AuthContext.get());
    }

    @Test
    void invalidAndExpiredTokensCannotUseStaffRoutes() throws Exception {
        mvc.perform(MockMvcRequestBuilders.delete("/api/candidates/id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        ReflectionTestUtils.setField(jwt, "expireHours", -1);
        mvc.perform(MockMvcRequestBuilders.delete("/api/candidates/id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generate("admin", "admin", null, true)))
                .andExpect(status().isUnauthorized());
        assertNull(AuthContext.get());
    }

    @Test
    void validTokenProvidesIdentityOnlyForCurrentRequest() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/candidates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generate("staff", "interviewer", "A", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("staff"));
        assertNull(AuthContext.get());
        mvc.perform(MockMvcRequestBuilders.get("/api/candidates"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void leftoverThreadIdentityDoesNotAuthorizeAnonymousRequest() throws Exception {
        AuthContext.set(AuthUser.of("admin", "admin", null, true));
        mvc.perform(MockMvcRequestBuilders.delete("/api/candidates/id"))
                .andExpect(status().isUnauthorized());
        assertNull(AuthContext.get());
    }

    @Test
    void optionsAndAsyncHandoffClearThreadIdentity() {
        AuthContext.set(AuthUser.of("admin", "admin", null, true));
        interceptor.preHandle(new MockHttpServletRequest("OPTIONS", "/api/candidates"),
                new MockHttpServletResponse(), new Object());
        assertNull(AuthContext.get());
        AuthContext.set(AuthUser.of("admin", "admin", null, true));
        interceptor.afterConcurrentHandlingStarted(new MockHttpServletRequest(),
                new MockHttpServletResponse(), new Object());
        assertNull(AuthContext.get());
    }

    @RestController
    static class IdentityController {
        @RequestMapping("/api/**")
        Map<String, String> identity() {
            AuthUser user = AuthContext.get();
            return Map.of("name", user == null ? "anonymous" : user.getName());
        }
    }
}
