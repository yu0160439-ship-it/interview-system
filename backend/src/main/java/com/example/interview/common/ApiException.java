package com.example.interview.common;

/**
 * 业务异常：携带 HTTP 状态码与面向用户的错误消息。
 * 全局异常处理器会将其转换为 { message, statusCode, error:true } 响应（与原版前端契约一致）。
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException badRequest(String msg) {
        return new ApiException(400, msg);
    }

    public static ApiException notFound(String msg) {
        return new ApiException(404, msg);
    }

    public static ApiException forbidden(String msg) {
        return new ApiException(403, msg);
    }

    public static ApiException conflict(String msg) {
        return new ApiException(409, msg);
    }

    public int getStatus() {
        return status;
    }
}
