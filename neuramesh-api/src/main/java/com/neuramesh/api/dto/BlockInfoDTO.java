package com.neuramesh.api.dto;

/**
 * 区块摘要。
 *
 * @param height    高度
 * @param hash      区块哈希 hex
 * @param prevHash  前驱哈希 hex
 * @param timestamp 时间戳（毫秒）
 * @param txCount   交易数
 */
public record BlockInfoDTO(long height, String hash, String prevHash, long timestamp, int txCount) {
}
