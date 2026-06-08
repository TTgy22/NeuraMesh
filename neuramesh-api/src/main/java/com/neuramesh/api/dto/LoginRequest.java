package com.neuramesh.api.dto;

/**
 * 登录请求。
 *
 * @param username 用户名
 * @param password 密码（明文，仅传输用，BCrypt 校验）
 */
public record LoginRequest(String username, String password) {
}
