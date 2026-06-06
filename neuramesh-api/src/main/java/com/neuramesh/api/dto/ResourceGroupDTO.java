package com.neuramesh.api.dto;

/**
 * 资源组视图（用于厂商选购与控制台展示）。
 *
 * @param groupId        资源组 id（如 north-china-qingdao）
 * @param region         地区名（如 华北-青岛）
 * @param minBenchmarkScore 软性加入分数门槛
 * @param requiredHttp2  是否要求 HTTP/2.0
 * @param nodeCount      组内节点数
 * @param totalWeight    组内节点总权重
 * @param averageLatency 平均延迟（毫秒，演示派生值；真实测量 TODO P6）
 * @param onlineRate     组内在线率（0..1）
 */
public record ResourceGroupDTO(String groupId, String region, double minBenchmarkScore,
                               boolean requiredHttp2, int nodeCount, double totalWeight,
                               double averageLatency, double onlineRate) {
}
