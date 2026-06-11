package com.neuramesh.api.dto;

/**
 * 节点注册请求。
 *
 * @param deviceModel     设备型号（用于 Benchmark 与指纹）
 * @param resourceGroupId 目标资源组 id（null/空 → 链上兜底默认组 general-purpose）
 */
public record NodeRegisterRequest(String deviceModel, String resourceGroupId) {
}
