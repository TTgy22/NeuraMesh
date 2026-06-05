package com.neuramesh.benchmark;

import com.neuramesh.core.ByteUtils;
import com.neuramesh.core.CryptoUtils;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 设备指纹。
 *
 * <p>{@code hash = SHA-256(deviceModel + cpuCores + memoryMB + benchmarkScore + randomSalt)}。
 * 绑定设备硬件画像与一次 Benchmark 结果；{@link #verify(BenchmarkResult)} 校验指纹未被篡改
 * （用相同输入与盐重算比对）。
 *
 * <p>债务：当前无 TEE/证书链绑定，无法完全防止虚拟机刷分（P4/P5 引入）。
 */
public final class Fingerprint {

    private final byte[] hash;
    private final byte[] salt;

    private Fingerprint(byte[] hash, byte[] salt) {
        this.hash = hash.clone();
        this.salt = salt.clone();
    }

    /**
     * 由 Benchmark 结果与随机盐生成指纹。
     *
     * @param result Benchmark 结果
     * @param salt   随机盐（调用方保存以便校验）
     * @return 指纹
     */
    public static Fingerprint generate(BenchmarkResult result, byte[] salt) {
        if (result == null || salt == null) {
            throw new IllegalArgumentException("result/salt 不可为 null");
        }
        byte[] hash = compute(result, salt);
        return new Fingerprint(hash, salt);
    }

    private static byte[] compute(BenchmarkResult result, byte[] salt) {
        byte[] model = result.deviceModel().getBytes(StandardCharsets.UTF_8);
        return CryptoUtils.sha256(ByteUtils.concat(
                model,
                ByteUtils.intToBytes(result.cpuCores()),
                ByteUtils.longToBytes(result.memoryMB()),
                ByteUtils.longToBytes(Double.doubleToLongBits(result.score())),
                salt));
    }

    /**
     * 校验给定结果是否与本指纹匹配（未被篡改）。
     *
     * @param result 待校验的 Benchmark 结果
     * @return 是否匹配
     */
    public boolean verify(BenchmarkResult result) {
        if (result == null) {
            return false;
        }
        return Arrays.equals(hash, compute(result, salt));
    }

    public byte[] getHash() {
        return hash.clone();
    }

    public String getHashHex() {
        return CryptoUtils.toHex(hash);
    }

    public byte[] getSalt() {
        return salt.clone();
    }
}
