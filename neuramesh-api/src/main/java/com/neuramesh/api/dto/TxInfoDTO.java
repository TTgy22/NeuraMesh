package com.neuramesh.api.dto;

/**
 * 交易详情。
 *
 * @param txId      交易哈希 hex
 * @param type      交易类型
 * @param from      发送方地址 hex
 * @param to        接收方地址 hex
 * @param nonce     nonce
 * @param timestamp 时间戳
 */
public record TxInfoDTO(String txId, String type, String from, String to, long nonce, long timestamp) {
}
