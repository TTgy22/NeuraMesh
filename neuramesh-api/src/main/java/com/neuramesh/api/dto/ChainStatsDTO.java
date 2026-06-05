package com.neuramesh.api.dto;

/**
 * 网络聚合统计（用于控制台总览图表）。
 *
 * @param blockHeight  区块高度（区块数）
 * @param txCount      交易总数
 * @param nodeCount    节点总数
 * @param accountCount 账户总数
 * @param totalWeight  全网总权重
 * @param totalEarned  全网累计收益
 * @param totalBalance 全网账户余额总和
 */
public record ChainStatsDTO(int blockHeight, int txCount, int nodeCount, int accountCount,
                            double totalWeight, long totalEarned, long totalBalance) {
}
