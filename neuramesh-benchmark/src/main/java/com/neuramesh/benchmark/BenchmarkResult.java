package com.neuramesh.benchmark;

/**
 * 设备 Benchmark 结果。
 *
 * @param score       标准化分数（越高越好）
 * @param durationMs  测试耗时（毫秒）
 * @param deviceModel 设备型号
 * @param cpuCores    CPU 核数
 * @param memoryMB    内存（MB）
 * @param timestamp   生成时间戳
 */
public record BenchmarkResult(double score, long durationMs, String deviceModel,
                              int cpuCores, long memoryMB, long timestamp) {

    public BenchmarkResult {
        if (deviceModel == null) {
            deviceModel = "unknown";
        }
    }
}
