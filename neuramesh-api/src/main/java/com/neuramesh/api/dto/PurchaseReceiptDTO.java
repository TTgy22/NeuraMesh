package com.neuramesh.api.dto;

/**
 * 购买凭证。
 *
 * @param groupId           资源组 id
 * @param region            地区
 * @param hours             购买时长（小时）
 * @param totalCost         总费用（NMT 最小单位）
 * @param expiresAt         到期时间戳（毫秒）
 * @param settleTxId        扣款交易哈希 hex
 * @param groupPrivateKey   安全组私钥（明文交付，赛事简化；TODO P6 加密交付）
 * @param remainingBalance  扣款后余额
 */
public record PurchaseReceiptDTO(String groupId, String region, int hours, long totalCost,
                                 long expiresAt, String settleTxId, String groupPrivateKey,
                                 long remainingBalance) {
}
