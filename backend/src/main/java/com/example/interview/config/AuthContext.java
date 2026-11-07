package com.example.interview.config;

/**
 * 线程级身份上下文：由 AuthInterceptor 在解析到 Bearer token 时填充。
 */
public final class AuthContext {

    private static final ThreadLocal<AuthUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthUser user) {
        HOLDER.set(user);
    }

    public static AuthUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
