package com.example.interview.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * 口令哈希工具：SHA-256 + 随机盐（JDK 自带，不引入额外依赖）。
 * 存储格式：{@code saltHex:hashHex}，盐与摘要均为十六进制。
 * 适配校园招新场景；如对公网安全有更高要求，可替换为 BCrypt。
 */
public final class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_BYTES = 16;

    private PasswordUtil() {
    }

    /** 生成口令哈希（含随机盐）。 */
    public static String hash(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] digest = digest(salt, rawPassword);
        return toHex(salt) + ":" + toHex(digest);
    }

    /** 校验明文口令是否与已存储哈希匹配。 */
    public static boolean matches(String rawPassword, String stored) {
        if (rawPassword == null || stored == null) {
            return false;
        }
        String[] parts = stored.split(":");
        if (parts.length != 2) {
            return false;
        }
        byte[] salt = fromHex(parts[0]);
        byte[] expected = fromHex(parts[1]);
        byte[] actual = digest(salt, rawPassword);
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] digest(byte[] salt, String rawPassword) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            return md.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] fromHex(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }
}
