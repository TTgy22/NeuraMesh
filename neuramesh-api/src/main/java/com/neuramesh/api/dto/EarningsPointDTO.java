package com.neuramesh.api.dto;

/**
 * 收益曲线数据点。
 *
 * @param day      日期标签（如 "06-05"）
 * @param earnings 当日收益
 */
public record EarningsPointDTO(String day, long earnings) {
}
