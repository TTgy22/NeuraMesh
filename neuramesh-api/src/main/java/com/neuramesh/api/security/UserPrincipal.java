package com.neuramesh.api.security;

/**
 * 认证主体：从 JWT 解析得到，注入 Spring Security 上下文。
 *
 * @param userId   用户 id
 * @param username 用户名
 * @param role     角色（VENDOR / NODE_OPERATOR / ADMIN）
 * @param address  链上地址 hex（用于余额/扣款）
 */
public record UserPrincipal(String userId, String username, String role, String address) {
}
