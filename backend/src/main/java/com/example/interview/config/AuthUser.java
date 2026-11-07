package com.example.interview.config;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前请求身份（登录用户）。
 */
@Data
@AllArgsConstructor
public class AuthUser {
    private String name;
    private String role;
    private String groupName;
    private boolean admin;

    public static AuthUser of(String name, String role, String groupName, boolean admin) {
        return new AuthUser(name, role, groupName, admin);
    }
}
