package com.neuramesh.benchmark;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 设备标准化算力测试。
 *
 * <p>测试环境无真实 GPU/NPU，使用 CPU 密集型计算（迭代 SHA-256）模拟标准推理负载：
 * 连续做 {@code iterations} 次哈希链运算并测时，分数 = {@code iterations / 耗时秒} 的标准化值
 * （吞吐越高分越高）。同型号设备多次测试分数偏差应较小。
 */
public final class DeviceBenchmark {

    private static final Logger LOG = LoggerFactory.getLogger(DeviceBenchmark.class);

    /** 默认迭代次数。 */
    public static final int DEFAULT_ITERATIONS = 1000;

    private final int iterations;

    public DeviceBenchmark() {
        this(DEFAULT_ITERATIONS);
    }

    public DeviceBenchmark(int iterations) {
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations 必须为正");
        }
        this.iterations = iterations;
    }

    /**
     * 运行 Benchmark。
     *
     * @param deviceModel 设备型号（用于结果记录与指纹）
     * @return Benchmark 结果
     */
    public BenchmarkResult run(String deviceModel) {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        long memoryMB = Runtime.getRuntime().maxMemory() / (1024 * 1024);

        long startNs = System.nanoTime();
        byte[] acc = new byte[32];
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < iterations; i++) {
                digest.update(acc);
                digest.update((byte) i);
                acc = digest.digest();
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
        long durationNs = System.nanoTime() - startNs;
        // 防止 JIT 把循环优化掉：消费 acc
        long consumed = 0;
        for (byte b : acc) {
            consumed += b;
        }
        double durationMs = durationNs / 1_000_000.0;
        // 分数：吞吐量（每毫秒迭代数）放大为可读数值；与耗时成反比
        double throughput = iterations / Math.max(durationMs, 0.0001);
        double score = throughput * 1000.0 + (consumed & 0x0);

        LOG.info("Benchmark {} 完成: score={}, durationMs={}, cores={}, memMB={}",
                deviceModel, String.format("%.1f", score), String.format("%.2f", durationMs),
                cpuCores, memoryMB);
        return new BenchmarkResult(score, (long) durationMs, deviceModel, cpuCores, memoryMB,
                System.currentTimeMillis());
    }
}
