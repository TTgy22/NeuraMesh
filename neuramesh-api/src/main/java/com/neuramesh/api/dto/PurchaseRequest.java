package com.neuramesh.api.dto;

/**
 * 资源组购买请求。
 *
 * @param hours 购买时长（小时，&gt; 0）
 */
public record PurchaseRequest(int hours) {
}
