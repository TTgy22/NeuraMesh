package com.neuramesh.api.dto;

/**
 * 登录/刷新返回的令牌与用户信息。
 *
 * @param accessToken  访问令牌（15 分钟）
 * @param refreshToken 刷新令牌（7 天）
 * @param userId       用户 id
 * @param username     用户名
 * @param role         角色
 * @param address      链上地址 hex（0x 前缀）
 */
public record TokenResponse(String accessToken, String refreshToken, String userId,
                            String username, String role, String address) {
}
