package com.neuramesh.api.common;

/**
 * 统一 API 返回包装：{@code {code, data, message}}。code=0 表示成功。
 *
 * @param <T> 数据类型
 */
public record ApiResponse<T>(int code, T data, String message) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, data, "ok");
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, null, message);
    }
}
