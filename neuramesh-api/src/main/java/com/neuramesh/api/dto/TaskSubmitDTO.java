package com.neuramesh.api.dto;

/**
 * 厂商任务提交。
 *
 * @param vendorId 厂商地址 hex
 * @param taskType 任务类型（image-classification / ocr / defect-detection）
 * @param budget   预算（NMT 最小单位）
 */
public record TaskSubmitDTO(String vendorId, String taskType, long budget) {
}
