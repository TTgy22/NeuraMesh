package com.neuramesh.api.dto;

/**
 * 节点注册请求。
 *
 * @param deviceModel 设备型号（用于 Benchmark 与指纹）
 */
public record NodeRegisterRequest(String deviceModel) {
}
