package com.neuramesh.api.dto;

import java.util.List;

/**
 * 任务状态与结果。
 *
 * @param taskId      任务 ID
 * @param taskType    任务类型
 * @param status      状态（PENDING / SETTLED / FAILED）
 * @param budget      预算
 * @param settleTxId  结算交易哈希 hex
 * @param assignedNodes 参与分配的节点地址列表
 * @param resultUri   结果下载地址（占位）
 */
public record TaskStatusDTO(String taskId, String taskType, String status, long budget,
                            String settleTxId, List<String> assignedNodes, String resultUri) {
}
