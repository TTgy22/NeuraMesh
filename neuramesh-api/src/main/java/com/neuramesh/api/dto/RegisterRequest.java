package com.neuramesh.api.dto;

/**
 * 注册请求。
 *
 * @param username 用户名（唯一）
 * @param password 密码（明文，BCrypt 哈希后存储）
 * @param role     角色（VENDOR / NODE_OPERATOR / ADMIN，留空默认 VENDOR）
 */
public record RegisterRequest(String username, String password, String role) {
}
