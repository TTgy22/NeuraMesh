package com.neuramesh.api.dto;

/**
 * 节点状态视图。
 *
 * @param nodeId        节点地址 hex（0x 前缀）
 * @param online        是否在线运行
 * @param deviceModel   设备型号
 * @param hardwareScore 硬件分
 * @param qualityScore  质量分
 * @param uptimeScore   在线分
 * @param bandwidthScore 带宽分
 * @param totalWeight   总权重
 * @param totalEarned   累计收益（NMT 最小单位）
 * @param level         节点等级（青铜/白银/黄金/铂金/钻石）
 * @param fingerprint   链上设备指纹 hex（终身绑定，注册后不可变）
 */
public record NodeStatusDTO(String nodeId, boolean online, String deviceModel,
                            double hardwareScore, double qualityScore, double uptimeScore,
                            double bandwidthScore, double totalWeight, long totalEarned,
                            String level, String fingerprint) {
}
