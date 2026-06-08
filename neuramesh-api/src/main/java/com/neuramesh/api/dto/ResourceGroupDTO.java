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
 * @param pricePerHour   每小时价格（NMT 最小单位）
 * @param groupPublicKey 安全组公钥（hex，公开）
 * @param category       规格族（通用型/计算型/高可靠型/存储型/网络增强型）
 * @param reliabilityPct 可靠性硬件占比（0-100）
 * @param multiNodePct   多节点冗余占比（0-100）
 * @param tags           特性标签（如 GPU、低延迟、SLA99.9）
 */
public record ResourceGroupDTO(String groupId, String region, double minBenchmarkScore,
                               boolean requiredHttp2, int nodeCount, double totalWeight,
                               double averageLatency, double onlineRate, long pricePerHour,
                               String groupPublicKey, String category, int reliabilityPct,
                               int multiNodePct, java.util.List<String> tags) {
}
